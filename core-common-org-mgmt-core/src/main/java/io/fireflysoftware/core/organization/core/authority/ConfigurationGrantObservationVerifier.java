package io.fireflysoftware.core.organization.core.authority;

import io.fireflysoftware.core.organization.interfaces.authority.ConfigurationGrantObservation;
import io.fireflysoftware.core.organization.interfaces.authority.FinancialCalendarUse;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class ConfigurationGrantObservationVerifier {
    private static final String ENCODING="FF_TENANT_INSTITUTION_GRANT_DEFINITION_V2";
    private ConfigurationGrantObservationVerifier() {}
    public record RequestScope(String tenantId,String institutionId,String issuerAuthorityId,FinancialCalendarUse use,String canonicalIntent,String intentDigest) {}
    public static RequestScope requestScope(String tenant,String institution,String issuer,FinancialCalendarUse use) {
        AuthorityWire.uuid(tenant);AuthorityWire.uuid(institution);AuthorityWire.uuid(issuer);if(use==null)throw AuthorityWire.bad();
        var canonical=AuthorityWire.token("FF_ORG_CONFIG_GRANT_OBSERVATION_V1")+AuthorityWire.recordToken(tenant,institution,issuer,use);
        return new RequestScope(tenant,institution,issuer,use,canonical,AuthorityWire.sha(canonical));
    }

    public static VerifiedInstitutionGrantObservation verify(String tenant,String institution,String issuer,FinancialCalendarUse use,byte[] original,long receivedAtEpochMillis) {
        AuthorityWire.uuid(tenant);AuthorityWire.uuid(institution);AuthorityWire.uuid(issuer);timestamp(receivedAtEpochMillis);
        if(use==null) throw AuthorityWire.bad();
        var raw=AuthorityWire.original(original,262144);var value=AuthorityWire.record(AuthorityWire.parse(raw),ConfigurationGrantObservation.class,AuthorityWire.nullable(ConfigurationGrantObservation.class));
        if(!tenant.equals(value.tenantId()) || !institution.equals(value.institutionId()) || !issuer.equals(value.issuerAuthorityId()) || use!=value.requestedUse() || !Set.of("CURRENT_AUTHORIZED","NO_CURRENT_AUTHORITY").contains(value.state())) throw AuthorityWire.bad();
        timestamp(value.observedAtEpochMillis());
        if(value.definition()!=null) definition(tenant,institution,issuer,value.definition());
        if(value.control()!=null) {
            if(!Set.of("ACTIVE","SUSPENDED","REVOKED").contains(value.control().state()) || value.control().controlVersion()<0) throw AuthorityWire.bad();
            AuthorityWire.uuid(value.control().lastActionId());
        }
        if(value.headVersion()!=null && value.headVersion()<0) throw AuthorityWire.bad();
        if(value.tenantEligibility()!=null) {
            var live=value.tenantEligibility();if(!tenant.equals(live.tenantId())) throw AuthorityWire.bad();generation(live.tenantGeneration());generation(live.statusGeneration());AuthorityWire.uuid(live.statusId());AuthorityWire.text(live.statusCode(),500);if(live.lastActionId()!=null)AuthorityWire.uuid(live.lastActionId());
        }
        if(value.state().equals("NO_CURRENT_AUTHORITY")) {
            if(value.validUntilEpochMillis()!=null || value.denialReason()==null) throw AuthorityWire.bad();AuthorityWire.text(value.denialReason(),500);
            return new VerifiedInstitutionGrantObservation(raw,value,null);
        }
        if(value.definition()==null || value.control()==null || !value.control().state().equals("ACTIVE") || value.headVersion()==null || value.tenantEligibility()==null || value.validUntilEpochMillis()==null || value.denialReason()!=null) throw AuthorityWire.bad();
        var grant=value.definition().target().grant();var epoch=value.definition().target().eligibilityEpoch();var live=value.tenantEligibility();long observed=value.observedAtEpochMillis(),until=value.validUntilEpochMillis();
        timestamp(until);
        if(value.headVersion()!=grant.version() || !grant.uses().contains(use) || !live.eligibleNow() || !live.tenantActive() || !live.statusEnabled() || !"ACTIVE".equals(live.statusCode()) || epoch.tenantGeneration()!=live.tenantGeneration() || !epoch.statusId().equals(live.statusId()) || epoch.statusGeneration()!=live.statusGeneration() || observed<grant.effectiveFromEpochMillis() || observed>=grant.effectiveToEpochMillis() || until<=observed || until<=receivedAtEpochMillis || until>Math.min(Math.addExact(observed,30000),grant.effectiveToEpochMillis())) throw AuthorityWire.bad();
        long usableUntil=Math.min(until,Math.addExact(receivedAtEpochMillis,30000));
        return new VerifiedInstitutionGrantObservation(raw,value,usableUntil);
    }
    static void definition(String tenant,String institution,String issuer,ConfigurationGrantObservation.Definition value) {
        if(!tenant.equals(value.tenantId()) || !ENCODING.equals(value.definitionEncoding())) throw AuthorityWire.bad();
        var target=value.target();var grant=target.grant();var epoch=target.eligibilityEpoch();
        for(var id:List.of(grant.actionId(),grant.proposalId(),grant.grantId(),grant.institutionId(),grant.issuerAuthorityId()))AuthorityWire.uuid(id);
        if(!institution.equals(grant.institutionId()) || !issuer.equals(grant.issuerAuthorityId()) || grant.proposalRevision()<1 || grant.expectedGrantHeadVersion()<0 || grant.expectedGrantHeadVersion()==Long.MAX_VALUE || grant.version()!=grant.expectedGrantHeadVersion()+1) throw AuthorityWire.bad();
        timestamp(grant.effectiveFromEpochMillis());timestamp(grant.effectiveToEpochMillis());timestamp(value.recordedAtEpochMillis());
        if(grant.effectiveFromEpochMillis()>=grant.effectiveToEpochMillis() || grant.uses().isEmpty() || grant.uses().size()>3) throw AuthorityWire.bad();
        for(int i=1;i<grant.uses().size();i++)if(grant.uses().get(i-1).name().compareTo(grant.uses().get(i).name())>=0)throw AuthorityWire.bad();
        AuthorityWire.text(grant.reason(),500);
        if(grant.evidenceReferences().size()>32 || new HashSet<>(grant.evidenceReferences()).size()!=grant.evidenceReferences().size())throw AuthorityWire.bad();grant.evidenceReferences().forEach(reference->AuthorityWire.text(reference,500));
        generation(epoch.tenantGeneration());generation(epoch.statusGeneration());AuthorityWire.uuid(epoch.statusId());
        var targetCanonical=AuthorityWire.token("FF_TENANT_INSTITUTION_GRANT_V2")+AuthorityWire.recordToken(tenant,"GRANT_ISSUE",target);
        var canonical=AuthorityWire.token(ENCODING)+AuthorityWire.recordToken(tenant,targetCanonical,value.recordedAtEpochMillis());
        AuthorityWire.digest(value.definitionDigest());
        if(!canonical.equals(value.canonicalDefinition()) || !AuthorityWire.sha(canonical).equals(value.definitionDigest()))throw AuthorityWire.bad();
    }
    private static void timestamp(long value){if(value < -2208988800000L || value>253402300799999L)throw AuthorityWire.bad();}
    private static void generation(long value){if(value<0 || value==Long.MAX_VALUE)throw AuthorityWire.bad();}
}
