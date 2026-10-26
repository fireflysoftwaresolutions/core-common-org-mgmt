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
import io.fireflysoftware.core.organization.core.services.authority.BankEnrollmentPreparationCommandService;
import io.fireflysoftware.core.organization.interfaces.authority.*;
import io.fireflysoftware.core.organization.models.entities.authority.*;
import io.fireflysoftware.core.organization.models.repositories.authority.*;

import org.springframework.data.r2dbc.core.R2dbcEntityOperations;
import org.springframework.http.HttpStatus;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import org.springframework.transaction.reactive.TransactionalOperator;
import org.springframework.web.server.ResponseStatusException;

import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

public final class BankEnrollmentPreparationCommandServiceImpl
        implements BankEnrollmentPreparationCommandService {
    private final OrganizationPreparationAuthorization authorization;
    private final NativeEnrollmentPreparationContextRepository preparations;
    private final NativeEnrollmentContextRepository contexts;
    private final NativeEnrollmentActionRepository actions;
    private final R2dbcEntityOperations operations;
    private final TransactionalOperator transactions;

    public BankEnrollmentPreparationCommandServiceImpl(
            OrganizationPreparationAuthorization authorization,
            NativeEnrollmentPreparationContextRepository preparations,
            NativeEnrollmentContextRepository contexts,
            NativeEnrollmentActionRepository actions,
            R2dbcEntityOperations operations,
            R2dbcTransactionManager manager) {
        this.authorization = Objects.requireNonNull(authorization);
        this.preparations = Objects.requireNonNull(preparations);
        this.contexts = Objects.requireNonNull(contexts);
        this.actions = Objects.requireNonNull(actions);
        this.operations = Objects.requireNonNull(operations);
        if (manager.getConnectionFactory() != operations.getDatabaseClient().getConnectionFactory())
            throw new IllegalStateException(
                    "Preparation writer requires the repository ConnectionFactory transaction"
                        + " manager");
        transactions = TransactionalOperator.create(manager);
    }

    @Override
    public Mono<EnrollmentPreparedProposal> prepare(
            OrganizationPreparationAuthorization.Prepared admission) {
        return authorization
                .recheckPrepared(admission)
                .flatMap(
                        current ->
                                Mono.defer(() -> apply(current)).as(transactions::transactional));
    }

    private Mono<EnrollmentPreparedProposal> apply(
            OrganizationPreparationAuthorization.Prepared admission) {
        UUID nonce = UUID.randomUUID();
        var packet = PreparationWriteEvidenceCodec.context(admission, nonce);
        return preparations
                .beginPreparation(nonce, ProposalTerminationEvidenceCodec.json(packet))
                .filter(nonce::equals)
                .switchIfEmpty(Mono.error(conflict("Native preparation context unavailable")))
                .flatMap(contexts::lockContext)
                .switchIfEmpty(Mono.error(conflict("Native preparation lock unavailable")))
                .flatMap(
                        stamp -> {
                            validateStamp(admission, packet, stamp, nonce);
                            var desired = admission.plan().desired().request();
                            var key =
                                    new EnrollmentActionKey(
                                            UUID.fromString(desired.destinationTenantId()),
                                            UUID.fromString(desired.actionId()));
                            return current(nonce)
                                    .then(actions.findById(key))
                                    .flatMap(
                                            existing ->
                                                    Mono.<EnrollmentPreparedProposal>error(
                                                            conflict(
                                                                    "Action already resolved;"
                                                                        + " independently"
                                                                        + " authorized action read"
                                                                        + " required")))
                                    .switchIfEmpty(
                                            Mono.defer(() -> write(admission, stamp, nonce)));
                        });
    }

    private Mono<EnrollmentPreparedProposal> write(
            OrganizationPreparationAuthorization.Prepared admission,
            EnrollmentNativeContextStamp stamp,
            UUID nonce) {
        admission.requireCurrent();
        var plan = admission.plan();
        var wanted = plan.desired().request();
        var revision = plan.revision();
        var actor = admission.actor();
        long now = stamp.dbNowMillis();
        UUID tenant = UUID.fromString(wanted.destinationTenantId()),
                bank = UUID.fromString(wanted.bankId()),
                proposal = UUID.fromString(revision.request().proposalId()),
                actionId = UUID.fromString(wanted.actionId()),
                receiptId = UUID.randomUUID(),
                auditId = UUID.randomUUID(),
                eventId = UUID.randomUUID();
        var key = new EnrollmentProposalKey(tenant, proposal);
        var actionKey = new EnrollmentActionKey(tenant, actionId);
        var header =
                new EnrollmentProposalRow(
                        key,
                        bank,
                        UUID.fromString(wanted.institutionId()),
                        UUID.fromString(wanted.issuerAuthorityId()),
                        wanted.mode().name(),
                        UUID.fromString(plan.allocation().enrollmentId()),
                        actor.kind(),
                        actor.issuer(),
                        actor.subject(),
                        UUID.fromString(actor.tenantId()),
                        admission.staffRealm(),
                        UUID.fromString(actor.staffActorId()),
                        actor.clientId(),
                        actionId,
                        now);
        var full =
                new EnrollmentProposalRevisionRow(
                        new EnrollmentProposalRevisionKey(tenant, proposal, 1L),
                        wanted.mode().name(),
                        UUID.fromString(plan.allocation().executionActionId()),
                        plan.local().bankGeneration(),
                        plan.local().ownershipGeneration(),
                        plan.local().sourceGeneration(),
                        plan.local().previousOwner() == null
                                ? null
                                : plan.local().previousOwner().controlVersion(),
                        wanted.notAfterEpochMillis(),
                        wanted.allowedUses().stream().map(Enum::name).toList(),
                        revision.originalJson().getBytes(StandardCharsets.UTF_8),
                        ProposalTerminationEvidenceCodec.REVISION_ENCODING,
                        revision.canonicalIntent(),
                        revision.intentDigest(),
                        now);
        var control = new EnrollmentProposalControlRow(key, "DRAFT", 1, 0, null, null, actionId);
        var result =
                new EnrollmentProposalCommandResult(
                        2,
                        wanted.actionId(),
                        wanted.destinationTenantId(),
                        wanted.bankId(),
                        proposal.toString(),
                        1,
                        PreparationWriteEvidenceCodec.operation(admission),
                        "DRAFT",
                        0,
                        revision.intentDigest(),
                        receiptId.toString(),
                        auditId.toString(),
                        eventId.toString(),
                        now);
        var encoded = ProposalTerminationEvidenceCodec.result(result);
        var event = PreparationWriteEvidenceCodec.event(admission, result);
        var command = PreparationWriteEvidenceCodec.command(admission);
        byte[] request = command.originalJson().getBytes(StandardCharsets.UTF_8);
        var action =
                new EnrollmentActionRow(
                        actionKey,
                        bank,
                        result.operation(),
                        PreparationWriteEvidenceCodec.route(admission),
                        request,
                        command.encoding(),
                        command.canonical(),
                        command.digest(),
                        "COMMAND",
                        request,
                        command.encoding(),
                        command.canonical(),
                        command.digest(),
                        actor.kind(),
                        actor.issuer(),
                        actor.subject(),
                        UUID.fromString(actor.tenantId()),
                        admission.staffRealm(),
                        UUID.fromString(actor.staffActorId()),
                        actor.clientId(),
                        proposal,
                        1L,
                        revision.intentDigest(),
                        "APPLIED",
                        now);
        var receipt =
                new EnrollmentReceiptRow(
                        actionKey,
                        receiptId,
                        encoded.originalJson().getBytes(StandardCharsets.UTF_8),
                        encoded.encoding(),
                        encoded.canonical(),
                        encoded.digest(),
                        now);
        var audit =
                new EnrollmentAuditRow(
                        actionKey,
                        auditId,
                        bank,
                        event.originalJson().getBytes(StandardCharsets.UTF_8),
                        event.encoding(),
                        event.canonical(),
                        event.digest(),
                        now);
        var outbox =
                new EnrollmentOutboxRow(
                        actionKey,
                        eventId,
                        bank,
                        event.originalJson().getBytes(StandardCharsets.UTF_8),
                        event.encoding(),
                        event.canonical(),
                        event.digest(),
                        now);
        var delivery =
                new EnrollmentOutboxDeliveryRow(
                        new EnrollmentOutboxEventKey(tenant, eventId),
                        0,
                        "PENDING",
                        0,
                        null,
                        null,
                        now,
                        null,
                        null);
        return Mono.defer(() -> operations.insert(header))
                .then(Mono.defer(() -> operations.insert(full)))
                .then(Mono.defer(() -> operations.insert(control)))
                .then(Mono.defer(() -> operations.insert(action)))
                .then(Mono.defer(() -> operations.insert(receipt)))
                .then(Mono.defer(() -> operations.insert(audit)))
                .then(Mono.defer(() -> operations.insert(outbox)))
                .then(Mono.defer(() -> operations.insert(delivery)))
                .then(current(nonce))
                .then(
                        contexts.validateComplete(nonce)
                                .filter(Boolean.TRUE::equals)
                                .switchIfEmpty(
                                        Mono.error(conflict("Native prepared graph incomplete"))))
                .thenReturn(
                        new EnrollmentPreparedProposal(
                                2,
                                result,
                                new EnrollmentActionHistory.Payload(
                                        ProposalTerminationEvidenceCodec.REVISION_ENCODING,
                                        revision.originalJson(),
                                        revision.canonicalIntent(),
                                        revision.intentDigest())));
    }

    private static void validateStamp(
            OrganizationPreparationAuthorization.Prepared admission,
            PreparationWriteEvidenceCodec.Packet packet,
            EnrollmentNativeContextStamp stamp,
            UUID nonce) {
        if (!nonce.equals(stamp.contextNonce())
                || !packet.contextDigest().equals(stamp.contextDigest())
                || stamp.dbNowMillis() <= 0
                || stamp.authorityUntilEpochMillis() <= stamp.dbNowMillis()
                || stamp.commandUntilEpochMillis() <= stamp.dbNowMillis()
                || stamp.authorityUntilEpochMillis() > admission.validUntilEpochMillis()
                || stamp.commandUntilEpochMillis()
                        > admission.plan().desired().request().notAfterEpochMillis()
                || stamp.authorityUntilEpochMillis() > stamp.dbNowMillis() + 30000
                || stamp.commandUntilEpochMillis() > stamp.dbNowMillis() + 30000)
            throw conflict("Native preparation context mismatch");
        try {
            admission.requireCurrent();
            BankEnrollmentPreparationPlan.build(
                    admission.plan().desired(),
                    admission.plan().local(),
                    admission.plan().allocation(),
                    admission.plan().observations(),
                    stamp.dbNowMillis());
        } catch (IllegalStateException | IllegalArgumentException error) {
            throw conflict("Preparation observations expired after wait");
        }
    }

    private Mono<Boolean> current(UUID nonce) {
        return contexts.contextCurrent(nonce)
                .filter(Boolean.TRUE::equals)
                .switchIfEmpty(Mono.error(conflict("Native preparation context expired")));
    }

    private static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }
}
