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

import io.fireflysoftware.core.organization.models.entities.BranchPosition;
import io.fireflysoftware.core.organization.models.repositories.scoped.BranchPositionScopedRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.util.UUID;

/**
 * Repository for managing {@link BranchPosition} entities.
 */
@Repository
public interface BranchPositionRepository extends BaseRepository<BranchPosition, UUID>, BranchPositionScopedRepository {
    
    /**
     * Find all positions belonging to a specific department.
     *
     * @param departmentId the department ID
     * @return a Flux emitting all positions for the specified department
     */
    Flux<BranchPosition> findByDepartmentId(UUID departmentId);
    
    /**
     * Find all active positions belonging to a specific department.
     *
     * @param departmentId the department ID
     * @return a Flux emitting all active positions for the specified department
     */
    Flux<BranchPosition> findByDepartmentIdAndIsActiveTrue(UUID departmentId);
    
    /**
     * Find a position by its title and department ID.
     *
     * @param title the position title
     * @param departmentId the department ID
     * @return a Mono emitting the position if found, or empty if not found
     */
    Mono<BranchPosition> findByTitleAndDepartmentId(String title, UUID departmentId);
    
    /**
     * Find a position by its title.
     *
     * @param title the position title
     * @return a Mono emitting the position if found, or empty if not found
     */
    Mono<BranchPosition> findByTitle(String title);
}