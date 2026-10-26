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
import io.fireflysoftware.core.organization.core.services.authority.BankEnrollmentProposalApplicationService;
import io.fireflysoftware.core.organization.core.services.authority.BankEnrollmentProposalCommandService;
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
import java.util.Objects;
import java.util.UUID;

public final class BankEnrollmentProposalApplicationServiceImpl implements BankEnrollmentProposalApplicationService {
    private final OrganizationProposalAuthorization authorization;
    private final NativeEnrollmentContextRepository contexts;
    private final NativeEnrollmentProposalRepository proposals;
    private final NativeEnrollmentRevisionRepository revisions;
    private final NativeEnrollmentProposalControlRepository controls;
    private final BankEnrollmentProposalCommandService commands;
    private final TransactionalOperator transactions;
    public BankEnrollmentProposalApplicationServiceImpl(OrganizationProposalAuthorization authorization,NativeEnrollmentContextRepository contexts,
            NativeEnrollmentProposalRepository proposals,NativeEnrollmentRevisionRepository revisions,NativeEnrollmentProposalControlRepository controls,
            BankEnrollmentProposalCommandService commands,R2dbcEntityOperations operations,R2dbcTransactionManager manager) {
        this.authorization=Objects.requireNonNull(authorization);this.contexts=Objects.requireNonNull(contexts);this.proposals=Objects.requireNonNull(proposals);
        this.revisions=Objects.requireNonNull(revisions);this.controls=Objects.requireNonNull(controls);this.commands=Objects.requireNonNull(commands);
        if(manager.getConnectionFactory()!=operations.getDatabaseClient().getConnectionFactory())throw new IllegalStateException("Proposal discovery requires the repository ConnectionFactory transaction manager");
        transactions=TransactionalOperator.create(manager);
    }
    @Override public Mono<EnrollmentProposalCommandResult> command(EnrollmentProposalCommand.Operation operation,UUID tenant,UUID bank,UUID proposal,long revision,byte[] body) {
        return Mono.defer(()->authorization.discover(operation,tenant.toString(),bank.toString(),proposal.toString(),revision,body)
                .flatMap(discovery->Mono.defer(()->read(discovery)).as(transactions::transactional)
                        .flatMap(scope->authorization.promote(discovery,scope)))
                .flatMap(commands::execute));
    }
    private Mono<OrganizationProposalAuthorization.ProposalScope> read(OrganizationProposalAuthorization.DiscoveryAdmission admission) {
        UUID nonce=UUID.randomUUID();var packet=ProposalDiscoveryEvidenceCodec.context(admission,nonce);var scope=admission.scope();
        var key=new EnrollmentProposalKey(UUID.fromString(scope.tenantId()),UUID.fromString(scope.proposalId()));
        return contexts.beginProposalRead(nonce,ProposalTerminationEvidenceCodec.json(packet)).filter(nonce::equals)
                .switchIfEmpty(Mono.error(conflict())).flatMap(contexts::lockContext).switchIfEmpty(Mono.error(conflict())).flatMap(stamp->{
                    if(!nonce.equals(stamp.contextNonce()) || !packet.contextDigest().equals(stamp.contextDigest()) || stamp.dbNowMillis()<=0
                            || stamp.authorityUntilEpochMillis()<=stamp.dbNowMillis() || stamp.commandUntilEpochMillis()<=stamp.dbNowMillis()
                            || stamp.authorityUntilEpochMillis()>admission.validUntilEpochMillis() || stamp.commandUntilEpochMillis()>scope.request().request().commandNotAfterEpochMillis()
                            || stamp.authorityUntilEpochMillis()>stamp.dbNowMillis()+30000 || stamp.commandUntilEpochMillis()>stamp.dbNowMillis()+30000)throw conflict();
                    admission.requireCurrent();
                    return current(nonce).then(proposals.findById(key)).switchIfEmpty(Mono.error(missing())).flatMap(header->
                            revisions.findById(new EnrollmentProposalRevisionKey(key.tenantId(),key.proposalId(),scope.revision())).switchIfEmpty(Mono.error(missing())).flatMap(row->
                                    controls.findById(key).switchIfEmpty(Mono.error(missing())).flatMap(control->{
                                        OrganizationProposalAuthorization.ProposalScope result;
                                        try{result=RetainedEnrollmentProposal.scope(key.tenantId(),UUID.fromString(scope.bankId()),key.proposalId(),scope.revision(),header,row,control);}
                                        catch(IllegalArgumentException|NullPointerException error){throw conflict();}
                                        admission.requireCurrent();return current(nonce).thenReturn(result);
                                    })));
                });
    }
    private Mono<Boolean> current(UUID nonce){return contexts.contextCurrent(nonce).filter(Boolean.TRUE::equals).switchIfEmpty(Mono.error(conflict()));}
    private static ResponseStatusException conflict(){return new ResponseStatusException(HttpStatus.CONFLICT,"Retained proposal read changed or expired");}
    private static ResponseStatusException missing(){return new ResponseStatusException(HttpStatus.NOT_FOUND,"Retained proposal not found");}
}
