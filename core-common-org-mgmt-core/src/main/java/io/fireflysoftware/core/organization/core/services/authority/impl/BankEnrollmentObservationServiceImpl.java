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
import io.fireflysoftware.core.organization.core.authority.OrganizationAuthorityAuthorization.EnrollmentAdmission;
import io.fireflysoftware.core.organization.core.services.authority.BankEnrollmentObservationService;
import org.springframework.http.*;
import org.springframework.security.oauth2.client.*;
import org.springframework.security.oauth2.client.registration.*;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.web.reactive.function.client.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import java.time.*;
import java.util.*;

public final class BankEnrollmentObservationServiceImpl implements BankEnrollmentObservationService {
    private final OrganizationAuthorityAuthorization authorization;
    private final ReactiveClientRegistrationRepository registrations;
    private final ReactiveOAuth2AuthorizedClientManager manager;
    private final Map<String,ReactiveJwtDecoder> decoders;
    private final Map<String,EnrollmentObserverProfile> profiles;
    private final WebClient transport;private final Clock clock;
    public BankEnrollmentObservationServiceImpl(OrganizationAuthorityAuthorization authorization,
            ReactiveClientRegistrationRepository registrations,ReactiveOAuth2AuthorizedClientManager manager,
            Map<String,ReactiveJwtDecoder> decoders,Map<String,EnrollmentObserverProfile> profiles,WebClient transport,Clock clock) {
        this.authorization=Objects.requireNonNull(authorization);this.registrations=Objects.requireNonNull(registrations);
        this.manager=Objects.requireNonNull(manager);this.decoders=Map.copyOf(decoders);this.profiles=Map.copyOf(profiles);
        this.transport=Objects.requireNonNull(transport);this.clock=Objects.requireNonNull(clock);
        if(!this.decoders.keySet().equals(this.profiles.keySet())) throw new IllegalArgumentException("Exact tenant-bound configuration decoders required");
        this.profiles.forEach((tenant,p)->{if(!tenant.equals(p.connection().tenantId()))throw new IllegalArgumentException("Exact tenant-bound observer profiles required");});
    }
    @Override public Mono<ObservedInstitutionGrantEvidence> observeForEnrollment(EnrollmentAdmission admission) {
        return authorization.recheck(admission).flatMap(approved->{
            var t=approved.scope().intent().request();var profile=profiles.get(t.destinationTenantId());
            if(profile==null) return Mono.error(denied());var connection=profile.connection();
            return registrations.findByRegistrationId(connection.registrationId()).timeout(Duration.ofSeconds(5))
                    .filter(r->EnrollmentObserverProtocol.registered(connection,r)).switchIfEmpty(Mono.error(denied()))
                    .flatMap(r->{approved.requireCurrent();return manager.authorize(OAuth2AuthorizeRequest.withClientRegistrationId(connection.registrationId())
                            .principal("organization-bank-enrollment-observer").build()).timeout(Duration.ofSeconds(5)).switchIfEmpty(Mono.error(denied()));})
                    .flatMap(client->{
                        var token=client.getAccessToken();
                        if(!EnrollmentObserverProtocol.registered(connection,client.getClientRegistration()) || token==null
                                || !OAuth2AccessToken.TokenType.BEARER.equals(token.getTokenType()) || token.getExpiresAt()==null
                                || !token.getExpiresAt().isAfter(clock.instant()) || token.getTokenValue()==null
                                || token.getTokenValue().length()>16384 || !token.getTokenValue().matches("[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+")) return Mono.error(denied());
                        return Mono.defer(()->decoders.get(t.destinationTenantId()).decode(token.getTokenValue()))
                                .timeout(Duration.ofSeconds(5)).onErrorMap(e->denied()).switchIfEmpty(Mono.error(denied()))
                                .flatMap(jwt->{
                                    var observer=EnrollmentObserverProtocol.observer(profile,jwt,token.getExpiresAt(),clock);approved.requireCurrent();
                                    var apiClient=new ApiClient(transport).setBasePath(connection.configurationBaseUri().toString());
                                    apiClient.addDefaultHeader(HttpHeaders.AUTHORIZATION,"Bearer "+token.getTokenValue());apiClient.addDefaultHeader(HttpHeaders.CACHE_CONTROL,"no-cache");
                                    return new InstitutionAuthorityApi(apiClient)
                                            .observeCurrentInstitutionAuthorityGrantWithResponseSpec(t.destinationTenantId(),t.institutionId(),t.issuerAuthorityId(),t.use().name(),null)
                                            .toEntity(byte[].class).timeout(Duration.ofSeconds(5)).switchIfEmpty(Mono.error(unavailable()))
                                            .flatMap(response->{
                                                long received=clock.millis();
                                                if(response.getStatusCode()!=HttpStatus.OK || response.getBody()==null
                                                        || response.getHeaders().getContentType()==null
                                                        || !MediaType.APPLICATION_JSON.isCompatibleWith(response.getHeaders().getContentType())
                                                        || !EnrollmentObserverProtocol.noStore(response.getHeaders().getCacheControl())) return Mono.error(unavailable());
                                                return authorization.recheck(approved).map(current->{
                                                    var now=clock.millis();if(now>=observer.credentialExpiresAtEpochMillis())throw denied();
                                                    VerifiedInstitutionGrantObservation evidence;
                                                    try { evidence=ConfigurationGrantObservationVerifier.verify(t.destinationTenantId(),t.institutionId(),t.issuerAuthorityId(),t.use(),response.getBody(),received); }
                                                    catch(IllegalArgumentException e){throw unavailable();}
                                                    try { BankEnrollmentIntentCodec.requireObservation(t,evidence,now); }
                                                    catch(IllegalStateException e){throw denied();}
                                                    catch(IllegalArgumentException e){throw new ResponseStatusException(HttpStatus.CONFLICT,"Enrollment target no longer current");}
                                                    long until=Math.min(current.validUntilEpochMillis(),observer.credentialExpiresAtEpochMillis());
                                                    var bounded=evidence.limitedTo(until);bounded.requireCurrentAt(now);
                                                    return new ObservedInstitutionGrantEvidence(bounded,observer,received,bounded.usableUntilEpochMillis());
                                                });
                                            });
                                });
                    });
        }).onErrorMap(error->{
            if(error instanceof ResponseStatusException) return error;
            if(error instanceof WebClientResponseException e && (e.getStatusCode().value()==401 || e.getStatusCode().value()==403)) return denied();
            return unavailable();
        });
    }
    private static ResponseStatusException denied() { return new ResponseStatusException(HttpStatus.FORBIDDEN,"Organization enrollment observation denied"); }
    private static ResponseStatusException unavailable() { return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Configuration enrollment observation unavailable"); }
}
