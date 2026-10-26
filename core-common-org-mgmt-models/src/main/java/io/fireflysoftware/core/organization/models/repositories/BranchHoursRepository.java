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

import io.fireflysoftware.core.organization.interfaces.enums.DayOfWeek;
import io.fireflysoftware.core.organization.models.entities.BranchHours;
import io.fireflysoftware.core.organization.models.repositories.scoped.BranchHoursScopedRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.util.UUID;

/**
 * Repository for managing {@link BranchHours} entities.
 */
@Repository
public interface BranchHoursRepository extends BaseRepository<BranchHours, UUID>, BranchHoursScopedRepository {
    
    /**
     * Find all hours for a specific branch.
     *
     * @param branchId the branch ID
     * @return a Flux emitting all hours for the specified branch
     */
    Flux<BranchHours> findByBranchId(UUID branchId);
    
    /**
     * Find hours for a specific branch and day of week.
     *
     * @param branchId the branch ID
     * @param dayOfWeek the day of week
     * @return a Mono emitting the hours if found, or empty if not found
     */
    Mono<BranchHours> findByBranchIdAndDayOfWeek(UUID branchId, DayOfWeek dayOfWeek);
    
    /**
     * Find all closed days for a specific branch.
     *
     * @param branchId the branch ID
     * @return a Flux emitting all closed days for the specified branch
     */
    Flux<BranchHours> findByBranchIdAndIsClosedTrue(UUID branchId);
    
    /**
     * Find all open days for a specific branch.
     *
     * @param branchId the branch ID
     * @return a Flux emitting all open days for the specified branch
     */
    Flux<BranchHours> findByBranchIdAndIsClosedFalse(UUID branchId);
    
    /**
     * Find all hours for a specific day of week across all branches.
     *
     * @param dayOfWeek the day of week
     * @return a Flux emitting all hours for the specified day of week
     */
    Flux<BranchHours> findByDayOfWeek(DayOfWeek dayOfWeek);
}