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
import java.time.temporal.ChronoUnit;
import java.time.zone.ZoneRulesProvider;
import java.util.*;

final class FinancialCalendarRules {
    private FinancialCalendarRules() {}

    static LocalDate date(String value) {
        try {
            var date=LocalDate.parse(value);
            if (date.getYear()<1900 || date.getYear()>9999 || !date.toString().equals(value)) throw AuthorityWire.bad();
            return date;
        } catch (Exception e) { throw AuthorityWire.bad(); }
    }
    static LocalTime time(String value) {
        try {
            if(value==null || !value.matches("[0-9]{2}:[0-9]{2}:[0-9]{2}")) throw AuthorityWire.bad();
            return LocalTime.parse(value);
        } catch(Exception e) { throw AuthorityWire.bad(); }
    }
    static void validate(FinancialCalendarRuleDraft r) {
        if(r==null) throw AuthorityWire.bad();
        var from=date(r.validFromDate());var to=date(r.validToDateExclusive());var count=ChronoUnit.DAYS.between(from,to);
        if(count<1 || count>3663) throw AuthorityWire.bad();
        AuthorityWire.text(r.ianaZoneId(),100);
        if(!ZoneId.getAvailableZoneIds().contains(r.ianaZoneId())) throw AuthorityWire.bad();
        if(r.weeklyRules().size()!=7) throw AuthorityWire.bad();
        for(int i=0;i<7;i++) { var rule=r.weeklyRules().get(i);if(rule.weekday()!=i+1) throw AuthorityWire.bad();validateWorking(rule.working(),rule.cutoffLocalTime(),null); }
        if(r.dateOverrides().size()>count) throw AuthorityWire.bad();
        LocalDate previous=null;
        for(var override:r.dateOverrides()) {
            var d=date(override.date());
            if(d.isBefore(from) || !d.isBefore(to) || previous!=null && !d.isAfter(previous)) throw AuthorityWire.bad();
            validateWorking(override.working(),override.cutoffLocalTime(),override.cutoffOffsetSeconds());AuthorityWire.text(override.reason(),500);previous=d;
        }
        if(r.rollConventions().isEmpty() || new HashSet<>(r.rollConventions()).size()!=r.rollConventions().size()) throw AuthorityWire.bad();
        for(int i=1;i<r.rollConventions().size();i++) if(r.rollConventions().get(i-1).ordinal()>=r.rollConventions().get(i).ordinal()) throw AuthorityWire.bad();
    }
    private static void validateWorking(boolean working,String cutoff,Integer offset) {
        if(working) { time(cutoff);if(offset!=null && (offset < -64800 || offset > 64800)) throw AuthorityWire.bad(); }
        else if(cutoff!=null || offset!=null) throw AuthorityWire.bad();
    }
    static String tzdbVersion(String zone) { return ZoneRulesProvider.getVersions(zone).lastKey(); }

    static List<FinancialCalendarDay> materialize(FinancialCalendarRuleDraft rules,Set<LocalDate> holidays) {
        validate(rules);var zone=ZoneId.of(rules.ianaZoneId());var result=new ArrayList<FinancialCalendarDay>();
        var overrides=new HashMap<LocalDate,FinancialCalendarDateOverride>();for(var o:rules.dateOverrides()) overrides.put(date(o.date()),o);
        for(var d=date(rules.validFromDate());d.isBefore(date(rules.validToDateExclusive()));d=d.plusDays(1)) {
            var start=d.atStartOfDay(zone);var end=d.plusDays(1).atStartOfDay(zone);
            if(!start.toLocalDate().equals(d) || !end.toInstant().isAfter(start.toInstant())) throw AuthorityWire.bad();
            var weekly=rules.weeklyRules().get(d.getDayOfWeek().getValue()-1);var override=overrides.get(d);
            boolean working=override!=null?override.working():weekly.working()&&!holidays.contains(d);
            var cutoff=working?(override!=null?override.cutoffLocalTime():weekly.cutoffLocalTime()):null;
            Long cutoffMillis=null;Integer selected=null;
            if(working) {
                var local=LocalDateTime.of(d,time(cutoff));var offsets=zone.getRules().getValidOffsets(local);
                var explicit=override==null?null:override.cutoffOffsetSeconds();
                if(offsets.isEmpty() || offsets.size()>1&&explicit==null) throw AuthorityWire.bad();
                var offset=explicit==null?offsets.getFirst():ZoneOffset.ofTotalSeconds(explicit);
                if(!offsets.contains(offset)) throw AuthorityWire.bad();
                selected=offset.getTotalSeconds();cutoffMillis=local.toInstant(offset).toEpochMilli();
                if(cutoffMillis<=start.toInstant().toEpochMilli() || cutoffMillis>=end.toInstant().toEpochMilli()) throw AuthorityWire.bad();
            }
            result.add(new FinancialCalendarDay(d.toString(),working,start.toInstant().toEpochMilli(),end.toInstant().toEpochMilli(),cutoff,cutoffMillis,selected));
        }
        return List.copyOf(result);
    }

    static void validateRetainedDays(FinancialCalendarDefinition definition) {
        var rules=definition.rules();validate(rules);var expected=date(rules.validFromDate());
        if(definition.days().size()!=ChronoUnit.DAYS.between(expected,date(rules.validToDateExclusive()))) throw AuthorityWire.bad();
        var overrides=new HashMap<LocalDate,FinancialCalendarDateOverride>();for(var o:rules.dateOverrides()) overrides.put(date(o.date()),o);
        var holidays=definition.source() instanceof LegacyBankCalendarSource legacy?LegacyBankCalendarSourcePreparer.holidays(legacy.snapshot(),rules):Set.<LocalDate>of();
        Long previousEnd=null;
        for(var day:definition.days()) {
            var weekly=rules.weeklyRules().get(expected.getDayOfWeek().getValue()-1);var override=overrides.get(expected);
            boolean expectedWorking=override!=null?override.working():weekly.working()&&!holidays.contains(expected);
            var expectedCutoff=expectedWorking?(override!=null?override.cutoffLocalTime():weekly.cutoffLocalTime()):null;
            if(day.working()!=expectedWorking || !Objects.equals(day.cutoffLocalTime(),expectedCutoff) || override!=null&&override.cutoffOffsetSeconds()!=null&&!override.cutoffOffsetSeconds().equals(day.selectedOffsetSeconds())) throw AuthorityWire.bad();
            if(!date(day.date()).equals(expected) || day.localDayStartEpochMillis()>=day.nextLocalDayStartEpochMillis() || previousEnd!=null&&previousEnd!=day.localDayStartEpochMillis()) throw AuthorityWire.bad();
            validateWorking(day.working(),day.cutoffLocalTime(),day.selectedOffsetSeconds());
            if(day.working()) {
                if(day.cutoffEpochMillis()==null || day.selectedOffsetSeconds()==null || day.cutoffEpochMillis()<=day.localDayStartEpochMillis() || day.cutoffEpochMillis()>=day.nextLocalDayStartEpochMillis()) throw AuthorityWire.bad();
                var local=Instant.ofEpochMilli(day.cutoffEpochMillis()).atOffset(ZoneOffset.ofTotalSeconds(day.selectedOffsetSeconds())).toLocalDateTime();
                if(!local.equals(LocalDateTime.of(expected,time(day.cutoffLocalTime())))) throw AuthorityWire.bad();
            } else if(day.cutoffEpochMillis()!=null) throw AuthorityWire.bad();
            previousEnd=day.nextLocalDayStartEpochMillis();expected=expected.plusDays(1);
        }
    }
}
