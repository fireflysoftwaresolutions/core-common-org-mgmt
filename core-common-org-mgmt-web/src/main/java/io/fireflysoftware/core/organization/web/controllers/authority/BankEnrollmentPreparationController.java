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

import io.fireflysoftware.core.organization.core.authority.BankEnrollmentPreparationCodec;
import io.fireflysoftware.core.organization.core.services.authority.BankEnrollmentPreparationService;
import io.fireflysoftware.core.organization.interfaces.authority.BankEnrollmentPreparationRequest;
import io.fireflysoftware.core.organization.interfaces.authority.EnrollmentPreparedProposal;
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
@Tag(name="Bank Enrollment Proposal Preparation")
public final class BankEnrollmentPreparationController {
    private final BankEnrollmentPreparationService service;
    public BankEnrollmentPreparationController(BankEnrollmentPreparationService service){this.service=service;}
    @DisableIdempotency
    @PostMapping(consumes=MediaType.APPLICATION_JSON_VALUE,produces=MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary="Prepare a DRAFT Bank enrollment proposal.",description="Uses current STAFF preparation permission and current configuration observations. Mode TRANSFER requires its separate transfer-preparation permission. Returns the allocated revision and original preparation outcome; retained recovery requires independent current action-read permission.")
    public Mono<ResponseEntity<EnrollmentPreparedProposal>> prepareBankEnrollmentProposal(@PathVariable UUID tenantId,@PathVariable UUID bankId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required=true,content=@Content(mediaType=MediaType.APPLICATION_JSON_VALUE,schema=@Schema(implementation=BankEnrollmentPreparationRequest.class)))
            @RequestBody byte[] body) {
        return Mono.defer(()->{
            BankEnrollmentPreparationRequest.Mode mode;
            try{mode=BankEnrollmentPreparationCodec.requestedMode(body);}catch(IllegalArgumentException error){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Closed enrollment preparation request required");}
            return service.prepare(mode,tenantId,bankId,body).map(value->ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(value));
        });
    }
}
