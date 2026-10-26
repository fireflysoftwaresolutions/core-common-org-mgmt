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

@Table("org_native_receipt")
public record EnrollmentReceiptRow(
        @Id @Embedded(onEmpty = Embedded.OnEmpty.USE_NULL) EnrollmentActionKey id,
        UUID receiptId,
        byte[] resultOriginal,
        String resultEncoding,
        String resultCanonical,
        String resultDigest,
        long recordedAtMs) {
    public EnrollmentReceiptRow {
        if(id==null)throw new IllegalArgumentException("Complete enrollment identity required");
        if(resultOriginal==null || resultOriginal.length==0)throw new IllegalArgumentException("Original enrollment evidence required");
        resultOriginal=resultOriginal.clone();
    }
    @Override public byte[] resultOriginal(){return resultOriginal.clone();}
}
