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

import io.fireflysoftware.core.organization.core.services.BankDivisionService;
import io.fireflysoftware.core.organization.core.services.BankRegionService;

import io.fireflysoftware.core.organization.core.services.ScopedFilterSupport;
import org.fireflyframework.core.filters.FilterRequest;
import org.fireflyframework.core.filters.FilterUtils;
import org.fireflyframework.core.queries.PaginationResponse;
import io.fireflysoftware.core.organization.core.mappers.BankRegionMapper;
import io.fireflysoftware.core.organization.interfaces.dtos.BankRegionDTO;
import io.fireflysoftware.core.organization.models.entities.BankRegion;
import io.fireflysoftware.core.organization.models.repositories.BankRegionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;
import java.util.UUID;

@Service
@Transactional
public class BankRegionServiceImpl implements BankRegionService {

    @Autowired
    private BankRegionRepository repository;

    @Autowired
    private BankRegionMapper mapper;

    @Autowired
    private BankDivisionService bankDivisionService;

    @Override
    public Mono<PaginationResponse<BankRegionDTO>> filterBankRegions(FilterRequest<BankRegionDTO> filterRequest) {
        return FilterUtils
                .createFilter(
                        BankRegion.class,
                        mapper::toDTO
                )
                .filter(filterRequest);
    }

    @Override
    public Mono<PaginationResponse<BankRegionDTO>> filterBankRegionsForDivision(UUID bankId, UUID divisionId, FilterRequest<BankRegionDTO> filterRequest) {
        return bankDivisionService.getBankDivisionByIdForBank(bankId, divisionId)
                .flatMap(division -> ScopedFilterSupport.filter(BankRegion.class, mapper::toDTO, filterRequest,
                        (criteria, pageable) -> repository.findPageForDivision(divisionId, criteria, pageable),
                        criteria -> repository.countForDivision(divisionId, criteria)));
    }

    @Override
    public Mono<BankRegionDTO> createBankRegion(BankRegionDTO bankRegionDTO) {
        return Mono.just(bankRegionDTO)
                .map(mapper::toEntity)
                .flatMap(repository::save)
                .map(mapper::toDTO);
    }

    @Override
    public Mono<BankRegionDTO> createBankRegionForDivision(UUID bankId, UUID divisionId, BankRegionDTO bankRegionDTO) {
        return bankDivisionService.getBankDivisionById(divisionId)
                .filter(division -> division.getBankId().equals(bankId))
                .switchIfEmpty(Mono.error(new RuntimeException("Division not found for bank with ID: " + bankId)))
                .flatMap(division -> {
                    bankRegionDTO.setDivisionId(divisionId);
                    return createBankRegion(bankRegionDTO);
                });
    }

    @Override
    public Mono<BankRegionDTO> updateBankRegion(UUID bankRegionId, BankRegionDTO bankRegionDTO) {
        return repository.findById(bankRegionId)
                .switchIfEmpty(Mono.error(new RuntimeException("Bank region not found with ID: " + bankRegionId)))
                .flatMap(existingBankRegion -> {
                    BankRegion updatedBankRegion = mapper.toEntity(bankRegionDTO);
                    updatedBankRegion.setId(bankRegionId);
                    return repository.save(updatedBankRegion);
                })
                .map(mapper::toDTO);
    }

    @Override
    public Mono<BankRegionDTO> updateBankRegionForDivision(UUID bankId, UUID divisionId, UUID regionId, BankRegionDTO bankRegionDTO) {
        return bankDivisionService.getBankDivisionById(divisionId)
                .filter(division -> division.getBankId().equals(bankId))
                .switchIfEmpty(Mono.error(new RuntimeException("Division not found for bank with ID: " + bankId)))
                .flatMap(division -> getBankRegionById(regionId))
                .filter(region -> region.getDivisionId().equals(divisionId))
                .switchIfEmpty(Mono.error(new RuntimeException("Region not found for division with ID: " + divisionId)))
                .flatMap(region -> {
                    bankRegionDTO.setDivisionId(divisionId);
                    return updateBankRegion(regionId, bankRegionDTO);
                });
    }

    @Override
    public Mono<Void> deleteBankRegion(UUID bankRegionId) {
        return repository.findById(bankRegionId)
                .switchIfEmpty(Mono.error(new RuntimeException("Bank region not found with ID: " + bankRegionId)))
                .flatMap(bankRegion -> repository.deleteById(bankRegionId));
    }

    @Override
    public Mono<Void> deleteBankRegionForDivision(UUID bankId, UUID divisionId, UUID regionId) {
        return bankDivisionService.getBankDivisionById(divisionId)
                .filter(division -> division.getBankId().equals(bankId))
                .switchIfEmpty(Mono.error(new RuntimeException("Division not found for bank with ID: " + bankId)))
                .flatMap(division -> getBankRegionById(regionId))
                .filter(region -> region.getDivisionId().equals(divisionId))
                .switchIfEmpty(Mono.error(new RuntimeException("Region not found for division with ID: " + divisionId)))
                .flatMap(region -> deleteBankRegion(regionId));
    }

    @Override
    public Mono<BankRegionDTO> getBankRegionById(UUID bankRegionId) {
        return repository.findById(bankRegionId)
                .switchIfEmpty(Mono.error(new RuntimeException("Bank region not found with ID: " + bankRegionId)))
                .map(mapper::toDTO);
    }

    @Override
    public Mono<BankRegionDTO> getBankRegionByIdForDivision(UUID bankId, UUID divisionId, UUID regionId) {
        return bankDivisionService.getBankDivisionById(divisionId)
                .filter(division -> division.getBankId().equals(bankId))
                .switchIfEmpty(Mono.error(new RuntimeException("Division not found for bank with ID: " + bankId)))
                .flatMap(division -> getBankRegionById(regionId))
                .filter(region -> region.getDivisionId().equals(divisionId))
                .switchIfEmpty(Mono.error(new RuntimeException("Region not found for division with ID: " + divisionId)));
    }
}
