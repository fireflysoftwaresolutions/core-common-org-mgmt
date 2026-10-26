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

@Table("org_native_proposal_control")
public record EnrollmentProposalControlRow(
        @Id @Embedded(onEmpty = Embedded.OnEmpty.USE_NULL) EnrollmentProposalKey id,
        String state,
        long currentRevision,
        long controlVersion,
        UUID approvalActionId,
        Long approvalUntilMs,
        UUID lastActionId) {
    public EnrollmentProposalControlRow {
        if(id==null || state==null || !java.util.Set.of("DRAFT","SUBMITTED","APPROVED","REJECTED","WITHDRAWN","CANCELLED","EXECUTED","CLOSED_NO_EFFECT").contains(state)
                || currentRevision<=0 || currentRevision==Long.MAX_VALUE || controlVersion<0 || controlVersion==Long.MAX_VALUE
                || lastActionId==null || lastActionId.getMostSignificantBits()==0 && lastActionId.getLeastSignificantBits()==0
                || "APPROVED".equals(state) && (approvalActionId==null || approvalUntilMs==null)
                || !"APPROVED".equals(state) && (approvalActionId!=null || approvalUntilMs!=null)
                || approvalActionId!=null && approvalActionId.getMostSignificantBits()==0 && approvalActionId.getLeastSignificantBits()==0
                || approvalUntilMs!=null && (approvalUntilMs<=0 || approvalUntilMs>253402300799999L))
            throw new IllegalArgumentException("Complete proposal control required");
    }
}
