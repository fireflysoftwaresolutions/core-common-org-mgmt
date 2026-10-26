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

import io.fireflysoftware.core.organization.interfaces.authority.BankEnrollmentRevision;
import io.fireflysoftware.core.organization.interfaces.authority.EnrollmentProposalCommand;
import org.fireflyframework.security.spi.PolicyDecisionPort;
import org.fireflyframework.security.spi.SecurityContextPort;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import java.time.Clock;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** Current caller/policy admission over retained facts; native state must independently recheck them. */
public final class OrganizationProposalAuthorization {
    public record Maker(String issuer, String subject, String actorTenantId, String staffRealm, String staffActorId) { }
    public record ProposalScope(String tenantId, String bankId, String proposalId, long revision,
            long controlVersion, long bankGeneration, BankEnrollmentLifecycle.ProposalState state,
            OrganizationAuthorityIntent<BankEnrollmentRevision> target, Maker maker) { }
    public record DiscoveryScope(EnrollmentProposalCommand.Operation operation,String tenantId,String bankId,
            String proposalId,long revision,OrganizationAuthorityIntent<EnrollmentProposalCommand> request) { }
    public static final class DiscoveryAdmission {
        private final OrganizationProposalAuthorization owner;
        private final OrganizationAuthorityAuthorization.Actor actor;
        private final DiscoveryScope scope;
        private final long until;
        private DiscoveryAdmission(OrganizationProposalAuthorization owner,OrganizationAuthorityAuthorization.Actor actor,DiscoveryScope scope,long until) {
            this.owner=owner;this.actor=actor;this.scope=scope;this.until=until;
        }
        public OrganizationAuthorityAuthorization.Actor actor(){return actor;}
        public DiscoveryScope scope(){return scope;}
        public String staffRealm(){return "STAFF".equals(actor.kind())?owner.staffRealms.get(actor.issuer()):null;}
        public String permission(){return OrganizationProposalAuthorization.permission(scope.operation());}
        public long validUntilEpochMillis(){return until;}
        public void requireCurrent(){if(owner.clock.millis()>=until)throw denied();}
    }
    public static final class Admission {
        private final OrganizationProposalAuthorization owner;
        private final OrganizationAuthorityAuthorization.Actor actor;
        private final ProposalScope scope;
        private final OrganizationAuthorityIntent<EnrollmentProposalCommand> command;
        private final long until;
        private Admission(OrganizationProposalAuthorization owner, OrganizationAuthorityAuthorization.Actor actor,
                ProposalScope scope, OrganizationAuthorityIntent<EnrollmentProposalCommand> command, long until) {
            this.owner=owner;this.actor=actor;this.scope=scope;this.command=command;this.until=until;
        }
        public OrganizationAuthorityAuthorization.Actor actor() { return actor; }
        public ProposalScope scope() { return scope; }
        public OrganizationAuthorityIntent<EnrollmentProposalCommand> command() { return command; }
        public long validUntilEpochMillis() { return until; }
        public String staffRealm(){return "STAFF".equals(actor.kind())?owner.staffRealms.get(actor.issuer()):null;}
        public String permission(){return OrganizationProposalAuthorization.permission(command.request().operation());}
        public long currentEpochMillis(){requireCurrent();return owner.clock.millis();}
        public void requireCurrent() { if(owner.clock.millis()>=until) throw denied(); }
    }
    private final SecurityContextPort callers;
    private final PolicyDecisionPort policies;
    private final Clock clock;
    private final String platformTenant;
    private final Map<String, String> staffRealms;
    private final OrganizationAuthorityAuthorization actors;

