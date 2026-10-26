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

import io.fireflysoftware.core.organization.core.authority.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.Arrays;

final class EnrollmentObserverProtocol {
    private EnrollmentObserverProtocol() { }
    static ObservedInstitutionGrantEvidence.Observer observer(EnrollmentObserverProfile profile,Jwt jwt,Instant accessTokenExpiry,Clock clock) {
        var p=profile.connection();var policy=profile.claims();var expiry=jwt.getExpiresAt();var nbf=jwt.getNotBefore();
        if(jwt.getIssuer()==null || !p.issuer().equals(jwt.getIssuer().toString()) || !p.subject().equals(jwt.getSubject())
                || jwt.getAudience()==null || !jwt.getAudience().contains(p.audience()) || !p.tenantId().equals(jwt.getClaims().get(policy.tenantClaim()))
                || !policy.machineValue().equals(jwt.getClaims().get(policy.kindClaim()))
                || jwt.getClaims().containsKey(policy.staffActorClaim()) || !p.clientId().equals(jwt.getClaims().get(policy.clientClaim()))
                || expiry==null || !expiry.isAfter(clock.instant()) || expiry.isAfter(Instant.parse("9999-12-31T23:59:59Z"))
                || nbf!=null && nbf.isAfter(clock.instant())) throw denied();
        long until=Math.min(expiry.toEpochMilli(),accessTokenExpiry.toEpochMilli());
        if(clock.millis()>=until) throw denied();
        return new ObservedInstitutionGrantEvidence.Observer(p.issuer(),p.subject(),p.clientId(),p.tenantId(),p.audience(),clock.millis(),until);
    }
    static boolean registered(InstitutionAuthorityObserverProfile p,ClientRegistration r) {
        return r!=null && p.registrationId().equals(r.getRegistrationId()) && AuthorizationGrantType.CLIENT_CREDENTIALS.equals(r.getAuthorizationGrantType())
                && p.clientId().equals(r.getClientId()) && p.tokenEndpoint().toString().equals(r.getProviderDetails().getTokenUri());
    }
    static boolean noStore(String value) { return value!=null && Arrays.stream(value.split(",")).anyMatch(s->"no-store".equalsIgnoreCase(s.trim())); }
    private static ResponseStatusException denied(){return new ResponseStatusException(HttpStatus.FORBIDDEN,"Organization enrollment observation denied");}
}
