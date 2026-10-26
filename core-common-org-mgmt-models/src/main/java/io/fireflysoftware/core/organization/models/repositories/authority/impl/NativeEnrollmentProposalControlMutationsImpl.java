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

import io.fireflysoftware.core.organization.models.repositories.authority.NativeEnrollmentProposalControlMutations;

import io.fireflysoftware.core.organization.models.entities.authority.EnrollmentProposalControlRow;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.r2dbc.core.R2dbcEntityOperations;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.data.relational.core.query.Query;
import org.springframework.data.relational.core.query.Update;
import reactor.core.publisher.Mono;
import java.util.Objects;

public class NativeEnrollmentProposalControlMutationsImpl implements NativeEnrollmentProposalControlMutations {
    private final R2dbcEntityOperations operations;
    public NativeEnrollmentProposalControlMutationsImpl(@Qualifier("organizationAuthorityEntityOperations") R2dbcEntityOperations operations){this.operations=Objects.requireNonNull(operations);}
    @Override public Mono<Long> compareAndSet(EnrollmentProposalControlRow expected,EnrollmentProposalControlRow replacement) {
        if(expected==null || replacement==null || !expected.id().equals(replacement.id())
                || expected.controlVersion()<0 || expected.controlVersion()>=Long.MAX_VALUE-1
                || replacement.controlVersion()!=expected.controlVersion()+1 || replacement.currentRevision()<expected.currentRevision()
                || replacement.currentRevision()!=expected.currentRevision() && (expected.currentRevision()>=Long.MAX_VALUE-1
                || replacement.currentRevision()!=expected.currentRevision()+1))throw new IllegalArgumentException("Exact proposal control transition required");
        Criteria criteria=Criteria.where("id.tenantId").is(expected.id().tenantId()).and("id.proposalId").is(expected.id().proposalId())
                .and("state").is(expected.state()).and("currentRevision").is(expected.currentRevision()).and("controlVersion").is(expected.controlVersion())
                .and("lastActionId").is(expected.lastActionId());
        criteria=Criteria.from(criteria,exact("approvalActionId",expected.approvalActionId()),exact("approvalUntilMs",expected.approvalUntilMs()));
        Update update=Update.update("state",replacement.state()).set("currentRevision",replacement.currentRevision())
                .set("controlVersion",replacement.controlVersion()).set("approvalActionId",replacement.approvalActionId())
                .set("approvalUntilMs",replacement.approvalUntilMs()).set("lastActionId",replacement.lastActionId());
        return operations.update(EnrollmentProposalControlRow.class).matching(Query.query(criteria)).apply(update);
    }
    private static Criteria exact(String property,Object value){return value==null?Criteria.where(property).isNull():Criteria.where(property).is(value);}
}
