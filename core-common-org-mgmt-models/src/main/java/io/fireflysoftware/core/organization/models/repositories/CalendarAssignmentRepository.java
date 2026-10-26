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

import io.fireflysoftware.core.organization.models.entities.CalendarAssignment;
import io.fireflysoftware.core.organization.models.repositories.scoped.CalendarAssignmentScopedRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Repository for managing {@link CalendarAssignment} entities.
 */
@Repository
public interface CalendarAssignmentRepository extends BaseRepository<CalendarAssignment, UUID>, CalendarAssignmentScopedRepository {
    
    /**
     * Find all assignments for a specific calendar.
     *
     * @param calendarId the calendar ID
     * @return a Flux emitting all assignments for the specified calendar
     */
    Flux<CalendarAssignment> findByCalendarId(UUID calendarId);
    
    /**
     * Find all assignments for a specific branch.
     *
     * @param branchId the branch ID
     * @return a Flux emitting all assignments for the specified branch
     */
    Flux<CalendarAssignment> findByBranchId(UUID branchId);
    
    /**
     * Find all assignments for a specific department.
     *
     * @param departmentId the department ID
     * @return a Flux emitting all assignments for the specified department
     */
    Flux<CalendarAssignment> findByDepartmentId(UUID departmentId);
    
    /**
     * Find all assignments for a specific position.
     *
     * @param positionId the position ID
     * @return a Flux emitting all assignments for the specified position
     */
    Flux<CalendarAssignment> findByPositionId(UUID positionId);
    
    /**
     * Find all active assignments for a specific calendar.
     *
     * @param calendarId the calendar ID
     * @return a Flux emitting all active assignments for the specified calendar
     */
    Flux<CalendarAssignment> findByCalendarIdAndIsActiveTrue(UUID calendarId);
    
    /**
     * Find all active assignments for a specific branch.
     *
     * @param branchId the branch ID
     * @return a Flux emitting all active assignments for the specified branch
     */
    Flux<CalendarAssignment> findByBranchIdAndIsActiveTrue(UUID branchId);
    
    /**
     * Find all assignments effective at a specific date.
     *
     * @param date the date to check
     * @return a Flux emitting all assignments effective at the specified date
     */
    Flux<CalendarAssignment> findByEffectiveFromBeforeAndEffectiveToAfterOrEffectiveToIsNull(
            LocalDateTime date, LocalDateTime sameDate);
}