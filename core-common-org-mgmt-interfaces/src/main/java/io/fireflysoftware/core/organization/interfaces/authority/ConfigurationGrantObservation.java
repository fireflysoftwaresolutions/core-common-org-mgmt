package io.fireflysoftware.core.organization.interfaces.authority;

import java.util.List;

public record ConfigurationGrantObservation(String tenantId,String institutionId,String issuerAuthorityId,FinancialCalendarUse requestedUse,String state,Definition definition,Control control,Long headVersion,Eligibility tenantEligibility,long observedAtEpochMillis,Long validUntilEpochMillis,String denialReason) {
    public record Definition(String tenantId,Target target,String definitionEncoding,String canonicalDefinition,String definitionDigest,long recordedAtEpochMillis) {}
    public record Target(Grant grant,Epoch eligibilityEpoch) {}
    public record Grant(String actionId,String proposalId,long proposalRevision,String grantId,String institutionId,String issuerAuthorityId,long version,long expectedGrantHeadVersion,List<FinancialCalendarUse> uses,long effectiveFromEpochMillis,long effectiveToEpochMillis,String reason,List<String> evidenceReferences) {
        public Grant {uses=List.copyOf(uses);evidenceReferences=List.copyOf(evidenceReferences);}
    }
    public record Epoch(long tenantGeneration,String statusId,long statusGeneration) {}
    public record Control(String state,long controlVersion,String lastActionId) {}
    public record Eligibility(String tenantId,long tenantGeneration,String statusId,long statusGeneration,boolean tenantActive,boolean statusEnabled,String statusCode,boolean eligibleNow,String lastActionId) {}
}
