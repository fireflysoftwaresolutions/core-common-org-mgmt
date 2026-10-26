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

import io.fireflysoftware.core.organization.core.services.impl.BranchAuditLogServiceImpl;
import org.fireflyframework.core.filters.FilterRequest;
import org.fireflyframework.core.filters.FilterUtils;
import org.fireflyframework.core.queries.PaginationResponse;
import io.fireflysoftware.core.organization.core.mappers.BranchAuditLogMapper;
import io.fireflysoftware.core.organization.interfaces.dtos.BranchAuditLogDTO;
import io.fireflysoftware.core.organization.interfaces.enums.AuditAction;
import io.fireflysoftware.core.organization.models.entities.BranchAuditLog;
import io.fireflysoftware.core.organization.models.repositories.BranchAuditLogRepository;
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
public class BranchAuditLogServiceImplTest {

    @Mock
    private BranchAuditLogRepository branchAuditLogRepository;

    @Mock
    private BranchAuditLogMapper branchAuditLogMapper;

    @InjectMocks
    private BranchAuditLogServiceImpl branchAuditLogService;

    private BranchAuditLogDTO branchAuditLogDTO;
    private BranchAuditLog branchAuditLog;

    @BeforeEach
    void setUp() {
        // Setup test data
        LocalDateTime now = LocalDateTime.now();

        UUID testId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        UUID testBranchId = UUID.fromString("223e4567-e89b-12d3-a456-426614174000");
        UUID testUserId = UUID.fromString("323e4567-e89b-12d3-a456-426614174000");

        branchAuditLogDTO = BranchAuditLogDTO.builder()
                .id(testId)
                .branchId(testBranchId)
                .action(AuditAction.CREATED)
                .entity("Branch")
                .entityId("1")
                .metadata("{\"name\":\"Main Branch\",\"code\":\"BR001\"}")
                .ipAddress("192.168.1.1")
                .userId(testUserId)
                .timestamp(now)
                .build();

        branchAuditLog = BranchAuditLog.builder()
                .id(testId)
                .branchId(testBranchId)
                .action(AuditAction.CREATED)
                .entity("Branch")
                .entityId("1")
                .metadata("{\"name\":\"Main Branch\",\"code\":\"BR001\"}")
                .ipAddress("192.168.1.1")
                .userId(testUserId)
                .timestamp(now)
                .build();
    }


    @Test
    void createBranchAuditLog_ShouldCreateAndReturnBranchAuditLog() {
        // Arrange
        when(branchAuditLogMapper.toEntity(branchAuditLogDTO)).thenReturn(branchAuditLog);
        when(branchAuditLogRepository.save(branchAuditLog)).thenReturn(Mono.just(branchAuditLog));
        when(branchAuditLogMapper.toDTO(branchAuditLog)).thenReturn(branchAuditLogDTO);

        // Act & Assert
        StepVerifier.create(branchAuditLogService.createBranchAuditLog(branchAuditLogDTO))
                .expectNext(branchAuditLogDTO)
                .verifyComplete();

        verify(branchAuditLogMapper).toEntity(branchAuditLogDTO);
        verify(branchAuditLogRepository).save(branchAuditLog);
        verify(branchAuditLogMapper).toDTO(branchAuditLog);
    }

    @Test
    void updateBranchAuditLog_WhenBranchAuditLogExists_ShouldUpdateAndReturnBranchAuditLog() {
        // Arrange
        UUID branchAuditLogId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        when(branchAuditLogRepository.findById(branchAuditLogId)).thenReturn(Mono.just(branchAuditLog));
        when(branchAuditLogMapper.toEntity(branchAuditLogDTO)).thenReturn(branchAuditLog);
        when(branchAuditLogRepository.save(branchAuditLog)).thenReturn(Mono.just(branchAuditLog));
        when(branchAuditLogMapper.toDTO(branchAuditLog)).thenReturn(branchAuditLogDTO);

        // Act & Assert
        StepVerifier.create(branchAuditLogService.updateBranchAuditLog(branchAuditLogId, branchAuditLogDTO))
                .expectNext(branchAuditLogDTO)
                .verifyComplete();

        verify(branchAuditLogRepository).findById(branchAuditLogId);
        verify(branchAuditLogMapper).toEntity(branchAuditLogDTO);
        verify(branchAuditLogRepository).save(branchAuditLog);
        verify(branchAuditLogMapper).toDTO(branchAuditLog);
    }

