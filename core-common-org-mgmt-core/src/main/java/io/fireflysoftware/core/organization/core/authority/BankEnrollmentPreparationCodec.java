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

import io.fireflysoftware.core.organization.interfaces.authority.BankEnrollmentPreparationRequest;
import java.util.HashSet;
import java.util.Set;

public final class BankEnrollmentPreparationCodec {
    private BankEnrollmentPreparationCodec() { }
    public static BankEnrollmentPreparationRequest.Mode requestedMode(byte[] bytes) {
        return AuthorityWire.record(AuthorityWire.parse(AuthorityWire.original(bytes,131072)),BankEnrollmentPreparationRequest.class,Set.of()).mode();
    }
    public static OrganizationAuthorityIntent<BankEnrollmentPreparationRequest> decode(String tenant,String bank,
            BankEnrollmentPreparationRequest.Mode mode,long now,byte[] bytes) {
        AuthorityWire.uuid(tenant);AuthorityWire.uuid(bank);
        if(mode==null || now<=0 || now>253402300799999L)throw AuthorityWire.bad();
        String original=AuthorityWire.original(bytes,131072);
        var request=AuthorityWire.record(AuthorityWire.parse(original),BankEnrollmentPreparationRequest.class,Set.of());
        if(request.schemaVersion()!=2 || request.mode()!=mode || !tenant.equals(request.destinationTenantId()) || !bank.equals(request.bankId())
                || request.notAfterEpochMillis()<=now || request.notAfterEpochMillis()>253402300799999L || request.notAfterEpochMillis()>now+86400000)throw AuthorityWire.bad();
        for(var id:new String[]{request.actionId(),request.bankId(),request.destinationTenantId(),request.institutionId(),request.issuerAuthorityId()})AuthorityWire.uuid(id);
        AuthorityWire.text(request.reason(),4096);
        if(request.allowedUses().isEmpty() || request.allowedUses().size()>3 || request.evidenceReferences().isEmpty() || request.evidenceReferences().size()>32
                || new HashSet<>(request.evidenceReferences()).size()!=request.evidenceReferences().size())throw AuthorityWire.bad();
        int previous=-1;for(var use:request.allowedUses()){if(use==null || use.ordinal()<=previous)throw AuthorityWire.bad();previous=use.ordinal();}
        request.evidenceReferences().forEach(ref->AuthorityWire.text(ref,512));
        String canonical=AuthorityWire.token("FF_ORGANIZATION_BANK_PREPARATION_REQUEST_V2")+AuthorityWire.recordToken(tenant,bank,mode,request);
        return new OrganizationAuthorityIntent<>(original,request,canonical,AuthorityWire.sha(canonical));
    }
}
