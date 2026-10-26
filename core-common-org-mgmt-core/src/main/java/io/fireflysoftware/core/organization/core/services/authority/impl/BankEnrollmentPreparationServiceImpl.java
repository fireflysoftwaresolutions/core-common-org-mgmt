/*
 * Copyright 2026 Firefly Software Foundation
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
package io.fireflysoftware.core.organization.core.services.authority.impl;

import io.fireflysoftware.core.organization.core.authority.*;
import io.fireflysoftware.core.organization.core.services.authority.*;
import io.fireflysoftware.core.organization.interfaces.authority.*;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import reactor.core.publisher.Mono;

import java.util.Objects;
import java.util.UUID;

public final class BankEnrollmentPreparationServiceImpl
        implements BankEnrollmentPreparationService {
    private final OrganizationPreparationAuthorization authorization;
    private final BankEnrollmentPreparationCaptureService captures;
    private final BankEnrollmentPreparationObservationService observer;
    private final BankEnrollmentPreparationCommandService commands;

    public BankEnrollmentPreparationServiceImpl(
            OrganizationPreparationAuthorization authorization,
            BankEnrollmentPreparationCaptureService captures,
            BankEnrollmentPreparationObservationService observer,
            BankEnrollmentPreparationCommandService commands) {
        this.authorization = Objects.requireNonNull(authorization);
        this.captures = Objects.requireNonNull(captures);
        this.observer = Objects.requireNonNull(observer);
        this.commands = Objects.requireNonNull(commands);
    }

    @Override
    public Mono<EnrollmentPreparedProposal> prepare(
            BankEnrollmentPreparationRequest.Mode mode, UUID tenant, UUID bank, byte[] body) {
        return Mono.defer(
                () ->
                        authorization
                                .discover(
                                        tenant == null ? null : tenant.toString(),
                                        bank == null ? null : bank.toString(),
                                        mode,
                                        body)
                                .flatMap(captures::capture)
                                .flatMap(
                                        captured ->
                                                observer.observeForPreparation(captured)
                                                        .switchIfEmpty(Mono.error(conflict()))
                                                        .flatMap(
                                                                observations ->
                                                                        authorization
                                                                                .recheckCapture(
                                                                                        captured)
                                                                                .flatMap(
                                                                                        current -> {
                                                                                            BankEnrollmentPreparationPlan
                                                                                                            .Prepared
                                                                                                    plan;
                                                                                            try {
                                                                                                var
                                                                                                        allocation =
                                                                                                                new BankEnrollmentPreparationPlan
                                                                                                                        .Allocation(
                                                                                                                        UUID.randomUUID()
                                                                                                                                .toString(),
                                                                                                                        UUID.randomUUID()
                                                                                                                                .toString(),
                                                                                                                        UUID.randomUUID()
                                                                                                                                .toString());
                                                                                                plan =
                                                                                                        BankEnrollmentPreparationPlan
                                                                                                                .build(
                                                                                                                        current.discovery()
                                                                                                                                .desired(),
                                                                                                                        current
                                                                                                                                .local(),
                                                                                                                        allocation,
                                                                                                                        observations,
                                                                                                                        current
                                                                                                                                .currentEpochMillis());
                                                                                            } catch (IllegalArgumentException
                                                                                                    | IllegalStateException
                                                                                                            error) {
                                                                                                throw conflict();
                                                                                            }
                                                                                            return authorization
                                                                                                    .admitPrepared(
                                                                                                            current,
                                                                                                            plan);
                                                                                        })))
                                .flatMap(commands::prepare));
    }

    private static ResponseStatusException conflict() {
        return new ResponseStatusException(
                HttpStatus.CONFLICT, "Current complete preparation observations required");
    }
}
