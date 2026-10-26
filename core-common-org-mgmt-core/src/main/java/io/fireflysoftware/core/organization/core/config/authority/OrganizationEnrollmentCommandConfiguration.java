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
package io.fireflysoftware.core.organization.core.config.authority;

import io.fireflysoftware.core.organization.core.authority.OrganizationProposalAuthorization;
import io.fireflysoftware.core.organization.core.services.authority.*;
import io.fireflysoftware.core.organization.core.services.authority.impl.*;
import io.fireflysoftware.core.organization.models.repositories.authority.*;
import org.fireflyframework.security.rs.ResourceServerProperties;
import org.fireflyframework.security.spi.PolicyDecisionPort;
import org.fireflyframework.security.spi.SecurityContextPort;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.r2dbc.core.R2dbcEntityOperations;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.web.reactive.function.client.WebClient;
import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashMap;

@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(name="firefly.organization.enrollment.proposal-commands.enabled",havingValue="true")
@EnableConfigurationProperties({OrganizationEnrollmentReadProperties.class,OrganizationEnrollmentObserverProperties.class})
public class OrganizationEnrollmentCommandConfiguration {
    @Bean
    public OrganizationProposalAuthorization organizationProposalAuthorization(SecurityContextPort callers,PolicyDecisionPort policies,
            @Qualifier("organizationEnrollmentClock") Clock clock,OrganizationEnrollmentReadProperties properties,
            ResourceServerProperties shared,BankEnrollmentActionReadService protectedActionHistory) {
        properties.requireSharedProjection(shared);
        return new OrganizationProposalAuthorization(callers,policies,clock,properties.platformTenantId(),properties.profiles(),properties.staffRealms());
    }
    @Bean
    public BankEnrollmentProposalObservationService bankEnrollmentProposalObservationService(OrganizationProposalAuthorization authorization,
            ReactiveClientRegistrationRepository registrations,ReactiveOAuth2AuthorizedClientManager manager,
            OrganizationEnrollmentObserverProperties properties,@Qualifier("organizationEnrollmentClock") Clock clock) {
        return new BankEnrollmentProposalObservationServiceImpl(authorization,registrations,manager,configurationDecoders(properties,clock),properties.byTenant(),WebClient.builder().build(),clock);
    }
    @Bean
    public BankEnrollmentProposalCommandService bankEnrollmentProposalCommandService(OrganizationProposalAuthorization authorization,
            BankEnrollmentProposalObservationService observer,NativeEnrollmentContextRepository contexts,NativeEnrollmentProposalRepository proposals,
            NativeEnrollmentRevisionRepository revisions,NativeEnrollmentProposalControlRepository controls,NativeEnrollmentActionRepository actions,
            @Qualifier("organizationAuthorityEntityOperations") R2dbcEntityOperations operations,@Qualifier("organizationEnrollmentTransactionManager") R2dbcTransactionManager manager) {
        return new BankEnrollmentProposalCommandServiceImpl(authorization,observer,contexts,proposals,revisions,controls,actions,operations,manager);
    }
    @Bean
    public BankEnrollmentProposalApplicationService bankEnrollmentProposalApplicationService(OrganizationProposalAuthorization authorization,
            NativeEnrollmentContextRepository contexts,NativeEnrollmentProposalRepository proposals,NativeEnrollmentRevisionRepository revisions,
            NativeEnrollmentProposalControlRepository controls,BankEnrollmentProposalCommandService commands,
            @Qualifier("organizationAuthorityEntityOperations") R2dbcEntityOperations operations,@Qualifier("organizationEnrollmentTransactionManager") R2dbcTransactionManager manager) {
        return new BankEnrollmentProposalApplicationServiceImpl(authorization,contexts,proposals,revisions,controls,commands,operations,manager);
    }
    static java.util.Map<String,ReactiveJwtDecoder> configurationDecoders(OrganizationEnrollmentObserverProperties properties,Clock clock) {
        var decoders=new LinkedHashMap<String,ReactiveJwtDecoder>();
        for(var profile:properties.profiles()) {
            var decoder=NimbusReactiveJwtDecoder.withJwkSetUri(profile.jwkSetUri().toString()).jwsAlgorithm(SignatureAlgorithm.RS256).build();
            var timestamps=new JwtTimestampValidator(Duration.ZERO);timestamps.setClock(clock);
            OAuth2TokenValidator<Jwt> audience=jwt->jwt.getAudience()!=null && jwt.getAudience().contains(profile.audience())
                    ?OAuth2TokenValidatorResult.success():OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token","Invalid configuration audience",null));
            decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(timestamps,new JwtIssuerValidator(profile.issuer()),audience));
            decoders.put(profile.tenantId(),decoder);
        }
        return java.util.Map.copyOf(decoders);
    }

}
