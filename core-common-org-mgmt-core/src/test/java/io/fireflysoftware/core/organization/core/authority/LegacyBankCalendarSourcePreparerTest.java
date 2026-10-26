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
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static io.fireflysoftware.core.organization.core.authority.CalendarTestInputs.*;

class LegacyBankCalendarSourcePreparerTest {
    private static final String BANK="00000000-0000-0000-0000-000000000010";
    private static final String CALENDAR="00000000-0000-0000-0000-000000000011";
    private static final String ENROLLMENT="00000000-0000-0000-0000-000000000012";

    @Test void explicitBankProfileUsesRetainedBankHolidayAndNeverNativePreparer() throws Exception {
        var target=new FinancialCalendarTarget(CalendarTargetKind.BANK,BANK);
        var snapshot=new FinancialCalendarSourceSnapshot(BANK,CALENDAR,BANK,true,target,List.of(),List.of(new FinancialCalendarHolidaySnapshot(SOURCE,BANK,null,null,"Closure","2026-10-23",false)),null,List.of());
        var digest=OrganizationAuthorityIntentCodec.sourceSnapshotDigest(snapshot);
        var draft=new CalendarPublicationDraft(null,0,draft().grant(),new LegacyBankCalendarDraft("LEGACY_BANK_CALENDAR",ENROLLMENT,1,"b".repeat(64),CALENDAR,target,digest),draft().rules());
        var result=new LegacyBankCalendarSourcePreparer().prepare(TENANT,INSTITUTION,ISSUER,draft,snapshot);
        assertFalse(result.days().getFirst().working());
        assertInstanceOf(LegacyBankCalendarSource.class,result.source());
        assertThrows(IllegalArgumentException.class,()->definition(draft));
        assertThrows(IllegalArgumentException.class,()->new LegacyBankCalendarSourcePreparer().prepare(TENANT,INSTITUTION,ISSUER,draft(),snapshot));
    }
    @Test void foreignCalendarParentAndChangedSourceDigestReject() throws Exception {
        var target=new FinancialCalendarTarget(CalendarTargetKind.BANK,BANK);
        var snapshot=new FinancialCalendarSourceSnapshot(BANK,CALENDAR,SOURCE,true,target,List.of(),List.of(),null,List.of());
        var draft=new CalendarPublicationDraft(null,0,draft().grant(),new LegacyBankCalendarDraft("LEGACY_BANK_CALENDAR",ENROLLMENT,1,"b".repeat(64),CALENDAR,target,OrganizationAuthorityIntentCodec.sourceSnapshotDigest(snapshot)),draft().rules());
        assertThrows(IllegalArgumentException.class,()->new LegacyBankCalendarSourcePreparer().prepare(TENANT,INSTITUTION,ISSUER,draft,snapshot));
        var changed=new FinancialCalendarSourceSnapshot(BANK,CALENDAR,BANK,true,target,List.of(),List.of(),null,List.of());
        assertThrows(IllegalArgumentException.class,()->new LegacyBankCalendarSourcePreparer().prepare(TENANT,INSTITUTION,ISSUER,draft,changed));
    }
    @Test void historicalLegacyAssignmentDoesNotReResolveAgainstChangedTimezoneRules() throws Exception {
        var provider=new MutableRules();java.time.zone.ZoneRulesProvider.registerProvider(provider);
        var nativeDraft=draft();var r=nativeDraft.rules();
        var rules=new FinancialCalendarRuleDraft(r.validFromDate(),r.validToDateExclusive(),MutableRules.ZONE,r.weeklyRules(),r.dateOverrides(),r.rollConventions());
        var branch="00000000-0000-0000-0000-000000000020";var region="00000000-0000-0000-0000-000000000021";var division="00000000-0000-0000-0000-000000000022";
        var target=new FinancialCalendarTarget(CalendarTargetKind.BRANCH,branch);
        var ancestry=List.of(new OrganizationMembershipSnapshot("BRANCH",branch,"REGION",region,BANK,true),new OrganizationMembershipSnapshot("REGION",region,"DIVISION",division,BANK,true),new OrganizationMembershipSnapshot("DIVISION",division,"BANK",BANK,BANK,true));
        var hours=new java.util.ArrayList<FinancialCalendarBranchHoursSnapshot>();for(int i=1;i<=7;i++) hours.add(new FinancialCalendarBranchHoursSnapshot(i,"09:00:00","17:00:00",false));
        var assignment=new FinancialCalendarAssignmentSnapshot(SOURCE,CALENDAR,target,"2026-10-23T00:00:00","2026-10-27T00:00:00",1792713600000L,1793059200000L,true);
        var snapshot=new FinancialCalendarSourceSnapshot(BANK,CALENDAR,BANK,true,target,ancestry,List.of(),assignment,hours);
        var bankDraft=new CalendarPublicationDraft(null,0,nativeDraft.grant(),new LegacyBankCalendarDraft("LEGACY_BANK_CALENDAR",ENROLLMENT,1,"b".repeat(64),CALENDAR,target,OrganizationAuthorityIntentCodec.sourceSnapshotDigest(snapshot)),rules);
        var definition=new LegacyBankCalendarSourcePreparer().prepare(TENANT,INSTITUTION,ISSUER,bankDraft,snapshot);
        var publication=OrganizationAuthorityIntentCodec.publication(PUBLICATION,1,1792713600000L,definition);
        provider.offset=java.time.ZoneOffset.ofHours(1);
        var result=new FinancialCalendarEvaluator().evaluate(publication,new FinancialCalendarEvaluationRequest(PUBLICATION,1,publication.definitionDigest(),FinancialCalendarEvaluationMode.HISTORICAL,FinancialCalendarRollConvention.FOLLOWING,"2026-10-23",null));
        assertEquals("2026-10-23",result.chosenDate());assertEquals(1792774800000L,result.cutoffEpochMillis());
    }
    private static final class MutableRules extends java.time.zone.ZoneRulesProvider {
        static final String ZONE="Test/RetainedOrganizationCalendar";
        volatile java.time.ZoneOffset offset=java.time.ZoneOffset.UTC;
        @Override protected java.util.Set<String> provideZoneIds() { return java.util.Set.of(ZONE); }
        @Override protected java.time.zone.ZoneRules provideRules(String zoneId,boolean forCaching) { return offset.getRules(); }
        @Override protected java.util.NavigableMap<String,java.time.zone.ZoneRules> provideVersions(String zoneId) { var map=new java.util.TreeMap<String,java.time.zone.ZoneRules>();map.put("test-original",offset.getRules());return map; }
    }

}
