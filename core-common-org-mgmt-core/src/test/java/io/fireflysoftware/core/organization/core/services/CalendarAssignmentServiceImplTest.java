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


package io.fireflysoftware.core.organization.core.services;

import io.fireflysoftware.core.organization.core.services.impl.CalendarAssignmentServiceImpl;
import org.fireflyframework.core.filters.FilterRequest;
import org.fireflyframework.core.filters.FilterUtils;
import org.fireflyframework.core.queries.PaginationResponse;
import io.fireflysoftware.core.organization.core.mappers.CalendarAssignmentMapper;
import io.fireflysoftware.core.organization.interfaces.dtos.CalendarAssignmentDTO;
import io.fireflysoftware.core.organization.models.entities.CalendarAssignment;
import io.fireflysoftware.core.organization.models.repositories.CalendarAssignmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.function.Function;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.util.UUID;

@ExtendWith(MockitoExtension.class)
public class CalendarAssignmentServiceImplTest {

    @Mock
    private CalendarAssignmentRepository calendarAssignmentRepository;

    @Mock
    private CalendarAssignmentMapper calendarAssignmentMapper;

    @InjectMocks
    private CalendarAssignmentServiceImpl calendarAssignmentService;

    private CalendarAssignmentDTO calendarAssignmentDTO;
    private CalendarAssignment calendarAssignment;

    @BeforeEach
    void setUp() {
        // Setup test data
        LocalDateTime now = LocalDateTime.now();
        
        UUID testId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        UUID testCalendarId = UUID.fromString("223e4567-e89b-12d3-a456-426614174000");
        UUID testBranchId = UUID.fromString("323e4567-e89b-12d3-a456-426614174000");
        UUID testDepartmentId = UUID.fromString("423e4567-e89b-12d3-a456-426614174000");
        UUID testUserId = UUID.fromString("523e4567-e89b-12d3-a456-426614174000");

        calendarAssignmentDTO = CalendarAssignmentDTO.builder()
                .id(testId)
                .calendarId(testCalendarId)
                .branchId(testBranchId)
                .departmentId(testDepartmentId)
                .positionId(null) // Optional field
                .effectiveFrom(now)
                .effectiveTo(now.plusYears(1))
                .isActive(true)
                .createdAt(now)
                .createdBy(testUserId)
                .build();

        calendarAssignment = CalendarAssignment.builder()
                .id(testId)
                .calendarId(testCalendarId)
                .branchId(testBranchId)
                .departmentId(testDepartmentId)
                .positionId(null) // Optional field
                .effectiveFrom(now)
                .effectiveTo(now.plusYears(1))
                .isActive(true)
                .createdAt(now)
                .createdBy(testUserId)
                .build();
    }

    @Test
    void createCalendarAssignment_ShouldCreateAndReturnCalendarAssignment() {
        // Arrange
        when(calendarAssignmentMapper.toEntity(calendarAssignmentDTO)).thenReturn(calendarAssignment);
        when(calendarAssignmentRepository.save(calendarAssignment)).thenReturn(Mono.just(calendarAssignment));
        when(calendarAssignmentMapper.toDTO(calendarAssignment)).thenReturn(calendarAssignmentDTO);

        // Act & Assert
        StepVerifier.create(calendarAssignmentService.createCalendarAssignment(calendarAssignmentDTO))
                .expectNext(calendarAssignmentDTO)
                .verifyComplete();

        verify(calendarAssignmentMapper).toEntity(calendarAssignmentDTO);
        verify(calendarAssignmentRepository).save(calendarAssignment);
        verify(calendarAssignmentMapper).toDTO(calendarAssignment);
    }

    @Test
    void updateCalendarAssignment_WhenCalendarAssignmentExists_ShouldUpdateAndReturnCalendarAssignment() {
        // Arrange
        UUID calendarAssignmentId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        when(calendarAssignmentRepository.findById(calendarAssignmentId)).thenReturn(Mono.just(calendarAssignment));
        when(calendarAssignmentMapper.toEntity(calendarAssignmentDTO)).thenReturn(calendarAssignment);
        when(calendarAssignmentRepository.save(calendarAssignment)).thenReturn(Mono.just(calendarAssignment));
        when(calendarAssignmentMapper.toDTO(calendarAssignment)).thenReturn(calendarAssignmentDTO);

        // Act & Assert
        StepVerifier.create(calendarAssignmentService.updateCalendarAssignment(calendarAssignmentId, calendarAssignmentDTO))
                .expectNext(calendarAssignmentDTO)
                .verifyComplete();

        verify(calendarAssignmentRepository).findById(calendarAssignmentId);
        verify(calendarAssignmentMapper).toEntity(calendarAssignmentDTO);
        verify(calendarAssignmentRepository).save(calendarAssignment);
        verify(calendarAssignmentMapper).toDTO(calendarAssignment);
    }

