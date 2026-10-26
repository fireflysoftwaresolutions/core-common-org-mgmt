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

import io.fireflysoftware.core.organization.models.entities.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.r2dbc.core.R2dbcEntityOperations;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.data.relational.core.query.Query;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ScopedRelatedRepositoryTest {
    @Test void branchesKeepBank() throws Exception { check(Branch.class, "Bank", "bank_id"); }
    @Test void regionsKeepDivision() throws Exception { check(BankRegion.class, "Division", "division_id"); }
    @Test void departmentsKeepBranch() throws Exception { check(BranchDepartment.class, "Branch", "branch_id"); }
    @Test void positionsKeepDepartment() throws Exception { check(BranchPosition.class, "Department", "department_id"); }
    @Test void hoursKeepBranch() throws Exception { check(BranchHours.class, "Branch", "branch_id"); }

    private <E> void check(Class<E> entityType, String scope, String column) throws Exception {
        var operations = mock(R2dbcEntityOperations.class, RETURNS_DEEP_STUBS);
        var selection = operations.select(entityType);
        when(selection.matching(any(Query.class)).all()).thenReturn(Flux.empty());
        when(selection.matching(any(Query.class)).count()).thenReturn(Mono.just(0L));
        clearInvocations(selection);
        var implementation = Class.forName(getClass().getPackageName() + ".impl." + entityType.getSimpleName() + "ScopedRepositoryImpl");
        var repository = implementation.getConstructor(R2dbcEntityOperations.class).newInstance(operations);
        var parent = UUID.fromString("00000000-0000-0000-0000-000000000001");
        var client = Criteria.where("id").is(UUID.randomUUID()).or("id").is(UUID.randomUUID());
        var data = implementation.getMethod("findPageFor" + scope, UUID.class, Criteria.class, org.springframework.data.domain.Pageable.class);
        var count = implementation.getMethod("countFor" + scope, UUID.class, Criteria.class);
        ((Flux<?>) data.invoke(repository, parent, client, PageRequest.of(0, 5))).collectList().block();
        ((Mono<?>) count.invoke(repository, parent, client)).block();
        var captured = ArgumentCaptor.forClass(Query.class);
        verify(selection, times(2)).matching(captured.capture());
        var queries = captured.getAllValues();
        String dataSql = ScopedParentRepositoryTest.render(entityType, queries.get(0));
        assertEquals(dataSql, ScopedParentRepositoryTest.render(entityType, queries.get(1)));
        assertTrue(dataSql.contains(column), dataSql);
        assertTrue(dataSql.contains(" AND "), dataSql);
        assertTrue(dataSql.contains(" OR "), dataSql);
        assertTrue(queries.get(0).isLimited());
        assertFalse(queries.get(1).isLimited());
    }
}
