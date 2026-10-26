/*
 * Copyright 2026 Firefly Software Foundation
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.fireflysoftware.core.organization.web.security;

import io.fireflysoftware.core.organization.web.config.OrganizationEnrollmentPolicyConfiguration;
import org.fireflyframework.security.adapter.opa.OpaPolicyDecisionAdapter;
import org.fireflyframework.security.api.domain.SecurityPrincipal;
import org.fireflyframework.security.core.policy.EmbeddedPolicyDecisionAdapter;
import org.fireflyframework.security.rs.JwtToFireflyPrincipalConverter;
import org.fireflyframework.security.rs.ResourceServerAutoConfiguration;
import org.fireflyframework.security.spi.PolicyDecisionPort;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ReactiveWebApplicationContextRunner;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class OrganizationEnrollmentPolicyConfigurationTest {
    private static final Duration WAIT = Duration.ofSeconds(3);
    private final ReactiveWebApplicationContextRunner runner = new ReactiveWebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ResourceServerAutoConfiguration.class))
            .withUserConfiguration(OrganizationEnrollmentPolicyConfiguration.class)
            .withPropertyValues("firefly.security.resource-server.strict-policy=true",
                    "firefly.security.resource-server.issuer=https://owner.example.test",
                    "firefly.security.resource-server.audiences=organization-source",
                    "firefly.security.resource-server.jwk-set-uri=https://owner.example.test/jwks",
                    "firefly.security.resource-server.tenant-required=true",
                    "firefly.security.resource-server.tenant-claim-paths[0]=tenant_id");

    @Test
    void enabledProductionPolicySelectsSharedAdapterAndPreservesOriginalSecurityBeans() {
        runner.withPropertyValues("firefly.organization.enrollment.action-read.enabled=true",
                "firefly.organization.enrollment.policy.base-uri=https://policy.example.test",
                "firefly.organization.enrollment.policy.decision-path=organization/allow")
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    assertInstanceOf(OpaPolicyDecisionAdapter.class, context.getBean(PolicyDecisionPort.class));
                    assertInstanceOf(JwtToFireflyPrincipalConverter.class,
                            context.getBean("fireflyJwtAuthenticationConverter"));
                    assertTrue(context.containsBean("fireflySecurityWebFilterChain"));
                    assertFalse(context.containsBean("fireflyPolicyDecisionPort"));
                });
    }

    @Test
    void enabledNativePolicyMissingOrMalformedConfigurationFailsStartup() {
        for (String[] values : new String[][] {{},
                {"firefly.organization.enrollment.policy.base-uri=ftp://policy.example.test", "firefly.organization.enrollment.policy.decision-path=organization/allow"},
                {"firefly.organization.enrollment.policy.base-uri=https://userinfo@policy.example.test", "firefly.organization.enrollment.policy.decision-path=organization/allow"},
                {"firefly.organization.enrollment.policy.base-uri=https://policy.example.test", "firefly.organization.enrollment.policy.decision-path=../allow"}}) {
            runner.withPropertyValues("firefly.organization.enrollment.action-read.enabled=true")
                    .withPropertyValues(values).run(context -> assertNotNull(context.getStartupFailure()));
        }
    }

    @Test
    void disabledOwnerConfigurationRetainsStrictDefaultDenialWithoutPolicyRules() {
        runner.run(context -> {
            assertNull(context.getStartupFailure());
            var policy = context.getBean(PolicyDecisionPort.class);
            assertInstanceOf(EmbeddedPolicyDecisionAdapter.class, policy);
            assertFalse(policy.authorize(SecurityPrincipal.builder().subject("source-only-reader").build(),
                    "GET", "/api/v1/banks/source-only", Map.of()).block(WAIT).granted());
            assertFalse(context.containsBean("organizationEnrollmentPolicyDecisionPort"));
        });
    }

    @Test
    void sharedAdapterMalformedMissingAndFalseResponsesNeverGrant() {
        var principal = SecurityPrincipal.builder().subject("source-only-reader").build();
        for (String response : new String[] {"{invalid", "{}", "{\"result\":false}", "{\"result\":\"true\"}"}) {
            var client = WebClient.builder().baseUrl("https://policy.example.test")
                    .exchangeFunction(request -> Mono.just(ClientResponse.create(HttpStatus.OK)
                            .header("Content-Type", "application/json").body(response).build())).build();
            var decision = new OpaPolicyDecisionAdapter(client, "organization/allow")
                    .authorize(principal, "GET", "/api/v1/banks/source-only", Map.of()).block(WAIT);
            assertFalse(decision.granted(), "Malformed or denied protocol response must never grant");
        }
    }

    @Test
    void sharedAdapterUnavailableResponseDenies() {
        var principal = SecurityPrincipal.builder().subject("source-only-reader").build();
        var unavailable = WebClient.builder().baseUrl("https://policy.example.test")
                .exchangeFunction(request -> Mono.error(new IOException("Owned protocol transport unavailable"))).build();
        assertFalse(new OpaPolicyDecisionAdapter(unavailable, "organization/allow")
                .authorize(principal, "GET", "/api/v1/banks/source-only", Map.of()).block(WAIT).granted());
    }

    @Test
    void realAdapterRetainsExactCallerScopeAndCurrentDecisionAcrossCapturedAdmission() throws Exception {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var inputs = new java.util.ArrayList<com.fasterxml.jackson.databind.JsonNode>();
        var allow = new java.util.concurrent.atomic.AtomicBoolean(true);
        var strategies = org.springframework.web.reactive.function.client.ExchangeStrategies.withDefaults();
        var client = WebClient.builder().baseUrl("https://policy.example.test")
                .exchangeFunction(request -> {
                    assertEquals("/v1/data/organization/allow", request.url().getPath());
                    assertNull(request.headers().getFirst("Authorization"));
                    var output = new org.springframework.mock.http.client.reactive.MockClientHttpRequest(
                            request.method(), request.url());
                    var context = new org.springframework.web.reactive.function.BodyInserter.Context() {
                        public java.util.List<org.springframework.http.codec.HttpMessageWriter<?>> messageWriters() {
                            return strategies.messageWriters();
                        }
                        public java.util.Optional<org.springframework.http.server.reactive.ServerHttpRequest> serverRequest() {
                            return java.util.Optional.empty();
                        }
                        public Map<String, Object> hints() { return Map.of(); }
                    };
                    return request.body().insert(output, context).then(Mono.defer(output::getBodyAsString)).map(body -> {
                        try { inputs.add(mapper.readTree(body).get("input")); }
                        catch (java.io.IOException error) { throw new java.io.UncheckedIOException(error); }
                        return ClientResponse.create(HttpStatus.OK).header("Content-Type", "application/json")
                                .body("{\"result\":" + allow.get() + "}").build();
                    });
                }).build();
        var policy = new OpaPolicyDecisionAdapter(client, "organization/allow");
        String issuer = "https://owner.example.test";
        String platform = "00000000-0000-0000-0000-000000000090";
        String tenant = "00000000-0000-0000-0000-000000000091";
        String bank = "00000000-0000-0000-0000-000000000092";
        var now = java.time.Instant.parse("2026-10-25T00:00:00Z");
        var clock = java.time.Clock.fixed(now, java.time.ZoneOffset.UTC);
        var principal = SecurityPrincipal.builder().issuer(issuer).subject("source-model-maker")
                .tenantId(platform).claims(Map.of("tenant_id", platform, "org_kind", "employee",
                        "org_staff", "00000000-0000-0000-0000-000000000094", "exp", now.plusSeconds(60))).build();
        var current = new java.util.concurrent.atomic.AtomicReference<>(principal);
        var authorization = new io.fireflysoftware.core.organization.core.authority.OrganizationPreparationAuthorization(
                () -> Mono.just(current.get()), policy, clock, platform,
                Map.of(issuer, new io.fireflysoftware.core.organization.core.authority.OrganizationActorIssuerProfile(
                        issuer, "tenant_id", "org_kind", "org_staff", "org_client", "employee", "service")),
                Map.of(issuer, "owned-source-realm"));
        var body = mapper.createObjectNode().put("schemaVersion", 2)
                .put("actionId", "00000000-0000-0000-0000-000000000095").put("mode", "ENROLL")
                .put("bankId", bank).put("destinationTenantId", tenant)
                .put("institutionId", "00000000-0000-0000-0000-000000000096")
                .put("issuerAuthorityId", "00000000-0000-0000-0000-000000000097")
                .put("notAfterEpochMillis", clock.millis() + 20000).put("reason", "Owned source protocol admission");
        body.putArray("allowedUses").add("ACCOUNTING_BOOK");
        body.putArray("evidenceReferences").add("fixture://owned-source-evidence");
        var mode = io.fireflysoftware.core.organization.interfaces.authority.BankEnrollmentPreparationRequest.Mode.ENROLL;
        var discovery = authorization.discover(tenant, bank, mode, mapper.writeValueAsBytes(body)).block(WAIT);
        var captured = authorization.bindCapture(discovery,
                new io.fireflysoftware.core.organization.core.authority.BankEnrollmentPreparationPlan.LocalSnapshot(
                        bank, 7, 0, 9, null), clock.millis() + 15000).block(WAIT);
        assertNotNull(captured);
        assertEquals("organization.bank.enrollment.prepare", inputs.get(0).get("action").asText());
        assertEquals("tenant/" + tenant + "/BANK_ENROLLMENT/" + bank, inputs.get(0).get("resource").asText());
        assertEquals(platform, inputs.get(0).get("subject").get("tenantId").asText());
        assertEquals(platform, inputs.get(0).get("context").get("actorTenantId").asText());
        assertEquals(tenant, inputs.get(0).get("context").get("resourceTenantId").asText());
        allow.set(false);
        assertEquals(403, assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> authorization.recheckCapture(captured).block(WAIT)).getStatusCode().value());
        allow.set(true);
        current.set(principal.toBuilder().subject("replacement-source-model-caller").build());
        assertEquals(403, assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> authorization.recheckCapture(captured).block(WAIT)).getStatusCode().value());
    }
}
