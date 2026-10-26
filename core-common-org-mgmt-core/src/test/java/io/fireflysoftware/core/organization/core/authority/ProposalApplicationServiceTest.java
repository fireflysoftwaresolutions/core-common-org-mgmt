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

import io.fireflysoftware.core.organization.core.services.authority.impl.BankEnrollmentProposalApplicationServiceImpl;
import io.fireflysoftware.core.organization.interfaces.authority.EnrollmentProposalCommand;
import io.fireflysoftware.core.organization.models.entities.authority.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static io.fireflysoftware.core.organization.core.authority.ConfigurationGrantObservationVerifierTest.*;
import static io.fireflysoftware.core.organization.core.authority.OrganizationEnrollmentAuthorizationTest.*;
import static io.fireflysoftware.core.organization.core.authority.OrganizationProposalAuthorizationTest.*;

class ProposalApplicationServiceTest {
    static class Fixture extends ProposalDecisionServiceTest.Fixture {
        void retained(EnrollmentProposalCommand.Operation op)throws Exception {
            var full=scope(op==EnrollmentProposalCommand.Operation.SUBMIT?BankEnrollmentLifecycle.ProposalState.DRAFT:BankEnrollmentLifecycle.ProposalState.SUBMITTED).target();var t=BankEnrollmentRevisionCodec.enrollmentTarget(full.request()).request();
            var row=new EnrollmentProposalRevisionRow(new EnrollmentProposalRevisionKey(UUID.fromString(TENANT),UUID.fromString(EnrollmentProposalCommandCodecTest.PROPOSAL),1L),"ENROLL",UUID.fromString(t.actionId()),7,0,0,null,t.commandDeadlineEpochMillis(),List.of("ACCOUNTING_BOOK"),full.originalJson().getBytes(java.nio.charset.StandardCharsets.UTF_8),ProposalTerminationEvidenceCodec.REVISION_ENCODING,full.canonicalIntent(),full.intentDigest(),RECEIVED);
            doReturn(Mono.just(row)).when(revisions).findById(any());doReturn(Mono.just(new EnrollmentProposalControlRow(new EnrollmentProposalKey(UUID.fromString(TENANT),UUID.fromString(EnrollmentProposalCommandCodecTest.PROPOSAL)),op==EnrollmentProposalCommand.Operation.SUBMIT?"DRAFT":"SUBMITTED",1,2,null,null,UUID.randomUUID()))).when(controls).findById(any());
            when(contexts.beginProposalRead(any(),anyString())).thenAnswer(i->{packet.set(JSON.readTree((String)i.getArgument(1)));return Mono.just(i.getArgument(0));});
            if(op==EnrollmentProposalCommand.Operation.SUBMIT)current.caller.set(principal(PLATFORM,Map.of("org_staff",MAKER)).toBuilder().subject("retained-maker").build());
        }
        BankEnrollmentProposalApplicationServiceImpl application(){return new BankEnrollmentProposalApplicationServiceImpl(authorization,contexts,proposals,revisions,controls,commandService(),operations,manager);}
        io.fireflysoftware.core.organization.interfaces.authority.EnrollmentProposalCommandResult run(EnrollmentProposalCommand.Operation op)throws Exception{return application().command(op,UUID.fromString(TENANT),UUID.fromString(BANK),UUID.fromString(EnrollmentProposalCommandCodecTest.PROPOSAL),1,request(op)).block();}
    }
    @Test void actualProtectedDiscoveryClosesBeforeFullPromotionCredentialObservationAndMappedCommand()throws Exception {
        for(var op:List.of(EnrollmentProposalCommand.Operation.SUBMIT,EnrollmentProposalCommand.Operation.APPROVE)){
            var f=new Fixture();f.retained(op);f.duringObservation=()->verify(f.connection).commitTransaction();var result=f.run(op);assertEquals(op.name().equals("SUBMIT")?"SUBMITTED":"APPROVED",result.state());verify(f.contexts).beginProposalRead(any(),anyString());verify(f.contexts).beginMutation(any(),anyString());verify(f.connection,times(2)).commitTransaction();assertEquals(1,f.observed.get());
        }
    }
    @Test void currentDiscoveryDenialPreventsAnyRetainedRowReadOrCredentialAcquisition()throws Exception {
        var f=new Fixture();f.retained(EnrollmentProposalCommand.Operation.APPROVE);f.current.policy.set(org.fireflyframework.security.api.domain.Decision.deny("no discovery"));denied(()->{try{f.run(EnrollmentProposalCommand.Operation.APPROVE);}catch(RuntimeException e){throw e;}catch(Exception e){throw new AssertionError(e);}});verify(f.contexts,never()).beginProposalRead(any(),anyString());verify(f.proposals,never()).findById(any());assertEquals(0,f.observed.get());
    }
    @Test void withdrawalAfterRetainedIoCannotPromoteOrObserveAndAbsenceIsOnly404()throws Exception {
        var f=new Fixture();f.retained(EnrollmentProposalCommand.Operation.APPROVE);doAnswer(i->{f.current.policy.set(org.fireflyframework.security.api.domain.Decision.deny("withdrawn"));return Mono.just(new EnrollmentProposalControlRow(i.getArgument(0),"SUBMITTED",1,2,null,null,UUID.randomUUID()));}).when(f.controls).findById(any());assertEquals(403,assertThrows(ResponseStatusException.class,()->f.run(EnrollmentProposalCommand.Operation.APPROVE)).getStatusCode().value());assertEquals(0,f.observed.get());verify(f.contexts,never()).beginMutation(any(),anyString());
        var g=new Fixture();g.retained(EnrollmentProposalCommand.Operation.APPROVE);doReturn(Mono.empty()).when(g.proposals).findById(any());assertEquals(404,assertThrows(ResponseStatusException.class,()->g.run(EnrollmentProposalCommand.Operation.APPROVE)).getStatusCode().value());assertTrue(g.writes.isEmpty());assertEquals(0,g.observed.get());
    }
    @Test void scopedHeaderMismatchOrChangedControlCannotBecomeAFullAdmission()throws Exception {
        var f=new Fixture();f.retained(EnrollmentProposalCommand.Operation.APPROVE);doReturn(Mono.just(new EnrollmentProposalRow(new EnrollmentProposalKey(UUID.fromString(TENANT),UUID.fromString(EnrollmentProposalCommandCodecTest.PROPOSAL)),UUID.randomUUID(),UUID.fromString(INSTITUTION),UUID.fromString(ISSUER),"ENROLL",UUID.fromString(ENROLLMENT),"STAFF",IDP,"retained-maker",UUID.fromString(PLATFORM),REALM,UUID.fromString(MAKER),null,UUID.randomUUID(),RECEIVED))).when(f.proposals).findById(any());assertEquals(409,assertThrows(ResponseStatusException.class,()->f.run(EnrollmentProposalCommand.Operation.APPROVE)).getStatusCode().value());assertEquals(0,f.observed.get());assertTrue(f.writes.isEmpty());
        var g=new Fixture();g.retained(EnrollmentProposalCommand.Operation.APPROVE);doReturn(Mono.just(new EnrollmentProposalControlRow(new EnrollmentProposalKey(UUID.fromString(TENANT),UUID.fromString(EnrollmentProposalCommandCodecTest.PROPOSAL)),"SUBMITTED",1,3,null,null,UUID.randomUUID()))).when(g.controls).findById(any());assertEquals(403,assertThrows(ResponseStatusException.class,()->g.run(EnrollmentProposalCommand.Operation.APPROVE)).getStatusCode().value());assertEquals(0,g.observed.get());assertTrue(g.writes.isEmpty());
    }
}
