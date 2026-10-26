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

class PreparationFixtureCallbackTest {
    @Test void mappedBlockingMetadataMutationIsRejectedOnTheActualNonblockingObserverExecutionModel(){
        Mono.fromRunnable(()->{assertTrue(reactor.core.scheduler.Schedulers.isInNonBlockingThread());var f=new NativeBankGateMappingTest.Fixture();var error=assertThrows(IllegalStateException.class,()->f.template.update(io.fireflysoftware.core.organization.models.entities.Bank.class).matching(org.springframework.data.relational.core.query.Query.query(org.springframework.data.relational.core.query.Criteria.where("id").is(NativeBankGateMappingTest.BANK))).apply(org.springframework.data.relational.core.query.Update.update("isActive",false)).block());assertTrue(error.getMessage().contains("blocking"),error.getMessage());}).subscribeOn(reactor.core.scheduler.Schedulers.parallel()).block();
    }
    @Test void synchronousOwnedMetadataHookMakesBothCommittedChangesOnTheObserverThread()throws Exception {
        var connection=mock(java.sql.Connection.class);var statement=mock(java.sql.PreparedStatement.class);when(connection.getAutoCommit()).thenReturn(true);when(connection.prepareStatement(anyString())).thenReturn(statement);when(statement.executeUpdate()).thenReturn(1);
        Mono.fromRunnable(()->{assertTrue(reactor.core.scheduler.Schedulers.isInNonBlockingThread());try{OwnedBankPreparationFixture.mutateFixtureBankSource(connection,NativeBankGateMappingTest.BANK);}catch(Exception error){throw new AssertionError(error);}}).subscribeOn(reactor.core.scheduler.Schedulers.parallel()).block();
        verify(statement,times(2)).executeUpdate();verify(statement).setBoolean(1,false);verify(statement).setBoolean(1,true);verify(statement,times(2)).setObject(2,NativeBankGateMappingTest.BANK);verify(statement).close();
    }

}
