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
package io.fireflysoftware.core.organization.models.entities.authority;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Embedded;
import org.springframework.data.relational.core.mapping.Table;
import java.util.UUID;

@Table("org_native_proposal")
public record EnrollmentProposalRow(
        @Id @Embedded(onEmpty = Embedded.OnEmpty.USE_NULL) EnrollmentProposalKey id,
        UUID bankId,
        UUID institutionId,
        UUID issuerId,
        String targetKind,
        UUID allocatedEnrollmentId,
        String makerKind,
        String makerIssuer,
        String makerSubject,
        UUID makerTenantId,
        String makerStaffRealm,
        UUID makerStaffId,
        String makerClientId,
        UUID prepareActionId,
        long recordedAtMs) {
    public EnrollmentProposalRow {
        if(id==null)throw new IllegalArgumentException("Complete enrollment identity required");
    }
}
