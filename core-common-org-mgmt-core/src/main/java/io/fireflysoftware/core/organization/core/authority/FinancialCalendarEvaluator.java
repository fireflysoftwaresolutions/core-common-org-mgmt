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
import java.time.LocalDate;

public final class FinancialCalendarEvaluator {
    public FinancialCalendarEvaluation evaluate(FinancialCalendarPublication publication,FinancialCalendarEvaluationRequest request) {
        if(publication==null || request==null || request.mode()==null) throw AuthorityWire.bad();
        var actual=OrganizationAuthorityIntentCodec.publication(publication.publicationId(),publication.version(),publication.preparedAtEpochMillis(),publication.definition());
        if(!actual.canonicalDefinition().equals(publication.canonicalDefinition()) || !actual.definitionDigest().equals(publication.definitionDigest()) || !publication.publicationId().equals(request.publicationId()) || publication.version()!=request.version() || !publication.definitionDigest().equals(request.digest())) throw AuthorityWire.bad();
        var days=publication.definition().days();int index=-1;int chosen=-1;
        if(request.date()!=null && request.instantEpochMillis()==null && request.roll()!=null) {
            var date=FinancialCalendarRules.date(request.date());
            if(!publication.definition().rules().rollConventions().contains(request.roll())) throw AuthorityWire.bad();
            for(int i=0;i<days.size();i++) if(days.get(i).date().equals(date.toString())) { index=i;break; }
            if(index>=0) {
                chosen=switch(request.roll()) {
                    case UNADJUSTED -> index;
                    case FOLLOWING -> seek(publication,index,1,null);
                    case PRECEDING -> seek(publication,index,-1,null);
                    case MODIFIED_FOLLOWING -> {
                        int following=seek(publication,index,1,null);
                        yield following<0?-1:LocalDate.parse(days.get(following).date()).getMonth()!=date.getMonth()?seek(publication,index,-1,null):following;
                    }
                };
            }
        } else if(request.date()==null && request.instantEpochMillis()!=null && request.roll()==null) {
            for(int i=0;i<days.size();i++) if(request.instantEpochMillis()>=days.get(i).localDayStartEpochMillis() && request.instantEpochMillis()<days.get(i).nextLocalDayStartEpochMillis()) { index=i;break; }
            if(index>=0) chosen=seek(publication,index,1,request.instantEpochMillis());
        } else throw AuthorityWire.bad();
        if(chosen<0) return new FinancialCalendarEvaluation(publication.publicationId(),publication.version(),publication.definitionDigest(),request.mode(),"OUTSIDE_PUBLISHED_HORIZON",null,false,null,index<0?null:days.get(index).date(),null);
        var day=days.get(chosen);var status=request.mode()==FinancialCalendarEvaluationMode.HISTORICAL?"HISTORICAL_ONLY":"CURRENT_AUTHORITY_REQUIRED";
        return new FinancialCalendarEvaluation(publication.publicationId(),publication.version(),publication.definitionDigest(),request.mode(),status,day.date(),day.working(),day.cutoffEpochMillis(),days.get(Math.min(index,chosen)).date(),days.get(Math.max(index,chosen)).date());
    }
    private static int seek(FinancialCalendarPublication publication,int index,int direction,Long instant) {
        var days=publication.definition().days();
        for(int i=index;i>=0 && i<days.size();i+=direction) if(days.get(i).working() && (instant==null || days.get(i).cutoffEpochMillis()>instant)) return i;
        return -1;
    }
}
