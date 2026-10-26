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
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/** Pure target assembly; native current-state fences and caller authorization remain mandatory. */
public final class BankEnrollmentPreparationPlan {
    private BankEnrollmentPreparationPlan() { }
    public record Allocation(String proposalId,String enrollmentId,String executionActionId) {
        public Allocation {for(var id:new String[]{proposalId,enrollmentId,executionActionId})AuthorityWire.uuid(id);}
    }
    public record LocalSnapshot(String bankId,long bankGeneration,long ownershipGeneration,long sourceGeneration,BankEnrollmentOwnerReference previousOwner) {
        public LocalSnapshot {
            AuthorityWire.uuid(bankId);
            if(bankGeneration<0 || bankGeneration==Long.MAX_VALUE || ownershipGeneration<0 || ownershipGeneration==Long.MAX_VALUE || sourceGeneration<0 || sourceGeneration==Long.MAX_VALUE || bankGeneration<ownershipGeneration)throw AuthorityWire.bad();
            if(previousOwner!=null){
                if(ownershipGeneration==0)throw AuthorityWire.bad();
                for(var id:new String[]{previousOwner.tenantId(),previousOwner.institutionId(),previousOwner.issuerAuthorityId(),previousOwner.enrollmentId()})AuthorityWire.uuid(id);
                if(previousOwner.version()<=0 || previousOwner.version()==Long.MAX_VALUE || previousOwner.controlVersion()<0 || previousOwner.controlVersion()==Long.MAX_VALUE)throw AuthorityWire.bad();
            }
        }
    }
    public record Prepared(OrganizationAuthorityIntent<BankEnrollmentPreparationRequest> desired,LocalSnapshot local,Allocation allocation,
            OrganizationAuthorityIntent<BankEnrollmentRevision> revision,List<ObservedInstitutionGrantEvidence> observations,long usableUntilEpochMillis) {
        public Prepared {observations=List.copyOf(observations);}
    }
    public static Prepared build(OrganizationAuthorityIntent<BankEnrollmentPreparationRequest> desired,LocalSnapshot local,Allocation allocation,
            List<ObservedInstitutionGrantEvidence> observations,long now) {
        if(desired==null || desired.request()==null || desired.originalJson()==null || local==null || allocation==null || observations==null)throw AuthorityWire.bad();
        var wanted=desired.request();var decoded=BankEnrollmentPreparationCodec.decode(wanted.destinationTenantId(),wanted.bankId(),wanted.mode(),now,desired.originalJson().getBytes(StandardCharsets.UTF_8));
        if(!decoded.equals(desired) || !local.bankId().equals(wanted.bankId()) || wanted.mode()==BankEnrollmentPreparationRequest.Mode.ENROLL && (local.ownershipGeneration()!=0 || local.previousOwner()!=null)
                || wanted.mode()==BankEnrollmentPreparationRequest.Mode.TRANSFER && local.previousOwner()==null)throw AuthorityWire.bad();
        if(new HashSet<>(List.of(wanted.actionId(),allocation.proposalId(),allocation.enrollmentId(),allocation.executionActionId())).size()!=4)throw AuthorityWire.bad();
        List<ObservedInstitutionGrantEvidence> evidence;
        try{evidence=List.copyOf(observations);}catch(NullPointerException error){throw AuthorityWire.bad();}
        if(evidence.size()!=wanted.allowedUses().size())throw AuthorityWire.bad();
        ConfigurationGrantObservation first=null;var seen=new HashSet<FinancialCalendarUse>();long until=wanted.notAfterEpochMillis();
        for(var item:evidence){
            item.requireCurrentAt(now);var observed=item.observation().observation();
            if(!wanted.destinationTenantId().equals(observed.tenantId()) || !wanted.institutionId().equals(observed.institutionId()) || !wanted.issuerAuthorityId().equals(observed.issuerAuthorityId())
                    || !wanted.allowedUses().contains(observed.requestedUse()) || !seen.add(observed.requestedUse()) || !wanted.destinationTenantId().equals(item.observer().tenantId()))throw AuthorityWire.bad();
            if(first==null)first=observed;
            else if(!Objects.equals(first.definition(),observed.definition()) || !Objects.equals(first.headVersion(),observed.headVersion()) || !Objects.equals(first.control(),observed.control()) || !Objects.equals(first.tenantEligibility(),observed.tenantEligibility()))throw AuthorityWire.bad();
            until=Math.min(until,item.usableUntilEpochMillis());
        }
        var previous=local.previousOwner();var target=new BankEnrollmentTarget(2,allocation.executionActionId(),BankEnrollmentTarget.Mode.valueOf(wanted.mode().name()),wanted.bankId(),allocation.enrollmentId(),wanted.destinationTenantId(),wanted.institutionId(),wanted.issuerAuthorityId(),wanted.allowedUses().getFirst(),local.ownershipGeneration(),local.sourceGeneration(),previous==null?null:previous.enrollmentId(),previous==null?null:previous.version(),previous==null?null:previous.controlVersion(),first.definition(),first.headVersion(),first.control().controlVersion(),wanted.notAfterEpochMillis());
        var targetIntent=BankEnrollmentIntentCodec.decode(ProposalTerminationEvidenceCodec.json(target).getBytes(StandardCharsets.UTF_8));
        var revision=new BankEnrollmentRevision(2,allocation.proposalId(),1,local.bankGeneration(),previous,targetIntent.originalJson(),targetIntent.canonicalIntent(),targetIntent.intentDigest(),wanted.reason(),wanted.allowedUses(),wanted.evidenceReferences());
        var full=BankEnrollmentRevisionCodec.decode(ProposalTerminationEvidenceCodec.json(revision).getBytes(StandardCharsets.UTF_8));
        until=Math.min(until,BankEnrollmentRevisionCodec.requireObservationSet(full,evidence.stream().map(ObservedInstitutionGrantEvidence::observation).toList(),now));
        return new Prepared(desired,local,allocation,full,evidence,until);
    }
}
