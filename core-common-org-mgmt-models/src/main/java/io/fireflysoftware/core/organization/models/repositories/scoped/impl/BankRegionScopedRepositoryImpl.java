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

import io.fireflysoftware.core.organization.models.repositories.scoped.BankRegionScopedRepository;

import io.fireflysoftware.core.organization.models.entities.BankRegion;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Pageable;
import org.springframework.data.r2dbc.core.R2dbcEntityOperations;
import org.springframework.data.relational.core.query.Criteria;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.util.UUID;

public class BankRegionScopedRepositoryImpl extends ScopedRepositoryOperations<BankRegion> implements BankRegionScopedRepository {
    public BankRegionScopedRepositoryImpl(@Qualifier("r2dbcEntityTemplate") R2dbcEntityOperations operations) {
        super(operations, BankRegion.class, "divisionId");
    }

    @Override
    public Flux<BankRegion> findPageForDivision(UUID parentId, Criteria clientCriteria, Pageable pageable) {
        return findPage(parentId, clientCriteria, pageable);
    }

    @Override
    public Mono<Long> countForDivision(UUID parentId, Criteria clientCriteria) {
        return count(parentId, clientCriteria);
    }
}
