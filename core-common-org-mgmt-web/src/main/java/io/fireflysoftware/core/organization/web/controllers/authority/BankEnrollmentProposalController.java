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
package io.fireflysoftware.core.organization.web.controllers.authority;

import io.fireflysoftware.core.organization.core.authority.EnrollmentProposalCommandCodec;
import io.fireflysoftware.core.organization.core.services.authority.BankEnrollmentProposalApplicationService;
import io.fireflysoftware.core.organization.interfaces.authority.EnrollmentProposalCommand;
import io.fireflysoftware.core.organization.interfaces.authority.EnrollmentProposalCommandResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.fireflyframework.web.idempotency.annotation.DisableIdempotency;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import java.util.UUID;

@RestController
@ConditionalOnProperty(name="firefly.organization.enrollment.proposal-commands.enabled",havingValue="true")
@RequestMapping("/api/v1/platform/tenants/{tenantId}/banks/{bankId}/enrollment-proposals")
@Tag(name="Bank Enrollment Proposal Commands")
public final class BankEnrollmentProposalController {
    private final BankEnrollmentProposalApplicationService service;
    public BankEnrollmentProposalController(BankEnrollmentProposalApplicationService service){this.service=service;}
    @DisableIdempotency
    @PostMapping(value="/{proposalId}/submit",consumes=MediaType.APPLICATION_JSON_VALUE,produces=MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary="Submit the exact retained proposal with current maker permission and fresh configuration observations.",description="Reads require independent current action-read permission. An existing action or missing row never implies no effect.")
    public Mono<ResponseEntity<EnrollmentProposalCommandResult>> submitBankEnrollmentProposal(@PathVariable UUID tenantId,@PathVariable UUID bankId,@PathVariable UUID proposalId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required=true,content=@Content(mediaType=MediaType.APPLICATION_JSON_VALUE,schema=@Schema(implementation=EnrollmentProposalCommand.class)))
            @RequestBody byte[] body) {return command(EnrollmentProposalCommand.Operation.SUBMIT,tenantId,bankId,proposalId,body);}
    @DisableIdempotency
    @PostMapping(value="/{proposalId}/approve",consumes=MediaType.APPLICATION_JSON_VALUE,produces=MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary="Record a distinct current staff checker decision over the exact revision and fixed expiry.",description="Reads require independent current action-read permission. An existing action or missing row never implies no effect.")
    public Mono<ResponseEntity<EnrollmentProposalCommandResult>> approveBankEnrollmentProposal(@PathVariable UUID tenantId,@PathVariable UUID bankId,@PathVariable UUID proposalId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required=true,content=@Content(mediaType=MediaType.APPLICATION_JSON_VALUE,schema=@Schema(implementation=EnrollmentProposalCommand.class)))
            @RequestBody byte[] body) {return command(EnrollmentProposalCommand.Operation.APPROVE,tenantId,bankId,proposalId,body);}
    @DisableIdempotency
    @PostMapping(value="/{proposalId}/reject",consumes=MediaType.APPLICATION_JSON_VALUE,produces=MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary="Reject the retained submitted proposal with current independent checker permission.",description="Reads require independent current action-read permission. An existing action or missing row never implies no effect.")
    public Mono<ResponseEntity<EnrollmentProposalCommandResult>> rejectBankEnrollmentProposal(@PathVariable UUID tenantId,@PathVariable UUID bankId,@PathVariable UUID proposalId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required=true,content=@Content(mediaType=MediaType.APPLICATION_JSON_VALUE,schema=@Schema(implementation=EnrollmentProposalCommand.class)))
            @RequestBody byte[] body) {return command(EnrollmentProposalCommand.Operation.REJECT,tenantId,bankId,proposalId,body);}
    @DisableIdempotency
    @PostMapping(value="/{proposalId}/withdraw",consumes=MediaType.APPLICATION_JSON_VALUE,produces=MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary="Withdraw the retained approval with current maker permission.",description="Reads require independent current action-read permission. An existing action or missing row never implies no effect.")
    public Mono<ResponseEntity<EnrollmentProposalCommandResult>> withdrawBankEnrollmentProposal(@PathVariable UUID tenantId,@PathVariable UUID bankId,@PathVariable UUID proposalId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required=true,content=@Content(mediaType=MediaType.APPLICATION_JSON_VALUE,schema=@Schema(implementation=EnrollmentProposalCommand.class)))
            @RequestBody byte[] body) {return command(EnrollmentProposalCommand.Operation.WITHDRAW,tenantId,bankId,proposalId,body);}
    @DisableIdempotency
    @PostMapping(value="/{proposalId}/cancel",consumes=MediaType.APPLICATION_JSON_VALUE,produces=MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary="Cancel the retained proposal with current maker permission.",description="Reads require independent current action-read permission. An existing action or missing row never implies no effect.")
    public Mono<ResponseEntity<EnrollmentProposalCommandResult>> cancelBankEnrollmentProposal(@PathVariable UUID tenantId,@PathVariable UUID bankId,@PathVariable UUID proposalId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required=true,content=@Content(mediaType=MediaType.APPLICATION_JSON_VALUE,schema=@Schema(implementation=EnrollmentProposalCommand.class)))
            @RequestBody byte[] body) {return command(EnrollmentProposalCommand.Operation.CANCEL,tenantId,bankId,proposalId,body);}
    @DisableIdempotency
    @PostMapping(value="/{proposalId}/emergency-cancel",consumes=MediaType.APPLICATION_JSON_VALUE,produces=MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary="Cancel the retained proposal with exact current emergency permission as the actual staff or machine caller.",description="Reads require independent current action-read permission. An existing action or missing row never implies no effect.")
    public Mono<ResponseEntity<EnrollmentProposalCommandResult>> emergencyCancelBankEnrollmentProposal(@PathVariable UUID tenantId,@PathVariable UUID bankId,@PathVariable UUID proposalId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required=true,content=@Content(mediaType=MediaType.APPLICATION_JSON_VALUE,schema=@Schema(implementation=EnrollmentProposalCommand.class)))
            @RequestBody byte[] body) {return command(EnrollmentProposalCommand.Operation.CANCEL_EMERGENCY,tenantId,bankId,proposalId,body);}
    private Mono<ResponseEntity<EnrollmentProposalCommandResult>> command(EnrollmentProposalCommand.Operation operation,UUID tenant,UUID bank,UUID proposal,byte[] body) {
        return Mono.defer(()->{
            long revision;
            try{revision=EnrollmentProposalCommandCodec.requestedRevision(body);}catch(IllegalArgumentException error){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Closed enrollment proposal command required");}
            return service.command(operation,tenant,bank,proposal,revision,body).map(value->ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(value));
        });
    }
}
