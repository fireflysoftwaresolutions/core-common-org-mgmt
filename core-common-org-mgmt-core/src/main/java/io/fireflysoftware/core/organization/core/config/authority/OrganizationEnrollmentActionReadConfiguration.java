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
package io.fireflysoftware.core.organization.core.config.authority;

import io.fireflysoftware.core.organization.core.authority.OrganizationEnrollmentHistoryAuthorization;
import io.fireflysoftware.core.organization.core.services.authority.BankEnrollmentActionReadService;
import io.fireflysoftware.core.organization.core.services.authority.BankEnrollmentPreparationRecoveryService;
import io.fireflysoftware.core.organization.core.services.authority.impl.BankEnrollmentPreparationRecoveryServiceImpl;
import io.fireflysoftware.core.organization.core.services.authority.impl.BankEnrollmentActionReadServiceImpl;
import io.fireflysoftware.core.organization.models.repositories.authority.*;
import org.fireflyframework.security.rs.ResourceServerProperties;
import org.fireflyframework.security.spi.PolicyDecisionPort;
import org.fireflyframework.security.spi.SecurityContextPort;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.r2dbc.core.R2dbcEntityOperations;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import java.time.Clock;

@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(name="firefly.organization.enrollment.action-read.enabled",havingValue="true")
@EnableConfigurationProperties(OrganizationEnrollmentReadProperties.class)
public class OrganizationEnrollmentActionReadConfiguration {
    @Bean("organizationEnrollmentClock")
    public Clock organizationEnrollmentClock(){return Clock.systemUTC();}
    @Bean
    public OrganizationEnrollmentHistoryAuthorization organizationEnrollmentHistoryAuthorization(SecurityContextPort callers,
            PolicyDecisionPort policies,@Qualifier("organizationEnrollmentClock") Clock clock,
            OrganizationEnrollmentReadProperties properties,ResourceServerProperties shared) {
        properties.requireSharedProjection(shared);
        return new OrganizationEnrollmentHistoryAuthorization(callers,policies,clock,properties.platformTenantId(),properties.profiles(),properties.staffRealms());
    }
    @Bean
    public BankEnrollmentActionReadService bankEnrollmentActionReadService(OrganizationEnrollmentHistoryAuthorization authorization,
            NativeEnrollmentContextRepository contexts,NativeEnrollmentActionRepository actions,NativeEnrollmentReceiptRepository receipts,
            NativeEnrollmentRevisionRepository revisions,@Qualifier("organizationAuthorityEntityOperations") R2dbcEntityOperations operations,
            @Qualifier("organizationEnrollmentTransactionManager") R2dbcTransactionManager manager) {
        return new BankEnrollmentActionReadServiceImpl(authorization,contexts,actions,receipts,revisions,operations,manager);
    }
    @Bean
    public BankEnrollmentPreparationRecoveryService bankEnrollmentPreparationRecoveryService(BankEnrollmentActionReadService history){return new BankEnrollmentPreparationRecoveryServiceImpl(history);}

}
