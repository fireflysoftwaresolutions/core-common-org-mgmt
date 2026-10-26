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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.r2dbc.spi.*;

import org.junit.jupiter.api.Test;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;

class PreparationOwnerProjectionMappingTest {
    @Test
    void springDataDetectorFindsOwnerIdentityFragmentInFunctionalImplPackage() {
        var configuration =
                new org.springframework.data.repository.config
                        .ImplementationDetectionConfiguration() {
                    public String getImplementationPostfix() {
                        return "Impl";
                    }

                    public org.springframework.data.util.Streamable<String> getBasePackages() {
                        return org.springframework.data.util.Streamable.of(
                                "io.fireflysoftware.core.organization.models.repositories.authority");
                    }

                    public org.springframework.data.util.Streamable<
                                    org.springframework.core.type.filter.TypeFilter>
                            getExcludeFilters() {
                        return org.springframework.data.util.Streamable.empty();
                    }

                    public org.springframework.core.type.classreading.MetadataReaderFactory
                            getMetadataReaderFactory() {
                        return new org.springframework.core.type.classreading
                                .CachingMetadataReaderFactory();
                    }
                };
        var detector =
                new org.springframework.data.repository.config
                        .CustomRepositoryImplementationDetector(
                        new org.springframework.core.env.StandardEnvironment(),
                        new org.springframework.core.io.DefaultResourceLoader(),
                        configuration);
        var definition =
                detector.detectCustomImplementation(
                                configuration.forFragment(
                                        io.fireflysoftware.core.organization.models.repositories
                                                .authority.NativeBankEnrollmentOwnerIdentityReads
                                                .class
                                                .getName()))
                        .orElseThrow();
        assertEquals(
                "io.fireflysoftware.core.organization.models.repositories.authority.impl.NativeBankEnrollmentOwnerIdentityReadsImpl",
                definition.getBeanClassName());
    }

    @Test
    void actualDerivedOwnerProjectionUsesOnlyItsSevenNecessaryColumnsAndSameFactory() {
        var f = new NativeBankGateMappingTest.Fixture();
        try (var context =
                new org.springframework.context.annotation.AnnotationConfigApplicationContext()) {
            context.registerBean(ConnectionFactory.class, () -> f.factory);
            context.register(
                    io.fireflysoftware.core.organization.models.config
                            .OrganizationAuthorityRepositoryConfiguration.class,
                    io.fireflysoftware.core.organization.models.config
                            .OrganizationEnrollmentTransactionConfiguration.class);
            context.refresh();
            var repository =
                    context.getBean(
                            io.fireflysoftware.core.organization.models.repositories.authority
                                    .NativeBankEnrollmentOwnerIdentityRepository.class);
            var key =
                    new io.fireflysoftware.core.organization.models.entities.authority
                            .BankEnrollmentVersionKey(
                            NativeBankGateMappingTest.TENANT,
                            NativeBankGateMappingTest.ENROLLMENT,
                            1L);
            repository.findById(key).block();
            String sql = f.sql();
            for (String column :
                    new String[] {
                        "tenant_id",
                        "enrollment_id",
                        "version",
                        "bank_id",
                        "institution_id",
                        "issuer_id",
                        "ownership_generation_at_admission"
                    }) assertTrue(sql.contains(column), sql);
            for (String column :
                    new String[] {
                        "approved_revision_original",
                        "approved_revision_canonical",
                        "approved_revision_digest",
                        "allowed_uses",
                        "proposal_id",
                        "execution_action_id"
                    }) assertFalse(sql.contains(column), sql);
            assertSame(
                    f.factory,
                    context.getBean(
                                    "organizationAuthorityEntityOperations",
                                    R2dbcEntityTemplate.class)
                            .getDatabaseClient()
                            .getConnectionFactory());
            assertSame(
                    f.factory,
                    context.getBean(
                                    "organizationEnrollmentTransactionManager",
                                    org.springframework.r2dbc.connection.R2dbcTransactionManager
                                            .class)
                            .getConnectionFactory());
            verify(f.statement).bind(anyInt(), eq(NativeBankGateMappingTest.TENANT));
            verify(f.statement).bind(anyInt(), eq(NativeBankGateMappingTest.ENROLLMENT));
            verify(f.statement).bind(anyInt(), eq(1L));
        }
    }
}
