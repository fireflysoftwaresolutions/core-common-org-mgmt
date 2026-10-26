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
import io.fireflysoftware.core.organization.core.services.WorkingCalendarService;

import io.fireflysoftware.core.organization.core.services.ScopedFilterSupport;
import org.fireflyframework.core.filters.FilterRequest;
import org.fireflyframework.core.filters.FilterUtils;
import org.fireflyframework.core.queries.PaginationResponse;
import io.fireflysoftware.core.organization.core.mappers.WorkingCalendarMapper;
import io.fireflysoftware.core.organization.interfaces.dtos.WorkingCalendarDTO;
import io.fireflysoftware.core.organization.models.entities.WorkingCalendar;
import io.fireflysoftware.core.organization.models.repositories.WorkingCalendarRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;
import java.util.UUID;

@Service
@Transactional
public class WorkingCalendarServiceImpl implements WorkingCalendarService {

    @Autowired
    private WorkingCalendarRepository repository;

    @Autowired
    private WorkingCalendarMapper mapper;

    @Autowired
    private BankService bankService;

    @Override
    public Mono<PaginationResponse<WorkingCalendarDTO>> filterWorkingCalendars(FilterRequest<WorkingCalendarDTO> filterRequest) {
        return FilterUtils
                .createFilter(
                        WorkingCalendar.class,
                        mapper::toDTO
                )
                .filter(filterRequest);
    }

    @Override
    public Mono<PaginationResponse<WorkingCalendarDTO>> filterWorkingCalendarsForBank(UUID bankId, FilterRequest<WorkingCalendarDTO> filterRequest) {
        return bankService.getBankById(bankId)
                .switchIfEmpty(Mono.error(new RuntimeException("Bank not found with ID: " + bankId)))
                .flatMap(bank -> ScopedFilterSupport.filter(WorkingCalendar.class, mapper::toDTO, filterRequest,
                        (criteria, pageable) -> repository.findPageForBank(bankId, criteria, pageable),
                        criteria -> repository.countForBank(bankId, criteria)));
    }

    @Override
    public Mono<WorkingCalendarDTO> createWorkingCalendar(WorkingCalendarDTO workingCalendarDTO) {
        return Mono.just(workingCalendarDTO)
                .map(mapper::toEntity)
                .flatMap(repository::save)
                .map(mapper::toDTO);
    }

    @Override
    public Mono<WorkingCalendarDTO> createWorkingCalendarForBank(UUID bankId, WorkingCalendarDTO workingCalendarDTO) {
        return bankService.getBankById(bankId)
                .switchIfEmpty(Mono.error(new RuntimeException("Bank not found with ID: " + bankId)))
                .flatMap(bank -> {
                    workingCalendarDTO.setBankId(bankId);
                    return createWorkingCalendar(workingCalendarDTO);
                });
    }

    @Override
    public Mono<WorkingCalendarDTO> updateWorkingCalendar(UUID workingCalendarId, WorkingCalendarDTO workingCalendarDTO) {
        return repository.findById(workingCalendarId)
                .switchIfEmpty(Mono.error(new RuntimeException("Working calendar not found with ID: " + workingCalendarId)))
                .flatMap(existingWorkingCalendar -> {
                    WorkingCalendar updatedWorkingCalendar = mapper.toEntity(workingCalendarDTO);
                    updatedWorkingCalendar.setId(workingCalendarId);
                    return repository.save(updatedWorkingCalendar);
                })
                .map(mapper::toDTO);
    }

    @Override
    public Mono<WorkingCalendarDTO> updateWorkingCalendarForBank(UUID bankId, UUID calendarId, WorkingCalendarDTO workingCalendarDTO) {
        return bankService.getBankById(bankId)
                .switchIfEmpty(Mono.error(new RuntimeException("Bank not found with ID: " + bankId)))
                .flatMap(bank -> getWorkingCalendarById(calendarId))
                .filter(calendar -> calendar.getBankId().equals(bankId))
                .switchIfEmpty(Mono.error(new RuntimeException("Calendar not found for bank with ID: " + bankId)))
                .flatMap(calendar -> {
                    workingCalendarDTO.setBankId(bankId);
                    return updateWorkingCalendar(calendarId, workingCalendarDTO);
                });
    }

    @Override
    public Mono<Void> deleteWorkingCalendar(UUID workingCalendarId) {
        return repository.findById(workingCalendarId)
                .switchIfEmpty(Mono.error(new RuntimeException("Working calendar not found with ID: " + workingCalendarId)))
                .flatMap(workingCalendar -> repository.deleteById(workingCalendarId));
    }

    @Override
    public Mono<Void> deleteWorkingCalendarForBank(UUID bankId, UUID calendarId) {
        return bankService.getBankById(bankId)
                .switchIfEmpty(Mono.error(new RuntimeException("Bank not found with ID: " + bankId)))
                .flatMap(bank -> getWorkingCalendarById(calendarId))
                .filter(calendar -> calendar.getBankId().equals(bankId))
                .switchIfEmpty(Mono.error(new RuntimeException("Calendar not found for bank with ID: " + bankId)))
                .flatMap(calendar -> deleteWorkingCalendar(calendarId));
    }

    @Override
    public Mono<WorkingCalendarDTO> getWorkingCalendarById(UUID workingCalendarId) {
        return repository.findById(workingCalendarId)
                .switchIfEmpty(Mono.error(new RuntimeException("Working calendar not found with ID: " + workingCalendarId)))
                .map(mapper::toDTO);
    }

    @Override
    public Mono<WorkingCalendarDTO> getWorkingCalendarByIdForBank(UUID bankId, UUID calendarId) {
        return bankService.getBankById(bankId)
                .switchIfEmpty(Mono.error(new RuntimeException("Bank not found with ID: " + bankId)))
                .flatMap(bank -> repository.findByIdAndBankId(calendarId, bankId))
                .switchIfEmpty(Mono.error(new RuntimeException("Calendar not found for bank with ID: " + bankId)))
                .map(mapper::toDTO);
    }
}
