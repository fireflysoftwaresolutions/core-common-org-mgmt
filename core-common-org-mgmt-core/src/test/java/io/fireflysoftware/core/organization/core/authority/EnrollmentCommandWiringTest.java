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

import io.fireflysoftware.core.organization.core.config.authority.OrganizationEnrollmentCommandConfiguration;
import io.fireflysoftware.core.organization.core.services.authority.*;
import io.fireflysoftware.core.organization.core.services.authority.impl.*;
import io.fireflysoftware.core.organization.models.repositories.authority.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static io.fireflysoftware.core.organization.core.authority.ConfigurationGrantObservationVerifierTest.TENANT;

class EnrollmentCommandWiringTest {
    ApplicationContextRunner configured(){
        return new EnrollmentReadWiringTest().configured(true).withUserConfiguration(OrganizationEnrollmentCommandConfiguration.class)
                .withPropertyValues("firefly.organization.enrollment.proposal-commands.enabled=true",
                        "firefly.organization.enrollment.observers.profiles[0].tenant-id="+TENANT,
                        "firefly.organization.enrollment.observers.profiles[0].registration-id=bank-observer",
                        "firefly.organization.enrollment.observers.profiles[0].issuer=https://config-idp.example.test",
                        "firefly.organization.enrollment.observers.profiles[0].client-id=observer-client",
                        "firefly.organization.enrollment.observers.profiles[0].subject=observer-machine",
                        "firefly.organization.enrollment.observers.profiles[0].audience=configuration-authority",
                        "firefly.organization.enrollment.observers.profiles[0].configuration-base-uri=https://configuration.example.test",
                        "firefly.organization.enrollment.observers.profiles[0].token-endpoint=https://config-idp.example.test/token",
                        "firefly.organization.enrollment.observers.profiles[0].jwk-set-uri=https://config-idp.example.test/jwks",
                        "firefly.organization.enrollment.observers.profiles[0].tenant-claim=tenant_id",
                        "firefly.organization.enrollment.observers.profiles[0].kind-claim=config_kind",
                        "firefly.organization.enrollment.observers.profiles[0].staff-actor-claim=config_staff",
                        "firefly.organization.enrollment.observers.profiles[0].client-claim=config_client",
                        "firefly.organization.enrollment.observers.profiles[0].staff-value=employee",
                        "firefly.organization.enrollment.observers.profiles[0].machine-value=service")
                .withBean(ReactiveOAuth2AuthorizedClientManager.class,()->mock(ReactiveOAuth2AuthorizedClientManager.class))
                .withBean(ReactiveClientRegistrationRepository.class,()->mock(ReactiveClientRegistrationRepository.class))
                .withBean(NativeEnrollmentProposalRepository.class,()->mock(NativeEnrollmentProposalRepository.class))
                .withBean(NativeEnrollmentProposalControlRepository.class,()->mock(NativeEnrollmentProposalControlRepository.class));
    }
    @Test void optInConstructsActualTypedObservationCommandAndApplicationImplementations(){configured().run(context->{assertNull(context.getStartupFailure());assertInstanceOf(BankEnrollmentProposalObservationServiceImpl.class,context.getBean(BankEnrollmentProposalObservationService.class));assertInstanceOf(BankEnrollmentProposalCommandServiceImpl.class,context.getBean(BankEnrollmentProposalCommandService.class));assertInstanceOf(BankEnrollmentProposalApplicationServiceImpl.class,context.getBean(BankEnrollmentProposalApplicationService.class));assertNotNull(context.getBean(BankEnrollmentActionReadService.class));});}
    @Test void commandsCannotStartWithoutIndependentProtectedActionReadWiring(){configured().withPropertyValues("firefly.organization.enrollment.action-read.enabled=false").run(context->assertNotNull(context.getStartupFailure()));}
    @Test void missingOrInvalidExplicitObserverProfileFailsClosedAtStartup(){configured().withPropertyValues("firefly.organization.enrollment.observers.profiles[0].jwk-set-uri=http://insecure.example.test/jwks").run(context->assertNotNull(context.getStartupFailure()));configured().withPropertyValues("firefly.organization.enrollment.observers.profiles[0].machine-value=").run(context->assertNotNull(context.getStartupFailure()));}
    @Test void defaultDisabledCommandsNeedNoCredentialsOrFinancialPorts(){new ApplicationContextRunner().withUserConfiguration(OrganizationEnrollmentCommandConfiguration.class).run(context->{assertNull(context.getStartupFailure());assertFalse(context.containsBean("bankEnrollmentProposalApplicationService"));});}
}
