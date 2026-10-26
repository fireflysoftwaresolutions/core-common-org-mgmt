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
import io.fireflysoftware.core.organization.core.services.authority.BankEnrollmentProposalTerminationService;
import io.fireflysoftware.core.organization.interfaces.authority.EnrollmentProposalCommand;
import io.fireflysoftware.core.organization.interfaces.authority.EnrollmentProposalCommandResult;
import io.fireflysoftware.core.organization.models.entities.authority.*;
import io.fireflysoftware.core.organization.models.repositories.authority.*;
import org.springframework.data.r2dbc.core.R2dbcEntityOperations;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import java.util.Set;

public final class BankEnrollmentProposalTerminationServiceImpl implements BankEnrollmentProposalTerminationService {
    private final BankEnrollmentProposalCommandServiceImpl commands;
    public BankEnrollmentProposalTerminationServiceImpl(OrganizationProposalAuthorization authorization,
            NativeEnrollmentContextRepository contexts,NativeEnrollmentProposalRepository proposals,
            NativeEnrollmentRevisionRepository revisions,NativeEnrollmentProposalControlRepository controls,
            NativeEnrollmentActionRepository actions,R2dbcEntityOperations operations,R2dbcTransactionManager manager) {
        this.commands=new BankEnrollmentProposalCommandServiceImpl(authorization,
                admission->Mono.error(conflict()),contexts,proposals,revisions,controls,actions,operations,manager);
    }
    @Override public Mono<EnrollmentProposalCommandResult> terminate(OrganizationProposalAuthorization.Admission admission) {
        return Mono.defer(()->{
            if(admission==null || !Set.of(EnrollmentProposalCommand.Operation.REJECT,EnrollmentProposalCommand.Operation.WITHDRAW,
                    EnrollmentProposalCommand.Operation.CANCEL,EnrollmentProposalCommand.Operation.CANCEL_EMERGENCY).contains(admission.command().request().operation()))return Mono.error(conflict());
            return commands.execute(admission);
        });
    }
    private static ResponseStatusException conflict(){return new ResponseStatusException(HttpStatus.CONFLICT,"Proposal termination admission required");}
}
