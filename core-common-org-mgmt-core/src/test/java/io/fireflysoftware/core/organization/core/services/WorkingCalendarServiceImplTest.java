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

import io.fireflysoftware.core.organization.core.services.impl.WorkingCalendarServiceImpl;
import org.fireflyframework.core.filters.FilterRequest;
import org.fireflyframework.core.filters.FilterUtils;
import org.fireflyframework.core.queries.PaginationResponse;
import io.fireflysoftware.core.organization.core.mappers.WorkingCalendarMapper;
import io.fireflysoftware.core.organization.interfaces.dtos.WorkingCalendarDTO;
import io.fireflysoftware.core.organization.models.entities.WorkingCalendar;
import io.fireflysoftware.core.organization.models.repositories.WorkingCalendarRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.function.Function;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.util.UUID;

@ExtendWith(MockitoExtension.class)
public class WorkingCalendarServiceImplTest {

    @Mock
    private WorkingCalendarRepository workingCalendarRepository;

    @Mock
    private WorkingCalendarMapper workingCalendarMapper;

    @InjectMocks
    private WorkingCalendarServiceImpl workingCalendarService;

    private WorkingCalendarDTO workingCalendarDTO;
    private WorkingCalendar workingCalendar;

    @BeforeEach
    void setUp() {
        // Setup test data
        LocalDateTime now = LocalDateTime.now();
        
        UUID testId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        UUID testBankId = UUID.fromString("223e4567-e89b-12d3-a456-426614174000");
        UUID testTimeZoneId = UUID.fromString("323e4567-e89b-12d3-a456-426614174000");
        UUID testUserId = UUID.fromString("423e4567-e89b-12d3-a456-426614174000");

        workingCalendarDTO = WorkingCalendarDTO.builder()
                .id(testId)
                .bankId(testBankId)
                .name("Standard Working Calendar")
                .description("Standard 9-5 Working Calendar")
                .isDefault(true)
                .timeZoneId(testTimeZoneId)
                .createdAt(now)
                .createdBy(testUserId)
                .build();

        workingCalendar = WorkingCalendar.builder()
                .id(testId)
                .bankId(testBankId)
                .name("Standard Working Calendar")
                .description("Standard 9-5 Working Calendar")
                .isDefault(true)
                .timeZoneId(testTimeZoneId)
                .createdAt(now)
                .createdBy(testUserId)
                .build();
    }

    @Test
    void createWorkingCalendar_ShouldCreateAndReturnWorkingCalendar() {
        // Arrange
        when(workingCalendarMapper.toEntity(workingCalendarDTO)).thenReturn(workingCalendar);
        when(workingCalendarRepository.save(workingCalendar)).thenReturn(Mono.just(workingCalendar));
        when(workingCalendarMapper.toDTO(workingCalendar)).thenReturn(workingCalendarDTO);

        // Act & Assert
        StepVerifier.create(workingCalendarService.createWorkingCalendar(workingCalendarDTO))
                .expectNext(workingCalendarDTO)
                .verifyComplete();

        verify(workingCalendarMapper).toEntity(workingCalendarDTO);
        verify(workingCalendarRepository).save(workingCalendar);
        verify(workingCalendarMapper).toDTO(workingCalendar);
    }

    @Test
    void updateWorkingCalendar_WhenWorkingCalendarExists_ShouldUpdateAndReturnWorkingCalendar() {
        // Arrange
        UUID workingCalendarId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        when(workingCalendarRepository.findById(workingCalendarId)).thenReturn(Mono.just(workingCalendar));
        when(workingCalendarMapper.toEntity(workingCalendarDTO)).thenReturn(workingCalendar);
        when(workingCalendarRepository.save(workingCalendar)).thenReturn(Mono.just(workingCalendar));
        when(workingCalendarMapper.toDTO(workingCalendar)).thenReturn(workingCalendarDTO);

        // Act & Assert
        StepVerifier.create(workingCalendarService.updateWorkingCalendar(workingCalendarId, workingCalendarDTO))
                .expectNext(workingCalendarDTO)
                .verifyComplete();

        verify(workingCalendarRepository).findById(workingCalendarId);
        verify(workingCalendarMapper).toEntity(workingCalendarDTO);
        verify(workingCalendarRepository).save(workingCalendar);
        verify(workingCalendarMapper).toDTO(workingCalendar);
    }

    @Test
    void updateWorkingCalendar_WhenWorkingCalendarDoesNotExist_ShouldReturnError() {
        // Arrange
        UUID workingCalendarId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        when(workingCalendarRepository.findById(workingCalendarId)).thenReturn(Mono.empty());

        // Act & Assert
        StepVerifier.create(workingCalendarService.updateWorkingCalendar(workingCalendarId, workingCalendarDTO))
                .expectErrorMatches(throwable -> throwable instanceof RuntimeException &&
                        throwable.getMessage().equals("Working calendar not found with ID: " + workingCalendarId))
                .verify();

        verify(workingCalendarRepository).findById(workingCalendarId);
    }

    @Test
    void deleteWorkingCalendar_WhenWorkingCalendarExists_ShouldDeleteWorkingCalendar() {
        // Arrange
        UUID workingCalendarId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        when(workingCalendarRepository.findById(workingCalendarId)).thenReturn(Mono.just(workingCalendar));
        when(workingCalendarRepository.deleteById(workingCalendarId)).thenReturn(Mono.empty());

        // Act & Assert
        StepVerifier.create(workingCalendarService.deleteWorkingCalendar(workingCalendarId))
                .verifyComplete();

        verify(workingCalendarRepository).findById(workingCalendarId);
        verify(workingCalendarRepository).deleteById(workingCalendarId);
    }

    @Test
    void deleteWorkingCalendar_WhenWorkingCalendarDoesNotExist_ShouldReturnError() {
        // Arrange
        UUID workingCalendarId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        when(workingCalendarRepository.findById(workingCalendarId)).thenReturn(Mono.empty());

        // Act & Assert
        StepVerifier.create(workingCalendarService.deleteWorkingCalendar(workingCalendarId))
                .expectErrorMatches(throwable -> throwable instanceof RuntimeException &&
                        throwable.getMessage().equals("Working calendar not found with ID: " + workingCalendarId))
                .verify();

        verify(workingCalendarRepository).findById(workingCalendarId);
    }

    @Test
    void getWorkingCalendarById_WhenWorkingCalendarExists_ShouldReturnWorkingCalendar() {
        // Arrange
        UUID workingCalendarId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        when(workingCalendarRepository.findById(workingCalendarId)).thenReturn(Mono.just(workingCalendar));
        when(workingCalendarMapper.toDTO(workingCalendar)).thenReturn(workingCalendarDTO);

        // Act & Assert
        StepVerifier.create(workingCalendarService.getWorkingCalendarById(workingCalendarId))
                .expectNext(workingCalendarDTO)
                .verifyComplete();

        verify(workingCalendarRepository).findById(workingCalendarId);
        verify(workingCalendarMapper).toDTO(workingCalendar);
    }

    @Test
    void getWorkingCalendarById_WhenWorkingCalendarDoesNotExist_ShouldReturnError() {
        // Arrange
        UUID workingCalendarId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        when(workingCalendarRepository.findById(workingCalendarId)).thenReturn(Mono.empty());

        // Act & Assert
        StepVerifier.create(workingCalendarService.getWorkingCalendarById(workingCalendarId))
                .expectErrorMatches(throwable -> throwable instanceof RuntimeException &&
                        throwable.getMessage().equals("Working calendar not found with ID: " + workingCalendarId))
                .verify();

        verify(workingCalendarRepository).findById(workingCalendarId);
    }
}