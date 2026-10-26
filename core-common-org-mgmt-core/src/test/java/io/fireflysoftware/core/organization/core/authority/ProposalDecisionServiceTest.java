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

import io.fireflysoftware.core.organization.core.services.authority.impl.BankEnrollmentProposalCommandServiceImpl;
import io.fireflysoftware.core.organization.interfaces.authority.EnrollmentProposalCommand;
import io.fireflysoftware.core.organization.models.entities.authority.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static io.fireflysoftware.core.organization.core.authority.ConfigurationGrantObservationVerifierTest.*;
import static io.fireflysoftware.core.organization.core.authority.OrganizationEnrollmentAuthorizationTest.*;
import static io.fireflysoftware.core.organization.core.authority.OrganizationProposalAuthorizationTest.*;

class ProposalDecisionServiceTest {
    static class Fixture extends ProposalTerminationServiceTest.Fixture {
        final AtomicInteger observed=new AtomicInteger();Runnable duringObservation=()->{};
        boolean evidenceAvailable=true;
        Fixture(){
            doAnswer(i->{var t=packet.get().get("target");var op=t.get("operation").textValue();return Mono.just(new EnrollmentProposalControlRow(i.getArgument(0),op.equals("SUBMIT")?"DRAFT":"SUBMITTED",1,2,null,null,UUID.randomUUID()));}).when(controls).findById(any());
        }
        OrganizationProposalAuthorization.Admission decision(EnrollmentProposalCommand.Operation op)throws Exception {
            if(op==EnrollmentProposalCommand.Operation.SUBMIT)current.caller.set(principal(PLATFORM,Map.of("org_staff",MAKER)).toBuilder().subject("retained-maker").build());
            var s=scope(op==EnrollmentProposalCommand.Operation.SUBMIT?BankEnrollmentLifecycle.ProposalState.DRAFT:BankEnrollmentLifecycle.ProposalState.SUBMITTED);
            return authorization.promote(authorization.discover(op,TENANT,BANK,s.proposalId(),1,request(op)).block(),s).block();
        }
        BankEnrollmentProposalCommandServiceImpl commandService(){
            return new BankEnrollmentProposalCommandServiceImpl(authorization,a->{observed.incrementAndGet();duringObservation.run();if(!evidenceAvailable)return Mono.just(List.of());
                try{var verified=ConfigurationGrantObservationVerifier.verify(TENANT,INSTITUTION,ISSUER,io.fireflysoftware.core.organization.interfaces.authority.FinancialCalendarUse.ACCOUNTING_BOOK,bytes(observation()),RECEIVED).limitedTo(a.validUntilEpochMillis());
                    return Mono.just(List.of(new ObservedInstitutionGrantEvidence(verified,new ObservedInstitutionGrantEvidence.Observer("https://config-idp.example.test","real-observer","observer",TENANT,"config-authority",RECEIVED,RECEIVED+60000),RECEIVED,verified.usableUntilEpochMillis())));
                }catch(Exception error){return Mono.error(error);}},contexts,proposals,revisions,controls,actions,operations,manager);
        }
    }
    @Test void actualSubmitWritesCompleteGraphAndApprovalRetainsDistinctCheckerDecisionExpiry()throws Exception {
        for(var op:List.of(EnrollmentProposalCommand.Operation.SUBMIT,EnrollmentProposalCommand.Operation.APPROVE)){
            var f=new Fixture();var a=f.decision(op);var result=f.commandService().execute(a).block();assertEquals(op==EnrollmentProposalCommand.Operation.SUBMIT?"SUBMITTED":"APPROVED",result.state());assertEquals(3,result.controlVersion());assertEquals(1,f.observed.get());
            var decisions=f.writes.stream().filter(EnrollmentDecisionRow.class::isInstance).map(EnrollmentDecisionRow.class::cast).toList();
            if(op==EnrollmentProposalCommand.Operation.APPROVE){assertEquals(1,decisions.size());assertEquals(UUID.fromString(STAFF),decisions.getFirst().decisionActorStaffId());assertEquals(RECEIVED+15000,decisions.getFirst().approvalUntilMs());assertEquals("APPROVE",decisions.getFirst().decision());}
            else assertTrue(decisions.isEmpty());
            var capture=org.mockito.ArgumentCaptor.forClass(EnrollmentProposalControlRow.class);verify(f.controls).compareAndSet(any(),capture.capture());var control=capture.getValue();assertEquals(op==EnrollmentProposalCommand.Operation.APPROVE?UUID.fromString(a.command().request().actionId()):null,control.approvalActionId());assertEquals(op==EnrollmentProposalCommand.Operation.APPROVE?RECEIVED+15000:null,control.approvalUntilMs());
            assertTrue(f.packet.get().has("observations"));assertEquals(1,f.packet.get().get("observations").size());var audit=f.writes.stream().filter(EnrollmentAuditRow.class::isInstance).map(EnrollmentAuditRow.class::cast).findFirst().orElseThrow();assertTrue(new String(audit.eventOriginal(),java.nio.charset.StandardCharsets.UTF_8).contains("CURRENT_AUTHORIZED"));verify(f.connection).commitTransaction();
        }
    }
    @Test void observationMissingExpiredOrCallerWithdrawnAfterIoCannotReachNativeMutation()throws Exception {
        var f=new Fixture();f.evidenceAvailable=false;var a=f.decision(EnrollmentProposalCommand.Operation.APPROVE);assertThrows(ResponseStatusException.class,()->f.commandService().execute(a).block());verify(f.contexts,never()).beginMutation(any(),anyString());assertTrue(f.writes.isEmpty());
        var g=new Fixture();var b=g.decision(EnrollmentProposalCommand.Operation.APPROVE);g.duringObservation=()->g.current.policy.set(org.fireflyframework.security.api.domain.Decision.deny("withdrawn"));denied(()->g.commandService().execute(b).block());verify(g.contexts,never()).beginMutation(any(),anyString());
        var h=new Fixture();var c=h.decision(EnrollmentProposalCommand.Operation.APPROVE);h.duringObservation=()->h.current.now.set(RECEIVED+5000);assertThrows(ResponseStatusException.class,()->h.commandService().execute(c).block());verify(h.contexts,never()).beginMutation(any(),anyString());
    }
    @Test void nativeWaitCannotOutliveObservationAndFinalCompletenessFailureRollsBackApproval()throws Exception {
        var f=new Fixture();var a=f.decision(EnrollmentProposalCommand.Operation.APPROVE);doAnswer(i->{f.current.now.set(RECEIVED+5000);return Mono.just(new EnrollmentNativeContextStamp(i.getArgument(0),RECEIVED+5000,RECEIVED+6000,RECEIVED+6000,f.packet.get().get("contextDigest").textValue()));}).when(f.contexts).lockContext(any());assertThrows(ResponseStatusException.class,()->f.commandService().execute(a).block());assertTrue(f.writes.isEmpty());verify(f.connection).rollbackTransaction();
        var g=new Fixture();var b=g.decision(EnrollmentProposalCommand.Operation.APPROVE);when(g.contexts.validateComplete(any())).thenReturn(Mono.just(false));assertThrows(ResponseStatusException.class,()->g.commandService().execute(b).block());verify(g.connection).rollbackTransaction();verify(g.connection,never()).commitTransaction();
    }
    @Test void actualApprovalReceiptRemainsReadableByFreshIndependentReaderAfterTargetAndOldActorExpire()throws Exception {
        var f=new Fixture();var admission=f.decision(EnrollmentProposalCommand.Operation.APPROVE);f.commandService().execute(admission).block();int written=f.writes.size();
        var action=f.writes.stream().filter(EnrollmentActionRow.class::isInstance).map(EnrollmentActionRow.class::cast).findFirst().orElseThrow();var receipt=f.writes.stream().filter(EnrollmentReceiptRow.class::isInstance).map(EnrollmentReceiptRow.class::cast).findFirst().orElseThrow();
        var full=admission.scope().target();var t=BankEnrollmentRevisionCodec.enrollmentTarget(full.request()).request();var row=new EnrollmentProposalRevisionRow(new EnrollmentProposalRevisionKey(UUID.fromString(TENANT),UUID.fromString(EnrollmentProposalCommandCodecTest.PROPOSAL),1L),"ENROLL",UUID.fromString(t.actionId()),7,0,0,null,t.commandDeadlineEpochMillis(),List.of("ACCOUNTING_BOOK"),full.originalJson().getBytes(java.nio.charset.StandardCharsets.UTF_8),ProposalTerminationEvidenceCodec.REVISION_ENCODING,full.canonicalIntent(),full.intentDigest(),RECEIVED);
        doReturn(Mono.just(action)).when(f.actions).findById(any());doReturn(Mono.just(row)).when(f.revisions).findById(any());var receipts=mock(io.fireflysoftware.core.organization.models.repositories.authority.NativeEnrollmentReceiptRepository.class);when(receipts.findById(any())).thenReturn(Mono.just(receipt));
        when(f.contexts.beginRead(any(),anyString())).thenAnswer(a->{f.packet.set(JSON.readTree((String)a.getArgument(1)));return Mono.just(a.getArgument(0));});
        f.current.now.set(RECEIVED+70000);f.current.caller.set(principal(PLATFORM,Map.of("exp",java.time.Instant.ofEpochMilli(RECEIVED+180000))).toBuilder().subject("independent-current-reader").build());
        var reads=new OrganizationEnrollmentHistoryAuthorization(()->Mono.justOrEmpty(f.current.caller.get()),(p,a,r,c)->{assertEquals("organization.bank.enrollment.action.read",a);return Mono.justOrEmpty(f.current.policy.get());},f.current.clock,PLATFORM,Map.of(IDP,new OrganizationActorIssuerProfile(IDP,"tenant_id","org_kind","org_staff","org_client","employee","service")),Map.of(IDP,REALM));
        var reader=new io.fireflysoftware.core.organization.core.services.authority.impl.BankEnrollmentActionReadServiceImpl(reads,f.contexts,f.actions,receipts,f.revisions,f.operations,f.manager);
        var history=reader.read(UUID.fromString(TENANT),UUID.fromString(BANK),action.id().actionId()).block();assertEquals("APPLIED",history.outcome());assertEquals("APPROVE",history.intendedOperation());assertEquals(STAFF,history.resolvedBy().staffActorId());assertEquals(receipt.resultDigest(),history.receipt().digest());assertEquals(written,f.writes.size());verify(f.connection,times(2)).commitTransaction();
    }
}
