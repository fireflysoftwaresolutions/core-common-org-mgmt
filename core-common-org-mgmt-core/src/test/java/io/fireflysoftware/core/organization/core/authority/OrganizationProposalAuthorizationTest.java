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

import io.fireflysoftware.core.organization.interfaces.authority.EnrollmentProposalCommand;
import org.fireflyframework.security.api.domain.Decision;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static io.fireflysoftware.core.organization.core.authority.ConfigurationGrantObservationVerifierTest.*;
import static io.fireflysoftware.core.organization.core.authority.OrganizationEnrollmentAuthorizationTest.*;
import static io.fireflysoftware.core.organization.core.authority.EnrollmentProposalCommandCodecTest.*;

class OrganizationProposalAuthorizationTest {
    static final String REALM="staff-registry-a",MAKER="00000000-0000-0000-0000-000000000401";
    static OrganizationProposalAuthorization.ProposalScope scope(BankEnrollmentLifecycle.ProposalState state) throws Exception {
        return new OrganizationProposalAuthorization.ProposalScope(TENANT,BANK,PROPOSAL,1,2,7,state,BankEnrollmentRevisionCodec.decode(bytes(BankEnrollmentRevisionCodecTest.revision())),
                new OrganizationProposalAuthorization.Maker(IDP,"retained-maker",PLATFORM,REALM,MAKER));
    }
    static class ProposalFixture extends Fixture {
        String permission;Map<String,Object> proposalContext;
        OrganizationProposalAuthorization proposals(){return new OrganizationProposalAuthorization(()->reactor.core.publisher.Mono.justOrEmpty(caller.get()),(p,a,r,c)->{calls.incrementAndGet();permission=a;proposalContext=c;duringPolicy.run();return reactor.core.publisher.Mono.justOrEmpty(policy.get());},clock,PLATFORM,Map.of(IDP,new OrganizationActorIssuerProfile(IDP,"tenant_id","org_kind","org_staff","org_client","employee","service")),Map.of(IDP,REALM));}
    }
    static byte[] request(EnrollmentProposalCommand.Operation operation) throws Exception {var n=command(operation.name()).put("expectedTargetDigest",BankEnrollmentRevisionCodec.decode(bytes(BankEnrollmentRevisionCodecTest.revision())).intentDigest());if(operation!=EnrollmentProposalCommand.Operation.APPROVE)n.putNull("approvalUntilEpochMillis");return bytes(n);}
    @Test void distinctCurrentCheckerHasExactPermissionAndFullActorTargetContext() throws Exception {
        var f=new ProposalFixture();var a=f.proposals().admit(EnrollmentProposalCommand.Operation.APPROVE,scope(BankEnrollmentLifecycle.ProposalState.SUBMITTED),request(EnrollmentProposalCommand.Operation.APPROVE)).block();assertNotNull(a);assertEquals("organization.bank.enrollment.proposal.approve",f.permission);assertEquals(PLATFORM,a.actor().tenantId());assertEquals(TENANT,f.proposalContext.get("resourceTenantId"));assertEquals(REALM,f.proposalContext.get("makerStaffRealm"));assertEquals(7L,f.proposalContext.get("expectedBankGeneration"));assertEquals(a.command().canonicalIntent(),f.proposalContext.get("canonicalIntent"));assertEquals(RECEIVED+5000,a.validUntilEpochMillis());
    }
    @Test void sameCanonicalStaffOrSameIssuerSubjectCannotApproveAndNullTenantNeverRepairs() throws Exception {
        for(var p:java.util.List.of(principal(PLATFORM,Map.of("org_staff",MAKER)),principal(PLATFORM,Map.of("org_staff",MAKER,"org_client","different-client-same-human")),principal(PLATFORM,Map.of()).toBuilder().subject("retained-maker").build(),principal(null,Map.of()))) {
            var f=new ProposalFixture();f.caller.set(p);denied(()->f.proposals().admit(EnrollmentProposalCommand.Operation.APPROVE,scopeUnchecked(),approveUnchecked()).block());assertEquals(0,f.calls.get());
        }
    }
    @Test void submitWithdrawAndCancelRequireCurrentExactMakerAndTheirOwnPermission() throws Exception {
        for(var operation:new EnrollmentProposalCommand.Operation[]{EnrollmentProposalCommand.Operation.SUBMIT,EnrollmentProposalCommand.Operation.WITHDRAW,EnrollmentProposalCommand.Operation.CANCEL}) {
            var state=operation==EnrollmentProposalCommand.Operation.SUBMIT?BankEnrollmentLifecycle.ProposalState.DRAFT:BankEnrollmentLifecycle.ProposalState.APPROVED;
            var f=new ProposalFixture();var auth=f.proposals();var s=scope(state);var bytes=request(operation);denied(()->auth.admit(operation,s,bytes).block());assertEquals(0,f.calls.get());f.caller.set(principal(PLATFORM,Map.of("org_staff",MAKER)).toBuilder().subject("retained-maker").build());assertNotNull(auth.admit(operation,s,bytes).block());assertEquals("organization.bank.enrollment.proposal."+operation.name().toLowerCase(java.util.Locale.ROOT),f.permission);
        }
    }
    @Test void machineCannotApproveOrImpersonateStaffButEmergencyCancelRetainsActualMachine() throws Exception {
        var f=new ProposalFixture();var claims=new java.util.HashMap<>(f.caller.get().claims());claims.remove("org_staff");claims.put("org_kind","service");claims.put("org_client","real-worker");f.caller.set(f.caller.get().toBuilder().claims(claims).build());var auth=f.proposals();denied(()->auth.admit(EnrollmentProposalCommand.Operation.APPROVE,scopeUnchecked(),approveUnchecked()).block());
        var a=auth.admit(EnrollmentProposalCommand.Operation.CANCEL_EMERGENCY,scope(BankEnrollmentLifecycle.ProposalState.APPROVED),request(EnrollmentProposalCommand.Operation.CANCEL_EMERGENCY)).block();assertEquals("MACHINE",a.actor().kind());assertNull(a.actor().staffActorId());assertEquals("real-worker",a.actor().clientId());assertEquals("organization.bank.enrollment.proposal.cancel.emergency",f.permission);
    }
    @Test void currentWithdrawalForeignAdmissionChangedIdentityAndPolicyWaitFailClosed() throws Exception {
        var f=new ProposalFixture();var auth=f.proposals();var a=auth.admit(EnrollmentProposalCommand.Operation.APPROVE,scopeUnchecked(),approveUnchecked()).block();assertSame(a,auth.recheck(a).block());f.policy.set(Decision.deny("withdrawn"));denied(()->auth.recheck(a).block());f.policy.set(Decision.permit());denied(()->f.proposals().recheck(a).block());f.caller.set(f.caller.get().toBuilder().subject("replacement").build());denied(()->auth.recheck(a).block());f.caller.set(principal(PLATFORM,Map.of()));f.duringPolicy=()->f.now.addAndGet(5000);denied(()->auth.admit(EnrollmentProposalCommand.Operation.APPROVE,scopeUnchecked(),approveUnchecked()).block());
    }
    @Test void retainedControlGenerationStateAndConfiguredRealmAreMandatoryBeforePolicy() throws Exception {
        var f=new ProposalFixture();var auth=f.proposals();var n=command("APPROVE").put("expectedControlVersion",1).put("expectedTargetDigest",scopeUnchecked().target().intentDigest());denied(()->auth.admit(EnrollmentProposalCommand.Operation.APPROVE,scopeUnchecked(),bytes(n)).block());var s=scope(BankEnrollmentLifecycle.ProposalState.DRAFT);denied(()->auth.admit(EnrollmentProposalCommand.Operation.APPROVE,s,approveUnchecked()).block());
        var submitted=scopeUnchecked();var wrongRealm=new OrganizationProposalAuthorization.ProposalScope(submitted.tenantId(),submitted.bankId(),submitted.proposalId(),submitted.revision(),submitted.controlVersion(),submitted.bankGeneration(),submitted.state(),submitted.target(),new OrganizationProposalAuthorization.Maker(IDP,"retained-maker",PLATFORM,"unknown-realm",MAKER));denied(()->auth.admit(EnrollmentProposalCommand.Operation.APPROVE,wrongRealm,approveUnchecked()).block());assertEquals(0,f.calls.get());
    }
    static OrganizationProposalAuthorization.ProposalScope scopeUnchecked(){try{return scope(BankEnrollmentLifecycle.ProposalState.SUBMITTED);}catch(Exception e){throw new AssertionError(e);}}
    static byte[] approveUnchecked(){try{return request(EnrollmentProposalCommand.Operation.APPROVE);}catch(Exception e){throw new AssertionError(e);}}
    @Test void emergencyCancellationDoesNotDependOnHistoricalMakerIssuerStillBeingEnabled() throws Exception {
        var f=new ProposalFixture();var original=scopeUnchecked();var old=new OrganizationProposalAuthorization.ProposalScope(original.tenantId(),original.bankId(),original.proposalId(),original.revision(),original.controlVersion(),original.bankGeneration(),original.state(),original.target(),new OrganizationProposalAuthorization.Maker("https://retired-idp.example.test","retained-maker",PLATFORM,"retained-realm",MAKER));
        var auth=f.proposals();assertNotNull(auth.admit(EnrollmentProposalCommand.Operation.CANCEL_EMERGENCY,old,request(EnrollmentProposalCommand.Operation.CANCEL_EMERGENCY)).block());assertEquals("https://retired-idp.example.test",f.proposalContext.get("makerIssuer"));
        denied(()->auth.admit(EnrollmentProposalCommand.Operation.APPROVE,old,approveUnchecked()).block());
    }
    @Test void oldSingleTargetDigestCannotAuthorizeTheFullApprovedRevision() throws Exception {
        var f=new ProposalFixture();var original=command("APPROVE");denied(()->f.proposals().admit(EnrollmentProposalCommand.Operation.APPROVE,scopeUnchecked(),bytes(original)).block());assertEquals(0,f.calls.get());
    }
    @Test void exactDiscoveryAdmissionPrecedesProtectedReadsAndPromotesOnlyItsOriginalRequest() throws Exception {
        var f=new ProposalFixture();var auth=f.proposals();var bytes=request(EnrollmentProposalCommand.Operation.APPROVE);
        var discovery=auth.discover(EnrollmentProposalCommand.Operation.APPROVE,TENANT,BANK,PROPOSAL,1,bytes).block();assertNotNull(discovery);assertEquals("PROPOSAL_DISCOVERY",f.proposalContext.get("stage"));assertEquals("organization.bank.enrollment.proposal.approve",f.permission);assertEquals(PLATFORM,discovery.actor().tenantId());
        f.now.addAndGet(1000);var full=auth.promote(discovery,scopeUnchecked()).block();assertNotNull(full);assertEquals(RECEIVED+5000,full.validUntilEpochMillis());assertEquals(discovery.actor(),full.actor());assertEquals(scopeUnchecked().target().canonicalIntent(),f.proposalContext.get("retainedTargetCanonicalIntent"));
    }
    @Test void discoveryCannotCrossAuthorizersScopeActorOrItsOriginalFiniteHorizon() throws Exception {
        var f=new ProposalFixture();var auth=f.proposals();var d=auth.discover(EnrollmentProposalCommand.Operation.APPROVE,TENANT,BANK,PROPOSAL,1,request(EnrollmentProposalCommand.Operation.APPROVE)).block();
        var original=scopeUnchecked();var wrong=new OrganizationProposalAuthorization.ProposalScope(original.tenantId(),STAFF,original.proposalId(),original.revision(),original.controlVersion(),original.bankGeneration(),original.state(),original.target(),original.maker());int calls=f.calls.get();denied(()->auth.promote(d,wrong).block());assertEquals(calls,f.calls.get());
        denied(()->f.proposals().promote(d,scopeUnchecked()).block());f.policy.set(Decision.deny("withdrawn"));denied(()->auth.promote(d,scopeUnchecked()).block());f.policy.set(Decision.permit());f.caller.set(f.caller.get().toBuilder().subject("replacement").build());denied(()->auth.promote(d,scopeUnchecked()).block());f.caller.set(principal(PLATFORM,Map.of()));f.now.set(RECEIVED+5000);denied(()->auth.promote(d,scopeUnchecked()).block());
    }
    @Test void discoveryRejectsNullProjectedTenantAndMachineGovernanceBeforePolicy() throws Exception {
        var f=new ProposalFixture();f.caller.set(principal(null,Map.of()));denied(()->f.proposals().discover(EnrollmentProposalCommand.Operation.APPROVE,TENANT,BANK,PROPOSAL,1,approveUnchecked()).block());assertEquals(0,f.calls.get());
        var claims=new java.util.HashMap<>(principal(PLATFORM,Map.of()).claims());claims.remove("org_staff");claims.put("org_kind","service");claims.put("org_client","actual-machine");f.caller.set(principal(PLATFORM,Map.of()).toBuilder().claims(claims).build());denied(()->f.proposals().discover(EnrollmentProposalCommand.Operation.APPROVE,TENANT,BANK,PROPOSAL,1,approveUnchecked()).block());assertEquals(0,f.calls.get());
        assertEquals("MACHINE",f.proposals().discover(EnrollmentProposalCommand.Operation.CANCEL_EMERGENCY,TENANT,BANK,PROPOSAL,1,request(EnrollmentProposalCommand.Operation.CANCEL_EMERGENCY)).block().actor().kind());
    }
    @Test void promotionAfterReadCannotRestartTheOriginalThirtySecondAuthorityWindow() throws Exception {
        var f=new ProposalFixture();var auth=f.proposals();var original=scopeUnchecked();var target=BankEnrollmentIntentCodec.decode(bytes(intent().put("commandDeadlineEpochMillis",RECEIVED+60000)));
        var revision=BankEnrollmentRevisionCodecTest.revision().put("originalEnrollmentTargetJson",target.originalJson()).put("canonicalEnrollmentTarget",target.canonicalIntent()).put("enrollmentTargetDigest",target.intentDigest());var full=BankEnrollmentRevisionCodec.decode(bytes(revision));var known=new OrganizationProposalAuthorization.ProposalScope(original.tenantId(),original.bankId(),original.proposalId(),original.revision(),original.controlVersion(),original.bankGeneration(),original.state(),full,original.maker());
        var request=command("APPROVE").put("expectedTargetDigest",full.intentDigest()).put("commandNotAfterEpochMillis",RECEIVED+60000).put("approvalUntilEpochMillis",RECEIVED+55000);
        var discovery=auth.discover(EnrollmentProposalCommand.Operation.APPROVE,TENANT,BANK,PROPOSAL,1,bytes(request)).block();assertEquals(RECEIVED+30000,discovery.validUntilEpochMillis());f.now.addAndGet(5000);var promoted=auth.promote(discovery,known).block();assertEquals(RECEIVED+30000,promoted.validUntilEpochMillis());f.now.set(RECEIVED+30000);denied(promoted::requireCurrent);
    }
    @Test void freshEmergencyCancellationCanTerminateAnExpiredTargetWithoutRevivingApproval() throws Exception {
        var f=new ProposalFixture();f.now.set(RECEIVED+30000);var auth=f.proposals();var known=scopeUnchecked();var body=command("CANCEL_EMERGENCY").putNull("approvalUntilEpochMillis").put("expectedTargetDigest",known.target().intentDigest()).put("commandNotAfterEpochMillis",RECEIVED+35000);
        var discovery=auth.discover(EnrollmentProposalCommand.Operation.CANCEL_EMERGENCY,TENANT,BANK,PROPOSAL,1,bytes(body)).block();assertNotNull(auth.promote(discovery,known).block());
        var approve=command("APPROVE").put("expectedTargetDigest",known.target().intentDigest()).put("commandNotAfterEpochMillis",RECEIVED+35000).put("approvalUntilEpochMillis",RECEIVED+34000);var pending=auth.discover(EnrollmentProposalCommand.Operation.APPROVE,TENANT,BANK,PROPOSAL,1,bytes(approve)).block();denied(()->auth.promote(pending,known).block());
    }
}
