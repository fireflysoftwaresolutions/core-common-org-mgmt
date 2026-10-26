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
package io.fireflysoftware.core.organization.core.services.authority.impl;

import io.fireflysoftware.common.config.sdk.api.InstitutionAuthorityApi;
import io.fireflysoftware.common.config.sdk.invoker.ApiClient;
import io.fireflysoftware.core.organization.core.authority.*;
import io.fireflysoftware.core.organization.core.services.authority.BankEnrollmentPreparationObservationService;
import io.fireflysoftware.core.organization.interfaces.authority.FinancialCalendarUse;

import org.springframework.http.*;
import org.springframework.security.oauth2.client.*;
import org.springframework.security.oauth2.client.registration.*;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.web.reactive.function.client.*;
import org.springframework.web.server.ResponseStatusException;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.*;
import java.util.*;

public final class BankEnrollmentPreparationObservationServiceImpl
        implements BankEnrollmentPreparationObservationService {
    private final OrganizationPreparationAuthorization authorization;
    private final ReactiveClientRegistrationRepository registrations;
    private final ReactiveOAuth2AuthorizedClientManager manager;
    private final Map<String, ReactiveJwtDecoder> decoders;
    private final Map<String, EnrollmentObserverProfile> profiles;
    private final WebClient transport;
    private final Clock clock;

    public BankEnrollmentPreparationObservationServiceImpl(
            OrganizationPreparationAuthorization authorization,
            ReactiveClientRegistrationRepository registrations,
            ReactiveOAuth2AuthorizedClientManager manager,
            Map<String, ReactiveJwtDecoder> decoders,
            Map<String, EnrollmentObserverProfile> profiles,
            WebClient transport,
            Clock clock) {
        this.authorization = Objects.requireNonNull(authorization);
        this.registrations = Objects.requireNonNull(registrations);
        this.manager = Objects.requireNonNull(manager);
        this.decoders = Map.copyOf(decoders);
        this.profiles = Map.copyOf(profiles);
        this.transport = Objects.requireNonNull(transport);
        this.clock = Objects.requireNonNull(clock);
        if (!this.decoders.keySet().equals(this.profiles.keySet()))
            throw new IllegalArgumentException(
                    "Exact tenant-bound configuration decoders required");
        this.profiles.forEach(
                (tenant, p) -> {
                    if (!tenant.equals(p.connection().tenantId()))
                        throw new IllegalArgumentException(
                                "Exact tenant-bound observer profiles required");
                });
    }

    @Override
    public Mono<List<ObservedInstitutionGrantEvidence>> observeForPreparation(
            OrganizationPreparationAuthorization.Captured admission) {
        return Mono.defer(
                        () -> {
                            if (admission == null) throw denied();
                            return authorization
                                    .recheckCapture(admission)
                                    .flatMap(
                                            current ->
                                                    Flux.fromIterable(
                                                                    current.discovery()
                                                                            .desired()
                                                                            .request()
                                                                            .allowedUses())
                                                            .concatMap(use -> observe(current, use))
                                                            .collectList()
                                                            .flatMap(
                                                                    observations ->
                                                                            authorization
                                                                                    .recheckCapture(
                                                                                            current)
                                                                                    .map(
                                                                                            finalCaller -> {
                                                                                                if (observations
                                                                                                                .size()
                                                                                                        != current.discovery()
                                                                                                                .desired()
                                                                                                                .request()
                                                                                                                .allowedUses()
                                                                                                                .size())
                                                                                                    throw changed();
                                                                                                var
                                                                                                        first =
                                                                                                                observations
                                                                                                                        .getFirst()
                                                                                                                        .observation()
                                                                                                                        .observation();
                                                                                                for (var
                                                                                                        item :
                                                                                                                observations) {
                                                                                                    var
                                                                                                            next =
                                                                                                                    item.observation()
                                                                                                                            .observation();
                                                                                                    if (!Objects
                                                                                                                    .equals(
                                                                                                                            first
                                                                                                                                    .definition(),
                                                                                                                            next
                                                                                                                                    .definition())
                                                                                                            || !Objects
                                                                                                                    .equals(
                                                                                                                            first
                                                                                                                                    .headVersion(),
                                                                                                                            next
                                                                                                                                    .headVersion())
                                                                                                            || !Objects
                                                                                                                    .equals(
                                                                                                                            first
                                                                                                                                    .control(),
                                                                                                                            next
                                                                                                                                    .control())
                                                                                                            || !Objects
                                                                                                                    .equals(
                                                                                                                            first
                                                                                                                                    .tenantEligibility(),
                                                                                                                            next
                                                                                                                                    .tenantEligibility()))
                                                                                                        throw changed();
                                                                                                }
                                                                                                observations
                                                                                                        .forEach(
                                                                                                                e ->
                                                                                                                        e
                                                                                                                                .requireCurrentAt(
                                                                                                                                        clock
                                                                                                                                                .millis()));
                                                                                                return List
                                                                                                        .copyOf(
                                                                                                                observations);
                                                                                            })));
                        })
                .onErrorMap(
                        error -> {
                            if (error instanceof ResponseStatusException) return error;
                            if (error instanceof WebClientResponseException e
                                    && (e.getStatusCode().value() == 401
                                            || e.getStatusCode().value() == 403)) return denied();
                            return unavailable();
                        });
    }

    private Mono<ObservedInstitutionGrantEvidence> observe(
            OrganizationPreparationAuthorization.Captured admission, FinancialCalendarUse use) {
        return authorization
                .recheckCapture(admission)
                .flatMap(
                        current -> {
                            var target = current.discovery().desired().request();
                            var profile = profiles.get(target.destinationTenantId());
                            if (profile == null) return Mono.error(denied());
                            var connection = profile.connection();
                            return registrations
                                    .findByRegistrationId(connection.registrationId())
                                    .timeout(Duration.ofSeconds(5))
                                    .filter(
                                            r ->
                                                    EnrollmentObserverProtocol.registered(
                                                            connection, r))
                                    .switchIfEmpty(Mono.error(denied()))
                                    .flatMap(
                                            r ->
                                                    authorization
                                                            .recheckCapture(current)
                                                            .flatMap(
                                                                    checked ->
                                                                            manager.authorize(
                                                                                            OAuth2AuthorizeRequest
                                                                                                    .withClientRegistrationId(
                                                                                                            connection
                                                                                                                    .registrationId())
                                                                                                    .principal(
                                                                                                            "organization-bank-preparation-observer")
                                                                                                    .build())
                                                                                    .timeout(
                                                                                            Duration
                                                                                                    .ofSeconds(
                                                                                                            5))
                                                                                    .switchIfEmpty(
                                                                                            Mono
                                                                                                    .error(
                                                                                                            denied()))))
                                    .flatMap(
                                            client -> {
                                                var token = client.getAccessToken();
                                                if (!EnrollmentObserverProtocol.registered(
                                                                connection,
                                                                client.getClientRegistration())
                                                        || token == null
                                                        || !OAuth2AccessToken.TokenType.BEARER
                                                                .equals(token.getTokenType())
                                                        || token.getExpiresAt() == null
                                                        || !token.getExpiresAt()
                                                                .isAfter(clock.instant())
                                                        || token.getTokenValue() == null
                                                        || token.getTokenValue().length() > 16384
                                                        || !token.getTokenValue()
                                                                .matches(
                                                                        "[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+"))
                                                    return Mono.error(denied());
                                                return Mono.defer(
                                                                () ->
                                                                        decoders.get(
                                                                                        target
                                                                                                .destinationTenantId())
                                                                                .decode(
                                                                                        token
                                                                                                .getTokenValue()))
                                                        .timeout(Duration.ofSeconds(5))
                                                        .onErrorMap(e -> denied())
                                                        .switchIfEmpty(Mono.error(denied()))
                                                        .flatMap(
                                                                jwt -> {
                                                                    var observer =
                                                                            EnrollmentObserverProtocol
                                                                                    .observer(
                                                                                            profile,
                                                                                            jwt,
                                                                                            token
                                                                                                    .getExpiresAt(),
                                                                                            clock);
                                                                    return authorization
                                                                            .recheckCapture(current)
                                                                            .flatMap(
                                                                                    checked -> {
                                                                                        if (clock
                                                                                                        .millis()
                                                                                                >= observer
                                                                                                        .credentialExpiresAtEpochMillis())
                                                                                            throw denied();
                                                                                        var
                                                                                                apiClient =
                                                                                                        new ApiClient(
                                                                                                                        transport)
                                                                                                                .setBasePath(
                                                                                                                        connection
                                                                                                                                .configurationBaseUri()
                                                                                                                                .toString());
                                                                                        apiClient
                                                                                                .addDefaultHeader(
                                                                                                        HttpHeaders
                                                                                                                .AUTHORIZATION,
                                                                                                        "Bearer"
                                                                                                            + " "
                                                                                                                + token
                                                                                                                        .getTokenValue());
                                                                                        apiClient
                                                                                                .addDefaultHeader(
                                                                                                        HttpHeaders
                                                                                                                .CACHE_CONTROL,
                                                                                                        "no-cache");
                                                                                        return new InstitutionAuthorityApi(
                                                                                                        apiClient)
                                                                                                .observeCurrentInstitutionAuthorityGrantWithResponseSpec(
                                                                                                        target
                                                                                                                .destinationTenantId(),
                                                                                                        target
                                                                                                                .institutionId(),
                                                                                                        target
                                                                                                                .issuerAuthorityId(),
                                                                                                        use
                                                                                                                .name(),
                                                                                                        null)
                                                                                                .toEntity(
                                                                                                        byte
                                                                                                                        []
                                                                                                                .class)
                                                                                                .timeout(
                                                                                                        Duration
                                                                                                                .ofSeconds(
                                                                                                                        5))
                                                                                                .switchIfEmpty(
                                                                                                        Mono
                                                                                                                .error(
                                                                                                                        unavailable()))
                                                                                                .flatMap(
                                                                                                        response -> {
                                                                                                            long
                                                                                                                    received =
                                                                                                                            clock
                                                                                                                                    .millis();
                                                                                                            if (response
                                                                                                                                    .getStatusCode()
                                                                                                                            != HttpStatus
                                                                                                                                    .OK
                                                                                                                    || response
                                                                                                                                    .getBody()
                                                                                                                            == null
                                                                                                                    || response.getHeaders()
                                                                                                                                    .getContentType()
                                                                                                                            == null
                                                                                                                    || !MediaType
                                                                                                                            .APPLICATION_JSON
                                                                                                                            .isCompatibleWith(
                                                                                                                                    response.getHeaders()
                                                                                                                                            .getContentType())
                                                                                                                    || !EnrollmentObserverProtocol
                                                                                                                            .noStore(
                                                                                                                                    response.getHeaders()
                                                                                                                                            .getCacheControl()))
                                                                                                                return Mono
                                                                                                                        .error(
                                                                                                                                unavailable());
                                                                                                            return authorization
                                                                                                                    .recheckCapture(
                                                                                                                            current)
                                                                                                                    .map(
                                                                                                                            after -> {
                                                                                                                                if (clock
                                                                                                                                                .millis()
                                                                                                                                        >= observer
                                                                                                                                                .credentialExpiresAtEpochMillis())
                                                                                                                                    throw denied();
                                                                                                                                VerifiedInstitutionGrantObservation
                                                                                                                                        evidence;
                                                                                                                                try {
                                                                                                                                    evidence =
                                                                                                                                            ConfigurationGrantObservationVerifier
                                                                                                                                                    .verify(
                                                                                                                                                            target
                                                                                                                                                                    .destinationTenantId(),
                                                                                                                                                            target
                                                                                                                                                                    .institutionId(),
                                                                                                                                                            target
                                                                                                                                                                    .issuerAuthorityId(),
                                                                                                                                                            use,
                                                                                                                                                            response
                                                                                                                                                                    .getBody(),
                                                                                                                                                            received);
                                                                                                                                } catch (
                                                                                                                                        IllegalArgumentException
                                                                                                                                                e) {
                                                                                                                                    throw unavailable();
                                                                                                                                }
                                                                                                                                long
                                                                                                                                        until =
                                                                                                                                                Math
                                                                                                                                                        .min(
                                                                                                                                                                after
                                                                                                                                                                        .validUntilEpochMillis(),
                                                                                                                                                                observer
                                                                                                                                                                        .credentialExpiresAtEpochMillis());
                                                                                                                                var
                                                                                                                                        bounded =
                                                                                                                                                evidence
                                                                                                                                                        .limitedTo(
                                                                                                                                                                until);
                                                                                                                                bounded
                                                                                                                                        .requireCurrentAt(
                                                                                                                                                clock
                                                                                                                                                        .millis());
                                                                                                                                return new ObservedInstitutionGrantEvidence(
                                                                                                                                        bounded,
                                                                                                                                        observer,
                                                                                                                                        received,
                                                                                                                                        bounded
                                                                                                                                                .usableUntilEpochMillis());
                                                                                                                            });
                                                                                                        });
                                                                                    });
                                                                });
                                            });
                        });
    }

    private static ResponseStatusException denied() {
        return new ResponseStatusException(
                HttpStatus.FORBIDDEN, "Organization preparation observation denied");
    }

    private static ResponseStatusException changed() {
        return new ResponseStatusException(
                HttpStatus.CONFLICT, "Preparation observations no longer consistent");
    }

    private static ResponseStatusException unavailable() {
        return new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Configuration preparation observation unavailable");
    }
}
