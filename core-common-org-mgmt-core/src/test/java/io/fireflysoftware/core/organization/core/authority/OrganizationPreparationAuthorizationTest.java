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
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static io.fireflysoftware.core.organization.core.authority.ConfigurationGrantObservationVerifierTest.*;
import static io.fireflysoftware.core.organization.core.authority.OrganizationEnrollmentAuthorizationTest.*;
import static io.fireflysoftware.core.organization.core.authority.OrganizationProposalAuthorizationTest.REALM;

class OrganizationPreparationAuthorizationTest {
    static class Fixture extends OrganizationEnrollmentAuthorizationTest.Fixture {
        OrganizationPreparationAuthorization preparations(){return new OrganizationPreparationAuthorization(()->Mono.justOrEmpty(caller.get()),(p,a,r,c)->{calls.incrementAndGet();action=a;resource=r;context=c;duringPolicy.run();return Mono.justOrEmpty(policy.get());},clock,PLATFORM,Map.of(IDP,new OrganizationActorIssuerProfile(IDP,"tenant_id","org_kind","org_staff","org_client","employee","service")),Map.of(IDP,REALM));}
    }
    @Test void exactDesiredPreparePermissionPrecedesAnyGrantOrAllocationKnowledge(){var f=new Fixture();var auth=f.preparations();var admission=auth.discover(TENANT,BANK,BankEnrollmentPreparationRequest.Mode.ENROLL,bytes(BankEnrollmentPreparationCodecTest.desired())).block();assertNotNull(admission);assertEquals("organization.bank.enrollment.prepare",f.action);assertEquals("tenant/"+TENANT+"/BANK_ENROLLMENT/"+BANK,f.resource);assertEquals(PLATFORM,f.context.get("actorTenantId"));assertEquals(TENANT,f.context.get("resourceTenantId"));assertEquals("PREPARE_DISCOVERY",f.context.get("stage"));assertFalse(f.context.containsKey("grantDefinition"));}
    @Test void requiredSharedProjectionAndActualStaffKindCannotBeRepairedOrImpersonated(){var f=new Fixture();f.caller.set(principal(null,Map.of()));denied(()->f.preparations().discover(TENANT,BANK,BankEnrollmentPreparationRequest.Mode.ENROLL,bytes(BankEnrollmentPreparationCodecTest.desired())).block());assertEquals(0,f.calls.get());var g=new Fixture();var claims=new HashMap<>(g.caller.get().claims());claims.remove("org_staff");claims.put("org_kind","service");claims.put("org_client","actual-machine");g.caller.set(g.caller.get().toBuilder().claims(claims).build());denied(()->g.preparations().discover(TENANT,BANK,BankEnrollmentPreparationRequest.Mode.ENROLL,bytes(BankEnrollmentPreparationCodecTest.desired())).block());assertEquals(0,g.calls.get());}
    @Test void capturedLocalFactsAreBoundBeforeCredentialAcquisitionAndDoNotRestartHorizon(){var f=new Fixture();var auth=f.preparations();var d=auth.discover(TENANT,BANK,BankEnrollmentPreparationRequest.Mode.ENROLL,bytes(BankEnrollmentPreparationCodecTest.desired())).block();f.now.addAndGet(1000);var c=auth.bindCapture(d,new BankEnrollmentPreparationPlan.LocalSnapshot(BANK,7,0,9,null),RECEIVED+20000).block();assertEquals("PREPARE_CAPTURED",f.context.get("stage"));assertEquals(7L,f.context.get("expectedBankGeneration"));assertEquals(9L,f.context.get("expectedSourceGeneration"));assertEquals(RECEIVED+20000,c.validUntilEpochMillis());assertSame(c,auth.recheckCapture(c).block());assertEquals(RECEIVED+20000,c.validUntilEpochMillis());}
    @Test void fullPreparedIntentRequiresCurrentOriginalCallerAndExactObservedCapture()throws Exception {var f=new Fixture();var auth=f.preparations();var d=auth.discover(TENANT,BANK,BankEnrollmentPreparationRequest.Mode.ENROLL,bytes(BankEnrollmentPreparationCodecTest.desired())).block();var local=new BankEnrollmentPreparationPlan.LocalSnapshot(BANK,7,0,9,null);var c=auth.bindCapture(d,local,RECEIVED+20000).block();var plan=BankEnrollmentPreparationPlan.build(d.desired(),local,BankEnrollmentPreparationPlanTest.allocation(),BankEnrollmentPreparationPlanTest.evidence(),RECEIVED);var full=auth.admitPrepared(c,plan).block();assertEquals("PREPARE_FULL",f.context.get("stage"));assertEquals(plan.revision().canonicalIntent(),f.context.get("preparedRevisionCanonical"));assertEquals(plan.revision().intentDigest(),f.context.get("preparedRevisionDigest"));assertEquals(RECEIVED+20000,full.validUntilEpochMillis());f.policy.set(Decision.deny("withdrawn"));denied(()->auth.recheckPrepared(full).block());}
    @Test void identityReplacementForeignAdmissionAndExpiredCaptureFailClosed(){var f=new Fixture();var auth=f.preparations();var d=auth.discover(TENANT,BANK,BankEnrollmentPreparationRequest.Mode.ENROLL,bytes(BankEnrollmentPreparationCodecTest.desired())).block();denied(()->f.preparations().bindCapture(d,new BankEnrollmentPreparationPlan.LocalSnapshot(BANK,7,0,9,null),RECEIVED+20000).block());var c=auth.bindCapture(d,new BankEnrollmentPreparationPlan.LocalSnapshot(BANK,7,0,9,null),RECEIVED+20000).block();f.caller.set(f.caller.get().toBuilder().claims(principal(PLATFORM,Map.of("org_client","different-current-console")).claims()).build());denied(()->auth.recheckCapture(c).block());f.caller.set(principal(PLATFORM,Map.of()));f.now.set(RECEIVED+20000);denied(()->auth.recheckCapture(c).block());}
}
