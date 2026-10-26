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

import io.fireflysoftware.core.organization.core.services.BankHolidayService;

import org.fireflyframework.core.filters.FilterRequest;
import org.fireflyframework.core.filters.FilterUtils;
import org.fireflyframework.core.queries.PaginationResponse;
import io.fireflysoftware.core.organization.core.mappers.BankHolidayMapper;
import io.fireflysoftware.core.organization.interfaces.dtos.BankHolidayDTO;
import io.fireflysoftware.core.organization.models.entities.BankHoliday;
import io.fireflysoftware.core.organization.models.repositories.BankHolidayRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;
import java.util.UUID;

@Service
@Transactional
public class BankHolidayServiceImpl implements BankHolidayService {

    @Autowired
    private BankHolidayRepository repository;

    @Autowired
    private BankHolidayMapper mapper;

    @Override
    public Mono<PaginationResponse<BankHolidayDTO>> filterBankHolidays(FilterRequest<BankHolidayDTO> filterRequest) {
        return FilterUtils
                .createFilter(
                        BankHoliday.class,
                        mapper::toDTO
                )
                .filter(filterRequest);
    }

    @Override
    public Mono<BankHolidayDTO> createBankHoliday(BankHolidayDTO bankHolidayDTO) {
        return Mono.just(bankHolidayDTO)
                .map(mapper::toEntity)
                .flatMap(repository::save)
                .map(mapper::toDTO);
    }

    @Override
    public Mono<BankHolidayDTO> updateBankHoliday(UUID bankHolidayId, BankHolidayDTO bankHolidayDTO) {
        return repository.findById(bankHolidayId)
                .switchIfEmpty(Mono.error(new RuntimeException("Bank holiday not found with ID: " + bankHolidayId)))
                .flatMap(existingBankHoliday -> {
                    BankHoliday updatedBankHoliday = mapper.toEntity(bankHolidayDTO);
                    updatedBankHoliday.setId(bankHolidayId);
                    return repository.save(updatedBankHoliday);
                })
                .map(mapper::toDTO);
    }

    @Override
    public Mono<Void> deleteBankHoliday(UUID bankHolidayId) {
        return repository.findById(bankHolidayId)
                .switchIfEmpty(Mono.error(new RuntimeException("Bank holiday not found with ID: " + bankHolidayId)))
                .flatMap(bankHoliday -> repository.deleteById(bankHolidayId));
    }

    @Override
    public Mono<BankHolidayDTO> getBankHolidayById(UUID bankHolidayId) {
        return repository.findById(bankHolidayId)
                .switchIfEmpty(Mono.error(new RuntimeException("Bank holiday not found with ID: " + bankHolidayId)))
                .map(mapper::toDTO);
    }
}