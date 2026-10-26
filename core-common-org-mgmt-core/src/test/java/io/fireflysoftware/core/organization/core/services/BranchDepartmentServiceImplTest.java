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

import io.fireflysoftware.core.organization.core.services.impl.BranchDepartmentServiceImpl;
import org.fireflyframework.core.filters.FilterRequest;
import org.fireflyframework.core.filters.FilterUtils;
import org.fireflyframework.core.queries.PaginationResponse;
import io.fireflysoftware.core.organization.core.mappers.BranchDepartmentMapper;
import io.fireflysoftware.core.organization.interfaces.dtos.BranchDepartmentDTO;
import io.fireflysoftware.core.organization.models.entities.BranchDepartment;
import io.fireflysoftware.core.organization.models.repositories.BranchDepartmentRepository;
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
public class BranchDepartmentServiceImplTest {

    @Mock
    private BranchDepartmentRepository branchDepartmentRepository;

    @Mock
    private BranchDepartmentMapper branchDepartmentMapper;

    @InjectMocks
    private BranchDepartmentServiceImpl branchDepartmentService;

    private BranchDepartmentDTO branchDepartmentDTO;
    private BranchDepartment branchDepartment;

    @BeforeEach
    void setUp() {
        // Setup test data
        LocalDateTime now = LocalDateTime.now();
        
        UUID testId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        UUID testBranchId = UUID.fromString("223e4567-e89b-12d3-a456-426614174000");
        UUID testUserId = UUID.fromString("323e4567-e89b-12d3-a456-426614174000");

        branchDepartmentDTO = BranchDepartmentDTO.builder()
                .id(testId)
                .branchId(testBranchId)
                .name("Customer Service")
                .description("Customer Service Department")
                .isActive(true)
                .createdAt(now)
                .createdBy(testUserId)
                .build();

        branchDepartment = BranchDepartment.builder()
                .id(testId)
                .branchId(testBranchId)
                .name("Customer Service")
                .description("Customer Service Department")
                .isActive(true)
                .createdAt(now)
                .createdBy(testUserId)
                .build();
    }

    @Test
    void createBranchDepartment_ShouldCreateAndReturnBranchDepartment() {
        // Arrange
        when(branchDepartmentMapper.toEntity(branchDepartmentDTO)).thenReturn(branchDepartment);
        when(branchDepartmentRepository.save(branchDepartment)).thenReturn(Mono.just(branchDepartment));
        when(branchDepartmentMapper.toDTO(branchDepartment)).thenReturn(branchDepartmentDTO);

        // Act & Assert
        StepVerifier.create(branchDepartmentService.createBranchDepartment(branchDepartmentDTO))
                .expectNext(branchDepartmentDTO)
                .verifyComplete();

        verify(branchDepartmentMapper).toEntity(branchDepartmentDTO);
        verify(branchDepartmentRepository).save(branchDepartment);
        verify(branchDepartmentMapper).toDTO(branchDepartment);
    }

    @Test
    void updateBranchDepartment_WhenBranchDepartmentExists_ShouldUpdateAndReturnBranchDepartment() {
        // Arrange
        UUID branchDepartmentId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        when(branchDepartmentRepository.findById(branchDepartmentId)).thenReturn(Mono.just(branchDepartment));
        when(branchDepartmentMapper.toEntity(branchDepartmentDTO)).thenReturn(branchDepartment);
        when(branchDepartmentRepository.save(branchDepartment)).thenReturn(Mono.just(branchDepartment));
        when(branchDepartmentMapper.toDTO(branchDepartment)).thenReturn(branchDepartmentDTO);

        // Act & Assert
        StepVerifier.create(branchDepartmentService.updateBranchDepartment(branchDepartmentId, branchDepartmentDTO))
                .expectNext(branchDepartmentDTO)
                .verifyComplete();

        verify(branchDepartmentRepository).findById(branchDepartmentId);
        verify(branchDepartmentMapper).toEntity(branchDepartmentDTO);
        verify(branchDepartmentRepository).save(branchDepartment);
        verify(branchDepartmentMapper).toDTO(branchDepartment);
    }

    @Test
    void updateBranchDepartment_WhenBranchDepartmentDoesNotExist_ShouldReturnError() {
        // Arrange
        UUID branchDepartmentId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        when(branchDepartmentRepository.findById(branchDepartmentId)).thenReturn(Mono.empty());

        // Act & Assert
        StepVerifier.create(branchDepartmentService.updateBranchDepartment(branchDepartmentId, branchDepartmentDTO))
                .expectErrorMatches(throwable -> throwable instanceof RuntimeException &&
                        throwable.getMessage().equals("Branch department not found with ID: " + branchDepartmentId))
                .verify();

        verify(branchDepartmentRepository).findById(branchDepartmentId);
    }

    @Test
    void deleteBranchDepartment_WhenBranchDepartmentExists_ShouldDeleteBranchDepartment() {
        // Arrange
        UUID branchDepartmentId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        when(branchDepartmentRepository.findById(branchDepartmentId)).thenReturn(Mono.just(branchDepartment));
        when(branchDepartmentRepository.deleteById(branchDepartmentId)).thenReturn(Mono.empty());

        // Act & Assert
        StepVerifier.create(branchDepartmentService.deleteBranchDepartment(branchDepartmentId))
                .verifyComplete();

        verify(branchDepartmentRepository).findById(branchDepartmentId);
        verify(branchDepartmentRepository).deleteById(branchDepartmentId);
    }

    @Test
    void deleteBranchDepartment_WhenBranchDepartmentDoesNotExist_ShouldReturnError() {
        // Arrange
        UUID branchDepartmentId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        when(branchDepartmentRepository.findById(branchDepartmentId)).thenReturn(Mono.empty());

        // Act & Assert
        StepVerifier.create(branchDepartmentService.deleteBranchDepartment(branchDepartmentId))
                .expectErrorMatches(throwable -> throwable instanceof RuntimeException &&
                        throwable.getMessage().equals("Branch department not found with ID: " + branchDepartmentId))
                .verify();

        verify(branchDepartmentRepository).findById(branchDepartmentId);
    }

    @Test
    void getBranchDepartmentById_WhenBranchDepartmentExists_ShouldReturnBranchDepartment() {
        // Arrange
        UUID branchDepartmentId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        when(branchDepartmentRepository.findById(branchDepartmentId)).thenReturn(Mono.just(branchDepartment));
        when(branchDepartmentMapper.toDTO(branchDepartment)).thenReturn(branchDepartmentDTO);

        // Act & Assert
        StepVerifier.create(branchDepartmentService.getBranchDepartmentById(branchDepartmentId))
                .expectNext(branchDepartmentDTO)
                .verifyComplete();

        verify(branchDepartmentRepository).findById(branchDepartmentId);
        verify(branchDepartmentMapper).toDTO(branchDepartment);
    }

    @Test
    void getBranchDepartmentById_WhenBranchDepartmentDoesNotExist_ShouldReturnError() {
        // Arrange
        UUID branchDepartmentId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        when(branchDepartmentRepository.findById(branchDepartmentId)).thenReturn(Mono.empty());

        // Act & Assert
        StepVerifier.create(branchDepartmentService.getBranchDepartmentById(branchDepartmentId))
                .expectErrorMatches(throwable -> throwable instanceof RuntimeException &&
                        throwable.getMessage().equals("Branch department not found with ID: " + branchDepartmentId))
                .verify();

        verify(branchDepartmentRepository).findById(branchDepartmentId);
    }
}