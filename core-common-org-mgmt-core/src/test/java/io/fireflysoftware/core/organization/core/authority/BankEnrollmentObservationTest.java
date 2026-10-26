/*
 * Copyright 2026 Firefly Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.fireflysoftware.core.organization.core.authority;

import io.fireflysoftware.core.organization.core.services.authority.impl.BankEnrollmentObservationServiceImpl;
import org.fireflyframework.security.api.domain.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.security.oauth2.client.*;
import org.springframework.security.oauth2.client.registration.*;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.web.reactive.function.client.*;
import org.springframework.web.server.ResponseStatusException;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.*;
import com.nimbusds.jwt.*;
import reactor.core.publisher.Mono;
import java.net.URI;
import java.security.*;
import java.security.interfaces.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;
import static io.fireflysoftware.core.organization.core.authority.ConfigurationGrantObservationVerifierTest.*;
import static io.fireflysoftware.core.organization.core.authority.OrganizationEnrollmentAuthorizationTest.*;

class BankEnrollmentObservationTest {
    static final String OBSERVER_ISSUER="https://config-idp.example.test",CLIENT="enrollment-observer",SUBJECT="org-enrollment-machine",AUDIENCE="configuration-authority";
    static final KeyPair KEY=key();
    static KeyPair key(){try{var g=KeyPairGenerator.getInstance("RSA");g.initialize(2048);return g.generateKeyPair();}catch(Exception e){throw new AssertionError(e);}}
    static String signed(Map<String,Object> changes,KeyPair key) {
        try {
            var claims=new HashMap<String,Object>();claims.put("iss",OBSERVER_ISSUER);claims.put("sub",SUBJECT);claims.put("aud",List.of(AUDIENCE));claims.put("exp",Date.from(Instant.ofEpochMilli(RECEIVED+60000)));claims.put("nbf",Date.from(Instant.ofEpochMilli(RECEIVED-1000)));claims.put("tenant_id",TENANT);claims.put("org_kind","service");claims.put("org_client",CLIENT);claims.putAll(changes);
            var builder=new JWTClaimsSet.Builder();claims.forEach(builder::claim);var jwt=new SignedJWT(new JWSHeader(JWSAlgorithm.RS256),builder.build());jwt.sign(new RSASSASigner((RSAPrivateKey)key.getPrivate()));return jwt.serialize();
        }catch(Exception e){throw new AssertionError(e);}
    }
    static class Fixture {
        final OrganizationEnrollmentAuthorizationTest.Fixture auth=new OrganizationEnrollmentAuthorizationTest.Fixture();final AtomicInteger acquisitions=new AtomicInteger(),requests=new AtomicInteger();final AtomicReference<ClientRequest> sent=new AtomicReference<>();String token=signed(Map.of(),KEY);Runnable duringResponse=()->{},duringDecode=()->{},duringRegistration=()->{};long envelopeLifetime=60000;java.util.function.UnaryOperator<com.fasterxml.jackson.databind.node.ObjectNode> responseTransform=n->n;
        final ClientRegistration registration=ClientRegistration.withRegistrationId("enrollment-machine").authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS).clientId(CLIENT).clientSecret("source-unit-fixture").tokenUri("https://config-idp.example.test/token").build();
        final InstitutionAuthorityObserverProfile connection=new InstitutionAuthorityObserverProfile(TENANT,"enrollment-machine",OBSERVER_ISSUER,CLIENT,SUBJECT,AUDIENCE,URI.create("https://configuration.example.test"),URI.create("https://config-idp.example.test/token"));
        final OrganizationActorIssuerProfile claims=new OrganizationActorIssuerProfile(OBSERVER_ISSUER,"tenant_id","org_kind","org_staff","org_client","employee","service");
        final OrganizationAuthorityAuthorization authorizer=auth.authorization();
        BankEnrollmentObservationServiceImpl service(){
            var decoder=NimbusReactiveJwtDecoder.withPublicKey((RSAPublicKey)KEY.getPublic()).build();var time=new JwtTimestampValidator(Duration.ZERO);time.setClock(auth.clock);decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(time,new JwtIssuerValidator(OBSERVER_ISSUER)));
            ReactiveOAuth2AuthorizedClientManager manager=request->{acquisitions.incrementAndGet();assertEquals("enrollment-machine",request.getClientRegistrationId());return Mono.just(new OAuth2AuthorizedClient(registration,request.getPrincipal().getName(),new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER,token,Instant.ofEpochMilli(RECEIVED-1000),Instant.ofEpochMilli(RECEIVED+envelopeLifetime))));};
            var transport=WebClient.builder().exchangeFunction(request->{requests.incrementAndGet();sent.set(request);duringResponse.run();try{return Mono.just(ClientResponse.create(HttpStatus.OK).header("Content-Type","application/json").header("Cache-Control","no-store").body(responseTransform.apply(observation()).toString()).build());}catch(Exception e){return Mono.error(e);}}).build();
            ReactiveClientRegistrationRepository registrations=id->new InMemoryReactiveClientRegistrationRepository(registration).findByRegistrationId(id).doOnNext(r->duringRegistration.run());
            ReactiveJwtDecoder actualDecoder=value->decoder.decode(value).doOnNext(jwt->duringDecode.run());
            return new BankEnrollmentObservationServiceImpl(authorizer,registrations,manager,Map.of(TENANT,actualDecoder),Map.of(TENANT,new EnrollmentObserverProfile(connection,claims)),transport,auth.clock);
        }
        OrganizationAuthorityAuthorization.EnrollmentAdmission admission(){return authorizer.admit(OrganizationAuthorityAuthorization.EnrollmentOperation.PREPARE,bytesUnchecked()).block();}
    }
    static void forbidden(Runnable r){assertEquals(403,assertThrows(ResponseStatusException.class,r::run).getStatusCode().value());}
    @Test void actualSignedJwtIsProtocolValidatedWithoutConstructingGenericMachinePrincipal() {
        var f=new Fixture();var result=f.service().observeForEnrollment(f.admission()).block();assertNotNull(result);assertEquals(3,f.auth.calls.get());assertEquals(1,f.acquisitions.get());assertEquals(1,f.requests.get());assertEquals(TENANT,result.observer().tenantId());assertEquals(OBSERVER_ISSUER,result.observer().issuer());assertEquals(SUBJECT,result.observer().subject());assertEquals(CLIENT,result.observer().clientId());assertEquals(AUDIENCE,result.observer().audience());assertEquals(RECEIVED+20000,result.usableUntilEpochMillis());assertEquals("Bearer "+f.token,f.sent.get().headers().getFirst(HttpHeaders.AUTHORIZATION));assertNull(f.sent.get().headers().getFirst("X-Tenant-Id"));result.requireCurrentAt(RECEIVED);
    }
    @Test void wrongSignedMachineProfileAudienceTenantKindOrSignatureCannotReachSdk() {
        for(var changes:List.<Map<String,Object>>of(Map.of("aud",List.of("org-inbound")),Map.of("tenant_id",PLATFORM),Map.of("org_kind","employee"),Map.of("org_client","wrong-client"),Map.of("org_staff",STAFF),Map.of("iss","https://other.example.test"),Map.of("sub","other"),Map.of("exp",Date.from(Instant.ofEpochMilli(RECEIVED))))) {
            var f=new Fixture();f.token=signed(changes,KEY);forbidden(()->f.service().observeForEnrollment(f.admission()).block());assertEquals(0,f.requests.get());
        }
        var f=new Fixture();f.token=signed(Map.of(),key());forbidden(()->f.service().observeForEnrollment(f.admission()).block());assertEquals(0,f.requests.get());
    }
    @Test void callerWithdrawalReplacementAndExpiryAfterIoCannotReturnEvidence() {
        var f=new Fixture();var a=f.admission();f.auth.policy.set(Decision.deny("withdrawn"));var initial=f;forbidden(()->initial.service().observeForEnrollment(a).block());assertEquals(0,f.acquisitions.get());
        f=new Fixture();var g=f;f.duringResponse=()->g.auth.caller.set(g.auth.caller.get().toBuilder().subject("replacement").build());forbidden(()->g.service().observeForEnrollment(g.admission()).block());assertEquals(1,f.requests.get());
        f=new Fixture();var h=f;f.duringResponse=()->h.auth.now.set(RECEIVED+20000);forbidden(()->h.service().observeForEnrollment(h.admission()).block());
    }
    @Test void independentlyCurrentPolicyWithdrawalAfterSdkReadDeniesEvidence() {
        var f=new Fixture();f.duringResponse=()->f.auth.policy.set(Decision.deny("withdrawn"));forbidden(()->f.service().observeForEnrollment(f.admission()).block());assertEquals(1,f.requests.get());
    }
    @Test void actualSharedRequiredTenantResolverProjectsSignedCallerWithoutOwnerRepair() throws Exception {
        var f=new Fixture();String token=signed(Map.of("iss",IDP,"sub","verified-operator","aud",List.of("org-inbound"),"tenant_id",PLATFORM,"org_kind","employee","org_staff",STAFF),KEY);
        var decoder=NimbusReactiveJwtDecoder.withPublicKey((RSAPublicKey)KEY.getPublic()).build();var time=new JwtTimestampValidator(Duration.ZERO);time.setClock(f.auth.clock);decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(time,new JwtIssuerValidator(IDP)));var jwt=decoder.decode(token).block();
        var mapper=new org.fireflyframework.security.spi.AuthorityMappingPort(){public Set<String> mapAuthorities(Map<String,Object> c){return Set.of();}public Set<String> mapScopes(Map<String,Object> c){return Set.of();}};
        var factory=new org.fireflyframework.security.core.context.PrincipalFactory(mapper);
        var legacy=new org.fireflyframework.security.rs.JwtToFireflyPrincipalConverter(factory);f.auth.caller.set((SecurityPrincipal)legacy.convert(jwt).block().getPrincipal());assertNull(f.auth.caller.get().tenantId());forbidden(f::admission);
        var configured=new org.fireflyframework.security.rs.JwtToFireflyPrincipalConverter(factory,new org.fireflyframework.security.core.tenant.ConfigurableTenantResolver(List.of("tenant_id")),true);var authentication=configured.convert(jwt).block();f.auth.caller.set((SecurityPrincipal)authentication.getPrincipal());assertEquals(PLATFORM,f.auth.caller.get().tenantId());assertNotNull(f.admission());assertNotEquals(token,authentication.getCredentials());
        QualificationClassOrigins.verifyAndRecord(configured.getClass(),"firefly.qualification.security-resource-server-origin");
    }
    @Test void matchingStaticFullTargetRejectsAChangedRemoteControlEvenWithValidCurrentEvidence() {
        var f=new Fixture();f.responseTransform=n->{((com.fasterxml.jackson.databind.node.ObjectNode)n.get("control")).put("controlVersion",1);return n;};assertEquals(409,assertThrows(ResponseStatusException.class,()->f.service().observeForEnrollment(f.admission()).block()).getStatusCode().value());assertEquals(1,f.requests.get());
    }
    @Test void concurrentDestinationsUseTheirOwnFreshGeneratedSdkCredentialAndFullScope() throws Exception {
        var f=new Fixture();String otherTenant="00000000-0000-0000-0000-000000000097",otherClient="enrollment-observer-2";
        String firstToken=f.token,secondToken=signed(Map.of("tenant_id",otherTenant,"org_client",otherClient),KEY);
        var secondResponse=observation();secondResponse.put("tenantId",otherTenant);var definition=(com.fasterxml.jackson.databind.node.ObjectNode)secondResponse.get("definition");definition.put("tenantId",otherTenant);String canonical=definition.get("canonicalDefinition").textValue().replace(TENANT,otherTenant);definition.put("canonicalDefinition",canonical).put("definitionDigest",AuthorityWire.sha(canonical));((com.fasterxml.jackson.databind.node.ObjectNode)secondResponse.get("tenantEligibility")).put("tenantId",otherTenant);
        var target=intent();target.put("actionId","00000000-0000-0000-0000-000000000098").put("destinationTenantId",otherTenant);target.set("grantDefinition",definition);
        var first=f.admission();var second=f.authorizer.admit(OrganizationAuthorityAuthorization.EnrollmentOperation.PREPARE,bytes(target)).block();
        var registration=ClientRegistration.withRegistrationId("enrollment-machine-2").authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS).clientId(otherClient).clientSecret("source-unit-fixture").tokenUri("https://config-idp.example.test/token").build();
        var connection=new InstitutionAuthorityObserverProfile(otherTenant,"enrollment-machine-2",OBSERVER_ISSUER,otherClient,SUBJECT,AUDIENCE,URI.create("https://configuration.example.test"),URI.create("https://config-idp.example.test/token"));
        var decoder=NimbusReactiveJwtDecoder.withPublicKey((RSAPublicKey)KEY.getPublic()).build();var timestamp=new JwtTimestampValidator(Duration.ZERO);timestamp.setClock(f.auth.clock);decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(timestamp,new JwtIssuerValidator(OBSERVER_ISSUER)));
        ReactiveOAuth2AuthorizedClientManager manager=request->{boolean other=request.getClientRegistrationId().equals("enrollment-machine-2");return Mono.just(new OAuth2AuthorizedClient(other?registration:f.registration,request.getPrincipal().getName(),new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER,other?secondToken:firstToken,Instant.ofEpochMilli(RECEIVED-1000),Instant.ofEpochMilli(RECEIVED+f.envelopeLifetime))));};
        var seen=java.util.concurrent.ConcurrentHashMap.<String>newKeySet();var transport=WebClient.builder().exchangeFunction(request->{boolean other=request.url().getPath().contains(otherTenant);String tenant=other?otherTenant:TENANT;assertEquals("Bearer "+(other?secondToken:firstToken),request.headers().getFirst(HttpHeaders.AUTHORIZATION));seen.add(tenant);try{return Mono.delay(Duration.ofMillis(10)).map(ignored->ClientResponse.create(HttpStatus.OK).header("Content-Type","application/json").header("Cache-Control","no-store").body(other?secondResponse.toString():uncheckedObservation()).build());}catch(Exception e){return Mono.error(e);}}).build();
        var service=new BankEnrollmentObservationServiceImpl(f.authorizer,new InMemoryReactiveClientRegistrationRepository(f.registration,registration),manager,Map.of(TENANT,decoder,otherTenant,decoder),Map.of(TENANT,new EnrollmentObserverProfile(f.connection,f.claims),otherTenant,new EnrollmentObserverProfile(connection,f.claims)),transport,f.auth.clock);
        var results=reactor.core.publisher.Flux.merge(service.observeForEnrollment(first),service.observeForEnrollment(second)).collectMap(e->e.observer().tenantId()).block();assertEquals(Set.of(TENANT,otherTenant),results.keySet());assertEquals(Set.of(TENANT,otherTenant),seen);assertEquals(CLIENT,results.get(TENANT).observer().clientId());assertEquals(otherClient,results.get(otherTenant).observer().clientId());
    }
    static String uncheckedObservation(){try{return observation().toString();}catch(Exception e){throw new AssertionError(e);}}
    @Test void credentialEnvelopeExpiryDuringActualDecodeCannotReachSdk() {
        var f=new Fixture();f.envelopeLifetime=5000;f.duringDecode=()->f.auth.now.addAndGet(6000);forbidden(()->f.service().observeForEnrollment(f.admission()).block());assertEquals(0,f.requests.get());
    }
    @Test void admissionExpiryDuringRegistrationCannotAcquireACredential() {
        var f=new Fixture();f.duringRegistration=()->f.auth.now.addAndGet(20000);forbidden(()->f.service().observeForEnrollment(f.admission()).block());assertEquals(0,f.acquisitions.get());assertEquals(0,f.requests.get());
    }
    @Test void missingActualJwtAudienceOrExpiryIsInvalidCredentialBeforeSdk() {
        for(String key:List.of("aud","exp")) {var f=new Fixture();var missing=new HashMap<String,Object>();missing.put(key,null);f.token=signed(missing,KEY);forbidden(()->f.service().observeForEnrollment(f.admission()).block());assertEquals(0,f.requests.get());}
    }
    @Test void shortActualJwtExpiryOnlyShortensEvidenceAndNoSecretIsAProvenanceField() {
        var f=new Fixture();f.token=signed(Map.of("exp",Date.from(Instant.ofEpochMilli(RECEIVED+5000))),KEY);var result=f.service().observeForEnrollment(f.admission()).block();long expiry=(RECEIVED+5000)/1000*1000;assertEquals(expiry,result.usableUntilEpochMillis());assertThrows(IllegalStateException.class,()->result.requireCurrentAt(expiry));
        for(var component:result.observer().getClass().getRecordComponents())assertFalse(component.getName().toLowerCase().matches(".*(token|bearer|secret|authentication|claims).*"));
    }
}
