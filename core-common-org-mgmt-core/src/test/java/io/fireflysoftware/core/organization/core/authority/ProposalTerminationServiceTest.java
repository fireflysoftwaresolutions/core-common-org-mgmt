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

import io.fireflysoftware.core.organization.core.services.authority.impl.BankEnrollmentProposalTerminationServiceImpl;
import io.fireflysoftware.core.organization.interfaces.authority.EnrollmentProposalCommand;
import io.fireflysoftware.core.organization.models.entities.authority.*;
import io.fireflysoftware.core.organization.models.repositories.authority.*;
import io.r2dbc.spi.*;
import org.junit.jupiter.api.Test;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import reactor.core.publisher.Mono;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static io.fireflysoftware.core.organization.core.authority.ConfigurationGrantObservationVerifierTest.*;
import static io.fireflysoftware.core.organization.core.authority.OrganizationEnrollmentAuthorizationTest.*;
import static io.fireflysoftware.core.organization.core.authority.OrganizationProposalAuthorizationTest.*;

class ProposalTerminationServiceTest {
    static class Fixture {
        final ProposalFixture current=new ProposalFixture();final OrganizationProposalAuthorization authorization=current.proposals();
        final ConnectionFactory factory=mock(ConnectionFactory.class);final Connection connection=mock(Connection.class);
        final NativeEnrollmentContextRepository contexts=mock(NativeEnrollmentContextRepository.class);
        final NativeEnrollmentProposalControlRepository controls=mock(NativeEnrollmentProposalControlRepository.class);
        final NativeEnrollmentActionRepository actions=mock(NativeEnrollmentActionRepository.class);
        final NativeEnrollmentProposalRepository proposals=mock(NativeEnrollmentProposalRepository.class);
        final NativeEnrollmentRevisionRepository revisions=mock(NativeEnrollmentRevisionRepository.class);
        final AtomicReference<com.fasterxml.jackson.databind.JsonNode> packet=new AtomicReference<>();final List<Object> writes=new ArrayList<>();final R2dbcEntityTemplate operations;final R2dbcTransactionManager manager;
        Fixture(){
            when(factory.getMetadata()).thenReturn(()->"PostgreSQL");when(factory.create()).thenAnswer(i->Mono.just(connection));when(connection.close()).thenReturn(Mono.empty());when(connection.beginTransaction(any(TransactionDefinition.class))).thenReturn(Mono.empty());when(connection.beginTransaction()).thenReturn(Mono.empty());when(connection.commitTransaction()).thenReturn(Mono.empty());when(connection.rollbackTransaction()).thenReturn(Mono.empty());when(connection.setAutoCommit(anyBoolean())).thenReturn(Mono.empty());when(connection.isAutoCommit()).thenReturn(true);when(connection.getTransactionIsolationLevel()).thenReturn(IsolationLevel.READ_COMMITTED);
            operations=spy(new R2dbcEntityTemplate(factory));manager=new R2dbcTransactionManager(factory);
            for(Class<?> type:List.of(EnrollmentDecisionRow.class,EnrollmentActionRow.class,EnrollmentReceiptRow.class,EnrollmentAuditRow.class,EnrollmentOutboxRow.class,EnrollmentOutboxDeliveryRow.class))stubInsert(type);
            when(contexts.beginMutation(any(),anyString())).thenAnswer(i->{packet.set(JSON.readTree((String)i.getArgument(1)));return Mono.just(i.getArgument(0));});
            when(contexts.lockContext(any())).thenAnswer(i->{var p=packet.get();return Mono.just(new EnrollmentNativeContextStamp(i.getArgument(0),current.now.get(),p.get("target").get("authorityUntilEpochMillis").longValue(),p.get("target").get("commandUntilEpochMillis").longValue(),p.get("contextDigest").textValue()));});
            when(contexts.contextCurrent(any())).thenReturn(Mono.just(true));when(contexts.validateComplete(any())).thenReturn(Mono.just(true));when(controls.compareAndSet(any(),any())).thenReturn(Mono.just(1L));when(actions.findById(any())).thenReturn(Mono.empty());
            when(proposals.findById(any())).thenAnswer(i->{var target=BankEnrollmentIntentCodec.decode(bytes(intent())).request();return Mono.just(new EnrollmentProposalRow(i.getArgument(0),UUID.fromString(BANK),UUID.fromString(INSTITUTION),UUID.fromString(ISSUER),"ENROLL",UUID.fromString(ENROLLMENT),"STAFF",IDP,"retained-maker",UUID.fromString(PLATFORM),REALM,UUID.fromString(MAKER),null,UUID.randomUUID(),RECEIVED));});
            when(revisions.findById(any())).thenAnswer(i->{var full=BankEnrollmentRevisionCodec.decode(packet.get().get("target").get("originalRevisionJson").textValue().getBytes(java.nio.charset.StandardCharsets.UTF_8));var t=BankEnrollmentRevisionCodec.enrollmentTarget(full.request()).request();return Mono.just(new EnrollmentProposalRevisionRow(i.getArgument(0),"ENROLL",UUID.fromString(t.actionId()),7,0,0,null,t.commandDeadlineEpochMillis(),List.of("ACCOUNTING_BOOK"),full.originalJson().getBytes(java.nio.charset.StandardCharsets.UTF_8),ProposalTerminationEvidenceCodec.REVISION_ENCODING,full.canonicalIntent(),full.intentDigest(),RECEIVED));});
            when(controls.findById(any())).thenAnswer(i->{var target=packet.get().get("target");var operation=target.get("operation").textValue();boolean submitted=operation.equals("REJECT");UUID previous=UUID.fromString("00000000-0000-0000-0000-000000000306");return Mono.just(new EnrollmentProposalControlRow(new EnrollmentProposalKey(UUID.fromString(TENANT),UUID.fromString(EnrollmentProposalCommandCodecTest.PROPOSAL)),submitted?"SUBMITTED":"APPROVED",1,2,submitted?null:previous,submitted?null:RECEIVED+15000,previous));});
        }
        @SuppressWarnings({"rawtypes","unchecked"}) void stubInsert(Class<?> type){doAnswer(i->Mono.defer(()->{Object entity=i.getArgument(0);writes.add(entity);return Mono.just(entity);})).when(operations).insert(isA((Class)type));}
        BankEnrollmentProposalTerminationServiceImpl service(){return new BankEnrollmentProposalTerminationServiceImpl(authorization,contexts,proposals,revisions,controls,actions,operations,manager);}
        OrganizationProposalAuthorization.Admission admission(EnrollmentProposalCommand.Operation operation,boolean machine)throws Exception {
            if(machine){var claims=new HashMap<>(current.caller.get().claims());claims.remove("org_staff");claims.put("org_kind","service");claims.put("org_client","actual-machine");current.caller.set(current.caller.get().toBuilder().claims(claims).build());}
            else if(operation!=EnrollmentProposalCommand.Operation.REJECT)current.caller.set(principal(PLATFORM,Map.of("org_staff",MAKER)).toBuilder().subject("retained-maker").build());
            var state=operation==EnrollmentProposalCommand.Operation.REJECT?BankEnrollmentLifecycle.ProposalState.SUBMITTED:BankEnrollmentLifecycle.ProposalState.APPROVED;var scope=scope(state);var body=JSON.readTree(request(operation));((com.fasterxml.jackson.databind.node.ObjectNode)body).put("commandNotAfterEpochMillis",current.now.get()+5000);var request=bytes(body);var d=authorization.discover(operation,TENANT,BANK,EnrollmentProposalCommandCodecTest.PROPOSAL,1,request).block();return authorization.promote(d,scope).block();
        }
    }
    @Test void actualMachineCancellationWritesCompleteMappedGraphWithoutInventingStaff()throws Exception {
        var f=new Fixture();var a=f.admission(EnrollmentProposalCommand.Operation.CANCEL_EMERGENCY,true);var result=f.service().terminate(a).block();assertNotNull(result);assertEquals("CANCELLED",result.state());assertEquals(3,result.controlVersion());assertEquals(5,f.writes.size());
        var action=f.writes.stream().filter(EnrollmentActionRow.class::isInstance).map(EnrollmentActionRow.class::cast).findFirst().orElseThrow();assertEquals("MACHINE",action.resolverKind());assertNull(action.resolverStaffId());assertEquals("actual-machine",action.resolverClientId());assertEquals("APPLIED",action.outcome());
        assertEquals(1,f.writes.stream().filter(EnrollmentReceiptRow.class::isInstance).count());assertEquals(1,f.writes.stream().filter(EnrollmentAuditRow.class::isInstance).count());assertEquals(1,f.writes.stream().filter(EnrollmentOutboxRow.class::isInstance).count());assertEquals(1,f.writes.stream().filter(EnrollmentOutboxDeliveryRow.class::isInstance).count());verify(f.connection).commitTransaction();verify(f.connection,never()).rollbackTransaction();
    }
    @Test void rejectionRecordsTheActualDistinctCheckerAndWithdrawalRecordsTheMaker()throws Exception {
        for(var op:List.of(EnrollmentProposalCommand.Operation.REJECT,EnrollmentProposalCommand.Operation.WITHDRAW)){
            var f=new Fixture();var a=f.admission(op,false);var result=f.service().terminate(a).block();assertEquals(op==EnrollmentProposalCommand.Operation.REJECT?"REJECTED":"WITHDRAWN",result.state());var decision=f.writes.stream().filter(EnrollmentDecisionRow.class::isInstance).map(EnrollmentDecisionRow.class::cast).findFirst().orElseThrow();assertEquals(UUID.fromString(op==EnrollmentProposalCommand.Operation.REJECT?STAFF:MAKER),decision.decisionActorStaffId());assertEquals(op.name(),decision.decision());assertNull(decision.approvalUntilMs());assertEquals(6,f.writes.size());
        }
    }
    @Test void staleControlRollsBackBeforeAnyEvidenceInsertAndFinalGraphFailureCannotReturnSuccess()throws Exception {
        var stale=new Fixture();var a=stale.admission(EnrollmentProposalCommand.Operation.CANCEL,false);when(stale.controls.compareAndSet(any(),any())).thenReturn(Mono.just(0L));assertThrows(org.springframework.web.server.ResponseStatusException.class,()->stale.service().terminate(a).block());assertTrue(stale.writes.isEmpty());verify(stale.connection).rollbackTransaction();verify(stale.connection,never()).commitTransaction();
        var broken=new Fixture();var b=broken.admission(EnrollmentProposalCommand.Operation.CANCEL,false);when(broken.contexts.validateComplete(any())).thenReturn(Mono.just(false));assertThrows(org.springframework.web.server.ResponseStatusException.class,()->broken.service().terminate(b).block());verify(broken.connection).rollbackTransaction();verify(broken.connection,never()).commitTransaction();
    }
    @Test void currentPermissionWithdrawalStopsBeforeNativeAdmissionAndDifferentFactoryIsRejected()throws Exception {
        var f=new Fixture();var a=f.admission(EnrollmentProposalCommand.Operation.CANCEL,false);f.current.policy.set(org.fireflyframework.security.api.domain.Decision.deny("withdrawn"));denied(()->f.service().terminate(a).block());verify(f.contexts,never()).beginMutation(any(),anyString());assertTrue(f.writes.isEmpty());
        var other=mock(ConnectionFactory.class);when(other.getMetadata()).thenReturn(()->"PostgreSQL");assertThrows(IllegalStateException.class,()->new BankEnrollmentProposalTerminationServiceImpl(f.authorization,f.contexts,f.proposals,f.revisions,f.controls,f.actions,f.operations,new R2dbcTransactionManager(other)));
    }
    @Test void expiredRetainedTargetCanBeCancelledByFreshMachineAuthorityWithoutReadingMutableBank()throws Exception {
        var f=new Fixture();f.current.now.set(RECEIVED+30000);var admission=f.admission(EnrollmentProposalCommand.Operation.CANCEL_EMERGENCY,true);var result=f.service().terminate(admission).block();assertEquals("CANCELLED",result.state());assertEquals(RECEIVED+30000,result.recordedAtEpochMillis());verify(f.connection).commitTransaction();
    }
    @Test void mismatchedImmutableRevisionRollsBackBeforeControlCasOrEvidenceWrites()throws Exception {
        var f=new Fixture();var admission=f.admission(EnrollmentProposalCommand.Operation.CANCEL,false);var full=admission.scope().target();var target=BankEnrollmentRevisionCodec.enrollmentTarget(full.request()).request();
        var wrong=new EnrollmentProposalRevisionRow(new EnrollmentProposalRevisionKey(UUID.fromString(TENANT),UUID.fromString(EnrollmentProposalCommandCodecTest.PROPOSAL),1L),"ENROLL",UUID.fromString(target.actionId()),7,0,0,null,target.commandDeadlineEpochMillis(),List.of("ACCOUNTING_BOOK"),full.originalJson().getBytes(java.nio.charset.StandardCharsets.UTF_8),ProposalTerminationEvidenceCodec.REVISION_ENCODING,"wrong-retained-canonical",full.intentDigest(),RECEIVED);
        doReturn(Mono.just(wrong)).when(f.revisions).findById(any());assertThrows(org.springframework.web.server.ResponseStatusException.class,()->f.service().terminate(admission).block());verify(f.controls,never()).compareAndSet(any(),any());assertTrue(f.writes.isEmpty());verify(f.connection).rollbackTransaction();
    }
    @Test void actualStaffClientMetadataDoesNotInvalidateRetainedMakerOrBecomeTheHistoricalClient()throws Exception {
        var f=new Fixture();f.current.caller.set(principal(PLATFORM,Map.of("org_staff",MAKER,"org_client","current-staff-console")).toBuilder().subject("retained-maker").build());
        var known=scope(BankEnrollmentLifecycle.ProposalState.APPROVED);var body=request(EnrollmentProposalCommand.Operation.CANCEL);var discovery=f.authorization.discover(EnrollmentProposalCommand.Operation.CANCEL,TENANT,BANK,known.proposalId(),1,body).block();var admission=f.authorization.promote(discovery,known).block();
        doAnswer(i->Mono.just(new EnrollmentProposalRow(i.getArgument(0),UUID.fromString(BANK),UUID.fromString(INSTITUTION),UUID.fromString(ISSUER),"ENROLL",UUID.fromString(ENROLLMENT),"STAFF",IDP,"retained-maker",UUID.fromString(PLATFORM),REALM,UUID.fromString(MAKER),"historical-maker-console",UUID.randomUUID(),RECEIVED))).when(f.proposals).findById(any());
        var result=f.service().terminate(admission).block();assertEquals("CANCELLED",result.state());var action=f.writes.stream().filter(EnrollmentActionRow.class::isInstance).map(EnrollmentActionRow.class::cast).findFirst().orElseThrow();assertEquals("STAFF",action.resolverKind());assertEquals(UUID.fromString(MAKER),action.resolverStaffId());assertEquals("current-staff-console",action.resolverClientId());assertNotEquals("historical-maker-console",action.resolverClientId());
    }
}
