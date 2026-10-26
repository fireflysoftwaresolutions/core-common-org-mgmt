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


package io.fireflysoftware.core.organization.models.repositories;

import io.fireflysoftware.core.organization.models.entities.Branch;
import io.fireflysoftware.core.organization.models.repositories.scoped.BranchScopedRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.util.UUID;

/**
 * Repository for managing {@link Branch} entities.
 */
@Repository
public interface BranchRepository extends BaseRepository<Branch, UUID>, BranchScopedRepository {
    
    /**
     * Find all branches belonging to a specific bank.
     *
     * @param bankId the bank ID
     * @return a Flux emitting all branches for the specified bank
     */
    Flux<Branch> findByBankId(UUID bankId);
    
    /**
     * Find all branches belonging to a specific region.
     *
     * @param regionId the region ID
     * @return a Flux emitting all branches for the specified region
     */
    Flux<Branch> findByRegionId(UUID regionId);
    
    /**
     * Find a branch by its bank ID and code.
     *
     * @param bankId the bank ID
     * @param code the branch code
     * @return a Mono emitting the branch if found, or empty if not found
     */
    Mono<Branch> findByBankIdAndCode(UUID bankId, String code);
    
    /**
     * Find all active branches belonging to a specific bank.
     *
     * @param bankId the bank ID
     * @return a Flux emitting all active branches for the specified bank
     */
    Flux<Branch> findByBankIdAndIsActiveTrue(UUID bankId);
    
    /**
     * Find all active branches belonging to a specific region.
     *
     * @param regionId the region ID
     * @return a Flux emitting all active branches for the specified region
     */
    Flux<Branch> findByRegionIdAndIsActiveTrue(UUID regionId);
    
    /**
     * Find a branch by its name.
     *
     * @param name the branch name
     * @return a Mono emitting the branch if found, or empty if not found
     */
    Mono<Branch> findByName(String name);
}