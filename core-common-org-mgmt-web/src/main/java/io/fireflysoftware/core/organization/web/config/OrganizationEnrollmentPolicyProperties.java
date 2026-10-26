/*
 * Copyright 2026 Firefly Software Foundation
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.fireflysoftware.core.organization.web.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.net.URI;
import java.util.Set;

@ConfigurationProperties(prefix = "firefly.organization.enrollment.policy")
public record OrganizationEnrollmentPolicyProperties(URI baseUri, String decisionPath) {
    public OrganizationEnrollmentPolicyProperties {
        if (baseUri == null || !Set.of("http", "https").contains(baseUri.getScheme())
                || baseUri.getHost() == null || baseUri.getUserInfo() != null
                || baseUri.getRawQuery() != null || baseUri.getRawFragment() != null
                || !(baseUri.getPath().isEmpty() || baseUri.getPath().equals("/"))
                || decisionPath == null
                || !decisionPath.matches("[A-Za-z0-9_-]+(?:/[A-Za-z0-9_-]+)*")) {
            throw new IllegalArgumentException("Explicit valid organization enrollment policy endpoint and decision path required");
        }
    }
}
