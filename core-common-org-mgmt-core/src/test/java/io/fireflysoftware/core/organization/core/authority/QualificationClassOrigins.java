/*
 * Copyright 2026 Firefly Software Foundation
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.fireflysoftware.core.organization.core.authority;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.jar.JarFile;
import static org.junit.jupiter.api.Assertions.*;

final class QualificationClassOrigins {
    private QualificationClassOrigins() { }

    static void verifyAndRecord(Class<?> type, String property) throws Exception {
        String expected = System.getProperty(property);
        if (expected == null) return;
        var origin = type.getProtectionDomain().getCodeSource().getLocation().toURI();
        assertEquals(URI.create(expected), origin);
        String entry = type.getName().replace('.', '/') + ".class";
        var record = new LinkedHashMap<String, Object>();
        record.put("className", type.getName());
        record.put("origin", origin.toString());
        try (var input = type.getResourceAsStream("/" + entry)) {
            assertNotNull(input);
            record.put("classSha256", digest(input.readAllBytes()));
        }
        var artifact = Path.of(origin);
        var specs = new LinkedHashMap<String, String>();
        if (Files.isRegularFile(artifact)) {
            record.put("artifactSha256", digest(Files.readAllBytes(artifact)));
            try (var jar = new JarFile(artifact.toFile())) {
                for (String name : new String[] {"openapi.yml", "api-spec/openapi.yml"}) {
                    var resource = jar.getJarEntry(name);
                    if (resource != null) {
                        try (var input = jar.getInputStream(resource)) {
                            specs.put(name, digest(input.readAllBytes()));
                        }
                    }
                }
            }
        }
        record.put("specSha256", specs);
        record.put("observedAtEpochMillis", System.currentTimeMillis());
        record.put("processId", ProcessHandle.current().pid());
        String output = System.getProperty("firefly.qualification.origin-output");
        if (output != null) {
            var directory = Path.of(output);
            Files.createDirectories(directory);
            Files.writeString(directory.resolve(type.getName() + ".json"),
                    new ObjectMapper().writeValueAsString(record) + "\n", StandardOpenOption.CREATE_NEW);
        }
    }

    private static String digest(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
