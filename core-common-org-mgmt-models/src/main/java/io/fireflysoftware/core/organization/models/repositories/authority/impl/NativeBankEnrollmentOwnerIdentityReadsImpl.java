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
package io.fireflysoftware.core.organization.models.repositories.authority.impl;

import io.fireflysoftware.core.organization.models.entities.authority.BankEnrollmentOwnerIdentityRow;
import io.fireflysoftware.core.organization.models.entities.authority.BankEnrollmentVersionKey;
import io.fireflysoftware.core.organization.models.repositories.authority.NativeBankEnrollmentOwnerIdentityReads;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.r2dbc.core.R2dbcEntityOperations;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.data.relational.core.query.Query;

import reactor.core.publisher.Mono;

import java.util.Objects;

public class NativeBankEnrollmentOwnerIdentityReadsImpl
        implements NativeBankEnrollmentOwnerIdentityReads {
    private final R2dbcEntityOperations operations;

    public NativeBankEnrollmentOwnerIdentityReadsImpl(
            @Qualifier("organizationAuthorityEntityOperations") R2dbcEntityOperations operations) {
        this.operations = Objects.requireNonNull(operations);
    }

    @Override
    public Mono<BankEnrollmentOwnerIdentityRow> findById(BankEnrollmentVersionKey id) {
        Objects.requireNonNull(id);
        var query =
                Query.query(
                                Criteria.where("id.tenantId")
                                        .is(id.tenantId())
                                        .and("id.enrollmentId")
                                        .is(id.enrollmentId())
                                        .and("id.version")
                                        .is(id.version()))
                        .columns(
                                "id.tenantId",
                                "id.enrollmentId",
                                "id.version",
                                "bankId",
                                "institutionId",
                                "issuerId",
                                "ownershipGenerationAtAdmission");
        return operations.selectOne(query, BankEnrollmentOwnerIdentityRow.class);
    }
}
