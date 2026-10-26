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

import io.fireflysoftware.core.organization.models.entities.authority.*;
import java.util.Objects;
import java.util.UUID;

/** Internal consistency of retained facts; current authorization and native source gates are separate. */
public final class RetainedEnrollmentProposal {
    private RetainedEnrollmentProposal() { }
    public static OrganizationProposalAuthorization.ProposalScope scope(UUID tenant,UUID bank,UUID proposal,long revision,
            EnrollmentProposalRow header,EnrollmentProposalRevisionRow row,EnrollmentProposalControlRow control) {
        var key=new EnrollmentProposalKey(tenant,proposal);var full=BankEnrollmentRevisionCodec.decode(row.revisionOriginal());
        var target=BankEnrollmentRevisionCodec.enrollmentTarget(full.request()).request();
        if(!header.id().equals(key) || !row.id().tenantId().equals(tenant) || !row.id().proposalId().equals(proposal)
                || !row.id().revision().equals(revision) || !control.id().equals(key) || !bank.equals(header.bankId())
                || !tenant.toString().equals(target.destinationTenantId()) || !bank.toString().equals(target.bankId())
                || !proposal.toString().equals(full.request().proposalId()) || revision!=full.request().revision()
                || !ProposalTerminationEvidenceCodec.REVISION_ENCODING.equals(row.revisionEncoding())
                || !full.canonicalIntent().equals(row.revisionCanonical()) || !full.intentDigest().equals(row.revisionDigest())
                || row.expectedBankGeneration()!=full.request().expectedBankGeneration() || row.expectedOwnershipGeneration()!=target.expectedOwnershipGeneration()
                || row.expectedSourceGeneration()!=target.expectedSourceGeneration() || !Objects.equals(row.expectedControlVersion(),target.expectedEnrollmentControlVersion())
                || row.notAfterMs()!=target.commandDeadlineEpochMillis() || !row.allowedUses().equals(full.request().allowedUses().stream().map(Enum::name).toList())
                || !row.reservedExecutionActionId().toString().equals(target.actionId()) || !row.targetKind().equals(target.mode().name())
                || !header.institutionId().toString().equals(target.institutionId()) || !header.issuerId().toString().equals(target.issuerAuthorityId())
                || !header.allocatedEnrollmentId().toString().equals(target.enrollmentId()) || !header.targetKind().equals(target.mode().name())
                || !"STAFF".equals(header.makerKind()) || control.currentRevision()!=revision)throw AuthorityWire.bad();
        var maker=new OrganizationProposalAuthorization.Maker(header.makerIssuer(),header.makerSubject(),header.makerTenantId().toString(),header.makerStaffRealm(),header.makerStaffId().toString());
        AuthorityWire.text(maker.issuer(),500);AuthorityWire.text(maker.subject(),255);AuthorityWire.text(maker.staffRealm(),255);AuthorityWire.uuid(maker.staffActorId());
        return new OrganizationProposalAuthorization.ProposalScope(tenant.toString(),bank.toString(),proposal.toString(),revision,control.controlVersion(),row.expectedBankGeneration(),BankEnrollmentLifecycle.ProposalState.valueOf(control.state()),full,maker);
    }
}
