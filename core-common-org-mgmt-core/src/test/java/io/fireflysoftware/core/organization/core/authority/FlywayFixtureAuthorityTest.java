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

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Map;

class FlywayFixtureAuthorityTest {
    private final Map<String, String> environment =
            Map.of(
                    "ORG_FLYWAY_MIGRATOR_USER", "restricted-migrator",
                    "ORG_FLYWAY_MIGRATOR_PASSWORD", "unused-migrator-test-value",
                    "ORG_BANK_NATIVE_ADMIN_USER", "explicit-baseline-administrator",
                    "ORG_BANK_NATIVE_ADMIN_PASSWORD", "unused-admin-test-value");

    @Test
    void legacyRoleBaselineAloneUsesExplicitAdministratorAndIsBoundToItsDatabase() {
        var configuration =
                BankFlywayFixtureConfiguration.legacyBaseline(
                        54321, Path.of("/owned-fixture"), environment);
        assertEquals("explicit-baseline-administrator", configuration.getUser());
        assertEquals("jdbc:postgresql://127.0.0.1:54321/org_bank_baseline", configuration.getUrl());
        assertTrue(configuration.isCleanDisabled());
        assertFalse(configuration.isBaselineOnMigrate());
    }

    @Test
    void everyPositiveMigrationKeepsRestrictedMigratorCredentials() {
        for (String database :
                new String[] {"org_bank_native", "org_bank_fresh", "org_bank_race"}) {
            var configuration =
                    BankFlywayFixtureConfiguration.positive(
                            database, 54321, null, "classpath:db/migration", environment);
            assertEquals("restricted-migrator", configuration.getUser());
            assertEquals("jdbc:postgresql://127.0.0.1:54321/" + database, configuration.getUrl());
            assertNotEquals(environment.get("ORG_BANK_NATIVE_ADMIN_USER"), configuration.getUser());
            assertTrue(configuration.isCleanDisabled());
        }
    }
}
