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
import java.time.*;
import java.util.*;

public final class LegacyBankCalendarSourcePreparer {
    public FinancialCalendarDefinition prepare(String tenantId,String institutionId,String issuerAuthorityId,CalendarPublicationDraft draft,FinancialCalendarSourceSnapshot snapshot) {
        OrganizationAuthorityIntentCodec.validateDraft(tenantId,institutionId,issuerAuthorityId,draft);
        if(!(draft.source() instanceof LegacyBankCalendarDraft source)) throw AuthorityWire.bad();
        var digest=OrganizationAuthorityIntentCodec.sourceSnapshotDigest(snapshot);
        if(!digest.equals(source.expectedSourceSnapshotDigest()) || !source.workingCalendarId().equals(snapshot.workingCalendarId()) || !source.target().equals(snapshot.target())) throw AuthorityWire.bad();
        validateSnapshotFacts(snapshot,draft.rules(),null);
        var retained=new LegacyBankCalendarSource("LEGACY_BANK_CALENDAR",source.bankEnrollmentId(),source.enrollmentVersion(),source.enrollmentDigest(),source.workingCalendarId(),digest,snapshot);
        return new FinancialCalendarDefinition(1,tenantId,institutionId,issuerAuthorityId,draft.grant(),source.target(),retained,draft.rules(),FinancialCalendarRules.tzdbVersion(draft.rules().ianaZoneId()),FinancialCalendarRules.materialize(draft.rules(),holidays(snapshot,draft.rules())));
    }

