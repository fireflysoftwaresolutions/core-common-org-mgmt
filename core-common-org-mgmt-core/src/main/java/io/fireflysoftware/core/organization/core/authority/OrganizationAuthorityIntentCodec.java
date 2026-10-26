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

import io.fireflysoftware.core.organization.interfaces.authority.*;
import java.util.*;

public final class OrganizationAuthorityIntentCodec {
    private OrganizationAuthorityIntentCodec() {}

    public static OrganizationAuthorityIntent<CalendarPublicationDraft> decodeDraft(String tenantId,String institutionId,String issuerAuthorityId,byte[] bytes) {
        var original=AuthorityWire.original(bytes,2097152);
        var draft=AuthorityWire.record(AuthorityWire.parse(original),CalendarPublicationDraft.class,AuthorityWire.nullable(CalendarPublicationDraft.class));
        validateDraft(tenantId,institutionId,issuerAuthorityId,draft);
        var canonical=AuthorityWire.token("FF_ORGANIZATION_CALENDAR_DRAFT_V1")+AuthorityWire.recordToken(tenantId,institutionId,issuerAuthorityId,draft);
        return new OrganizationAuthorityIntent<>(original,draft,canonical,AuthorityWire.sha(canonical));
    }

    public static OrganizationAuthorityIntent<CalendarPublicationTarget> decodeTarget(String tenantId,String institutionId,byte[] bytes) {
        var original=AuthorityWire.original(bytes,2097152);
        var target=AuthorityWire.record(AuthorityWire.parse(original),CalendarPublicationTarget.class,Set.of());
        var canonical=canonicalTarget(tenantId,institutionId,target);
        return new OrganizationAuthorityIntent<>(original,target,canonical.canonicalIntent(),canonical.intentDigest());
    }

    public static OrganizationAuthorityIntent<CalendarPublicationTarget> canonicalTarget(String tenantId,String institutionId,CalendarPublicationTarget target) {
        AuthorityWire.uuid(tenantId);AuthorityWire.uuid(institutionId);
        for(var id:List.of(target.actionId(),target.proposalId(),target.publicationId())) AuthorityWire.uuid(id);
        if(target.proposalRevision()<1 || target.expectedSeriesHeadVersion()<0 || target.expectedSeriesHeadVersion()==Long.MAX_VALUE || target.version()!=target.expectedSeriesHeadVersion()+1) throw AuthorityWire.bad();
        time(target.preparedAtEpochMillis());validateDefinition(target.definition());
        if(!target.definition().tenantId().equals(tenantId) || !target.definition().institutionId().equals(institutionId)) throw AuthorityWire.bad();
        if(target.definition().source() instanceof NativeInstitutionCalendarSource n && n.preparedAtEpochMillis()!=target.preparedAtEpochMillis()) throw AuthorityWire.bad();
        var canonical=AuthorityWire.token("FF_ORGANIZATION_AUTHORITY_TARGET_V1")+AuthorityWire.recordToken(tenantId,institutionId,"CALENDAR_PUBLISH",target);
        return new OrganizationAuthorityIntent<>(null,target,canonical,AuthorityWire.sha(canonical));
    }

    public static FinancialCalendarPublication publication(String publicationId,long version,long preparedAtEpochMillis,FinancialCalendarDefinition definition) {
        AuthorityWire.uuid(publicationId);if(version<1) throw AuthorityWire.bad();time(preparedAtEpochMillis);validateDefinition(definition);
        if(definition.source() instanceof NativeInstitutionCalendarSource n && n.preparedAtEpochMillis()!=preparedAtEpochMillis) throw AuthorityWire.bad();
        var canonical=AuthorityWire.token("FF_FINANCIAL_CALENDAR_V1")+AuthorityWire.recordToken(publicationId,version,preparedAtEpochMillis,definition);
        return new FinancialCalendarPublication(publicationId,version,preparedAtEpochMillis,definition,canonical,AuthorityWire.sha(canonical));
    }

    public static String sourceSnapshotDigest(FinancialCalendarSourceSnapshot snapshot) {
        if(snapshot==null) throw AuthorityWire.bad();
        return AuthorityWire.sha(AuthorityWire.token("FF_LEGACY_BANK_CALENDAR_SOURCE_V1")+AuthorityWire.token(snapshot));
    }

