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

import io.fireflysoftware.core.organization.core.config.authority.OrganizationEnrollmentActionReadConfiguration;
import io.fireflysoftware.core.organization.core.services.authority.BankEnrollmentActionReadService;
import io.fireflysoftware.core.organization.core.services.authority.impl.BankEnrollmentActionReadServiceImpl;
import io.fireflysoftware.core.organization.models.repositories.authority.*;
import io.r2dbc.spi.ConnectionFactory;
import org.fireflyframework.security.spi.*;
import org.fireflyframework.security.rs.ResourceServerProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.r2dbc.core.R2dbcEntityOperations;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import reactor.core.publisher.Mono;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static io.fireflysoftware.core.organization.core.authority.OrganizationEnrollmentAuthorizationTest.*;
import static io.fireflysoftware.core.organization.core.authority.OrganizationProposalAuthorizationTest.REALM;

class EnrollmentReadWiringTest {
    ApplicationContextRunner configured(boolean tenantRequired){
        var factory=mock(ConnectionFactory.class);when(factory.getMetadata()).thenReturn(()->"PostgreSQL");var template=new R2dbcEntityTemplate(factory);
        var properties=new ResourceServerProperties();properties.setTenantRequired(tenantRequired);properties.setTenantClaimPaths(List.of("tenant_id"));properties.setIssuer(IDP);properties.setAudiences(List.of("org-inbound"));properties.setStrictPolicy(true);
        return new ApplicationContextRunner().withUserConfiguration(OrganizationEnrollmentActionReadConfiguration.class)
                .withPropertyValues("firefly.organization.enrollment.action-read.enabled=true","firefly.organization.enrollment.platform-tenant-id="+PLATFORM,
                        "firefly.organization.enrollment.caller-profiles[0].issuer="+IDP,"firefly.organization.enrollment.caller-profiles[0].tenant-claim=tenant_id",
                        "firefly.organization.enrollment.caller-profiles[0].kind-claim=org_kind","firefly.organization.enrollment.caller-profiles[0].staff-actor-claim=org_staff",
                        "firefly.organization.enrollment.caller-profiles[0].client-claim=org_client","firefly.organization.enrollment.caller-profiles[0].staff-value=employee",
                        "firefly.organization.enrollment.caller-profiles[0].machine-value=service","firefly.organization.enrollment.caller-profiles[0].staff-realm="+REALM)
                .withBean(ResourceServerProperties.class,()->properties).withBean(SecurityContextPort.class,()->()->Mono.empty())
                .withBean(PolicyDecisionPort.class,()->(p,a,r,c)->Mono.just(org.fireflyframework.security.api.domain.Decision.deny("source-unit")))
                .withBean("organizationAuthorityEntityOperations",R2dbcEntityOperations.class,()->template)
                .withBean("organizationEnrollmentTransactionManager",R2dbcTransactionManager.class,()->new R2dbcTransactionManager(factory))
                .withBean(NativeEnrollmentContextRepository.class,()->mock(NativeEnrollmentContextRepository.class))
                .withBean(NativeEnrollmentActionRepository.class,()->mock(NativeEnrollmentActionRepository.class))
                .withBean(NativeEnrollmentReceiptRepository.class,()->mock(NativeEnrollmentReceiptRepository.class))
                .withBean(NativeEnrollmentRevisionRepository.class,()->mock(NativeEnrollmentRevisionRepository.class));
    }
    @Test void explicitOptInBindsActualServiceWithRequiredSharedTenantProjection(){configured(true).run(context->{assertNull(context.getStartupFailure());assertInstanceOf(BankEnrollmentActionReadServiceImpl.class,context.getBean(BankEnrollmentActionReadService.class));});}
    @Test void missingSharedRequiredProjectionFailsStartupInsteadOfRepairingCallerTenant(){configured(false).run(context->assertNotNull(context.getStartupFailure()));}
    @Test void defaultDisabledModuleDoesNotRequireFinancialSecurityBeans(){new ApplicationContextRunner().withUserConfiguration(OrganizationEnrollmentActionReadConfiguration.class).run(context->{assertNull(context.getStartupFailure());assertFalse(context.containsBean("bankEnrollmentActionReadService"));});}
}
