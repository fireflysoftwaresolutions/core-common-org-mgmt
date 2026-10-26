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


package io.fireflysoftware.core.organization.models.repositories.scoped.impl;

import org.springframework.data.domain.Pageable;
import org.springframework.data.r2dbc.core.R2dbcEntityOperations;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.data.relational.core.query.Query;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.util.UUID;

abstract class ScopedRepositoryOperations<E> {
    private final R2dbcEntityOperations operations;
    private final Class<E> entityType;
    private final String parentProperty;

    ScopedRepositoryOperations(R2dbcEntityOperations operations, Class<E> entityType, String parentProperty) {
        this.operations = operations;
        this.entityType = entityType;
        this.parentProperty = parentProperty;
    }

    final Flux<E> findPage(UUID parentId, Criteria clientCriteria, Pageable pageable) {
        Query query = scopedQuery(parentId, clientCriteria).with(pageable);
        return operations.select(entityType).matching(query).all();
    }

    final Mono<Long> count(UUID parentId, Criteria clientCriteria) {
        Query query = scopedQuery(parentId, clientCriteria);
        return operations.select(entityType).matching(query).count();
    }

    private Query scopedQuery(UUID parentId, Criteria clientCriteria) {
        if (parentId == null) {
            throw new IllegalArgumentException("Parent ID is required");
        }
        Criteria parent = Criteria.where(parentProperty).is(parentId);
        return Query.query(clientCriteria == null || clientCriteria.isEmpty()
                ? parent : Criteria.from(parent, clientCriteria));
    }
}
