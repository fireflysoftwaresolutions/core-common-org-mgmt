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
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static io.fireflysoftware.core.organization.core.authority.ConfigurationGrantObservationVerifierTest.*;
import static io.fireflysoftware.core.organization.core.authority.OrganizationEnrollmentAuthorizationTest.*;

class BankEnrollmentPreparationPlanTest {
    static OrganizationAuthorityIntent<BankEnrollmentPreparationRequest> desired(BankEnrollmentPreparationRequest.Mode mode){return BankEnrollmentPreparationCodec.decode(TENANT,BANK,mode,RECEIVED,bytes(BankEnrollmentPreparationCodecTest.desired().put("mode",mode.name())));}
    static List<ObservedInstitutionGrantEvidence> evidence()throws Exception {var result=new ArrayList<ObservedInstitutionGrantEvidence>();for(var use:List.of(FinancialCalendarUse.ACCOUNTING_BOOK,FinancialCalendarUse.PRODUCT_ISSUANCE)){var body=observation().put("requestedUse",use.name());var verified=ConfigurationGrantObservationVerifier.verify(TENANT,INSTITUTION,ISSUER,use,bytes(body),RECEIVED);result.add(new ObservedInstitutionGrantEvidence(verified,new ObservedInstitutionGrantEvidence.Observer("https://config-idp.example.test","real-observer","actual-client",TENANT,"configuration-authority",RECEIVED,RECEIVED+60000),RECEIVED,verified.usableUntilEpochMillis()));}return result;}
    static BankEnrollmentPreparationPlan.Allocation allocation(){return new BankEnrollmentPreparationPlan.Allocation("00000000-0000-0000-0000-000000000501",ENROLLMENT,"00000000-0000-0000-0000-000000000502");}
    @Test void fullTargetBindsCapturedLocalFactsActualV2DefinitionAndServerAllocations()throws Exception {var wanted=desired(BankEnrollmentPreparationRequest.Mode.ENROLL);var snapshot=new BankEnrollmentPreparationPlan.LocalSnapshot(BANK,7,0,9,null);var result=BankEnrollmentPreparationPlan.build(wanted,snapshot,allocation(),evidence(),RECEIVED);var revision=result.revision().request();var target=BankEnrollmentRevisionCodec.enrollmentTarget(revision).request();assertEquals(7,revision.expectedBankGeneration());assertEquals(0,target.expectedOwnershipGeneration());assertEquals(9,target.expectedSourceGeneration());assertEquals(allocation().executionActionId(),target.actionId());assertEquals(allocation().proposalId(),revision.proposalId());assertEquals(ENROLLMENT,target.enrollmentId());assertEquals(wanted.request().notAfterEpochMillis(),target.commandDeadlineEpochMillis());assertEquals(wanted.request().allowedUses(),revision.allowedUses());assertEquals(OBSERVED+30000,result.usableUntilEpochMillis());assertEquals(evidence().getFirst().observation().observation().definition(),target.grantDefinition());assertNotEquals(wanted.request().actionId(),target.actionId());}
    @Test void unownedTombstoneCannotBeReinterpretedAsVirginEnrollment()throws Exception {var wanted=desired(BankEnrollmentPreparationRequest.Mode.ENROLL);var old=new BankEnrollmentPreparationPlan.LocalSnapshot(BANK,9,4,11,null);assertThrows(IllegalArgumentException.class,()->BankEnrollmentPreparationPlan.build(wanted,old,allocation(),evidence(),RECEIVED));}
    @Test void transferKeepsTheFullPreviousOwnerScopeWithoutInferringCurrentOldTenantEligibility()throws Exception {var previous=new BankEnrollmentOwnerReference("00000000-0000-0000-0000-000000000097","00000000-0000-0000-0000-000000000098","00000000-0000-0000-0000-000000000099",STAFF,2,5);var result=BankEnrollmentPreparationPlan.build(desired(BankEnrollmentPreparationRequest.Mode.TRANSFER),new BankEnrollmentPreparationPlan.LocalSnapshot(BANK,7,4,9,previous),allocation(),evidence(),RECEIVED);assertEquals(previous,result.revision().request().previousOwner());var t=BankEnrollmentRevisionCodec.enrollmentTarget(result.revision().request()).request();assertEquals(4,t.expectedOwnershipGeneration());assertEquals(STAFF,t.existingEnrollmentId());assertEquals(2,t.expectedEnrollmentVersion());assertEquals(5,t.expectedEnrollmentControlVersion());}
    @Test void missingDuplicateOrChangedRemoteFactsCannotProduceAPreparedRevision()throws Exception {var wanted=desired(BankEnrollmentPreparationRequest.Mode.ENROLL);var local=new BankEnrollmentPreparationPlan.LocalSnapshot(BANK,7,0,9,null);var evidence=evidence();for(var changed:List.of(List.<ObservedInstitutionGrantEvidence>of(),List.of(evidence.getFirst(),evidence.getFirst()),List.of(evidence.getFirst()))){assertThrows(IllegalArgumentException.class,()->BankEnrollmentPreparationPlan.build(wanted,local,allocation(),changed,RECEIVED));}var body=observation().put("requestedUse","PRODUCT_ISSUANCE");((com.fasterxml.jackson.databind.node.ObjectNode)body.get("control")).put("controlVersion",1);var verified=ConfigurationGrantObservationVerifier.verify(TENANT,INSTITUTION,ISSUER,FinancialCalendarUse.PRODUCT_ISSUANCE,bytes(body),RECEIVED);var other=new ObservedInstitutionGrantEvidence(verified,evidence.getLast().observer(),RECEIVED,verified.usableUntilEpochMillis());assertThrows(IllegalArgumentException.class,()->BankEnrollmentPreparationPlan.build(wanted,local,allocation(),List.of(evidence.getFirst(),other),RECEIVED));}
    @Test void malformedAllocationsOrTamperedDesiredIntentCannotAcquireAFullTarget()throws Exception {var wanted=desired(BankEnrollmentPreparationRequest.Mode.ENROLL);var local=new BankEnrollmentPreparationPlan.LocalSnapshot(BANK,7,0,9,null);assertThrows(IllegalArgumentException.class,()->BankEnrollmentPreparationPlan.build(wanted,local,new BankEnrollmentPreparationPlan.Allocation(allocation().proposalId(),ENROLLMENT,ACTION),evidence(),RECEIVED));var forged=new OrganizationAuthorityIntent<>(wanted.originalJson(),wanted.request(),"forged","a".repeat(64));assertThrows(IllegalArgumentException.class,()->BankEnrollmentPreparationPlan.build(forged,local,allocation(),evidence(),RECEIVED));}
    @Test void finiteObservationsAndDesiredDeadlineMustRemainCurrent()throws Exception {var wanted=desired(BankEnrollmentPreparationRequest.Mode.ENROLL);var local=new BankEnrollmentPreparationPlan.LocalSnapshot(BANK,7,0,9,null);assertThrows(IllegalStateException.class,()->BankEnrollmentPreparationPlan.build(wanted,local,allocation(),evidence(),OBSERVED+30000));}
    @Test void separatelyEligibleSnapshotsWithDifferentEligibilityActionsCannotBeCombined()throws Exception {
        var evidence=evidence();var body=observation().put("requestedUse","PRODUCT_ISSUANCE");((com.fasterxml.jackson.databind.node.ObjectNode)body.get("tenantEligibility")).put("lastActionId","00000000-0000-0000-0000-000000000709");
        var verified=ConfigurationGrantObservationVerifier.verify(TENANT,INSTITUTION,ISSUER,FinancialCalendarUse.PRODUCT_ISSUANCE,bytes(body),RECEIVED);var other=new ObservedInstitutionGrantEvidence(verified,evidence.getLast().observer(),RECEIVED,verified.usableUntilEpochMillis());
        var first=evidence.getFirst().observation().observation();var second=verified.observation();assertEquals(first.definition(),second.definition());assertEquals(first.headVersion(),second.headVersion());assertEquals(first.control(),second.control());assertNotEquals(first.tenantEligibility(),second.tenantEligibility());
        assertThrows(IllegalArgumentException.class,()->BankEnrollmentPreparationPlan.build(desired(BankEnrollmentPreparationRequest.Mode.ENROLL),new BankEnrollmentPreparationPlan.LocalSnapshot(BANK,7,0,9,null),allocation(),List.of(evidence.getFirst(),other),RECEIVED));
    }

}
