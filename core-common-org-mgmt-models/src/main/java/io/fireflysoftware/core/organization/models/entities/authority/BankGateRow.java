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
import org.springframework.data.relational.core.mapping.Table;
import java.util.UUID;

@Table("org_native_bank_gate")
public record BankGateRow(@Id UUID bankId, long bankGeneration, long ownershipGeneration, long sourceGeneration,
        UUID currentTenantId, UUID currentInstitutionId, UUID currentIssuerId, UUID currentEnrollmentId,
        Long currentEnrollmentVersion, UUID lastOwnerActionId, UUID lastOwnerActionTenantId) {
    public BankGateRow {
        if(bankId==null || bankGeneration<0 || bankGeneration==Long.MAX_VALUE || ownershipGeneration<0
                || ownershipGeneration==Long.MAX_VALUE || sourceGeneration<0 || sourceGeneration==Long.MAX_VALUE
                || bankGeneration<ownershipGeneration) throw invalid();
        int present=(currentTenantId==null?0:1)+(currentInstitutionId==null?0:1)+(currentIssuerId==null?0:1)
                +(currentEnrollmentId==null?0:1)+(currentEnrollmentVersion==null?0:1);
        if(present!=0 && present!=5 || currentEnrollmentVersion!=null
                && (currentEnrollmentVersion<=0 || currentEnrollmentVersion==Long.MAX_VALUE)
                || (lastOwnerActionId==null)!=(lastOwnerActionTenantId==null)
                || ownershipGeneration==0 && (present!=0 || lastOwnerActionId!=null)
                || ownershipGeneration>0 && lastOwnerActionId==null) throw invalid();
        for(UUID id:new UUID[]{bankId,currentTenantId,currentInstitutionId,currentIssuerId,currentEnrollmentId,lastOwnerActionId,lastOwnerActionTenantId})
            if(id!=null && id.getMostSignificantBits()==0 && id.getLeastSignificantBits()==0) throw invalid();
    }
    private static IllegalArgumentException invalid(){return new IllegalArgumentException("Invalid complete Bank gate snapshot");}
}
