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

import java.util.UUID;

public record EnrollmentProposalRevisionKey(UUID tenantId, UUID proposalId, Long revision) {
    public EnrollmentProposalRevisionKey {
        if(tenantId==null || tenantId.getMostSignificantBits()==0 && tenantId.getLeastSignificantBits()==0) throw new IllegalArgumentException("Complete enrollment identity required");
        if(proposalId==null || proposalId.getMostSignificantBits()==0 && proposalId.getLeastSignificantBits()==0) throw new IllegalArgumentException("Complete enrollment identity required");
        if(revision==null || revision<=0 || revision==Long.MAX_VALUE) throw new IllegalArgumentException("Complete enrollment identity required");
    }
}
