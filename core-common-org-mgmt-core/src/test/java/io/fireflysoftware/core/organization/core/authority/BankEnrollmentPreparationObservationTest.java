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

import io.fireflysoftware.core.organization.core.services.authority.impl.BankEnrollmentPreparationObservationServiceImpl;
import io.fireflysoftware.core.organization.interfaces.authority.BankEnrollmentPreparationRequest;
import org.fireflyframework.security.api.domain.Decision;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.security.oauth2.client.*;
import org.springframework.security.oauth2.client.registration.*;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.web.reactive.function.client.*;
import reactor.core.publisher.Mono;
import java.security.interfaces.RSAPublicKey;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static io.fireflysoftware.core.organization.core.authority.ConfigurationGrantObservationVerifierTest.*;
import static io.fireflysoftware.core.organization.core.authority.OrganizationEnrollmentAuthorizationTest.*;
import static io.fireflysoftware.core.organization.core.authority.OrganizationProposalAuthorizationTest.*;
import static io.fireflysoftware.core.organization.core.authority.BankEnrollmentObservationTest.*;

class BankEnrollmentPreparationObservationTest {
    static class Fixture extends BankEnrollmentObservationTest.Fixture {
        final List<String> permissions=new ArrayList<>();long captureLifetime=20000;
        final OrganizationPreparationAuthorization preparations=new OrganizationPreparationAuthorization(()->Mono.justOrEmpty(auth.caller.get()),
                (p,a,r,c)->{permissions.add(a);auth.calls.incrementAndGet();auth.duringPolicy.run();return Mono.justOrEmpty(auth.policy.get());},auth.clock,PLATFORM,
                Map.of(IDP,new OrganizationActorIssuerProfile(IDP,"tenant_id","org_kind","org_staff","org_client","employee","service")),Map.of(IDP,REALM));
        OrganizationPreparationAuthorization.Captured captured(){
            var desired=preparations.discover(TENANT,BANK,BankEnrollmentPreparationRequest.Mode.ENROLL,bytes(BankEnrollmentPreparationCodecTest.desired())).block();
            return preparations.bindCapture(desired,new BankEnrollmentPreparationPlan.LocalSnapshot(BANK,7,0,9,null),RECEIVED+captureLifetime).block();
        }
        BankEnrollmentPreparationObservationServiceImpl preparationService(){
            var decoder=NimbusReactiveJwtDecoder.withPublicKey((RSAPublicKey)KEY.getPublic()).build();var time=new JwtTimestampValidator(Duration.ZERO);time.setClock(auth.clock);decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(time,new JwtIssuerValidator(OBSERVER_ISSUER)));
            ReactiveOAuth2AuthorizedClientManager manager=request->{acquisitions.incrementAndGet();return Mono.just(new OAuth2AuthorizedClient(registration,request.getPrincipal().getName(),new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER,token,Instant.ofEpochMilli(RECEIVED-1000),Instant.ofEpochMilli(RECEIVED+envelopeLifetime))));};
            var transport=WebClient.builder().exchangeFunction(request->{requests.incrementAndGet();sent.set(request);duringResponse.run();try{return Mono.just(ClientResponse.create(HttpStatus.OK).header("Content-Type","application/json").header("Cache-Control","no-store").body(responseTransform.apply(observation().put("requestedUse",org.springframework.web.util.UriComponentsBuilder.fromUri(request.url()).build().getQueryParams().getFirst("use"))).toString()).build());}catch(Exception e){return Mono.error(e);}}).build();
            ReactiveClientRegistrationRepository registrations=id->new InMemoryReactiveClientRegistrationRepository(registration).findByRegistrationId(id).doOnNext(r->duringRegistration.run());
            ReactiveJwtDecoder actual=value->decoder.decode(value).doOnNext(jwt->duringDecode.run());
            return new BankEnrollmentPreparationObservationServiceImpl(preparations,registrations,manager,Map.of(TENANT,actual),Map.of(TENANT,new EnrollmentObserverProfile(connection,claims)),transport,auth.clock);
        }
    }
    @Test void desiredCaptureObservesEachUseThroughActualSignedMachineAndGenuineSdk()throws Exception {
        var f=new Fixture();var captured=f.captured();var result=f.preparationService().observeForPreparation(captured).block();assertNotNull(result);assertEquals(2,result.size());assertEquals(2,f.requests.get());assertEquals(2,f.acquisitions.get());assertEquals(List.of("ACCOUNTING_BOOK","PRODUCT_ISSUANCE"),result.stream().map(e->e.observation().observation().requestedUse().name()).toList());
        result.forEach(e->{e.requireCurrentAt(RECEIVED);assertEquals(CLIENT,e.observer().clientId());assertEquals(TENANT,e.observer().tenantId());});assertTrue(f.permissions.stream().allMatch("organization.bank.enrollment.prepare"::equals));assertEquals("Bearer "+f.token,f.sent.get().headers().getFirst(HttpHeaders.AUTHORIZATION));assertNull(f.sent.get().headers().getFirst("X-Tenant-Id"));
        var plan=BankEnrollmentPreparationPlan.build(captured.discovery().desired(),captured.local(),BankEnrollmentPreparationPlanTest.allocation(),result,RECEIVED);assertNotNull(f.preparations.admitPrepared(captured,plan).block());
    }
    @Test void currentOriginalPermissionIsRequiredBeforeCredentialsAndAfterEveryResponse(){
        var f=new Fixture();var c=f.captured();f.auth.policy.set(Decision.deny("withdrawn"));forbidden(()->f.preparationService().observeForPreparation(c).block());assertEquals(0,f.acquisitions.get());
        var g=new Fixture();var d=g.captured();g.duringResponse=()->g.auth.policy.set(Decision.deny("withdrawn"));forbidden(()->g.preparationService().observeForPreparation(d).block());assertEquals(1,g.requests.get());assertEquals(1,g.acquisitions.get());
    }
    @Test void signedConfigurationMachineAudienceTenantKindAndSignatureCannotBeSubstituted(){
        for(var claims:List.<Map<String,Object>>of(Map.of("aud",List.of("org-inbound")),Map.of("tenant_id",PLATFORM),Map.of("org_kind","employee"),Map.of("org_client","wrong-client"),Map.of("org_staff",STAFF))){var f=new Fixture();f.token=signed(claims,KEY);var c=f.captured();forbidden(()->f.preparationService().observeForPreparation(c).block());assertEquals(0,f.requests.get());}
        var f=new Fixture();f.token=signed(Map.of(),key());var c=f.captured();forbidden(()->f.preparationService().observeForPreparation(c).block());assertEquals(0,f.requests.get());
    }
    @Test void observationCannotRestartTheOriginalCaptureHorizon(){var f=new Fixture();f.captureLifetime=3000;var c=f.captured();var result=f.preparationService().observeForPreparation(c).block();assertNotNull(result);result.forEach(e->{assertEquals(RECEIVED+3000,e.usableUntilEpochMillis());assertThrows(IllegalStateException.class,()->e.requireCurrentAt(RECEIVED+3000));});assertEquals(RECEIVED+3000,c.validUntilEpochMillis());}
    @Test void independentlyValidUsesCannotCombineDifferentCurrentGrantControls(){var f=new Fixture();f.responseTransform=n->{if(f.requests.get()==2)((com.fasterxml.jackson.databind.node.ObjectNode)n.get("control")).put("controlVersion",1);return n;};var c=f.captured();assertEquals(409,assertThrows(org.springframework.web.server.ResponseStatusException.class,()->f.preparationService().observeForPreparation(c).block()).getStatusCode().value());assertEquals(2,f.requests.get());}
    @Test void replacementCallerOrExpiryDuringCredentialIoStopsTheNextBoundary(){var f=new Fixture();var c=f.captured();f.duringRegistration=()->f.auth.caller.set(f.auth.caller.get().toBuilder().subject("replacement").build());forbidden(()->f.preparationService().observeForPreparation(c).block());assertEquals(0,f.acquisitions.get());
        var g=new Fixture();g.envelopeLifetime=1000;var d=g.captured();g.duringDecode=()->g.auth.now.set(RECEIVED+1000);forbidden(()->g.preparationService().observeForPreparation(d).block());assertEquals(0,g.requests.get());}
    @Test void foreignPreparationAdmissionCannotAcquireAnObserverCredential(){var f=new Fixture();var other=new Fixture();var c=other.captured();forbidden(()->f.preparationService().observeForPreparation(c).block());assertEquals(0,f.acquisitions.get());assertEquals(0,f.requests.get());}
}
