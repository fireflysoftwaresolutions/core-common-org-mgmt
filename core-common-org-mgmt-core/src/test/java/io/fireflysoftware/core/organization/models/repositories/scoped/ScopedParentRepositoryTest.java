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


package io.fireflysoftware.core.organization.models.repositories.scoped;

import io.fireflysoftware.core.organization.models.entities.CalendarAssignment;
import io.fireflysoftware.core.organization.models.repositories.scoped.impl.CalendarAssignmentScopedRepositoryImpl;
import io.fireflysoftware.core.organization.models.repositories.scoped.impl.WorkingCalendarScopedRepositoryImpl;
import io.fireflysoftware.core.organization.models.entities.WorkingCalendar;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.r2dbc.core.R2dbcEntityOperations;
import org.springframework.data.r2dbc.core.DefaultReactiveDataAccessStrategy;
import org.springframework.data.r2dbc.core.StatementMapper;
import org.springframework.data.r2dbc.dialect.PostgresDialect;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.data.relational.core.query.Query;
import org.springframework.r2dbc.core.PreparedOperation;
import org.springframework.r2dbc.core.binding.BindTarget;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.util.Arrays;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

class ScopedParentRepositoryTest {
    @Test void bankScopeAndGroupedOrAreIdenticalForDataAndCount() {
        var operations = mock(R2dbcEntityOperations.class, RETURNS_DEEP_STUBS);
        var selection = operations.select(WorkingCalendar.class);
        when(selection.matching(any(Query.class)).all()).thenReturn(Flux.empty());
        when(selection.matching(any(Query.class)).count()).thenReturn(Mono.just(0L));
        clearInvocations(selection);
        var repository = new WorkingCalendarScopedRepositoryImpl(operations);
        var bankId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        var client = Criteria.where("name").is("first").or("isDefault").is(true);
        repository.findPageForBank(bankId, client, PageRequest.of(0, 5)).collectList().block();
        repository.countForBank(bankId, client).block();
        var captured = ArgumentCaptor.forClass(Query.class);
        verify(selection, times(2)).matching(captured.capture());
        var queries = captured.getAllValues();
        String sql = render(WorkingCalendar.class, queries.get(0));
        assertEquals(sql, render(WorkingCalendar.class, queries.get(1)));
        assertTrue(sql.contains("WHERE (\"working_calendar\".\"bank_id\" = $1 AND ("), sql);
        assertTrue(sql.endsWith("))"), sql);
        assertTrue(sql.indexOf("bank_id") < sql.indexOf(" AND "), sql);
        assertTrue(sql.indexOf(" OR ") > sql.indexOf(" AND "), sql);
        var bindings = mock(BindTarget.class);
        prepared(WorkingCalendar.class, queries.get(0)).bindTo(bindings);
        verify(bindings).bind(0, bankId);
        verify(bindings).bind(1, "first");
        verify(bindings).bind(2, true);
        verifyNoMoreInteractions(bindings);
        assertTrue(queries.get(0).isLimited());
        assertFalse(queries.get(1).isLimited());
    }

    @Test void absentEmptyAndConflictingCalendarFiltersNeverReplaceParent() {
        var conflictingId = UUID.fromString("00000000-0000-0000-0000-000000000099");
        for (var client : Arrays.asList(null, Criteria.empty(), Criteria.where("calendarId").is(conflictingId))) {
            var operations = mock(R2dbcEntityOperations.class, RETURNS_DEEP_STUBS);
            var selection = operations.select(CalendarAssignment.class);
            when(selection.matching(any(Query.class)).all()).thenReturn(Flux.empty());
            when(selection.matching(any(Query.class)).count()).thenReturn(Mono.just(0L));
            clearInvocations(selection);
            var repository = new CalendarAssignmentScopedRepositoryImpl(operations);
            var parent = UUID.fromString("00000000-0000-0000-0000-000000000002");
            repository.findPageForCalendar(parent, client, PageRequest.of(0, 10)).collectList().block();
            repository.countForCalendar(parent, client).block();
            var captured = ArgumentCaptor.forClass(Query.class);
            verify(selection, times(2)).matching(captured.capture());
            var queries = captured.getAllValues();
            String sql = render(CalendarAssignment.class, queries.get(0));
            assertEquals(sql, render(CalendarAssignment.class, queries.get(1)));
            assertTrue(sql.contains("calendar_id"), sql);
            var bindings = mock(BindTarget.class);
            prepared(CalendarAssignment.class, queries.get(0)).bindTo(bindings);
            verify(bindings).bind(0, parent);
            if (client != null && !client.isEmpty()) {
                assertTrue(sql.contains(" AND "), sql);
                verify(bindings).bind(1, conflictingId);
            }
            verifyNoMoreInteractions(bindings);
        }
    }

    @Test void missingParentCannotBecomeUnscoped() {
        var operations = mock(R2dbcEntityOperations.class);
        var repository = new WorkingCalendarScopedRepositoryImpl(operations);
        assertThrows(IllegalArgumentException.class,
                () -> repository.findPageForBank(null, Criteria.empty(), PageRequest.of(0, 10)));
        assertThrows(IllegalArgumentException.class,
                () -> repository.countForBank(null, Criteria.empty()));
        verifyNoInteractions(operations);
    }

    static String render(Class<?> entity, Query query) {
        return prepared(entity, query).toQuery();
    }

    private static PreparedOperation<?> prepared(Class<?> entity, Query query) {
        var strategy = new DefaultReactiveDataAccessStrategy(PostgresDialect.INSTANCE);
        return strategy.getStatementMapper().forType(entity).getMappedObject(
                StatementMapper.SelectSpec.create(strategy.getTableName(entity))
                        .withProjection("*").withCriteria(query.getCriteria().orElseThrow()));
    }
}
