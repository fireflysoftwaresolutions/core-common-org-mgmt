package io.fireflysoftware.core.organization.core.authority;

import io.fireflysoftware.core.organization.core.services.authority.impl.InstitutionAuthorityObservationServiceImpl;
import io.fireflysoftware.core.organization.interfaces.authority.FinancialCalendarUse;
import io.fireflysoftware.common.config.sdk.api.InstitutionAuthorityApi;
import org.fireflyframework.security.api.domain.*;
import org.fireflyframework.security.spi.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.security.oauth2.client.*;
import org.springframework.security.oauth2.client.registration.*;
import org.springframework.security.oauth2.core.*;
import org.springframework.web.reactive.function.client.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import java.net.URI;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;
import static io.fireflysoftware.core.organization.core.authority.ConfigurationGrantObservationVerifierTest.*;

class SdkInstitutionAuthorityObservationServiceTest {
    static final String CALLER_ISSUER="https://organization-idp.example.test",OBSERVER_ISSUER="https://observer-idp.example.test",AUDIENCE="configuration-authority",CLIENT="organization-observer",OBSERVER="registered-machine";
    static class MutableClock extends Clock {long now=RECEIVED;public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId zone){return this;}public Instant instant(){return Instant.ofEpochMilli(now);}}
    static SecurityPrincipal principal(String subject,String issuer,String tenant,Map<String,Object> changes) {
        var claims=new HashMap<String,Object>();claims.put("tenant_id",TENANT);claims.put("exp",Instant.ofEpochMilli(RECEIVED+60000));claims.put("principal_kind","MACHINE");claims.put("client_id",CLIENT);claims.put("aud",List.of(AUDIENCE));claims.putAll(changes);return new SecurityPrincipal(subject,issuer,tenant,Set.of(),Set.of(),claims,null,null,Set.of(),Map.of());
    }
    static class Fixture {
        final MutableClock clock=new MutableClock();final AtomicInteger acquisitions=new AtomicInteger(),validations=new AtomicInteger(),requests=new AtomicInteger(),decisions=new AtomicInteger();
        final AtomicReference<SecurityPrincipal> caller=new AtomicReference<>(principal("actual-org-caller",CALLER_ISSUER,TENANT,Map.of("principal_kind","STAFF","staff_actor_id","00000000-0000-0000-0000-000000000101")));
        final AtomicReference<SecurityPrincipal> machine=new AtomicReference<>(principal(OBSERVER,OBSERVER_ISSUER,TENANT,Map.of()));
        final AtomicReference<Decision> policy=new AtomicReference<>(Decision.permit());final AtomicReference<ClientRequest> sent=new AtomicReference<>();final AtomicReference<OAuth2AuthorizeRequest> acquired=new AtomicReference<>();final AtomicReference<Map<String,Object>> policyContext=new AtomicReference<>();
        final AtomicReference<String> response=new AtomicReference<>();Runnable duringResponse=()->{};HttpStatus status=HttpStatus.OK;boolean emptyAcquisition,registrationNever;
        final InstitutionAuthorityObserverProfile profile=new InstitutionAuthorityObserverProfile(TENANT,"tenant-observer",OBSERVER_ISSUER,CLIENT,OBSERVER,AUDIENCE,URI.create("https://configuration.example.test"),URI.create("https://observer-idp.example.test/token"));
        final ClientRegistration registration=ClientRegistration.withRegistrationId("tenant-observer").authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS).clientId(CLIENT).clientSecret("unit-only-fixture").tokenUri("https://observer-idp.example.test/token").build();
        Fixture()throws Exception{response.set(observation().toString());}
        InstitutionAuthorityObservationServiceImpl service(Map<String,InstitutionAuthorityObserverProfile> profiles) {
            ReactiveOAuth2AuthorizedClientManager manager=request->{acquisitions.incrementAndGet();acquired.set(request);return emptyAcquisition?Mono.empty():Mono.just(new OAuth2AuthorizedClient(registration,"organization-institution-observer",new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER,"unit-observer-token",Instant.ofEpochMilli(RECEIVED-1000),Instant.ofEpochMilli(RECEIVED+60000))));};
            TokenValidationPort validator=token->{validations.incrementAndGet();assertEquals("unit-observer-token",token.value());return Mono.justOrEmpty(machine.get());};
            PolicyDecisionPort policies=(actor,action,resource,context)->{decisions.incrementAndGet();assertEquals("actual-org-caller",actor.subject());assertEquals("organization.institution.authority.grant.observe",action);assertTrue(resource.contains(TENANT));policyContext.set(context);return Mono.justOrEmpty(policy.get());};
            var transport=WebClient.builder().exchangeFunction(request->{requests.incrementAndGet();sent.set(request);duringResponse.run();return Mono.just(ClientResponse.create(status).header("Content-Type","application/json").header("Cache-Control","no-store").body(response.get()).build());}).build();
            ReactiveClientRegistrationRepository repository=registrationNever?ignored->Mono.never():new InMemoryReactiveClientRegistrationRepository(registration);
            return new InstitutionAuthorityObservationServiceImpl(()->Mono.justOrEmpty(caller.get()),policies,repository,manager,validator,profiles,transport,clock,Set.of(CALLER_ISSUER));
        }
        VerifiedInstitutionGrantObservation observe(){return service(Map.of(TENANT,profile)).observe(TENANT,INSTITUTION,ISSUER,FinancialCalendarUse.ACCOUNTING_BOOK).block();}
    }
    static void status(int expected,Runnable work){assertEquals(expected,assertThrows(ResponseStatusException.class,work::run).getStatusCode().value());}
    @Test void genuineGeneratedRequestUsesOnlyValidatedTenantBoundMachineCredential()throws Exception {
        var f=new Fixture();var result=f.observe();assertNotNull(result);result.requireCurrentAt(RECEIVED);assertEquals(2,f.decisions.get());assertEquals(1,f.acquisitions.get());assertEquals(1,f.validations.get());assertEquals("organization-institution-observer",f.acquired.get().getPrincipal().getName());assertEquals("tenant-observer",f.acquired.get().getClientRegistrationId());var sent=f.sent.get();assertEquals(HttpMethod.GET,sent.method());assertEquals("https://configuration.example.test/api/v1/tenants/"+TENANT+"/institution-authorities/"+INSTITUTION+"/issuers/"+ISSUER+"/current-grant?use=ACCOUNTING_BOOK",sent.url().toString());assertEquals(List.of("Bearer unit-observer-token"),sent.headers().get(HttpHeaders.AUTHORIZATION));assertNull(sent.headers().getFirst("X-Tenant-Id"));assertEquals(INSTITUTION,f.policyContext.get().get("institutionId"));assertEquals(ISSUER,f.policyContext.get().get("issuerAuthorityId"));QualificationClassOrigins.verifyAndRecord(InstitutionAuthorityApi.class,"firefly.qualification.configuration-sdk-origin");
    }
    @Test void absentRegisteredTenantAndWrongOriginalTenantDenyBeforeAcquisition()throws Exception {
        var f=new Fixture();status(403,()->f.service(Map.of()).observe(TENANT,INSTITUTION,ISSUER,FinancialCalendarUse.ACCOUNTING_BOOK).block());assertEquals(0,f.acquisitions.get());f.caller.set(principal("actual-org-caller",CALLER_ISSUER,"00000000-0000-0000-0000-000000000099",Map.of()));status(403,f::observe);assertEquals(0,f.requests.get());assertEquals(0,f.acquisitions.get());
    }
    @Test void machineTenantMayComeOnlyFromValidatedClaimsWhenGenericProjectionIsNull()throws Exception {
        var f=new Fixture();f.machine.set(principal(OBSERVER,OBSERVER_ISSUER,null,Map.of()));assertNotNull(f.observe());assertEquals(1,f.requests.get());
    }
    @Test void wrongMachineKindIdentityTenantIssuerAudienceOrExpiryCannotReachSdk()throws Exception {
        for(var changes:List.<Map<String,Object>>of(Map.of("principal_kind","STAFF","staff_actor_id","00000000-0000-0000-0000-000000000101"),Map.of("client_id","other-client"),Map.of("tenant_id","00000000-0000-0000-0000-000000000099"),Map.of("aud",List.of("organization-inbound")),Map.of("exp",Instant.ofEpochMilli(RECEIVED)),Map.of("staff_actor_id","00000000-0000-0000-0000-000000000101"))){var f=new Fixture();f.machine.set(principal(OBSERVER,OBSERVER_ISSUER,TENANT,changes));status(403,f::observe);assertEquals(0,f.requests.get());}
        for(var machine:List.of(principal("wrong-subject",OBSERVER_ISSUER,TENANT,Map.of()),principal(OBSERVER,CALLER_ISSUER,TENANT,Map.of()))){var f=new Fixture();f.machine.set(machine);status(403,f::observe);assertEquals(0,f.requests.get());}
    }
    @Test void originalCurrentPolicyWithdrawalDuringDependencyReadDeniesReturnedEvidence()throws Exception {
        var f=new Fixture();f.duringResponse=()->f.policy.set(Decision.deny("withdrawn"));status(403,f::observe);assertEquals(1,f.requests.get());assertEquals(2,f.decisions.get());
    }
    @Test void rawResponseRejectsUnknownFactsAndNon200BeforeAnyEvidenceReturn()throws Exception {
        var f=new Fixture();var malformed=observation();malformed.put("unknown",true);f.response.set(malformed.toString());status(503,f::observe);var unavailable=new Fixture();unavailable.status=HttpStatus.SERVICE_UNAVAILABLE;status(503,unavailable::observe);var denied=new Fixture();denied.status=HttpStatus.FORBIDDEN;status(403,denied::observe);
    }
    @Test void emptyAcquisitionAndUnfulfilledCurrentObligationsFailClosed()throws Exception {
        var f=new Fixture();f.emptyAcquisition=true;status(403,f::observe);assertEquals(0,f.requests.get());var obligated=new Fixture();obligated.policy.set(Decision.permit(List.of(Obligation.of("mask",Map.of()))));status(403,obligated::observe);assertEquals(0,obligated.acquisitions.get());
    }
    @Test void callerExpiryDuringReadCannotReturnAuthorityOrRetainItsCredential()throws Exception {
        var f=new Fixture();f.duringResponse=()->f.clock.now=RECEIVED+60001;status(403,f::observe);assertEquals(1,f.requests.get());assertFalse(f.response.get().contains("unit-observer-token"));
    }
    @Test void stalledRegistrationLookupHasFiniteFailureBeforeAcquisition()throws Exception {
        var f=new Fixture();f.registrationNever=true;
        reactor.test.StepVerifier.withVirtualTime(()->f.service(Map.of(TENANT,f.profile)).observe(TENANT,INSTITUTION,ISSUER,FinancialCalendarUse.ACCOUNTING_BOOK))
            .thenAwait(Duration.ofSeconds(6)).expectErrorMatches(error->error instanceof ResponseStatusException status && status.getStatusCode().value()==503).verify(Duration.ofMillis(500));
        assertEquals(0,f.acquisitions.get());assertEquals(0,f.requests.get());
    }
    @Test void ambientTransactionCannotHoldLocksAcrossCredentialOrRemoteRead()throws Exception {
        var f=new Fixture();var result=org.springframework.transaction.reactive.TransactionSynchronizationManager.forCurrentTransaction().flatMap(manager->{manager.setActualTransactionActive(true);return f.service(Map.of(TENANT,f.profile)).observe(TENANT,INSTITUTION,ISSUER,FinancialCalendarUse.ACCOUNTING_BOOK);}).contextWrite(org.springframework.transaction.reactive.TransactionContextManager.createTransactionContext());
        reactor.test.StepVerifier.create(result).expectErrorMatches(error->error instanceof ResponseStatusException status && status.getStatusCode().value()==409).verify();assertEquals(0,f.acquisitions.get());assertEquals(0,f.requests.get());
    }

}
