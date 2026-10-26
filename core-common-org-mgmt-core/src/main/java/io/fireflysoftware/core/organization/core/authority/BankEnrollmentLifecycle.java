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

import java.util.Objects;
import java.util.UUID;

/** Pure state/fence calculations; these do not authorize callers or establish native effects. */
public final class BankEnrollmentLifecycle {
    private BankEnrollmentLifecycle() { }
    public record OwnerReference(UUID tenantId,UUID institutionId,UUID issuerAuthorityId,UUID enrollmentId,long version) {
        public OwnerReference {
            uuid(tenantId);uuid(institutionId);uuid(issuerAuthorityId);uuid(enrollmentId);
            if(version<=0 || version==Long.MAX_VALUE) throw AuthorityWire.bad();
        }
    }
    public record OwnershipSnapshot(long ownershipGeneration,long bankGeneration,OwnerReference owner) {
        public OwnershipSnapshot {
            generation(ownershipGeneration);generation(bankGeneration);
            if(bankGeneration<ownershipGeneration || ownershipGeneration==0 && owner!=null) throw AuthorityWire.bad();
        }
        public boolean neverOwned() { return ownershipGeneration==0 && owner==null; }
        public UUID enrollmentId() { return owner==null?null:owner.enrollmentId(); }
    }
    public record OwnerTransition(long expectedOwnershipGeneration,long expectedBankGeneration,
            OwnerReference expectedOwner,OwnerReference newOwner) {
        public OwnerTransition {
            generation(expectedOwnershipGeneration);generation(expectedBankGeneration);
            if(expectedBankGeneration<expectedOwnershipGeneration || expectedOwnershipGeneration==0 && expectedOwner!=null
                    || Objects.equals(expectedOwner,newOwner)) throw AuthorityWire.bad();
        }
    }
    public static OwnershipSnapshot applyOwnership(OwnershipSnapshot current,OwnerTransition target) {
        if(current==null || target==null || current.ownershipGeneration()!=target.expectedOwnershipGeneration()
                || current.bankGeneration()!=target.expectedBankGeneration() || !Objects.equals(current.owner(),target.expectedOwner())) throw stale();
        return new OwnershipSnapshot(next(current.ownershipGeneration()),next(current.bankGeneration()),target.newOwner());
    }
    public static OwnershipSnapshot advanceBankLifecycle(OwnershipSnapshot current) {
        Objects.requireNonNull(current);return new OwnershipSnapshot(current.ownershipGeneration(),next(current.bankGeneration()),current.owner());
    }
    public enum ProposalState { DRAFT,SUBMITTED,APPROVED,REJECTED,WITHDRAWN,CANCELLED,EXECUTED,CLOSED_NO_EFFECT }
    public enum ProposalEvent { SUBMIT,APPROVE,REJECT,WITHDRAW,CANCEL,EXECUTE,CLOSE_NO_EFFECT }
    public record ProposalSnapshot(ProposalState state,long revision,long controlVersion,String targetDigest) {
        public ProposalSnapshot {
            Objects.requireNonNull(state);generation(controlVersion);AuthorityWire.digest(targetDigest);
            if(revision<=0 || revision==Long.MAX_VALUE) throw AuthorityWire.bad();
        }
    }
    public static ProposalSnapshot transition(ProposalSnapshot current,ProposalEvent event,
            long expectedRevision,long expectedControlVersion,String expectedDigest) {
        exact(current,expectedRevision,expectedControlVersion,expectedDigest);
        if(event==null) throw stale();
        ProposalState state=switch(current.state()) {
            case DRAFT -> switch(event) {case SUBMIT->ProposalState.SUBMITTED;case CANCEL->ProposalState.CANCELLED;case CLOSE_NO_EFFECT->ProposalState.CLOSED_NO_EFFECT;default->throw stale();};
            case SUBMITTED -> switch(event) {case APPROVE->ProposalState.APPROVED;case REJECT->ProposalState.REJECTED;case WITHDRAW->ProposalState.WITHDRAWN;case CANCEL->ProposalState.CANCELLED;case CLOSE_NO_EFFECT->ProposalState.CLOSED_NO_EFFECT;default->throw stale();};
            case APPROVED -> switch(event) {case EXECUTE->ProposalState.EXECUTED;case WITHDRAW->ProposalState.WITHDRAWN;case CANCEL->ProposalState.CANCELLED;case CLOSE_NO_EFFECT->ProposalState.CLOSED_NO_EFFECT;default->throw stale();};
            case REJECTED,WITHDRAWN -> switch(event) {case CANCEL->ProposalState.CANCELLED;case CLOSE_NO_EFFECT->ProposalState.CLOSED_NO_EFFECT;default->throw stale();};
            case CANCELLED,EXECUTED,CLOSED_NO_EFFECT -> throw stale();
        };
        return new ProposalSnapshot(state,current.revision(),next(current.controlVersion()),current.targetDigest());
    }
    public static ProposalSnapshot edit(ProposalSnapshot current,long expectedRevision,long expectedControlVersion,String newDigest) {
        if(current==null || current.revision()!=expectedRevision || current.controlVersion()!=expectedControlVersion
                || current.state()!=ProposalState.DRAFT && current.state()!=ProposalState.REJECTED && current.state()!=ProposalState.WITHDRAWN) throw stale();
        AuthorityWire.digest(newDigest);
        return new ProposalSnapshot(ProposalState.DRAFT,next(current.revision()),next(current.controlVersion()),newDigest);
    }
    private static void exact(ProposalSnapshot current,long revision,long version,String digest) {
        if(current==null || current.revision()!=revision || current.controlVersion()!=version || !current.targetDigest().equals(digest)) throw stale();
    }
    private static void generation(long n) { if(n<0 || n==Long.MAX_VALUE) throw AuthorityWire.bad(); }
    private static long next(long n) { if(n>=Long.MAX_VALUE-1) throw new IllegalStateException("Enrollment generation exhausted");return n+1; }
    private static void uuid(UUID id) { if(id==null || id.getMostSignificantBits()==0 && id.getLeastSignificantBits()==0) throw AuthorityWire.bad(); }
    private static IllegalStateException stale() { return new IllegalStateException("Exact current enrollment state required"); }
}
