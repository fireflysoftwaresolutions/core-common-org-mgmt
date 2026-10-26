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

import io.fireflysoftware.core.organization.interfaces.authority.BankEnrollmentRevision;
import io.fireflysoftware.core.organization.interfaces.authority.BankEnrollmentTarget;
import io.fireflysoftware.core.organization.interfaces.authority.FinancialCalendarUse;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class BankEnrollmentRevisionCodec {
    private BankEnrollmentRevisionCodec() { }
    public static OrganizationAuthorityIntent<BankEnrollmentRevision> decode(byte[] bytes) {
        String original=AuthorityWire.original(bytes,4194304);
        var revision=AuthorityWire.record(AuthorityWire.parse(original),BankEnrollmentRevision.class,Set.of("previousOwner"),Set.of("originalEnrollmentTargetJson"));
        var target=enrollmentTarget(revision);
        String canonical=AuthorityWire.token("FF_ORGANIZATION_BANK_ENROLLMENT_REVISION_V2")
                +AuthorityWire.recordToken(revision.proposalId(),revision.revision(),revision.expectedBankGeneration(),
                revision.previousOwner(),target.canonicalIntent(),revision.reason(),revision.allowedUses(),revision.evidenceReferences());
        return new OrganizationAuthorityIntent<>(original,revision,canonical,AuthorityWire.sha(canonical));
    }
    public static OrganizationAuthorityIntent<BankEnrollmentTarget> enrollmentTarget(BankEnrollmentRevision revision) {
        if(revision==null || revision.schemaVersion()!=2 || revision.revision()<=0 || revision.revision()==Long.MAX_VALUE
                || revision.expectedBankGeneration()<0 || revision.expectedBankGeneration()==Long.MAX_VALUE
                || revision.originalEnrollmentTargetJson()==null) throw AuthorityWire.bad();
        AuthorityWire.uuid(revision.proposalId());AuthorityWire.text(revision.reason(),4096);
        var target=BankEnrollmentIntentCodec.decode(revision.originalEnrollmentTargetJson().getBytes(StandardCharsets.UTF_8));
        var t=target.request();
        if(!target.canonicalIntent().equals(revision.canonicalEnrollmentTarget())
                || !target.intentDigest().equals(revision.enrollmentTargetDigest())
                || revision.expectedBankGeneration()<t.expectedOwnershipGeneration()) throw AuthorityWire.bad();
        var previous=revision.previousOwner();
        if(t.mode()==BankEnrollmentTarget.Mode.ENROLL) {
            if(previous!=null) throw AuthorityWire.bad();
        } else {
            if(previous==null) throw AuthorityWire.bad();
            for(String id:new String[]{previous.tenantId(),previous.institutionId(),previous.issuerAuthorityId(),previous.enrollmentId()})AuthorityWire.uuid(id);
            if(previous.version()<=0 || previous.version()==Long.MAX_VALUE || previous.controlVersion()<0
                    || previous.controlVersion()==Long.MAX_VALUE || !previous.enrollmentId().equals(t.existingEnrollmentId())
                    || previous.version()!=t.expectedEnrollmentVersion() || previous.controlVersion()!=t.expectedEnrollmentControlVersion()) throw AuthorityWire.bad();
        }
        if(revision.allowedUses().isEmpty() || revision.allowedUses().size()>3 || !revision.allowedUses().contains(t.use())
                || !t.grantDefinition().target().grant().uses().containsAll(revision.allowedUses())) throw AuthorityWire.bad();
        int previousOrdinal=-1;
        for(var use:revision.allowedUses()) {if(use==null || use.ordinal()<=previousOrdinal)throw AuthorityWire.bad();previousOrdinal=use.ordinal();}
        if(revision.evidenceReferences().isEmpty() || revision.evidenceReferences().size()>32
                || new HashSet<>(revision.evidenceReferences()).size()!=revision.evidenceReferences().size()) throw AuthorityWire.bad();
        revision.evidenceReferences().forEach(ref->AuthorityWire.text(ref,512));
        return target;
    }
    /** Checks finite observations only; caller authority and native state remain separate. */
    public static long requireObservationSet(OrganizationAuthorityIntent<BankEnrollmentRevision> retained,
            List<VerifiedInstitutionGrantObservation> observations,long now) {
        if(retained==null || retained.originalJson()==null || observations==null || now<=0)throw AuthorityWire.bad();
        var full=decode(retained.originalJson().getBytes(StandardCharsets.UTF_8));
        List<VerifiedInstitutionGrantObservation> snapshot;
        try {snapshot=List.copyOf(observations);}catch(NullPointerException error){throw AuthorityWire.bad();}
        if(!full.equals(retained) || snapshot.size()!=full.request().allowedUses().size())throw AuthorityWire.bad();
        var target=enrollmentTarget(full.request()).request();long until=target.commandDeadlineEpochMillis();
        if(now>=until)throw new IllegalStateException("Enrollment target expired");
        var seen=new HashSet<FinancialCalendarUse>();
        for(var evidence:snapshot) {
            if(evidence==null)throw AuthorityWire.bad();evidence.requireCurrentAt(now);var observed=evidence.observation();
            if(!full.request().allowedUses().contains(observed.requestedUse()) || !seen.add(observed.requestedUse())
                    || !target.destinationTenantId().equals(observed.tenantId()) || !target.institutionId().equals(observed.institutionId())
                    || !target.issuerAuthorityId().equals(observed.issuerAuthorityId()) || !target.grantDefinition().equals(observed.definition())
                    || target.expectedGrantHeadVersion()!=observed.headVersion() || target.expectedGrantControlVersion()!=observed.control().controlVersion())throw AuthorityWire.bad();
            until=Math.min(until,evidence.usableUntilEpochMillis());
        }
        return until;
    }
}
