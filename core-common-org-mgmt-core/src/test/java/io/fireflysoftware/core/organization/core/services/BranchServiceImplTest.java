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

import io.fireflysoftware.core.organization.core.services.impl.BranchServiceImpl;
import org.fireflyframework.core.filters.FilterRequest;
import org.fireflyframework.core.filters.FilterUtils;
import org.fireflyframework.core.queries.PaginationResponse;
import io.fireflysoftware.core.organization.core.mappers.BranchMapper;
import io.fireflysoftware.core.organization.interfaces.dtos.BankDTO;
import io.fireflysoftware.core.organization.interfaces.dtos.BranchDTO;
import io.fireflysoftware.core.organization.models.entities.Branch;
import io.fireflysoftware.core.organization.models.repositories.BranchRepository;
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
import java.util.Collections;
import java.util.function.Function;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.util.UUID;

@ExtendWith(MockitoExtension.class)
public class BranchServiceImplTest {

    @Mock
    private BranchRepository branchRepository;

    @Mock
    private BranchMapper branchMapper;

    @Mock
    private BankService bankService;

    @InjectMocks
    private BranchServiceImpl branchService;

    private BranchDTO branchDTO;
    private Branch branch;

    @BeforeEach
    void setUp() {
        // Setup test data
        LocalDateTime now = LocalDateTime.now();

        UUID testId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        UUID testBankId = UUID.fromString("223e4567-e89b-12d3-a456-426614174000");
        UUID testRegionId = UUID.fromString("323e4567-e89b-12d3-a456-426614174000");
        UUID testCountryId = UUID.fromString("423e4567-e89b-12d3-a456-426614174000");

        branchDTO = BranchDTO.builder()
                .id(testId)
                .bankId(testBankId)
                .regionId(testRegionId)
                .code("BR001")
                .name("Main Branch")
                .description("Main Branch of Test Bank")
                .phoneNumber("123-456-7890")
                .email("main@testbank.com")
                .addressLine("123 Main St")
                .postalCode("12345")
                .city("Test City")
                .state("Test State")
                .countryId(testCountryId)
                .isActive(true)
                .openedAt(now.minusYears(5))
                .createdAt(now)
                .build();

        branch = Branch.builder()
                .id(testId)
                .bankId(testBankId)
                .regionId(testRegionId)
                .code("BR001")
                .name("Main Branch")
                .description("Main Branch of Test Bank")
                .phoneNumber("123-456-7890")
                .email("main@testbank.com")
                .addressLine("123 Main St")
                .postalCode("12345")
                .city("Test City")
                .state("Test State")
                .countryId(testCountryId)
                .isActive(true)
                .openedAt(now.minusYears(5))
                .createdAt(now)
                .build();
    }


    @Test
    void createBranch_ShouldCreateAndReturnBranch() {
        // Arrange
        when(branchMapper.toEntity(branchDTO)).thenReturn(branch);
        when(branchRepository.save(branch)).thenReturn(Mono.just(branch));
        when(branchMapper.toDTO(branch)).thenReturn(branchDTO);

        // Act & Assert
        StepVerifier.create(branchService.createBranch(branchDTO))
                .expectNext(branchDTO)
                .verifyComplete();

        verify(branchMapper).toEntity(branchDTO);
        verify(branchRepository).save(branch);
        verify(branchMapper).toDTO(branch);
    }

    @Test
    void updateBranch_WhenBranchExists_ShouldUpdateAndReturnBranch() {
        // Arrange
        UUID branchId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        when(branchRepository.findById(branchId)).thenReturn(Mono.just(branch));
        when(branchMapper.toEntity(branchDTO)).thenReturn(branch);
        when(branchRepository.save(branch)).thenReturn(Mono.just(branch));
        when(branchMapper.toDTO(branch)).thenReturn(branchDTO);

        // Act & Assert
        StepVerifier.create(branchService.updateBranch(branchId, branchDTO))
                .expectNext(branchDTO)
                .verifyComplete();

        verify(branchRepository).findById(branchId);
        verify(branchMapper).toEntity(branchDTO);
        verify(branchRepository).save(branch);
        verify(branchMapper).toDTO(branch);
    }

    @Test
    void updateBranch_WhenBranchDoesNotExist_ShouldReturnError() {
        // Arrange
        UUID branchId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        when(branchRepository.findById(branchId)).thenReturn(Mono.empty());

        // Act & Assert
        StepVerifier.create(branchService.updateBranch(branchId, branchDTO))
                .expectErrorMatches(throwable -> throwable instanceof RuntimeException &&
                        throwable.getMessage().equals("Branch not found with ID: " + branchId))
                .verify();

        verify(branchRepository).findById(branchId);
    }

