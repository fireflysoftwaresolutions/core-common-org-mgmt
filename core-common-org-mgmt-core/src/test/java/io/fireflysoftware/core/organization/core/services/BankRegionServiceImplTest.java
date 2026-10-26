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

import io.fireflysoftware.core.organization.core.services.impl.BankRegionServiceImpl;
import org.fireflyframework.core.filters.FilterRequest;
import org.fireflyframework.core.filters.FilterUtils;
import org.fireflyframework.core.queries.PaginationResponse;
import io.fireflysoftware.core.organization.core.mappers.BankRegionMapper;
import io.fireflysoftware.core.organization.interfaces.dtos.BankRegionDTO;
import io.fireflysoftware.core.organization.models.entities.BankRegion;
import io.fireflysoftware.core.organization.models.repositories.BankRegionRepository;
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
public class BankRegionServiceImplTest {

    @Mock
    private BankRegionRepository bankRegionRepository;

    @Mock
    private BankRegionMapper bankRegionMapper;

    @InjectMocks
    private BankRegionServiceImpl bankRegionService;

    private BankRegionDTO bankRegionDTO;
    private BankRegion bankRegion;

    @BeforeEach
    void setUp() {
        // Setup test data
        UUID testId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        UUID testDivisionId = UUID.fromString("223e4567-e89b-12d3-a456-426614174000");

        bankRegionDTO = BankRegionDTO.builder()
                .id(testId)
                .divisionId(testDivisionId)
                .code("TEST_REGION")
                .name("Test Region")
                .description("Test Region Description")
                .isActive(true)
                .createdAt(LocalDateTime.now())
                .build();

        bankRegion = BankRegion.builder()
                .id(testId)
                .divisionId(testDivisionId)
                .code("TEST_REGION")
                .name("Test Region")
                .description("Test Region Description")
                .isActive(true)
                .createdAt(LocalDateTime.now())
                .build();
    }


    @Test
    void createBankRegion_ShouldCreateAndReturnBankRegion() {
        // Arrange
        when(bankRegionMapper.toEntity(bankRegionDTO)).thenReturn(bankRegion);
        when(bankRegionRepository.save(bankRegion)).thenReturn(Mono.just(bankRegion));
        when(bankRegionMapper.toDTO(bankRegion)).thenReturn(bankRegionDTO);

        // Act & Assert
        StepVerifier.create(bankRegionService.createBankRegion(bankRegionDTO))
                .expectNext(bankRegionDTO)
                .verifyComplete();

        verify(bankRegionMapper).toEntity(bankRegionDTO);
        verify(bankRegionRepository).save(bankRegion);
        verify(bankRegionMapper).toDTO(bankRegion);
    }

    @Test
    void updateBankRegion_WhenBankRegionExists_ShouldUpdateAndReturnBankRegion() {
        // Arrange
        UUID bankRegionId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        when(bankRegionRepository.findById(bankRegionId)).thenReturn(Mono.just(bankRegion));
        when(bankRegionMapper.toEntity(bankRegionDTO)).thenReturn(bankRegion);
        when(bankRegionRepository.save(bankRegion)).thenReturn(Mono.just(bankRegion));
        when(bankRegionMapper.toDTO(bankRegion)).thenReturn(bankRegionDTO);

        // Act & Assert
        StepVerifier.create(bankRegionService.updateBankRegion(bankRegionId, bankRegionDTO))
                .expectNext(bankRegionDTO)
                .verifyComplete();

        verify(bankRegionRepository).findById(bankRegionId);
        verify(bankRegionMapper).toEntity(bankRegionDTO);
        verify(bankRegionRepository).save(bankRegion);
        verify(bankRegionMapper).toDTO(bankRegion);
    }

    @Test
    void updateBankRegion_WhenBankRegionDoesNotExist_ShouldReturnError() {
        // Arrange
        UUID bankRegionId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        when(bankRegionRepository.findById(bankRegionId)).thenReturn(Mono.empty());

        // Act & Assert
        StepVerifier.create(bankRegionService.updateBankRegion(bankRegionId, bankRegionDTO))
                .expectErrorMatches(throwable -> throwable instanceof RuntimeException &&
                        throwable.getMessage().equals("Bank region not found with ID: " + bankRegionId))
                .verify();

        verify(bankRegionRepository).findById(bankRegionId);
    }

    @Test
    void deleteBankRegion_WhenBankRegionExists_ShouldDeleteBankRegion() {
        // Arrange
        UUID bankRegionId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        when(bankRegionRepository.findById(bankRegionId)).thenReturn(Mono.just(bankRegion));
        when(bankRegionRepository.deleteById(bankRegionId)).thenReturn(Mono.empty());

        // Act & Assert
        StepVerifier.create(bankRegionService.deleteBankRegion(bankRegionId))
                .verifyComplete();

        verify(bankRegionRepository).findById(bankRegionId);
        verify(bankRegionRepository).deleteById(bankRegionId);
    }

    @Test
    void deleteBankRegion_WhenBankRegionDoesNotExist_ShouldReturnError() {
        // Arrange
        UUID bankRegionId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        when(bankRegionRepository.findById(bankRegionId)).thenReturn(Mono.empty());

        // Act & Assert
        StepVerifier.create(bankRegionService.deleteBankRegion(bankRegionId))
                .expectErrorMatches(throwable -> throwable instanceof RuntimeException &&
                        throwable.getMessage().equals("Bank region not found with ID: " + bankRegionId))
                .verify();

        verify(bankRegionRepository).findById(bankRegionId);
    }

    @Test
    void getBankRegionById_WhenBankRegionExists_ShouldReturnBankRegion() {
        // Arrange
        UUID bankRegionId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        when(bankRegionRepository.findById(bankRegionId)).thenReturn(Mono.just(bankRegion));
        when(bankRegionMapper.toDTO(bankRegion)).thenReturn(bankRegionDTO);

        // Act & Assert
        StepVerifier.create(bankRegionService.getBankRegionById(bankRegionId))
                .expectNext(bankRegionDTO)
                .verifyComplete();

        verify(bankRegionRepository).findById(bankRegionId);
        verify(bankRegionMapper).toDTO(bankRegion);
    }

    @Test
    void getBankRegionById_WhenBankRegionDoesNotExist_ShouldReturnError() {
        // Arrange
        UUID bankRegionId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        when(bankRegionRepository.findById(bankRegionId)).thenReturn(Mono.empty());

        // Act & Assert
        StepVerifier.create(bankRegionService.getBankRegionById(bankRegionId))
                .expectErrorMatches(throwable -> throwable instanceof RuntimeException &&
                        throwable.getMessage().equals("Bank region not found with ID: " + bankRegionId))
                .verify();

        verify(bankRegionRepository).findById(bankRegionId);
    }
}