    @Test
    void updateCalendarAssignment_WhenCalendarAssignmentDoesNotExist_ShouldReturnError() {
        // Arrange
        UUID calendarAssignmentId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        when(calendarAssignmentRepository.findById(calendarAssignmentId)).thenReturn(Mono.empty());

        // Act & Assert
        StepVerifier.create(calendarAssignmentService.updateCalendarAssignment(calendarAssignmentId, calendarAssignmentDTO))
                .expectErrorMatches(throwable -> throwable instanceof RuntimeException &&
                        throwable.getMessage().equals("Calendar assignment not found with ID: " + calendarAssignmentId))
                .verify();

        verify(calendarAssignmentRepository).findById(calendarAssignmentId);
    }

    @Test
    void deleteCalendarAssignment_WhenCalendarAssignmentExists_ShouldDeleteCalendarAssignment() {
        // Arrange
        UUID calendarAssignmentId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        when(calendarAssignmentRepository.findById(calendarAssignmentId)).thenReturn(Mono.just(calendarAssignment));
        when(calendarAssignmentRepository.deleteById(calendarAssignmentId)).thenReturn(Mono.empty());

        // Act & Assert
        StepVerifier.create(calendarAssignmentService.deleteCalendarAssignment(calendarAssignmentId))
                .verifyComplete();

        verify(calendarAssignmentRepository).findById(calendarAssignmentId);
        verify(calendarAssignmentRepository).deleteById(calendarAssignmentId);
    }

    @Test
    void deleteCalendarAssignment_WhenCalendarAssignmentDoesNotExist_ShouldReturnError() {
        // Arrange
        UUID calendarAssignmentId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        when(calendarAssignmentRepository.findById(calendarAssignmentId)).thenReturn(Mono.empty());

        // Act & Assert
        StepVerifier.create(calendarAssignmentService.deleteCalendarAssignment(calendarAssignmentId))
                .expectErrorMatches(throwable -> throwable instanceof RuntimeException &&
                        throwable.getMessage().equals("Calendar assignment not found with ID: " + calendarAssignmentId))
                .verify();

        verify(calendarAssignmentRepository).findById(calendarAssignmentId);
    }

    @Test
    void getCalendarAssignmentById_WhenCalendarAssignmentExists_ShouldReturnCalendarAssignment() {
        // Arrange
        UUID calendarAssignmentId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        when(calendarAssignmentRepository.findById(calendarAssignmentId)).thenReturn(Mono.just(calendarAssignment));
        when(calendarAssignmentMapper.toDTO(calendarAssignment)).thenReturn(calendarAssignmentDTO);

        // Act & Assert
        StepVerifier.create(calendarAssignmentService.getCalendarAssignmentById(calendarAssignmentId))
                .expectNext(calendarAssignmentDTO)
                .verifyComplete();

        verify(calendarAssignmentRepository).findById(calendarAssignmentId);
        verify(calendarAssignmentMapper).toDTO(calendarAssignment);
    }

    @Test
    void getCalendarAssignmentById_WhenCalendarAssignmentDoesNotExist_ShouldReturnError() {
        // Arrange
        UUID calendarAssignmentId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        when(calendarAssignmentRepository.findById(calendarAssignmentId)).thenReturn(Mono.empty());

        // Act & Assert
        StepVerifier.create(calendarAssignmentService.getCalendarAssignmentById(calendarAssignmentId))
                .expectErrorMatches(throwable -> throwable instanceof RuntimeException &&
                        throwable.getMessage().equals("Calendar assignment not found with ID: " + calendarAssignmentId))
                .verify();

        verify(calendarAssignmentRepository).findById(calendarAssignmentId);
    }
}