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
import java.util.Set;

public final class BankEnrollmentIntentCodec {
    private BankEnrollmentIntentCodec() { }
    public static OrganizationAuthorityIntent<BankEnrollmentTarget> decode(byte[] bytes) {
        String original=AuthorityWire.original(bytes,2097152);
        var target=AuthorityWire.record(AuthorityWire.parse(original),BankEnrollmentTarget.class,
                Set.of("existingEnrollmentId","expectedEnrollmentVersion","expectedEnrollmentControlVersion"));
        validate(target);
        String canonical=AuthorityWire.token("FF_ORGANIZATION_BANK_ENROLLMENT_TARGET_V2")+AuthorityWire.token(target);
        return new OrganizationAuthorityIntent<>(original,target,canonical,AuthorityWire.sha(canonical));
    }
    static void validate(BankEnrollmentTarget t) {
        if(t==null || t.schemaVersion()!=2 || t.mode()==null || t.use()==null) throw AuthorityWire.bad();
        for(String id:new String[]{t.actionId(),t.bankId(),t.enrollmentId(),t.destinationTenantId(),t.institutionId(),t.issuerAuthorityId()}) AuthorityWire.uuid(id);
        generation(t.expectedOwnershipGeneration());generation(t.expectedSourceGeneration());generation(t.expectedGrantControlVersion());
        if(t.mode()==BankEnrollmentTarget.Mode.ENROLL) {
            if(t.expectedOwnershipGeneration()!=0 || t.existingEnrollmentId()!=null
                    || t.expectedEnrollmentVersion()!=null || t.expectedEnrollmentControlVersion()!=null) throw AuthorityWire.bad();
        } else {
            AuthorityWire.uuid(t.existingEnrollmentId());
            if(t.expectedOwnershipGeneration()==0 || t.existingEnrollmentId().equals(t.enrollmentId())
                    || t.expectedEnrollmentVersion()==null || t.expectedEnrollmentVersion()<=0
                    || t.expectedEnrollmentVersion()==Long.MAX_VALUE || t.expectedEnrollmentControlVersion()==null) throw AuthorityWire.bad();
            generation(t.expectedEnrollmentControlVersion());
        }
        ConfigurationGrantObservationVerifier.definition(t.destinationTenantId(),t.institutionId(),t.issuerAuthorityId(),t.grantDefinition());
        var grant=t.grantDefinition().target().grant();
        if(t.expectedGrantHeadVersion()!=grant.version() || !grant.uses().contains(t.use())
                || t.commandDeadlineEpochMillis()<=0 || t.commandDeadlineEpochMillis()>253402300799999L) throw AuthorityWire.bad();
    }
    public static void requireObservation(BankEnrollmentTarget t,VerifiedInstitutionGrantObservation evidence,long now) {
        validate(t);if(evidence==null) throw AuthorityWire.bad();evidence.requireCurrentAt(now);
        var o=evidence.observation();
        if(!t.destinationTenantId().equals(o.tenantId()) || !t.institutionId().equals(o.institutionId())
                || !t.issuerAuthorityId().equals(o.issuerAuthorityId()) || t.use()!=o.requestedUse()
                || !t.grantDefinition().equals(o.definition()) || t.expectedGrantHeadVersion()!=o.headVersion()
                || t.expectedGrantControlVersion()!=o.control().controlVersion()) throw AuthorityWire.bad();
    }
    private static void generation(long n) { if(n<0 || n==Long.MAX_VALUE) throw AuthorityWire.bad(); }
}
