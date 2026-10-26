package io.fireflysoftware.core.organization.core.authority;

import io.fireflysoftware.core.organization.interfaces.authority.ConfigurationGrantObservation;
import io.fireflysoftware.core.organization.interfaces.authority.InstitutionAuthorityGrantReference;

public final class VerifiedInstitutionGrantObservation {
    private final String original;
    private final ConfigurationGrantObservation observation;
    private final Long usableUntil;

    VerifiedInstitutionGrantObservation(String original,ConfigurationGrantObservation observation,Long usableUntil) {
        this.original=original;this.observation=observation;this.usableUntil=usableUntil;
    }
    public String originalObservationJson(){return original;}
    public ConfigurationGrantObservation observation(){return observation;}
    public Long usableUntilEpochMillis(){return usableUntil;}
    public VerifiedInstitutionGrantObservation limitedTo(long epochMillis) {
        return new VerifiedInstitutionGrantObservation(original,observation,usableUntil==null?null:Math.min(usableUntil,epochMillis));
    }
    public void requireCurrentAt(long epochMillis) {
        if(!"CURRENT_AUTHORIZED".equals(observation.state()) || usableUntil==null || epochMillis>=usableUntil || epochMillis<observation.definition().target().grant().effectiveFromEpochMillis()) throw new IllegalStateException("Current institution authority evidence required");
    }
    public void requireReference(InstitutionAuthorityGrantReference expected) {
        if(expected==null || observation.definition()==null) throw AuthorityWire.bad();
        var definition=observation.definition();var grant=definition.target().grant();
        if(!grant.grantId().equals(expected.grantId()) || grant.version()!=expected.version() || !definition.definitionDigest().equals(expected.digest()) || observation.requestedUse()!=expected.use()) throw AuthorityWire.bad();
    }
}
