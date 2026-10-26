/*
 * Copyright 2025 Firefly Software Foundation
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


package io.fireflysoftware.core.organization.core.services;

import io.fireflysoftware.core.organization.core.services.impl.CalendarAssignmentServiceImpl;
import io.fireflysoftware.core.organization.core.services.impl.WorkingCalendarServiceImpl;
import io.fireflysoftware.core.organization.core.mappers.CalendarAssignmentMapper;
import io.fireflysoftware.core.organization.core.mappers.WorkingCalendarMapper;
import io.fireflysoftware.core.organization.interfaces.dtos.*;
import io.fireflysoftware.core.organization.models.entities.*;
import io.fireflysoftware.core.organization.models.repositories.*;
import org.fireflyframework.core.filters.FilterUtils;
import org.junit.jupiter.api.Test;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ScopedCalendarServiceTest {
    @Test void calendarFilterUsesBothScopedRepositoryQueries() {
        FilterUtils.initializeTemplate(mock(R2dbcEntityTemplate.class));
        var repository = mock(WorkingCalendarRepository.class);
        var bankService = mock(BankService.class);
        var mapper = mock(WorkingCalendarMapper.class);
        var service = new WorkingCalendarServiceImpl();
        ReflectionTestUtils.setField(service, "repository", repository);
        ReflectionTestUtils.setField(service, "bankService", bankService);
        ReflectionTestUtils.setField(service, "mapper", mapper);
        UUID bankId = UUID.randomUUID();
        when(bankService.getBankById(bankId)).thenReturn(Mono.just(BankDTO.builder().id(bankId).build()));
        when(repository.findPageForBank(eq(bankId), any(), any())).thenReturn(Flux.empty());
        when(repository.countForBank(eq(bankId), any())).thenReturn(Mono.just(7L));
        StepVerifier.create(service.filterWorkingCalendarsForBank(bankId, null))
                .assertNext(page -> assertEquals(7L, page.getTotalElements())).verifyComplete();
        verify(repository).findPageForBank(eq(bankId), any(), any());
        verify(repository).countForBank(eq(bankId), any());
        verifyNoMoreInteractions(repository);
    }

    @Test void assignmentFilterRequiresBankBoundCalendarBeforeBothScopedQueries() {
        FilterUtils.initializeTemplate(mock(R2dbcEntityTemplate.class));
        var repository = mock(CalendarAssignmentRepository.class);
        var bankService = mock(BankService.class);
        var calendarService = mock(WorkingCalendarService.class);
        var service = new CalendarAssignmentServiceImpl();
        ReflectionTestUtils.setField(service, "repository", repository);
        ReflectionTestUtils.setField(service, "mapper", mock(CalendarAssignmentMapper.class));
        ReflectionTestUtils.setField(service, "bankService", bankService);
        ReflectionTestUtils.setField(service, "workingCalendarService", calendarService);
        UUID bankId = UUID.randomUUID(), calendarId = UUID.randomUUID();
        when(calendarService.getWorkingCalendarByIdForBank(bankId, calendarId)).thenReturn(Mono.just(WorkingCalendarDTO.builder().id(calendarId).bankId(bankId).build()));
        when(repository.findPageForCalendar(eq(calendarId), any(), any())).thenReturn(Flux.empty());
        when(repository.countForCalendar(eq(calendarId), any())).thenReturn(Mono.just(3L));
        StepVerifier.create(service.filterCalendarAssignmentsForCalendar(bankId, calendarId, null))
                .assertNext(page -> assertEquals(3L, page.getTotalElements())).verifyComplete();
        verify(calendarService).getWorkingCalendarByIdForBank(bankId, calendarId);
        verifyNoInteractions(bankService);
        verify(repository).findPageForCalendar(eq(calendarId), any(), any());
        verify(repository).countForCalendar(eq(calendarId), any());
        verifyNoMoreInteractions(repository);
    }

    @Test void missingBankBoundCalendarDoesNotQueryAssignments() {
        var repository = mock(CalendarAssignmentRepository.class);
        var calendarService = mock(WorkingCalendarService.class);
        var service = new CalendarAssignmentServiceImpl();
        ReflectionTestUtils.setField(service, "repository", repository);
        ReflectionTestUtils.setField(service, "workingCalendarService", calendarService);
        ReflectionTestUtils.setField(service, "bankService", mock(BankService.class));
        UUID bankId = UUID.randomUUID(), calendarId = UUID.randomUUID();
        when(calendarService.getWorkingCalendarByIdForBank(bankId, calendarId)).thenReturn(Mono.error(new RuntimeException("Calendar not found for bank with ID: " + bankId)));
        StepVerifier.create(Mono.defer(() -> service.filterCalendarAssignmentsForCalendar(bankId, calendarId, null)))
                .expectErrorMessage("Calendar not found for bank with ID: " + bankId).verify();
        verifyNoInteractions(repository);
    }
}
