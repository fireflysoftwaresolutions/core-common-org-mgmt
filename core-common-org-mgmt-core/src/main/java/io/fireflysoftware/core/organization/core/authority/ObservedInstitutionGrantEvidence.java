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

public record ObservedInstitutionGrantEvidence(VerifiedInstitutionGrantObservation observation,
        Observer observer,long receivedAtEpochMillis,long usableUntilEpochMillis) {
    public record Observer(String issuer,String subject,String clientId,String tenantId,String audience,
            long verifiedAtEpochMillis,long credentialExpiresAtEpochMillis) { }
    public ObservedInstitutionGrantEvidence {
        Objects.requireNonNull(observation);Objects.requireNonNull(observer);
        if(usableUntilEpochMillis>observer.credentialExpiresAtEpochMillis()
                || observation.usableUntilEpochMillis()==null || usableUntilEpochMillis>observation.usableUntilEpochMillis()
                || usableUntilEpochMillis<=receivedAtEpochMillis) throw AuthorityWire.bad();
    }
    public void requireCurrentAt(long epochMillis) {
        observation.requireCurrentAt(epochMillis);
        if(epochMillis<receivedAtEpochMillis || epochMillis>=usableUntilEpochMillis) throw new IllegalStateException("Current institution authority evidence required");
    }
}
