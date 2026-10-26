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


package io.fireflysoftware.core.organization.core.services.impl;

import io.fireflysoftware.core.organization.core.services.BankService;
import io.fireflysoftware.core.organization.core.services.BranchService;

import io.fireflysoftware.core.organization.core.services.ScopedFilterSupport;
import org.fireflyframework.core.filters.FilterRequest;
import org.fireflyframework.core.filters.FilterUtils;
import org.fireflyframework.core.queries.PaginationResponse;
import io.fireflysoftware.core.organization.core.mappers.BranchMapper;
import io.fireflysoftware.core.organization.interfaces.dtos.BranchDTO;
import io.fireflysoftware.core.organization.models.entities.Branch;
import io.fireflysoftware.core.organization.models.repositories.BranchRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;
import java.util.UUID;

@Service
@Transactional
public class BranchServiceImpl implements BranchService {

    @Autowired
    private BranchRepository repository;

    @Autowired
    private BranchMapper mapper;

    @Autowired
    private BankService bankService;

    @Override
    public Mono<PaginationResponse<BranchDTO>> filterBranches(FilterRequest<BranchDTO> filterRequest) {
        return FilterUtils
                .createFilter(
                        Branch.class,
                        mapper::toDTO
                )
                .filter(filterRequest);
    }

    @Override
    public Mono<PaginationResponse<BranchDTO>> filterBranchesForBank(UUID bankId, FilterRequest<BranchDTO> filterRequest) {
        return bankService.getBankById(bankId)
                .switchIfEmpty(Mono.error(new RuntimeException("Bank not found with ID: " + bankId)))
                .flatMap(bank -> ScopedFilterSupport.filter(Branch.class, mapper::toDTO, filterRequest,
                        (criteria, pageable) -> repository.findPageForBank(bankId, criteria, pageable),
                        criteria -> repository.countForBank(bankId, criteria)));
    }

    @Override
    public Mono<BranchDTO> createBranch(BranchDTO branchDTO) {
        return Mono.just(branchDTO)
                .map(mapper::toEntity)
                .flatMap(repository::save)
                .map(mapper::toDTO);
    }

    @Override
    public Mono<BranchDTO> createBranchForBank(UUID bankId, BranchDTO branchDTO) {
        return bankService.getBankById(bankId)
                .switchIfEmpty(Mono.error(new RuntimeException("Bank not found with ID: " + bankId)))
                .flatMap(bank -> {
                    branchDTO.setBankId(bankId);
                    return createBranch(branchDTO);
                });
    }

    @Override
    public Mono<BranchDTO> updateBranch(UUID branchId, BranchDTO branchDTO) {
        return repository.findById(branchId)
                .switchIfEmpty(Mono.error(new RuntimeException("Branch not found with ID: " + branchId)))
                .flatMap(existingBranch -> {
                    Branch updatedBranch = mapper.toEntity(branchDTO);
                    updatedBranch.setId(branchId);
                    return repository.save(updatedBranch);
                })
                .map(mapper::toDTO);
    }

    @Override
    public Mono<BranchDTO> updateBranchForBank(UUID bankId, UUID branchId, BranchDTO branchDTO) {
        return bankService.getBankById(bankId)
                .switchIfEmpty(Mono.error(new RuntimeException("Bank not found with ID: " + bankId)))
                .flatMap(bank -> getBranchById(branchId))
                .filter(branch -> branch.getBankId().equals(bankId))
                .switchIfEmpty(Mono.error(new RuntimeException("Branch not found for bank with ID: " + bankId)))
                .flatMap(branch -> {
                    branchDTO.setBankId(bankId);
                    return updateBranch(branchId, branchDTO);
                });
    }

    @Override
    public Mono<Void> deleteBranch(UUID branchId) {
        return repository.findById(branchId)
                .switchIfEmpty(Mono.error(new RuntimeException("Branch not found with ID: " + branchId)))
                .flatMap(branch -> repository.deleteById(branchId));
    }

    @Override
    public Mono<Void> deleteBranchForBank(UUID bankId, UUID branchId) {
        return bankService.getBankById(bankId)
                .switchIfEmpty(Mono.error(new RuntimeException("Bank not found with ID: " + bankId)))
                .flatMap(bank -> getBranchById(branchId))
                .filter(branch -> branch.getBankId().equals(bankId))
                .switchIfEmpty(Mono.error(new RuntimeException("Branch not found for bank with ID: " + bankId)))
                .flatMap(branch -> deleteBranch(branchId));
    }

    @Override
    public Mono<BranchDTO> getBranchById(UUID branchId) {
        return repository.findById(branchId)
                .switchIfEmpty(Mono.error(new RuntimeException("Branch not found with ID: " + branchId)))
                .map(mapper::toDTO);
    }

    @Override
    public Mono<BranchDTO> getBranchByIdForBank(UUID bankId, UUID branchId) {
        return bankService.getBankById(bankId)
                .switchIfEmpty(Mono.error(new RuntimeException("Bank not found with ID: " + bankId)))
                .flatMap(bank -> getBranchById(branchId))
                .filter(branch -> branch.getBankId().equals(bankId))
                .switchIfEmpty(Mono.error(new RuntimeException("Branch not found for bank with ID: " + bankId)));
    }
}