    @Test
    void deleteBranch_WhenBranchExists_ShouldDeleteBranch() {
        // Arrange
        UUID branchId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        when(branchRepository.findById(branchId)).thenReturn(Mono.just(branch));
        when(branchRepository.deleteById(branchId)).thenReturn(Mono.empty());

        // Act & Assert
        StepVerifier.create(branchService.deleteBranch(branchId))
                .verifyComplete();

        verify(branchRepository).findById(branchId);
        verify(branchRepository).deleteById(branchId);
    }

    @Test
    void deleteBranch_WhenBranchDoesNotExist_ShouldReturnError() {
        // Arrange
        UUID branchId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        when(branchRepository.findById(branchId)).thenReturn(Mono.empty());

        // Act & Assert
        StepVerifier.create(branchService.deleteBranch(branchId))
                .expectErrorMatches(throwable -> throwable instanceof RuntimeException &&
                        throwable.getMessage().equals("Branch not found with ID: " + branchId))
                .verify();

        verify(branchRepository).findById(branchId);
    }

    @Test
    void getBranchById_WhenBranchExists_ShouldReturnBranch() {
        // Arrange
        UUID branchId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        when(branchRepository.findById(branchId)).thenReturn(Mono.just(branch));
        when(branchMapper.toDTO(branch)).thenReturn(branchDTO);

        // Act & Assert
        StepVerifier.create(branchService.getBranchById(branchId))
                .expectNext(branchDTO)
                .verifyComplete();

        verify(branchRepository).findById(branchId);
        verify(branchMapper).toDTO(branch);
    }

    @Test
    void getBranchById_WhenBranchDoesNotExist_ShouldReturnError() {
        // Arrange
        UUID branchId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        when(branchRepository.findById(branchId)).thenReturn(Mono.empty());

        // Act & Assert
        StepVerifier.create(branchService.getBranchById(branchId))
                .expectErrorMatches(throwable -> throwable instanceof RuntimeException &&
                        throwable.getMessage().equals("Branch not found with ID: " + branchId))
                .verify();

        verify(branchRepository).findById(branchId);
    }

    // Skip filter tests as they require R2dbcEntityTemplate initialization
    // In a real application, these would be integration tests rather than unit tests

    @Test
    void filterBranchesForBank_WhenBankDoesNotExist_ShouldReturnError() {
        // Arrange
        UUID bankId = UUID.fromString("223e4567-e89b-12d3-a456-426614174000");
        FilterRequest<BranchDTO> filterRequest = new FilterRequest<>();

        when(bankService.getBankById(bankId)).thenReturn(Mono.empty());

        // Act & Assert
        StepVerifier.create(branchService.filterBranchesForBank(bankId, filterRequest))
                .expectErrorMatches(throwable -> throwable instanceof RuntimeException &&
                        throwable.getMessage().equals("Bank not found with ID: " + bankId))
                .verify();

        verify(bankService).getBankById(bankId);
    }

    @Test
    void createBranchForBank_WhenBankExists_ShouldCreateAndReturnBranch() {
        // Arrange
        UUID bankId = UUID.fromString("223e4567-e89b-12d3-a456-426614174000");
        BankDTO bankDTO = BankDTO.builder().id(bankId).build();

        when(bankService.getBankById(bankId)).thenReturn(Mono.just(bankDTO));
        when(branchMapper.toEntity(branchDTO)).thenReturn(branch);
        when(branchRepository.save(branch)).thenReturn(Mono.just(branch));
        when(branchMapper.toDTO(branch)).thenReturn(branchDTO);

        // Act & Assert
        StepVerifier.create(branchService.createBranchForBank(bankId, branchDTO))
                .expectNext(branchDTO)
                .verifyComplete();

        verify(bankService).getBankById(bankId);
        verify(branchMapper).toEntity(branchDTO);
        verify(branchRepository).save(branch);
        verify(branchMapper).toDTO(branch);
    }

    @Test
    void createBranchForBank_WhenBankDoesNotExist_ShouldReturnError() {
        // Arrange
        UUID bankId = UUID.fromString("223e4567-e89b-12d3-a456-426614174000");

        when(bankService.getBankById(bankId)).thenReturn(Mono.empty());

        // Act & Assert
        StepVerifier.create(branchService.createBranchForBank(bankId, branchDTO))
                .expectErrorMatches(throwable -> throwable instanceof RuntimeException &&
                        throwable.getMessage().equals("Bank not found with ID: " + bankId))
                .verify();

        verify(bankService).getBankById(bankId);
        verify(branchMapper, never()).toEntity(any());
        verify(branchRepository, never()).save(any());
    }

