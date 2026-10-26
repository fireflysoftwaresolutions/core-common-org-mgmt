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

import io.fireflysoftware.core.organization.core.services.authority.BankEnrollmentActionReadService;
import io.fireflysoftware.core.organization.interfaces.authority.EnrollmentActionHistory;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import java.util.UUID;

@RestController
@ConditionalOnProperty(name="firefly.organization.enrollment.action-read.enabled",havingValue="true")
@RequestMapping("/api/v1/platform/tenants/{tenantId}/banks/{bankId}/enrollment-actions")
@Tag(name="Bank Enrollment Action History")
public final class BankEnrollmentActionController {
    private final BankEnrollmentActionReadService service;
    public BankEnrollmentActionController(BankEnrollmentActionReadService service){this.service=service;}
    @GetMapping(value="/{actionId}",produces=MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary="Read retained enrollment action and receipt",description="Requires current action-read authority. Returns immutable historical evidence; a missing action does not close execution.")
    public Mono<ResponseEntity<EnrollmentActionHistory>> getEnrollmentActionHistory(@PathVariable UUID tenantId,@PathVariable UUID bankId,@PathVariable UUID actionId) {
        return service.read(tenantId,bankId,actionId).map(value->ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(value));
    }
}
