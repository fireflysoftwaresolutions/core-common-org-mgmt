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

import io.fireflysoftware.core.organization.models.entities.BankRegion;
import io.fireflysoftware.core.organization.models.repositories.scoped.BankRegionScopedRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.util.UUID;

/**
 * Repository for managing {@link BankRegion} entities.
 */
@Repository
public interface BankRegionRepository extends BaseRepository<BankRegion, UUID>, BankRegionScopedRepository {
    
    /**
     * Find all regions belonging to a specific division.
     *
     * @param divisionId the division ID
     * @return a Flux emitting all regions for the specified division
     */
    Flux<BankRegion> findByDivisionId(UUID divisionId);
    
    /**
     * Find a region by its division ID and code.
     *
     * @param divisionId the division ID
     * @param code the region code
     * @return a Mono emitting the region if found, or empty if not found
     */
    Mono<BankRegion> findByDivisionIdAndCode(UUID divisionId, String code);
    
    /**
     * Find all active regions belonging to a specific division.
     *
     * @param divisionId the division ID
     * @return a Flux emitting all active regions for the specified division
     */
    Flux<BankRegion> findByDivisionIdAndIsActiveTrue(UUID divisionId);
    
    /**
     * Find a region by its name.
     *
     * @param name the region name
     * @return a Mono emitting the region if found, or empty if not found
     */
    Mono<BankRegion> findByName(String name);
}