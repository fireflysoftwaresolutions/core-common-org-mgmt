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

import io.fireflysoftware.core.organization.models.repositories.authority.NativeBankGateMutations;

import io.fireflysoftware.core.organization.models.entities.authority.BankGateRow;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.r2dbc.core.R2dbcEntityOperations;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.data.relational.core.query.Query;
import org.springframework.data.relational.core.query.Update;
import reactor.core.publisher.Mono;
import java.util.Objects;

public class NativeBankGateMutationsImpl implements NativeBankGateMutations {
    private final R2dbcEntityOperations operations;
    public NativeBankGateMutationsImpl(@Qualifier("organizationAuthorityEntityOperations") R2dbcEntityOperations operations) {
        this.operations=Objects.requireNonNull(operations);
    }
    @Override
    public Mono<Long> compareAndSetOwnership(BankGateRow expected, BankGateRow replacement) {
        if(expected==null || replacement==null || !expected.bankId().equals(replacement.bankId())
                || expected.sourceGeneration()!=replacement.sourceGeneration()
                || replacement.bankGeneration()!=expected.bankGeneration()+1
                || replacement.ownershipGeneration()!=expected.ownershipGeneration()+1 || sameOwner(expected,replacement)) throw new IllegalArgumentException("Exact ownership transition required");
        Criteria criteria=Criteria.where("bankId").is(expected.bankId()).and("bankGeneration").is(expected.bankGeneration())
                .and("ownershipGeneration").is(expected.ownershipGeneration()).and("sourceGeneration").is(expected.sourceGeneration());
        criteria=Criteria.from(criteria,exact("currentTenantId",expected.currentTenantId()),exact("currentInstitutionId",expected.currentInstitutionId()),
                exact("currentIssuerId",expected.currentIssuerId()),exact("currentEnrollmentId",expected.currentEnrollmentId()),
                exact("currentEnrollmentVersion",expected.currentEnrollmentVersion()),exact("lastOwnerActionId",expected.lastOwnerActionId()),
                exact("lastOwnerActionTenantId",expected.lastOwnerActionTenantId()));
        Update update=Update.update("bankGeneration",replacement.bankGeneration()).set("ownershipGeneration",replacement.ownershipGeneration())
                .set("currentTenantId",replacement.currentTenantId()).set("currentInstitutionId",replacement.currentInstitutionId())
                .set("currentIssuerId",replacement.currentIssuerId()).set("currentEnrollmentId",replacement.currentEnrollmentId())
                .set("currentEnrollmentVersion",replacement.currentEnrollmentVersion()).set("lastOwnerActionId",replacement.lastOwnerActionId())
                .set("lastOwnerActionTenantId",replacement.lastOwnerActionTenantId());
        return operations.update(BankGateRow.class).matching(Query.query(criteria)).apply(update);
    }
    private static boolean sameOwner(BankGateRow left,BankGateRow right) {
        return Objects.equals(left.currentTenantId(),right.currentTenantId())
                && Objects.equals(left.currentInstitutionId(),right.currentInstitutionId())
                && Objects.equals(left.currentIssuerId(),right.currentIssuerId())
                && Objects.equals(left.currentEnrollmentId(),right.currentEnrollmentId())
                && Objects.equals(left.currentEnrollmentVersion(),right.currentEnrollmentVersion());
    }
    private static Criteria exact(String property,Object value) {return value==null?Criteria.where(property).isNull():Criteria.where(property).is(value);}
}
