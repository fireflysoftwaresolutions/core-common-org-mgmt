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

import io.fireflysoftware.core.organization.core.services.BranchAuditLogService;

import org.fireflyframework.core.filters.FilterRequest;
import org.fireflyframework.core.filters.FilterUtils;
import org.fireflyframework.core.queries.PaginationResponse;
import io.fireflysoftware.core.organization.core.mappers.BranchAuditLogMapper;
import io.fireflysoftware.core.organization.interfaces.dtos.BranchAuditLogDTO;
import io.fireflysoftware.core.organization.models.entities.BranchAuditLog;
import io.fireflysoftware.core.organization.models.repositories.BranchAuditLogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;
import java.util.UUID;

@Service
@Transactional
public class BranchAuditLogServiceImpl implements BranchAuditLogService {

    @Autowired
    private BranchAuditLogRepository repository;

    @Autowired
    private BranchAuditLogMapper mapper;

    @Override
    public Mono<PaginationResponse<BranchAuditLogDTO>> filterBranchAuditLogs(FilterRequest<BranchAuditLogDTO> filterRequest) {
        return FilterUtils
                .createFilter(
                        BranchAuditLog.class,
                        mapper::toDTO
                )
                .filter(filterRequest);
    }

    @Override
    public Mono<BranchAuditLogDTO> createBranchAuditLog(BranchAuditLogDTO branchAuditLogDTO) {
        return Mono.just(branchAuditLogDTO)
                .map(mapper::toEntity)
                .flatMap(repository::save)
                .map(mapper::toDTO);
    }

    @Override
    public Mono<BranchAuditLogDTO> updateBranchAuditLog(UUID branchAuditLogId, BranchAuditLogDTO branchAuditLogDTO) {
        return repository.findById(branchAuditLogId)
                .switchIfEmpty(Mono.error(new RuntimeException("Branch audit log not found with ID: " + branchAuditLogId)))
                .flatMap(existingBranchAuditLog -> {
                    BranchAuditLog updatedBranchAuditLog = mapper.toEntity(branchAuditLogDTO);
                    updatedBranchAuditLog.setId(branchAuditLogId);
                    return repository.save(updatedBranchAuditLog);
                })
                .map(mapper::toDTO);
    }

    @Override
    public Mono<Void> deleteBranchAuditLog(UUID branchAuditLogId) {
        return repository.findById(branchAuditLogId)
                .switchIfEmpty(Mono.error(new RuntimeException("Branch audit log not found with ID: " + branchAuditLogId)))
                .flatMap(branchAuditLog -> repository.deleteById(branchAuditLogId));
    }

    @Override
    public Mono<BranchAuditLogDTO> getBranchAuditLogById(UUID branchAuditLogId) {
        return repository.findById(branchAuditLogId)
                .switchIfEmpty(Mono.error(new RuntimeException("Branch audit log not found with ID: " + branchAuditLogId)))
                .map(mapper::toDTO);
    }
}