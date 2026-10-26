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

public final class OrganizationEnrollmentHistoryAuthorization {
    public static final String PERMISSION="organization.bank.enrollment.action.read";
    public record Scope(String tenantId,String bankId,String actionId,String canonicalRequest,String requestDigest) { }
    public static final class Admission {
        private final OrganizationEnrollmentHistoryAuthorization owner;
        private final OrganizationAuthorityAuthorization.Actor actor;
        private final Scope scope;
        private final long until;
        private Admission(OrganizationEnrollmentHistoryAuthorization owner,OrganizationAuthorityAuthorization.Actor actor,Scope scope,long until){this.owner=owner;this.actor=actor;this.scope=scope;this.until=until;}
        public OrganizationAuthorityAuthorization.Actor actor(){return actor;}
        public Scope scope(){return scope;}
        public long validUntilEpochMillis(){return until;}
        public String staffRealm(){return "STAFF".equals(actor.kind())?owner.staffRealms.get(actor.issuer()):null;}
        public void requireCurrent(){if(owner.clock.millis()>=until)throw denied();}
    }
    private final SecurityContextPort callers;
    private final PolicyDecisionPort policies;
    private final Clock clock;
    private final Map<String,String> staffRealms;
    private final OrganizationAuthorityAuthorization actors;
    public OrganizationEnrollmentHistoryAuthorization(SecurityContextPort callers,PolicyDecisionPort policies,Clock clock,
            String platformTenant,Map<String,OrganizationActorIssuerProfile> profiles,Map<String,String> staffRealms) {
        this.callers=Objects.requireNonNull(callers);this.policies=Objects.requireNonNull(policies);this.clock=Objects.requireNonNull(clock);
        this.actors=new OrganizationAuthorityAuthorization(callers,policies,clock,platformTenant,profiles);this.staffRealms=Map.copyOf(staffRealms);
        this.staffRealms.forEach((issuer,realm)->{if(!profiles.containsKey(issuer))throw AuthorityWire.bad();AuthorityWire.text(realm,255);});
    }
    public Mono<Admission> admit(String tenantId,String bankId,String actionId) {
        return OrganizationAuthorityAuthorization.outsideTransaction().then(Mono.defer(()->{
            for(String id:new String[]{tenantId,bankId,actionId})AuthorityWire.uuid(id);
            String canonical=AuthorityWire.token("FF_ORGANIZATION_BANK_ENROLLMENT_ACTION_READ_V2")+AuthorityWire.recordToken(tenantId,bankId,actionId);
            var scope=new Scope(tenantId,bankId,actionId,canonical,AuthorityWire.sha(canonical));long until=Math.addExact(clock.millis(),30000);
            return current(scope).map(actor->{var admission=new Admission(this,actor,scope,Math.min(until,actor.expiresAtEpochMillis()));admission.requireCurrent();return admission;});
        })).onErrorMap(OrganizationEnrollmentHistoryAuthorization::safe);
    }
    public Mono<Admission> recheck(Admission admission) {
        return OrganizationAuthorityAuthorization.outsideTransaction().then(Mono.defer(()->{
            if(admission==null || admission.owner!=this)throw denied();admission.requireCurrent();
            return current(admission.scope).map(actor->{if(!actor.equals(admission.actor))throw denied();admission.requireCurrent();return admission;});
        })).onErrorMap(OrganizationEnrollmentHistoryAuthorization::safe);
    }
    private Mono<OrganizationAuthorityAuthorization.Actor> current(Scope scope) {
        return callers.currentPrincipal().switchIfEmpty(Mono.error(denied())).flatMap(principal->{
            var actor=actors.actor(principal);if("STAFF".equals(actor.kind()) && !staffRealms.containsKey(actor.issuer()))throw denied();
            var context=new HashMap<String,Object>();context.put("purpose","ACTION_READ");context.put("actorKind",actor.kind());
            context.put("actorIssuer",actor.issuer());context.put("actorSubject",actor.subject());context.put("actorTenantId",actor.tenantId());
            context.put("resourceTenantId",scope.tenantId());context.put("bankId",scope.bankId());context.put("actionId",scope.actionId());
            if(actor.staffActorId()!=null){context.put("staffActorId",actor.staffActorId());context.put("staffRealm",staffRealms.get(actor.issuer()));}
            if(actor.clientId()!=null)context.put("clientId",actor.clientId());context.put("canonicalReadRequest",scope.canonicalRequest());context.put("readRequestDigest",scope.requestDigest());
            return Mono.defer(()->policies.authorize(principal,PERMISSION,"tenant/"+scope.tenantId()+"/BANK_ENROLLMENT/"+scope.bankId()+"/ACTION/"+scope.actionId(),Map.copyOf(context)))
                    .timeout(Duration.ofSeconds(5)).filter(d->d.granted()&&d.obligations().isEmpty()).switchIfEmpty(Mono.error(denied()))
                    .map(d->{if(clock.millis()>=actor.expiresAtEpochMillis())throw denied();return actor;});
        }).timeout(Duration.ofSeconds(5));
    }
    private static Throwable safe(Throwable error){return error instanceof ResponseStatusException?error:denied();}
    private static ResponseStatusException denied(){return new ResponseStatusException(HttpStatus.FORBIDDEN,"Enrollment retained action read denied");}
}
