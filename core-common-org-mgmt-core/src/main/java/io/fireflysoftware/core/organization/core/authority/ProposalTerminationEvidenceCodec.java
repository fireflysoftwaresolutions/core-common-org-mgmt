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

import com.fasterxml.jackson.databind.json.JsonMapper;
import io.fireflysoftware.core.organization.interfaces.authority.EnrollmentProposalCommandResult;
import java.util.UUID;
import java.util.List;

public final class ProposalTerminationEvidenceCodec {
    public static final String COMMAND_ENCODING="FF_ORGANIZATION_BANK_ENROLLMENT_REVISION_COMMAND_V2";
    public static final String REVISION_ENCODING="FF_ORGANIZATION_BANK_ENROLLMENT_REVISION_V2";
    public static final String RESULT_ENCODING="FF_ORGANIZATION_BANK_PROPOSAL_RESULT_V2";
    public static final String EVENT_ENCODING="FF_ORGANIZATION_BANK_PROPOSAL_EVENT_V2";
    private static final String CONTEXT_ENCODING="FF_ORGANIZATION_NATIVE_MUTATION_CONTEXT_V2";
    private static final JsonMapper JSON=new JsonMapper();
    private ProposalTerminationEvidenceCodec() { }
    public record Encoded(String originalJson,String encoding,String canonical,String digest) { }
    public record Actor(String kind,String issuer,String subject,String tenantId,String staffRealm,String staffActorId,String clientId,long expiresAtEpochMillis) { }
    public record Context(int schemaVersion,String nonce,String purpose,String operation,String intendedRoute,String permission,
            String tenantId,String bankId,String proposalId,long revision,String actionId,Actor actor,long authorityUntilEpochMillis,
            long commandUntilEpochMillis,String originalRevisionJson,String canonicalRevision,String revisionDigest,
            String originalCommandJson,String canonicalCommand,String commandDigest) { }
    public record ContextPacket(Context target,String contextEncoding,String canonicalContext,String contextDigest) { }
    public record Event(int schemaVersion,String eventType,EnrollmentProposalCommandResult result,Actor actor,
            String commandEncoding,String originalCommandJson,String canonicalCommand,String commandDigest,
            String revisionEncoding,String originalRevisionJson,String canonicalRevision,String revisionDigest) { }
    public static ContextPacket context(OrganizationProposalAuthorization.Admission admission,UUID nonce) {
        admission.requireCurrent();AuthorityWire.uuid(nonce.toString());var scope=admission.scope();var request=admission.command();
        var context=new Context(2,nonce.toString(),"MUTATION",request.request().operation().name(),route(admission),admission.permission(),
                scope.tenantId(),scope.bankId(),scope.proposalId(),scope.revision(),request.request().actionId(),actor(admission),
                admission.validUntilEpochMillis(),request.request().commandNotAfterEpochMillis(),scope.target().originalJson(),
                scope.target().canonicalIntent(),scope.target().intentDigest(),request.originalJson(),request.canonicalIntent(),request.intentDigest());
        String canonical=AuthorityWire.token(CONTEXT_ENCODING)+AuthorityWire.recordToken(context.schemaVersion(),context.nonce(),context.purpose(),
                context.operation(),context.intendedRoute(),context.permission(),context.tenantId(),context.bankId(),context.proposalId(),context.revision(),
                context.actionId(),context.actor(),context.authorityUntilEpochMillis(),context.commandUntilEpochMillis(),context.canonicalRevision(),
                context.revisionDigest(),context.canonicalCommand(),context.commandDigest());
        return new ContextPacket(context,CONTEXT_ENCODING,canonical,AuthorityWire.sha(canonical));
    }
    public record GrantObservation(String originalObservationJson,
            io.fireflysoftware.core.organization.interfaces.authority.ConfigurationGrantObservation observation,
            ObservedInstitutionGrantEvidence.Observer observer,long receivedAtEpochMillis,long usableUntilEpochMillis) { }
    public record ObservedContextPacket(Context target,List<GrantObservation> observations,long observationsUntilEpochMillis,
            String contextEncoding,String canonicalContext,String contextDigest) { }
    public record ObservedEvent(Event proposal,List<GrantObservation> observations,long observationsUntilEpochMillis) { }
    private static List<GrantObservation> observationPackets(OrganizationProposalAuthorization.Admission admission,List<ObservedInstitutionGrantEvidence> evidence) {
        long now=admission.currentEpochMillis();var snapshot=List.copyOf(evidence);snapshot.forEach(e->e.requireCurrentAt(now));
        BankEnrollmentRevisionCodec.requireObservationSet(admission.scope().target(),snapshot.stream().map(ObservedInstitutionGrantEvidence::observation).toList(),now);
        return snapshot.stream().map(e->new GrantObservation(e.observation().originalObservationJson(),e.observation().observation(),e.observer(),e.receivedAtEpochMillis(),e.usableUntilEpochMillis())).toList();
    }
    private static String observationCanonical(List<GrantObservation> evidence) {
        String canonical=AuthorityWire.token(evidence.size());
        for(var e:evidence)canonical+=AuthorityWire.recordToken(e.observation(),e.observer(),e.receivedAtEpochMillis(),e.usableUntilEpochMillis());
        return canonical;
    }
    public static ObservedContextPacket observedContext(OrganizationProposalAuthorization.Admission admission,UUID nonce,List<ObservedInstitutionGrantEvidence> evidence) {
        var base=context(admission,nonce);var observations=observationPackets(admission,evidence);
        long until=Math.min(admission.validUntilEpochMillis(),observations.stream().mapToLong(GrantObservation::usableUntilEpochMillis).min().orElseThrow());
        String encoding="FF_ORGANIZATION_NATIVE_OBSERVED_PROPOSAL_CONTEXT_V2";
        String canonical=AuthorityWire.token(encoding)+AuthorityWire.recordToken(base.canonicalContext(),base.contextDigest(),until)+observationCanonical(observations);
        return new ObservedContextPacket(base.target(),observations,until,encoding,canonical,AuthorityWire.sha(canonical));
    }
    public static Encoded observedEvent(OrganizationProposalAuthorization.Admission admission,EnrollmentProposalCommandResult result,List<ObservedInstitutionGrantEvidence> evidence) {
        var command=admission.command();var revision=admission.scope().target();var observations=observationPackets(admission,evidence);
        String type=switch(command.request().operation()){case SUBMIT->"submitted";case APPROVE->"approved";default->throw AuthorityWire.bad();};
        var event=new Event(2,"organization.bank.enrollment.proposal."+type,result,actor(admission),COMMAND_ENCODING,
                command.originalJson(),command.canonicalIntent(),command.intentDigest(),REVISION_ENCODING,revision.originalJson(),revision.canonicalIntent(),revision.intentDigest());
        long until=Math.min(admission.validUntilEpochMillis(),observations.stream().mapToLong(GrantObservation::usableUntilEpochMillis).min().orElseThrow());
        String encoding="FF_ORGANIZATION_BANK_OBSERVED_PROPOSAL_EVENT_V2";
        String canonical=AuthorityWire.token(encoding)+AuthorityWire.recordToken(event.schemaVersion(),event.eventType(),result,event.actor(),COMMAND_ENCODING,event.canonicalCommand(),event.commandDigest(),REVISION_ENCODING,event.canonicalRevision(),event.revisionDigest(),until)+observationCanonical(observations);
        return new Encoded(json(new ObservedEvent(event,observations,until)),encoding,canonical,AuthorityWire.sha(canonical));
    }
    public static Encoded result(EnrollmentProposalCommandResult result) {
        String canonical=AuthorityWire.token(RESULT_ENCODING)+AuthorityWire.token(result);
        return new Encoded(json(result),RESULT_ENCODING,canonical,AuthorityWire.sha(canonical));
    }
    public static Encoded event(OrganizationProposalAuthorization.Admission admission,EnrollmentProposalCommandResult result) {
        var command=admission.command();var revision=admission.scope().target();
        var event=new Event(2,"organization.bank.enrollment.proposal.terminated",result,actor(admission),COMMAND_ENCODING,
                command.originalJson(),command.canonicalIntent(),command.intentDigest(),REVISION_ENCODING,
                revision.originalJson(),revision.canonicalIntent(),revision.intentDigest());
        String canonical=AuthorityWire.token(EVENT_ENCODING)+AuthorityWire.recordToken(event.schemaVersion(),event.eventType(),result,event.actor(),
                COMMAND_ENCODING,event.canonicalCommand(),event.commandDigest(),REVISION_ENCODING,event.canonicalRevision(),event.revisionDigest());
        return new Encoded(json(event),EVENT_ENCODING,canonical,AuthorityWire.sha(canonical));
    }
    public static String json(Object value){try{return JSON.writeValueAsString(value);}catch(Exception error){throw AuthorityWire.bad();}}
    public static String route(OrganizationProposalAuthorization.Admission admission) {
        var scope=admission.scope();String suffix=switch(admission.command().request().operation()) {
            case SUBMIT->"submit";case APPROVE->"approve";case REJECT->"reject";case WITHDRAW->"withdraw";case CANCEL->"cancel";case CANCEL_EMERGENCY->"emergency-cancel";
        };
        return "POST /api/v1/platform/tenants/"+scope.tenantId()+"/banks/"+scope.bankId()+"/enrollment-proposals/"+scope.proposalId()+"/"+suffix;
    }
    private static Actor actor(OrganizationProposalAuthorization.Admission admission) {
        var a=admission.actor();return new Actor(a.kind(),a.issuer(),a.subject(),a.tenantId(),admission.staffRealm(),a.staffActorId(),a.clientId(),a.expiresAtEpochMillis());
    }
}
