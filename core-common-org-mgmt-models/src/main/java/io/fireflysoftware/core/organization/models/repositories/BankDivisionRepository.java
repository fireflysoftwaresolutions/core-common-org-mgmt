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

import io.fireflysoftware.core.organization.models.entities.BankDivision;
import org.springframework.data.relational.repository.Lock;
import org.springframework.data.relational.core.sql.LockMode;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.util.UUID;

/**
 * Repository for managing {@link BankDivision} entities.
 */
@Repository
public interface BankDivisionRepository extends BaseRepository<BankDivision, UUID> {
    @Lock(LockMode.PESSIMISTIC_READ)
    Mono<BankDivision> findByIdAndBankId(UUID id, UUID bankId);

    
    /**
     * Find all divisions belonging to a specific bank.
     *
     * @param bankId the bank ID
     * @return a Flux emitting all divisions for the specified bank
     */
    Flux<BankDivision> findByBankId(UUID bankId);
    
    /**
     * Find a division by its bank ID and code.
     *
     * @param bankId the bank ID
     * @param code the division code
     * @return a Mono emitting the division if found, or empty if not found
     */
    Mono<BankDivision> findByBankIdAndCode(UUID bankId, String code);
    
    /**
     * Find all active divisions belonging to a specific bank.
     *
     * @param bankId the bank ID
     * @return a Flux emitting all active divisions for the specified bank
     */
    Flux<BankDivision> findByBankIdAndIsActiveTrue(UUID bankId);
    
    /**
     * Find a division by its name.
     *
     * @param name the division name
     * @return a Mono emitting the division if found, or empty if not found
     */
    Mono<BankDivision> findByName(String name);
}