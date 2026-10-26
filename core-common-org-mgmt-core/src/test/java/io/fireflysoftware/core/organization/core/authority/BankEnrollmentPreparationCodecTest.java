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

import io.fireflysoftware.core.organization.interfaces.authority.BankEnrollmentPreparationRequest;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static io.fireflysoftware.core.organization.core.authority.ConfigurationGrantObservationVerifierTest.*;
import static io.fireflysoftware.core.organization.core.authority.OrganizationEnrollmentAuthorizationTest.*;

class BankEnrollmentPreparationCodecTest {
    static com.fasterxml.jackson.databind.node.ObjectNode desired(){var n=JSON.createObjectNode().put("schemaVersion",2).put("actionId",ACTION).put("mode","ENROLL").put("bankId",BANK).put("destinationTenantId",TENANT).put("institutionId",INSTITUTION).put("issuerAuthorityId",ISSUER).put("notAfterEpochMillis",RECEIVED+600000).put("reason","Requested Bank scope – explicit issuer");n.putArray("allowedUses").add("ACCOUNTING_BOOK").add("PRODUCT_ISSUANCE");n.putArray("evidenceReferences").add("fixture:π");return n;}
    @Test void desiredRequestIsUsableBeforeGrantAndServerAllocationsAreKnown(){var result=BankEnrollmentPreparationCodec.decode(TENANT,BANK,BankEnrollmentPreparationRequest.Mode.ENROLL,RECEIVED,bytes(desired()));assertEquals(TENANT,result.request().destinationTenantId());assertEquals(2,result.request().allowedUses().size());assertFalse(result.originalJson().contains("grantDefinition"));assertFalse(result.originalJson().contains("proposalId"));assertTrue(result.canonicalIntent().startsWith(AuthorityWire.token("FF_ORGANIZATION_BANK_PREPARATION_REQUEST_V2")));}
    @Test void noAuthorityIdentityOrAllocationCanBeInjectedIntoTheClosedDesiredRequest(){for(var field:List.of("staffActorId","clientId","grantDefinition","proposalId","enrollmentId","reservedExecutionActionId","legalEntityName","regulatoryLicense")){var changed=desired().put(field,"injected");assertThrows(IllegalArgumentException.class,()->BankEnrollmentPreparationCodec.decode(TENANT,BANK,BankEnrollmentPreparationRequest.Mode.ENROLL,RECEIVED,bytes(changed)),field);}var coerced=desired().put("notAfterEpochMillis",Long.toString(RECEIVED+600000));assertThrows(IllegalArgumentException.class,()->BankEnrollmentPreparationCodec.decode(TENANT,BANK,BankEnrollmentPreparationRequest.Mode.ENROLL,RECEIVED,bytes(coerced)));}
    @Test void routeTenantBankAndModeAreExplicitBindings(){for(var field:List.of("destinationTenantId","bankId")){var changed=desired().put(field,"00000000-0000-0000-0000-000000000099");assertThrows(IllegalArgumentException.class,()->BankEnrollmentPreparationCodec.decode(TENANT,BANK,BankEnrollmentPreparationRequest.Mode.ENROLL,RECEIVED,bytes(changed)));}assertThrows(IllegalArgumentException.class,()->BankEnrollmentPreparationCodec.decode(TENANT,BANK,BankEnrollmentPreparationRequest.Mode.TRANSFER,RECEIVED,bytes(desired())));var transfer=desired().put("mode","TRANSFER");assertNotNull(BankEnrollmentPreparationCodec.decode(TENANT,BANK,BankEnrollmentPreparationRequest.Mode.TRANSFER,RECEIVED,bytes(transfer)));}
    @Test void allowedUsesAreAnExactSortedNonemptySetAndReferencesAreBounded(){for(var values:List.of(List.of("PRODUCT_ISSUANCE","ACCOUNTING_BOOK"),List.of("ACCOUNTING_BOOK","ACCOUNTING_BOOK"),List.<String>of())){var changed=desired();var uses=changed.putArray("allowedUses");values.forEach(uses::add);assertThrows(IllegalArgumentException.class,()->BankEnrollmentPreparationCodec.decode(TENANT,BANK,BankEnrollmentPreparationRequest.Mode.ENROLL,RECEIVED,bytes(changed)));}var duplicate=desired();duplicate.putArray("evidenceReferences").add("same").add("same");assertThrows(IllegalArgumentException.class,()->BankEnrollmentPreparationCodec.decode(TENANT,BANK,BankEnrollmentPreparationRequest.Mode.ENROLL,RECEIVED,bytes(duplicate)));}
    @Test void fixedDesiredDeadlineExpiresAndCannotExceedOneDay(){for(long until:new long[]{RECEIVED,RECEIVED-1,RECEIVED+86400001}){var changed=desired().put("notAfterEpochMillis",until);assertThrows(IllegalArgumentException.class,()->BankEnrollmentPreparationCodec.decode(TENANT,BANK,BankEnrollmentPreparationRequest.Mode.ENROLL,RECEIVED,bytes(changed)));}assertNotNull(BankEnrollmentPreparationCodec.decode(TENANT,BANK,BankEnrollmentPreparationRequest.Mode.ENROLL,RECEIVED,bytes(desired().put("notAfterEpochMillis",RECEIVED+86400000))));}
    @Test void literalUtf8VectorAndOriginalFormattingHaveOneCanonicalIntent()throws Exception {try(var input=getClass().getResourceAsStream("/authority/bank-preparation-request-v2-golden.json")){var vector=JSON.readTree(input);var bytes=vector.get("original").textValue().getBytes(StandardCharsets.UTF_8);var actual=BankEnrollmentPreparationCodec.decode(TENANT,BANK,BankEnrollmentPreparationRequest.Mode.ENROLL,RECEIVED,bytes);assertEquals(vector.get("canonical").textValue(),actual.canonicalIntent());assertEquals(vector.get("digest").textValue(),actual.intentDigest());var formatted=BankEnrollmentPreparationCodec.decode(TENANT,BANK,BankEnrollmentPreparationRequest.Mode.ENROLL,RECEIVED,JSON.writerWithDefaultPrettyPrinter().writeValueAsBytes(JSON.readTree(bytes)));assertEquals(actual.intentDigest(),formatted.intentDigest());assertNotEquals(actual.originalJson(),formatted.originalJson());}}
}
