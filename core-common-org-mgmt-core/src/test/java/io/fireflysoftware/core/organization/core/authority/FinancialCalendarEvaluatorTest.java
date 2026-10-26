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
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static io.fireflysoftware.core.organization.core.authority.CalendarTestInputs.*;

class FinancialCalendarEvaluatorTest {
    private FinancialCalendarEvaluation evaluate(FinancialCalendarPublication p,String date,FinancialCalendarRollConvention roll,Long instant,FinancialCalendarEvaluationMode mode) {
        return new FinancialCalendarEvaluator().evaluate(p,new FinancialCalendarEvaluationRequest(p.publicationId(),p.version(),p.definitionDigest(),mode,roll,date,instant));
    }
    @Test void rollsFromRetainedFactsAndKeepsHistoricResultsNonAuthoritative() throws Exception {
        var p=publication(draft());
        assertEquals("2026-10-26",evaluate(p,"2026-10-24",FinancialCalendarRollConvention.FOLLOWING,null,FinancialCalendarEvaluationMode.HISTORICAL).chosenDate());
        assertEquals("2026-10-23",evaluate(p,"2026-10-24",FinancialCalendarRollConvention.PRECEDING,null,FinancialCalendarEvaluationMode.HISTORICAL).chosenDate());
        var historical=evaluate(p,"2026-10-24",FinancialCalendarRollConvention.UNADJUSTED,null,FinancialCalendarEvaluationMode.HISTORICAL);
        assertFalse(historical.working());assertEquals("HISTORICAL_ONLY",historical.authorityStatus());
        var current=evaluate(p,"2026-10-23",FinancialCalendarRollConvention.FOLLOWING,null,FinancialCalendarEvaluationMode.CURRENT_ADMISSION);
        assertEquals("CURRENT_AUTHORITY_REQUIRED",current.authorityStatus());
    }
    @Test void cutoffEqualityMovesBusinessDateForwardAndHorizonDoesNotFallback() throws Exception {
        var p=publication(draft());
        assertEquals("2026-10-23",evaluate(p,null,null,1792774799999L,FinancialCalendarEvaluationMode.HISTORICAL).chosenDate());
        assertEquals("2026-10-26",evaluate(p,null,null,1792774800000L,FinancialCalendarEvaluationMode.HISTORICAL).chosenDate());
        assertEquals("OUTSIDE_PUBLISHED_HORIZON",evaluate(p,"2026-10-28",FinancialCalendarRollConvention.FOLLOWING,null,FinancialCalendarEvaluationMode.HISTORICAL).authorityStatus());
    }
    @Test void expectedReferenceOrDigestMismatchRejectsInsteadOfEvaluatingOtherVersion() throws Exception {
        var p=publication(draft());
        assertThrows(IllegalArgumentException.class,()->new FinancialCalendarEvaluator().evaluate(p,new FinancialCalendarEvaluationRequest(p.publicationId(),2,p.definitionDigest(),FinancialCalendarEvaluationMode.HISTORICAL,FinancialCalendarRollConvention.FOLLOWING,"2026-10-23",null)));
        assertThrows(IllegalArgumentException.class,()->new FinancialCalendarEvaluator().evaluate(p,new FinancialCalendarEvaluationRequest(p.publicationId(),1,"b".repeat(64),FinancialCalendarEvaluationMode.HISTORICAL,FinancialCalendarRollConvention.FOLLOWING,"2026-10-23",null)));
    }
    @Test void modifiedFollowingOnlySwitchesAfterAnObservedMonthBoundary() throws Exception {
        var d=draft();var r=d.rules();
        var shortRules=new FinancialCalendarRuleDraft("2026-10-23","2026-10-25","UTC",r.weeklyRules(),r.dateOverrides(),r.rollConventions());
        var shortPublication=publication(new CalendarPublicationDraft(null,0,d.grant(),d.source(),shortRules));
        assertEquals("OUTSIDE_PUBLISHED_HORIZON",evaluate(shortPublication,"2026-10-24",FinancialCalendarRollConvention.MODIFIED_FOLLOWING,null,FinancialCalendarEvaluationMode.HISTORICAL).authorityStatus());
        var monthEndRules=new FinancialCalendarRuleDraft("2026-10-30","2026-11-03","UTC",r.weeklyRules(),r.dateOverrides(),r.rollConventions());
        var monthEnd=publication(new CalendarPublicationDraft(null,0,d.grant(),d.source(),monthEndRules));
        assertEquals("2026-10-30",evaluate(monthEnd,"2026-10-31",FinancialCalendarRollConvention.MODIFIED_FOLLOWING,null,FinancialCalendarEvaluationMode.HISTORICAL).chosenDate());
    }

}
