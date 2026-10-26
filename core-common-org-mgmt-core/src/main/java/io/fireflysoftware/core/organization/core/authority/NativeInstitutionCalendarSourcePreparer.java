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
import java.util.Set;

public final class NativeInstitutionCalendarSourcePreparer {
    public FinancialCalendarDefinition prepare(String tenantId,String institutionId,String issuerAuthorityId,CalendarPublicationDraft draft,String sourceRevisionId,long preparedAtEpochMillis) {
        OrganizationAuthorityIntentCodec.validateDraft(tenantId,institutionId,issuerAuthorityId,draft);
        AuthorityWire.uuid(sourceRevisionId);OrganizationAuthorityIntentCodec.time(preparedAtEpochMillis);
        if(!(draft.source() instanceof NativeInstitutionCalendarDraft source)) throw AuthorityWire.bad();
        var canonical=sourceCanonical(sourceRevisionId,tenantId,institutionId,issuerAuthorityId,draft.grant().use(),source.targetKind(),source.targetId(),draft.rules(),preparedAtEpochMillis);
        var retained=new NativeInstitutionCalendarSource("NATIVE_INSTITUTION",sourceRevisionId,1,tenantId,institutionId,issuerAuthorityId,draft.grant().use(),source.targetKind(),source.targetId(),draft.rules(),preparedAtEpochMillis,canonical,AuthorityWire.sha(canonical));
        return new FinancialCalendarDefinition(1,tenantId,institutionId,issuerAuthorityId,draft.grant(),new FinancialCalendarTarget(source.targetKind(),source.targetId()),retained,draft.rules(),FinancialCalendarRules.tzdbVersion(draft.rules().ianaZoneId()),FinancialCalendarRules.materialize(draft.rules(),Set.of()));
    }

    static String sourceCanonical(String id,String tenant,String institution,String issuer,FinancialCalendarUse use,CalendarTargetKind kind,String target,FinancialCalendarRuleDraft rules,long preparedAt) {
        return AuthorityWire.token("FF_NATIVE_INSTITUTION_CALENDAR_SOURCE_V1")+AuthorityWire.recordToken("NATIVE_INSTITUTION",id,1L,tenant,institution,issuer,use,kind,target,rules,preparedAt);
    }
}
