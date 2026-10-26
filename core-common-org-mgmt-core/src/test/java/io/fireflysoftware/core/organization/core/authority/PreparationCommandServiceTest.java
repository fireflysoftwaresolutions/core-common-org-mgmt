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

import io.fireflysoftware.core.organization.core.services.authority.impl.BankEnrollmentPreparationCommandServiceImpl;
import io.fireflysoftware.core.organization.interfaces.authority.*;
import io.fireflysoftware.core.organization.models.entities.Bank;
import io.fireflysoftware.core.organization.models.repositories.BankRepository;
import io.fireflysoftware.core.organization.models.repositories.authority.*;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import io.r2dbc.spi.ConnectionFactory;
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

class PreparationCommandServiceTest {
    static class Fixture extends PreparationCaptureServiceTest.Fixture {
        Fixture(){stubInsert(EnrollmentProposalRow.class);stubInsert(EnrollmentProposalRevisionRow.class);stubInsert(EnrollmentProposalControlRow.class);when(preparations.beginPreparation(any(),anyString())).thenAnswer(i->{packet.set(JSON.readTree((String)i.getArgument(1)));return Mono.just(i.getArgument(0));});}
        OrganizationPreparationAuthorization.Prepared prepared()throws Exception {var discovery=discovery();var captured=preparation.bindCapture(discovery,new BankEnrollmentPreparationPlan.LocalSnapshot(BANK,7,0,9,null),RECEIVED+20000).block();return preparation.admitPrepared(captured,BankEnrollmentPreparationPlan.build(discovery.desired(),captured.local(),BankEnrollmentPreparationPlanTest.allocation(),BankEnrollmentPreparationPlanTest.evidence(),RECEIVED)).block();}
        BankEnrollmentPreparationCommandServiceImpl writer(){return new BankEnrollmentPreparationCommandServiceImpl(preparation,preparations,contexts,actions,operations,manager);}
    }
    @Test void actualMappedPrepareRecordsDraftFullRevisionAndCompleteImmutableEvidenceGraph()throws Exception {var f=new Fixture();f.current.caller.set(principal(PLATFORM,Map.of("org_client","current-prepare-console")));var a=f.prepared();var result=f.writer().prepare(a).block();assertNotNull(result);assertEquals("PREPARE",result.outcome().operation());assertEquals("DRAFT",result.outcome().state());assertEquals(0,result.outcome().controlVersion());assertEquals(a.plan().revision().intentDigest(),result.revision().digest());assertEquals(a.plan().revision().originalJson(),result.revision().originalJson());assertEquals(8,f.writes.size());var header=f.writes.stream().filter(EnrollmentProposalRow.class::isInstance).map(EnrollmentProposalRow.class::cast).findFirst().orElseThrow();assertEquals("STAFF",header.makerKind());assertEquals(UUID.fromString(STAFF),header.makerStaffId());assertEquals(UUID.fromString(PLATFORM),header.makerTenantId());assertEquals("current-prepare-console",header.makerClientId());var control=f.writes.stream().filter(EnrollmentProposalControlRow.class::isInstance).map(EnrollmentProposalControlRow.class::cast).findFirst().orElseThrow();assertEquals("DRAFT",control.state());assertEquals(0,control.controlVersion());assertNull(control.approvalActionId());assertNull(control.approvalUntilMs());assertEquals(UUID.fromString(a.plan().desired().request().actionId()),control.lastActionId());for(var type:List.of(EnrollmentActionRow.class,EnrollmentReceiptRow.class,EnrollmentAuditRow.class,EnrollmentOutboxRow.class,EnrollmentOutboxDeliveryRow.class))assertEquals(1,f.writes.stream().filter(type::isInstance).count());verify(f.connection).commitTransaction();verify(f.contexts).validateComplete(any());}
    @Test void currentPreparedAuthorityWithdrawalPreventsAnyNativeOrMappedWrite()throws Exception {var f=new Fixture();var a=f.prepared();f.current.policy.set(org.fireflyframework.security.api.domain.Decision.deny("withdrawn"));denied(()->f.writer().prepare(a).block());verify(f.preparations,never()).beginPreparation(any(),anyString());assertTrue(f.writes.isEmpty());}
    @Test void existingActionRequiresSeparatelyAuthorizedHistoryAndDoesNotReturnAnInventedReplay()throws Exception {var f=new Fixture();var a=f.prepared();doReturn(Mono.just(mock(EnrollmentActionRow.class))).when(f.actions).findById(any());var error=assertThrows(ResponseStatusException.class,()->f.writer().prepare(a).block());assertEquals(409,error.getStatusCode().value());assertTrue(error.getReason().contains("independently authorized action read"));assertTrue(f.writes.isEmpty());verify(f.connection).rollbackTransaction();}
    @Test void finalNativeCompletenessFailureRollsBackEveryMappedCreation()throws Exception {var f=new Fixture();var a=f.prepared();when(f.contexts.validateComplete(any())).thenReturn(Mono.just(false));assertThrows(ResponseStatusException.class,()->f.writer().prepare(a).block());assertEquals(8,f.writes.size());verify(f.connection).rollbackTransaction();verify(f.connection,never()).commitTransaction();}
    @Test void outboxInsertFailureCannotReturnSuccessOrCommitAnIncompletePrepare()throws Exception {var f=new Fixture();var a=f.prepared();var validated=new java.util.concurrent.atomic.AtomicBoolean();when(f.contexts.validateComplete(any())).thenReturn(Mono.fromSupplier(()->{validated.set(true);return true;}));doReturn(Mono.error(new IllegalStateException("owned persistence fixture failure"))).when(f.operations).insert(isA(EnrollmentOutboxRow.class));assertThrows(IllegalStateException.class,()->f.writer().prepare(a).block());verify(f.connection).rollbackTransaction();verify(f.connection,never()).commitTransaction();assertFalse(validated.get());}
    @Test void postWaitDatabaseStampCannotOutliveTheObservedGrantEvidence()throws Exception {var f=new Fixture();var a=f.prepared();doAnswer(i->{var p=f.packet.get();return Mono.just(new EnrollmentNativeContextStamp(i.getArgument(0),a.validUntilEpochMillis(),a.validUntilEpochMillis()+1000,a.validUntilEpochMillis()+1000,p.get("contextDigest").textValue()));}).when(f.contexts).lockContext(any());assertThrows(ResponseStatusException.class,()->f.writer().prepare(a).block());assertTrue(f.writes.isEmpty());verify(f.connection).rollbackTransaction();}
    @Test void preparationWriterRequiresExactFactoryAndOwnerAdmission()throws Exception {var f=new Fixture();var other=mock(ConnectionFactory.class);when(other.getMetadata()).thenReturn(()->"PostgreSQL");assertThrows(IllegalStateException.class,()->new BankEnrollmentPreparationCommandServiceImpl(f.preparation,f.preparations,f.contexts,f.actions,f.operations,new R2dbcTransactionManager(other)));var foreign=new Fixture();var a=foreign.prepared();denied(()->f.writer().prepare(a).block());verify(f.preparations,never()).beginPreparation(any(),anyString());}
    static EnrollmentActionRow requestOriginal(EnrollmentActionRow a,byte[] request){return new EnrollmentActionRow(a.id(),a.bankId(),a.intendedOperation(),a.intendedRoute(),request,a.requestEncoding(),a.requestCanonical(),a.requestDigest(),a.resolutionKind(),request,a.resolutionEncoding(),a.resolutionCanonical(),a.resolutionDigest(),a.resolverKind(),a.resolverIssuer(),a.resolverSubject(),a.resolverTenantId(),a.resolverStaffRealm(),a.resolverStaffId(),a.resolverClientId(),a.proposalId(),a.proposalRevision(),a.targetDigest(),a.outcome(),a.recordedAtMs());}
    @Test void retainedPrepareHistoryRevalidatesTheFullClosedOriginalCommandAgainstItsRevision()throws Exception {var f=new Fixture();var admission=f.prepared();f.writer().prepare(admission).block();var action=f.writes.stream().filter(EnrollmentActionRow.class::isInstance).map(EnrollmentActionRow.class::cast).findFirst().orElseThrow();var receipt=f.writes.stream().filter(EnrollmentReceiptRow.class::isInstance).map(EnrollmentReceiptRow.class::cast).findFirst().orElseThrow();var revision=f.writes.stream().filter(EnrollmentProposalRevisionRow.class::isInstance).map(EnrollmentProposalRevisionRow.class::cast).findFirst().orElseThrow();assertEquals("APPLIED",EnrollmentActionHistoryCodec.history(action,receipt,revision).outcome());
        var outer=JSON.readTree(action.requestOriginal());((com.fasterxml.jackson.databind.node.ObjectNode)outer).put("tenantId",PLATFORM);assertThrows(IllegalArgumentException.class,()->EnrollmentActionHistoryCodec.history(requestOriginal(action,bytes(outer)),receipt,revision));
    }

