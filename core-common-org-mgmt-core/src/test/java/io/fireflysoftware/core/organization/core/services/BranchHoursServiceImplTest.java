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

import io.fireflysoftware.core.organization.core.services.impl.BranchHoursServiceImpl;
import org.fireflyframework.core.filters.FilterRequest;
import org.fireflyframework.core.filters.FilterUtils;
import org.fireflyframework.core.queries.PaginationResponse;
import io.fireflysoftware.core.organization.core.mappers.BranchHoursMapper;
import io.fireflysoftware.core.organization.interfaces.dtos.BranchHoursDTO;
import io.fireflysoftware.core.organization.interfaces.enums.DayOfWeek;
import io.fireflysoftware.core.organization.models.entities.BranchHours;
import io.fireflysoftware.core.organization.models.repositories.BranchHoursRepository;
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
import java.time.LocalTime;
import java.util.function.Function;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.util.UUID;

@ExtendWith(MockitoExtension.class)
public class BranchHoursServiceImplTest {

    @Mock
    private BranchHoursRepository branchHoursRepository;

    @Mock
    private BranchHoursMapper branchHoursMapper;

    @InjectMocks
    private BranchHoursServiceImpl branchHoursService;

    private BranchHoursDTO branchHoursDTO;
    private BranchHours branchHours;

    @BeforeEach
    void setUp() {
        // Setup test data
        LocalDateTime now = LocalDateTime.now();
        LocalTime openTime = LocalTime.of(9, 0); // 9:00 AM
        LocalTime closeTime = LocalTime.of(17, 0); // 5:00 PM
        
        UUID testId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        UUID testBranchId = UUID.fromString("223e4567-e89b-12d3-a456-426614174000");

        branchHoursDTO = BranchHoursDTO.builder()
                .id(testId)
                .branchId(testBranchId)
                .dayOfWeek(DayOfWeek.MONDAY)
                .openTime(openTime)
                .closeTime(closeTime)
                .isClosed(false)
                .createdAt(now)
                .createdBy(UUID.fromString("323e4567-e89b-12d3-a456-426614174000"))
                .build();

        branchHours = BranchHours.builder()
                .id(testId)
                .branchId(testBranchId)
                .dayOfWeek(DayOfWeek.MONDAY)
                .openTime(openTime)
                .closeTime(closeTime)
                .isClosed(false)
                .createdAt(now)
                .createdBy(UUID.fromString("323e4567-e89b-12d3-a456-426614174000"))
                .build();
    }

    @Test
    void createBranchHours_ShouldCreateAndReturnBranchHours() {
        // Arrange
        when(branchHoursMapper.toEntity(branchHoursDTO)).thenReturn(branchHours);
        when(branchHoursRepository.save(branchHours)).thenReturn(Mono.just(branchHours));
        when(branchHoursMapper.toDTO(branchHours)).thenReturn(branchHoursDTO);

        // Act & Assert
        StepVerifier.create(branchHoursService.createBranchHours(branchHoursDTO))
                .expectNext(branchHoursDTO)
                .verifyComplete();

        verify(branchHoursMapper).toEntity(branchHoursDTO);
        verify(branchHoursRepository).save(branchHours);
        verify(branchHoursMapper).toDTO(branchHours);
    }

    @Test
    void updateBranchHours_WhenBranchHoursExists_ShouldUpdateAndReturnBranchHours() {
        // Arrange
        UUID branchHoursId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        when(branchHoursRepository.findById(branchHoursId)).thenReturn(Mono.just(branchHours));
        when(branchHoursMapper.toEntity(branchHoursDTO)).thenReturn(branchHours);
        when(branchHoursRepository.save(branchHours)).thenReturn(Mono.just(branchHours));
        when(branchHoursMapper.toDTO(branchHours)).thenReturn(branchHoursDTO);

        // Act & Assert
        StepVerifier.create(branchHoursService.updateBranchHours(branchHoursId, branchHoursDTO))
                .expectNext(branchHoursDTO)
                .verifyComplete();

        verify(branchHoursRepository).findById(branchHoursId);
        verify(branchHoursMapper).toEntity(branchHoursDTO);
        verify(branchHoursRepository).save(branchHours);
        verify(branchHoursMapper).toDTO(branchHours);
    }

    @Test
    void updateBranchHours_WhenBranchHoursDoesNotExist_ShouldReturnError() {
        // Arrange
        UUID branchHoursId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        when(branchHoursRepository.findById(branchHoursId)).thenReturn(Mono.empty());

        // Act & Assert
        StepVerifier.create(branchHoursService.updateBranchHours(branchHoursId, branchHoursDTO))
                .expectErrorMatches(throwable -> throwable instanceof RuntimeException &&
                        throwable.getMessage().equals("Branch hours not found with ID: " + branchHoursId))
                .verify();

        verify(branchHoursRepository).findById(branchHoursId);
    }

    @Test
    void deleteBranchHours_WhenBranchHoursExists_ShouldDeleteBranchHours() {
        // Arrange
        UUID branchHoursId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        when(branchHoursRepository.findById(branchHoursId)).thenReturn(Mono.just(branchHours));
        when(branchHoursRepository.deleteById(branchHoursId)).thenReturn(Mono.empty());

        // Act & Assert
        StepVerifier.create(branchHoursService.deleteBranchHours(branchHoursId))
                .verifyComplete();

        verify(branchHoursRepository).findById(branchHoursId);
        verify(branchHoursRepository).deleteById(branchHoursId);
    }

    @Test
    void deleteBranchHours_WhenBranchHoursDoesNotExist_ShouldReturnError() {
        // Arrange
        UUID branchHoursId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        when(branchHoursRepository.findById(branchHoursId)).thenReturn(Mono.empty());

        // Act & Assert
        StepVerifier.create(branchHoursService.deleteBranchHours(branchHoursId))
                .expectErrorMatches(throwable -> throwable instanceof RuntimeException &&
                        throwable.getMessage().equals("Branch hours not found with ID: " + branchHoursId))
                .verify();

        verify(branchHoursRepository).findById(branchHoursId);
    }

    @Test
    void getBranchHoursById_WhenBranchHoursExists_ShouldReturnBranchHours() {
        // Arrange
        UUID branchHoursId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        when(branchHoursRepository.findById(branchHoursId)).thenReturn(Mono.just(branchHours));
        when(branchHoursMapper.toDTO(branchHours)).thenReturn(branchHoursDTO);

        // Act & Assert
        StepVerifier.create(branchHoursService.getBranchHoursById(branchHoursId))
                .expectNext(branchHoursDTO)
                .verifyComplete();

        verify(branchHoursRepository).findById(branchHoursId);
        verify(branchHoursMapper).toDTO(branchHours);
    }

    @Test
    void getBranchHoursById_WhenBranchHoursDoesNotExist_ShouldReturnError() {
        // Arrange
        UUID branchHoursId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        when(branchHoursRepository.findById(branchHoursId)).thenReturn(Mono.empty());

        // Act & Assert
        StepVerifier.create(branchHoursService.getBranchHoursById(branchHoursId))
                .expectErrorMatches(throwable -> throwable instanceof RuntimeException &&
                        throwable.getMessage().equals("Branch hours not found with ID: " + branchHoursId))
                .verify();

        verify(branchHoursRepository).findById(branchHoursId);
    }
}