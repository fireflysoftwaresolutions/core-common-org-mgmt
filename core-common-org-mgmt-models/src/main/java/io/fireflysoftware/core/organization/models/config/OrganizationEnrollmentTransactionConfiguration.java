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
package io.fireflysoftware.core.organization.models.config;

import io.r2dbc.spi.ConnectionFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.r2dbc.core.R2dbcEntityOperations;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;

@Configuration(proxyBeanMethods = false)
public class OrganizationEnrollmentTransactionConfiguration {
    @Bean("organizationEnrollmentTransactionManager")
    public R2dbcTransactionManager organizationEnrollmentTransactionManager(ConnectionFactory factory,
            @Qualifier("organizationAuthorityEntityOperations") R2dbcEntityOperations operations) {
        if(operations.getDatabaseClient().getConnectionFactory()!=factory)
            throw new IllegalStateException("Enrollment repositories and transaction manager require the same ConnectionFactory");
        return new R2dbcTransactionManager(factory);
    }
}