    @Test void mappedTransferDraftRetainsTheCapturedPreviousOwnerControlVersion()throws Exception {var f=new Fixture();var prior=f.currentOwner("ACTIVE");var discovery=f.discovery(BankEnrollmentPreparationRequest.Mode.TRANSFER);var owner=new BankEnrollmentOwnerReference(prior.tenantId().toString(),INSTITUTION,ISSUER,prior.enrollmentId().toString(),1,5);var local=new BankEnrollmentPreparationPlan.LocalSnapshot(BANK,8,2,9,owner);var captured=f.preparation.bindCapture(discovery,local,RECEIVED+20000).block();var admission=f.preparation.admitPrepared(captured,BankEnrollmentPreparationPlan.build(discovery.desired(),local,new BankEnrollmentPreparationPlan.Allocation(BankEnrollmentPreparationPlanTest.allocation().proposalId(),"00000000-0000-0000-0000-000000000714",BankEnrollmentPreparationPlanTest.allocation().executionActionId()),BankEnrollmentPreparationPlanTest.evidence(),RECEIVED)).block();var result=f.writer().prepare(admission).block();assertEquals("TRANSFER_PREPARE",result.outcome().operation());var header=f.writes.stream().filter(EnrollmentProposalRow.class::isInstance).map(EnrollmentProposalRow.class::cast).findFirst().orElseThrow();var revision=f.writes.stream().filter(EnrollmentProposalRevisionRow.class::isInstance).map(EnrollmentProposalRevisionRow.class::cast).findFirst().orElseThrow();var control=f.writes.stream().filter(EnrollmentProposalControlRow.class::isInstance).map(EnrollmentProposalControlRow.class::cast).findFirst().orElseThrow();assertEquals(5L,revision.expectedControlVersion());assertDoesNotThrow(()->RetainedEnrollmentProposal.scope(UUID.fromString(TENANT),UUID.fromString(BANK),header.id().proposalId(),1,header,revision,control));}

}
