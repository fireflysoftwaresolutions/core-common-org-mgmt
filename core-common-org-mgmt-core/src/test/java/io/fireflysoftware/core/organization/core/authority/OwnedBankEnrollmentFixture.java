/*
 * Copyright 2026 Firefly Software Foundation
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.fireflysoftware.core.organization.core.authority;

import com.fasterxml.jackson.databind.json.JsonMapper;
import io.fireflysoftware.core.organization.core.services.authority.impl.*;
import io.fireflysoftware.core.organization.interfaces.authority.*;
import io.fireflysoftware.core.organization.models.config.*;
import io.fireflysoftware.core.organization.models.repositories.authority.*;
import io.r2dbc.spi.*;
import org.fireflyframework.security.api.domain.*;
import org.fireflyframework.security.core.context.PrincipalFactory;
import org.fireflyframework.security.core.policy.EmbeddedPolicyDecisionAdapter;
import org.fireflyframework.security.core.tenant.ConfigurableTenantResolver;
import org.fireflyframework.security.rs.JwtToFireflyPrincipalConverter;
import org.fireflyframework.security.spi.*;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.http.*;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import org.springframework.security.oauth2.client.*;
import org.springframework.security.oauth2.client.registration.*;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.web.reactive.function.client.*;
import reactor.core.publisher.Mono;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.interfaces.RSAPublicKey;
import java.sql.DriverManager;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;
import static io.fireflysoftware.core.organization.core.authority.ConfigurationGrantObservationVerifierTest.*;
import static io.fireflysoftware.core.organization.core.authority.OrganizationEnrollmentAuthorizationTest.*;
import static io.fireflysoftware.core.organization.core.authority.OrganizationProposalAuthorizationTest.*;
import static io.fireflysoftware.core.organization.core.authority.BankEnrollmentObservationTest.*;

/** Owned protocol/native fixture, never a live configuration issuer or external policy proof. */
final class OwnedBankEnrollmentFixture implements AutoCloseable {
    static final Duration WAIT=Duration.ofSeconds(15);
    static final JsonMapper MAPPER=new JsonMapper();
    static final Set<String> STAFF_PERMISSIONS=Set.of("organization.bank.enrollment.proposal.submit","organization.bank.enrollment.proposal.approve","organization.bank.enrollment.proposal.reject","organization.bank.enrollment.proposal.withdraw","organization.bank.enrollment.proposal.cancel","organization.bank.enrollment.proposal.cancel.emergency","organization.bank.enrollment.action.read");
    record Case(int index,UUID bank,UUID proposal,String revisionOriginal,long recordedAt) {
        OrganizationAuthorityIntent<BankEnrollmentRevision> full(){return BankEnrollmentRevisionCodec.decode(revisionOriginal.getBytes(StandardCharsets.UTF_8));}
    }
    final Path run;final int port;final String jdbcUrl;final AnnotationConfigApplicationContext context;
    final ConnectionFactory factory;final R2dbcEntityTemplate operations;final R2dbcTransactionManager manager;
    final AtomicReference<SecurityPrincipal> principal=new AtomicReference<>();final AtomicBoolean policyCurrent=new AtomicBoolean(true);
    final AtomicInteger sdkCalls=new AtomicInteger();Runnable duringObservation=()->{};
    final OrganizationProposalAuthorization authorization;final BankEnrollmentProposalObservationServiceImpl observer;
    final BankEnrollmentProposalCommandServiceImpl commands;final BankEnrollmentProposalApplicationServiceImpl application;
    final BankEnrollmentActionReadServiceImpl reader;
    final OrganizationEnrollmentHistoryAuthorization historyAuthorization;
    OwnedBankEnrollmentFixture()throws Exception {this(null);}
    OwnedBankEnrollmentFixture(Class<?> ordinaryConfiguration)throws Exception {
        assertEquals("approved",required("ORG_BANK_NATIVE_LEASE"));run=Path.of(required("ORG_BANK_NATIVE_RUN_DIR")).toRealPath();var marker=MAPPER.readTree(run.resolve("owner.json").toFile());
        port=marker.get("port").intValue();assertFalse(Set.of(18080,9001).contains(port));jdbcUrl="jdbc:postgresql://127.0.0.1:"+port+"/org_bank_native";assertEquals(jdbcUrl,required("ORG_BANK_NATIVE_JDBC_URL"));
        try(var connection=admin();var statement=connection.createStatement();var result=statement.executeQuery("SELECT current_setting('cluster_name'),current_setting('data_directory'),system_identifier::text,current_setting('server_encoding') FROM pg_control_system()")){
            assertTrue(result.next());assertEquals(marker.get("clusterName").textValue(),result.getString(1));assertEquals(run.resolve("data").toRealPath(),Path.of(result.getString(2)).toRealPath());assertEquals(marker.get("systemIdentifier").textValue(),result.getString(3));assertEquals("UTF8",result.getString(4));
        }
        String user=required("ORG_BANK_NATIVE_RUNTIME_USER");assertTrue(Set.of("org_native_probe","org_native_runtime").contains(user));factory=factory(user,required("ORG_BANK_NATIVE_RUNTIME_PASSWORD"));
        context=new AnnotationConfigApplicationContext();context.setAllowBeanDefinitionOverriding(false);context.registerBean(ConnectionFactory.class,()->factory);context.register(OrganizationAuthorityRepositoryConfiguration.class,OrganizationEnrollmentTransactionConfiguration.class);if(ordinaryConfiguration!=null)context.register(ordinaryConfiguration);context.refresh();
        operations=context.getBean("organizationAuthorityEntityOperations",R2dbcEntityTemplate.class);manager=context.getBean("organizationEnrollmentTransactionManager",R2dbcTransactionManager.class);
        assertSame(operations,context.getBean("r2dbcEntityTemplate"));assertSame(factory,operations.getDatabaseClient().getConnectionFactory());assertSame(factory,manager.getConnectionFactory());
        PolicyDecisionPort policies=new EmbeddedPolicyDecisionAdapter(List.of((p,a,r,c)->Mono.just(policyCurrent.get() && PLATFORM.equals(p.tenantId()) && java.util.stream.IntStream.range(0,12).mapToObj(i->"tenant/"+TENANT+"/BANK_ENROLLMENT/"+id(1000+i)).anyMatch(bankResource->OrganizationEnrollmentHistoryAuthorization.PERMISSION.equals(a)?c.get("actionId") instanceof String action && action.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}") && r.equals(bankResource+"/ACTION/"+action):r.equals(bankResource)) && p.claims().get("fixture_permissions") instanceof Collection<?> permissions && permissions.contains(a)?Decision.permit():Decision.deny("owned exact policy"))),true);
        var profile=new OrganizationActorIssuerProfile(IDP,"tenant_id","org_kind","org_staff","org_client","employee","service");
        SecurityContextPort callers=()->Mono.justOrEmpty(principal.get());authorization=new OrganizationProposalAuthorization(callers,policies,Clock.systemUTC(),PLATFORM,Map.of(IDP,profile),Map.of(IDP,REALM));
        observer=observer();var contexts=context.getBean(NativeEnrollmentContextRepository.class);var proposals=context.getBean(NativeEnrollmentProposalRepository.class);var revisions=context.getBean(NativeEnrollmentRevisionRepository.class);var controls=context.getBean(NativeEnrollmentProposalControlRepository.class);var actions=context.getBean(NativeEnrollmentActionRepository.class);
        commands=new BankEnrollmentProposalCommandServiceImpl(authorization,observer,contexts,proposals,revisions,controls,actions,operations,manager);
        application=new BankEnrollmentProposalApplicationServiceImpl(authorization,contexts,proposals,revisions,controls,commands,operations,manager);
        historyAuthorization=new OrganizationEnrollmentHistoryAuthorization(callers,policies,Clock.systemUTC(),PLATFORM,Map.of(IDP,profile),Map.of(IDP,REALM));
        reader=new BankEnrollmentActionReadServiceImpl(historyAuthorization,contexts,actions,context.getBean(NativeEnrollmentReceiptRepository.class),revisions,operations,manager);
        var origins=new LinkedHashMap<String,Object>();
        for(var type:List.of(BankEnrollmentProposalApplicationServiceImpl.class,BankEnrollmentProposalCommandServiceImpl.class,BankEnrollmentProposalObservationServiceImpl.class,BankEnrollmentActionReadServiceImpl.class,JwtToFireflyPrincipalConverter.class,EmbeddedPolicyDecisionAdapter.class,R2dbcEntityTemplate.class,R2dbcTransactionManager.class,io.fireflysoftware.common.config.sdk.api.InstitutionAuthorityApi.class)){
            try(var bytes=type.getResourceAsStream("/"+type.getName().replace('.','/')+".class")){assertNotNull(bytes);origins.put(type.getName(),Map.of("sha256",java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes.readAllBytes())),"origin",type.getProtectionDomain().getCodeSource().getLocation().toString()));}
        }
        MAPPER.writeValue(run.resolve("class-origins-"+required("ORG_BANK_NATIVE_STAGE")+".json").toFile(),origins);
    }
    static boolean hasSqlState(Throwable error,String state){for(var cause=error;cause!=null;cause=cause.getCause()){if(cause instanceof io.r2dbc.spi.R2dbcException r && state.equals(r.getSqlState()) || cause instanceof java.sql.SQLException j && state.equals(j.getSQLState()))return true;}return false;}
    static boolean hasMessage(Throwable error,String fragment){for(var cause=error;cause!=null;cause=cause.getCause())if(cause.getMessage()!=null && cause.getMessage().contains(fragment))return true;return false;}
    static String required(String name){var value=System.getenv(name);if(value==null || value.isBlank())throw new IllegalStateException("Missing owned native input: "+name);return value;}
    static UUID id(int value){return UUID.fromString("20000000-0000-0000-0000-"+String.format("%012d",value));}
    ConnectionFactory factory(String user,String password){return ConnectionFactories.get(ConnectionFactoryOptions.builder().option(ConnectionFactoryOptions.DRIVER,"postgresql").option(ConnectionFactoryOptions.HOST,"127.0.0.1").option(ConnectionFactoryOptions.PORT,port).option(ConnectionFactoryOptions.DATABASE,"org_bank_native").option(ConnectionFactoryOptions.USER,user).option(ConnectionFactoryOptions.PASSWORD,password).build());}
    java.sql.Connection admin()throws Exception{return DriverManager.getConnection(jdbcUrl,required("ORG_BANK_NATIVE_ADMIN_USER"),required("ORG_BANK_NATIVE_ADMIN_PASSWORD"));}
    java.sql.Connection runtime()throws Exception{return DriverManager.getConnection(jdbcUrl,required("ORG_BANK_NATIVE_RUNTIME_USER"),required("ORG_BANK_NATIVE_RUNTIME_PASSWORD"));}
    R2dbcEntityTemplate adminOperations(){return new R2dbcEntityTemplate(factory(required("ORG_BANK_NATIVE_ADMIN_USER"),required("ORG_BANK_NATIVE_ADMIN_PASSWORD")));}
    Case caseAt(int index)throws Exception {return MAPPER.readValue(run.resolve("case-"+index+".json").toFile(),Case.class);}
    void staff(boolean maker,String client)throws Exception {var claims=new HashMap<String,Object>();claims.put("iss",IDP);claims.put("sub",maker?"retained-maker":"verified-checker");claims.put("aud",List.of("org-inbound"));claims.put("tenant_id",PLATFORM);claims.put("org_kind","employee");claims.put("org_staff",maker?MAKER:STAFF);claims.put("org_client",client);claims.put("fixture_permissions",STAFF_PERMISSIONS);setPrincipal(claims);}
    void machine()throws Exception {setPrincipal(Map.of("iss",IDP,"sub","verified-emergency-machine","aud",List.of("org-inbound"),"tenant_id",PLATFORM,"org_kind","service","org_client","actual-emergency-client","fixture_permissions",List.of("organization.bank.enrollment.proposal.cancel.emergency","organization.bank.enrollment.action.read")));}
    private void setPrincipal(Map<String,Object> values)throws Exception {
        var changes=new HashMap<>(values);changes.put("exp",Date.from(Instant.now().plusSeconds(120)));changes.put("nbf",Date.from(Instant.now().minusSeconds(1)));var signed=signed(changes,KEY);
        var decoder=NimbusReactiveJwtDecoder.withPublicKey((RSAPublicKey)KEY.getPublic()).build();decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(new JwtTimestampValidator(Duration.ZERO),new JwtIssuerValidator(IDP),jwt->jwt.getAudience().contains("org-inbound")?OAuth2TokenValidatorResult.success():OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token"))));
        var jwt=decoder.decode(signed).block(WAIT);var mapper=new AuthorityMappingPort(){public Set<String> mapAuthorities(Map<String,Object> c){return Set.of();}public Set<String> mapScopes(Map<String,Object> c){return Set.of();}};
        var authentication=new JwtToFireflyPrincipalConverter(new PrincipalFactory(mapper),new ConfigurableTenantResolver(List.of("tenant_id")),true).convert(jwt).block(WAIT);principal.set((SecurityPrincipal)authentication.getPrincipal());assertEquals(PLATFORM,principal.get().tenantId());assertFalse(signed.equals(authentication.getCredentials()),"Bearer credentials must not be retained");
    }
    byte[] commandBody(Case c,EnrollmentProposalCommand.Operation op,long ttl)throws Exception {
        long version;try(var connection=admin();var statement=connection.prepareStatement("SELECT control_version FROM public.org_native_proposal_control WHERE tenant_id=? AND proposal_id=?")){statement.setObject(1,UUID.fromString(TENANT));statement.setObject(2,c.proposal());try(var result=statement.executeQuery()){assertTrue(result.next());version=result.getLong(1);}}
        long now=System.currentTimeMillis();var target=BankEnrollmentRevisionCodec.enrollmentTarget(c.full().request()).request();var body=MAPPER.createObjectNode().put("schemaVersion",2).put("actionId",UUID.randomUUID().toString()).put("operation",op.name()).put("expectedRevision",1).put("expectedControlVersion",version).put("expectedBankGeneration",0).put("expectedTargetDigest",c.full().intentDigest()).put("commandNotAfterEpochMillis",now+ttl).put("reason","Owned native protocol decision");
        if(op==EnrollmentProposalCommand.Operation.APPROVE)body.put("approvalUntilEpochMillis",Math.min(now+20000,target.commandDeadlineEpochMillis()));else body.putNull("approvalUntilEpochMillis");return body.toString().getBytes(StandardCharsets.UTF_8);
    }
    EnrollmentProposalCommandResult command(Case c,EnrollmentProposalCommand.Operation op)throws Exception{return application.command(op,UUID.fromString(TENANT),c.bank(),c.proposal(),1,commandBody(c,op,6000)).block(WAIT);}
    BankEnrollmentProposalObservationServiceImpl observer(){
        var registration=ClientRegistration.withRegistrationId("native-config-observer").authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS).clientId(CLIENT).clientSecret(required("ORG_BANK_NATIVE_OBSERVER_SECRET")).tokenUri("https://config-idp.example.test/token").build();
        var connection=new InstitutionAuthorityObserverProfile(TENANT,"native-config-observer",OBSERVER_ISSUER,CLIENT,SUBJECT,AUDIENCE,java.net.URI.create("https://configuration.example.test"),java.net.URI.create("https://config-idp.example.test/token"));
        var profile=new EnrollmentObserverProfile(connection,new OrganizationActorIssuerProfile(OBSERVER_ISSUER,"tenant_id","org_kind","org_staff","org_client","employee","service"));
        ReactiveOAuth2AuthorizedClientManager controlled=request->{String token=signed(Map.of("exp",Date.from(Instant.now().plusSeconds(60)),"nbf",Date.from(Instant.now().minusSeconds(1))),KEY);return Mono.just(new OAuth2AuthorizedClient(registration,request.getPrincipal().getName(),new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER,token,Instant.now().minusSeconds(1),Instant.now().plusSeconds(60))));};
        var decoder=NimbusReactiveJwtDecoder.withPublicKey((RSAPublicKey)KEY.getPublic()).build();decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(new JwtTimestampValidator(Duration.ZERO),new JwtIssuerValidator(OBSERVER_ISSUER)));
        var web=WebClient.builder().exchangeFunction(request->{sdkCalls.incrementAndGet();duringObservation.run();try{
            var query=org.springframework.web.util.UriComponentsBuilder.fromUri(request.url()).build().getQueryParams();var use=FinancialCalendarUse.valueOf(query.getFirst("use"));var definition=MAPPER.readValue(run.resolve("definition.json").toFile(),ConfigurationGrantObservation.Definition.class);long now=System.currentTimeMillis();
            var response=new ConfigurationGrantObservation(TENANT,INSTITUTION,ISSUER,use,"CURRENT_AUTHORIZED",definition,new ConfigurationGrantObservation.Control("ACTIVE",0,id(900).toString()),1L,new ConfigurationGrantObservation.Eligibility(TENANT,2,"00000000-0000-0000-0000-000000000010",3,true,true,"ACTIVE",true,null),now,now+25000,null);
            assertNotNull(request.headers().getFirst(HttpHeaders.AUTHORIZATION));assertNull(request.headers().getFirst("X-Tenant-Id"));return Mono.just(ClientResponse.create(HttpStatus.OK).header("Content-Type","application/json").header("Cache-Control","no-store").body(MAPPER.writeValueAsString(response)).build());
        }catch(Exception error){return Mono.error(error);}}).build();
        return new BankEnrollmentProposalObservationServiceImpl(authorization,new InMemoryReactiveClientRegistrationRepository(registration),controlled,Map.of(TENANT,decoder),Map.of(TENANT,profile),web,Clock.systemUTC());
    }
    @Override public void close(){context.close();}
}
