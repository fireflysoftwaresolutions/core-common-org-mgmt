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
import org.fireflyframework.security.api.domain.*;
import org.fireflyframework.security.spi.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;
import static io.fireflysoftware.core.organization.core.authority.ConfigurationGrantObservationVerifierTest.*;

class OrganizationEnrollmentAuthorizationTest {
    static final String PLATFORM="00000000-0000-0000-0000-000000000090",BANK="00000000-0000-0000-0000-000000000091",ACTION="00000000-0000-0000-0000-000000000092",ENROLLMENT="00000000-0000-0000-0000-000000000093",STAFF="00000000-0000-0000-0000-000000000094",IDP="https://org-idp.example.test";
    static com.fasterxml.jackson.databind.node.ObjectNode intent() throws Exception {
        var n=JSON.createObjectNode().put("schemaVersion",2).put("actionId",ACTION).put("mode","ENROLL").put("bankId",BANK).put("enrollmentId",ENROLLMENT).put("destinationTenantId",TENANT).put("institutionId",INSTITUTION).put("issuerAuthorityId",ISSUER).put("use","ACCOUNTING_BOOK").put("expectedOwnershipGeneration",0).put("expectedSourceGeneration",0).putNull("existingEnrollmentId").putNull("expectedEnrollmentVersion").putNull("expectedEnrollmentControlVersion").put("expectedGrantHeadVersion",1).put("expectedGrantControlVersion",0).put("commandDeadlineEpochMillis",RECEIVED+20000);
        n.set("grantDefinition",observation().get("definition"));return n;
    }
    static SecurityPrincipal principal(String tenant,Map<String,Object> changes) {
        var c=new HashMap<String,Object>();c.put("tenant_id",PLATFORM);c.put("exp",Instant.ofEpochMilli(RECEIVED+60000));c.put("org_kind","employee");c.put("org_staff",STAFF);c.putAll(changes);
        return SecurityPrincipal.builder().issuer(IDP).subject("verified-operator").tenantId(tenant).claims(c).build();
    }
    static class Fixture {
        final AtomicLong now=new AtomicLong(RECEIVED);final Clock clock=new Clock(){public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId z){return this;}public Instant instant(){return Instant.ofEpochMilli(now.get());}};
        final AtomicReference<SecurityPrincipal> caller=new AtomicReference<>(principal(PLATFORM,Map.of()));final AtomicReference<Decision> policy=new AtomicReference<>(Decision.permit());final AtomicInteger calls=new AtomicInteger();String action,resource;Map<String,Object> context;Runnable duringPolicy=()->{};
        OrganizationAuthorityAuthorization authorization(){return new OrganizationAuthorityAuthorization(()->Mono.justOrEmpty(caller.get()),(p,a,r,c)->{calls.incrementAndGet();action=a;resource=r;context=c;duringPolicy.run();return Mono.justOrEmpty(policy.get());},clock,PLATFORM,Map.of(IDP,new OrganizationActorIssuerProfile(IDP,"tenant_id","org_kind","org_staff","org_client","employee","service")));}
    }
    static void denied(Runnable r){assertEquals(403,assertThrows(ResponseStatusException.class,r::run).getStatusCode().value());}
    @Test void fullClosedIntentActionAndGrantFactsBindPrivatePlatformAdmission() throws Exception {
        var f=new Fixture();var auth=f.authorization();var bytes=bytes(intent());var a=auth.admit(OrganizationAuthorityAuthorization.EnrollmentOperation.PREPARE,bytes).block();assertNotNull(a);assertEquals(PLATFORM,a.actor().tenantId());assertEquals(TENANT,a.scope().intent().request().destinationTenantId());assertEquals(RECEIVED+20000,a.validUntilEpochMillis());assertEquals("organization.bank.enrollment.prepare",f.action);assertEquals("tenant/"+TENANT+"/BANK_ENROLLMENT/"+BANK,f.resource);assertEquals(PLATFORM,f.context.get("actorTenantId"));assertEquals(TENANT,f.context.get("resourceTenantId"));assertEquals(ACTION,f.context.get("actionId"));assertEquals(a.scope().intent().canonicalIntent(),f.context.get("canonicalIntent"));assertEquals(a.scope().intent().intentDigest(),f.context.get("intentDigest"));assertEquals(1,f.calls.get());assertSame(a,auth.recheck(a).block());assertEquals(2,f.calls.get());
    }
    @Test void nullProjectionNeverRepairedFromSignedClaimAndWrongPlatformDeniesBeforePolicy() throws Exception {
        for(String tenant:Arrays.asList(null,TENANT)){var f=new Fixture();f.caller.set(principal(tenant,Map.of()));denied(()->f.authorization().admit(OrganizationAuthorityAuthorization.EnrollmentOperation.PREPARE,bytesUnchecked()).block());assertEquals(0,f.calls.get());}
    }
    static byte[] bytesUnchecked(){try{return bytes(intent());}catch(Exception e){throw new AssertionError(e);}}
    @Test void onlyExplicitConfiguredKindClaimsAreRecognizedAndMachineIsNeverStaff() {
        var f=new Fixture();var c=new HashMap<String,Object>(f.caller.get().claims());c.remove("org_kind");c.put("principal_kind","STAFF");f.caller.set(f.caller.get().toBuilder().claims(c).build());denied(()->f.authorization().admit(OrganizationAuthorityAuthorization.EnrollmentOperation.PREPARE,bytesUnchecked()).block());
        c.put("org_kind","service");c.put("org_client","actual-client");f.caller.set(f.caller.get().toBuilder().claims(c).build());denied(()->f.authorization().admit(OrganizationAuthorityAuthorization.EnrollmentOperation.EXECUTE,bytesUnchecked()).block());c.remove("org_staff");f.caller.set(f.caller.get().toBuilder().claims(c).build());var a=f.authorization().admit(OrganizationAuthorityAuthorization.EnrollmentOperation.EXECUTE,bytesUnchecked()).block();assertEquals("MACHINE",a.actor().kind());assertNull(a.actor().staffActorId());assertEquals("actual-client",a.actor().clientId());denied(()->f.authorization().admit(OrganizationAuthorityAuthorization.EnrollmentOperation.PREPARE,bytesUnchecked()).block());
    }
    @Test void recheckUsesCurrentExactPolicyAndOriginalIdentityWithoutExtendingHorizon() {
        var f=new Fixture();var auth=f.authorization();var a=auth.admit(OrganizationAuthorityAuthorization.EnrollmentOperation.EXECUTE,bytesUnchecked()).block();f.now.addAndGet(1000);assertSame(a,auth.recheck(a).block());assertEquals(RECEIVED+20000,a.validUntilEpochMillis());f.policy.set(Decision.deny("withdrawn"));denied(()->auth.recheck(a).block());f.policy.set(Decision.permit());f.caller.set(f.caller.get().toBuilder().subject("replacement").build());denied(()->auth.recheck(a).block());f.caller.set(principal(PLATFORM,Map.of()));f.now.set(RECEIVED+20000);denied(()->auth.recheck(a).block());
    }
    @Test void foreignAuthorizerAdmissionUnsupportedObligationsAndExpiredInputsFailClosed() {
        var f=new Fixture();var auth=f.authorization();var a=auth.admit(OrganizationAuthorityAuthorization.EnrollmentOperation.PREPARE,bytesUnchecked()).block();denied(()->f.authorization().recheck(a).block());f.policy.set(new Decision(Decision.Effect.PERMIT,List.of(Obligation.of("mask",Map.of())),null));denied(()->auth.admit(OrganizationAuthorityAuthorization.EnrollmentOperation.PREPARE,bytesUnchecked()).block());f.policy.set(Decision.permit());f.caller.set(principal(PLATFORM,Map.of("exp",Instant.ofEpochMilli(RECEIVED))));denied(()->auth.admit(OrganizationAuthorityAuthorization.EnrollmentOperation.PREPARE,bytesUnchecked()).block());
    }
    @Test void policyWaitCannotExtendTheOriginalThirtySecondAdmissionHorizon() throws Exception {
        var f=new Fixture();var n=intent();n.put("commandDeadlineEpochMillis",RECEIVED+60000);f.duringPolicy=()->f.now.addAndGet(5000);
        var a=f.authorization().admit(OrganizationAuthorityAuthorization.EnrollmentOperation.PREPARE,bytes(n)).block();assertEquals(RECEIVED+30000,a.validUntilEpochMillis());
    }
    @Test void fourEnumeratedOperationsRequireTheirOwnExactPolicyAndMode() throws Exception {
        for(var operation:OrganizationAuthorityAuthorization.EnrollmentOperation.values()) {
            var f=new Fixture();var n=intent();boolean transfer=operation.name().startsWith("TRANSFER");
            if(transfer)n.put("mode","TRANSFER").put("expectedOwnershipGeneration",4).put("existingEnrollmentId",STAFF).put("expectedEnrollmentVersion",2).put("expectedEnrollmentControlVersion",3);
            var admission=f.authorization().admit(operation,bytes(n)).block();assertNotNull(admission);
            String expected=switch(operation){case PREPARE->"prepare";case EXECUTE->"execute";case TRANSFER_PREPARE->"transfer.prepare";case TRANSFER_EXECUTE->"transfer.execute";};assertEquals("organization.bank.enrollment."+expected,f.action);
            var mismatch=n.deepCopy();mismatch.put("mode",transfer?"ENROLL":"TRANSFER");denied(()->f.authorization().admit(operation,bytes(mismatch)).block());
        }
    }
    @Test void ambientTransactionRejectsBeforeCurrentPrincipalOrPolicy() {
        var f=new Fixture();var auth=f.authorization();var result=org.springframework.transaction.reactive.TransactionSynchronizationManager.forCurrentTransaction().flatMap(manager->{manager.setActualTransactionActive(true);return auth.admit(OrganizationAuthorityAuthorization.EnrollmentOperation.PREPARE,bytesUnchecked());}).contextWrite(org.springframework.transaction.reactive.TransactionContextManager.createTransactionContext());
        reactor.test.StepVerifier.create(result).expectErrorMatches(e->e instanceof ResponseStatusException status&&status.getStatusCode().value()==409).verify();assertEquals(0,f.calls.get());
    }
    @Test void fullIntentAndCurrentPolicyMustBindTheExpectedBankSourceGeneration() throws Exception {
        var f=new Fixture();var n=intent();n.put("expectedSourceGeneration",7);var admission=assertDoesNotThrow(()->f.authorization().admit(OrganizationAuthorityAuthorization.EnrollmentOperation.PREPARE,bytes(n)).block());assertNotNull(admission);assertEquals(7L,f.context.get("expectedSourceGeneration"));
    }
    @Test void closedIntentRejectsUnknownCoercedDuplicateAndMismatchedGrantBeforePolicy() throws Exception {
        var f=new Fixture();var unknown=intent();unknown.put("crossTenant",true);denied(()->f.authorization().admit(OrganizationAuthorityAuthorization.EnrollmentOperation.PREPARE,bytes(unknown)).block());var coerced=intent();coerced.put("expectedOwnershipGeneration","0");denied(()->f.authorization().admit(OrganizationAuthorityAuthorization.EnrollmentOperation.PREPARE,bytes(coerced)).block());var mismatch=intent();mismatch.put("institutionId",BANK);denied(()->f.authorization().admit(OrganizationAuthorityAuthorization.EnrollmentOperation.PREPARE,bytes(mismatch)).block());var transfer=intent();transfer.put("mode","TRANSFER");denied(()->f.authorization().admit(OrganizationAuthorityAuthorization.EnrollmentOperation.TRANSFER_PREPARE,bytes(transfer)).block());assertEquals(0,f.calls.get());
        String duplicate=intent().toString().replace("\"schemaVersion\":2","\"schemaVersion\":2,\"schemaVersion\":2");denied(()->f.authorization().admit(OrganizationAuthorityAuthorization.EnrollmentOperation.PREPARE,duplicate.getBytes(java.nio.charset.StandardCharsets.UTF_8)).block());
    }
}
