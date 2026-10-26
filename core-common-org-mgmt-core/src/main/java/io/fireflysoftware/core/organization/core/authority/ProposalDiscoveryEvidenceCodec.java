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

public final class ProposalDiscoveryEvidenceCodec {
    public static final String ENCODING="FF_ORGANIZATION_BANK_PROPOSAL_READ_CONTEXT_V2";
    private ProposalDiscoveryEvidenceCodec() { }
    public record Context(int schemaVersion,String nonce,String purpose,String operation,String permission,
            String tenantId,String bankId,String proposalId,long revision,String actionId,ProposalTerminationEvidenceCodec.Actor actor,
            long authorityUntilEpochMillis,long commandUntilEpochMillis,String originalCommandJson,String canonicalDiscoveryRequest,String discoveryRequestDigest) { }
    public record Packet(Context target,String contextEncoding,String canonicalContext,String contextDigest) { }
    public static Packet context(OrganizationProposalAuthorization.DiscoveryAdmission admission,UUID nonce) {
        admission.requireCurrent();AuthorityWire.uuid(nonce.toString());var scope=admission.scope();var a=admission.actor();
        var actor=new ProposalTerminationEvidenceCodec.Actor(a.kind(),a.issuer(),a.subject(),a.tenantId(),admission.staffRealm(),a.staffActorId(),a.clientId(),a.expiresAtEpochMillis());
        var target=new Context(2,nonce.toString(),"PROPOSAL_DISCOVERY",scope.operation().name(),admission.permission(),scope.tenantId(),scope.bankId(),scope.proposalId(),scope.revision(),scope.request().request().actionId(),actor,
                admission.validUntilEpochMillis(),scope.request().request().commandNotAfterEpochMillis(),scope.request().originalJson(),scope.request().canonicalIntent(),scope.request().intentDigest());
        String canonical=AuthorityWire.token(ENCODING)+AuthorityWire.recordToken(target.schemaVersion(),target.nonce(),target.purpose(),target.operation(),target.permission(),target.tenantId(),target.bankId(),target.proposalId(),target.revision(),target.actionId(),target.actor(),target.authorityUntilEpochMillis(),target.commandUntilEpochMillis(),target.canonicalDiscoveryRequest(),target.discoveryRequestDigest());
        return new Packet(target,ENCODING,canonical,AuthorityWire.sha(canonical));
    }
}
