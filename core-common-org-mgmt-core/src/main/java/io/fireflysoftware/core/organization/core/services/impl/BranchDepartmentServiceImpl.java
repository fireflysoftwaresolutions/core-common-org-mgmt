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

import io.fireflysoftware.core.organization.core.services.BranchService;
import io.fireflysoftware.core.organization.core.services.BranchDepartmentService;

import io.fireflysoftware.core.organization.core.services.ScopedFilterSupport;
import org.fireflyframework.core.filters.FilterRequest;
import org.fireflyframework.core.filters.FilterUtils;
import org.fireflyframework.core.queries.PaginationResponse;
import io.fireflysoftware.core.organization.core.mappers.BranchDepartmentMapper;
import io.fireflysoftware.core.organization.interfaces.dtos.BranchDepartmentDTO;
import io.fireflysoftware.core.organization.models.entities.BranchDepartment;
import io.fireflysoftware.core.organization.models.repositories.BranchDepartmentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;
import java.util.UUID;

@Service
@Transactional
public class BranchDepartmentServiceImpl implements BranchDepartmentService {

    @Autowired
    private BranchDepartmentRepository repository;

    @Autowired
    private BranchDepartmentMapper mapper;

    @Autowired
    private BranchService branchService;

    @Override
    public Mono<PaginationResponse<BranchDepartmentDTO>> filterBranchDepartments(FilterRequest<BranchDepartmentDTO> filterRequest) {
        return FilterUtils
                .createFilter(
                        BranchDepartment.class,
                        mapper::toDTO
                )
                .filter(filterRequest);
    }

    @Override
    public Mono<PaginationResponse<BranchDepartmentDTO>> filterBranchDepartmentsForBranch(UUID branchId, FilterRequest<BranchDepartmentDTO> filterRequest) {
        return branchService.getBranchById(branchId)
                .switchIfEmpty(Mono.error(new RuntimeException("Branch not found with ID: " + branchId)))
                .flatMap(branch -> ScopedFilterSupport.filter(BranchDepartment.class, mapper::toDTO, filterRequest,
                        (criteria, pageable) -> repository.findPageForBranch(branchId, criteria, pageable),
                        criteria -> repository.countForBranch(branchId, criteria)));
    }

    @Override
    public Mono<BranchDepartmentDTO> createBranchDepartment(BranchDepartmentDTO branchDepartmentDTO) {
        return Mono.just(branchDepartmentDTO)
                .map(mapper::toEntity)
                .flatMap(repository::save)
                .map(mapper::toDTO);
    }

    @Override
    public Mono<BranchDepartmentDTO> createBranchDepartmentForBranch(UUID branchId, BranchDepartmentDTO branchDepartmentDTO) {
        return branchService.getBranchById(branchId)
                .switchIfEmpty(Mono.error(new RuntimeException("Branch not found with ID: " + branchId)))
                .flatMap(branch -> {
                    branchDepartmentDTO.setBranchId(branchId);
                    return createBranchDepartment(branchDepartmentDTO);
                });
    }

    @Override
    public Mono<BranchDepartmentDTO> updateBranchDepartment(UUID branchDepartmentId, BranchDepartmentDTO branchDepartmentDTO) {
        return repository.findById(branchDepartmentId)
                .switchIfEmpty(Mono.error(new RuntimeException("Branch department not found with ID: " + branchDepartmentId)))
                .flatMap(existingBranchDepartment -> {
                    BranchDepartment updatedBranchDepartment = mapper.toEntity(branchDepartmentDTO);
                    updatedBranchDepartment.setId(branchDepartmentId);
                    return repository.save(updatedBranchDepartment);
                })
                .map(mapper::toDTO);
    }

    @Override
    public Mono<BranchDepartmentDTO> updateBranchDepartmentForBranch(UUID branchId, UUID departmentId, BranchDepartmentDTO branchDepartmentDTO) {
        return branchService.getBranchById(branchId)
                .switchIfEmpty(Mono.error(new RuntimeException("Branch not found with ID: " + branchId)))
                .flatMap(branch -> getBranchDepartmentById(departmentId))
                .filter(department -> department.getBranchId().equals(branchId))
                .switchIfEmpty(Mono.error(new RuntimeException("Department not found for branch with ID: " + branchId)))
                .flatMap(department -> {
                    branchDepartmentDTO.setBranchId(branchId);
                    return updateBranchDepartment(departmentId, branchDepartmentDTO);
                });
    }

    @Override
    public Mono<Void> deleteBranchDepartment(UUID branchDepartmentId) {
        return repository.findById(branchDepartmentId)
                .switchIfEmpty(Mono.error(new RuntimeException("Branch department not found with ID: " + branchDepartmentId)))
                .flatMap(branchDepartment -> repository.deleteById(branchDepartmentId));
    }

    @Override
    public Mono<Void> deleteBranchDepartmentForBranch(UUID branchId, UUID departmentId) {
        return branchService.getBranchById(branchId)
                .switchIfEmpty(Mono.error(new RuntimeException("Branch not found with ID: " + branchId)))
                .flatMap(branch -> getBranchDepartmentById(departmentId))
                .filter(department -> department.getBranchId().equals(branchId))
                .switchIfEmpty(Mono.error(new RuntimeException("Department not found for branch with ID: " + branchId)))
                .flatMap(department -> deleteBranchDepartment(departmentId));
    }

    @Override
    public Mono<BranchDepartmentDTO> getBranchDepartmentById(UUID branchDepartmentId) {
        return repository.findById(branchDepartmentId)
                .switchIfEmpty(Mono.error(new RuntimeException("Branch department not found with ID: " + branchDepartmentId)))
                .map(mapper::toDTO);
    }

    @Override
    public Mono<BranchDepartmentDTO> getBranchDepartmentByIdForBranch(UUID branchId, UUID departmentId) {
        return branchService.getBranchById(branchId)
                .switchIfEmpty(Mono.error(new RuntimeException("Branch not found with ID: " + branchId)))
                .flatMap(branch -> repository.findByIdAndBranchId(departmentId, branchId))
                .switchIfEmpty(Mono.error(new RuntimeException("Department not found for branch with ID: " + branchId)))
                .map(mapper::toDTO);
    }
}
