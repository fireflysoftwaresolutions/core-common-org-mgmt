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

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;

import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

final class BankFlywayFixtureConfiguration {
    private BankFlywayFixtureConfiguration() {}

    static FluentConfiguration positive(
            String database,
            int port,
            String target,
            String location,
            Map<String, String> environment) {
        if (!Set.of("org_bank_native", "org_bank_fresh", "org_bank_race", "org_bank_baseline")
                .contains(database)) {
            throw new IllegalArgumentException("Exact owned migration database required");
        }
        var configuration =
                configure(
                        database,
                        port,
                        location,
                        required(environment, "ORG_FLYWAY_MIGRATOR_USER"),
                        required(environment, "ORG_FLYWAY_MIGRATOR_PASSWORD"));
        return target == null ? configuration : configuration.target(target);
    }

    static FluentConfiguration legacyBaseline(int port, Path run, Map<String, String> environment) {
        return configure(
                "org_bank_baseline",
                port,
                "filesystem:" + run.resolve("legacy-migrations"),
                required(environment, "ORG_BANK_NATIVE_ADMIN_USER"),
                required(environment, "ORG_BANK_NATIVE_ADMIN_PASSWORD"));
    }

    private static FluentConfiguration configure(
            String database, int port, String location, String username, String password) {
        if (port <= 0 || port > 65535 || Set.of(18080, 9001).contains(port)) {
            throw new IllegalArgumentException("Owned random nonproducer port required");
        }
        return Flyway.configure()
                .dataSource(
                        "jdbc:postgresql://127.0.0.1:" + port + "/" + database, username, password)
                .locations(location)
                .baselineOnMigrate(false)
                .cleanDisabled(true)
                .connectRetries(0)
                .lockRetryCount(10);
    }

    private static String required(Map<String, String> values, String name) {
        var value = values.get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Owned migration input required: " + name);
        }
        return value;
    }
}
