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
import io.fireflysoftware.core.organization.core.services.BranchPositionService;

import io.fireflysoftware.core.organization.core.services.ScopedFilterSupport;
import org.fireflyframework.core.filters.FilterRequest;
import org.fireflyframework.core.filters.FilterUtils;
import org.fireflyframework.core.queries.PaginationResponse;
import io.fireflysoftware.core.organization.core.mappers.BranchPositionMapper;
import io.fireflysoftware.core.organization.interfaces.dtos.BranchPositionDTO;
import io.fireflysoftware.core.organization.models.entities.BranchPosition;
import io.fireflysoftware.core.organization.models.repositories.BranchPositionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;
import java.util.UUID;

@Service
@Transactional
public class BranchPositionServiceImpl implements BranchPositionService {

    @Autowired
    private BranchPositionRepository repository;

    @Autowired
    private BranchPositionMapper mapper;

    @Autowired
    private BranchService branchService;

    @Autowired
    private BranchDepartmentService branchDepartmentService;

    @Override
    public Mono<PaginationResponse<BranchPositionDTO>> filterBranchPositions(FilterRequest<BranchPositionDTO> filterRequest) {
        return FilterUtils
                .createFilter(
                        BranchPosition.class,
                        mapper::toDTO
                )
                .filter(filterRequest);
    }

    @Override
    public Mono<PaginationResponse<BranchPositionDTO>> filterBranchPositionsForDepartment(UUID branchId, UUID departmentId, FilterRequest<BranchPositionDTO> filterRequest) {
        return branchDepartmentService.getBranchDepartmentByIdForBranch(branchId, departmentId)
                .flatMap(department -> ScopedFilterSupport.filter(BranchPosition.class, mapper::toDTO, filterRequest,
                        (criteria, pageable) -> repository.findPageForDepartment(departmentId, criteria, pageable),
                        criteria -> repository.countForDepartment(departmentId, criteria)));
    }

    @Override
    public Mono<BranchPositionDTO> createBranchPosition(BranchPositionDTO branchPositionDTO) {
        return Mono.just(branchPositionDTO)
                .map(mapper::toEntity)
                .flatMap(repository::save)
                .map(mapper::toDTO);
    }

    @Override
    public Mono<BranchPositionDTO> createBranchPositionForDepartment(UUID branchId, UUID departmentId, BranchPositionDTO branchPositionDTO) {
        return branchService.getBranchById(branchId)
                .switchIfEmpty(Mono.error(new RuntimeException("Branch not found with ID: " + branchId)))
                .flatMap(branch -> branchDepartmentService.getBranchDepartmentById(departmentId))
                .filter(department -> department.getBranchId().equals(branchId))
                .switchIfEmpty(Mono.error(new RuntimeException("Department not found for branch with ID: " + branchId)))
                .flatMap(department -> {
                    branchPositionDTO.setDepartmentId(departmentId);
                    return createBranchPosition(branchPositionDTO);
                });
    }

    @Override
    public Mono<BranchPositionDTO> updateBranchPosition(UUID branchPositionId, BranchPositionDTO branchPositionDTO) {
        return repository.findById(branchPositionId)
                .switchIfEmpty(Mono.error(new RuntimeException("Branch position not found with ID: " + branchPositionId)))
                .flatMap(existingBranchPosition -> {
                    BranchPosition updatedBranchPosition = mapper.toEntity(branchPositionDTO);
                    updatedBranchPosition.setId(branchPositionId);
                    return repository.save(updatedBranchPosition);
                })
                .map(mapper::toDTO);
    }

    @Override
    public Mono<BranchPositionDTO> updateBranchPositionForDepartment(UUID branchId, UUID departmentId, UUID positionId, BranchPositionDTO branchPositionDTO) {
        return branchService.getBranchById(branchId)
                .switchIfEmpty(Mono.error(new RuntimeException("Branch not found with ID: " + branchId)))
                .flatMap(branch -> branchDepartmentService.getBranchDepartmentById(departmentId))
                .filter(department -> department.getBranchId().equals(branchId))
                .switchIfEmpty(Mono.error(new RuntimeException("Department not found for branch with ID: " + branchId)))
                .flatMap(department -> getBranchPositionById(positionId))
                .filter(position -> position.getDepartmentId().equals(departmentId))
                .switchIfEmpty(Mono.error(new RuntimeException("Position not found for department with ID: " + departmentId)))
                .flatMap(position -> {
                    branchPositionDTO.setDepartmentId(departmentId);
                    return updateBranchPosition(positionId, branchPositionDTO);
                });
    }

    @Override
    public Mono<Void> deleteBranchPosition(UUID branchPositionId) {
        return repository.findById(branchPositionId)
                .switchIfEmpty(Mono.error(new RuntimeException("Branch position not found with ID: " + branchPositionId)))
                .flatMap(branchPosition -> repository.deleteById(branchPositionId));
    }

    @Override
    public Mono<Void> deleteBranchPositionForDepartment(UUID branchId, UUID departmentId, UUID positionId) {
        return branchService.getBranchById(branchId)
                .switchIfEmpty(Mono.error(new RuntimeException("Branch not found with ID: " + branchId)))
                .flatMap(branch -> branchDepartmentService.getBranchDepartmentById(departmentId))
                .filter(department -> department.getBranchId().equals(branchId))
                .switchIfEmpty(Mono.error(new RuntimeException("Department not found for branch with ID: " + branchId)))
                .flatMap(department -> getBranchPositionById(positionId))
                .filter(position -> position.getDepartmentId().equals(departmentId))
                .switchIfEmpty(Mono.error(new RuntimeException("Position not found for department with ID: " + departmentId)))
                .flatMap(position -> deleteBranchPosition(positionId));
    }

    @Override
    public Mono<BranchPositionDTO> getBranchPositionById(UUID branchPositionId) {
        return repository.findById(branchPositionId)
                .switchIfEmpty(Mono.error(new RuntimeException("Branch position not found with ID: " + branchPositionId)))
                .map(mapper::toDTO);
    }

    @Override
    public Mono<BranchPositionDTO> getBranchPositionByIdForDepartment(UUID branchId, UUID departmentId, UUID positionId) {
        return branchService.getBranchById(branchId)
                .switchIfEmpty(Mono.error(new RuntimeException("Branch not found with ID: " + branchId)))
                .flatMap(branch -> branchDepartmentService.getBranchDepartmentById(departmentId))
                .filter(department -> department.getBranchId().equals(branchId))
                .switchIfEmpty(Mono.error(new RuntimeException("Department not found for branch with ID: " + branchId)))
                .flatMap(department -> getBranchPositionById(positionId))
                .filter(position -> position.getDepartmentId().equals(departmentId))
                .switchIfEmpty(Mono.error(new RuntimeException("Position not found for department with ID: " + departmentId)));
    }
}
