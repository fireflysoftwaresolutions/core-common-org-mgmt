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

import io.fireflysoftware.core.organization.models.config.OrganizationAuthorityRepositoryConfiguration;
import io.fireflysoftware.core.organization.models.entities.authority.PublicationVersionKey;
import io.fireflysoftware.core.organization.models.entities.authority.PublicationVersionRow;
import org.springframework.data.r2dbc.mapping.OutboundRow;
import io.fireflysoftware.core.organization.models.repositories.WorkingCalendarRepository;
import io.fireflysoftware.core.organization.models.repositories.authority.NativePublicationVersionRepository;
import io.r2dbc.spi.*;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.*;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OrganizationAuthorityRepositoryBindingTest {
    @Configuration(proxyBeanMethods = false)
    @EnableR2dbcRepositories(basePackages = "io.fireflysoftware.core.organization.models.repositories",
            entityOperationsRef = "r2dbcEntityTemplate",
            excludeFilters = @ComponentScan.Filter(type = FilterType.REGEX,
                    pattern = "io\\.fireflysoftware\\.core\\.organization\\.models\\.repositories\\.authority\\..*"))
    static class BaselineRepositories { }

    @Test void protectedPointReadBindsAllCompositeColumnsThroughActualSameFactoryTemplate() {
        var factory = mock(ConnectionFactory.class);
        when(factory.getMetadata()).thenReturn(() -> "PostgreSQL");
        var connection = mock(Connection.class);
        when(factory.create()).thenAnswer(invocation -> Mono.just(connection));
        when(connection.close()).thenReturn(Mono.empty());
        var statement = mock(Statement.class, RETURNS_SELF);
        when(connection.createStatement(anyString())).thenReturn(statement);
        when(statement.execute()).thenReturn(Flux.empty());
        try (var context = new AnnotationConfigApplicationContext()) {
            context.registerBean(ConnectionFactory.class, () -> factory);
            context.register(OrganizationAuthorityRepositoryConfiguration.class, BaselineRepositories.class);
            context.refresh();
            var operations = context.getBean("organizationAuthorityEntityOperations", R2dbcEntityTemplate.class);
            assertSame(operations, context.getBean("r2dbcEntityTemplate"));
            assertSame(factory, operations.getDatabaseClient().getConnectionFactory());
            assertNotNull(context.getBean(WorkingCalendarRepository.class));
            UUID tenant = UUID.randomUUID(), publication = UUID.randomUUID();
            context.getBean(NativePublicationVersionRepository.class)
                    .findById(new PublicationVersionKey(tenant, publication, 2L)).block();
            var captured = org.mockito.ArgumentCaptor.forClass(String.class);
            verify(connection).createStatement(captured.capture());
            String sql = captured.getValue();
            assertTrue(sql.contains("tenant_id"), sql);
            assertTrue(sql.contains("publication_id"), sql);
            assertTrue(sql.contains("version"), sql);
            verify(statement).bind(anyInt(), eq(tenant));
            verify(statement).bind(anyInt(), eq(publication));
            verify(statement).bind(anyInt(), eq(2L));
            assertTrue(java.util.Arrays.stream(NativePublicationVersionRepository.class.getMethods())
                    .noneMatch(method -> method.getName().equals("save") || method.getName().startsWith("delete")
                            || method.getName().equals("findAll")));
        }
    }
    @Test void actualMapperFlattensAllIdentityColumnsAndKeepsOriginalEvidenceBytes() {
        var factory = mock(ConnectionFactory.class);
        when(factory.getMetadata()).thenReturn(() -> "PostgreSQL");
        var template = new R2dbcEntityTemplate(factory);
        UUID tenant = UUID.randomUUID(), publication = UUID.randomUUID();
        byte[] definition = new byte[]{1, 2}, execution = new byte[]{3, 4};
        var entity = new PublicationVersionRow(new PublicationVersionKey(tenant, publication, 2L),
                UUID.randomUUID(), UUID.randomUUID(), "ACCOUNTING_BOOK", "NATIVE_INSTITUTION",
                UUID.randomUUID(), UUID.randomUUID(), "FF_FINANCIAL_CALENDAR_V1", definition,
                "retained-definition", "a".repeat(64), "FF_ORGANIZATION_AUTHORITY_EXECUTION_TARGET_V2",
                execution, "retained-execution", "b".repeat(64), java.time.OffsetDateTime.parse("2026-10-24T00:00:00Z"));
        definition[0] = 9; execution[0] = 9;
        entity.originalDefinition()[0] = 8; entity.originalExecutionTarget()[0] = 8;
        var row = new OutboundRow(); template.getConverter().write(entity, row);
        assertEquals(18, row.size());
        var columns = row.entrySet().stream().collect(java.util.stream.Collectors.toMap(
                entry -> entry.getKey().getReference(), entry -> entry.getValue().getValue()));
        assertFalse(columns.containsKey("id"));
        assertEquals(tenant, columns.get("tenant_id"));
        assertEquals(publication, columns.get("publication_id"));
        assertEquals(2L, columns.get("version"));
        assertArrayEquals(new byte[]{1, 2}, (byte[]) columns.get("original_definition"));
        assertArrayEquals(new byte[]{3, 4}, (byte[]) columns.get("original_execution_target"));
        verify(factory, never()).create();
    }

}
