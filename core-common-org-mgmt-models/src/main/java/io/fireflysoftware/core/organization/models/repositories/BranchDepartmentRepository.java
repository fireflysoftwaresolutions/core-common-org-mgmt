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

import io.fireflysoftware.core.organization.models.entities.BranchDepartment;
import io.fireflysoftware.core.organization.models.repositories.scoped.BranchDepartmentScopedRepository;
import org.springframework.data.relational.repository.Lock;
import org.springframework.data.relational.core.sql.LockMode;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.util.UUID;

/**
 * Repository for managing {@link BranchDepartment} entities.
 */
@Repository
public interface BranchDepartmentRepository extends BaseRepository<BranchDepartment, UUID>, BranchDepartmentScopedRepository {
    @Lock(LockMode.PESSIMISTIC_READ)
    Mono<BranchDepartment> findByIdAndBranchId(UUID id, UUID branchId);

    
    /**
     * Find all departments belonging to a specific branch.
     *
     * @param branchId the branch ID
     * @return a Flux emitting all departments for the specified branch
     */
    Flux<BranchDepartment> findByBranchId(UUID branchId);
    
    /**
     * Find all active departments belonging to a specific branch.
     *
     * @param branchId the branch ID
     * @return a Flux emitting all active departments for the specified branch
     */
    Flux<BranchDepartment> findByBranchIdAndIsActiveTrue(UUID branchId);
    
    /**
     * Find a department by its name and branch ID.
     *
     * @param name the department name
     * @param branchId the branch ID
     * @return a Mono emitting the department if found, or empty if not found
     */
    Mono<BranchDepartment> findByNameAndBranchId(String name, UUID branchId);
    
    /**
     * Find a department by its name.
     *
     * @param name the department name
     * @return a Mono emitting the department if found, or empty if not found
     */
    Mono<BranchDepartment> findByName(String name);
}