/*
 * Copyright 2025 Firefly Software Foundation
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


package io.fireflysoftware.core.organization.models.repositories.scoped;

import io.fireflysoftware.core.organization.models.entities.WorkingCalendar;
import io.fireflysoftware.core.organization.models.repositories.WorkingCalendarRepository;
import io.r2dbc.spi.*;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.r2dbc.dialect.PostgresDialect;
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import org.springframework.r2dbc.core.DatabaseClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ScopedRepositoryBindingTest {
    @Configuration(proxyBeanMethods = false)
    @EnableR2dbcRepositories(basePackages = "io.fireflysoftware.core.organization.models.repositories",
            entityOperationsRef = "r2dbcEntityTemplate",
            excludeFilters = @org.springframework.context.annotation.ComponentScan.Filter(
                    type = org.springframework.context.annotation.FilterType.REGEX,
                    pattern = "io\\.fireflysoftware\\.core\\.organization\\.models\\.repositories\\.authority\\..*"))
    static class RepositoryConfiguration { }

    @Test void actualRepositoryProxyDiscoversFragmentAndMapsDerivedParentShareLock() {
        var factory = mock(ConnectionFactory.class);
        var metadata = mock(ConnectionFactoryMetadata.class);
        when(factory.getMetadata()).thenReturn(metadata);
        when(metadata.getName()).thenReturn("PostgreSQL");
        var connection = mock(Connection.class);
        when(factory.create()).thenAnswer(invocation -> Mono.just(connection));
        when(connection.close()).thenReturn(Mono.empty());
        var statement = mock(Statement.class, RETURNS_SELF);
        when(connection.createStatement(anyString())).thenReturn(statement);
        when(statement.execute()).thenReturn(Flux.empty());
        var template = new R2dbcEntityTemplate(DatabaseClient.create(factory), PostgresDialect.INSTANCE);
        var transactionManager = new R2dbcTransactionManager(factory);
        try (var context = new AnnotationConfigApplicationContext()) {
            context.registerBean("connectionFactory", ConnectionFactory.class, () -> factory);
            context.registerBean("r2dbcEntityTemplate", R2dbcEntityTemplate.class, () -> template);
            context.registerBean("transactionManager", R2dbcTransactionManager.class, () -> transactionManager);
            context.register(RepositoryConfiguration.class);
            context.refresh();
            assertSame(factory, template.getDatabaseClient().getConnectionFactory());
            assertSame(factory, transactionManager.getConnectionFactory());
            var repository = context.getBean(WorkingCalendarRepository.class);
            UUID calendarId = UUID.randomUUID(), bankId = UUID.randomUUID();
            repository.findPageForBank(bankId, Criteria.empty(), PageRequest.of(0, 5)).collectList().block();
            repository.countForBank(bankId, Criteria.empty()).block();
            repository.findByIdAndBankId(calendarId, bankId).block();
            var sql = org.mockito.ArgumentCaptor.forClass(String.class);
            verify(connection, times(3)).createStatement(sql.capture());
            assertTrue(sql.getAllValues().get(0).contains("bank_id"));
            assertTrue(sql.getAllValues().get(1).contains("bank_id"));
            assertFalse(sql.getAllValues().get(1).contains("LIMIT"));
            assertTrue(sql.getAllValues().get(2).contains("FOR SHARE OF \"working_calendar\""), sql.getAllValues().get(2));
            verify(statement, atLeastOnce()).bind(anyInt(), eq(bankId));
            verify(statement).bind(anyInt(), eq(calendarId));
        }
    }
}
