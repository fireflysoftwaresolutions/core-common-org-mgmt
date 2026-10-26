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
import io.fireflysoftware.core.organization.core.services.BankDivisionService;

import org.fireflyframework.core.filters.FilterRequest;
import org.fireflyframework.core.filters.FilterUtils;
import org.fireflyframework.core.queries.PaginationResponse;
import io.fireflysoftware.core.organization.core.mappers.BankDivisionMapper;
import io.fireflysoftware.core.organization.interfaces.dtos.BankDivisionDTO;
import io.fireflysoftware.core.organization.models.entities.BankDivision;
import io.fireflysoftware.core.organization.models.repositories.BankDivisionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;
import java.util.UUID;

@Service
@Transactional
public class BankDivisionServiceImpl implements BankDivisionService {

    @Autowired
    private BankDivisionRepository repository;

    @Autowired
    private BankDivisionMapper mapper;

    @Autowired
    private BankService bankService;

    @Override
    public Mono<PaginationResponse<BankDivisionDTO>> filterBankDivisions(FilterRequest<BankDivisionDTO> filterRequest) {
        return FilterUtils
                .createFilter(
                        BankDivision.class,
                        mapper::toDTO
                )
                .filter(filterRequest);
    }

    @Override
    public Mono<BankDivisionDTO> createBankDivision(BankDivisionDTO bankDivisionDTO) {
        return Mono.just(bankDivisionDTO)
                .map(mapper::toEntity)
                .flatMap(repository::save)
                .map(mapper::toDTO);
    }

    @Override
    public Mono<BankDivisionDTO> updateBankDivision(UUID bankDivisionId, BankDivisionDTO bankDivisionDTO) {
        return repository.findById(bankDivisionId)
                .switchIfEmpty(Mono.error(new RuntimeException("Bank division not found with ID: " + bankDivisionId)))
                .flatMap(existingBankDivision -> {
                    BankDivision updatedBankDivision = mapper.toEntity(bankDivisionDTO);
                    updatedBankDivision.setId(bankDivisionId);
                    return repository.save(updatedBankDivision);
                })
                .map(mapper::toDTO);
    }

    @Override
    public Mono<Void> deleteBankDivision(UUID bankDivisionId) {
        return repository.findById(bankDivisionId)
                .switchIfEmpty(Mono.error(new RuntimeException("Bank division not found with ID: " + bankDivisionId)))
                .flatMap(bankDivision -> repository.deleteById(bankDivisionId));
    }

    @Override
    public Mono<BankDivisionDTO> getBankDivisionById(UUID bankDivisionId) {
        return repository.findById(bankDivisionId)
                .switchIfEmpty(Mono.error(new RuntimeException("Bank division not found with ID: " + bankDivisionId)))
                .map(mapper::toDTO);
    }

    @Override
    public Mono<BankDivisionDTO> getBankDivisionByIdForBank(UUID bankId, UUID divisionId) {
        return bankService.getBankById(bankId)
                .switchIfEmpty(Mono.error(new RuntimeException("Bank not found with ID: " + bankId)))
                .flatMap(bank -> repository.findByIdAndBankId(divisionId, bankId))
                .switchIfEmpty(Mono.error(new RuntimeException("Division not found for bank with ID: " + bankId)))
                .map(mapper::toDTO);
    }

    @Override
    public Mono<BankDivisionDTO> updateBankDivisionForBank(UUID bankId, UUID divisionId, BankDivisionDTO bankDivisionDTO) {
        return bankService.getBankById(bankId)
                .switchIfEmpty(Mono.error(new RuntimeException("Bank not found with ID: " + bankId)))
                .flatMap(bank -> getBankDivisionById(divisionId))
                .filter(division -> division.getBankId().equals(bankId))
                .switchIfEmpty(Mono.error(new RuntimeException("Division not found for bank with ID: " + bankId)))
                .flatMap(division -> {
                    bankDivisionDTO.setBankId(bankId);
                    return updateBankDivision(divisionId, bankDivisionDTO);
                });
    }

    @Override
    public Mono<Void> deleteBankDivisionForBank(UUID bankId, UUID divisionId) {
        return bankService.getBankById(bankId)
                .switchIfEmpty(Mono.error(new RuntimeException("Bank not found with ID: " + bankId)))
                .flatMap(bank -> getBankDivisionById(divisionId))
                .filter(division -> division.getBankId().equals(bankId))
                .switchIfEmpty(Mono.error(new RuntimeException("Division not found for bank with ID: " + bankId)))
                .flatMap(division -> deleteBankDivision(divisionId));
    }
}
