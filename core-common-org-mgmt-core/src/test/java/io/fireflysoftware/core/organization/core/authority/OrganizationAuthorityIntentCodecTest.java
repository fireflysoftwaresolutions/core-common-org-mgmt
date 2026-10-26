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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
import static io.fireflysoftware.core.organization.core.authority.CalendarTestInputs.*;

class OrganizationAuthorityIntentCodecTest {
    @Test void literalNativeDraftVectorNeedsNoBankOrCustomerEntity() throws Exception {
        var v=vector(); var result=OrganizationAuthorityIntentCodec.decodeDraft(TENANT,INSTITUTION,ISSUER,bytes(v.get("original").textValue()));
        assertEquals(v.get("canonical").textValue(),result.canonicalIntent());
        assertEquals(v.get("digest").textValue(),result.intentDigest());
        assertInstanceOf(NativeInstitutionCalendarDraft.class,result.request().source());
        assertNull(result.request().existingPublicationId());
        assertThrows(UnsupportedOperationException.class,()->result.request().rules().weeklyRules().clear());
    }
    @ParameterizedTest @ValueSource(strings={"1e0","1.0","-0","null","9223372036854775808","\"1\""})
    void rejectsNonIntegerHeadLexemes(String token) throws Exception {
        var json=vector().get("original").textValue().replace("\"expectedSeriesHeadVersion\":0","\"expectedSeriesHeadVersion\":"+token);
        assertThrows(IllegalArgumentException.class,()->OrganizationAuthorityIntentCodec.decodeDraft(TENANT,INSTITUTION,ISSUER,bytes(json)));
    }
    @Test void rejectsMixedProfilesUnknownMissingDuplicateAndMalformedUtf8() throws Exception {
        var json=vector().get("original").textValue();
        for(var body:List.of(json.replace("\"profile\":\"NATIVE_INSTITUTION\"","\"profile\":\"NATIVE_INSTITUTION\",\"bankId\":null"),json.replace("{","{\"unexpected\":null,"),json.replace("\"expectedSeriesHeadVersion\":0,",""),json.replace("{","{\"expectedSeriesHeadVersion\":0,"),json.replace("NATIVE_INSTITUTION","UNKNOWN"))) {
            assertThrows(IllegalArgumentException.class,()->OrganizationAuthorityIntentCodec.decodeDraft(TENANT,INSTITUTION,ISSUER,bytes(body)));
        }
        assertThrows(IllegalArgumentException.class,()->OrganizationAuthorityIntentCodec.decodeDraft(TENANT,INSTITUTION,ISSUER,new byte[]{(byte)0xff}));
        assertThrows(IllegalArgumentException.class,()->OrganizationAuthorityIntentCodec.decodeDraft(TENANT,INSTITUTION,ISSUER,bytes(json+" []")));
    }
    @Test void nativeTargetMustBeInstitutionAndNotAnUnrelatedId() throws Exception {
        var json=vector().get("original").textValue();
        assertThrows(IllegalArgumentException.class,()->OrganizationAuthorityIntentCodec.decodeDraft(TENANT,INSTITUTION,ISSUER,bytes(json.replace("\"targetKind\":\"INSTITUTION\"","\"targetKind\":\"BANK\""))));
        assertThrows(IllegalArgumentException.class,()->OrganizationAuthorityIntentCodec.decodeDraft(TENANT,INSTITUTION,ISSUER,bytes(json.replace("\"targetId\":\""+INSTITUTION,"\"targetId\":\""+SOURCE))));
    }
    @Test void fullPublicationTargetBindsActionApprovalGrantAndMaterializedDates() throws Exception {
        var definition=definition(draft());
        var target=new CalendarPublicationTarget(SOURCE,"00000000-0000-0000-0000-000000000009",1,PUBLICATION,1,0,definition,1792713600000L);
        var first=OrganizationAuthorityIntentCodec.canonicalTarget(TENANT,INSTITUTION,target);
        var changed=new CalendarPublicationTarget("00000000-0000-0000-0000-000000000010",target.proposalId(),1,PUBLICATION,1,0,definition,1792713600000L);
        assertNotEquals(first.intentDigest(),OrganizationAuthorityIntentCodec.canonicalTarget(TENANT,INSTITUTION,changed).intentDigest());
        var forgedDays=new ArrayList<>(definition.days()); var d=forgedDays.getFirst();
        forgedDays.set(0,new FinancialCalendarDay(d.date(),d.working(),d.localDayStartEpochMillis(),d.nextLocalDayStartEpochMillis(),d.cutoffLocalTime(),d.cutoffEpochMillis()+1,d.selectedOffsetSeconds()));
        var forged=new FinancialCalendarDefinition(1,definition.tenantId(),definition.institutionId(),definition.issuerAuthorityId(),definition.grant(),definition.target(),definition.source(),definition.rules(),definition.tzdbVersion(),forgedDays);
        assertThrows(IllegalArgumentException.class,()->OrganizationAuthorityIntentCodec.canonicalTarget(TENANT,INSTITUTION,new CalendarPublicationTarget(SOURCE,target.proposalId(),1,PUBLICATION,1,0,forged,1792713600000L)));
    }
    @Test void literalCompleteTargetVectorSurvivesActualRawByteDecode() throws Exception {
        var v=vector();var result=OrganizationAuthorityIntentCodec.decodeTarget(TENANT,INSTITUTION,bytes(v.get("publicationTargetOriginal").textValue()));
        assertEquals(v.get("publicationTargetCanonical").textValue(),result.canonicalIntent());
        assertEquals(v.get("publicationTargetDigest").textValue(),result.intentDigest());
        var source=(NativeInstitutionCalendarSource)result.request().definition().source();
        assertEquals(v.get("nativeSourceCanonical").textValue(),source.canonicalSource());
        assertEquals(v.get("nativeSourceDigest").textValue(),source.sourceDigest());
        assertEquals(4,result.request().definition().days().size());
        var invalid=v.get("publicationTargetOriginal").textValue().replace("\"expectedSeriesHeadVersion\":0","\"expectedSeriesHeadVersion\":1");
        assertThrows(IllegalArgumentException.class,()->OrganizationAuthorityIntentCodec.decodeTarget(TENANT,INSTITUTION,bytes(invalid)));
    }

    @Test void materializedWorkingAndCutoffMustMatchTheApprovedSourceRules() throws Exception {
        var d=definition(draft());var first=d.days().getFirst();
        for(var changed:List.of(new FinancialCalendarDay(first.date(),false,first.localDayStartEpochMillis(),first.nextLocalDayStartEpochMillis(),null,null,null),new FinancialCalendarDay(first.date(),true,first.localDayStartEpochMillis(),first.nextLocalDayStartEpochMillis(),"16:00:00",1792771200000L,0))) {
            var days=new ArrayList<>(d.days());days.set(0,changed);
            var forged=new FinancialCalendarDefinition(1,d.tenantId(),d.institutionId(),d.issuerAuthorityId(),d.grant(),d.target(),d.source(),d.rules(),d.tzdbVersion(),days);
            var target=new CalendarPublicationTarget(SOURCE,"00000000-0000-0000-0000-000000000009",1,PUBLICATION,1,0,forged,1792713600000L);
            assertThrows(IllegalArgumentException.class,()->OrganizationAuthorityIntentCodec.canonicalTarget(TENANT,INSTITUTION,target));
        }
    }

}
