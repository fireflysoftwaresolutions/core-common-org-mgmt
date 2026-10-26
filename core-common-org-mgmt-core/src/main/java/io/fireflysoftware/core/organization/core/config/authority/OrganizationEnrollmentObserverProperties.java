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

import io.fireflysoftware.core.organization.core.authority.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import java.net.URI;
import java.util.*;

@ConfigurationProperties(prefix="firefly.organization.enrollment.observers")
public record OrganizationEnrollmentObserverProperties(List<Profile> profiles) {
    public record Profile(String tenantId,String registrationId,String issuer,String clientId,String subject,String audience,
            URI configurationBaseUri,URI tokenEndpoint,URI jwkSetUri,String tenantClaim,String kindClaim,String staffActorClaim,
            String clientClaim,String staffValue,String machineValue) {
        public EnrollmentObserverProfile observer() {
            return new EnrollmentObserverProfile(new InstitutionAuthorityObserverProfile(tenantId,registrationId,issuer,clientId,subject,audience,configurationBaseUri,tokenEndpoint),
                    new OrganizationActorIssuerProfile(issuer,tenantClaim,kindClaim,staffActorClaim,clientClaim,staffValue,machineValue));
        }
    }
    public OrganizationEnrollmentObserverProperties {
        if(profiles==null || profiles.isEmpty())throw new IllegalArgumentException("Explicit tenant-bound configuration observer profiles required");
        profiles=List.copyOf(profiles);var tenants=new HashSet<String>();var registrations=new HashSet<String>();
        for(var profile:profiles) {
            profile.observer();var uri=profile.jwkSetUri();
            if(uri==null || !"https".equals(uri.getScheme()) || uri.getHost()==null || uri.getUserInfo()!=null || uri.getRawQuery()!=null || uri.getFragment()!=null
                    || !tenants.add(profile.tenantId()) || !registrations.add(profile.registrationId()))throw new IllegalArgumentException("Unique tenant-bound observer registrations and trusted HTTPS JWKS required");
        }
    }
    public Map<String,EnrollmentObserverProfile> byTenant(){var result=new LinkedHashMap<String,EnrollmentObserverProfile>();profiles.forEach(p->result.put(p.tenantId(),p.observer()));return Map.copyOf(result);}
}
