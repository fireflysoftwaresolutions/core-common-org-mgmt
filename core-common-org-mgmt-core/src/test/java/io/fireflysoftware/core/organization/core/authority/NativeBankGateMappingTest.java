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

import io.fireflysoftware.core.organization.models.entities.authority.BankGateRow;
import io.fireflysoftware.core.organization.models.repositories.authority.impl.NativeBankGateMutationsImpl;
import io.r2dbc.spi.*;
import org.junit.jupiter.api.Test;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class NativeBankGateMappingTest {
    static final UUID BANK=UUID.fromString("00000000-0000-0000-0000-000000000091"),TENANT=UUID.fromString("00000000-0000-0000-0000-000000000001"),INSTITUTION=UUID.fromString("00000000-0000-0000-0000-000000000002"),ISSUER=UUID.fromString("00000000-0000-0000-0000-000000000006"),ENROLLMENT=UUID.fromString("00000000-0000-0000-0000-000000000093"),ACTION=UUID.fromString("00000000-0000-0000-0000-000000000092");
    static BankGateRow owned(){return new BankGateRow(BANK,7,4,3,TENANT,INSTITUTION,ISSUER,ENROLLMENT,1L,ACTION,TENANT);}
    @Test void completeOwnerAndDurableUnownedTombstoneMapButPartialPointerCannotExist() {
        assertEquals(4,owned().ownershipGeneration());assertNotNull(new BankGateRow(BANK,8,5,3,null,null,null,null,null,ACTION,TENANT));
        assertThrows(IllegalArgumentException.class,()->new BankGateRow(BANK,7,4,3,TENANT,null,ISSUER,ENROLLMENT,1L,ACTION,TENANT));
        assertThrows(IllegalArgumentException.class,()->new BankGateRow(BANK,0,0,0,TENANT,INSTITUTION,ISSUER,ENROLLMENT,1L,null,null));
        assertThrows(IllegalArgumentException.class,()->new BankGateRow(BANK,8,5,3,null,null,null,null,null,ACTION,null));
    }
    static class Fixture {
        final ConnectionFactory factory=mock(ConnectionFactory.class);final Connection connection=mock(Connection.class);final Statement statement=mock(Statement.class,RETURNS_SELF);final R2dbcEntityTemplate template;
        Fixture(){when(factory.getMetadata()).thenReturn(()->"PostgreSQL");when(factory.create()).thenAnswer(i->Mono.just(connection));when(connection.close()).thenReturn(Mono.empty());when(connection.createStatement(anyString())).thenReturn(statement);when(statement.execute()).thenReturn(Flux.empty());template=new R2dbcEntityTemplate(factory);}
        String sql(){var c=org.mockito.ArgumentCaptor.forClass(String.class);verify(connection).createStatement(c.capture());return c.getValue();}
    }
    @Test void ownershipCasMatchesAllObservedFactsAndNeverUpdatesSourceGeneration() {
        var f=new Fixture();var next=new BankGateRow(BANK,8,5,3,null,null,null,null,null,ACTION,TENANT);
        new NativeBankGateMutationsImpl(f.template).compareAndSetOwnership(owned(),next).block();String sql=f.sql();String set=sql.substring(0,sql.indexOf(" WHERE "));String where=sql.substring(sql.indexOf(" WHERE "));
        for(String column:new String[]{"bank_id","bank_generation","ownership_generation","source_generation","current_tenant_id","current_institution_id","current_issuer_id","current_enrollment_id","current_enrollment_version","last_owner_action_id","last_owner_action_tenant_id"})assertTrue(where.contains(column),sql);
        assertFalse(set.contains("source_generation"),sql);assertTrue(set.contains("\"current_tenant_id\" = NULL"),sql);
    }
    @Test void virginCasIncludesEveryNullPointerPredicateAndCannotReparentTheGate() {
        var f=new Fixture();var virgin=new BankGateRow(BANK,0,0,0,null,null,null,null,null,null,null);
        var next=new BankGateRow(BANK,1,1,0,TENANT,INSTITUTION,ISSUER,ENROLLMENT,1L,ACTION,TENANT);
        new NativeBankGateMutationsImpl(f.template).compareAndSetOwnership(virgin,next).block();String where=f.sql().split(" WHERE ")[1];assertEquals(7,java.util.regex.Pattern.compile("IS NULL").matcher(where).results().count());
        assertThrows(IllegalArgumentException.class,()->new NativeBankGateMutationsImpl(f.template).compareAndSetOwnership(next,new BankGateRow(UUID.randomUUID(),2,2,0,TENANT,INSTITUTION,ISSUER,ENROLLMENT,1L,ACTION,TENANT)));
    }
    @Test void changingOnlyCounterValuesCannotMasqueradeAsAnOwnershipTransition() {
        var f=new Fixture();var sameOwner=new BankGateRow(BANK,8,5,3,TENANT,INSTITUTION,ISSUER,ENROLLMENT,1L,UUID.randomUUID(),TENANT);
        assertThrows(IllegalArgumentException.class,()->new NativeBankGateMutationsImpl(f.template).compareAndSetOwnership(owned(),sameOwner));
        verify(f.factory,never()).create();
    }
    @Test void actualProxyAndExplicitNativeTransactionManagerUseTheIdenticalFactory() {
        var f=new Fixture();try(var context=new org.springframework.context.annotation.AnnotationConfigApplicationContext()) {
            context.registerBean(ConnectionFactory.class,()->f.factory);
            context.register(io.fireflysoftware.core.organization.models.config.OrganizationAuthorityRepositoryConfiguration.class,io.fireflysoftware.core.organization.models.config.OrganizationEnrollmentTransactionConfiguration.class);
            context.refresh();var template=context.getBean("organizationAuthorityEntityOperations",R2dbcEntityTemplate.class);
            var manager=context.getBean("organizationEnrollmentTransactionManager",org.springframework.r2dbc.connection.R2dbcTransactionManager.class);
            assertSame(f.factory,template.getDatabaseClient().getConnectionFactory());assertSame(f.factory,manager.getConnectionFactory());
            var repository=context.getBean(io.fireflysoftware.core.organization.models.repositories.authority.NativeBankGateRepository.class);
            repository.findById(BANK).block();assertTrue(f.sql().contains("bank_id"));verify(f.statement).bind(anyInt(),eq(BANK));
        }
    }
    @Test void differentTemplateFactoryCannotSilentlyJoinTheNativeTransactionManager() {
        var f=new Fixture();var other=mock(ConnectionFactory.class);when(other.getMetadata()).thenReturn(()->"PostgreSQL");
        assertThrows(IllegalStateException.class,()->new io.fireflysoftware.core.organization.models.config.OrganizationEnrollmentTransactionConfiguration().organizationEnrollmentTransactionManager(f.factory,new R2dbcEntityTemplate(other)));
    }
}
