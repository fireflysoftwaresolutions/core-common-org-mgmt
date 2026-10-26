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

import io.fireflysoftware.core.organization.core.services.impl.BankDivisionServiceImpl;
import org.fireflyframework.core.filters.FilterRequest;
import org.fireflyframework.core.filters.FilterUtils;
import org.fireflyframework.core.queries.PaginationResponse;
import io.fireflysoftware.core.organization.core.mappers.BankDivisionMapper;
import io.fireflysoftware.core.organization.interfaces.dtos.BankDivisionDTO;
import io.fireflysoftware.core.organization.models.entities.BankDivision;
import io.fireflysoftware.core.organization.models.repositories.BankDivisionRepository;
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
public class BankDivisionServiceImplTest {

    @Mock
    private BankDivisionRepository bankDivisionRepository;

    @Mock
    private BankDivisionMapper bankDivisionMapper;

    @InjectMocks
    private BankDivisionServiceImpl bankDivisionService;

    private BankDivisionDTO bankDivisionDTO;
    private BankDivision bankDivision;

    @BeforeEach
    void setUp() {
        // Setup test data
        UUID testId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        UUID testBankId = UUID.fromString("223e4567-e89b-12d3-a456-426614174000");

        bankDivisionDTO = BankDivisionDTO.builder()
                .id(testId)
                .bankId(testBankId)
                .code("TEST_DIVISION")
                .name("Test Division")
                .description("Test Division Description")
                .isActive(true)
                .createdAt(LocalDateTime.now())
                .build();

        bankDivision = BankDivision.builder()
                .id(testId)
                .bankId(testBankId)
                .code("TEST_DIVISION")
                .name("Test Division")
                .description("Test Division Description")
                .isActive(true)
                .createdAt(LocalDateTime.now())
                .build();
    }


    @Test
    void createBankDivision_ShouldCreateAndReturnBankDivision() {
        // Arrange
        when(bankDivisionMapper.toEntity(bankDivisionDTO)).thenReturn(bankDivision);
        when(bankDivisionRepository.save(bankDivision)).thenReturn(Mono.just(bankDivision));
        when(bankDivisionMapper.toDTO(bankDivision)).thenReturn(bankDivisionDTO);

        // Act & Assert
        StepVerifier.create(bankDivisionService.createBankDivision(bankDivisionDTO))
                .expectNext(bankDivisionDTO)
                .verifyComplete();

        verify(bankDivisionMapper).toEntity(bankDivisionDTO);
        verify(bankDivisionRepository).save(bankDivision);
        verify(bankDivisionMapper).toDTO(bankDivision);
    }

    @Test
    void updateBankDivision_WhenBankDivisionExists_ShouldUpdateAndReturnBankDivision() {
        // Arrange
        UUID bankDivisionId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        when(bankDivisionRepository.findById(bankDivisionId)).thenReturn(Mono.just(bankDivision));
        when(bankDivisionMapper.toEntity(bankDivisionDTO)).thenReturn(bankDivision);
        when(bankDivisionRepository.save(bankDivision)).thenReturn(Mono.just(bankDivision));
        when(bankDivisionMapper.toDTO(bankDivision)).thenReturn(bankDivisionDTO);

        // Act & Assert
        StepVerifier.create(bankDivisionService.updateBankDivision(bankDivisionId, bankDivisionDTO))
                .expectNext(bankDivisionDTO)
                .verifyComplete();

        verify(bankDivisionRepository).findById(bankDivisionId);
        verify(bankDivisionMapper).toEntity(bankDivisionDTO);
        verify(bankDivisionRepository).save(bankDivision);
        verify(bankDivisionMapper).toDTO(bankDivision);
    }

    @Test
    void updateBankDivision_WhenBankDivisionDoesNotExist_ShouldReturnError() {
        // Arrange
        UUID bankDivisionId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        when(bankDivisionRepository.findById(bankDivisionId)).thenReturn(Mono.empty());

        // Act & Assert
        StepVerifier.create(bankDivisionService.updateBankDivision(bankDivisionId, bankDivisionDTO))
                .expectErrorMatches(throwable -> throwable instanceof RuntimeException &&
                        throwable.getMessage().equals("Bank division not found with ID: " + bankDivisionId))
                .verify();

        verify(bankDivisionRepository).findById(bankDivisionId);
    }

    @Test
    void deleteBankDivision_WhenBankDivisionExists_ShouldDeleteBankDivision() {
        // Arrange
        UUID bankDivisionId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        when(bankDivisionRepository.findById(bankDivisionId)).thenReturn(Mono.just(bankDivision));
        when(bankDivisionRepository.deleteById(bankDivisionId)).thenReturn(Mono.empty());

        // Act & Assert
        StepVerifier.create(bankDivisionService.deleteBankDivision(bankDivisionId))
                .verifyComplete();

        verify(bankDivisionRepository).findById(bankDivisionId);
        verify(bankDivisionRepository).deleteById(bankDivisionId);
    }

    @Test
    void deleteBankDivision_WhenBankDivisionDoesNotExist_ShouldReturnError() {
        // Arrange
        UUID bankDivisionId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        when(bankDivisionRepository.findById(bankDivisionId)).thenReturn(Mono.empty());

        // Act & Assert
        StepVerifier.create(bankDivisionService.deleteBankDivision(bankDivisionId))
                .expectErrorMatches(throwable -> throwable instanceof RuntimeException &&
                        throwable.getMessage().equals("Bank division not found with ID: " + bankDivisionId))
                .verify();

        verify(bankDivisionRepository).findById(bankDivisionId);
    }

    @Test
    void getBankDivisionById_WhenBankDivisionExists_ShouldReturnBankDivision() {
        // Arrange
        UUID bankDivisionId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        when(bankDivisionRepository.findById(bankDivisionId)).thenReturn(Mono.just(bankDivision));
        when(bankDivisionMapper.toDTO(bankDivision)).thenReturn(bankDivisionDTO);

        // Act & Assert
        StepVerifier.create(bankDivisionService.getBankDivisionById(bankDivisionId))
                .expectNext(bankDivisionDTO)
                .verifyComplete();

        verify(bankDivisionRepository).findById(bankDivisionId);
        verify(bankDivisionMapper).toDTO(bankDivision);
    }

    @Test
    void getBankDivisionById_WhenBankDivisionDoesNotExist_ShouldReturnError() {
        // Arrange
        UUID bankDivisionId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        when(bankDivisionRepository.findById(bankDivisionId)).thenReturn(Mono.empty());

        // Act & Assert
        StepVerifier.create(bankDivisionService.getBankDivisionById(bankDivisionId))
                .expectErrorMatches(throwable -> throwable instanceof RuntimeException &&
                        throwable.getMessage().equals("Bank division not found with ID: " + bankDivisionId))
                .verify();

        verify(bankDivisionRepository).findById(bankDivisionId);
    }
}
