/*
 * Copyright 2026 Firefly Software Foundation
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.fireflysoftware.core.organization.core.authority;

import io.fireflysoftware.core.organization.core.services.authority.impl.*;
import io.fireflysoftware.core.organization.models.repositories.BankRepository;

import com.fasterxml.jackson.databind.json.JsonMapper;
import io.fireflysoftware.core.organization.core.services.authority.impl.*;
import io.fireflysoftware.core.organization.interfaces.authority.*;
import io.fireflysoftware.core.organization.models.config.*;
import io.fireflysoftware.core.organization.models.repositories.authority.*;
import io.fireflysoftware.core.organization.models.repositories.authority.impl.NativeBankEnrollmentOwnerIdentityReadsImpl;
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
import static io.fireflysoftware.core.organization.core.authority.OwnedBankEnrollmentFixture.*;

/** Owned protocol/native fixture, never a live configuration issuer or external policy proof. */
final class OwnedBankPreparationFixture implements AutoCloseable {
    @org.springframework.boot.test.context.TestConfiguration(proxyBeanMethods=false)
    @org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories(basePackages="io.fireflysoftware.core.organization.models.repositories",entityOperationsRef="r2dbcEntityTemplate",
            excludeFilters=@org.springframework.context.annotation.ComponentScan.Filter(type=org.springframework.context.annotation.FilterType.REGEX,pattern="io\\.fireflysoftware\\.core\\.organization\\.models\\.repositories\\.authority\\..*"))
    static class OrdinaryRepositories { }
    static final Set<Integer> BANK_NUMBERS=java.util.stream.Stream.concat(java.util.stream.IntStream.range(1000,1012).boxed(),java.util.stream.Stream.of(1200,1201,1202)).collect(java.util.stream.Collectors.toUnmodifiableSet());
    final OwnedBankEnrollmentFixture base;final OrganizationPreparationAuthorization authorization;
    final BankEnrollmentPreparationCaptureServiceImpl captures;final BankEnrollmentPreparationObservationServiceImpl observer;
    final BankEnrollmentPreparationCommandServiceImpl writer;final BankEnrollmentPreparationServiceImpl application;final BankEnrollmentPreparationRecoveryServiceImpl recovery;
    OwnedBankPreparationFixture()throws Exception {
        base=new OwnedBankEnrollmentFixture(OrdinaryRepositories.class);
        PolicyDecisionPort policies=new EmbeddedPolicyDecisionAdapter(List.of((p,a,r,c)->Mono.just(base.policyCurrent.get() && PLATFORM.equals(p.tenantId()) && Set.of("organization.bank.enrollment.prepare","organization.bank.enrollment.transfer.prepare").contains(a)
                && BANK_NUMBERS.stream().map(i->"tenant/"+TENANT+"/BANK_ENROLLMENT/"+id(i)).anyMatch(r::equals)
                && p.claims().get("fixture_permissions") instanceof Collection<?> permissions && permissions.contains(a)?Decision.permit():Decision.deny("owned exact preparation policy"))),true);
        var profile=new OrganizationActorIssuerProfile(IDP,"tenant_id","org_kind","org_staff","org_client","employee","service");
        authorization=new OrganizationPreparationAuthorization(()->Mono.justOrEmpty(base.principal.get()),policies,Clock.systemUTC(),PLATFORM,Map.of(IDP,profile),Map.of(IDP,REALM));
        var context=base.context;var nativeContext=context.getBean(NativeEnrollmentContextRepository.class);var preparationContext=context.getBean(NativeEnrollmentPreparationContextRepository.class);
        captures=new BankEnrollmentPreparationCaptureServiceImpl(authorization,preparationContext,nativeContext,context.getBean(BankRepository.class),context.getBean(NativeBankGateRepository.class),context.getBean(NativeBankEnrollmentOwnerIdentityRepository.class),context.getBean(NativeBankEnrollmentControlRepository.class),base.operations,base.manager);
        observer=observer();writer=new BankEnrollmentPreparationCommandServiceImpl(authorization,preparationContext,nativeContext,context.getBean(NativeEnrollmentActionRepository.class),base.operations,base.manager);
        application=new BankEnrollmentPreparationServiceImpl(authorization,captures,observer,writer);recovery=new BankEnrollmentPreparationRecoveryServiceImpl(base.reader);
        var origins=new LinkedHashMap<String,Object>();for(var type:List.of(BankEnrollmentPreparationCaptureServiceImpl.class,BankEnrollmentPreparationObservationServiceImpl.class,BankEnrollmentPreparationCommandServiceImpl.class,BankEnrollmentPreparationServiceImpl.class,BankEnrollmentPreparationRecoveryServiceImpl.class,NativeBankEnrollmentOwnerIdentityReadsImpl.class)){
            try(var bytes=type.getResourceAsStream("/"+type.getName().replace('.','/')+".class")){assertNotNull(bytes);origins.put(type.getName(),Map.of("sha256",java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes.readAllBytes())),"origin",type.getProtectionDomain().getCodeSource().getLocation().toString()));}
        }MAPPER.writeValue(base.run.resolve("preparation-class-origins-"+required("ORG_BANK_NATIVE_STAGE")+".json").toFile(),origins);
    }
    void staff(boolean maker,String client)throws Exception {var claims=new HashMap<String,Object>();claims.put("iss",IDP);claims.put("sub",maker?"retained-maker":"verified-checker");claims.put("aud",List.of("org-inbound"));claims.put("tenant_id",PLATFORM);claims.put("org_kind","employee");claims.put("org_staff",maker?MAKER:STAFF);claims.put("org_client",client);var permissions=new HashSet<>(STAFF_PERMISSIONS);permissions.add("organization.bank.enrollment.prepare");permissions.add("organization.bank.enrollment.transfer.prepare");claims.put("fixture_permissions",permissions);setPrincipal(claims);}
    byte[] desired(UUID bank,UUID action,long ttl){var value=MAPPER.createObjectNode().put("schemaVersion",2).put("actionId",action.toString()).put("mode","ENROLL").put("bankId",bank.toString()).put("destinationTenantId",TENANT).put("institutionId",INSTITUTION).put("issuerAuthorityId",ISSUER).put("notAfterEpochMillis",System.currentTimeMillis()+ttl).put("reason","Owned actual preparation command");value.putArray("allowedUses").add("ACCOUNTING_BOOK").add("PRODUCT_ISSUANCE");value.putArray("evidenceReferences").add("fixture://actual-preparation-no-upstream-acceptance");return value.toString().getBytes(StandardCharsets.UTF_8);}
    EnrollmentPreparedProposal prepare(UUID bank,UUID action,long ttl){return application.prepare(BankEnrollmentPreparationRequest.Mode.ENROLL,UUID.fromString(TENANT),bank,desired(bank,action,ttl)).block(WAIT);}
    OrganizationPreparationAuthorization.Prepared admitted(UUID bank,UUID action,long ttl){var desired=desired(bank,action,ttl);var discovery=authorization.discover(TENANT,bank.toString(),BankEnrollmentPreparationRequest.Mode.ENROLL,desired).block(WAIT);var captured=captures.capture(discovery).block(WAIT);var observations=observer.observeForPreparation(captured).block(WAIT);authorization.recheckCapture(captured).block(WAIT);var allocation=new BankEnrollmentPreparationPlan.Allocation(UUID.randomUUID().toString(),UUID.randomUUID().toString(),UUID.randomUUID().toString());return authorization.admitPrepared(captured,BankEnrollmentPreparationPlan.build(captured.discovery().desired(),captured.local(),allocation,observations,System.currentTimeMillis())).block(WAIT);}
    static void mutateFixtureBankSource(java.sql.Connection connection,UUID bank)throws Exception {
        assertTrue(connection.getAutoCommit(),"Owned concurrent metadata changes require separate committed transactions");
        try(var statement=connection.prepareStatement("UPDATE public.bank SET is_active=? WHERE id=?")){
            for(boolean active:List.of(false,true)){statement.setBoolean(1,active);statement.setObject(2,bank);assertEquals(1,statement.executeUpdate());}
        }
    }
    @Override public void close(){base.close();}
    private void setPrincipal(Map<String,Object> values)throws Exception {
        var changes=new HashMap<>(values);changes.put("exp",Date.from(Instant.now().plusSeconds(120)));changes.put("nbf",Date.from(Instant.now().minusSeconds(1)));var signed=signed(changes,KEY);
        var decoder=NimbusReactiveJwtDecoder.withPublicKey((RSAPublicKey)KEY.getPublic()).build();decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(new JwtTimestampValidator(Duration.ZERO),new JwtIssuerValidator(IDP),jwt->jwt.getAudience().contains("org-inbound")?OAuth2TokenValidatorResult.success():OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token"))));
        var jwt=decoder.decode(signed).block(WAIT);var mapper=new AuthorityMappingPort(){public Set<String> mapAuthorities(Map<String,Object> c){return Set.of();}public Set<String> mapScopes(Map<String,Object> c){return Set.of();}};
        var authentication=new JwtToFireflyPrincipalConverter(new PrincipalFactory(mapper),new ConfigurableTenantResolver(List.of("tenant_id")),true).convert(jwt).block(WAIT);base.principal.set((SecurityPrincipal)authentication.getPrincipal());assertEquals(PLATFORM,base.principal.get().tenantId());assertFalse(signed.equals(authentication.getCredentials()),"Bearer credentials must not be retained");
    }
    BankEnrollmentPreparationObservationServiceImpl observer(){
        var registration=ClientRegistration.withRegistrationId("native-config-observer").authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS).clientId(CLIENT).clientSecret(required("ORG_BANK_NATIVE_OBSERVER_SECRET")).tokenUri("https://config-idp.example.test/token").build();
        var connection=new InstitutionAuthorityObserverProfile(TENANT,"native-config-observer",OBSERVER_ISSUER,CLIENT,SUBJECT,AUDIENCE,java.net.URI.create("https://configuration.example.test"),java.net.URI.create("https://config-idp.example.test/token"));
        var profile=new EnrollmentObserverProfile(connection,new OrganizationActorIssuerProfile(OBSERVER_ISSUER,"tenant_id","org_kind","org_staff","org_client","employee","service"));
        ReactiveOAuth2AuthorizedClientManager controlled=request->{String token=signed(Map.of("exp",Date.from(Instant.now().plusSeconds(60)),"nbf",Date.from(Instant.now().minusSeconds(1))),KEY);return Mono.just(new OAuth2AuthorizedClient(registration,request.getPrincipal().getName(),new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER,token,Instant.now().minusSeconds(1),Instant.now().plusSeconds(60))));};
        var decoder=NimbusReactiveJwtDecoder.withPublicKey((RSAPublicKey)KEY.getPublic()).build();decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(new JwtTimestampValidator(Duration.ZERO),new JwtIssuerValidator(OBSERVER_ISSUER)));
        var web=WebClient.builder().exchangeFunction(request->{base.sdkCalls.incrementAndGet();base.duringObservation.run();try{
            var query=org.springframework.web.util.UriComponentsBuilder.fromUri(request.url()).build().getQueryParams();var use=FinancialCalendarUse.valueOf(query.getFirst("use"));var definition=MAPPER.readValue(base.run.resolve("definition.json").toFile(),ConfigurationGrantObservation.Definition.class);long now=System.currentTimeMillis();
            var response=new ConfigurationGrantObservation(TENANT,INSTITUTION,ISSUER,use,"CURRENT_AUTHORIZED",definition,new ConfigurationGrantObservation.Control("ACTIVE",0,id(900).toString()),1L,new ConfigurationGrantObservation.Eligibility(TENANT,2,"00000000-0000-0000-0000-000000000010",3,true,true,"ACTIVE",true,null),now,now+25000,null);
            assertNotNull(request.headers().getFirst(HttpHeaders.AUTHORIZATION));assertNull(request.headers().getFirst("X-Tenant-Id"));return Mono.just(ClientResponse.create(HttpStatus.OK).header("Content-Type","application/json").header("Cache-Control","no-store").body(MAPPER.writeValueAsString(response)).build());
        }catch(Exception error){return Mono.error(error);}}).build();
        return new BankEnrollmentPreparationObservationServiceImpl(authorization,new InMemoryReactiveClientRegistrationRepository(registration),controlled,Map.of(TENANT,decoder),Map.of(TENANT,profile),web,Clock.systemUTC());
    }
}