    @Test
    void updateBranchForBank_WhenBankAndBranchExistAndBranchBelongsToBank_ShouldUpdateAndReturnBranch() {
        // Arrange
        UUID bankId = UUID.fromString("223e4567-e89b-12d3-a456-426614174000");
        UUID branchId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        BankDTO bankDTO = BankDTO.builder().id(bankId).build();

        branchDTO.setBankId(bankId);

        when(bankService.getBankById(bankId)).thenReturn(Mono.just(bankDTO));
        when(branchRepository.findById(branchId)).thenReturn(Mono.just(branch));
        when(branchMapper.toDTO(branch)).thenReturn(branchDTO);
        when(branchMapper.toEntity(branchDTO)).thenReturn(branch);
        when(branchRepository.save(branch)).thenReturn(Mono.just(branch));

        // Act & Assert
        StepVerifier.create(branchService.updateBranchForBank(bankId, branchId, branchDTO))
                .expectNext(branchDTO)
                .verifyComplete();

        verify(bankService).getBankById(bankId);
        verify(branchRepository, Mockito.times(2)).findById(branchId); // Called by getBranchById and updateBranch
        verify(branchMapper).toEntity(branchDTO);
        verify(branchRepository).save(branch);
        verify(branchMapper, Mockito.times(2)).toDTO(branch);
    }

    @Test
    void updateBranchForBank_WhenBankDoesNotExist_ShouldReturnError() {
        // Arrange
        UUID bankId = UUID.fromString("223e4567-e89b-12d3-a456-426614174000");
        UUID branchId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");

        when(bankService.getBankById(bankId)).thenReturn(Mono.empty());

        // Act & Assert
        StepVerifier.create(branchService.updateBranchForBank(bankId, branchId, branchDTO))
                .expectErrorMatches(throwable -> throwable instanceof RuntimeException &&
                        throwable.getMessage().equals("Bank not found with ID: " + bankId))
                .verify();

        verify(bankService).getBankById(bankId);
        verify(branchRepository, never()).findById(any(UUID.class));
    }

    @Test
    void updateBranchForBank_WhenBranchDoesNotBelongToBank_ShouldReturnError() {
        // Arrange
        UUID bankId = UUID.fromString("223e4567-e89b-12d3-a456-426614174000");
        UUID branchId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        BankDTO bankDTO = BankDTO.builder().id(bankId).build();

        // Branch belongs to a different bank
        branchDTO.setBankId(UUID.fromString("999e4567-e89b-12d3-a456-426614174000"));

        when(bankService.getBankById(bankId)).thenReturn(Mono.just(bankDTO));
        when(branchRepository.findById(branchId)).thenReturn(Mono.just(branch));
        when(branchMapper.toDTO(branch)).thenReturn(branchDTO);

        // Act & Assert
        StepVerifier.create(branchService.updateBranchForBank(bankId, branchId, branchDTO))
                .expectErrorMatches(throwable -> throwable instanceof RuntimeException &&
                        throwable.getMessage().equals("Branch not found for bank with ID: " + bankId))
                .verify();

        verify(bankService).getBankById(bankId);
        verify(branchRepository).findById(branchId);
        verify(branchMapper).toDTO(branch);
    }

    @Test
    void deleteBranchForBank_WhenBankAndBranchExistAndBranchBelongsToBank_ShouldDeleteBranch() {
        // Arrange
        UUID bankId = UUID.fromString("223e4567-e89b-12d3-a456-426614174000");
        UUID branchId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        BankDTO bankDTO = BankDTO.builder().id(bankId).build();

        branchDTO.setBankId(bankId);

        when(bankService.getBankById(bankId)).thenReturn(Mono.just(bankDTO));
        when(branchRepository.findById(branchId)).thenReturn(Mono.just(branch));
        when(branchMapper.toDTO(branch)).thenReturn(branchDTO);
        when(branchRepository.deleteById(branchId)).thenReturn(Mono.empty());

        // Act & Assert
        StepVerifier.create(branchService.deleteBranchForBank(bankId, branchId))
                .verifyComplete();

        verify(bankService).getBankById(bankId);
        verify(branchRepository, Mockito.times(2)).findById(branchId); // Called by getBranchById and deleteBranch
        verify(branchMapper).toDTO(branch);
        verify(branchRepository).deleteById(branchId);
    }