    public OrganizationProposalAuthorization(SecurityContextPort callers, PolicyDecisionPort policies, Clock clock,
            String platformTenant, Map<String, OrganizationActorIssuerProfile> profiles, Map<String, String> staffRealms) {
        this.callers=Objects.requireNonNull(callers);this.policies=Objects.requireNonNull(policies);
        this.clock=Objects.requireNonNull(clock);this.platformTenant=platformTenant;
        this.actors=new OrganizationAuthorityAuthorization(callers,policies,clock,platformTenant,profiles);
        this.staffRealms=Map.copyOf(staffRealms);
        if(this.staffRealms.isEmpty()) throw AuthorityWire.bad();
        this.staffRealms.forEach((issuer,realm)->{if(!profiles.containsKey(issuer)) throw AuthorityWire.bad();AuthorityWire.text(realm,255);});
    }
    public Mono<DiscoveryAdmission> discover(EnrollmentProposalCommand.Operation operation,String tenantId,
            String bankId,String proposalId,long revision,byte[] bytes) {
        return OrganizationAuthorityAuthorization.outsideTransaction().then(Mono.defer(()->{
            long start=clock.millis();var request=EnrollmentProposalCommandCodec.decodeDiscovery(tenantId,bankId,proposalId,operation,revision,start,bytes);
            var scope=new DiscoveryScope(operation,tenantId,bankId,proposalId,revision,request);
            long until=Math.min(start+30000,request.request().commandNotAfterEpochMillis());
            return currentDiscovery(scope).map(actor->{var admission=new DiscoveryAdmission(this,actor,scope,Math.min(until,actor.expiresAtEpochMillis()));admission.requireCurrent();return admission;});
        })).onErrorMap(OrganizationProposalAuthorization::safe);
    }
    public Mono<DiscoveryAdmission> recheckDiscovery(DiscoveryAdmission admission) {
        return OrganizationAuthorityAuthorization.outsideTransaction().then(Mono.defer(()->{
            if(admission==null || admission.owner!=this)throw denied();admission.requireCurrent();
            return currentDiscovery(admission.scope).map(actor->{if(!actor.equals(admission.actor))throw denied();admission.requireCurrent();return admission;});
        })).onErrorMap(OrganizationProposalAuthorization::safe);
    }
    public Mono<Admission> promote(DiscoveryAdmission discovery,ProposalScope scope) {
        return OrganizationAuthorityAuthorization.outsideTransaction().then(Mono.defer(()->{
            if(discovery==null || discovery.owner!=this || scope==null)throw denied();discovery.requireCurrent();
            var known=discovery.scope;
            if(!known.tenantId().equals(scope.tenantId()) || !known.bankId().equals(scope.bankId())
                    || !known.proposalId().equals(scope.proposalId()) || known.revision()!=scope.revision())throw denied();
            return recheckDiscovery(discovery).then(admit(known.operation(),scope,
                    known.request().originalJson().getBytes(java.nio.charset.StandardCharsets.UTF_8))).map(full->{
                discovery.requireCurrent();if(!full.actor.equals(discovery.actor))throw denied();
                var bounded=new Admission(this,full.actor,scope,full.command,Math.min(discovery.until,full.until));bounded.requireCurrent();return bounded;
            });
        })).onErrorMap(OrganizationProposalAuthorization::safe);
    }
    private Mono<OrganizationAuthorityAuthorization.Actor> currentDiscovery(DiscoveryScope scope) {
        return callers.currentPrincipal().switchIfEmpty(Mono.error(denied())).flatMap(principal->{
            var actor=actors.actor(principal);
            if(scope.operation()!=EnrollmentProposalCommand.Operation.CANCEL_EMERGENCY && !"STAFF".equals(actor.kind())
                    || "STAFF".equals(actor.kind()) && !staffRealms.containsKey(actor.issuer())
                    || clock.millis()>=scope.request().request().commandNotAfterEpochMillis())throw denied();
            var context=new HashMap<String,Object>();context.put("stage","PROPOSAL_DISCOVERY");
            context.put("actorKind",actor.kind());context.put("actorIssuer",actor.issuer());context.put("actorSubject",actor.subject());
            context.put("actorTenantId",actor.tenantId());context.put("resourceTenantId",scope.tenantId());
            if(actor.staffActorId()!=null){context.put("staffActorId",actor.staffActorId());context.put("staffRealm",staffRealms.get(actor.issuer()));}
            if(actor.clientId()!=null)context.put("clientId",actor.clientId());
            context.put("bankId",scope.bankId());context.put("proposalId",scope.proposalId());context.put("operation",scope.operation().name());
            context.put("actionId",scope.request().request().actionId());context.put("requestedRevision",scope.revision());
            context.put("canonicalDiscoveryRequest",scope.request().canonicalIntent());context.put("discoveryRequestDigest",scope.request().intentDigest());
            return Mono.defer(()->policies.authorize(principal,permission(scope.operation()),
                    "tenant/"+scope.tenantId()+"/BANK_ENROLLMENT/"+scope.bankId(),Map.copyOf(context)))
                    .timeout(Duration.ofSeconds(5)).filter(d->d.granted()&&d.obligations().isEmpty()).switchIfEmpty(Mono.error(denied()))
                    .map(d->{if(clock.millis()>=actor.expiresAtEpochMillis() || clock.millis()>=scope.request().request().commandNotAfterEpochMillis())throw denied();return actor;});
        }).timeout(Duration.ofSeconds(5));
    }
    Mono<Admission> admit(EnrollmentProposalCommand.Operation operation, ProposalScope scope, byte[] bytes) {
        return OrganizationAuthorityAuthorization.outsideTransaction().then(Mono.defer(()->{
            if(scope==null) throw denied();
            var command=EnrollmentProposalCommandCodec.decodeRevision(scope.tenantId(),scope.bankId(),scope.proposalId(),
                    operation,scope.revision(),scope.target(),clock.millis(),bytes);
            requireExact(scope,command.request());
            long until=Math.min(clock.millis()+30000,command.request().commandNotAfterEpochMillis());
            return current(scope,command).map(actor->{var admission=new Admission(this,actor,scope,command,
                    Math.min(until,actor.expiresAtEpochMillis()));admission.requireCurrent();return admission;});
        })).onErrorMap(OrganizationProposalAuthorization::safe);
    }
    public Mono<Admission> recheck(Admission admission) {
        return OrganizationAuthorityAuthorization.outsideTransaction().then(Mono.defer(()->{
            if(admission==null || admission.owner!=this) throw denied();admission.requireCurrent();
            return current(admission.scope,admission.command).map(actor->{
                if(!actor.equals(admission.actor)) throw denied();admission.requireCurrent();return admission;
            });
        })).onErrorMap(OrganizationProposalAuthorization::safe);
    }
    private Mono<OrganizationAuthorityAuthorization.Actor> current(ProposalScope scope,
            OrganizationAuthorityIntent<EnrollmentProposalCommand> command) {
        return callers.currentPrincipal().switchIfEmpty(Mono.error(denied())).flatMap(principal->{
            var actor=actors.actor(principal);var request=command.request();var maker=scope.maker();
            requireExact(scope,request);requireActor(actor,maker,request.operation());
            if(clock.millis()>=request.commandNotAfterEpochMillis()) throw denied();
            var context=new HashMap<String,Object>();context.put("stage","FULL_PROPOSAL_MUTATION");
            context.put("actorKind",actor.kind());context.put("actorIssuer",actor.issuer());context.put("actorSubject",actor.subject());
            context.put("actorTenantId",actor.tenantId());context.put("resourceTenantId",scope.tenantId());
            if(actor.staffActorId()!=null){context.put("staffActorId",actor.staffActorId());context.put("staffRealm",staffRealms.get(actor.issuer()));}
            if(actor.clientId()!=null)context.put("clientId",actor.clientId());
            context.put("makerIssuer",maker.issuer());context.put("makerSubject",maker.subject());context.put("makerTenantId",maker.actorTenantId());
            context.put("makerStaffRealm",maker.staffRealm());context.put("makerStaffActorId",maker.staffActorId());
            var enrollment=BankEnrollmentRevisionCodec.enrollmentTarget(scope.target().request()).request();
            context.put("institutionId",enrollment.institutionId());context.put("issuerAuthorityId",enrollment.issuerAuthorityId());
            context.put("allowedUses",scope.target().request().allowedUses().stream().map(Enum::name).toList());
            context.put("expectedOwnershipGeneration",enrollment.expectedOwnershipGeneration());context.put("expectedSourceGeneration",enrollment.expectedSourceGeneration());
            context.put("bankId",scope.bankId());context.put("proposalId",scope.proposalId());context.put("operation",request.operation().name());
            context.put("actionId",request.actionId());context.put("expectedRevision",scope.revision());context.put("expectedControlVersion",scope.controlVersion());
            context.put("expectedBankGeneration",scope.bankGeneration());context.put("proposalState",scope.state().name());
            context.put("retainedTargetCanonicalIntent",scope.target().canonicalIntent());context.put("retainedTargetDigest",scope.target().intentDigest());
            context.put("canonicalIntent",command.canonicalIntent());context.put("intentDigest",command.intentDigest());
            return Mono.defer(()->policies.authorize(principal,permission(request.operation()),
                    "tenant/"+scope.tenantId()+"/BANK_ENROLLMENT/"+scope.bankId(),Map.copyOf(context)))
                    .timeout(Duration.ofSeconds(5)).filter(d->d.granted()&&d.obligations().isEmpty())
                    .switchIfEmpty(Mono.error(denied())).map(d->{
                        if(clock.millis()>=actor.expiresAtEpochMillis() || clock.millis()>=request.commandNotAfterEpochMillis()) throw denied();
                        return actor;
                    });
        }).timeout(Duration.ofSeconds(5));
    }
    private void requireActor(OrganizationAuthorityAuthorization.Actor actor, Maker maker, EnrollmentProposalCommand.Operation operation) {
        if(maker==null || !platformTenant.equals(maker.actorTenantId())) throw denied();
        AuthorityWire.text(maker.issuer(),500);AuthorityWire.text(maker.subject(),255);
        AuthorityWire.text(maker.staffRealm(),255);AuthorityWire.uuid(maker.staffActorId());
        if(operation==EnrollmentProposalCommand.Operation.CANCEL_EMERGENCY) {
            if("STAFF".equals(actor.kind()) && !staffRealms.containsKey(actor.issuer())) throw denied();
            return;
        }
        String realm=staffRealms.get(actor.issuer());
        if(!Objects.equals(staffRealms.get(maker.issuer()),maker.staffRealm()) || !"STAFF".equals(actor.kind()) || realm==null || !realm.equals(maker.staffRealm())) throw denied();
        boolean sameStaff=actor.staffActorId().equals(maker.staffActorId());
        boolean sameSubject=actor.issuer().equals(maker.issuer()) && actor.subject().equals(maker.subject());
        if(operation==EnrollmentProposalCommand.Operation.APPROVE || operation==EnrollmentProposalCommand.Operation.REJECT) {
            if(sameStaff || sameSubject) throw denied();
        } else if(!sameStaff || !sameSubject) throw denied();
    }
    private static void requireExact(ProposalScope scope, EnrollmentProposalCommand command) {
        if(scope.controlVersion()!=command.expectedControlVersion() || scope.bankGeneration()!=command.expectedBankGeneration()) throw denied();
        var event=switch(command.operation()) {
            case SUBMIT->BankEnrollmentLifecycle.ProposalEvent.SUBMIT;
            case APPROVE->BankEnrollmentLifecycle.ProposalEvent.APPROVE;
            case REJECT->BankEnrollmentLifecycle.ProposalEvent.REJECT;
            case WITHDRAW->BankEnrollmentLifecycle.ProposalEvent.WITHDRAW;
            case CANCEL,CANCEL_EMERGENCY->BankEnrollmentLifecycle.ProposalEvent.CANCEL;
        };
        BankEnrollmentLifecycle.transition(new BankEnrollmentLifecycle.ProposalSnapshot(scope.state(),scope.revision(),scope.controlVersion(),scope.target().intentDigest()),
                event,command.expectedRevision(),command.expectedControlVersion(),command.expectedTargetDigest());
    }
    private static String permission(EnrollmentProposalCommand.Operation operation) {
        return "organization.bank.enrollment.proposal."+switch(operation) {
            case SUBMIT->"submit";case APPROVE->"approve";case REJECT->"reject";case WITHDRAW->"withdraw";
            case CANCEL->"cancel";case CANCEL_EMERGENCY->"cancel.emergency";
        };
    }
    private static Throwable safe(Throwable error) { return error instanceof ResponseStatusException?error:denied(); }
    private static ResponseStatusException denied() { return new ResponseStatusException(HttpStatus.FORBIDDEN,"Organization proposal admission denied"); }
}
