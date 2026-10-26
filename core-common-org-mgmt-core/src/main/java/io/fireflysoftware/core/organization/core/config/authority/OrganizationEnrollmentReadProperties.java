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

import io.fireflysoftware.core.organization.core.authority.OrganizationActorIssuerProfile;
import org.springframework.boot.context.properties.ConfigurationProperties;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@ConfigurationProperties(prefix="firefly.organization.enrollment")
public record OrganizationEnrollmentReadProperties(String platformTenantId,List<CallerProfile> callerProfiles) {
    public record CallerProfile(String issuer,String tenantClaim,String kindClaim,String staffActorClaim,
            String clientClaim,String staffValue,String machineValue,String staffRealm) { }
    public OrganizationEnrollmentReadProperties {
        if(platformTenantId==null || callerProfiles==null || callerProfiles.isEmpty())throw new IllegalArgumentException("Explicit enrollment caller profiles required");
        callerProfiles=List.copyOf(callerProfiles);
    }
    public Map<String,OrganizationActorIssuerProfile> profiles() {
        var result=new LinkedHashMap<String,OrganizationActorIssuerProfile>();
        for(var profile:callerProfiles) {
            var actor=new OrganizationActorIssuerProfile(profile.issuer(),profile.tenantClaim(),profile.kindClaim(),profile.staffActorClaim(),
                    profile.clientClaim(),profile.staffValue(),profile.machineValue());
            if(result.put(actor.issuer(),actor)!=null)throw new IllegalArgumentException("Unique enrollment caller issuer profiles required");
        }
        return Map.copyOf(result);
    }
    public Map<String,String> staffRealms() {
        var result=new LinkedHashMap<String,String>();for(var profile:callerProfiles)if(profile.staffRealm()!=null)result.put(profile.issuer(),profile.staffRealm());return Map.copyOf(result);
    }
    public void requireSharedProjection(org.fireflyframework.security.rs.ResourceServerProperties shared) {
        if(!shared.isTenantRequired() || shared.getTenantClaimPaths().isEmpty() || !shared.isStrictPolicy()
                || shared.getIssuer()==null || shared.getIssuer().isBlank() || shared.getAudiences().isEmpty()
                || shared.getAudiences().stream().anyMatch(value->value==null || value.isBlank())
                || callerProfiles.stream().anyMatch(p->!shared.getTenantClaimPaths().contains(p.tenantClaim())))
            throw new IllegalStateException("Native enrollment requires strict shared issuer/audience/tenant projection configuration");
    }
}
