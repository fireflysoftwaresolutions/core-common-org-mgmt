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

class PreparationFixtureWiringTest {
    @Test void explicitOrdinaryAndNativeRepositoryConfigurationsUseTheSameFactoryWithoutOverrides(){var f=new NativeBankGateMappingTest.Fixture();try(var context=new org.springframework.context.annotation.AnnotationConfigApplicationContext()){
        context.setAllowBeanDefinitionOverriding(false);context.registerBean(ConnectionFactory.class,()->f.factory);context.register(io.fireflysoftware.core.organization.models.config.OrganizationAuthorityRepositoryConfiguration.class,io.fireflysoftware.core.organization.models.config.OrganizationEnrollmentTransactionConfiguration.class,OwnedBankPreparationFixture.OrdinaryRepositories.class);context.refresh();
        var bank=context.getBean(io.fireflysoftware.core.organization.models.repositories.BankRepository.class);bank.findById(NativeBankGateMappingTest.BANK).block();assertTrue(f.sql().contains("bank"));var operations=context.getBean("organizationAuthorityEntityOperations",R2dbcEntityTemplate.class);assertSame(operations,context.getBean("r2dbcEntityTemplate"));assertSame(f.factory,operations.getDatabaseClient().getConnectionFactory());assertSame(f.factory,context.getBean("organizationEnrollmentTransactionManager",org.springframework.r2dbc.connection.R2dbcTransactionManager.class).getConnectionFactory());assertNotNull(context.getBean(io.fireflysoftware.core.organization.models.repositories.authority.NativeEnrollmentPreparationContextRepository.class));
        assertNotNull(OwnedBankPreparationFixture.OrdinaryRepositories.class.getAnnotation(org.springframework.boot.test.context.TestConfiguration.class));
    }}
}
