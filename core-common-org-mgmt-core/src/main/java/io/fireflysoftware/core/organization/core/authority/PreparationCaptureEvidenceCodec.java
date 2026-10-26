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

import java.util.UUID;

public final class PreparationCaptureEvidenceCodec {
    public static final String ENCODING="FF_ORGANIZATION_BANK_PREPARATION_READ_CONTEXT_V2";
    private PreparationCaptureEvidenceCodec() { }
    public record Context(int schemaVersion,String nonce,String purpose,String operation,String permission,
            String tenantId,String bankId,String actionId,ProposalTerminationEvidenceCodec.Actor actor,
            long authorityUntilEpochMillis,long commandUntilEpochMillis,String originalDesiredJson,String canonicalDesiredRequest,String desiredRequestDigest) { }
    public record Packet(Context target,String contextEncoding,String canonicalContext,String contextDigest) { }
    public static Packet context(OrganizationPreparationAuthorization.Discovery admission,UUID nonce) {
        admission.requireCurrent();AuthorityWire.uuid(nonce.toString());var desired=admission.desired();var wanted=desired.request();var a=admission.actor();
        var actor=new ProposalTerminationEvidenceCodec.Actor(a.kind(),a.issuer(),a.subject(),a.tenantId(),admission.staffRealm(),a.staffActorId(),a.clientId(),a.expiresAtEpochMillis());
        var target=new Context(2,nonce.toString(),"PREPARATION_DISCOVERY",wanted.mode().name(),admission.permission(),wanted.destinationTenantId(),wanted.bankId(),wanted.actionId(),actor,
                admission.validUntilEpochMillis(),wanted.notAfterEpochMillis(),desired.originalJson(),desired.canonicalIntent(),desired.intentDigest());
        String canonical=AuthorityWire.token(ENCODING)+AuthorityWire.recordToken(target.schemaVersion(),target.nonce(),target.purpose(),target.operation(),target.permission(),target.tenantId(),target.bankId(),target.actionId(),target.actor(),target.authorityUntilEpochMillis(),target.commandUntilEpochMillis(),target.canonicalDesiredRequest(),target.desiredRequestDigest());
        return new Packet(target,ENCODING,canonical,AuthorityWire.sha(canonical));
    }
}
