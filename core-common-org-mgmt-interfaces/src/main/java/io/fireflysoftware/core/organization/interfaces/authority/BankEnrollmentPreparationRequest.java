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

import java.util.List;

@io.swagger.v3.oas.annotations.media.Schema(name="BankEnrollmentPreparationRequest",additionalProperties=io.swagger.v3.oas.annotations.media.Schema.AdditionalPropertiesValue.FALSE,
        requiredProperties={"schemaVersion","actionId","mode","bankId","destinationTenantId","institutionId","issuerAuthorityId","allowedUses","notAfterEpochMillis","reason","evidenceReferences"})
public record BankEnrollmentPreparationRequest(int schemaVersion,String actionId,Mode mode,String bankId,
        String destinationTenantId,String institutionId,String issuerAuthorityId,List<FinancialCalendarUse> allowedUses,
        long notAfterEpochMillis,String reason,List<String> evidenceReferences) {
    public enum Mode { ENROLL,TRANSFER }
    public BankEnrollmentPreparationRequest {
        allowedUses=List.copyOf(allowedUses);evidenceReferences=List.copyOf(evidenceReferences);
    }
}
