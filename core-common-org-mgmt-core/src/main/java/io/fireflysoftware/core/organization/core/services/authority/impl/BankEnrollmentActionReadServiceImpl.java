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

import io.fireflysoftware.core.organization.core.authority.EnrollmentActionHistoryCodec;
import io.fireflysoftware.core.organization.core.authority.OrganizationEnrollmentHistoryAuthorization;
import io.fireflysoftware.core.organization.core.services.authority.BankEnrollmentActionReadService;
import io.fireflysoftware.core.organization.interfaces.authority.EnrollmentActionHistory;
import io.fireflysoftware.core.organization.models.entities.authority.*;
import io.fireflysoftware.core.organization.models.repositories.authority.*;
import org.springframework.data.r2dbc.core.R2dbcEntityOperations;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import org.springframework.transaction.reactive.TransactionalOperator;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import java.util.Objects;
import java.util.UUID;

public final class BankEnrollmentActionReadServiceImpl implements BankEnrollmentActionReadService {
    private final OrganizationEnrollmentHistoryAuthorization authorization;
    private final NativeEnrollmentContextRepository contexts;
    private final NativeEnrollmentActionRepository actions;
    private final NativeEnrollmentReceiptRepository receipts;
    private final NativeEnrollmentRevisionRepository revisions;
    private final TransactionalOperator transactions;
    public BankEnrollmentActionReadServiceImpl(OrganizationEnrollmentHistoryAuthorization authorization,NativeEnrollmentContextRepository contexts,
            NativeEnrollmentActionRepository actions,NativeEnrollmentReceiptRepository receipts,NativeEnrollmentRevisionRepository revisions,
            R2dbcEntityOperations operations,R2dbcTransactionManager manager) {
        this.authorization=Objects.requireNonNull(authorization);this.contexts=Objects.requireNonNull(contexts);this.actions=Objects.requireNonNull(actions);
        this.receipts=Objects.requireNonNull(receipts);this.revisions=Objects.requireNonNull(revisions);
        if(manager.getConnectionFactory()!=operations.getDatabaseClient().getConnectionFactory())throw new IllegalStateException("Action read requires the repository ConnectionFactory transaction manager");
        this.transactions=TransactionalOperator.create(manager);
    }
    @Override public Mono<EnrollmentActionHistory> read(UUID tenantId,UUID bankId,UUID actionId) {
        return authorization.admit(text(tenantId),text(bankId),text(actionId)).flatMap(admission->
                Mono.defer(()->retained(admission)).as(transactions::transactional)
                        .flatMap(result->authorization.recheck(admission).thenReturn(result)));
    }
    private Mono<EnrollmentActionHistory> retained(OrganizationEnrollmentHistoryAuthorization.Admission admission) {
        UUID nonce=UUID.randomUUID();var packet=EnrollmentActionHistoryCodec.context(admission,nonce);var scope=admission.scope();
        var key=new EnrollmentActionKey(UUID.fromString(scope.tenantId()),UUID.fromString(scope.actionId()));
        return contexts.beginRead(nonce,io.fireflysoftware.core.organization.core.authority.ProposalTerminationEvidenceCodec.json(packet))
                .filter(nonce::equals).switchIfEmpty(Mono.error(conflict())).flatMap(contexts::lockContext).switchIfEmpty(Mono.error(conflict()))
                .flatMap(stamp->{validateStamp(admission,packet,stamp,nonce);return currentContext(nonce).then(actions.findById(key))
                        .switchIfEmpty(Mono.error(missing())).flatMap(action->{
                            if(!action.id().equals(key) || !action.bankId().toString().equals(scope.bankId()))return Mono.error(missing());
                            return receipts.findById(key).switchIfEmpty(Mono.error(conflict())).flatMap(receipt->{
                                if(action.proposalId()==null && action.proposalRevision()==null && action.targetDigest()==null)
                                    return Mono.fromCallable(()->EnrollmentActionHistoryCodec.history(action,receipt,null));
                                if(action.proposalId()==null || action.proposalRevision()==null || action.targetDigest()==null)return Mono.error(conflict());
                                return revisions.findById(new EnrollmentProposalRevisionKey(key.tenantId(),action.proposalId(),action.proposalRevision()))
                                        .switchIfEmpty(Mono.error(conflict())).map(revision->EnrollmentActionHistoryCodec.history(action,receipt,revision));
                            });
                        }).flatMap(result->currentContext(nonce).thenReturn(result));
                }).onErrorMap(error->error instanceof IllegalArgumentException?conflict():error);
    }
    private Mono<Boolean> currentContext(UUID nonce){return contexts.contextCurrent(nonce).filter(Boolean.TRUE::equals).switchIfEmpty(Mono.error(conflict()));}
    private static void validateStamp(OrganizationEnrollmentHistoryAuthorization.Admission admission,EnrollmentActionHistoryCodec.ContextPacket packet,
            EnrollmentNativeContextStamp stamp,UUID nonce) {
        if(!nonce.equals(stamp.contextNonce()) || !packet.contextDigest().equals(stamp.contextDigest()) || stamp.dbNowMillis()<=0
                || stamp.authorityUntilEpochMillis()<=stamp.dbNowMillis() || stamp.commandUntilEpochMillis()<=stamp.dbNowMillis()
                || stamp.authorityUntilEpochMillis()>admission.validUntilEpochMillis() || stamp.commandUntilEpochMillis()>admission.validUntilEpochMillis()
                || stamp.authorityUntilEpochMillis()>stamp.dbNowMillis()+30000 || stamp.commandUntilEpochMillis()>stamp.dbNowMillis()+30000)throw conflict();
    }
    private static String text(UUID id){return id==null?null:id.toString();}
    private static ResponseStatusException conflict(){return new ResponseStatusException(HttpStatus.CONFLICT,"Retained enrollment action evidence unavailable");}
    private static ResponseStatusException missing(){return new ResponseStatusException(HttpStatus.NOT_FOUND,"Enrollment action not found");}
}
