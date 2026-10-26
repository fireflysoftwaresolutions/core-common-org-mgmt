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

import io.fireflysoftware.core.organization.core.config.authority.OrganizationEnrollmentPreparationConfiguration;
import io.fireflysoftware.core.organization.core.config.authority.OrganizationEnrollmentActionReadConfiguration;
import io.fireflysoftware.core.organization.models.repositories.BankRepository;
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

class PreparationWiringTest {
    ApplicationContextRunner configured(){return new EnrollmentCommandWiringTest().configured().withUserConfiguration(OrganizationEnrollmentPreparationConfiguration.class)
            .withBean(BankRepository.class,()->mock(BankRepository.class)).withBean(NativeBankGateRepository.class,()->mock(NativeBankGateRepository.class))
            .withBean(NativeBankEnrollmentOwnerIdentityRepository.class,()->mock(NativeBankEnrollmentOwnerIdentityRepository.class)).withBean(NativeBankEnrollmentControlRepository.class,()->mock(NativeBankEnrollmentControlRepository.class))
            .withBean(NativeEnrollmentPreparationContextRepository.class,()->mock(NativeEnrollmentPreparationContextRepository.class));}
    @Test void actualPreparationInterfacesBindTheirFunctionalImplementationsOnlyWithStrictExistingSecurity(){configured().run(c->{assertNull(c.getStartupFailure());assertInstanceOf(BankEnrollmentPreparationCaptureServiceImpl.class,c.getBean(BankEnrollmentPreparationCaptureService.class));assertInstanceOf(BankEnrollmentPreparationObservationServiceImpl.class,c.getBean(BankEnrollmentPreparationObservationService.class));assertInstanceOf(BankEnrollmentPreparationCommandServiceImpl.class,c.getBean(BankEnrollmentPreparationCommandService.class));assertInstanceOf(BankEnrollmentPreparationServiceImpl.class,c.getBean(BankEnrollmentPreparationService.class));assertInstanceOf(BankEnrollmentPreparationRecoveryServiceImpl.class,c.getBean(BankEnrollmentPreparationRecoveryService.class));});}
    @Test void preparationCannotBootWithMissingSharedVerifiedTenantProjection(){new EnrollmentReadWiringTest().configured(false).withUserConfiguration(OrganizationEnrollmentPreparationConfiguration.class).withPropertyValues("firefly.organization.enrollment.proposal-commands.enabled=true").run(c->assertNotNull(c.getStartupFailure()));}
    @Test void recoveryReadNeedsNoCommandCredentialsOrWritablePreparationPorts(){new EnrollmentReadWiringTest().configured(true).run(c->{assertNull(c.getStartupFailure());assertInstanceOf(BankEnrollmentPreparationRecoveryServiceImpl.class,c.getBean(BankEnrollmentPreparationRecoveryService.class));assertFalse(c.containsBean("bankEnrollmentPreparationService"));assertFalse(c.containsBean("bankEnrollmentPreparationObservationService"));});}
    @Test void defaultDisabledPreparationDoesNotRequireAnyNativePortOrCredential(){new ApplicationContextRunner().withUserConfiguration(OrganizationEnrollmentPreparationConfiguration.class,OrganizationEnrollmentActionReadConfiguration.class).run(c->{assertNull(c.getStartupFailure());assertFalse(c.containsBean("bankEnrollmentPreparationService"));assertFalse(c.containsBean("bankEnrollmentPreparationRecoveryService"));});}
}
