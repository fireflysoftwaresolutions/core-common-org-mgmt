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

import io.fireflysoftware.core.organization.interfaces.authority.BankEnrollmentTarget;
import org.fireflyframework.security.api.domain.SecurityPrincipal;
import org.fireflyframework.security.spi.PolicyDecisionPort;
import org.fireflyframework.security.spi.SecurityContextPort;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.NoTransactionException;
import org.springframework.transaction.reactive.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import java.time.*;
import java.util.*;

public final class OrganizationAuthorityAuthorization {
    public enum EnrollmentOperation {
        PREPARE("prepare",true,false),TRANSFER_PREPARE("transfer.prepare",true,true),
        EXECUTE("execute",false,false),TRANSFER_EXECUTE("transfer.execute",false,true);
        private final String action;private final boolean staffOnly,transfer;
        EnrollmentOperation(String action,boolean staffOnly,boolean transfer) { this.action=action;this.staffOnly=staffOnly;this.transfer=transfer; }
    }
    public record Actor(String kind,String issuer,String subject,String tenantId,String staffActorId,String clientId,long expiresAtEpochMillis) { }
    public record EnrollmentScope(EnrollmentOperation operation,OrganizationAuthorityIntent<BankEnrollmentTarget> intent) { }
    public static final class EnrollmentAdmission {
        private final OrganizationAuthorityAuthorization owner;private final Actor actor;private final EnrollmentScope scope;private final long until;
        private EnrollmentAdmission(OrganizationAuthorityAuthorization owner,Actor actor,EnrollmentScope scope,long until) { this.owner=owner;this.actor=actor;this.scope=scope;this.until=until; }
        public Actor actor() { return actor; }
        public EnrollmentScope scope() { return scope; }
        public long validUntilEpochMillis() { return until; }
        public void requireCurrent() { if(owner.clock.millis()>=until || owner.clock.millis()>=actor.expiresAtEpochMillis()) throw denied(); }
    }
    private final SecurityContextPort callers;private final PolicyDecisionPort policies;private final Clock clock;
    private final String platformTenant;private final Map<String,OrganizationActorIssuerProfile> profiles;
    public OrganizationAuthorityAuthorization(SecurityContextPort callers,PolicyDecisionPort policies,Clock clock,
            String platformTenant,Map<String,OrganizationActorIssuerProfile> profiles) {
        this.callers=Objects.requireNonNull(callers);this.policies=Objects.requireNonNull(policies);this.clock=Objects.requireNonNull(clock);
        AuthorityWire.uuid(platformTenant);this.platformTenant=platformTenant;this.profiles=Map.copyOf(profiles);
        if(this.profiles.isEmpty()) throw AuthorityWire.bad();
        this.profiles.forEach((issuer,p)->{if(!issuer.equals(p.issuer())) throw AuthorityWire.bad();});
    }
    public Mono<EnrollmentAdmission> admit(EnrollmentOperation operation,byte[] bytes) {
        return outsideTransaction().then(Mono.defer(()->{
            var intent=BankEnrollmentIntentCodec.decode(bytes);var t=intent.request();
            if(operation==null || operation.transfer!=(t.mode()==BankEnrollmentTarget.Mode.TRANSFER)) throw denied();
            var scope=new EnrollmentScope(operation,intent);long requestedUntil=Math.min(clock.millis()+30000,t.commandDeadlineEpochMillis());
            return current(scope).map(actor->{long until=Math.min(actor.expiresAtEpochMillis(),requestedUntil);var a=new EnrollmentAdmission(this,actor,scope,until);a.requireCurrent();return a;});
        })).onErrorMap(OrganizationAuthorityAuthorization::safe);
    }
    public Mono<EnrollmentAdmission> recheck(EnrollmentAdmission admission) {
        return outsideTransaction().then(Mono.defer(()->{
            if(admission==null || admission.owner!=this) throw denied();admission.requireCurrent();
            return current(admission.scope).map(actor->{if(!actor.equals(admission.actor)) throw denied();admission.requireCurrent();return admission;});
        })).onErrorMap(OrganizationAuthorityAuthorization::safe);
    }
    private Mono<Actor> current(EnrollmentScope scope) {
        return callers.currentPrincipal().switchIfEmpty(Mono.error(denied())).flatMap(principal->{
            var actor=actor(principal);var t=scope.intent().request();
            if(scope.operation().staffOnly && !"STAFF".equals(actor.kind()) || clock.millis()>=t.commandDeadlineEpochMillis()) throw denied();
            var context=new HashMap<String,Object>();context.put("actorKind",actor.kind());context.put("actorIssuer",actor.issuer());context.put("actorSubject",actor.subject());context.put("actorTenantId",actor.tenantId());context.put("resourceTenantId",t.destinationTenantId());
            if(actor.staffActorId()!=null)context.put("staffActorId",actor.staffActorId());if(actor.clientId()!=null)context.put("clientId",actor.clientId());
            context.put("bankId",t.bankId());context.put("institutionId",t.institutionId());context.put("issuerAuthorityId",t.issuerAuthorityId());context.put("use",t.use().name());context.put("operation",scope.operation().name());context.put("actionId",t.actionId());context.put("expectedOwnershipGeneration",t.expectedOwnershipGeneration());context.put("expectedSourceGeneration",t.expectedSourceGeneration());
            if(t.existingEnrollmentId()!=null){context.put("existingEnrollmentId",t.existingEnrollmentId());context.put("expectedEnrollmentVersion",t.expectedEnrollmentVersion());context.put("expectedEnrollmentControlVersion",t.expectedEnrollmentControlVersion());}
            context.put("canonicalIntent",scope.intent().canonicalIntent());context.put("intentDigest",scope.intent().intentDigest());
            return Mono.defer(()->policies.authorize(principal,"organization.bank.enrollment."+scope.operation().action,
                    "tenant/"+t.destinationTenantId()+"/BANK_ENROLLMENT/"+t.bankId(),Map.copyOf(context)))
                    .timeout(Duration.ofSeconds(5)).filter(d->d.granted()&&d.obligations().isEmpty()).switchIfEmpty(Mono.error(denied()))
                    .map(d->{if(clock.millis()>=actor.expiresAtEpochMillis() || clock.millis()>=t.commandDeadlineEpochMillis())throw denied();return actor;});
        }).timeout(Duration.ofSeconds(5));
    }
    Actor actor(SecurityPrincipal p) {
        if(p==null || p.tenantId()==null || !platformTenant.equals(p.tenantId())) throw denied();
        var profile=profiles.get(p.issuer());if(profile==null || !p.tenantId().equals(p.claims().get(profile.tenantClaim()))) throw denied();
        AuthorityWire.text(p.subject(),255);AuthorityWire.uuid(p.tenantId());
        if(!(p.claims().get("exp") instanceof Instant expiry) || !expiry.isAfter(clock.instant()) || expiry.isAfter(Instant.parse("9999-12-31T23:59:59Z"))) throw denied();
        var nbf=p.claims().get("nbf");if(nbf!=null && (!(nbf instanceof Instant n) || n.isAfter(clock.instant()))) throw denied();
        String kind,staff=null,client=null;var raw=p.claims().get(profile.kindClaim());
        if(profile.staffValue().equals(raw)){kind="STAFF";if(!(p.claims().get(profile.staffActorClaim()) instanceof String id))throw denied();AuthorityWire.uuid(id);staff=id;if(p.claims().get(profile.clientClaim()) instanceof String c){AuthorityWire.text(c,255);client=c;}}
        else if(profile.machineValue().equals(raw)){kind="MACHINE";if(p.claims().containsKey(profile.staffActorClaim()) || !(p.claims().get(profile.clientClaim()) instanceof String c)) throw denied();AuthorityWire.text(c,255);client=c;}
        else throw denied();
        return new Actor(kind,p.issuer(),p.subject(),p.tenantId(),staff,client,expiry.toEpochMilli());
    }
    static Mono<Void> outsideTransaction() {
        return TransactionSynchronizationManager.forCurrentTransaction().map(m->m.isActualTransactionActive())
                .onErrorResume(NoTransactionException.class,e->Mono.just(false))
                .flatMap(active->active?Mono.error(new ResponseStatusException(HttpStatus.CONFLICT,"Organization observation requires a pre-execution transaction boundary")):Mono.empty());
    }
    private static Throwable safe(Throwable error) { return error instanceof ResponseStatusException?error:denied(); }
    private static ResponseStatusException denied() { return new ResponseStatusException(HttpStatus.FORBIDDEN,"Organization enrollment admission denied"); }
}
