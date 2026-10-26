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

import static io.fireflysoftware.core.organization.core.authority.OwnedBankEnrollmentFixture.*;

import static org.junit.jupiter.api.Assertions.*;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@EnabledIfEnvironmentVariable(named = "ORG_FLYWAY_NATIVE_LEASE", matches = "approved")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class BankFlywayStartupNativeIT {
    private static final List<String> DATABASES =
            List.of("org_bank_native", "org_bank_fresh", "org_bank_race", "org_bank_baseline");
    private static final UUID RETAINED_AUDIT =
            UUID.fromString("10000000-0000-0000-0000-000000009001");

    private Path run() {
        return Path.of(required("ORG_BANK_NATIVE_RUN_DIR"));
    }

    private String jdbc(String database) {
        assertTrue(DATABASES.contains(database));
        return "jdbc:postgresql://127.0.0.1:" + required("ORG_FLYWAY_NATIVE_PORT") + "/" + database;
    }

    private Flyway flyway(String database, String target, String location) {
        return BankFlywayFixtureConfiguration.positive(
                        database,
                        Integer.parseInt(required("ORG_FLYWAY_NATIVE_PORT")),
                        target,
                        location,
                        System.getenv())
                .load();
    }

    private Flyway flyway(String database) {
        return flyway(database, null, "classpath:db/migration");
    }

    private Connection admin(String database) throws Exception {
        return DriverManager.getConnection(
                jdbc(database),
                required("ORG_BANK_NATIVE_ADMIN_USER"),
                required("ORG_BANK_NATIVE_ADMIN_PASSWORD"));
    }

    private long count(String database, String sql) throws Exception {
        try (var connection = admin(database);
                var statement = connection.createStatement();
                var rows = statement.executeQuery(sql)) {
            assertTrue(rows.next());
            return rows.getLong(1);
        }
    }

    @Test
    @Order(1)
    void prepareActualOriginalFourDatabasesAndRetainedOrdinaryHistory() throws Exception {
        for (String database : List.of("org_bank_native", "org_bank_race", "org_bank_baseline")) {
            assertEquals(
                    4,
                    flyway(database, "4", "classpath:db/migration").migrate().migrationsExecuted);
            assertEquals(
                    4,
                    count(
                            database,
                            "SELECT count(*) FROM public.flyway_schema_history WHERE success"));
            assertEquals(
                    0,
                    count(
                            database,
                            "SELECT count(*) FROM pg_catalog.pg_class c JOIN"
                                    + " pg_catalog.pg_namespace n ON n.oid=c.relnamespace WHERE"
                                    + " n.nspname='public' AND c.relname LIKE 'org_native_%'"));
        }
        try (var connection = admin("org_bank_native");
                var statement =
                        connection.prepareStatement(
                                "INSERT INTO public.bank(id,code,name,is_active,created_at,created_by)"
                                        + " VALUES(?,?,?,true,?,?)")) {
            statement.setObject(1, id(900));
            statement.setString(2, "retained-upgrade-bank");
            statement.setString(3, "Retained ordinary upgrade Bank");
            statement.setObject(4, java.time.LocalDateTime.of(2026, 10, 1, 12, 0));
            statement.setObject(5, id(901));
            assertEquals(1, statement.executeUpdate());
        }
        try (var connection = admin("org_bank_native");
                var statement =
                        connection.prepareStatement(
                                "INSERT INTO"
                                    + " public.bank_audit_log(id,bank_id,action,entity,entity_id,timestamp)"
                                    + " VALUES(?,?,CAST(? AS public.audit_action_enum),?,?,?)")) {
            statement.setObject(1, RETAINED_AUDIT);
            statement.setObject(2, id(900));
            statement.setString(3, "CREATED");
            statement.setString(4, "Bank");
            statement.setString(5, id(900).toString());
            statement.setObject(6, java.time.LocalDateTime.of(2026, 10, 1, 12, 1));
            assertEquals(1, statement.executeUpdate());
        }
        Files.writeString(run().resolve("retained-ordinary-before.json"), ordinaryHistory());
    }

    @Test
    @Order(2)
    void originalExternalCandidateHasAnActualNamedPreprovisionedRoleFailure() throws Exception {
        var legacy =
                BankFlywayFixtureConfiguration.legacyBaseline(
                        Integer.parseInt(required("ORG_FLYWAY_NATIVE_PORT")),
                        run(),
                        System.getenv());
        try (var connection =
                        DriverManager.getConnection(
                                legacy.getUrl(), legacy.getUser(), legacy.getPassword());
                var statement = connection.createStatement();
                var identity =
                        statement.executeQuery(
                                "SELECT current_user,session_user,rolsuper,rolcreaterole FROM"
                                    + " pg_catalog.pg_roles WHERE rolname=current_user")) {
            assertTrue(identity.next());
            assertEquals(required("ORG_BANK_NATIVE_ADMIN_USER"), identity.getString(1));
            assertEquals(identity.getString(1), identity.getString(2));
            assertTrue(identity.getBoolean(3) || identity.getBoolean(4));
            Files.writeString(
                    run().resolve("legacy-baseline-execution-identity.json"),
                    MAPPER.writeValueAsString(
                            Map.of(
                                    "database",
                                    "org_bank_baseline",
                                    "currentUser",
                                    identity.getString(1),
                                    "sessionUser",
                                    identity.getString(2),
                                    "superuser",
                                    identity.getBoolean(3),
                                    "createRole",
                                    identity.getBoolean(4),
                                    "scope",
                                    "UNCHANGED_LEGACY_BASELINE_ONLY")));
        }
        try {
            legacy.load().migrate();
        } catch (RuntimeException error) {
            boolean named = hasSqlState(error, "42710") && hasMessage(error, "org_native_owner");
            Files.writeString(
                    run().resolve("legacy-role-red.json"),
                    MAPPER.writeValueAsString(
                            Map.of(
                                    "actual42710",
                                    hasSqlState(error, "42710"),
                                    "namedOrgNativeOwner",
                                    named)));
            assertTrue(
                    named,
                    "Only the actual named duplicate cluster-role failure is this baseline RED");
            assertEquals(
                    0,
                    count(
                            "org_bank_baseline",
                            "SELECT count(*) FROM pg_catalog.pg_namespace WHERE"
                                    + " nspname='org_private'"));
            throw error;
        }
        fail("Original external SQL unexpectedly admitted preprovisioned cluster roles");
    }

    @Test
    @Order(3)
    void freshInstallationUsesActualFlywayAndNonSuperuserMigrator() throws Exception {
        assertMigrator();
        assertEquals(5, flyway("org_bank_fresh").migrate().migrationsExecuted);
        assertHistory("org_bank_fresh");
    }

    @Test
    @Order(4)
    void actualFourToFiveUpgradeRetainsOrdinaryBankAndAuditHistory() throws Exception {
        assertMigrator();
        assertEquals(1, flyway("org_bank_native").migrate().migrationsExecuted);
        assertEquals(
                Files.readString(run().resolve("retained-ordinary-before.json")),
                ordinaryHistory());
        assertEquals(
                1,
                count(
                        "org_bank_native",
                        "SELECT count(*) FROM public.org_native_bank_gate WHERE bank_id='"
                                + id(900)
                                + "'::uuid AND bank_generation=0 AND ownership_generation=0 AND"
                                + " source_generation=0"));
        assertHistory("org_bank_native");
    }

    @Test
    @Order(5)
    void concurrentSameDatabaseStartupAppliesV5ExactlyOnce() throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> flyway("org_bank_race").migrate().migrationsExecuted);
            var second =
                    executor.submit(() -> flyway("org_bank_race").migrate().migrationsExecuted);
            assertEquals(1, first.get(30, TimeUnit.SECONDS) + second.get(30, TimeUnit.SECONDS));
            assertHistory("org_bank_race");
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @Order(6)
    void validatedRestartReusesExactExistingRolesWithoutMigrationOrSchemaCreatePrivileges()
            throws Exception {
        for (String database : List.of("org_bank_fresh", "org_bank_native", "org_bank_race")) {
            var migration = flyway(database);
            migration.validate();
            assertEquals(0, migration.migrate().migrationsExecuted);
            assertHistory(database);
            assertEquals(
                    0,
                    count(
                            database,
                            "SELECT count(*) FROM pg_catalog.pg_roles WHERE rolname"
                                + " IN('org_native_owner','org_native_guard','org_native_runtime')"
                                + " AND (rolsuper OR rolbypassrls OR rolinherit OR rolcreatedb OR"
                                + " rolcreaterole OR rolreplication)"));
            assertEquals(
                    0,
                    count(
                            database,
                            "SELECT count(*) FROM pg_catalog.pg_roles r WHERE r.rolname"
                                + " IN('org_native_owner','org_native_guard') AND"
                                + " pg_catalog.has_schema_privilege(r.rolname,'public','CREATE')"));
            assertEquals(
                    0,
                    count(
                            database,
                            "SELECT count(*) FROM pg_catalog.pg_roles r WHERE r.rolname"
                                + " IN('org_native_guard','org_native_runtime') AND"
                                + " pg_catalog.has_schema_privilege(r.rolname,'org_private','CREATE')"));
            assertEquals(
                    0,
                    count(
                            database,
                            "SELECT count(*) FROM pg_catalog.pg_class c JOIN"
                                + " pg_catalog.pg_namespace n ON n.oid=c.relnamespace JOIN"
                                + " pg_catalog.pg_roles r ON r.oid=c.relowner WHERE n.nspname"
                                + " IN('public','org_private') AND"
                                + " r.rolname='org_native_runtime'"));
            assertEquals(
                    0,
                    count(
                            database,
                            "SELECT count(*) FROM pg_catalog.pg_proc p JOIN pg_catalog.pg_namespace"
                                + " n ON n.oid=p.pronamespace JOIN pg_catalog.pg_roles r ON"
                                + " r.oid=p.proowner WHERE n.nspname IN('public','org_private') AND"
                                + " r.rolname='org_native_runtime'"));
            assertEquals(
                    0,
                    count(
                            database,
                            "SELECT count(*) FROM pg_catalog.pg_auth_members m JOIN"
                                + " pg_catalog.pg_roles r ON r.oid=m.member WHERE r.rolname"
                                + " IN('org_native_owner','org_native_guard','org_native_runtime')"));
        }
    }

    @Test
    @Order(7)
    void actualRuntimeRepositoryCompositionCreatesOnlyACompleteDraftAfterFlywayUpgrade()
            throws Exception {
        try (var fixture = new OwnedBankPreparationFixture()) {
            assertNotNull(
                    fixture.base.context.getBean(
                            io.fireflysoftware.core.organization.models.repositories.authority
                                    .NativeBankEnrollmentOwnerIdentityRepository.class));
            assertEquals(
                    "io.fireflysoftware.core.organization.models.repositories.authority.impl.NativeBankEnrollmentOwnerIdentityReadsImpl",
                    io.fireflysoftware.core.organization.models.repositories.authority.impl
                            .NativeBankEnrollmentOwnerIdentityReadsImpl.class
                            .getName());
            fixture.staff(true, "owned-flyway-maker");
            var action = UUID.randomUUID();
            var prepared = fixture.prepare(id(1000), action, 300000);
            assertNotNull(prepared);
            assertEquals("DRAFT", prepared.outcome().state());
            assertEquals(0, prepared.outcome().controlVersion());
            assertEquals(
                    1,
                    count(
                            "org_bank_native",
                            "SELECT count(*) FROM public.org_native_action WHERE action_id='"
                                    + action
                                    + "'::uuid"));
            assertEquals(
                    0, count("org_bank_native", "SELECT count(*) FROM public.org_native_decision"));
            assertEquals(
                    0,
                    count(
                            "org_bank_native",
                            "SELECT count(*) FROM public.org_native_bank_enrollment_version"));
        }
    }

    private void assertMigrator() throws Exception {
        try (var connection =
                        DriverManager.getConnection(
                                jdbc("org_bank_native"),
                                required("ORG_FLYWAY_MIGRATOR_USER"),
                                required("ORG_FLYWAY_MIGRATOR_PASSWORD"));
                var statement = connection.createStatement();
                var row =
                        statement.executeQuery(
                                "SELECT"
                                    + " current_user,session_user,rolsuper,rolbypassrls,rolcreatedb,rolcreaterole,rolreplication"
                                    + " FROM pg_catalog.pg_roles WHERE rolname=current_user")) {
            assertTrue(row.next());
            assertEquals(required("ORG_FLYWAY_MIGRATOR_USER"), row.getString(1));
            assertEquals(row.getString(1), row.getString(2));
            for (int index = 3; index <= 7; index++) {
                assertFalse(row.getBoolean(index));
            }
        }
    }

    private void assertHistory(String database) throws Exception {
        assertEquals(
                5,
                count(
                        database,
                        "SELECT count(*) FROM public.flyway_schema_history WHERE success AND"
                                + " type='SQL'"));
        assertEquals(
                1,
                count(
                        database,
                        "SELECT count(*) FROM public.flyway_schema_history WHERE success AND"
                                + " version='5'"));
        assertEquals(
                61,
                count(
                        database,
                        "SELECT count(*) FROM pg_catalog.pg_proc p JOIN pg_catalog.pg_namespace n"
                            + " ON n.oid=p.pronamespace WHERE n.nspname IN('public','org_private')"
                            + " AND (p.proname LIKE 'org_native_%' OR n.nspname='org_private')"));
    }

    private String ordinaryHistory() throws Exception {
        var records = new ArrayList<String>();
        try (var connection = admin("org_bank_native");
                var statement = connection.createStatement();
                var rows =
                        statement.executeQuery(
                                "SELECT to_jsonb(b)::text FROM public.bank b WHERE b.id='"
                                        + id(900)
                                        + "'::uuid UNION ALL SELECT to_jsonb(a)::text FROM"
                                        + " public.bank_audit_log a WHERE a.id='"
                                        + RETAINED_AUDIT
                                        + "'::uuid ORDER BY 1")) {
            while (rows.next()) {
                records.add(rows.getString(1));
            }
        }
        assertEquals(2, records.size());
        return MAPPER.writeValueAsString(records);
    }
}
