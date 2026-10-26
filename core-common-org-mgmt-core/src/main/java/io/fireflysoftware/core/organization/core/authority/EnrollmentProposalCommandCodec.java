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

import io.fireflysoftware.core.organization.interfaces.authority.BankEnrollmentTarget;
import io.fireflysoftware.core.organization.interfaces.authority.BankEnrollmentRevision;
import io.fireflysoftware.core.organization.interfaces.authority.EnrollmentProposalCommand;
import java.nio.charset.StandardCharsets;
import java.util.Set;

public final class EnrollmentProposalCommandCodec {
    private EnrollmentProposalCommandCodec() { }
    public static long requestedRevision(byte[] bytes) {
        var request=AuthorityWire.record(AuthorityWire.parse(AuthorityWire.original(bytes,16384)),EnrollmentProposalCommand.class,Set.of("approvalUntilEpochMillis"));
        if(request.expectedRevision()<=0 || request.expectedRevision()==Long.MAX_VALUE)throw AuthorityWire.bad();
        return request.expectedRevision();
    }


    public static OrganizationAuthorityIntent<EnrollmentProposalCommand> decode(String tenantId,
            String bankId, String proposalId, EnrollmentProposalCommand.Operation operation,
            long revision, OrganizationAuthorityIntent<BankEnrollmentTarget> retainedTarget,
            long now, byte[] bytes) {
        scope(tenantId,bankId,proposalId,operation,revision,now);
        if(retainedTarget==null || retainedTarget.originalJson()==null) throw AuthorityWire.bad();
        var target=BankEnrollmentIntentCodec.decode(retainedTarget.originalJson().getBytes(StandardCharsets.UTF_8));
        if(!target.equals(retainedTarget) || !tenantId.equals(target.request().destinationTenantId())
                || !bankId.equals(target.request().bankId())) throw AuthorityWire.bad();
        var request=request(operation,revision,target.intentDigest(),target.request(),now,bytes);
        return encode("FF_ORGANIZATION_ENROLLMENT_PROPOSAL_COMMAND_V2",tenantId,bankId,proposalId,revision,target.canonicalIntent(),request);
    }
    public static OrganizationAuthorityIntent<EnrollmentProposalCommand> decodeRevision(String tenantId,
            String bankId, String proposalId, EnrollmentProposalCommand.Operation operation,
            long revision, OrganizationAuthorityIntent<BankEnrollmentRevision> retainedRevision,
            long now, byte[] bytes) {
        scope(tenantId,bankId,proposalId,operation,revision,now);
        if(retainedRevision==null || retainedRevision.originalJson()==null) throw AuthorityWire.bad();
        var full=BankEnrollmentRevisionCodec.decode(retainedRevision.originalJson().getBytes(StandardCharsets.UTF_8));
        var target=BankEnrollmentRevisionCodec.enrollmentTarget(full.request());
        if(!full.equals(retainedRevision) || !tenantId.equals(target.request().destinationTenantId())
                || !bankId.equals(target.request().bankId()) || !proposalId.equals(full.request().proposalId())
                || revision!=full.request().revision()) throw AuthorityWire.bad();
        boolean requiresLiveTarget=operation==EnrollmentProposalCommand.Operation.SUBMIT || operation==EnrollmentProposalCommand.Operation.APPROVE;
        var request=request(operation,revision,full.intentDigest(),target.request(),now,bytes,requiresLiveTarget);
        if(request.request().expectedBankGeneration()!=full.request().expectedBankGeneration()) throw AuthorityWire.bad();
        return encode("FF_ORGANIZATION_BANK_ENROLLMENT_REVISION_COMMAND_V2",tenantId,bankId,proposalId,revision,full.canonicalIntent(),request);
    }
    public static OrganizationAuthorityIntent<EnrollmentProposalCommand> decodeDiscovery(String tenantId,String bankId,
            String proposalId,EnrollmentProposalCommand.Operation operation,long revision,long now,byte[] bytes) {
        scope(tenantId,bankId,proposalId,operation,revision,now);
        var parsed=parse(operation,revision,now,bytes);
        String canonical=AuthorityWire.token("FF_ORGANIZATION_BANK_PROPOSAL_DISCOVERY_V2")
                +AuthorityWire.recordToken(tenantId,bankId,proposalId,revision,parsed.request());
        return new OrganizationAuthorityIntent<>(parsed.originalJson(),parsed.request(),canonical,AuthorityWire.sha(canonical));
    }
    private static void scope(String tenantId,String bankId,String proposalId,EnrollmentProposalCommand.Operation operation,long revision,long now) {
        for(String id:new String[]{tenantId,bankId,proposalId})AuthorityWire.uuid(id);
        if(operation==null || revision<=0 || revision==Long.MAX_VALUE || now<=0)throw AuthorityWire.bad();
    }
    private record DecodedCommand(String originalJson,EnrollmentProposalCommand request) { }
    private static DecodedCommand request(EnrollmentProposalCommand.Operation operation,
            long revision,String targetDigest,BankEnrollmentTarget target,long now,byte[] bytes) {
        return request(operation,revision,targetDigest,target,now,bytes,true);
    }
    private static DecodedCommand request(EnrollmentProposalCommand.Operation operation,long revision,String targetDigest,
            BankEnrollmentTarget target,long now,byte[] bytes,boolean requiresLiveTarget) {
        var parsed=parse(operation,revision,now,bytes);var request=parsed.request();
        if(request.actionId().equals(target.actionId()) || !request.expectedTargetDigest().equals(targetDigest)
                || requiresLiveTarget && (request.commandNotAfterEpochMillis()>target.commandDeadlineEpochMillis()
                || request.approvalUntilEpochMillis()!=null && request.approvalUntilEpochMillis()>target.commandDeadlineEpochMillis()))throw AuthorityWire.bad();
        return parsed;
    }
    private static DecodedCommand parse(EnrollmentProposalCommand.Operation operation,long revision,long now,byte[] bytes) {
        String original=AuthorityWire.original(bytes,16384);
        var request=AuthorityWire.record(AuthorityWire.parse(original),EnrollmentProposalCommand.class,Set.of("approvalUntilEpochMillis"));
        AuthorityWire.uuid(request.actionId());AuthorityWire.digest(request.expectedTargetDigest());AuthorityWire.text(request.reason(),4096);
        if(request.schemaVersion()!=2 || request.operation()!=operation || request.expectedRevision()!=revision
                || request.expectedControlVersion()<0 || request.expectedControlVersion()==Long.MAX_VALUE
                || request.expectedBankGeneration()<0 || request.expectedBankGeneration()==Long.MAX_VALUE
                || request.commandNotAfterEpochMillis()<=now || request.commandNotAfterEpochMillis()>253402300799999L)throw AuthorityWire.bad();
        if(operation==EnrollmentProposalCommand.Operation.APPROVE) {
            if(request.approvalUntilEpochMillis()==null || request.approvalUntilEpochMillis()<=now
                    || request.approvalUntilEpochMillis()>253402300799999L)throw AuthorityWire.bad();
        }else if(request.approvalUntilEpochMillis()!=null)throw AuthorityWire.bad();
        return new DecodedCommand(original,request);
    }
    private static OrganizationAuthorityIntent<EnrollmentProposalCommand> encode(String encoding,String tenantId,String bankId,
            String proposalId,long revision,String targetCanonical,DecodedCommand request) {
        String canonical=AuthorityWire.token(encoding)+AuthorityWire.recordToken(tenantId,bankId,proposalId,revision,targetCanonical,request.request());
        return new OrganizationAuthorityIntent<>(request.originalJson(),request.request(),canonical,AuthorityWire.sha(canonical));
    }
}
