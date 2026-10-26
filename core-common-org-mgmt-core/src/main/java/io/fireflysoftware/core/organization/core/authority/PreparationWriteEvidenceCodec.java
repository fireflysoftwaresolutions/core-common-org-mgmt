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

import io.fireflysoftware.core.organization.interfaces.authority.*;
import java.util.UUID;
import java.util.List;

public final class PreparationWriteEvidenceCodec {
    public static final String COMMAND_ENCODING="FF_ORGANIZATION_BANK_PREPARE_COMMAND_V2";
    public static final String CONTEXT_ENCODING="FF_ORGANIZATION_BANK_PREPARATION_CONTEXT_V2";
    public static final String EVENT_ENCODING="FF_ORGANIZATION_BANK_PREPARED_PROPOSAL_EVENT_V2";
    private PreparationWriteEvidenceCodec() { }
    public record Command(int schemaVersion,String tenantId,String bankId,String intendedRoute,String originalDesiredJson,String canonicalDesiredRequest,String desiredRequestDigest,String originalRevisionJson,String canonicalRevision,String revisionDigest) { }
    public record Context(int schemaVersion,String nonce,String purpose,String operation,String intendedRoute,String permission,String tenantId,String bankId,String proposalId,long revision,String actionId,ProposalTerminationEvidenceCodec.Actor actor,long authorityUntilEpochMillis,long commandUntilEpochMillis,String originalCommandJson,String canonicalCommand,String commandDigest) { }
    public record Packet(Context target,List<ProposalTerminationEvidenceCodec.GrantObservation> observations,long observationsUntilEpochMillis,String contextEncoding,String canonicalContext,String contextDigest) { }
    public record Event(int schemaVersion,String eventType,EnrollmentProposalCommandResult result,ProposalTerminationEvidenceCodec.Actor actor,String commandEncoding,String originalCommandJson,String canonicalCommand,String commandDigest,List<ProposalTerminationEvidenceCodec.GrantObservation> observations,long observationsUntilEpochMillis) { }
    public static ProposalTerminationEvidenceCodec.Encoded command(OrganizationPreparationAuthorization.Prepared admission) {
        admission.requireCurrent();var plan=admission.plan();var desired=plan.desired();var revision=plan.revision();var wanted=desired.request();
        var command=new Command(2,wanted.destinationTenantId(),wanted.bankId(),route(admission),desired.originalJson(),desired.canonicalIntent(),desired.intentDigest(),revision.originalJson(),revision.canonicalIntent(),revision.intentDigest());
        String canonical=AuthorityWire.token(COMMAND_ENCODING)+AuthorityWire.recordToken(command.schemaVersion(),command.tenantId(),command.bankId(),command.intendedRoute(),command.canonicalDesiredRequest(),command.desiredRequestDigest(),command.canonicalRevision(),command.revisionDigest());
        return new ProposalTerminationEvidenceCodec.Encoded(ProposalTerminationEvidenceCodec.json(command),COMMAND_ENCODING,canonical,AuthorityWire.sha(canonical));
    }
    public static ProposalTerminationEvidenceCodec.Encoded retainedCommand(String tenant,String bank,String action,String operation,String route,long recordedAt,byte[] original,OrganizationAuthorityIntent<BankEnrollmentRevision> retained) {
        String json=AuthorityWire.original(original,4194304);var value=AuthorityWire.record(AuthorityWire.parse(json),Command.class,java.util.Set.of(),java.util.Set.of("originalDesiredJson","originalRevisionJson"));
        var revision=retained.request();var target=BankEnrollmentRevisionCodec.enrollmentTarget(revision).request();var mode=BankEnrollmentPreparationRequest.Mode.valueOf(target.mode().name());
        var desired=BankEnrollmentPreparationCodec.decode(tenant,bank,mode,recordedAt,value.originalDesiredJson().getBytes(java.nio.charset.StandardCharsets.UTF_8));var wanted=desired.request();
        String expectedOperation=mode==BankEnrollmentPreparationRequest.Mode.ENROLL?"PREPARE":"TRANSFER_PREPARE",expectedRoute=route(tenant,bank);
        if(value.schemaVersion()!=2 || !tenant.equals(value.tenantId()) || !bank.equals(value.bankId()) || !expectedRoute.equals(value.intendedRoute()) || !expectedRoute.equals(route) || !expectedOperation.equals(operation)
                || !action.equals(wanted.actionId()) || !tenant.equals(target.destinationTenantId()) || !bank.equals(target.bankId()) || !wanted.institutionId().equals(target.institutionId()) || !wanted.issuerAuthorityId().equals(target.issuerAuthorityId())
                || wanted.notAfterEpochMillis()!=target.commandDeadlineEpochMillis() || !wanted.allowedUses().equals(revision.allowedUses()) || !wanted.reason().equals(revision.reason()) || !wanted.evidenceReferences().equals(revision.evidenceReferences())
                || !value.canonicalDesiredRequest().equals(desired.canonicalIntent()) || !value.desiredRequestDigest().equals(desired.intentDigest())
                || !value.originalRevisionJson().equals(retained.originalJson()) || !value.canonicalRevision().equals(retained.canonicalIntent()) || !value.revisionDigest().equals(retained.intentDigest())
                || new java.util.HashSet<>(java.util.List.of(action,revision.proposalId(),target.enrollmentId(),target.actionId())).size()!=4)throw AuthorityWire.bad();
        String canonical=AuthorityWire.token(COMMAND_ENCODING)+AuthorityWire.recordToken(value.schemaVersion(),value.tenantId(),value.bankId(),value.intendedRoute(),value.canonicalDesiredRequest(),value.desiredRequestDigest(),value.canonicalRevision(),value.revisionDigest());
        return new ProposalTerminationEvidenceCodec.Encoded(json,COMMAND_ENCODING,canonical,AuthorityWire.sha(canonical));
    }
    public static EnrollmentPreparedProposal recover(EnrollmentActionHistory history,byte[] originalDesired) {
        if(history==null || history.schemaVersion()!=2 || !"APPLIED".equals(history.outcome()) || !"COMMAND".equals(history.resolutionKind())
                || !java.util.Set.of("PREPARE","TRANSFER_PREPARE").contains(history.intendedOperation()) || history.approvedRevision()==null
                || !COMMAND_ENCODING.equals(history.request().encoding()) || !COMMAND_ENCODING.equals(history.resolution().encoding())
                || !ProposalTerminationEvidenceCodec.REVISION_ENCODING.equals(history.approvedRevision().encoding()) || !ProposalTerminationEvidenceCodec.RESULT_ENCODING.equals(history.receipt().encoding())
                || !history.request().equals(history.resolution()))throw AuthorityWire.bad();
        var payload=history.approvedRevision();var revision=BankEnrollmentRevisionCodec.decode(payload.originalJson().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        if(!revision.canonicalIntent().equals(payload.canonical()) || !revision.intentDigest().equals(payload.digest()))throw AuthorityWire.bad();
        var command=retainedCommand(history.tenantId(),history.bankId(),history.actionId(),history.intendedOperation(),history.intendedRoute(),history.recordedAtEpochMillis(),history.request().originalJson().getBytes(java.nio.charset.StandardCharsets.UTF_8),revision);
        if(!command.canonical().equals(history.request().canonical()) || !command.digest().equals(history.request().digest()))throw AuthorityWire.bad();
        var value=AuthorityWire.record(AuthorityWire.parse(command.originalJson()),Command.class,java.util.Set.of(),java.util.Set.of("originalDesiredJson","originalRevisionJson"));
        var mode=BankEnrollmentPreparationRequest.Mode.valueOf(BankEnrollmentRevisionCodec.enrollmentTarget(revision.request()).request().mode().name());
        var desired=BankEnrollmentPreparationCodec.decode(history.tenantId(),history.bankId(),mode,history.recordedAtEpochMillis(),originalDesired);
        if(!desired.canonicalIntent().equals(value.canonicalDesiredRequest()) || !desired.intentDigest().equals(value.desiredRequestDigest()))throw AuthorityWire.bad();
        var result=AuthorityWire.record(AuthorityWire.parse(history.receipt().originalJson()),EnrollmentProposalCommandResult.class,java.util.Set.of());var encoded=ProposalTerminationEvidenceCodec.result(result);
        if(!encoded.canonical().equals(history.receipt().canonical()) || !encoded.digest().equals(history.receipt().digest()) || result.schemaVersion()!=2
                || !result.actionId().equals(history.actionId()) || !result.tenantId().equals(history.tenantId()) || !result.bankId().equals(history.bankId())
                || !result.proposalId().equals(revision.request().proposalId()) || !result.targetDigest().equals(revision.intentDigest()) || result.revision()!=1
                || !result.operation().equals(history.intendedOperation()) || !"DRAFT".equals(result.state()) || result.controlVersion()!=0 || result.recordedAtEpochMillis()!=history.recordedAtEpochMillis())throw AuthorityWire.bad();
        return new EnrollmentPreparedProposal(2,result,payload);
    }
    public static Packet context(OrganizationPreparationAuthorization.Prepared admission,UUID nonce) {
        admission.requireCurrent();AuthorityWire.uuid(nonce.toString());var desired=admission.plan().desired().request();var revision=admission.plan().revision().request();var command=command(admission);var observations=observations(admission);
        var target=new Context(2,nonce.toString(),"PREPARATION_MUTATION",operation(admission),route(admission),admission.permission(),desired.destinationTenantId(),desired.bankId(),revision.proposalId(),revision.revision(),desired.actionId(),actor(admission),admission.validUntilEpochMillis(),desired.notAfterEpochMillis(),command.originalJson(),command.canonical(),command.digest());
        long until=until(admission,observations);
        String canonical=AuthorityWire.token(CONTEXT_ENCODING)+AuthorityWire.recordToken(target.schemaVersion(),target.nonce(),target.purpose(),target.operation(),target.intendedRoute(),target.permission(),target.tenantId(),target.bankId(),target.proposalId(),target.revision(),target.actionId(),target.actor(),target.authorityUntilEpochMillis(),target.commandUntilEpochMillis(),target.canonicalCommand(),target.commandDigest(),until)+observationCanonical(observations);
        return new Packet(target,observations,until,CONTEXT_ENCODING,canonical,AuthorityWire.sha(canonical));
    }
    public static ProposalTerminationEvidenceCodec.Encoded event(OrganizationPreparationAuthorization.Prepared admission,EnrollmentProposalCommandResult result) {
        var command=command(admission);var observations=observations(admission);long until=until(admission,observations);
        var event=new Event(2,"organization.bank.enrollment.proposal.prepared",result,actor(admission),COMMAND_ENCODING,command.originalJson(),command.canonical(),command.digest(),observations,until);
        String canonical=AuthorityWire.token(EVENT_ENCODING)+AuthorityWire.recordToken(event.schemaVersion(),event.eventType(),event.result(),event.actor(),event.commandEncoding(),event.canonicalCommand(),event.commandDigest(),until)+observationCanonical(observations);
        return new ProposalTerminationEvidenceCodec.Encoded(ProposalTerminationEvidenceCodec.json(event),EVENT_ENCODING,canonical,AuthorityWire.sha(canonical));
    }
    private static List<ProposalTerminationEvidenceCodec.GrantObservation> observations(OrganizationPreparationAuthorization.Prepared admission) {
        admission.requireCurrent();return admission.plan().observations().stream().map(e->new ProposalTerminationEvidenceCodec.GrantObservation(e.observation().originalObservationJson(),e.observation().observation(),e.observer(),e.receivedAtEpochMillis(),e.usableUntilEpochMillis())).toList();
    }
    private static long until(OrganizationPreparationAuthorization.Prepared admission,List<ProposalTerminationEvidenceCodec.GrantObservation> observations){return Math.min(admission.validUntilEpochMillis(),observations.stream().mapToLong(ProposalTerminationEvidenceCodec.GrantObservation::usableUntilEpochMillis).min().orElseThrow());}
    private static String observationCanonical(List<ProposalTerminationEvidenceCodec.GrantObservation> observations){String canonical=AuthorityWire.token(observations.size());for(var e:observations)canonical+=AuthorityWire.recordToken(e.observation(),e.observer(),e.receivedAtEpochMillis(),e.usableUntilEpochMillis());return canonical;}
    public static String operation(OrganizationPreparationAuthorization.Prepared admission){return admission.plan().desired().request().mode()==BankEnrollmentPreparationRequest.Mode.ENROLL?"PREPARE":"TRANSFER_PREPARE";}
    public static String route(OrganizationPreparationAuthorization.Prepared admission){var desired=admission.plan().desired().request();return route(desired.destinationTenantId(),desired.bankId());}
    private static String route(String tenant,String bank){return "POST /api/v1/platform/tenants/"+tenant+"/banks/"+bank+"/enrollment-proposals";}
    private static ProposalTerminationEvidenceCodec.Actor actor(OrganizationPreparationAuthorization.Prepared admission){var a=admission.actor();return new ProposalTerminationEvidenceCodec.Actor(a.kind(),a.issuer(),a.subject(),a.tenantId(),admission.staffRealm(),a.staffActorId(),a.clientId(),a.expiresAtEpochMillis());}
}
