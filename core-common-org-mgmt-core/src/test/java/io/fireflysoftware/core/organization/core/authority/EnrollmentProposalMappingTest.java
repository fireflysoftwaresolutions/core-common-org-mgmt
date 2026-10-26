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
package io.fireflysoftware.core.organization.core.authority;

import io.fireflysoftware.core.organization.models.entities.authority.*;
import io.fireflysoftware.core.organization.models.repositories.authority.impl.NativeEnrollmentProposalControlMutationsImpl;
import io.r2dbc.spi.*;
import org.junit.jupiter.api.Test;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.r2dbc.mapping.OutboundRow;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class EnrollmentProposalMappingTest {
    static final UUID TENANT=UUID.fromString("00000000-0000-0000-0000-000000000001"),PROPOSAL=UUID.fromString("00000000-0000-0000-0000-000000000303"),ACTION=UUID.fromString("00000000-0000-0000-0000-000000000304"),NEXT=UUID.fromString("00000000-0000-0000-0000-000000000305");
    static class Fixture {
        final ConnectionFactory factory=mock(ConnectionFactory.class);final Connection connection=mock(Connection.class);final Statement statement=mock(Statement.class,RETURNS_SELF);final R2dbcEntityTemplate template;
        Fixture(){when(factory.getMetadata()).thenReturn(()->"PostgreSQL");when(factory.create()).thenAnswer(i->Mono.just(connection));when(connection.close()).thenReturn(Mono.empty());when(connection.createStatement(anyString())).thenReturn(statement);when(statement.execute()).thenReturn(Flux.empty());template=new R2dbcEntityTemplate(factory);}
        String sql(){var c=org.mockito.ArgumentCaptor.forClass(String.class);verify(connection).createStatement(c.capture());return c.getValue();}
    }
    @Test void immutableRevisionMapsAllCompositeColumnsAndDefensivelyRetainsEvidence() {
        var f=new Fixture();byte[] original={1,2};var entity=new EnrollmentProposalRevisionRow(new EnrollmentProposalRevisionKey(TENANT,PROPOSAL,1L),"ENROLL",ACTION,7,0,0,null,1792713620223L,java.util.List.of("ACCOUNTING_BOOK"),original,"FF_ORGANIZATION_BANK_ENROLLMENT_REVISION_V2","retained","a".repeat(64),1792713600223L);
        original[0]=9;entity.revisionOriginal()[0]=8;assertArrayEquals(new byte[]{1,2},entity.revisionOriginal());var row=new OutboundRow();f.template.getConverter().write(entity,row);var columns=row.entrySet().stream().collect(java.util.stream.Collectors.toMap(e->e.getKey().getReference(),e->e.getValue()));assertEquals(16,columns.size());assertFalse(columns.containsKey("id"));assertEquals(TENANT,columns.get("tenant_id").getValue());assertEquals(PROPOSAL,columns.get("proposal_id").getValue());assertEquals(1L,columns.get("revision").getValue());assertArrayEquals(new byte[]{1,2},(byte[])columns.get("revision_original").getValue());verify(f.factory,never()).create();
    }
    @Test void proposalControlCasUsesEntireScopedKeyAndAllObservedControlFacts() {
        var f=new Fixture();var key=new EnrollmentProposalKey(TENANT,PROPOSAL);var before=new EnrollmentProposalControlRow(key,"SUBMITTED",1,2,null,null,ACTION);var after=new EnrollmentProposalControlRow(key,"APPROVED",1,3,NEXT,1792713615223L,NEXT);
        new NativeEnrollmentProposalControlMutationsImpl(f.template).compareAndSet(before,after).block();String sql=f.sql(),where=sql.substring(sql.indexOf(" WHERE "));
        for(String field:new String[]{"tenant_id","proposal_id","state","current_revision","control_version","approval_action_id","approval_until_ms","last_action_id"})assertTrue(where.contains(field),sql);assertEquals(2,java.util.regex.Pattern.compile("IS NULL").matcher(where).results().count());verify(f.statement,atLeastOnce()).bind(anyInt(),eq(TENANT));verify(f.statement,atLeastOnce()).bind(anyInt(),eq(PROPOSAL));
    }
    @Test void partialCompositeIdentityAndChangedProposalCannotBeSavedThroughTheControlFragment() {
        assertThrows(IllegalArgumentException.class,()->new EnrollmentProposalKey(null,PROPOSAL));var f=new Fixture();var before=new EnrollmentProposalControlRow(new EnrollmentProposalKey(TENANT,PROPOSAL),"SUBMITTED",1,2,null,null,ACTION);var after=new EnrollmentProposalControlRow(new EnrollmentProposalKey(TENANT,UUID.randomUUID()),"APPROVED",1,3,NEXT,1792713615223L,NEXT);assertThrows(IllegalArgumentException.class,()->new NativeEnrollmentProposalControlMutationsImpl(f.template).compareAndSet(before,after));verify(f.factory,never()).create();
    }
    @Test void approvedControlNeedsCompleteApprovalAndBoundedRevisionAndCounter() {
        var key=new EnrollmentProposalKey(TENANT,PROPOSAL);
        assertThrows(IllegalArgumentException.class,()->new EnrollmentProposalControlRow(key,"APPROVED",1,3,null,null,NEXT));
        assertThrows(IllegalArgumentException.class,()->new EnrollmentProposalControlRow(key,"SUBMITTED",1,Long.MAX_VALUE,null,null,NEXT));
        assertThrows(IllegalArgumentException.class,()->new EnrollmentProposalControlRow(key,"APPROVED",1,3,NEXT,null,NEXT));
    }
}
