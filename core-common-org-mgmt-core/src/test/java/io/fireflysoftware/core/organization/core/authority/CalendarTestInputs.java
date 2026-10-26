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

import com.fasterxml.jackson.databind.json.JsonMapper;
import io.fireflysoftware.core.organization.interfaces.authority.*;
import java.nio.charset.StandardCharsets;

final class CalendarTestInputs {
    static final String TENANT = "00000000-0000-0000-0000-000000000001";
    static final String INSTITUTION = "00000000-0000-0000-0000-000000000002";
    static final String ISSUER = "00000000-0000-0000-0000-000000000006";
    static final String SOURCE = "00000000-0000-0000-0000-000000000007";
    static final String PUBLICATION = "00000000-0000-0000-0000-000000000008";
    static byte[] bytes(String v) { return v.getBytes(StandardCharsets.UTF_8); }
    static com.fasterxml.jackson.databind.JsonNode vector() throws Exception {
        try(var in=CalendarTestInputs.class.getResourceAsStream("/authority/organization-authority-v1-golden.json")) { return new JsonMapper().readTree(in); }
    }
    static CalendarPublicationDraft draft() throws Exception {
        return OrganizationAuthorityIntentCodec.decodeDraft(TENANT,INSTITUTION,ISSUER,bytes(vector().get("original").textValue())).request();
    }
    static FinancialCalendarDefinition definition(CalendarPublicationDraft draft) {
        return new NativeInstitutionCalendarSourcePreparer().prepare(TENANT,INSTITUTION,ISSUER,draft,SOURCE,1792713600000L);
    }
    static FinancialCalendarPublication publication(CalendarPublicationDraft draft) {
        return OrganizationAuthorityIntentCodec.publication(PUBLICATION,1,1792713600000L,definition(draft));
    }
}
