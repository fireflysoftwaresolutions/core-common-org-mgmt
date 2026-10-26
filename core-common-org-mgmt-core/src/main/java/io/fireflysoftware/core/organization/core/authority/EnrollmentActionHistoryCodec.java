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

import io.fireflysoftware.core.organization.interfaces.authority.EnrollmentActionHistory;
import io.fireflysoftware.core.organization.interfaces.authority.EnrollmentProposalCommandResult;
import io.fireflysoftware.core.organization.models.entities.authority.*;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.Objects;
import java.util.UUID;

public final class EnrollmentActionHistoryCodec {
    public static final String CONTEXT_ENCODING="FF_ORGANIZATION_BANK_ACTION_READ_CONTEXT_V2";
    private EnrollmentActionHistoryCodec() { }
    public record ReadContext(int schemaVersion,String nonce,String purpose,String operation,String permission,
            String tenantId,String bankId,String actionId,ProposalTerminationEvidenceCodec.Actor actor,
            long authorityUntilEpochMillis,long commandUntilEpochMillis,String canonicalReadRequest,String readRequestDigest) { }
    public record ContextPacket(ReadContext target,String contextEncoding,String canonicalContext,String contextDigest) { }
    public static ContextPacket context(OrganizationEnrollmentHistoryAuthorization.Admission admission,UUID nonce) {
        admission.requireCurrent();AuthorityWire.uuid(nonce.toString());var scope=admission.scope();var actor=admission.actor();
        var snapshot=new ProposalTerminationEvidenceCodec.Actor(actor.kind(),actor.issuer(),actor.subject(),actor.tenantId(),admission.staffRealm(),actor.staffActorId(),actor.clientId(),actor.expiresAtEpochMillis());
        var target=new ReadContext(2,nonce.toString(),"ACTION_READ","ACTION_READ",OrganizationEnrollmentHistoryAuthorization.PERMISSION,
                scope.tenantId(),scope.bankId(),scope.actionId(),snapshot,admission.validUntilEpochMillis(),admission.validUntilEpochMillis(),scope.canonicalRequest(),scope.requestDigest());
        String canonical=AuthorityWire.token(CONTEXT_ENCODING)+AuthorityWire.token(target);
        return new ContextPacket(target,CONTEXT_ENCODING,canonical,AuthorityWire.sha(canonical));
    }
    public static EnrollmentActionHistory history(EnrollmentActionRow action,EnrollmentReceiptRow receipt,EnrollmentProposalRevisionRow revision) {
        if(!action.id().equals(receipt.id()) || action.recordedAtMs()!=receipt.recordedAtMs()
                || !Set.of("APPLIED","NO_EFFECT_FINAL").contains(action.outcome()))throw AuthorityWire.bad();
        var request=payload(action.requestEncoding(),action.requestOriginal(),action.requestCanonical(),action.requestDigest());
        var resolution=payload(action.resolutionEncoding(),action.resolutionOriginal(),action.resolutionCanonical(),action.resolutionDigest());
        var result=payload(receipt.resultEncoding(),receipt.resultOriginal(),receipt.resultCanonical(),receipt.resultDigest());
        EnrollmentActionHistory.Payload approved=null;
        if(action.proposalId()!=null || action.proposalRevision()!=null || action.targetDigest()!=null) {
            if(revision==null || action.proposalId()==null || action.proposalRevision()==null || action.targetDigest()==null
                    || !revision.id().tenantId().equals(action.id().tenantId()) || !revision.id().proposalId().equals(action.proposalId())
                    || !revision.id().revision().equals(action.proposalRevision()) || !revision.revisionDigest().equals(action.targetDigest()))throw AuthorityWire.bad();
            approved=payload(revision.revisionEncoding(),revision.revisionOriginal(),revision.revisionCanonical(),revision.revisionDigest());
            if(ProposalTerminationEvidenceCodec.REVISION_ENCODING.equals(approved.encoding())) {
                var decoded=BankEnrollmentRevisionCodec.decode(revision.revisionOriginal());
                var target=BankEnrollmentRevisionCodec.enrollmentTarget(decoded.request()).request();
                if(!decoded.canonicalIntent().equals(approved.canonical()) || !decoded.intentDigest().equals(approved.digest())
                        || !target.destinationTenantId().equals(action.id().tenantId().toString()) || !target.bankId().equals(action.bankId().toString()))throw AuthorityWire.bad();
                if("APPLIED".equals(action.outcome()) && PreparationWriteEvidenceCodec.COMMAND_ENCODING.equals(request.encoding())) {
                    var command=PreparationWriteEvidenceCodec.retainedCommand(action.id().tenantId().toString(),action.bankId().toString(),action.id().actionId().toString(),action.intendedOperation(),action.intendedRoute(),action.recordedAtMs(),action.requestOriginal(),decoded);
                    if(!command.canonical().equals(request.canonical()) || !command.digest().equals(request.digest()))throw AuthorityWire.bad();
                }
                if("APPLIED".equals(action.outcome()) && ProposalTerminationEvidenceCodec.COMMAND_ENCODING.equals(request.encoding())) {
                    var command=EnrollmentProposalCommandCodec.decodeRevision(action.id().tenantId().toString(),action.bankId().toString(),action.proposalId().toString(),
                            io.fireflysoftware.core.organization.interfaces.authority.EnrollmentProposalCommand.Operation.valueOf(action.intendedOperation()),
                            action.proposalRevision(),decoded,action.recordedAtMs(),action.requestOriginal());
                    if(!command.canonicalIntent().equals(request.canonical()) || !command.intentDigest().equals(request.digest())
                            || !command.request().actionId().equals(action.id().actionId().toString()))throw AuthorityWire.bad();
                }
            }
        }else if(revision!=null)throw AuthorityWire.bad();
        if(ProposalTerminationEvidenceCodec.RESULT_ENCODING.equals(result.encoding())) {
            var value=AuthorityWire.record(AuthorityWire.parse(result.originalJson()),EnrollmentProposalCommandResult.class,Set.of());
            var decoded=ProposalTerminationEvidenceCodec.result(value);
            if(PreparationWriteEvidenceCodec.COMMAND_ENCODING.equals(request.encoding()) && (!"DRAFT".equals(value.state()) || value.controlVersion()!=0 || value.revision()!=1))throw AuthorityWire.bad();
            if(!decoded.canonical().equals(result.canonical()) || !decoded.digest().equals(result.digest())
                    || !value.actionId().equals(action.id().actionId().toString()) || !value.tenantId().equals(action.id().tenantId().toString())
                    || !value.bankId().equals(action.bankId().toString()) || !value.receiptId().equals(receipt.receiptId().toString())
                    || value.schemaVersion()!=2 || !"APPLIED".equals(action.outcome()) || !"COMMAND".equals(action.resolutionKind())
                    || !Objects.equals(value.targetDigest(),action.targetDigest()) || !Objects.equals(value.proposalId(),text(action.proposalId()))
                    || action.proposalRevision()==null || value.revision()!=action.proposalRevision()
                    || !request.encoding().equals(resolution.encoding()) || !request.canonical().equals(resolution.canonical()) || !request.digest().equals(resolution.digest())
                    || !value.operation().equals(action.intendedOperation()) || value.recordedAtEpochMillis()!=action.recordedAtMs())throw AuthorityWire.bad();
        }
        var resolver=new EnrollmentActionHistory.Actor(action.resolverKind(),action.resolverIssuer(),action.resolverSubject(),
                action.resolverTenantId().toString(),action.resolverStaffRealm(),text(action.resolverStaffId()),action.resolverClientId());
        return new EnrollmentActionHistory(2,action.id().tenantId().toString(),action.bankId().toString(),action.id().actionId().toString(),
                action.outcome(),action.intendedOperation(),action.intendedRoute(),action.resolutionKind(),request,resolution,result,approved,resolver,action.recordedAtMs());
    }
    private static EnrollmentActionHistory.Payload payload(String encoding,byte[] original,String canonical,String digest) {
        AuthorityWire.text(encoding,80);AuthorityWire.digest(digest);if(canonical==null || !AuthorityWire.sha(canonical).equals(digest))throw AuthorityWire.bad();
        String json=AuthorityWire.original(original,4194304);AuthorityWire.parse(json);return new EnrollmentActionHistory.Payload(encoding,json,canonical,digest);
    }
    private static String text(UUID id){return id==null?null:id.toString();}
}