    @Test
    void deleteBranchForBank_WhenBankDoesNotExist_ShouldReturnError() {
        // Arrange
        UUID bankId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        UUID branchId = UUID.fromString("223e4567-e89b-12d3-a456-426614174000");

        when(bankService.getBankById(bankId)).thenReturn(Mono.empty());

        // Act & Assert
        StepVerifier.create(branchService.deleteBranchForBank(bankId, branchId))
                .expectErrorMatches(throwable -> throwable instanceof RuntimeException &&
                        throwable.getMessage().equals("Bank not found with ID: " + bankId))
                .verify();

        verify(bankService).getBankById(bankId);
        verify(branchRepository, never()).findById(any(UUID.class));
    }

    @Test
    void deleteBranchForBank_WhenBranchDoesNotBelongToBank_ShouldReturnError() {
        // Arrange
        UUID bankId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        UUID branchId = UUID.fromString("223e4567-e89b-12d3-a456-426614174000");
        BankDTO bankDTO = BankDTO.builder().id(bankId).build();

        // Branch belongs to a different bank
        branchDTO.setBankId(UUID.fromString("999e4567-e89b-12d3-a456-426614174000"));

        when(bankService.getBankById(bankId)).thenReturn(Mono.just(bankDTO));
        when(branchRepository.findById(branchId)).thenReturn(Mono.just(branch));
        when(branchMapper.toDTO(branch)).thenReturn(branchDTO);

        // Act & Assert
        StepVerifier.create(branchService.deleteBranchForBank(bankId, branchId))
                .expectErrorMatches(throwable -> throwable instanceof RuntimeException &&
                        throwable.getMessage().equals("Branch not found for bank with ID: " + bankId))
                .verify();

        verify(bankService).getBankById(bankId);
        verify(branchRepository).findById(branchId);
        verify(branchMapper).toDTO(branch);
        verify(branchRepository, never()).deleteById(any(UUID.class));
    }

    @Test
    void getBranchByIdForBank_WhenBankAndBranchExistAndBranchBelongsToBank_ShouldReturnBranch() {
        // Arrange
        UUID bankId = UUID.fromString("223e4567-e89b-12d3-a456-426614174000");
        UUID branchId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        BankDTO bankDTO = BankDTO.builder().id(bankId).build();

        branchDTO.setBankId(bankId);

        when(bankService.getBankById(bankId)).thenReturn(Mono.just(bankDTO));
        when(branchRepository.findById(branchId)).thenReturn(Mono.just(branch));
        when(branchMapper.toDTO(branch)).thenReturn(branchDTO);

        // Act & Assert
        StepVerifier.create(branchService.getBranchByIdForBank(bankId, branchId))
                .expectNext(branchDTO)
                .verifyComplete();

        verify(bankService).getBankById(bankId);
        verify(branchRepository).findById(branchId);
        verify(branchMapper).toDTO(branch);
    }

    @Test
    void getBranchByIdForBank_WhenBankDoesNotExist_ShouldReturnError() {
        // Arrange
        UUID bankId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        UUID branchId = UUID.fromString("223e4567-e89b-12d3-a456-426614174000");

        when(bankService.getBankById(bankId)).thenReturn(Mono.empty());

        // Act & Assert
        StepVerifier.create(branchService.getBranchByIdForBank(bankId, branchId))
                .expectErrorMatches(throwable -> throwable instanceof RuntimeException &&
                        throwable.getMessage().equals("Bank not found with ID: " + bankId))
                .verify();

        verify(bankService).getBankById(bankId);
        verify(branchRepository, never()).findById(any(UUID.class));
    }

    @Test
    void getBranchByIdForBank_WhenBranchDoesNotBelongToBank_ShouldReturnError() {
        // Arrange
        UUID bankId = UUID.fromString("223e4567-e89b-12d3-a456-426614174000");
        UUID branchId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        BankDTO bankDTO = BankDTO.builder().id(bankId).build();

        // Branch belongs to a different bank
        branchDTO.setBankId(UUID.fromString("999e4567-e89b-12d3-a456-426614174000"));

        when(bankService.getBankById(bankId)).thenReturn(Mono.just(bankDTO));
        when(branchRepository.findById(branchId)).thenReturn(Mono.just(branch));
        when(branchMapper.toDTO(branch)).thenReturn(branchDTO);

        // Act & Assert
        StepVerifier.create(branchService.getBranchByIdForBank(bankId, branchId))
                .expectErrorMatches(throwable -> throwable instanceof RuntimeException &&
                        throwable.getMessage().equals("Branch not found for bank with ID: " + bankId))
                .verify();

        verify(bankService).getBankById(bankId);
        verify(branchRepository).findById(branchId);
        verify(branchMapper).toDTO(branch);
    }
}
