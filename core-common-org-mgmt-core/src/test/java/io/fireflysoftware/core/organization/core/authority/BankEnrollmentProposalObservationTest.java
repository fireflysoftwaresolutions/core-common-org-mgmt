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

import io.fireflysoftware.core.organization.core.services.authority.impl.BankEnrollmentProposalObservationServiceImpl;
import io.fireflysoftware.core.organization.interfaces.authority.EnrollmentProposalCommand;
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

class BankEnrollmentProposalObservationTest {
    static class Fixture extends BankEnrollmentObservationTest.Fixture {
        final List<String> permissions=new ArrayList<>();boolean allUses=false;
        final OrganizationProposalAuthorization proposals=new OrganizationProposalAuthorization(()->Mono.justOrEmpty(auth.caller.get()),
                (p,a,r,c)->{permissions.add(a);auth.calls.incrementAndGet();auth.duringPolicy.run();return Mono.justOrEmpty(auth.policy.get());},auth.clock,PLATFORM,
                Map.of(IDP,new OrganizationActorIssuerProfile(IDP,"tenant_id","org_kind","org_staff","org_client","employee","service")),Map.of(IDP,REALM));
        OrganizationProposalAuthorization.Admission proposal(EnrollmentProposalCommand.Operation op)throws Exception {
            if(op==EnrollmentProposalCommand.Operation.SUBMIT)auth.caller.set(principal(PLATFORM,Map.of("org_staff",MAKER)).toBuilder().subject("retained-maker").build());
            var scope=scope(op==EnrollmentProposalCommand.Operation.SUBMIT?BankEnrollmentLifecycle.ProposalState.DRAFT:BankEnrollmentLifecycle.ProposalState.SUBMITTED);
            var body=JSON.readTree(request(op));
            if(allUses){
                var node=BankEnrollmentRevisionCodecTest.revision();node.putArray("allowedUses").add("ACCOUNTING_BOOK").add("PRODUCT_ISSUANCE");
                var full=BankEnrollmentRevisionCodec.decode(bytes(node));scope=new OrganizationProposalAuthorization.ProposalScope(scope.tenantId(),scope.bankId(),scope.proposalId(),scope.revision(),scope.controlVersion(),scope.bankGeneration(),scope.state(),full,scope.maker());
                ((com.fasterxml.jackson.databind.node.ObjectNode)body).put("expectedTargetDigest",full.intentDigest());
            }
            return proposals.promote(proposals.discover(op,TENANT,BANK,scope.proposalId(),1,bytes(body)).block(),scope).block();
        }
        BankEnrollmentProposalObservationServiceImpl proposalService(){
            var decoder=NimbusReactiveJwtDecoder.withPublicKey((RSAPublicKey)KEY.getPublic()).build();var time=new JwtTimestampValidator(Duration.ZERO);time.setClock(auth.clock);decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(time,new JwtIssuerValidator(OBSERVER_ISSUER)));
            ReactiveOAuth2AuthorizedClientManager manager=request->{acquisitions.incrementAndGet();return Mono.just(new OAuth2AuthorizedClient(registration,request.getPrincipal().getName(),new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER,token,Instant.ofEpochMilli(RECEIVED-1000),Instant.ofEpochMilli(RECEIVED+envelopeLifetime))));};
            var transport=WebClient.builder().exchangeFunction(request->{requests.incrementAndGet();sent.set(request);duringResponse.run();try{return Mono.just(ClientResponse.create(HttpStatus.OK).header("Content-Type","application/json").header("Cache-Control","no-store").body(responseTransform.apply(observation().put("requestedUse",org.springframework.web.util.UriComponentsBuilder.fromUri(request.url()).build().getQueryParams().getFirst("use"))).toString()).build());}catch(Exception e){return Mono.error(e);}}).build();
            ReactiveClientRegistrationRepository registrations=id->new InMemoryReactiveClientRegistrationRepository(registration).findByRegistrationId(id).doOnNext(r->duringRegistration.run());
            ReactiveJwtDecoder actual=value->decoder.decode(value).doOnNext(jwt->duringDecode.run());
            return new BankEnrollmentProposalObservationServiceImpl(proposals,registrations,manager,Map.of(TENANT,actual),Map.of(TENANT,new EnrollmentObserverProfile(connection,claims)),transport,auth.clock);
        }
    }
    @Test void currentFullSubmitAndApproveUseOnlyTheirExactPermissionAndActualSignedMachineSdk()throws Exception {
        for(var op:List.of(EnrollmentProposalCommand.Operation.SUBMIT,EnrollmentProposalCommand.Operation.APPROVE)){
            var f=new Fixture();var a=f.proposal(op);var result=f.proposalService().observeForProposal(a).block();assertNotNull(result);result.forEach(e->e.requireCurrentAt(f.auth.now.get()));
            assertEquals(1,result.size());assertEquals(CLIENT,result.getFirst().observer().clientId());assertEquals(RECEIVED+5000,result.getFirst().usableUntilEpochMillis());assertEquals(1,f.requests.get());assertEquals(1,f.acquisitions.get());assertTrue(f.permissions.stream().allMatch(p->p.equals("organization.bank.enrollment.proposal."+op.name().toLowerCase(Locale.ROOT))));assertEquals("Bearer "+f.token,f.sent.get().headers().getFirst(HttpHeaders.AUTHORIZATION));assertNull(f.sent.get().headers().getFirst("X-Tenant-Id"));
        }
    }
    @Test void withdrawalBeforeCredentialsAndAfterSdkReadDeniesEvidence()throws Exception {
        var f=new Fixture();var a=f.proposal(EnrollmentProposalCommand.Operation.APPROVE);f.auth.policy.set(Decision.deny("withdrawn"));forbidden(()->f.proposalService().observeForProposal(a).block());assertEquals(0,f.acquisitions.get());
        var g=new Fixture();var b=g.proposal(EnrollmentProposalCommand.Operation.APPROVE);g.duringResponse=()->g.auth.policy.set(Decision.deny("withdrawn"));forbidden(()->g.proposalService().observeForProposal(b).block());assertEquals(1,g.requests.get());
    }
    @Test void signedWrongAudienceOrCurrentStaticGrantChangeCannotBecomeApprovalEvidence()throws Exception {
        var f=new Fixture();f.token=signed(Map.of("aud",List.of("org-inbound")),KEY);var a=f.proposal(EnrollmentProposalCommand.Operation.APPROVE);forbidden(()->f.proposalService().observeForProposal(a).block());assertEquals(0,f.requests.get());
        var g=new Fixture();g.responseTransform=n->{((com.fasterxml.jackson.databind.node.ObjectNode)n.get("control")).put("controlVersion",1);return n;};var b=g.proposal(EnrollmentProposalCommand.Operation.APPROVE);assertEquals(409,assertThrows(org.springframework.web.server.ResponseStatusException.class,()->g.proposalService().observeForProposal(b).block()).getStatusCode().value());
    }
    @Test void finiteEvidenceCannotOutliveTheOriginalAdmissionHorizon()throws Exception {
        var f=new Fixture();var a=f.proposal(EnrollmentProposalCommand.Operation.APPROVE);var result=f.proposalService().observeForProposal(a).block();f.auth.now.set(RECEIVED+5000);assertThrows(IllegalStateException.class,()->result.getFirst().requireCurrentAt(f.auth.now.get()));
    }
    @Test void eachAllowedUseGetsItsOwnFreshScopedSdkCallAndCurrentCallerRecheck()throws Exception {
        var f=new Fixture();f.allUses=true;var a=f.proposal(EnrollmentProposalCommand.Operation.APPROVE);var result=f.proposalService().observeForProposal(a).block();assertEquals(2,result.size());assertEquals(2,f.acquisitions.get());assertEquals(2,f.requests.get());assertEquals(List.of("ACCOUNTING_BOOK","PRODUCT_ISSUANCE"),result.stream().map(e->e.observation().observation().requestedUse().name()).toList());
        assertEquals(a.scope().target().request().allowedUses(),result.stream().map(e->e.observation().observation().requestedUse()).toList());
        var g=new Fixture();g.allUses=true;var b=g.proposal(EnrollmentProposalCommand.Operation.APPROVE);g.duringResponse=()->g.auth.policy.set(Decision.deny("withdrawn"));forbidden(()->g.proposalService().observeForProposal(b).block());assertEquals(1,g.acquisitions.get());assertEquals(1,g.requests.get());
    }
    @Test void observerCredentialExpiryDuringCurrentPolicyWaitCannotReachSdk()throws Exception {
        var f=new Fixture();f.envelopeLifetime=1000;var a=f.proposal(EnrollmentProposalCommand.Operation.APPROVE);f.duringDecode=()->f.auth.duringPolicy=()->f.auth.now.set(RECEIVED+1000);forbidden(()->f.proposalService().observeForProposal(a).block());assertEquals(1,f.acquisitions.get());assertEquals(0,f.requests.get());
    }
}
