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
import io.fireflysoftware.core.organization.models.entities.*;
import io.fireflysoftware.core.organization.models.repositories.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import java.util.UUID;
import static org.mockito.Mockito.*;

class ScopedParentLookupTest {
    @Test void workingCalendarLookupUsesDerivedParentBinding() {
        UUID id = UUID.randomUUID(), parentId = UUID.randomUUID();
        var entity = WorkingCalendar.builder().id(id).bankId(parentId).build();
        var dto = WorkingCalendarDTO.builder().id(id).bankId(parentId).build();
        var repository = mock(WorkingCalendarRepository.class, invocation ->
                invocation.getMethod().getName().equals("findByIdAndBankId") ? Mono.just(entity) : Mono.empty());
        var mapper = mock(WorkingCalendarMapper.class);
        when(mapper.toDTO(entity)).thenReturn(dto);
        var parent = mock(BankService.class);
        when(parent.getBankById(parentId)).thenReturn(Mono.just(BankDTO.builder().id(parentId).build()));
        var service = new WorkingCalendarServiceImpl();
        ReflectionTestUtils.setField(service, "repository", repository);
        ReflectionTestUtils.setField(service, "mapper", mapper);
        ReflectionTestUtils.setField(service, "bankService", parent);
        StepVerifier.create(service.getWorkingCalendarByIdForBank(parentId, id)).expectNext(dto).verifyComplete();
        verify(repository, never()).findById(id);
    }

    @Test void bankDivisionLookupUsesDerivedParentBinding() {
        UUID id = UUID.randomUUID(), parentId = UUID.randomUUID();
        var entity = BankDivision.builder().id(id).bankId(parentId).build();
        var dto = BankDivisionDTO.builder().id(id).bankId(parentId).build();
        var repository = mock(BankDivisionRepository.class, invocation ->
                invocation.getMethod().getName().equals("findByIdAndBankId") ? Mono.just(entity) : Mono.empty());
        var mapper = mock(BankDivisionMapper.class);
        when(mapper.toDTO(entity)).thenReturn(dto);
        var parent = mock(BankService.class);
        when(parent.getBankById(parentId)).thenReturn(Mono.just(BankDTO.builder().id(parentId).build()));
        var service = new BankDivisionServiceImpl();
        ReflectionTestUtils.setField(service, "repository", repository);
        ReflectionTestUtils.setField(service, "mapper", mapper);
        ReflectionTestUtils.setField(service, "bankService", parent);
        StepVerifier.create(service.getBankDivisionByIdForBank(parentId, id)).expectNext(dto).verifyComplete();
        verify(repository, never()).findById(id);
    }

    @Test void branchDepartmentLookupUsesDerivedParentBinding() {
        UUID id = UUID.randomUUID(), parentId = UUID.randomUUID();
        var entity = BranchDepartment.builder().id(id).branchId(parentId).build();
        var dto = BranchDepartmentDTO.builder().id(id).branchId(parentId).build();
        var repository = mock(BranchDepartmentRepository.class, invocation ->
                invocation.getMethod().getName().equals("findByIdAndBranchId") ? Mono.just(entity) : Mono.empty());
        var mapper = mock(BranchDepartmentMapper.class);
        when(mapper.toDTO(entity)).thenReturn(dto);
        var parent = mock(BranchService.class);
        when(parent.getBranchById(parentId)).thenReturn(Mono.just(BranchDTO.builder().id(parentId).build()));
        var service = new BranchDepartmentServiceImpl();
        ReflectionTestUtils.setField(service, "repository", repository);
        ReflectionTestUtils.setField(service, "mapper", mapper);
        ReflectionTestUtils.setField(service, "branchService", parent);
        StepVerifier.create(service.getBranchDepartmentByIdForBranch(parentId, id)).expectNext(dto).verifyComplete();
        verify(repository, never()).findById(id);
    }

}
