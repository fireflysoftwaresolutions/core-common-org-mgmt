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
package io.fireflysoftware.core.organization.core.services;

import com.fasterxml.jackson.databind.json.JsonMapper;
import io.fireflysoftware.core.organization.models.entities.*;
import io.fireflysoftware.core.organization.models.repositories.*;
import io.r2dbc.spi.*;
import org.fireflyframework.core.config.R2dbcAutoConfiguration;
import org.fireflyframework.core.config.R2dbcTransactionAutoConfiguration;
import org.fireflyframework.core.queries.PaginationResponse;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.reactivestreams.Publisher;
import org.springframework.context.annotation.*;
import org.springframework.data.domain.*;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "ORG_SCOPED_NATIVE_LEASE", matches = "approved")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ScopedOrganizationRepositoryNativeIT {
    private static final Duration WAIT = Duration.ofSeconds(15);
    private AnnotationConfigApplicationContext locker;
    private AnnotationConfigApplicationContext reader;
    private TracedFactory lockerFactory;
    private String jdbcUrl;
    private String ownerUser;
    private String ownerPassword;
    private static UUID id(int value) {
        return UUID.fromString("10000000-0000-0000-0000-" + String.format("%012d", value));
    }

    @Configuration(proxyBeanMethods = false)
    @Profile("org-scoped-native-fixture")
    @EnableR2dbcRepositories(basePackages = "io.fireflysoftware.core.organization.models.repositories",
            entityOperationsRef = "r2dbcEntityTemplate",
            excludeFilters = @ComponentScan.Filter(type = FilterType.REGEX,
                    pattern = "io\\.fireflysoftware\\.core\\.organization\\.models\\.repositories\\.authority\\..*"))
    @ComponentScan(basePackages = {"io.fireflysoftware.core.organization.core.services.impl",
            "io.fireflysoftware.core.organization.core.mappers"})
    static class OrdinaryConfiguration {
        @Bean R2dbcEntityTemplate r2dbcEntityTemplate(ConnectionFactory factory) {
            return new R2dbcEntityTemplate(factory);
        }
    }

    record ScopeCase(Class<?> entity, Class<?> repository, String scope, String property,
            int parent, int otherParent, int local, int foreign, int count) { }
    static Stream<ScopeCase> scopes() {
        return Stream.of(
                new ScopeCase(WorkingCalendar.class, WorkingCalendarRepository.class, "Bank", "bankId", 1, 2, 61, 63, 2),
                new ScopeCase(CalendarAssignment.class, CalendarAssignmentRepository.class, "Calendar", "calendarId", 61, 62, 71, 73, 2),
                new ScopeCase(Branch.class, BranchRepository.class, "Bank", "bankId", 1, 2, 31, 34, 3),
                new ScopeCase(BankRegion.class, BankRegionRepository.class, "Division", "divisionId", 11, 12, 21, 23, 2),
                new ScopeCase(BranchDepartment.class, BranchDepartmentRepository.class, "Branch", "branchId", 31, 32, 41, 43, 2),
                new ScopeCase(BranchPosition.class, BranchPositionRepository.class, "Department", "departmentId", 41, 42, 51, 53, 2),
                new ScopeCase(BranchHours.class, BranchHoursRepository.class, "Branch", "branchId", 31, 32, 81, 83, 2));
    }

    @BeforeAll void actualOwnedClusterAndContexts() throws Exception {
        Path run = Path.of(required("ORG_SCOPED_RUN_DIR")).toRealPath();
        var marker = new JsonMapper().readTree(run.resolve("owner.json").toFile());
        jdbcUrl = required("ORG_SCOPED_JDBC_URL");
        ownerUser = required("ORG_SCOPED_OWNER_USER");
        ownerPassword = required("ORG_SCOPED_OWNER_PASSWORD");
        assertEquals("org_scoped_fixture_owner", ownerUser);
        assertEquals("jdbc:postgresql://127.0.0.1:" + marker.get("port").intValue() + "/org_scoped", jdbcUrl);
        try (var connection = owner(); var statement = connection.createStatement();
                var row = statement.executeQuery("SELECT current_setting('cluster_name'),current_setting('data_directory'),system_identifier::text FROM pg_control_system()")) {
            assertTrue(row.next());
            assertEquals(marker.get("clusterName").textValue(), row.getString(1));
            assertEquals(run.resolve("data").toRealPath(), Path.of(row.getString(2)).toRealPath());
            assertEquals(marker.get("systemIdentifier").textValue(), row.getString(3));
        }
        lockerFactory = factory("org_scoped_locker", required("ORG_SCOPED_LOCKER_PASSWORD"), marker.get("port").intValue());
        locker = context(lockerFactory);
        reader = context(factory("org_scoped_reader", required("ORG_SCOPED_READER_PASSWORD"), marker.get("port").intValue()));
        var operations = locker.getBean("r2dbcEntityTemplate", R2dbcEntityTemplate.class);
        assertSame(lockerFactory, operations.getDatabaseClient().getConnectionFactory());
        assertSame(lockerFactory, locker.getBean(R2dbcTransactionManager.class).getConnectionFactory());
        Map<String, Object> origins = new LinkedHashMap<>();
        for (Class<?> type : List.of(
                io.fireflysoftware.core.organization.core.services.impl.WorkingCalendarServiceImpl.class,
                io.fireflysoftware.core.organization.core.services.impl.CalendarAssignmentServiceImpl.class,
                io.fireflysoftware.core.organization.models.repositories.scoped.impl.WorkingCalendarScopedRepositoryImpl.class,
                io.fireflysoftware.core.organization.models.repositories.scoped.impl.CalendarAssignmentScopedRepositoryImpl.class,
                R2dbcEntityTemplate.class, R2dbcTransactionManager.class,
                org.springframework.data.r2dbc.repository.support.R2dbcRepositoryFactory.class)) {
            try (var original = type.getResourceAsStream("/" + type.getName().replace('.', '/') + ".class")) {
                assertNotNull(original);
                String hash = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(original.readAllBytes()));
                origins.put(type.getName(), Map.of("classSha256", hash,
                        "origin", type.getProtectionDomain().getCodeSource().getLocation().toString()));
            }
        }
        new JsonMapper().writeValue(run.resolve("class-origins.json").toFile(), origins);
    }

    @AfterAll void closeContexts() {
        if (reader != null) reader.close();
        if (locker != null) locker.close();
    }

    @BeforeEach void restoreOwnedParentFixture() throws Exception {
        try (var connection = owner(); var update = connection.prepareStatement("UPDATE public.working_calendar SET bank_id=? WHERE id=?")) {
            update.setObject(1, id(1)); update.setObject(2, id(61)); assertEquals(1, update.executeUpdate());
        }
        lockerFactory.events.clear(); lockerFactory.gate.set(null);
    }

    @ParameterizedTest @MethodSource("scopes")
    void actualScopedDataAndCountKeepMandatoryParentAndGroupedClient(ScopeCase scope) throws Exception {
        var repository = locker.getBean(scope.repository());
        var data = scope.repository().getMethod("findPageFor" + scope.scope(), UUID.class, Criteria.class, Pageable.class);
        var count = scope.repository().getMethod("countFor" + scope.scope(), UUID.class, Criteria.class);
        for (Criteria client : Arrays.asList(null, Criteria.empty())) {
            var page = (Flux<?>) data.invoke(repository, id(scope.parent()), client, PageRequest.of(0, 1, Sort.by("id")));
            var total = (Mono<?>) count.invoke(repository, id(scope.parent()), client);
            assertEquals(1, page.collectList().block(WAIT).size());
            assertEquals((long) scope.count(), total.block(WAIT));
        }
        var conflicting = Criteria.where(scope.property()).is(id(scope.otherParent()));
        assertEquals(0, ((Flux<?>) data.invoke(repository, id(scope.parent()), conflicting, PageRequest.of(0, 5))).collectList().block(WAIT).size());
        assertEquals(0L, ((Mono<?>) count.invoke(repository, id(scope.parent()), conflicting)).block(WAIT));
        var grouped = Criteria.where("id").is(id(scope.foreign())).or("id").is(id(scope.local()));
        var rows = ((Flux<?>) data.invoke(repository, id(scope.parent()), grouped, PageRequest.of(0, 5))).collectList().block(WAIT);
        assertEquals(1, rows.size());
        assertEquals(id(scope.local()), scope.entity().getMethod("getId").invoke(rows.getFirst()));
        assertEquals(1L, ((Mono<?>) count.invoke(repository, id(scope.parent()), grouped)).block(WAIT));
    }

    @Test void actualServicePaginationAndExplicitMissingParentSemantics() {
        var service = locker.getBean(CalendarAssignmentService.class);
        var page = service.filterCalendarAssignmentsForCalendar(id(1), id(61), null).block(WAIT);
        assertEquals(2, page.getContent().size()); assertEquals(2L, page.getTotalElements());
        lockerFactory.events.clear();
        var missing = assertThrows(RuntimeException.class, () -> service.filterCalendarAssignmentsForCalendar(id(98), id(61), null).block(WAIT));
        assertEquals("Bank not found with ID: " + id(98), missing.getMessage());
        var mismatch = assertThrows(RuntimeException.class, () -> service.filterCalendarAssignmentsForCalendar(id(2), id(61), null).block(WAIT));
        assertEquals("Calendar not found for bank with ID: " + id(2), mismatch.getMessage());
        assertTrue(lockerFactory.events.stream().noneMatch(event -> event.sql().contains("calendar_assignment")));
    }

    @Test void exactReaderAndLockPrivilegesAreNotFinancialAuthority() throws Exception {
        assertEquals(2, reader.getBean(WorkingCalendarRepository.class)
                .findPageForBank(id(1), Criteria.empty(), PageRequest.of(0, 5)).collectList().block(WAIT).size());
        var denied = assertThrows(RuntimeException.class, () -> reader.getBean(WorkingCalendarRepository.class)
                .findByIdAndBankId(id(61), id(1)).block(WAIT));
        Throwable cause = denied; while (cause.getCause() != null) cause = cause.getCause();
        assertInstanceOf(R2dbcException.class, cause); assertEquals("42501", ((R2dbcException) cause).getSqlState());
        assertNotNull(locker.getBean(WorkingCalendarService.class).getWorkingCalendarByIdForBank(id(1), id(61)).block(WAIT));
        try (var connection = owner(); var statement = connection.createStatement()) {
            try (var rows = statement.executeQuery("SELECT rolname,rolsuper,rolinherit,rolcreaterole,rolcreatedb,rolbypassrls FROM pg_roles WHERE rolname IN ('org_scoped_reader','org_scoped_locker')")) {
                int found = 0; while (rows.next()) { found++; for (int i = 2; i <= 6; i++) assertFalse(rows.getBoolean(i)); } assertEquals(2, found);
            }
            for (String role : List.of("org_scoped_reader", "org_scoped_locker")) {
                for (String table : List.of("bank", "bank_division", "bank_region", "branch", "branch_department", "branch_position", "working_calendar", "calendar_assignment", "branch_hours")) {
                    assertTrue(flag(statement, "SELECT has_table_privilege('" + role + "','public." + table + "','SELECT')"));
                    for (String privilege : List.of("INSERT", "DELETE", "TRUNCATE", "REFERENCES", "UPDATE", "TRIGGER", "MAINTAIN"))
                        assertFalse(flag(statement, "SELECT has_table_privilege('" + role + "','public." + table + "','" + privilege + "')"));
                }
                for (String table : List.of("bank_holiday", "bank_audit_log", "branch_audit_log"))
                    assertFalse(flag(statement, "SELECT has_table_privilege('" + role + "','public." + table + "','SELECT')"));
                assertFalse(flag(statement, "SELECT has_database_privilege('" + role + "','org_scoped','CREATE')"));
                assertFalse(flag(statement, "SELECT has_database_privilege('" + role + "','org_scoped','TEMPORARY')"));
                assertFalse(flag(statement, "SELECT has_schema_privilege('" + role + "','public','CREATE')"));
                assertFalse(flag(statement, "SELECT EXISTS(SELECT 1 FROM pg_auth_members WHERE member='" + role + "'::regrole)"));
            }
            for (String table : List.of("working_calendar", "bank_division", "branch_department")) {
                assertTrue(flag(statement, "SELECT has_column_privilege('org_scoped_locker','public." + table + "','updated_at','UPDATE')"));
                assertFalse(flag(statement, "SELECT has_column_privilege('org_scoped_reader','public." + table + "','updated_at','UPDATE')"));
            }
            assertFalse(flag(statement, "SELECT has_column_privilege('org_scoped_locker','public.working_calendar','bank_id','UPDATE')"));
            assertFalse(flag(statement, "SELECT has_column_privilege('org_scoped_locker','public.bank_division','bank_id','UPDATE')"));
            assertFalse(flag(statement, "SELECT has_column_privilege('org_scoped_locker','public.branch_department','branch_id','UPDATE')"));
            assertFalse(flag(statement, "SELECT EXISTS(SELECT 1 FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='public' AND c.relname LIKE 'org_native_%')"));
        }
    }

    @Test void relatedParentLookupsUseActualShareLocksAndExactAncestor() {
        assertNotNull(locker.getBean(BankDivisionService.class).getBankDivisionByIdForBank(id(1), id(11)).block(WAIT));
        assertNotNull(locker.getBean(BranchDepartmentService.class).getBranchDepartmentByIdForBranch(id(31), id(41)).block(WAIT));
        assertThrows(RuntimeException.class, () -> locker.getBean(BankDivisionService.class).getBankDivisionByIdForBank(id(2), id(11)).block(WAIT));
        assertThrows(RuntimeException.class, () -> locker.getBean(BranchDepartmentService.class).getBranchDepartmentByIdForBranch(id(32), id(41)).block(WAIT));
        assertTrue(lockerFactory.events.stream().anyMatch(event -> event.sql().contains("FOR SHARE OF \"bank_division\"")));
        assertTrue(lockerFactory.events.stream().anyMatch(event -> event.sql().contains("FOR SHARE OF \"branch_department\"")));
    }

    @Test void actualParentShareLockSurvivesDataAndCountUntilCommit() throws Exception { lockRace(false); }
    @Test void actualParentShareLockSurvivesDataAndCountUntilRollback() throws Exception { lockRace(true); }

    private void lockRace(boolean rollback) throws Exception {
        var gate = new Gate(); lockerFactory.gate.set(gate);
        Mono<PaginationResponse<io.fireflysoftware.core.organization.interfaces.dtos.CalendarAssignmentDTO>> operation =
                locker.getBean(CalendarAssignmentService.class).filterCalendarAssignmentsForCalendar(id(1), id(61), null);
        if (rollback) operation = operation.flatMap(page -> Mono.<PaginationResponse<io.fireflysoftware.core.organization.interfaces.dtos.CalendarAssignmentDTO>>error(new IllegalStateException("owned-scoped-rollback")))
                .as(TransactionalOperator.create(locker.getBean(R2dbcTransactionManager.class))::transactional);
        var read = operation.toFuture();
        try (var writer = owner(); var observer = owner(); var executor = Executors.newSingleThreadExecutor()) {
            int pid; try (var statement = writer.createStatement(); var row = statement.executeQuery("SELECT pg_backend_pid()")) { row.next(); pid = row.getInt(1); }
            gate.dataEntered.asMono().block(WAIT); gate.countEntered.asMono().block(WAIT);
            var write = executor.submit(() -> {
                try (var statement = writer.createStatement()) { statement.execute("SET lock_timeout='10s'"); }
                try (var update = writer.prepareStatement("UPDATE public.working_calendar SET bank_id=? WHERE id=?")) {
                    update.setObject(1, id(2)); update.setObject(2, id(61)); return update.executeUpdate();
                }
            });
            awaitBlocked(observer, pid); assertFalse(write.isDone());
            gate.dataRelease.tryEmitEmpty(); gate.dataFinished.asMono().block(WAIT);
            awaitBlocked(observer, pid); assertFalse(write.isDone());
            gate.countRelease.tryEmitEmpty();
            if (rollback) {
                var failure = assertThrows(ExecutionException.class, () -> read.get(15, TimeUnit.SECONDS));
                assertEquals("owned-scoped-rollback", failure.getCause().getMessage());
            } else {
                var page = read.get(15, TimeUnit.SECONDS); assertEquals(2, page.getContent().size()); assertEquals(2L, page.getTotalElements());
            }
            assertEquals(1, write.get(15, TimeUnit.SECONDS));
            assertEquals(1, lockerFactory.events.stream().map(Event::connection).distinct().count());
            assertTrue(lockerFactory.events.stream().anyMatch(event -> event.sql().contains("FOR SHARE OF \"working_calendar\"")));
            assertTrue(lockerFactory.events.stream().anyMatch(event -> event.operation().equals(rollback ? "rollbackTransaction" : "commitTransaction")));
        } finally {
            gate.dataRelease.tryEmitEmpty(); gate.countRelease.tryEmitEmpty(); lockerFactory.gate.set(null);
        }
        try (var connection = owner(); var query = connection.prepareStatement("SELECT bank_id FROM public.working_calendar WHERE id=?")) {
            query.setObject(1, id(61)); try (var row = query.executeQuery()) { assertTrue(row.next()); assertEquals(id(2), row.getObject(1, UUID.class)); }
        }
    }

    private static void awaitBlocked(java.sql.Connection observer, int pid) throws Exception {
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < until) {
            try (var query = observer.prepareStatement("SELECT cardinality(pg_blocking_pids(pid))>0 FROM pg_stat_activity WHERE pid=? AND wait_event_type='Lock'")) {
                query.setInt(1, pid); try (var row = query.executeQuery()) { if (row.next() && row.getBoolean(1)) return; }
            }
            Thread.sleep(10);
        }
        fail("Owned parent writer did not exhibit a PostgreSQL lock wait");
    }

    private static boolean flag(java.sql.Statement statement, String sql) throws Exception {
        try (var row = statement.executeQuery(sql)) { assertTrue(row.next()); return row.getBoolean(1); }
    }
    private java.sql.Connection owner() throws Exception { return DriverManager.getConnection(jdbcUrl, ownerUser, ownerPassword); }
    private static String required(String name) { return Objects.requireNonNull(System.getenv(name), "Owned fixture setting required: " + name); }
    private static TracedFactory factory(String user, String password, int port) {
        var options = ConnectionFactoryOptions.builder().option(ConnectionFactoryOptions.DRIVER, "postgresql")
                .option(ConnectionFactoryOptions.HOST, "127.0.0.1").option(ConnectionFactoryOptions.PORT, port)
                .option(ConnectionFactoryOptions.DATABASE, "org_scoped").option(ConnectionFactoryOptions.USER, user)
                .option(ConnectionFactoryOptions.PASSWORD, password).build();
        return new TracedFactory(ConnectionFactories.get(options));
    }
    private static AnnotationConfigApplicationContext context(TracedFactory factory) {
        var context = new AnnotationConfigApplicationContext();
        context.getEnvironment().setActiveProfiles("org-scoped-native-fixture");
        context.registerBean(ConnectionFactory.class, () -> factory);
        context.register(OrdinaryConfiguration.class,
                R2dbcAutoConfiguration.class, R2dbcTransactionAutoConfiguration.class);
        context.refresh(); return context;
    }

    record Event(int connection, String operation, String sql) { }
    static final class Gate {
        final Sinks.One<Void> dataEntered = Sinks.one(), countEntered = Sinks.one();
        final Sinks.One<Void> dataRelease = Sinks.one(), countRelease = Sinks.one(), dataFinished = Sinks.one();
    }
    static final class TracedFactory implements ConnectionFactory {
        private final ConnectionFactory delegate;
        private final AtomicInteger sequence = new AtomicInteger();
        final List<Event> events = new CopyOnWriteArrayList<>();
        final AtomicReference<Gate> gate = new AtomicReference<>();
        TracedFactory(ConnectionFactory delegate) { this.delegate = delegate; }
        public ConnectionFactoryMetadata getMetadata() { return delegate.getMetadata(); }
        public Publisher<? extends io.r2dbc.spi.Connection> create() {
            return Mono.from(delegate.create()).map(connection -> {
                int identity = sequence.incrementAndGet();
                return (io.r2dbc.spi.Connection) Proxy.newProxyInstance(getClass().getClassLoader(),
                        new Class<?>[]{io.r2dbc.spi.Connection.class}, (proxy, method, args) -> {
                            Object result = invoke(connection, method, args);
                            if (method.getName().equals("createStatement")) return statement((Statement) result, identity, (String) args[0]);
                            if (Set.of("beginTransaction", "commitTransaction", "rollbackTransaction", "close").contains(method.getName()))
                                events.add(new Event(identity, method.getName(), ""));
                            return result;
                        });
            });
        }
        private Statement statement(Statement statement, int identity, String sql) {
            return (Statement) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Statement.class},
                    (proxy, method, args) -> {
                        Object result = invoke(statement, method, args);
                        if (method.getName().equals("execute")) {
                            events.add(new Event(identity, "execute", sql));
                            Gate current = gate.get();
                            boolean assignment = sql.contains("\"calendar_assignment\"");
                            boolean count = sql.toUpperCase(Locale.ROOT).startsWith("SELECT COUNT(");
                            @SuppressWarnings("unchecked") var results = (Publisher<? extends Result>) result;
                            if (current == null || !assignment) return results;
                            (count ? current.countEntered : current.dataEntered).tryEmitEmpty();
                            return (count ? current.countRelease : current.dataRelease).asMono().thenMany(
                                    Flux.from(results).map(row -> observed(row, current, count)));
                        }
                        return result == statement ? proxy : result;
                    });
        }
        private Result observed(Result result, Gate current, boolean count) {
            return (Result) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Result.class},
                    (proxy, method, args) -> {
                        Object value = invoke(result, method, args);
                        if (!count && value instanceof Publisher<?> publisher)
                            return Flux.from(publisher).doOnComplete(() -> current.dataFinished.tryEmitEmpty());
                        return value;
                    });
        }
        private static Object invoke(Object target, Method method, Object[] args) throws Throwable {
            try { return method.invoke(target, args); }
            catch (InvocationTargetException failure) { throw failure.getCause(); }
        }
    }
}
