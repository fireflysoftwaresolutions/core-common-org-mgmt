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

import io.fireflysoftware.core.organization.core.services.BankAuditLogService;

import org.fireflyframework.core.filters.FilterRequest;
import org.fireflyframework.core.filters.FilterUtils;
import org.fireflyframework.core.queries.PaginationResponse;
import io.fireflysoftware.core.organization.core.mappers.BankAuditLogMapper;
import io.fireflysoftware.core.organization.interfaces.dtos.BankAuditLogDTO;
import io.fireflysoftware.core.organization.models.entities.BankAuditLog;
import io.fireflysoftware.core.organization.models.repositories.BankAuditLogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;
import java.util.UUID;

@Service
@Transactional
public class BankAuditLogServiceImpl implements BankAuditLogService {

    @Autowired
    private BankAuditLogRepository repository;

    @Autowired
    private BankAuditLogMapper mapper;

    @Override
    public Mono<PaginationResponse<BankAuditLogDTO>> filterBankAuditLogs(FilterRequest<BankAuditLogDTO> filterRequest) {
        return FilterUtils
                .createFilter(
                        BankAuditLog.class,
                        mapper::toDTO
                )
                .filter(filterRequest);
    }

    @Override
    public Mono<BankAuditLogDTO> createBankAuditLog(BankAuditLogDTO bankAuditLogDTO) {
        return Mono.just(bankAuditLogDTO)
                .map(mapper::toEntity)
                .flatMap(repository::save)
                .map(mapper::toDTO);
    }

    @Override
    public Mono<BankAuditLogDTO> updateBankAuditLog(UUID bankAuditLogId, BankAuditLogDTO bankAuditLogDTO) {
        return repository.findById(bankAuditLogId)
                .switchIfEmpty(Mono.error(new RuntimeException("Bank audit log not found with ID: " + bankAuditLogId)))
                .flatMap(existingBankAuditLog -> {
                    BankAuditLog updatedBankAuditLog = mapper.toEntity(bankAuditLogDTO);
                    updatedBankAuditLog.setId(bankAuditLogId);
                    return repository.save(updatedBankAuditLog);
                })
                .map(mapper::toDTO);
    }

    @Override
    public Mono<Void> deleteBankAuditLog(UUID bankAuditLogId) {
        return repository.findById(bankAuditLogId)
                .switchIfEmpty(Mono.error(new RuntimeException("Bank audit log not found with ID: " + bankAuditLogId)))
                .flatMap(bankAuditLog -> repository.deleteById(bankAuditLogId));
    }

    @Override
    public Mono<BankAuditLogDTO> getBankAuditLogById(UUID bankAuditLogId) {
        return repository.findById(bankAuditLogId)
                .switchIfEmpty(Mono.error(new RuntimeException("Bank audit log not found with ID: " + bankAuditLogId)))
                .map(mapper::toDTO);
    }
}