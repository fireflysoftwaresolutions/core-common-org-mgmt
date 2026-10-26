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
package io.fireflysoftware.core.organization.core.authority;

import java.net.URI;
import java.util.Set;

public record OrganizationActorIssuerProfile(String issuer,String tenantClaim,String kindClaim,
        String staffActorClaim,String clientClaim,String staffValue,String machineValue) {
    public OrganizationActorIssuerProfile {
        AuthorityWire.text(issuer,500);
        var uri=URI.create(issuer);
        if(!"https".equals(uri.getScheme()) || uri.getHost()==null || uri.getUserInfo()!=null
                || uri.getRawQuery()!=null || uri.getFragment()!=null) throw AuthorityWire.bad();
        for(String claim:new String[]{tenantClaim,kindClaim,staffActorClaim,clientClaim}) {
            if(claim==null || !claim.matches("[A-Za-z_][A-Za-z0-9_.-]{0,63}")
                    || Set.of("iss","sub","aud","exp","nbf").contains(claim)) throw AuthorityWire.bad();
        }
        if(Set.of(tenantClaim,kindClaim,staffActorClaim,clientClaim).size()!=4) throw AuthorityWire.bad();
        AuthorityWire.text(staffValue,64);AuthorityWire.text(machineValue,64);
        if(staffValue.equals(machineValue)) throw AuthorityWire.bad();
    }
}
