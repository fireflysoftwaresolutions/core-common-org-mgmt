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

import io.fireflysoftware.core.organization.core.services.authority.impl.BankEnrollmentActionReadServiceImpl;
import io.fireflysoftware.core.organization.models.entities.authority.*;
import io.fireflysoftware.core.organization.models.repositories.authority.*;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import java.util.*;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static io.fireflysoftware.core.organization.core.authority.ConfigurationGrantObservationVerifierTest.*;
import static io.fireflysoftware.core.organization.core.authority.OrganizationEnrollmentAuthorizationTest.*;
import static io.fireflysoftware.core.organization.core.authority.OrganizationProposalAuthorizationTest.*;

class EnrollmentActionReadServiceTest {
    static class Fixture {
        final ProposalTerminationServiceTest.Fixture writer=new ProposalTerminationServiceTest.Fixture();
        final NativeEnrollmentReceiptRepository receipts=mock(NativeEnrollmentReceiptRepository.class);
        final EnrollmentActionRow action;final EnrollmentReceiptRow receipt;final int written;
        Fixture()throws Exception {
            var admission=writer.admission(io.fireflysoftware.core.organization.interfaces.authority.EnrollmentProposalCommand.Operation.CANCEL_EMERGENCY,true);writer.service().terminate(admission).block();
            action=writer.writes.stream().filter(EnrollmentActionRow.class::isInstance).map(EnrollmentActionRow.class::cast).findFirst().orElseThrow();receipt=writer.writes.stream().filter(EnrollmentReceiptRow.class::isInstance).map(EnrollmentReceiptRow.class::cast).findFirst().orElseThrow();written=writer.writes.size();
            var full=admission.scope().target();var t=BankEnrollmentRevisionCodec.enrollmentTarget(full.request()).request();
            var row=new EnrollmentProposalRevisionRow(new EnrollmentProposalRevisionKey(UUID.fromString(TENANT),UUID.fromString(EnrollmentProposalCommandCodecTest.PROPOSAL),1L),"ENROLL",UUID.fromString(t.actionId()),7,0,0,null,t.commandDeadlineEpochMillis(),List.of("ACCOUNTING_BOOK"),full.originalJson().getBytes(java.nio.charset.StandardCharsets.UTF_8),ProposalTerminationEvidenceCodec.REVISION_ENCODING,full.canonicalIntent(),full.intentDigest(),RECEIVED);
            doReturn(Mono.just(action)).when(writer.actions).findById(any());doReturn(Mono.just(row)).when(writer.revisions).findById(any());when(receipts.findById(any())).thenReturn(Mono.just(receipt));
            when(writer.contexts.beginRead(any(),anyString())).thenAnswer(i->{writer.packet.set(JSON.readTree((String)i.getArgument(1)));return Mono.just(i.getArgument(0));});
            writer.current.now.set(RECEIVED+70000);writer.current.caller.set(principal(PLATFORM,Map.of("exp",Instant.ofEpochMilli(RECEIVED+180000))).toBuilder().subject("fresh-history-reader").build());
        }
        OrganizationEnrollmentHistoryAuthorization authorization(){return new OrganizationEnrollmentHistoryAuthorization(()->Mono.justOrEmpty(writer.current.caller.get()),(p,a,r,c)->{writer.current.permission=a;writer.current.calls.incrementAndGet();return Mono.justOrEmpty(writer.current.policy.get());},writer.current.clock,PLATFORM,Map.of(IDP,new OrganizationActorIssuerProfile(IDP,"tenant_id","org_kind","org_staff","org_client","employee","service")),Map.of(IDP,REALM));}
        BankEnrollmentActionReadServiceImpl service(){return new BankEnrollmentActionReadServiceImpl(authorization(),writer.contexts,writer.actions,receipts,writer.revisions,writer.operations,writer.manager);}
    }
    @Test void freshReadAuthorityReturnsActualRetainedActionReceiptAfterTerminalTargetAndOldActorExpire()throws Exception {
        var f=new Fixture();var value=f.service().read(UUID.fromString(TENANT),UUID.fromString(BANK),f.action.id().actionId()).block();assertNotNull(value);assertEquals("APPLIED",value.outcome());assertEquals("CANCEL_EMERGENCY",value.intendedOperation());assertEquals("MACHINE",value.resolvedBy().kind());assertEquals("organization.bank.enrollment.action.read",f.writer.current.permission);assertEquals(f.receipt.resultCanonical(),value.receipt().canonical());assertEquals(f.receipt.resultDigest(),value.receipt().digest());assertNotNull(value.approvedRevision());assertEquals(f.written,f.writer.writes.size());
    }
    @Test void absenceIsOnlyNotFoundAndNeverAStoredNoEffectOutcome()throws Exception {
        var f=new Fixture();doReturn(Mono.empty()).when(f.writer.actions).findById(any());var error=assertThrows(org.springframework.web.server.ResponseStatusException.class,()->f.service().read(UUID.fromString(TENANT),UUID.fromString(BANK),f.action.id().actionId()).block());assertEquals(404,error.getStatusCode().value());assertEquals(f.written,f.writer.writes.size());
    }
    @Test void independentReadPermissionAndNonnullProjectionAreRequiredBeforeReadContext()throws Exception {
        var f=new Fixture();f.writer.current.policy.set(org.fireflyframework.security.api.domain.Decision.deny("read-withdrawn"));denied(()->f.service().read(UUID.fromString(TENANT),UUID.fromString(BANK),f.action.id().actionId()).block());verify(f.writer.contexts,never()).beginRead(any(),anyString());
        f.writer.current.policy.set(org.fireflyframework.security.api.domain.Decision.permit());f.writer.current.caller.set(f.writer.current.caller.get().toBuilder().tenantId(null).build());denied(()->f.service().read(UUID.fromString(TENANT),UUID.fromString(BANK),f.action.id().actionId()).block());verify(f.writer.contexts,never()).beginRead(any(),anyString());
    }
    @Test void readRechecksCurrentAuthorityAfterIoAndRejectsInconsistentReceipt()throws Exception {
        var f=new Fixture();doAnswer(i->{f.writer.current.policy.set(org.fireflyframework.security.api.domain.Decision.deny("withdrawn-after-read"));return Mono.just(f.receipt);}).when(f.receipts).findById(any());denied(()->f.service().read(UUID.fromString(TENANT),UUID.fromString(BANK),f.action.id().actionId()).block());
        var corrupt=new Fixture();var r=corrupt.receipt;doReturn(Mono.just(new EnrollmentReceiptRow(r.id(),r.receiptId(),r.resultOriginal(),r.resultEncoding(),r.resultCanonical(),"a".repeat(64),r.recordedAtMs()))).when(corrupt.receipts).findById(any());assertThrows(org.springframework.web.server.ResponseStatusException.class,()->corrupt.service().read(UUID.fromString(TENANT),UUID.fromString(BANK),corrupt.action.id().actionId()).block());assertEquals(corrupt.written,corrupt.writer.writes.size());
    }
    @Test void selfConsistentReceiptForAnotherTargetCannotBePresentedAsThisAction()throws Exception {
        var f=new Fixture();var original=JSON.readTree(f.receipt.resultOriginal());original=((com.fasterxml.jackson.databind.node.ObjectNode)original).put("targetDigest","b".repeat(64));
        var value=AuthorityWire.record(original,io.fireflysoftware.core.organization.interfaces.authority.EnrollmentProposalCommandResult.class,Set.of());var encoded=ProposalTerminationEvidenceCodec.result(value);var row=new EnrollmentReceiptRow(f.receipt.id(),f.receipt.receiptId(),encoded.originalJson().getBytes(java.nio.charset.StandardCharsets.UTF_8),encoded.encoding(),encoded.canonical(),encoded.digest(),f.receipt.recordedAtMs());
        doReturn(Mono.just(row)).when(f.receipts).findById(any());assertThrows(org.springframework.web.server.ResponseStatusException.class,()->f.service().read(UUID.fromString(TENANT),UUID.fromString(BANK),f.action.id().actionId()).block());
    }
}
