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

@Table("org_native_action")
public record EnrollmentActionRow(
        @Id @Embedded(onEmpty = Embedded.OnEmpty.USE_NULL) EnrollmentActionKey id,
        UUID bankId,
        String intendedOperation,
        String intendedRoute,
        byte[] requestOriginal,
        String requestEncoding,
        String requestCanonical,
        String requestDigest,
        String resolutionKind,
        byte[] resolutionOriginal,
        String resolutionEncoding,
        String resolutionCanonical,
        String resolutionDigest,
        String resolverKind,
        String resolverIssuer,
        String resolverSubject,
        UUID resolverTenantId,
        String resolverStaffRealm,
        UUID resolverStaffId,
        String resolverClientId,
        UUID proposalId,
        Long proposalRevision,
        String targetDigest,
        String outcome,
        long recordedAtMs) {
    public EnrollmentActionRow {
        if(id==null)throw new IllegalArgumentException("Complete enrollment identity required");
        if(requestOriginal==null || requestOriginal.length==0)throw new IllegalArgumentException("Original enrollment evidence required");
        requestOriginal=requestOriginal.clone();
        if(resolutionOriginal==null || resolutionOriginal.length==0)throw new IllegalArgumentException("Original enrollment evidence required");
        resolutionOriginal=resolutionOriginal.clone();
    }
    @Override public byte[] requestOriginal(){return requestOriginal.clone();}
    @Override public byte[] resolutionOriginal(){return resolutionOriginal.clone();}
}
