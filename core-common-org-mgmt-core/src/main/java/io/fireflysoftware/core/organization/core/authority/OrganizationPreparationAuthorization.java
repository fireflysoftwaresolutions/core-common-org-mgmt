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

public final class OrganizationPreparationAuthorization {
    public static final class Discovery {
        private final OrganizationPreparationAuthorization owner;
        private final OrganizationAuthorityAuthorization.Actor actor;
        private final OrganizationAuthorityIntent<BankEnrollmentPreparationRequest> desired;
        private final long until;
        private Discovery(OrganizationPreparationAuthorization owner,OrganizationAuthorityAuthorization.Actor actor,OrganizationAuthorityIntent<BankEnrollmentPreparationRequest> desired,long until){this.owner=owner;this.actor=actor;this.desired=desired;this.until=until;}
        public OrganizationAuthorityIntent<BankEnrollmentPreparationRequest> desired(){return desired;}
        public OrganizationAuthorityAuthorization.Actor actor(){return actor;}
        public String permission(){return OrganizationPreparationAuthorization.permission(desired.request().mode());}
        public String staffRealm(){return owner.staffRealms.get(actor.issuer());}
        public long validUntilEpochMillis(){return until;}
        public void requireCurrent(){if(owner.clock.millis()>=until)throw denied();}
    }
    public static final class Captured {
        private final Discovery discovery;
        private final BankEnrollmentPreparationPlan.LocalSnapshot local;
        private final long until;
        private Captured(Discovery discovery,BankEnrollmentPreparationPlan.LocalSnapshot local,long until){this.discovery=discovery;this.local=local;this.until=until;}
        public Discovery discovery(){return discovery;}
        public BankEnrollmentPreparationPlan.LocalSnapshot local(){return local;}
        public long validUntilEpochMillis(){return until;}
        public long currentEpochMillis(){return discovery.owner.clock.millis();}
        public void requireCurrent(){discovery.requireCurrent();if(discovery.owner.clock.millis()>=until)throw denied();}
    }
    public static final class Prepared {
        private final Captured captured;
        private final BankEnrollmentPreparationPlan.Prepared plan;
        private final long until;
        private Prepared(Captured captured,BankEnrollmentPreparationPlan.Prepared plan,long until){this.captured=captured;this.plan=plan;this.until=until;}
        public Captured captured(){return captured;}
        public BankEnrollmentPreparationPlan.Prepared plan(){return plan;}
        public OrganizationAuthorityAuthorization.Actor actor(){return captured.discovery.actor;}
        public String staffRealm(){return captured.discovery.owner.staffRealms.get(actor().issuer());}
        public String permission(){return OrganizationPreparationAuthorization.permission(plan.desired().request().mode());}
        public long validUntilEpochMillis(){return until;}
        public void requireCurrent(){captured.requireCurrent();if(captured.discovery.owner.clock.millis()>=until)throw denied();}
    }
    private final SecurityContextPort callers;
    private final PolicyDecisionPort policies;
    private final Clock clock;
    private final OrganizationAuthorityAuthorization actors;
    private final Map<String,String> staffRealms;
    public OrganizationPreparationAuthorization(SecurityContextPort callers,PolicyDecisionPort policies,Clock clock,String platformTenant,
            Map<String,OrganizationActorIssuerProfile> profiles,Map<String,String> staffRealms) {
        this.callers=Objects.requireNonNull(callers);this.policies=Objects.requireNonNull(policies);this.clock=Objects.requireNonNull(clock);
        actors=new OrganizationAuthorityAuthorization(callers,policies,clock,platformTenant,profiles);this.staffRealms=Map.copyOf(staffRealms);
        if(this.staffRealms.isEmpty())throw AuthorityWire.bad();this.staffRealms.forEach((issuer,realm)->{if(!profiles.containsKey(issuer))throw AuthorityWire.bad();AuthorityWire.text(realm,255);});
    }
    public Mono<Discovery> discover(String tenant,String bank,BankEnrollmentPreparationRequest.Mode mode,byte[] bytes) {
        return OrganizationAuthorityAuthorization.outsideTransaction().then(Mono.defer(()->{
            long started=clock.millis();var desired=BankEnrollmentPreparationCodec.decode(tenant,bank,mode,started,bytes);
            return current(desired,null,null,"PREPARE_DISCOVERY").map(actor->{var admission=new Discovery(this,actor,desired,Math.min(Math.min(started+30000,desired.request().notAfterEpochMillis()),actor.expiresAtEpochMillis()));admission.requireCurrent();return admission;});
        })).onErrorMap(OrganizationPreparationAuthorization::safe);
    }
    public Mono<Discovery> recheckDiscovery(Discovery discovery) {
        return OrganizationAuthorityAuthorization.outsideTransaction().then(Mono.defer(()->{
            owned(discovery);discovery.requireCurrent();
            return current(discovery.desired,null,null,"PREPARE_DISCOVERY").map(actor->{same(discovery,actor);return discovery;});
        })).onErrorMap(OrganizationPreparationAuthorization::safe);
    }
    public Mono<Captured> bindCapture(Discovery discovery,BankEnrollmentPreparationPlan.LocalSnapshot local,long captureUntil) {
        return OrganizationAuthorityAuthorization.outsideTransaction().then(Mono.defer(()->{
            owned(discovery);discovery.requireCurrent();checkLocal(discovery.desired,local);
            if(captureUntil<=clock.millis() || captureUntil>discovery.until)throw denied();
            return current(discovery.desired,local,null,"PREPARE_CAPTURED").map(actor->{same(discovery,actor);var result=new Captured(discovery,local,Math.min(discovery.until,captureUntil));result.requireCurrent();return result;});
        })).onErrorMap(OrganizationPreparationAuthorization::safe);
    }
    public Mono<Captured> recheckCapture(Captured captured) {
        return OrganizationAuthorityAuthorization.outsideTransaction().then(Mono.defer(()->{
            if(captured==null)throw denied();owned(captured.discovery);captured.requireCurrent();
            return current(captured.discovery.desired,captured.local,null,"PREPARE_CAPTURED").map(actor->{same(captured.discovery,actor);captured.requireCurrent();return captured;});
        })).onErrorMap(OrganizationPreparationAuthorization::safe);
    }
    public Mono<Prepared> admitPrepared(Captured captured,BankEnrollmentPreparationPlan.Prepared plan) {
        return OrganizationAuthorityAuthorization.outsideTransaction().then(Mono.defer(()->{
            validate(captured,plan);
            return current(captured.discovery.desired,captured.local,plan,"PREPARE_FULL").map(actor->{same(captured.discovery,actor);captured.requireCurrent();var result=new Prepared(captured,plan,Math.min(captured.until,plan.usableUntilEpochMillis()));result.requireCurrent();return result;});
        })).onErrorMap(OrganizationPreparationAuthorization::safe);
    }
    public Mono<Prepared> recheckPrepared(Prepared prepared) {
        return OrganizationAuthorityAuthorization.outsideTransaction().then(Mono.defer(()->{
            if(prepared==null)throw denied();prepared.requireCurrent();validate(prepared.captured,prepared.plan);
            return current(prepared.plan.desired(),prepared.captured.local,prepared.plan,"PREPARE_FULL").map(actor->{same(prepared.captured.discovery,actor);prepared.requireCurrent();return prepared;});
        })).onErrorMap(OrganizationPreparationAuthorization::safe);
    }
    private void validate(Captured captured,BankEnrollmentPreparationPlan.Prepared plan) {
        if(captured==null || plan==null)throw denied();owned(captured.discovery);captured.requireCurrent();
        if(!captured.discovery.desired.equals(plan.desired()) || !captured.local.equals(plan.local())
                || !BankEnrollmentPreparationPlan.build(plan.desired(),plan.local(),plan.allocation(),plan.observations(),clock.millis()).equals(plan))throw denied();
    }
    private Mono<OrganizationAuthorityAuthorization.Actor> current(OrganizationAuthorityIntent<BankEnrollmentPreparationRequest> desired,
            BankEnrollmentPreparationPlan.LocalSnapshot local,BankEnrollmentPreparationPlan.Prepared plan,String stage) {
        return callers.currentPrincipal().switchIfEmpty(Mono.error(denied())).flatMap(principal->{
            var actor=actors.actor(principal);var request=desired.request();
            if(!"STAFF".equals(actor.kind()) || !staffRealms.containsKey(actor.issuer()) || clock.millis()>=request.notAfterEpochMillis())throw denied();
            var context=new HashMap<String,Object>();context.put("stage",stage);context.put("actorKind",actor.kind());context.put("actorIssuer",actor.issuer());context.put("actorSubject",actor.subject());context.put("actorTenantId",actor.tenantId());context.put("staffRealm",staffRealms.get(actor.issuer()));context.put("staffActorId",actor.staffActorId());if(actor.clientId()!=null)context.put("clientId",actor.clientId());
            context.put("resourceTenantId",request.destinationTenantId());context.put("bankId",request.bankId());context.put("institutionId",request.institutionId());context.put("issuerAuthorityId",request.issuerAuthorityId());context.put("mode",request.mode().name());context.put("actionId",request.actionId());context.put("allowedUses",request.allowedUses().stream().map(Enum::name).toList());context.put("canonicalDesiredRequest",desired.canonicalIntent());context.put("desiredRequestDigest",desired.intentDigest());
            if(local!=null){context.put("expectedBankGeneration",local.bankGeneration());context.put("expectedOwnershipGeneration",local.ownershipGeneration());context.put("expectedSourceGeneration",local.sourceGeneration());if(local.previousOwner()!=null)context.put("previousOwner",local.previousOwner());}
            if(plan!=null){context.put("preparedRevisionCanonical",plan.revision().canonicalIntent());context.put("preparedRevisionDigest",plan.revision().intentDigest());context.put("observationUsableUntil",plan.usableUntilEpochMillis());}
            return Mono.defer(()->policies.authorize(principal,permission(request.mode()),"tenant/"+request.destinationTenantId()+"/BANK_ENROLLMENT/"+request.bankId(),Map.copyOf(context))).timeout(Duration.ofSeconds(5))
                    .filter(d->d.granted()&&d.obligations().isEmpty()).switchIfEmpty(Mono.error(denied())).map(d->{if(clock.millis()>=actor.expiresAtEpochMillis() || clock.millis()>=request.notAfterEpochMillis())throw denied();return actor;});
        }).timeout(Duration.ofSeconds(5));
    }
    private static void checkLocal(OrganizationAuthorityIntent<BankEnrollmentPreparationRequest> desired,BankEnrollmentPreparationPlan.LocalSnapshot local) {
        var wanted=desired.request();if(local==null || !wanted.bankId().equals(local.bankId()) || wanted.mode()==BankEnrollmentPreparationRequest.Mode.ENROLL && (local.ownershipGeneration()!=0 || local.previousOwner()!=null)
                || wanted.mode()==BankEnrollmentPreparationRequest.Mode.TRANSFER && local.previousOwner()==null)throw denied();
    }
    private void owned(Discovery value){if(value==null || value.owner!=this)throw denied();}
    private static void same(Discovery original,OrganizationAuthorityAuthorization.Actor current){if(!original.actor.equals(current))throw denied();original.requireCurrent();}
    private static String permission(BankEnrollmentPreparationRequest.Mode mode){return mode==BankEnrollmentPreparationRequest.Mode.ENROLL?"organization.bank.enrollment.prepare":"organization.bank.enrollment.transfer.prepare";}
    private static Throwable safe(Throwable error){return error instanceof ResponseStatusException?error:denied();}
    private static ResponseStatusException denied(){return new ResponseStatusException(HttpStatus.FORBIDDEN,"Organization preparation admission denied");}
}
