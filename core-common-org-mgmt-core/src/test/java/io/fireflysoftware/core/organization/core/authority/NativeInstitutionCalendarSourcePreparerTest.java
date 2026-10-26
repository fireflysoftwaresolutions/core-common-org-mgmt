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
import static org.junit.jupiter.api.Assertions.*;
import static io.fireflysoftware.core.organization.core.authority.CalendarTestInputs.*;

class NativeInstitutionCalendarSourcePreparerTest {
    @Test void materializesCompleteUtcDatesWithoutBankDependency() throws Exception {
        var result=definition(draft());
        assertEquals(4,result.days().size());
        assertEquals("2026-10-23",result.days().getFirst().date());
        assertEquals(1792774800000L,result.days().getFirst().cutoffEpochMillis());
        assertFalse(result.days().get(1).working()); assertNull(result.days().get(1).cutoffEpochMillis());
        assertEquals(CalendarTargetKind.INSTITUTION,result.target().kind());
        assertEquals(INSTITUTION,result.target().id());
        assertInstanceOf(NativeInstitutionCalendarSource.class,result.source());
        assertNotEquals("",result.tzdbVersion());
        assertThrows(UnsupportedOperationException.class,()->result.days().clear());
    }
    @Test void rejectsMissingWeekdayInvalidIntervalAndHorizon() throws Exception {
        var d=draft(); var r=d.rules();
        for(var rules:List.of(new FinancialCalendarRuleDraft(r.validFromDate(),r.validToDateExclusive(),r.ianaZoneId(),r.weeklyRules().subList(0,6),r.dateOverrides(),r.rollConventions()),new FinancialCalendarRuleDraft(r.validFromDate(),r.validFromDate(),r.ianaZoneId(),r.weeklyRules(),r.dateOverrides(),r.rollConventions()),new FinancialCalendarRuleDraft("2026-01-01","2037-01-01","UTC",r.weeklyRules(),r.dateOverrides(),r.rollConventions()))) {
            assertThrows(IllegalArgumentException.class,()->definition(new CalendarPublicationDraft(null,0,d.grant(),d.source(),rules)));
        }
    }
    @Test void dstOverlapNeedsExplicitValidOffsetAndGapNeverGetsShifted() throws Exception {
        var d=draft(); var weekly=new ArrayList<FinancialCalendarWeeklyRule>(); for(int i=1;i<=7;i++) weekly.add(new FinancialCalendarWeeklyRule(i,true,"01:30:00"));
        var ambiguous=new FinancialCalendarRuleDraft("2026-11-01","2026-11-02","America/New_York",weekly,List.of(),d.rules().rollConventions());
        assertThrows(IllegalArgumentException.class,()->definition(new CalendarPublicationDraft(null,0,d.grant(),d.source(),ambiguous)));
        var selected=new FinancialCalendarRuleDraft("2026-11-01","2026-11-02","America/New_York",weekly,List.of(new FinancialCalendarDateOverride("2026-11-01",true,"01:30:00",-14400,"First offset")),d.rules().rollConventions());
        assertEquals(1793511000000L,definition(new CalendarPublicationDraft(null,0,d.grant(),d.source(),selected)).days().getFirst().cutoffEpochMillis());
        var gap=new FinancialCalendarRuleDraft("2026-03-08","2026-03-09","America/New_York",weekly,List.of(new FinancialCalendarDateOverride("2026-03-08",true,"02:30:00",-18000,"Cannot invent a time")),d.rules().rollConventions());
        assertThrows(IllegalArgumentException.class,()->definition(new CalendarPublicationDraft(null,0,d.grant(),d.source(),gap)));
    }
    @Test void explicitNativeOverrideOpensWeekendWithoutClaimingHolidayFeed() throws Exception {
        var d=draft();var r=d.rules();var overrides=List.of(new FinancialCalendarDateOverride("2026-10-24",true,"12:00:00",null,"Special processing day"));
        var rules=new FinancialCalendarRuleDraft(r.validFromDate(),r.validToDateExclusive(),r.ianaZoneId(),r.weeklyRules(),overrides,r.rollConventions());
        assertTrue(definition(new CalendarPublicationDraft(null,0,d.grant(),d.source(),rules)).days().get(1).working());
        assertNotEquals(((NativeInstitutionCalendarSource)definition(d).source()).sourceDigest(),((NativeInstitutionCalendarSource)definition(new CalendarPublicationDraft(null,0,d.grant(),d.source(),rules)).source()).sourceDigest());
    }
}