    static void validateSnapshot(LegacyBankCalendarSource source,FinancialCalendarDefinition definition) {
        if(!"LEGACY_BANK_CALENDAR".equals(source.profile()) || source.enrollmentVersion()<1) throw AuthorityWire.bad();
        AuthorityWire.uuid(source.bankEnrollmentId());AuthorityWire.digest(source.enrollmentDigest());
        if(!source.workingCalendarId().equals(source.snapshot().workingCalendarId()) || !definition.target().equals(source.snapshot().target()) || !OrganizationAuthorityIntentCodec.sourceSnapshotDigest(source.snapshot()).equals(source.sourceSnapshotDigest())) throw AuthorityWire.bad();
        validateSnapshotFacts(source.snapshot(),definition.rules(),definition.days());
    }
    private static void validateSnapshotFacts(FinancialCalendarSourceSnapshot snapshot,FinancialCalendarRuleDraft rules,List<FinancialCalendarDay> retainedDays) {
        if(snapshot==null || !snapshot.bankActive() || !snapshot.bankId().equals(snapshot.calendarBankId()) || snapshot.target()==null || snapshot.target().kind()==CalendarTargetKind.INSTITUTION) throw AuthorityWire.bad();
        AuthorityWire.uuid(snapshot.bankId());AuthorityWire.uuid(snapshot.workingCalendarId());AuthorityWire.uuid(snapshot.target().id());
        if(snapshot.target().kind()==CalendarTargetKind.BANK) {
            if(!snapshot.bankId().equals(snapshot.target().id()) || !snapshot.ancestry().isEmpty() || snapshot.assignment()!=null || !snapshot.branchHours().isEmpty()) throw AuthorityWire.bad();
        } else {
            validateAncestry(snapshot);
            var assignment=snapshot.assignment();
            if(assignment==null || !assignment.active() || !assignment.calendarId().equals(snapshot.workingCalendarId()) || !assignment.target().equals(snapshot.target())) throw AuthorityWire.bad();
            AuthorityWire.uuid(assignment.id());
            if((assignment.originalEffectiveTo()==null)!=(assignment.effectiveToEpochMillis()==null)) throw AuthorityWire.bad();
            long from;long to;
            if(retainedDays==null) {
                var zone=ZoneId.of(rules.ianaZoneId());
                if(resolve(assignment.originalEffectiveFrom(),zone)!=assignment.effectiveFromEpochMillis()) throw AuthorityWire.bad();
                if(assignment.effectiveToEpochMillis()!=null && resolve(assignment.originalEffectiveTo(),zone)!=assignment.effectiveToEpochMillis()) throw AuthorityWire.bad();
                from=FinancialCalendarRules.date(rules.validFromDate()).atStartOfDay(zone).toInstant().toEpochMilli();
                to=FinancialCalendarRules.date(rules.validToDateExclusive()).atStartOfDay(zone).toInstant().toEpochMilli();
            } else {
                // Retained assignment instants and date boundaries belong to the published tzdb version.
                from=retainedDays.getFirst().localDayStartEpochMillis();
                to=retainedDays.getLast().nextLocalDayStartEpochMillis();
            }
            if(assignment.effectiveFromEpochMillis()>from || assignment.effectiveToEpochMillis()!=null&&assignment.effectiveToEpochMillis()<to) throw AuthorityWire.bad();
            if(snapshot.branchHours().size()!=7) throw AuthorityWire.bad();
            for(int i=0;i<7;i++) {
                var hours=snapshot.branchHours().get(i);if(hours.weekday()!=i+1) throw AuthorityWire.bad();
                if(hours.closed()) { if(hours.openTime()!=null || hours.closeTime()!=null) throw AuthorityWire.bad(); }
                else if(!FinancialCalendarRules.time(hours.openTime()).isBefore(FinancialCalendarRules.time(hours.closeTime()))) throw AuthorityWire.bad();
            }
        }
        if(snapshot.holidays().size()>1024) throw AuthorityWire.bad();
        String previous=null;var branch=snapshot.ancestry().stream().filter(m->m.kind().equals("BRANCH")).map(OrganizationMembershipSnapshot::id).findFirst().orElse(null);
        for(var holiday:snapshot.holidays()) {
            AuthorityWire.uuid(holiday.id());FinancialCalendarRules.date(holiday.date());AuthorityWire.text(holiday.name(),255);
            if(!holiday.bankId().equals(snapshot.bankId()) || holiday.branchId()!=null&&!holiday.branchId().equals(branch) || previous!=null&&previous.compareTo(holiday.id())>=0) throw AuthorityWire.bad();
            if(holiday.countryId()!=null) AuthorityWire.uuid(holiday.countryId());previous=holiday.id();
        }
    }
    private static void validateAncestry(FinancialCalendarSourceSnapshot snapshot) {
        var expectedParents=Map.of("POSITION","DEPARTMENT","DEPARTMENT","BRANCH","BRANCH","REGION","REGION","DIVISION","DIVISION","BANK");
        var remaining=new HashMap<String,OrganizationMembershipSnapshot>();
        for(var member:snapshot.ancestry()) {
            AuthorityWire.uuid(member.id());AuthorityWire.uuid(member.parentId());
            if(!member.active() || !snapshot.bankId().equals(member.bankId()) || !Objects.equals(expectedParents.get(member.kind()),member.parentKind()) || remaining.put(member.kind()+"/"+member.id(),member)!=null) throw AuthorityWire.bad();
        }
        var kind=snapshot.target().kind().name();var id=snapshot.target().id();int depth=0;
        while(!kind.equals("BANK")) {
            var member=remaining.remove(kind+"/"+id);if(member==null || depth++>5) throw AuthorityWire.bad();kind=member.parentKind();id=member.parentId();
        }
        if(!snapshot.bankId().equals(id) || !remaining.isEmpty()) throw AuthorityWire.bad();
    }
    private static long resolve(String local,ZoneId zone) {
        try {
            var parsed=LocalDateTime.parse(local);var offsets=zone.getRules().getValidOffsets(parsed);
            if(offsets.size()!=1) throw AuthorityWire.bad();return parsed.toInstant(offsets.getFirst()).toEpochMilli();
        } catch(Exception e) { throw AuthorityWire.bad(); }
    }
    static Set<LocalDate> holidays(FinancialCalendarSourceSnapshot snapshot,FinancialCalendarRuleDraft rules) {
        var result=new HashSet<LocalDate>();var from=FinancialCalendarRules.date(rules.validFromDate());var to=FinancialCalendarRules.date(rules.validToDateExclusive());
        for(var h:snapshot.holidays()) {
            var date=FinancialCalendarRules.date(h.date());
            if(h.recurring()) {
                var day=MonthDay.from(date);for(int y=from.getYear();y<=to.getYear();y++) if(day.isValidYear(y)) { var actual=day.atYear(y);if(!actual.isBefore(from)&&actual.isBefore(to)) result.add(actual); }
            } else if(!date.isBefore(from)&&date.isBefore(to)) result.add(date);
        }
        return Set.copyOf(result);
    }
}
