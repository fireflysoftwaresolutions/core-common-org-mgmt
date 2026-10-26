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

import io.fireflysoftware.core.organization.models.repositories.authority.NativePublicationVersionRepository;
import io.r2dbc.spi.ConnectionFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories;

@Configuration(proxyBeanMethods = false)
@EnableR2dbcRepositories(basePackageClasses = NativePublicationVersionRepository.class,
        entityOperationsRef = "organizationAuthorityEntityOperations")
public class OrganizationAuthorityRepositoryConfiguration {
    @Bean({"organizationAuthorityEntityOperations", "r2dbcEntityTemplate"})
    public R2dbcEntityTemplate organizationAuthorityEntityOperations(ConnectionFactory connectionFactory) {
        return new R2dbcEntityTemplate(connectionFactory);
    }
}