    static void validateDraft(String tenant,String institution,String issuer,CalendarPublicationDraft draft) {
        AuthorityWire.uuid(tenant);AuthorityWire.uuid(institution);AuthorityWire.uuid(issuer);
        if(draft==null || draft.expectedSeriesHeadVersion()<0 || draft.expectedSeriesHeadVersion()==Long.MAX_VALUE) throw AuthorityWire.bad();
        if(draft.existingPublicationId()==null) { if(draft.expectedSeriesHeadVersion()!=0) throw AuthorityWire.bad(); }
        else { AuthorityWire.uuid(draft.existingPublicationId());if(draft.expectedSeriesHeadVersion()<1) throw AuthorityWire.bad(); }
        grant(draft.grant());FinancialCalendarRules.validate(draft.rules());
        if(draft.source() instanceof NativeInstitutionCalendarDraft n) {
            if(!"NATIVE_INSTITUTION".equals(n.profile()) || n.targetKind()!=CalendarTargetKind.INSTITUTION || !institution.equals(n.targetId())) throw AuthorityWire.bad();
        } else if(draft.source() instanceof LegacyBankCalendarDraft b) {
            if(!"LEGACY_BANK_CALENDAR".equals(b.profile()) || b.enrollmentVersion()<1 || b.target().kind()==CalendarTargetKind.INSTITUTION) throw AuthorityWire.bad();
            AuthorityWire.uuid(b.bankEnrollmentId());AuthorityWire.uuid(b.workingCalendarId());AuthorityWire.uuid(b.target().id());AuthorityWire.digest(b.enrollmentDigest());AuthorityWire.digest(b.expectedSourceSnapshotDigest());
        } else throw AuthorityWire.bad();
    }
    static void grant(InstitutionAuthorityGrantReference grant) {
        if(grant==null || grant.version()<1 || grant.use()==null) throw AuthorityWire.bad();AuthorityWire.uuid(grant.grantId());AuthorityWire.digest(grant.digest());
    }
    static void time(long millis) { if(millis < -2208988800000L || millis > 253402300799999L) throw AuthorityWire.bad(); }

    static void validateDefinition(FinancialCalendarDefinition d) {
        if(d==null || d.schemaVersion()!=1) throw AuthorityWire.bad();
        AuthorityWire.uuid(d.tenantId());AuthorityWire.uuid(d.institutionId());AuthorityWire.uuid(d.issuerAuthorityId());grant(d.grant());AuthorityWire.text(d.tzdbVersion(),100);
        if(d.target()==null || d.target().kind()==null) throw AuthorityWire.bad();AuthorityWire.uuid(d.target().id());
        FinancialCalendarRules.validateRetainedDays(d);
        if(d.source() instanceof NativeInstitutionCalendarSource n) {
            AuthorityWire.uuid(n.sourceRevisionId());time(n.preparedAtEpochMillis());
            if(!"NATIVE_INSTITUTION".equals(n.profile()) || n.sourceRevision()!=1 || !d.tenantId().equals(n.tenantId()) || !d.institutionId().equals(n.institutionId()) || !d.issuerAuthorityId().equals(n.issuerAuthorityId()) || n.use()!=d.grant().use() || n.targetKind()!=CalendarTargetKind.INSTITUTION || d.target().kind()!=CalendarTargetKind.INSTITUTION || !d.institutionId().equals(n.targetId()) || !d.target().id().equals(n.targetId()) || !d.rules().equals(n.rules())) throw AuthorityWire.bad();
            var canonical=NativeInstitutionCalendarSourcePreparer.sourceCanonical(n.sourceRevisionId(),n.tenantId(),n.institutionId(),n.issuerAuthorityId(),n.use(),n.targetKind(),n.targetId(),n.rules(),n.preparedAtEpochMillis());
            if(!canonical.equals(n.canonicalSource()) || !AuthorityWire.sha(canonical).equals(n.sourceDigest())) throw AuthorityWire.bad();
        } else if(d.source() instanceof LegacyBankCalendarSource b) {
            LegacyBankCalendarSourcePreparer.validateSnapshot(b,d);
        } else throw AuthorityWire.bad();
    }
}
