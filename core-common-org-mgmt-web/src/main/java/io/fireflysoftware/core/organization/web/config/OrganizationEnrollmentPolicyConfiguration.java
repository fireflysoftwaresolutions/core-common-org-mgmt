/*
 * Copyright 2026 Firefly Software Foundation
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.fireflysoftware.core.organization.web.config;

import org.fireflyframework.security.adapter.opa.OpaPolicyDecisionAdapter;
import org.fireflyframework.security.spi.PolicyDecisionPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "firefly.organization.enrollment.action-read.enabled", havingValue = "true")
@EnableConfigurationProperties(OrganizationEnrollmentPolicyProperties.class)
public class OrganizationEnrollmentPolicyConfiguration {
    @Bean
    public PolicyDecisionPort organizationEnrollmentPolicyDecisionPort(OrganizationEnrollmentPolicyProperties properties) {
        return new OpaPolicyDecisionAdapter(
                WebClient.builder().baseUrl(properties.baseUri().toString()).build(),
                properties.decisionPath());
    }
}
