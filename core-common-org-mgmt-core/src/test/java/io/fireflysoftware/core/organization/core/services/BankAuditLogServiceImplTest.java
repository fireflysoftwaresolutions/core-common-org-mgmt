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

import io.fireflysoftware.core.organization.core.services.impl.BankAuditLogServiceImpl;
import org.fireflyframework.core.filters.FilterRequest;
import org.fireflyframework.core.filters.FilterUtils;
import org.fireflyframework.core.queries.PaginationResponse;
import io.fireflysoftware.core.organization.core.mappers.BankAuditLogMapper;
import io.fireflysoftware.core.organization.interfaces.dtos.BankAuditLogDTO;
import io.fireflysoftware.core.organization.interfaces.enums.AuditAction;
import io.fireflysoftware.core.organization.models.entities.BankAuditLog;
import io.fireflysoftware.core.organization.models.repositories.BankAuditLogRepository;
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
public class BankAuditLogServiceImplTest {

    @Mock
    private BankAuditLogRepository bankAuditLogRepository;

    @Mock
    private BankAuditLogMapper bankAuditLogMapper;

    @InjectMocks
    private BankAuditLogServiceImpl bankAuditLogService;

    private BankAuditLogDTO bankAuditLogDTO;
    private BankAuditLog bankAuditLog;

    @BeforeEach
    void setUp() {
        // Setup test data
        LocalDateTime now = LocalDateTime.now();

        UUID testId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        UUID testBankId = UUID.fromString("223e4567-e89b-12d3-a456-426614174000");
        UUID testUserId = UUID.fromString("323e4567-e89b-12d3-a456-426614174000");

        bankAuditLogDTO = BankAuditLogDTO.builder()
                .id(testId)
                .bankId(testBankId)
                .action(AuditAction.CREATED)
                .entity("Bank")
                .entityId("1")
                .metadata("{\"name\":\"Test Bank\",\"code\":\"TEST\"}")
                .ipAddress("192.168.1.1")
                .userId(testUserId)
                .timestamp(now)
                .build();

        bankAuditLog = BankAuditLog.builder()
                .id(testId)
                .bankId(testBankId)
                .action(AuditAction.CREATED)
                .entity("Bank")
                .entityId("1")
                .metadata("{\"name\":\"Test Bank\",\"code\":\"TEST\"}")
                .ipAddress("192.168.1.1")
                .userId(testUserId)
                .timestamp(now)
                .build();
    }


    @Test
    void createBankAuditLog_ShouldCreateAndReturnBankAuditLog() {
        // Arrange
        when(bankAuditLogMapper.toEntity(bankAuditLogDTO)).thenReturn(bankAuditLog);
        when(bankAuditLogRepository.save(bankAuditLog)).thenReturn(Mono.just(bankAuditLog));
        when(bankAuditLogMapper.toDTO(bankAuditLog)).thenReturn(bankAuditLogDTO);

        // Act & Assert
        StepVerifier.create(bankAuditLogService.createBankAuditLog(bankAuditLogDTO))
                .expectNext(bankAuditLogDTO)
                .verifyComplete();

        verify(bankAuditLogMapper).toEntity(bankAuditLogDTO);
        verify(bankAuditLogRepository).save(bankAuditLog);
        verify(bankAuditLogMapper).toDTO(bankAuditLog);
    }

    @Test
    void updateBankAuditLog_WhenBankAuditLogExists_ShouldUpdateAndReturnBankAuditLog() {
        // Arrange
        UUID bankAuditLogId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        when(bankAuditLogRepository.findById(bankAuditLogId)).thenReturn(Mono.just(bankAuditLog));
        when(bankAuditLogMapper.toEntity(bankAuditLogDTO)).thenReturn(bankAuditLog);
        when(bankAuditLogRepository.save(bankAuditLog)).thenReturn(Mono.just(bankAuditLog));
        when(bankAuditLogMapper.toDTO(bankAuditLog)).thenReturn(bankAuditLogDTO);

        // Act & Assert
        StepVerifier.create(bankAuditLogService.updateBankAuditLog(bankAuditLogId, bankAuditLogDTO))
                .expectNext(bankAuditLogDTO)
                .verifyComplete();

        verify(bankAuditLogRepository).findById(bankAuditLogId);
        verify(bankAuditLogMapper).toEntity(bankAuditLogDTO);
        verify(bankAuditLogRepository).save(bankAuditLog);
        verify(bankAuditLogMapper).toDTO(bankAuditLog);
    }

