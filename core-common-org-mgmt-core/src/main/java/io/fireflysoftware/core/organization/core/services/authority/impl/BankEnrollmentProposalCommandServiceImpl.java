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
import io.fireflysoftware.core.organization.core.services.authority.BankEnrollmentProposalCommandService;
import io.fireflysoftware.core.organization.core.services.authority.BankEnrollmentProposalObservationService;
import io.fireflysoftware.core.organization.interfaces.authority.EnrollmentProposalCommand;
import io.fireflysoftware.core.organization.interfaces.authority.EnrollmentProposalCommandResult;
import io.fireflysoftware.core.organization.models.entities.authority.*;
import io.fireflysoftware.core.organization.models.repositories.authority.*;
import org.springframework.data.r2dbc.core.R2dbcEntityOperations;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import org.springframework.transaction.reactive.TransactionalOperator;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Set;
import java.util.List;
import java.util.UUID;

public final class BankEnrollmentProposalCommandServiceImpl implements BankEnrollmentProposalCommandService {
    private final OrganizationProposalAuthorization authorization;
    private final BankEnrollmentProposalObservationService observer;
    private final NativeEnrollmentContextRepository contexts;
    private final NativeEnrollmentProposalRepository proposals;
    private final NativeEnrollmentRevisionRepository revisions;
    private final NativeEnrollmentProposalControlRepository controls;
    private final NativeEnrollmentActionRepository actions;
    private final R2dbcEntityOperations operations;
    private final TransactionalOperator transactions;
    public BankEnrollmentProposalCommandServiceImpl(OrganizationProposalAuthorization authorization,
            BankEnrollmentProposalObservationService observer,
            NativeEnrollmentContextRepository contexts,NativeEnrollmentProposalRepository proposals,
            NativeEnrollmentRevisionRepository revisions,NativeEnrollmentProposalControlRepository controls,
            NativeEnrollmentActionRepository actions,R2dbcEntityOperations operations,R2dbcTransactionManager manager) {
        this.authorization=Objects.requireNonNull(authorization);this.observer=Objects.requireNonNull(observer);this.contexts=Objects.requireNonNull(contexts);
        this.proposals=Objects.requireNonNull(proposals);this.revisions=Objects.requireNonNull(revisions);
        this.controls=Objects.requireNonNull(controls);this.actions=Objects.requireNonNull(actions);this.operations=Objects.requireNonNull(operations);
        if(manager.getConnectionFactory()!=operations.getDatabaseClient().getConnectionFactory())
            throw new IllegalStateException("Proposal service requires the repository ConnectionFactory transaction manager");
        this.transactions=TransactionalOperator.create(manager);
    }
    @Override public Mono<EnrollmentProposalCommandResult> execute(OrganizationProposalAuthorization.Admission admission) {
        return Mono.defer(()->{
            if(admission==null)return Mono.error(conflict("Proposal command admission required"));
            return authorization.recheck(admission).flatMap(current->{
                boolean observe=Set.of(EnrollmentProposalCommand.Operation.SUBMIT,EnrollmentProposalCommand.Operation.APPROVE).contains(current.command().request().operation());
                Mono<List<ObservedInstitutionGrantEvidence>> evidence=observe?observer.observeForProposal(current):Mono.just(List.of());
                return evidence.switchIfEmpty(Mono.error(conflict("Current proposal observations required"))).flatMap(observations->{
                    var snapshot=List.copyOf(observations);
                    if(observe)validateObservations(current,snapshot,current.currentEpochMillis());
                    else if(!snapshot.isEmpty())throw conflict("Unexpected termination observations");
                    return authorization.recheck(current).flatMap(checked->Mono.defer(()->apply(checked,snapshot)).as(transactions::transactional));
                });
            });
        });
    }
    private static void validateObservations(OrganizationProposalAuthorization.Admission admission,List<ObservedInstitutionGrantEvidence> evidence,long now) {
        try {
            evidence.forEach(e->e.requireCurrentAt(now));
            BankEnrollmentRevisionCodec.requireObservationSet(admission.scope().target(),evidence.stream().map(ObservedInstitutionGrantEvidence::observation).toList(),now);
        }catch(IllegalArgumentException|IllegalStateException error){throw conflict("Current exact proposal observations required");}
    }
    private Mono<EnrollmentProposalCommandResult> apply(OrganizationProposalAuthorization.Admission admission,List<ObservedInstitutionGrantEvidence> observations) {
        UUID nonce=UUID.randomUUID();var packet=ProposalTerminationEvidenceCodec.context(admission,nonce);
        var observed=observations.isEmpty()?null:ProposalTerminationEvidenceCodec.observedContext(admission,nonce,observations);
        String encoded=ProposalTerminationEvidenceCodec.json(observed==null?packet:observed);
        String digest=observed==null?packet.contextDigest():observed.contextDigest();
        return contexts.beginMutation(nonce,encoded).filter(nonce::equals)
                .switchIfEmpty(Mono.error(conflict("Native proposal context unavailable"))).flatMap(contexts::lockContext)
                .switchIfEmpty(Mono.error(conflict("Native proposal lock context unavailable"))).flatMap(stamp->{
                    validateStamp(admission,digest,stamp,nonce);admission.requireCurrent();
                    if(!observations.isEmpty()) {
                        validateObservations(admission,observations,admission.currentEpochMillis());validateObservations(admission,observations,stamp.dbNowMillis());
                        if(stamp.authorityUntilEpochMillis()>observed.observationsUntilEpochMillis())throw conflict("Native context exceeds observation horizon");
                    }
                    return currentContext(nonce).then(actions.findById(actionKey(admission)))
                            .flatMap(existing->Mono.<EnrollmentProposalCommandResult>error(conflict("Action already resolved; independently authorized action read required")))
                            .switchIfEmpty(Mono.defer(()->loadAndApply(admission,stamp,nonce,observations)));
                });
    }
    private Mono<EnrollmentProposalCommandResult> loadAndApply(OrganizationProposalAuthorization.Admission admission,
            EnrollmentNativeContextStamp stamp,UUID nonce,List<ObservedInstitutionGrantEvidence> observations) {
        var scope=admission.scope();var key=new EnrollmentProposalKey(UUID.fromString(scope.tenantId()),UUID.fromString(scope.proposalId()));
        return proposals.findById(key).switchIfEmpty(Mono.error(missing())).flatMap(header->
                revisions.findById(new EnrollmentProposalRevisionKey(key.tenantId(),key.proposalId(),scope.revision()))
                        .switchIfEmpty(Mono.error(missing())).flatMap(revision->controls.findById(key)
                                .switchIfEmpty(Mono.error(missing())).flatMap(control->{
                                    validateRetained(admission,header,revision,control);
                                    return write(admission,control,stamp,nonce,observations);
                                })));
    }
    private Mono<EnrollmentProposalCommandResult> write(OrganizationProposalAuthorization.Admission admission,
            EnrollmentProposalControlRow before,EnrollmentNativeContextStamp stamp,UUID nonce,List<ObservedInstitutionGrantEvidence> observations) {
        var scope=admission.scope();var command=admission.command().request();var op=command.operation();
        var event=switch(op){case SUBMIT->BankEnrollmentLifecycle.ProposalEvent.SUBMIT;case APPROVE->BankEnrollmentLifecycle.ProposalEvent.APPROVE;case REJECT->BankEnrollmentLifecycle.ProposalEvent.REJECT;case WITHDRAW->BankEnrollmentLifecycle.ProposalEvent.WITHDRAW;case CANCEL,CANCEL_EMERGENCY->BankEnrollmentLifecycle.ProposalEvent.CANCEL;};
        var next=BankEnrollmentLifecycle.transition(new BankEnrollmentLifecycle.ProposalSnapshot(scope.state(),scope.revision(),scope.controlVersion(),scope.target().intentDigest()),event,command.expectedRevision(),command.expectedControlVersion(),command.expectedTargetDigest());
        UUID actionId=UUID.fromString(command.actionId()),receiptId=UUID.randomUUID(),auditId=UUID.randomUUID(),eventId=UUID.randomUUID();
        long now=stamp.dbNowMillis();var after=new EnrollmentProposalControlRow(before.id(),next.state().name(),next.revision(),next.controlVersion(),op==EnrollmentProposalCommand.Operation.APPROVE?actionId:null,op==EnrollmentProposalCommand.Operation.APPROVE?command.approvalUntilEpochMillis():null,actionId);
        var result=new EnrollmentProposalCommandResult(2,command.actionId(),scope.tenantId(),scope.bankId(),scope.proposalId(),scope.revision(),op.name(),next.state().name(),next.controlVersion(),scope.target().intentDigest(),receiptId.toString(),auditId.toString(),eventId.toString(),now);
        var encoded=ProposalTerminationEvidenceCodec.result(result);var evidence=observations.isEmpty()?ProposalTerminationEvidenceCodec.event(admission,result):ProposalTerminationEvidenceCodec.observedEvent(admission,result,observations);var actor=admission.actor();
        UUID tenant=UUID.fromString(scope.tenantId()),bank=UUID.fromString(scope.bankId()),proposal=UUID.fromString(scope.proposalId());var actionKey=new EnrollmentActionKey(tenant,actionId);
        byte[] request=admission.command().originalJson().getBytes(StandardCharsets.UTF_8);
        var action=new EnrollmentActionRow(actionKey,bank,op.name(),ProposalTerminationEvidenceCodec.route(admission),request,ProposalTerminationEvidenceCodec.COMMAND_ENCODING,admission.command().canonicalIntent(),admission.command().intentDigest(),"COMMAND",request,ProposalTerminationEvidenceCodec.COMMAND_ENCODING,admission.command().canonicalIntent(),admission.command().intentDigest(),actor.kind(),actor.issuer(),actor.subject(),UUID.fromString(actor.tenantId()),admission.staffRealm(),uuid(actor.staffActorId()),actor.clientId(),proposal,scope.revision(),scope.target().intentDigest(),"APPLIED",now);
        var receipt=new EnrollmentReceiptRow(actionKey,receiptId,encoded.originalJson().getBytes(StandardCharsets.UTF_8),encoded.encoding(),encoded.canonical(),encoded.digest(),now);
        var audit=new EnrollmentAuditRow(actionKey,auditId,bank,evidence.originalJson().getBytes(StandardCharsets.UTF_8),evidence.encoding(),evidence.canonical(),evidence.digest(),now);
        var outbox=new EnrollmentOutboxRow(actionKey,eventId,bank,evidence.originalJson().getBytes(StandardCharsets.UTF_8),evidence.encoding(),evidence.canonical(),evidence.digest(),now);
        var delivery=new EnrollmentOutboxDeliveryRow(new EnrollmentOutboxEventKey(tenant,eventId),0,"PENDING",0,null,null,now,null,null);
        Mono<Void> decision=Mono.empty();
        if(op==EnrollmentProposalCommand.Operation.APPROVE || op==EnrollmentProposalCommand.Operation.REJECT || op==EnrollmentProposalCommand.Operation.WITHDRAW) {
            var row=new EnrollmentDecisionRow(actionKey,proposal,scope.revision(),scope.target().intentDigest(),op.name(),op==EnrollmentProposalCommand.Operation.APPROVE?command.approvalUntilEpochMillis():null,
                    actor.kind(),actor.issuer(),actor.subject(),UUID.fromString(actor.tenantId()),admission.staffRealm(),uuid(actor.staffActorId()),actor.clientId(),command.reason(),now);
            decision=Mono.defer(()->operations.insert(row)).then();
        }
        return controls.compareAndSet(before,after).filter(count->count==1).switchIfEmpty(Mono.error(conflict("Proposal control changed")))
                .then(decision).then(Mono.defer(()->operations.insert(action))).then(Mono.defer(()->operations.insert(receipt)))
                .then(Mono.defer(()->operations.insert(audit))).then(Mono.defer(()->operations.insert(outbox)))
                .then(Mono.defer(()->operations.insert(delivery))).then(currentContext(nonce))
                .then(contexts.validateComplete(nonce).filter(Boolean.TRUE::equals).switchIfEmpty(Mono.error(conflict("Native proposal graph incomplete"))))
                .thenReturn(result);
    }
    private void validateRetained(OrganizationProposalAuthorization.Admission admission,EnrollmentProposalRow header,
            EnrollmentProposalRevisionRow row,EnrollmentProposalControlRow control) {
        var scope=admission.scope();
        try {
            var actual=RetainedEnrollmentProposal.scope(UUID.fromString(scope.tenantId()),UUID.fromString(scope.bankId()),UUID.fromString(scope.proposalId()),scope.revision(),header,row,control);
            if(!actual.equals(scope))throw conflict("Retained proposal changed");
        }catch(IllegalArgumentException|NullPointerException error){throw conflict("Retained proposal changed");}
    }
    private Mono<Boolean> currentContext(UUID nonce) {
        return contexts.contextCurrent(nonce).filter(Boolean.TRUE::equals).switchIfEmpty(Mono.error(conflict("Native proposal context expired")));
    }
    private static void validateStamp(OrganizationProposalAuthorization.Admission admission,String contextDigest,
            EnrollmentNativeContextStamp stamp,UUID nonce) {
        if(!nonce.equals(stamp.contextNonce()) || !contextDigest.equals(stamp.contextDigest()) || stamp.dbNowMillis()<=0
                || stamp.authorityUntilEpochMillis()<=stamp.dbNowMillis() || stamp.commandUntilEpochMillis()<=stamp.dbNowMillis()
                || stamp.authorityUntilEpochMillis()>admission.validUntilEpochMillis() || stamp.commandUntilEpochMillis()>admission.command().request().commandNotAfterEpochMillis()
                || stamp.authorityUntilEpochMillis()>stamp.dbNowMillis()+30000 || stamp.commandUntilEpochMillis()>stamp.dbNowMillis()+30000)
            throw conflict("Native proposal context mismatch");
    }
    private static EnrollmentActionKey actionKey(OrganizationProposalAuthorization.Admission admission){return new EnrollmentActionKey(UUID.fromString(admission.scope().tenantId()),UUID.fromString(admission.command().request().actionId()));}
    private static UUID uuid(String id){return id==null?null:UUID.fromString(id);}
    private static ResponseStatusException conflict(String reason){return new ResponseStatusException(HttpStatus.CONFLICT,reason);}
    private static ResponseStatusException missing(){return new ResponseStatusException(HttpStatus.NOT_FOUND,"Enrollment proposal not available");}
}
