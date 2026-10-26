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
import static org.junit.jupiter.api.Assertions.*;
import static io.fireflysoftware.core.organization.core.authority.ConfigurationGrantObservationVerifierTest.*;
import static io.fireflysoftware.core.organization.core.authority.OrganizationEnrollmentAuthorizationTest.*;
import static io.fireflysoftware.core.organization.core.authority.EnrollmentProposalCommandCodecTest.PROPOSAL;

class BankEnrollmentRevisionCodecTest {
    static com.fasterxml.jackson.databind.node.ObjectNode revision() throws Exception {
        var target=BankEnrollmentIntentCodec.decode(bytes(intent()));var n=JSON.createObjectNode().put("schemaVersion",2).put("proposalId",PROPOSAL).put("revision",1).put("expectedBankGeneration",7).putNull("previousOwner").put("originalEnrollmentTargetJson",target.originalJson()).put("canonicalEnrollmentTarget",target.canonicalIntent()).put("enrollmentTargetDigest",target.intentDigest()).put("reason","Exact independent admission");n.putArray("allowedUses").add("ACCOUNTING_BOOK");n.putArray("evidenceReferences").add("case:fictional-lifecycle");return n;
    }
    @Test void revisionBindsBankGenerationCompleteTargetReasonAndUsesWithoutChangingSource188() throws Exception {
        var n=revision();var target=BankEnrollmentIntentCodec.decode(bytes(intent()));var a=BankEnrollmentRevisionCodec.decode(bytes(n));assertEquals(7,a.request().expectedBankGeneration());assertTrue(a.canonicalIntent().contains(target.canonicalIntent()));assertEquals(target,BankEnrollmentRevisionCodec.enrollmentTarget(a.request()));assertNotEquals(target.intentDigest(),a.intentDigest());
        for(String field:new String[]{"expectedBankGeneration","reason"}){var changed=n.deepCopy();if(field.equals("reason"))changed.put(field,"Changed approved reason");else changed.put(field,8);assertNotEquals(a.intentDigest(),BankEnrollmentRevisionCodec.decode(bytes(changed)).intentDigest());}
    }
    @Test void sourceFormattingDoesNotChangeApprovedMeaningButOriginalBytesAreRetained() throws Exception {
        var first=revision();var formatted=JSON.writerWithDefaultPrettyPrinter().writeValueAsString(intent());var second=first.deepCopy().put("originalEnrollmentTargetJson",formatted);
        var a=BankEnrollmentRevisionCodec.decode(bytes(first));var b=BankEnrollmentRevisionCodec.decode(bytes(second));assertEquals(a.canonicalIntent(),b.canonicalIntent());assertNotEquals(a.originalJson(),b.originalJson());assertEquals(formatted,b.request().originalEnrollmentTargetJson());
    }
    @Test void fullPreviousOwnerIsMandatoryForTransferAndForbiddenForVirginEnrollment() throws Exception {
        var first=revision();var previous=JSON.createObjectNode().put("tenantId",PLATFORM).put("institutionId",INSTITUTION).put("issuerAuthorityId",ISSUER).put("enrollmentId",STAFF).put("version",2).put("controlVersion",3);
        var virgin=first.deepCopy();virgin.set("previousOwner",previous);assertThrows(IllegalArgumentException.class,()->BankEnrollmentRevisionCodec.decode(bytes(virgin)));
        var transfer=intent().put("mode","TRANSFER").put("expectedOwnershipGeneration",4).put("existingEnrollmentId",STAFF).put("expectedEnrollmentVersion",2).put("expectedEnrollmentControlVersion",3);var target=BankEnrollmentIntentCodec.decode(bytes(transfer));first.put("originalEnrollmentTargetJson",target.originalJson()).put("canonicalEnrollmentTarget",target.canonicalIntent()).put("enrollmentTargetDigest",target.intentDigest());assertThrows(IllegalArgumentException.class,()->BankEnrollmentRevisionCodec.decode(bytes(first)));first.set("previousOwner",previous);assertNotNull(BankEnrollmentRevisionCodec.decode(bytes(first)));previous.put("controlVersion",4);assertThrows(IllegalArgumentException.class,()->BankEnrollmentRevisionCodec.decode(bytes(first)));
    }
    @Test void useSetMustBeOrderedDistinctNonemptyAndWithinTheActualGrant() throws Exception {
        for(String[] uses:new String[][]{{},{"ACCOUNTING_BOOK","ACCOUNTING_BOOK"},{"PRODUCT_ISSUANCE","ACCOUNTING_BOOK"},{"CASH_OPERATIONS"}}){var n=revision();var a=n.putArray("allowedUses");for(String use:uses)a.add(use);assertThrows(IllegalArgumentException.class,()->BankEnrollmentRevisionCodec.decode(bytes(n)));}
        var valid=revision();valid.putArray("allowedUses").add("ACCOUNTING_BOOK").add("PRODUCT_ISSUANCE");assertEquals(2,BankEnrollmentRevisionCodec.decode(bytes(valid)).request().allowedUses().size());
    }
    @Test void declaredTargetCanonicalAndDigestCannotSubstituteForActualRetainedContent() throws Exception {
        for(String field:new String[]{"canonicalEnrollmentTarget","enrollmentTargetDigest"}){var n=revision();n.put(field,field.endsWith("Digest")?"a".repeat(64):"forged");assertThrows(IllegalArgumentException.class,()->BankEnrollmentRevisionCodec.decode(bytes(n)));}
        var n=revision();n.put("crossTenant",true);assertThrows(IllegalArgumentException.class,()->BankEnrollmentRevisionCodec.decode(bytes(n)));
    }
    @Test void lifecycleCommandMustBindTheWholeRevisionNotOnlyItsOldEnrollmentTargetDigest() throws Exception {
        var full=BankEnrollmentRevisionCodec.decode(bytes(revision()));var command=EnrollmentProposalCommandCodecTest.command("APPROVE");
        assertThrows(IllegalArgumentException.class,()->EnrollmentProposalCommandCodec.decodeRevision(TENANT,BANK,PROPOSAL,io.fireflysoftware.core.organization.interfaces.authority.EnrollmentProposalCommand.Operation.APPROVE,1,full,RECEIVED,bytes(command)));
        command.put("expectedTargetDigest",full.intentDigest());var result=EnrollmentProposalCommandCodec.decodeRevision(TENANT,BANK,PROPOSAL,io.fireflysoftware.core.organization.interfaces.authority.EnrollmentProposalCommand.Operation.APPROVE,1,full,RECEIVED,bytes(command));assertTrue(result.canonicalIntent().contains(full.canonicalIntent()));
        var changed=revision().put("expectedBankGeneration",8);var different=BankEnrollmentRevisionCodec.decode(bytes(changed));assertThrows(IllegalArgumentException.class,()->EnrollmentProposalCommandCodec.decodeRevision(TENANT,BANK,PROPOSAL,io.fireflysoftware.core.organization.interfaces.authority.EnrollmentProposalCommand.Operation.APPROVE,1,different,RECEIVED,bytes(command)));
    }
    @Test void literalUtf8GoldenRevisionPinsTheNewWrapperEncodingAndDigest() throws Exception {
        try(var in=getClass().getResourceAsStream("/authority/bank-enrollment-revision-v2-golden.json")) {
            var vector=JSON.readTree(in);var actual=BankEnrollmentRevisionCodec.decode(vector.get("original").textValue().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            assertEquals(vector.get("canonical").textValue(),actual.canonicalIntent());assertEquals(vector.get("digest").textValue(),actual.intentDigest());
        }
    }
    @Test void eachAllowedUseNeedsItsOwnExactCurrentObservationAndTheMinimumHorizonWins() throws Exception {
        var n=revision();n.putArray("allowedUses").add("ACCOUNTING_BOOK").add("PRODUCT_ISSUANCE");var full=BankEnrollmentRevisionCodec.decode(bytes(n));var first=verify(observation());var secondJson=observation().put("requestedUse","PRODUCT_ISSUANCE").put("validUntilEpochMillis",RECEIVED+12000);
        var second=ConfigurationGrantObservationVerifier.verify(TENANT,INSTITUTION,ISSUER,io.fireflysoftware.core.organization.interfaces.authority.FinancialCalendarUse.PRODUCT_ISSUANCE,bytes(secondJson),RECEIVED);
        assertEquals(RECEIVED+12000,BankEnrollmentRevisionCodec.requireObservationSet(full,java.util.List.of(first,second),RECEIVED));
        assertThrows(IllegalArgumentException.class,()->BankEnrollmentRevisionCodec.requireObservationSet(full,java.util.List.of(first),RECEIVED));
        assertThrows(IllegalArgumentException.class,()->BankEnrollmentRevisionCodec.requireObservationSet(full,java.util.List.of(first,first),RECEIVED));
        assertThrows(IllegalStateException.class,()->BankEnrollmentRevisionCodec.requireObservationSet(full,java.util.List.of(first,second),RECEIVED+12000));
    }
    @Test void observationControlChangesAndForgedFullRevisionCannotReuseAnOldPreparedTarget() throws Exception {
        var full=BankEnrollmentRevisionCodec.decode(bytes(revision()));var changed=observation();((com.fasterxml.jackson.databind.node.ObjectNode)changed.get("control")).put("controlVersion",1);var evidence=verify(changed);
        assertThrows(IllegalArgumentException.class,()->BankEnrollmentRevisionCodec.requireObservationSet(full,java.util.List.of(evidence),RECEIVED));
        var forged=new OrganizationAuthorityIntent<>(full.originalJson(),full.request(),"forged","a".repeat(64));assertThrows(IllegalArgumentException.class,()->BankEnrollmentRevisionCodec.requireObservationSet(forged,java.util.List.of(verify(observation())),RECEIVED));
    }
}