    @Test
    void updateBranchAuditLog_WhenBranchAuditLogDoesNotExist_ShouldReturnError() {
        // Arrange
        UUID branchAuditLogId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        when(branchAuditLogRepository.findById(branchAuditLogId)).thenReturn(Mono.empty());

        // Act & Assert
        StepVerifier.create(branchAuditLogService.updateBranchAuditLog(branchAuditLogId, branchAuditLogDTO))
                .expectErrorMatches(throwable -> throwable instanceof RuntimeException &&
                        throwable.getMessage().equals("Branch audit log not found with ID: " + branchAuditLogId))
                .verify();

        verify(branchAuditLogRepository).findById(branchAuditLogId);
    }

    @Test
    void deleteBranchAuditLog_WhenBranchAuditLogExists_ShouldDeleteBranchAuditLog() {
        // Arrange
        UUID branchAuditLogId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        when(branchAuditLogRepository.findById(branchAuditLogId)).thenReturn(Mono.just(branchAuditLog));
        when(branchAuditLogRepository.deleteById(branchAuditLogId)).thenReturn(Mono.empty());

        // Act & Assert
        StepVerifier.create(branchAuditLogService.deleteBranchAuditLog(branchAuditLogId))
                .verifyComplete();

        verify(branchAuditLogRepository).findById(branchAuditLogId);
        verify(branchAuditLogRepository).deleteById(branchAuditLogId);
    }

    @Test
    void deleteBranchAuditLog_WhenBranchAuditLogDoesNotExist_ShouldReturnError() {
        // Arrange
        UUID branchAuditLogId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        when(branchAuditLogRepository.findById(branchAuditLogId)).thenReturn(Mono.empty());

        // Act & Assert
        StepVerifier.create(branchAuditLogService.deleteBranchAuditLog(branchAuditLogId))
                .expectErrorMatches(throwable -> throwable instanceof RuntimeException &&
                        throwable.getMessage().equals("Branch audit log not found with ID: " + branchAuditLogId))
                .verify();

        verify(branchAuditLogRepository).findById(branchAuditLogId);
    }

    @Test
    void getBranchAuditLogById_WhenBranchAuditLogExists_ShouldReturnBranchAuditLog() {
        // Arrange
        UUID branchAuditLogId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        when(branchAuditLogRepository.findById(branchAuditLogId)).thenReturn(Mono.just(branchAuditLog));
        when(branchAuditLogMapper.toDTO(branchAuditLog)).thenReturn(branchAuditLogDTO);

        // Act & Assert
        StepVerifier.create(branchAuditLogService.getBranchAuditLogById(branchAuditLogId))
                .expectNext(branchAuditLogDTO)
                .verifyComplete();

        verify(branchAuditLogRepository).findById(branchAuditLogId);
        verify(branchAuditLogMapper).toDTO(branchAuditLog);
    }

    @Test
    void getBranchAuditLogById_WhenBranchAuditLogDoesNotExist_ShouldReturnError() {
        // Arrange
        UUID branchAuditLogId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        when(branchAuditLogRepository.findById(branchAuditLogId)).thenReturn(Mono.empty());

        // Act & Assert
        StepVerifier.create(branchAuditLogService.getBranchAuditLogById(branchAuditLogId))
                .expectErrorMatches(throwable -> throwable instanceof RuntimeException &&
                        throwable.getMessage().equals("Branch audit log not found with ID: " + branchAuditLogId))
                .verify();

        verify(branchAuditLogRepository).findById(branchAuditLogId);
    }
}
