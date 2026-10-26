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

import io.fireflysoftware.core.organization.core.authority.PreparationWriteEvidenceCodec;
import io.fireflysoftware.core.organization.core.services.authority.BankEnrollmentActionReadService;
import io.fireflysoftware.core.organization.core.services.authority.BankEnrollmentPreparationRecoveryService;
import io.fireflysoftware.core.organization.interfaces.authority.EnrollmentPreparedProposal;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import reactor.core.publisher.Mono;

import java.util.Objects;
import java.util.UUID;

public final class BankEnrollmentPreparationRecoveryServiceImpl
        implements BankEnrollmentPreparationRecoveryService {
    private final BankEnrollmentActionReadService history;

    public BankEnrollmentPreparationRecoveryServiceImpl(BankEnrollmentActionReadService history) {
        this.history = Objects.requireNonNull(history);
    }

    @Override
    public Mono<EnrollmentPreparedProposal> recover(
            UUID tenant, UUID bank, UUID action, byte[] originalDesiredRequest) {
        return Mono.defer(
                () -> {
                    byte[] desired =
                            originalDesiredRequest == null ? null : originalDesiredRequest.clone();
                    return history.read(tenant, bank, action)
                            .map(
                                    retained -> {
                                        if (tenant == null
                                                || bank == null
                                                || action == null
                                                || !tenant.toString().equals(retained.tenantId())
                                                || !bank.toString().equals(retained.bankId())
                                                || !action.toString().equals(retained.actionId()))
                                            throw changed();
                                        try {
                                            return PreparationWriteEvidenceCodec.recover(
                                                    retained, desired);
                                        } catch (IllegalArgumentException
                                                | IllegalStateException error) {
                                            throw changed();
                                        }
                                    });
                });
    }

    private static ResponseStatusException changed() {
        return new ResponseStatusException(
                HttpStatus.CONFLICT, "Exact retained preparation intent required");
    }
}
