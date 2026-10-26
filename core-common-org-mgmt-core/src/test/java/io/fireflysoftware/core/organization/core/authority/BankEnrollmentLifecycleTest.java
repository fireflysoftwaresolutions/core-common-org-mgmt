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

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class BankEnrollmentLifecycleTest {
    static final UUID TENANT=UUID.fromString("00000000-0000-0000-0000-000000000001"),INSTITUTION=UUID.fromString("00000000-0000-0000-0000-000000000002"),ISSUER=UUID.fromString("00000000-0000-0000-0000-000000000006");
    static final UUID FIRST=UUID.fromString("00000000-0000-0000-0000-000000000301"),SECOND=UUID.fromString("00000000-0000-0000-0000-000000000302");
    static BankEnrollmentLifecycle.OwnerReference ref(UUID id,long version){return new BankEnrollmentLifecycle.OwnerReference(TENANT,INSTITUTION,ISSUER,id,version);}
    @Test void sameEnrollmentVersionChangeIsAnOwnershipTransitionAndOldFullReferenceCannotRevive() {
        var before=new BankEnrollmentLifecycle.OwnershipSnapshot(4,7,ref(FIRST,1));var after=BankEnrollmentLifecycle.applyOwnership(before,new BankEnrollmentLifecycle.OwnerTransition(4,7,ref(FIRST,1),ref(FIRST,2)));assertEquals(5,after.ownershipGeneration());assertEquals(8,after.bankGeneration());assertEquals(2,after.owner().version());assertThrows(IllegalStateException.class,()->BankEnrollmentLifecycle.applyOwnership(after,new BankEnrollmentLifecycle.OwnerTransition(5,8,ref(FIRST,1),ref(SECOND,1))));
    }
    @Test void virginOwnershipNeverRevivesAfterAdmitWithdrawAndUnownedRestoration() {
        var virgin=new BankEnrollmentLifecycle.OwnershipSnapshot(0,0,null);
        var prepared=new BankEnrollmentLifecycle.OwnerTransition(0,0,null,ref(FIRST,1));
        var owned=BankEnrollmentLifecycle.applyOwnership(virgin,prepared);assertEquals(1,owned.ownershipGeneration());assertEquals(1,owned.bankGeneration());assertEquals(FIRST,owned.enrollmentId());
        var unowned=BankEnrollmentLifecycle.applyOwnership(owned,new BankEnrollmentLifecycle.OwnerTransition(1,1,ref(FIRST,1),null));assertNull(unowned.enrollmentId());assertEquals(2,unowned.ownershipGeneration());assertEquals(2,unowned.bankGeneration());assertFalse(unowned.neverOwned());
        assertThrows(IllegalStateException.class,()->BankEnrollmentLifecycle.applyOwnership(unowned,prepared));
        var readmitted=BankEnrollmentLifecycle.applyOwnership(unowned,new BankEnrollmentLifecycle.OwnerTransition(2,2,null,ref(SECOND,1)));assertEquals(3,readmitted.ownershipGeneration());assertEquals(3,readmitted.bankGeneration());
    }
    @Test void bankLifecycleChangesHaveOneCounterAndDoNotResetOwnershipHistory() {
        var owned=new BankEnrollmentLifecycle.OwnershipSnapshot(4,7,ref(FIRST,1));var changed=BankEnrollmentLifecycle.advanceBankLifecycle(owned);assertEquals(4,changed.ownershipGeneration());assertEquals(8,changed.bankGeneration());assertEquals(FIRST,changed.enrollmentId());assertThrows(IllegalStateException.class,()->BankEnrollmentLifecycle.applyOwnership(changed,new BankEnrollmentLifecycle.OwnerTransition(4,7,ref(FIRST,1),ref(SECOND,1))));
    }
    @Test void proposalTransitionsRequireExactRevisionControlAndTargetBinding() {
        var draft=new BankEnrollmentLifecycle.ProposalSnapshot(BankEnrollmentLifecycle.ProposalState.DRAFT,1,0,"a".repeat(64));
        var submitted=BankEnrollmentLifecycle.transition(draft,BankEnrollmentLifecycle.ProposalEvent.SUBMIT,1,0,"a".repeat(64));assertEquals(BankEnrollmentLifecycle.ProposalState.SUBMITTED,submitted.state());assertEquals(1,submitted.controlVersion());
        assertThrows(IllegalStateException.class,()->BankEnrollmentLifecycle.transition(submitted,BankEnrollmentLifecycle.ProposalEvent.APPROVE,1,0,"a".repeat(64)));
        assertThrows(IllegalStateException.class,()->BankEnrollmentLifecycle.transition(submitted,BankEnrollmentLifecycle.ProposalEvent.APPROVE,1,1,"b".repeat(64)));
        var approved=BankEnrollmentLifecycle.transition(submitted,BankEnrollmentLifecycle.ProposalEvent.APPROVE,1,1,"a".repeat(64));assertEquals(BankEnrollmentLifecycle.ProposalState.APPROVED,approved.state());
        var withdrawn=BankEnrollmentLifecycle.transition(approved,BankEnrollmentLifecycle.ProposalEvent.WITHDRAW,1,2,"a".repeat(64));assertEquals(BankEnrollmentLifecycle.ProposalState.WITHDRAWN,withdrawn.state());assertThrows(IllegalStateException.class,()->BankEnrollmentLifecycle.transition(withdrawn,BankEnrollmentLifecycle.ProposalEvent.EXECUTE,1,3,"a".repeat(64)));
    }
    @Test void editCreatesFreshRevisionAndNeverCarriesAnOldApprovalForward() {
        var rejected=new BankEnrollmentLifecycle.ProposalSnapshot(BankEnrollmentLifecycle.ProposalState.REJECTED,2,5,"a".repeat(64));var edited=BankEnrollmentLifecycle.edit(rejected,2,5,"b".repeat(64));assertEquals(3,edited.revision());assertEquals(6,edited.controlVersion());assertEquals(BankEnrollmentLifecycle.ProposalState.DRAFT,edited.state());assertThrows(IllegalStateException.class,()->BankEnrollmentLifecycle.transition(edited,BankEnrollmentLifecycle.ProposalEvent.EXECUTE,3,6,"b".repeat(64)));
    }
    @Test void terminalCancelledExecutedAndClosedIntentsCannotBeEditedOrRevived() {
        for(var state:new BankEnrollmentLifecycle.ProposalState[]{BankEnrollmentLifecycle.ProposalState.CANCELLED,BankEnrollmentLifecycle.ProposalState.EXECUTED,BankEnrollmentLifecycle.ProposalState.CLOSED_NO_EFFECT}) {
            var terminal=new BankEnrollmentLifecycle.ProposalSnapshot(state,1,3,"a".repeat(64));assertThrows(IllegalStateException.class,()->BankEnrollmentLifecycle.edit(terminal,1,3,"b".repeat(64)));assertThrows(IllegalStateException.class,()->BankEnrollmentLifecycle.transition(terminal,BankEnrollmentLifecycle.ProposalEvent.SUBMIT,1,3,"a".repeat(64)));
        }
    }
}
