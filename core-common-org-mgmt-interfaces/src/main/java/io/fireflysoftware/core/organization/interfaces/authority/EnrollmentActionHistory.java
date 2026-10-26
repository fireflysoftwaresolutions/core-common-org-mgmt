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
package io.fireflysoftware.core.organization.interfaces.authority;

@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.ALWAYS)
@io.swagger.v3.oas.annotations.media.Schema(name="EnrollmentActionHistory")
public record EnrollmentActionHistory(int schemaVersion,String tenantId,String bankId,String actionId,
        String outcome,String intendedOperation,String intendedRoute,String resolutionKind,Payload request,
        Payload resolution,Payload receipt,Payload approvedRevision,Actor resolvedBy,long recordedAtEpochMillis) {
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.ALWAYS)
    @io.swagger.v3.oas.annotations.media.Schema(name="EnrollmentActionRetainedPayload")
    public record Payload(String encoding,String originalJson,String canonical,String digest) { }
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.ALWAYS)
    @io.swagger.v3.oas.annotations.media.Schema(name="EnrollmentActionResolver")
    public record Actor(String kind,String issuer,String subject,String tenantId,String staffRealm,String staffActorId,String clientId) { }
}
