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
import io.fireflysoftware.core.organization.core.services.authority.BankEnrollmentPreparationCaptureService;
import io.fireflysoftware.core.organization.interfaces.authority.BankEnrollmentOwnerReference;
import io.fireflysoftware.core.organization.models.entities.authority.*;
import io.fireflysoftware.core.organization.models.repositories.BankRepository;
import io.fireflysoftware.core.organization.models.repositories.authority.*;

import org.springframework.data.r2dbc.core.R2dbcEntityOperations;
import org.springframework.http.HttpStatus;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import org.springframework.transaction.reactive.TransactionalOperator;
import org.springframework.web.server.ResponseStatusException;

import reactor.core.publisher.Mono;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class BankEnrollmentPreparationCaptureServiceImpl
        implements BankEnrollmentPreparationCaptureService {
    private record Capture(BankEnrollmentPreparationPlan.LocalSnapshot local, long until) {}

    private final OrganizationPreparationAuthorization authorization;
    private final NativeEnrollmentPreparationContextRepository preparations;
    private final NativeEnrollmentContextRepository contexts;
    private final BankRepository banks;
    private final NativeBankGateRepository gates;
    private final NativeBankEnrollmentOwnerIdentityRepository versions;
    private final NativeBankEnrollmentControlRepository controls;
    private final TransactionalOperator transactions;

    public BankEnrollmentPreparationCaptureServiceImpl(
            OrganizationPreparationAuthorization authorization,
            NativeEnrollmentPreparationContextRepository preparations,
            NativeEnrollmentContextRepository contexts,
            BankRepository banks,
            NativeBankGateRepository gates,
            NativeBankEnrollmentOwnerIdentityRepository versions,
            NativeBankEnrollmentControlRepository controls,
            R2dbcEntityOperations operations,
            R2dbcTransactionManager manager) {
        this.authorization = Objects.requireNonNull(authorization);
        this.preparations = Objects.requireNonNull(preparations);
        this.contexts = Objects.requireNonNull(contexts);
        this.banks = Objects.requireNonNull(banks);
        this.gates = Objects.requireNonNull(gates);
        this.versions = Objects.requireNonNull(versions);
        this.controls = Objects.requireNonNull(controls);
        if (manager.getConnectionFactory() != operations.getDatabaseClient().getConnectionFactory())
            throw new IllegalStateException(
                    "Preparation capture requires the repository ConnectionFactory transaction"
                        + " manager");
        transactions = TransactionalOperator.create(manager);
    }

    @Override
    public Mono<OrganizationPreparationAuthorization.Captured> capture(
            OrganizationPreparationAuthorization.Discovery discovery) {
        return authorization
                .recheckDiscovery(discovery)
                .flatMap(
                        current ->
                                Mono.defer(() -> read(current))
                                        .as(transactions::transactional)
                                        .flatMap(
                                                captured ->
                                                        authorization.bindCapture(
                                                                current,
                                                                captured.local(),
                                                                captured.until())));
    }

    private Mono<Capture> read(OrganizationPreparationAuthorization.Discovery admission) {
        UUID nonce = UUID.randomUUID();
        var packet = PreparationCaptureEvidenceCodec.context(admission, nonce);
        var desired = admission.desired().request();
        UUID bank = UUID.fromString(desired.bankId());
        return preparations
                .beginCapture(nonce, ProposalTerminationEvidenceCodec.json(packet))
                .filter(nonce::equals)
                .switchIfEmpty(Mono.error(conflict()))
                .flatMap(contexts::lockContext)
                .switchIfEmpty(Mono.error(conflict()))
                .flatMap(
                        stamp -> {
                            if (!nonce.equals(stamp.contextNonce())
                                    || !packet.contextDigest().equals(stamp.contextDigest())
                                    || stamp.dbNowMillis() <= 0
                                    || stamp.authorityUntilEpochMillis() <= stamp.dbNowMillis()
                                    || stamp.commandUntilEpochMillis() <= stamp.dbNowMillis()
                                    || stamp.authorityUntilEpochMillis()
                                            > admission.validUntilEpochMillis()
                                    || stamp.commandUntilEpochMillis()
                                            > desired.notAfterEpochMillis()
                                    || stamp.authorityUntilEpochMillis()
                                            > stamp.dbNowMillis() + 30000
                                    || stamp.commandUntilEpochMillis()
                                            > stamp.dbNowMillis() + 30000) throw conflict();
                            admission.requireCurrent();
                            return current(nonce)
                                    .then(banks.findById(bank))
                                    .switchIfEmpty(Mono.error(missing()))
                                    .flatMap(
                                            row -> {
                                                if (!bank.equals(row.getId())
                                                        || !Boolean.TRUE.equals(row.getIsActive()))
                                                    throw conflict();
                                                return gates.findById(bank)
                                                        .switchIfEmpty(Mono.error(conflict()))
                                                        .flatMap(
                                                                gate -> {
                                                                    if (!bank.equals(gate.bankId()))
                                                                        throw conflict();
                                                                    return owner(gate)
                                                                            .map(
                                                                                    previous ->
                                                                                            new BankEnrollmentPreparationPlan
                                                                                                    .LocalSnapshot(
                                                                                                    bank
                                                                                                            .toString(),
                                                                                                    gate
                                                                                                            .bankGeneration(),
                                                                                                    gate
                                                                                                            .ownershipGeneration(),
                                                                                                    gate
                                                                                                            .sourceGeneration(),
                                                                                                    previous))
                                                                            .switchIfEmpty(
                                                                                    Mono
                                                                                            .fromSupplier(
                                                                                                    () ->
                                                                                                            new BankEnrollmentPreparationPlan
                                                                                                                    .LocalSnapshot(
                                                                                                                    bank
                                                                                                                            .toString(),
                                                                                                                    gate
                                                                                                                            .bankGeneration(),
                                                                                                                    gate
                                                                                                                            .ownershipGeneration(),
                                                                                                                    gate
                                                                                                                            .sourceGeneration(),
                                                                                                                    null)))
                                                                            .flatMap(
                                                                                    local -> {
                                                                                        admission
                                                                                                .requireCurrent();
                                                                                        return current(
                                                                                                        nonce)
                                                                                                .thenReturn(
                                                                                                        new Capture(
                                                                                                                local,
                                                                                                                stamp
                                                                                                                        .authorityUntilEpochMillis()));
                                                                                    });
                                                                });
                                            });
                        });
    }

    private Mono<BankEnrollmentOwnerReference> owner(BankGateRow gate) {
        if (gate.currentTenantId() == null) return Mono.empty();
        var key =
                new BankEnrollmentVersionKey(
                        gate.currentTenantId(),
                        gate.currentEnrollmentId(),
                        gate.currentEnrollmentVersion());
        return versions.findById(key)
                .switchIfEmpty(Mono.error(conflict()))
                .flatMap(
                        version -> {
                            if (!key.equals(version.id())
                                    || !gate.bankId().equals(version.bankId())
                                    || !gate.currentInstitutionId().equals(version.institutionId())
                                    || !gate.currentIssuerId().equals(version.issuerId())
                                    || version.ownershipGenerationAtAdmission() <= 0
                                    || version.ownershipGenerationAtAdmission()
                                            > gate.ownershipGeneration()) throw conflict();
                            return controls.findById(key)
                                    .switchIfEmpty(Mono.error(conflict()))
                                    .map(
                                            control -> {
                                                if (!key.equals(control.id())
                                                        || !Set.of("ACTIVE", "SUSPENDED")
                                                                .contains(control.state())
                                                        || control.controlVersion() < 0
                                                        || control.controlVersion()
                                                                == Long.MAX_VALUE
                                                        || control.lastActionId() == null
                                                        || control.lastActionTenantId() == null)
                                                    throw conflict();
                                                return new BankEnrollmentOwnerReference(
                                                        key.tenantId().toString(),
                                                        gate.currentInstitutionId().toString(),
                                                        gate.currentIssuerId().toString(),
                                                        key.enrollmentId().toString(),
                                                        key.version(),
                                                        control.controlVersion());
                                            });
                        });
    }

    private Mono<Boolean> current(UUID nonce) {
        return contexts.contextCurrent(nonce)
                .filter(Boolean.TRUE::equals)
                .switchIfEmpty(Mono.error(conflict()));
    }

    private static ResponseStatusException conflict() {
        return new ResponseStatusException(
                HttpStatus.CONFLICT, "Bank preparation source changed or expired");
    }

    private static ResponseStatusException missing() {
        return new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Bank preparation source not found");
    }
}
