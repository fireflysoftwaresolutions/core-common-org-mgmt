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

import io.fireflysoftware.core.organization.core.authority.OrganizationPreparationAuthorization;
import io.fireflysoftware.core.organization.core.services.authority.*;
import io.fireflysoftware.core.organization.core.services.authority.impl.*;
import io.fireflysoftware.core.organization.models.repositories.BankRepository;
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
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.web.reactive.function.client.WebClient;
import java.time.Clock;

@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(name="firefly.organization.enrollment.proposal-commands.enabled",havingValue="true")
@EnableConfigurationProperties({OrganizationEnrollmentReadProperties.class,OrganizationEnrollmentObserverProperties.class})
public class OrganizationEnrollmentPreparationConfiguration {
    @Bean
    public OrganizationPreparationAuthorization organizationPreparationAuthorization(SecurityContextPort callers,PolicyDecisionPort policies,
            @Qualifier("organizationEnrollmentClock") Clock clock,OrganizationEnrollmentReadProperties properties,ResourceServerProperties shared,
            BankEnrollmentActionReadService protectedActionHistory) {
        properties.requireSharedProjection(shared);return new OrganizationPreparationAuthorization(callers,policies,clock,properties.platformTenantId(),properties.profiles(),properties.staffRealms());
    }
    @Bean
    public BankEnrollmentPreparationCaptureService bankEnrollmentPreparationCaptureService(OrganizationPreparationAuthorization authorization,
            NativeEnrollmentPreparationContextRepository preparations,NativeEnrollmentContextRepository contexts,BankRepository banks,NativeBankGateRepository gates,
            NativeBankEnrollmentOwnerIdentityRepository versions,NativeBankEnrollmentControlRepository controls,
            @Qualifier("organizationAuthorityEntityOperations") R2dbcEntityOperations operations,@Qualifier("organizationEnrollmentTransactionManager") R2dbcTransactionManager manager) {
        return new BankEnrollmentPreparationCaptureServiceImpl(authorization,preparations,contexts,banks,gates,versions,controls,operations,manager);
    }
    @Bean
    public BankEnrollmentPreparationObservationService bankEnrollmentPreparationObservationService(OrganizationPreparationAuthorization authorization,
            ReactiveClientRegistrationRepository registrations,ReactiveOAuth2AuthorizedClientManager manager,OrganizationEnrollmentObserverProperties properties,
            @Qualifier("organizationEnrollmentClock") Clock clock) {
        return new BankEnrollmentPreparationObservationServiceImpl(authorization,registrations,manager,OrganizationEnrollmentCommandConfiguration.configurationDecoders(properties,clock),properties.byTenant(),WebClient.builder().build(),clock);
    }
    @Bean
    public BankEnrollmentPreparationCommandService bankEnrollmentPreparationCommandService(OrganizationPreparationAuthorization authorization,
            NativeEnrollmentPreparationContextRepository preparations,NativeEnrollmentContextRepository contexts,NativeEnrollmentActionRepository actions,
            @Qualifier("organizationAuthorityEntityOperations") R2dbcEntityOperations operations,@Qualifier("organizationEnrollmentTransactionManager") R2dbcTransactionManager manager) {
        return new BankEnrollmentPreparationCommandServiceImpl(authorization,preparations,contexts,actions,operations,manager);
    }
    @Bean
    public BankEnrollmentPreparationService bankEnrollmentPreparationService(OrganizationPreparationAuthorization authorization,BankEnrollmentPreparationCaptureService captures,
            BankEnrollmentPreparationObservationService observer,BankEnrollmentPreparationCommandService commands) {
        return new BankEnrollmentPreparationServiceImpl(authorization,captures,observer,commands);
    }
}
