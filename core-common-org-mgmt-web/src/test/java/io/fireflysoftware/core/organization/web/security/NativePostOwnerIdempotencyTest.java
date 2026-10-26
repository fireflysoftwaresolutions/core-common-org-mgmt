/*
 * Copyright 2026 Firefly Software Foundation
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.fireflysoftware.core.organization.web.security;

import io.fireflysoftware.core.organization.web.controllers.authority.BankEnrollmentPreparationController;
import io.fireflysoftware.core.organization.web.controllers.authority.BankEnrollmentPreparationRecoveryController;
import io.fireflysoftware.core.organization.web.controllers.authority.BankEnrollmentProposalController;
import org.fireflyframework.web.idempotency.cache.IdempotencyCache;
import org.fireflyframework.web.idempotency.config.IdempotencyProperties;
import org.fireflyframework.web.idempotency.filter.IdempotencyWebFilter;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.reactive.result.method.annotation.RequestMappingHandlerMapping;
import reactor.core.publisher.Mono;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NativePostOwnerIdempotencyTest {
    @Test
    void nativePostRoutesDelegateWithoutHttpResponseReuse() {
        try (var context = new GenericApplicationContext()) {
            context.registerBean("preparation", BankEnrollmentPreparationController.class,
                    () -> new BankEnrollmentPreparationController(null));
            context.registerBean("commands", BankEnrollmentProposalController.class,
                    () -> new BankEnrollmentProposalController(null));
            context.registerBean("recovery", BankEnrollmentPreparationRecoveryController.class,
                    () -> new BankEnrollmentPreparationRecoveryController(null));
            context.refresh();
            var mapping = new RequestMappingHandlerMapping();
            mapping.setApplicationContext(context);
            mapping.afterPropertiesSet();
            var cache = mock(IdempotencyCache.class);
            when(cache.get(anyString())).thenReturn(Mono.empty());
            when(cache.put(anyString(), any())).thenReturn(Mono.empty());
            var properties = new IdempotencyProperties();
            properties.setNamespace("owned-source-routing");
            var scopes = new AtomicInteger();
            var filter = new IdempotencyWebFilter(properties, cache,
                    exchange -> { scopes.incrementAndGet(); return Mono.just("owned-source-scope"); },
                    () -> List.of(mapping));
            String root = "/api/v1/platform/tenants/00000000-0000-0000-0000-000000000090/banks/00000000-0000-0000-0000-000000000091";
            String proposal = root + "/enrollment-proposals/00000000-0000-0000-0000-000000000092/";
            var paths = List.of(root + "/enrollment-proposals", proposal + "submit", proposal + "approve",
                    proposal + "reject", proposal + "withdraw", proposal + "cancel", proposal + "emergency-cancel",
                    root + "/enrollment-actions/00000000-0000-0000-0000-000000000093/recover-preparation");
            var delegated = new AtomicInteger();
            for (String path : paths) {
                var exchange = MockServerWebExchange.from(MockServerHttpRequest.post(path)
                        .contentType(MediaType.APPLICATION_JSON).header("X-Idempotency-Key", "owned-source-key").body("{}"));
                assertNotNull(mapping.getHandler(exchange).block(Duration.ofSeconds(3)));
                filter.filter(exchange, selected -> {
                    delegated.incrementAndGet();
                    return selected.getResponse().setComplete();
                }).block(Duration.ofSeconds(3));
            }
            assertEquals(8, delegated.get());
            assertEquals(0, scopes.get());
            verifyNoInteractions(cache);
        }
    }
}
