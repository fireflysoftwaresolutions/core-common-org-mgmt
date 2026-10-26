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

import io.fireflysoftware.core.organization.core.services.impl.*;
import io.fireflysoftware.core.organization.core.mappers.*;
import io.fireflysoftware.core.organization.interfaces.dtos.*;
import io.fireflysoftware.core.organization.models.repositories.*;
import org.fireflyframework.core.filters.FilterUtils;
import org.junit.jupiter.api.Test;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ScopedRelatedServiceTest {
    @Test void branchQueriesKeepBank() {
        FilterUtils.initializeTemplate(mock(R2dbcEntityTemplate.class));
        var repository = mock(BranchRepository.class);
        var parentService = mock(BankService.class);
        var service = new BranchServiceImpl();
        ReflectionTestUtils.setField(service, "repository", repository);
        ReflectionTestUtils.setField(service, "mapper", mock(BranchMapper.class));
        ReflectionTestUtils.setField(service, "bankService", parentService);
        UUID parent = UUID.randomUUID(), ancestor = UUID.randomUUID();
        when(parentService.getBankById(parent)).thenReturn(Mono.just(BankDTO.builder().id(parent).build()));
        when(repository.findPageForBank(eq(parent), any(), any())).thenReturn(Flux.empty());
        when(repository.countForBank(eq(parent), any())).thenReturn(Mono.just(4L));
        StepVerifier.create(Mono.defer(() -> service.filterBranchesForBank(parent, null)))
                .assertNext(page -> assertEquals(4L, page.getTotalElements())).verifyComplete();
        verify(repository).findPageForBank(eq(parent), any(), any());
        verify(repository).countForBank(eq(parent), any());
        verifyNoMoreInteractions(repository);
    }

    @Test void bankRegionQueriesKeepDivision() {
        FilterUtils.initializeTemplate(mock(R2dbcEntityTemplate.class));
        var repository = mock(BankRegionRepository.class);
        var parentService = mock(BankDivisionService.class);
        var service = new BankRegionServiceImpl();
        ReflectionTestUtils.setField(service, "repository", repository);
        ReflectionTestUtils.setField(service, "mapper", mock(BankRegionMapper.class));
        ReflectionTestUtils.setField(service, "bankDivisionService", parentService);
        UUID parent = UUID.randomUUID(), ancestor = UUID.randomUUID();
        when(parentService.getBankDivisionByIdForBank(ancestor, parent)).thenReturn(Mono.just(BankDivisionDTO.builder().id(parent).build()));
        when(repository.findPageForDivision(eq(parent), any(), any())).thenReturn(Flux.empty());
        when(repository.countForDivision(eq(parent), any())).thenReturn(Mono.just(4L));
        StepVerifier.create(Mono.defer(() -> service.filterBankRegionsForDivision(ancestor, parent, null)))
                .assertNext(page -> assertEquals(4L, page.getTotalElements())).verifyComplete();
        verify(repository).findPageForDivision(eq(parent), any(), any());
        verify(repository).countForDivision(eq(parent), any());
        verifyNoMoreInteractions(repository);
    }

    @Test void branchDepartmentQueriesKeepBranch() {
        FilterUtils.initializeTemplate(mock(R2dbcEntityTemplate.class));
        var repository = mock(BranchDepartmentRepository.class);
        var parentService = mock(BranchService.class);
        var service = new BranchDepartmentServiceImpl();
        ReflectionTestUtils.setField(service, "repository", repository);
        ReflectionTestUtils.setField(service, "mapper", mock(BranchDepartmentMapper.class));
        ReflectionTestUtils.setField(service, "branchService", parentService);
        UUID parent = UUID.randomUUID(), ancestor = UUID.randomUUID();
        when(parentService.getBranchById(parent)).thenReturn(Mono.just(BranchDTO.builder().id(parent).build()));
        when(repository.findPageForBranch(eq(parent), any(), any())).thenReturn(Flux.empty());
        when(repository.countForBranch(eq(parent), any())).thenReturn(Mono.just(4L));
        StepVerifier.create(Mono.defer(() -> service.filterBranchDepartmentsForBranch(parent, null)))
                .assertNext(page -> assertEquals(4L, page.getTotalElements())).verifyComplete();
        verify(repository).findPageForBranch(eq(parent), any(), any());
        verify(repository).countForBranch(eq(parent), any());
        verifyNoMoreInteractions(repository);
    }

    @Test void branchPositionQueriesKeepDepartment() {
        FilterUtils.initializeTemplate(mock(R2dbcEntityTemplate.class));
        var repository = mock(BranchPositionRepository.class);
        var parentService = mock(BranchDepartmentService.class);
        var service = new BranchPositionServiceImpl();
        ReflectionTestUtils.setField(service, "repository", repository);
        ReflectionTestUtils.setField(service, "mapper", mock(BranchPositionMapper.class));
        ReflectionTestUtils.setField(service, "branchDepartmentService", parentService);
        UUID parent = UUID.randomUUID(), ancestor = UUID.randomUUID();
        when(parentService.getBranchDepartmentByIdForBranch(ancestor, parent)).thenReturn(Mono.just(BranchDepartmentDTO.builder().id(parent).build()));
        when(repository.findPageForDepartment(eq(parent), any(), any())).thenReturn(Flux.empty());
        when(repository.countForDepartment(eq(parent), any())).thenReturn(Mono.just(4L));
        StepVerifier.create(Mono.defer(() -> service.filterBranchPositionsForDepartment(ancestor, parent, null)))
                .assertNext(page -> assertEquals(4L, page.getTotalElements())).verifyComplete();
        verify(repository).findPageForDepartment(eq(parent), any(), any());
        verify(repository).countForDepartment(eq(parent), any());
        verifyNoMoreInteractions(repository);
    }

    @Test void branchHoursQueriesKeepBranch() {
        FilterUtils.initializeTemplate(mock(R2dbcEntityTemplate.class));
        var repository = mock(BranchHoursRepository.class);
        var parentService = mock(BranchService.class);
        var service = new BranchHoursServiceImpl();
        ReflectionTestUtils.setField(service, "repository", repository);
        ReflectionTestUtils.setField(service, "mapper", mock(BranchHoursMapper.class));
        ReflectionTestUtils.setField(service, "branchService", parentService);
        UUID parent = UUID.randomUUID(), ancestor = UUID.randomUUID();
        when(parentService.getBranchById(parent)).thenReturn(Mono.just(BranchDTO.builder().id(parent).build()));
        when(repository.findPageForBranch(eq(parent), any(), any())).thenReturn(Flux.empty());
        when(repository.countForBranch(eq(parent), any())).thenReturn(Mono.just(4L));
        StepVerifier.create(Mono.defer(() -> service.filterBranchHoursForBranch(parent, null)))
                .assertNext(page -> assertEquals(4L, page.getTotalElements())).verifyComplete();
        verify(repository).findPageForBranch(eq(parent), any(), any());
        verify(repository).countForBranch(eq(parent), any());
        verifyNoMoreInteractions(repository);
    }

}
