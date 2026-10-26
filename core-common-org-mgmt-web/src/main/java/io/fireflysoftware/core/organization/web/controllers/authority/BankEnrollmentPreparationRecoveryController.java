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

import io.fireflysoftware.core.organization.core.services.authority.BankEnrollmentPreparationRecoveryService;
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
import reactor.core.publisher.Mono;
import java.util.UUID;

@RestController
@ConditionalOnProperty(name="firefly.organization.enrollment.action-read.enabled",havingValue="true")
@RequestMapping("/api/v1/platform/tenants/{tenantId}/banks/{bankId}/enrollment-actions")
@Tag(name="Bank Enrollment Preparation Recovery")
public final class BankEnrollmentPreparationRecoveryController {
    private final BankEnrollmentPreparationRecoveryService service;
    public BankEnrollmentPreparationRecoveryController(BankEnrollmentPreparationRecoveryService service){this.service=service;}
    @DisableIdempotency
    @PostMapping(value="/{actionId}/recover-preparation",consumes=MediaType.APPLICATION_JSON_VALUE,produces=MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary="Recover the original preparation outcome and allocated revision.",description="Requires independently current action-read permission and the same original desired intent. Matches retained recorded time after the original request deadline. Returns immutable historical preparation data; current proposal state is read separately. A missing action is only not found.")
    public Mono<ResponseEntity<EnrollmentPreparedProposal>> recoverBankEnrollmentPreparation(@PathVariable UUID tenantId,@PathVariable UUID bankId,@PathVariable UUID actionId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required=true,content=@Content(mediaType=MediaType.APPLICATION_JSON_VALUE,schema=@Schema(implementation=BankEnrollmentPreparationRequest.class)))
            @RequestBody byte[] body) {
        return service.recover(tenantId,bankId,actionId,body).map(value->ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(value));
    }
}