    @Test
    void updateBankAuditLog_WhenBankAuditLogDoesNotExist_ShouldReturnError() {
        // Arrange
        UUID bankAuditLogId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        when(bankAuditLogRepository.findById(bankAuditLogId)).thenReturn(Mono.empty());

        // Act & Assert
        StepVerifier.create(bankAuditLogService.updateBankAuditLog(bankAuditLogId, bankAuditLogDTO))
                .expectErrorMatches(throwable -> throwable instanceof RuntimeException &&
                        throwable.getMessage().equals("Bank audit log not found with ID: " + bankAuditLogId))
                .verify();

        verify(bankAuditLogRepository).findById(bankAuditLogId);
    }

    @Test
    void deleteBankAuditLog_WhenBankAuditLogExists_ShouldDeleteBankAuditLog() {
        // Arrange
        UUID bankAuditLogId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        when(bankAuditLogRepository.findById(bankAuditLogId)).thenReturn(Mono.just(bankAuditLog));
        when(bankAuditLogRepository.deleteById(bankAuditLogId)).thenReturn(Mono.empty());

        // Act & Assert
        StepVerifier.create(bankAuditLogService.deleteBankAuditLog(bankAuditLogId))
                .verifyComplete();

        verify(bankAuditLogRepository).findById(bankAuditLogId);
        verify(bankAuditLogRepository).deleteById(bankAuditLogId);
    }

    @Test
    void deleteBankAuditLog_WhenBankAuditLogDoesNotExist_ShouldReturnError() {
        // Arrange
        UUID bankAuditLogId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        when(bankAuditLogRepository.findById(bankAuditLogId)).thenReturn(Mono.empty());

        // Act & Assert
        StepVerifier.create(bankAuditLogService.deleteBankAuditLog(bankAuditLogId))
                .expectErrorMatches(throwable -> throwable instanceof RuntimeException &&
                        throwable.getMessage().equals("Bank audit log not found with ID: " + bankAuditLogId))
                .verify();

        verify(bankAuditLogRepository).findById(bankAuditLogId);
    }

    @Test
    void getBankAuditLogById_WhenBankAuditLogExists_ShouldReturnBankAuditLog() {
        // Arrange
        UUID bankAuditLogId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        when(bankAuditLogRepository.findById(bankAuditLogId)).thenReturn(Mono.just(bankAuditLog));
        when(bankAuditLogMapper.toDTO(bankAuditLog)).thenReturn(bankAuditLogDTO);

        // Act & Assert
        StepVerifier.create(bankAuditLogService.getBankAuditLogById(bankAuditLogId))
                .expectNext(bankAuditLogDTO)
                .verifyComplete();

        verify(bankAuditLogRepository).findById(bankAuditLogId);
        verify(bankAuditLogMapper).toDTO(bankAuditLog);
    }

    @Test
    void getBankAuditLogById_WhenBankAuditLogDoesNotExist_ShouldReturnError() {
        // Arrange
        UUID bankAuditLogId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        when(bankAuditLogRepository.findById(bankAuditLogId)).thenReturn(Mono.empty());

        // Act & Assert
        StepVerifier.create(bankAuditLogService.getBankAuditLogById(bankAuditLogId))
                .expectErrorMatches(throwable -> throwable instanceof RuntimeException &&
                        throwable.getMessage().equals("Bank audit log not found with ID: " + bankAuditLogId))
                .verify();

        verify(bankAuditLogRepository).findById(bankAuditLogId);
    }
}
