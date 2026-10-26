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
import io.fireflysoftware.core.organization.models.config.OrganizationEnrollmentTransactionConfiguration;
import io.fireflysoftware.core.organization.models.entities.authority.*;
import io.fireflysoftware.core.organization.models.repositories.*;
import io.fireflysoftware.core.organization.models.repositories.authority.*;
import io.fireflysoftware.core.organization.models.repositories.scoped.*;
import io.r2dbc.spi.*;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.type.classreading.CachingMetadataReaderFactory;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.core.type.filter.TypeFilter;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.data.repository.config.CustomRepositoryImplementationDetector;
import org.springframework.data.repository.config.ImplementationDetectionConfiguration;
import org.springframework.data.util.Streamable;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RepositoryFragmentImplementationDiscoveryTest {
    private static final UUID ID = UUID.fromString("00000000-0000-0000-0000-000000000091");
    private static final UUID TENANT = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private record Fragment(Class<?> contract, Class<?> repository, String parent, String table) { }

    private static final List<Fragment> FRAGMENTS = List.of(
            new Fragment(BankRegionScopedRepository.class, BankRegionRepository.class, "Division", "bank_region"),
            new Fragment(BranchDepartmentScopedRepository.class, BranchDepartmentRepository.class, "Branch", "branch_department"),
            new Fragment(BranchHoursScopedRepository.class, BranchHoursRepository.class, "Branch", "branch_hours"),
            new Fragment(BranchPositionScopedRepository.class, BranchPositionRepository.class, "Department", "branch_position"),
            new Fragment(BranchScopedRepository.class, BranchRepository.class, "Bank", "branch"),
            new Fragment(CalendarAssignmentScopedRepository.class, CalendarAssignmentRepository.class, "Calendar", "calendar_assignment"),
            new Fragment(WorkingCalendarScopedRepository.class, WorkingCalendarRepository.class, "Bank", "working_calendar"),
            new Fragment(NativeBankGateMutations.class, NativeBankGateRepository.class, null, "org_native_bank_gate"),
            new Fragment(NativeEnrollmentProposalControlMutations.class, NativeEnrollmentProposalControlRepository.class, null, "org_native_proposal_control"),
            new Fragment(NativeBankEnrollmentOwnerIdentityReads.class, NativeBankEnrollmentOwnerIdentityRepository.class, null, "org_native_bank_enrollment_version"));

    @TestFactory
    Stream<DynamicTest> springDataDetectorResolvesEveryFragmentInItsImplSubpackage() {
        return FRAGMENTS.stream().map(fragment -> DynamicTest.dynamicTest(fragment.contract().getSimpleName(), () -> {
            var configuration = new ImplementationDetectionConfiguration() {
                public String getImplementationPostfix() { return "Impl"; }
                public Streamable<String> getBasePackages() { return Streamable.of(fragment.contract().getPackageName()); }
                public Streamable<TypeFilter> getExcludeFilters() { return Streamable.empty(); }
                public MetadataReaderFactory getMetadataReaderFactory() { return new CachingMetadataReaderFactory(); }
            };
            var detector = new CustomRepositoryImplementationDetector(new StandardEnvironment(), new DefaultResourceLoader(), configuration);
            var definition = detector.detectCustomImplementation(configuration.forFragment(fragment.contract().getName())).orElseThrow();
            assertEquals(fragment.contract().getPackageName() + ".impl." + fragment.contract().getSimpleName() + "Impl", definition.getBeanClassName());
        }));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableR2dbcRepositories(basePackages = "io.fireflysoftware.core.organization.models.repositories",
            entityOperationsRef = "r2dbcEntityTemplate",
            excludeFilters = @ComponentScan.Filter(type = FilterType.REGEX,
                    pattern = "io\\.fireflysoftware\\.core\\.organization\\.models\\.repositories\\.authority\\..*"))
    static class OrdinaryRepositories { }

    @TestFactory
    Stream<DynamicTest> actualRepositoryProxiesComposeEveryFragmentUsingTheSameFactory() {
        return FRAGMENTS.stream().map(fragment -> DynamicTest.dynamicTest(fragment.repository().getSimpleName(), () -> {
            var factory = mock(ConnectionFactory.class);
            var connection = mock(Connection.class);
            var statement = mock(Statement.class, RETURNS_SELF);
            when(factory.getMetadata()).thenReturn(() -> "PostgreSQL");
            when(factory.create()).thenAnswer(invocation -> Mono.just(connection));
            when(connection.close()).thenReturn(Mono.empty());
            when(connection.createStatement(anyString())).thenReturn(statement);
            when(statement.execute()).thenReturn(Flux.empty());
            try (var context = new AnnotationConfigApplicationContext()) {
                context.registerBean(ConnectionFactory.class, () -> factory);
                context.register(OrdinaryRepositories.class, OrganizationAuthorityRepositoryConfiguration.class,
                        OrganizationEnrollmentTransactionConfiguration.class);
                context.refresh();
                var operations = context.getBean("organizationAuthorityEntityOperations", R2dbcEntityTemplate.class);
                assertSame(operations, context.getBean("r2dbcEntityTemplate"));
                assertSame(factory, operations.getDatabaseClient().getConnectionFactory());
                assertSame(factory, context.getBean("organizationEnrollmentTransactionManager", R2dbcTransactionManager.class).getConnectionFactory());
                Object repository = context.getBean(fragment.repository());
                if (fragment.parent() != null) {
                    var find = fragment.contract().getMethod("findPageFor" + fragment.parent(), UUID.class, Criteria.class, org.springframework.data.domain.Pageable.class);
                    var count = fragment.contract().getMethod("countFor" + fragment.parent(), UUID.class, Criteria.class);
                    ((Flux<?>) find.invoke(repository, ID, Criteria.empty(), PageRequest.of(0, 5))).collectList().block();
                    ((Mono<?>) count.invoke(repository, ID, Criteria.empty())).block();
                } else if (repository instanceof NativeBankGateMutations mutations) {
                    var before = new BankGateRow(ID, 0, 0, 0, null, null, null, null, null, null, null);
                    var after = new BankGateRow(ID, 1, 1, 0, TENANT, ID, ID, ID, 1L, ID, TENANT);
                    mutations.compareAndSetOwnership(before, after).block();
                } else if (repository instanceof NativeEnrollmentProposalControlMutations mutations) {
                    var key = new EnrollmentProposalKey(TENANT, ID);
                    var before = new EnrollmentProposalControlRow(key, "DRAFT", 1, 0, null, null, ID);
                    var after = new EnrollmentProposalControlRow(key, "SUBMITTED", 1, 1, null, null, ID);
                    mutations.compareAndSet(before, after).block();
                } else {
                    ((NativeBankEnrollmentOwnerIdentityReads) repository).findById(new BankEnrollmentVersionKey(TENANT, ID, 1L)).block();
                }
                var captured = org.mockito.ArgumentCaptor.forClass(String.class);
                verify(connection, times(fragment.parent() == null ? 1 : 2)).createStatement(captured.capture());
                assertTrue(captured.getAllValues().stream().allMatch(sql -> sql.contains(fragment.table())), captured.getAllValues().toString());
                verify(statement, atLeastOnce()).bind(anyInt(), eq(ID));
            }
        }));
    }
}
