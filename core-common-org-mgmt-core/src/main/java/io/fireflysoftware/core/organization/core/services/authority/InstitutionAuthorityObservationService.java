package io.fireflysoftware.core.organization.core.services.authority;

import io.fireflysoftware.core.organization.core.authority.VerifiedInstitutionGrantObservation;
import io.fireflysoftware.core.organization.interfaces.authority.FinancialCalendarUse;
import reactor.core.publisher.Mono;

public interface InstitutionAuthorityObservationService {
    Mono<VerifiedInstitutionGrantObservation> observe(String tenant,String institution,String issuer,FinancialCalendarUse use);
}
