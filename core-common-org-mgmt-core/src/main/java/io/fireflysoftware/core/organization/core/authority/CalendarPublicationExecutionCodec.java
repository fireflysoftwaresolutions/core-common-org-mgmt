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

import io.fireflysoftware.core.organization.interfaces.authority.CalendarPublicationExecutionTarget;
import io.fireflysoftware.core.organization.interfaces.authority.LegacyBankCalendarSource;
import io.fireflysoftware.core.organization.interfaces.authority.NativeInstitutionCalendarSource;

public final class CalendarPublicationExecutionCodec {
    private static final String ENCODING = "FF_ORGANIZATION_AUTHORITY_EXECUTION_TARGET_V2";

    private CalendarPublicationExecutionCodec() { }

    public static OrganizationAuthorityIntent<CalendarPublicationExecutionTarget> decode(
            String tenantId, String institutionId, String issuerAuthorityId, byte[] bytes) {
        String original = AuthorityWire.original(bytes, 2097152);
        var target = AuthorityWire.record(AuthorityWire.parse(original), CalendarPublicationExecutionTarget.class,
                AuthorityWire.nullable(CalendarPublicationExecutionTarget.class));
        validate(tenantId, institutionId, issuerAuthorityId, target);
        String canonical = AuthorityWire.token(ENCODING)
                + AuthorityWire.recordToken(tenantId, institutionId, issuerAuthorityId, "CALENDAR_PUBLISH", target);
        return new OrganizationAuthorityIntent<>(original, target, canonical, AuthorityWire.sha(canonical));
    }

    public static void requireObservation(CalendarPublicationExecutionTarget target,
            VerifiedInstitutionGrantObservation evidence, long epochMillis) {
        if (target == null || target.publication() == null || evidence == null) throw AuthorityWire.bad();
        var definition = target.publication().definition();
        if (definition == null) throw AuthorityWire.bad();
        validate(definition.tenantId(), definition.institutionId(), definition.issuerAuthorityId(), target);
        evidence.requireCurrentAt(epochMillis);
        evidence.requireReference(definition.grant());
        var observed = evidence.observation();
        if (!target.grantDefinition().equals(observed.definition())
                || target.expectedGrantHeadVersion() != observed.headVersion()
                || target.expectedGrantControlVersion() != observed.control().controlVersion()) {
            throw AuthorityWire.bad();
        }
    }

    private static void validate(String tenantId, String institutionId, String issuerAuthorityId,
            CalendarPublicationExecutionTarget target) {
        AuthorityWire.uuid(tenantId);
        AuthorityWire.uuid(institutionId);
        AuthorityWire.uuid(issuerAuthorityId);
        if (target == null || target.schemaVersion() != 2 || target.publication() == null
                || target.grantDefinition() == null) throw AuthorityWire.bad();
        var publication = target.publication();
        OrganizationAuthorityIntentCodec.canonicalTarget(tenantId, institutionId, publication);
        var definition = publication.definition();
        if (!issuerAuthorityId.equals(definition.issuerAuthorityId())) throw AuthorityWire.bad();
        ConfigurationGrantObservationVerifier.definition(tenantId, institutionId, issuerAuthorityId,
                target.grantDefinition());
        var grant = target.grantDefinition().target().grant();
        var reference = definition.grant();
        generation(target.expectedGrantControlVersion());
        if (target.expectedGrantHeadVersion() != grant.version()
                || !grant.grantId().equals(reference.grantId()) || grant.version() != reference.version()
                || !target.grantDefinition().definitionDigest().equals(reference.digest())
                || !grant.uses().contains(reference.use())) throw AuthorityWire.bad();
        if (publication.expectedSeriesHeadVersion() == 0) {
            if (target.expectedHeadControlVersion() != null) throw AuthorityWire.bad();
        } else {
            if (target.expectedHeadControlVersion() == null) throw AuthorityWire.bad();
            generation(target.expectedHeadControlVersion());
        }
        if (definition.source() instanceof NativeInstitutionCalendarSource) {
            if (target.legacyBinding() != null) throw AuthorityWire.bad();
        } else if (definition.source() instanceof LegacyBankCalendarSource legacy) {
            var binding = target.legacyBinding();
            if (binding == null) throw AuthorityWire.bad();
            AuthorityWire.uuid(binding.bankId());
            AuthorityWire.uuid(binding.enrollmentId());
            AuthorityWire.digest(binding.enrollmentDigest());
            generation(binding.ownershipGeneration());
            generation(binding.sourceGeneration());
            generation(binding.enrollmentControlVersion());
            if (binding.ownershipGeneration() == 0
                    || !legacy.snapshot().bankId().equals(binding.bankId())
                    || !legacy.bankEnrollmentId().equals(binding.enrollmentId())
                    || legacy.enrollmentVersion() != binding.enrollmentVersion()
                    || !legacy.enrollmentDigest().equals(binding.enrollmentDigest())) throw AuthorityWire.bad();
        } else throw AuthorityWire.bad();
    }

    private static void generation(long value) {
        if (value < 0 || value == Long.MAX_VALUE) throw AuthorityWire.bad();
    }
}
