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

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.fireflysoftware.core.organization.interfaces.authority.*;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.List;
import static io.fireflysoftware.core.organization.core.authority.CalendarTestInputs.*;
import static org.junit.jupiter.api.Assertions.*;

class CalendarPublicationExecutionCodecTest {
    private static final JsonMapper JSON = new JsonMapper();

    private static com.fasterxml.jackson.databind.JsonNode vector() throws Exception {
        try (var in = CalendarPublicationExecutionCodecTest.class.getResourceAsStream("/authority/organization-publication-execution-v2-golden.json")) {
            return JSON.readTree(in);
        }
    }
    private static ObjectNode target() throws Exception {
        return (ObjectNode) JSON.readTree(vector().get("original").textValue());
    }
    private static OrganizationAuthorityIntent<CalendarPublicationExecutionTarget> decode(com.fasterxml.jackson.databind.JsonNode value) {
        return CalendarPublicationExecutionCodec.decode(TENANT, INSTITUTION, ISSUER,
                value.toString().getBytes(StandardCharsets.UTF_8));
    }

    @Test void literalV2EnvelopeRetainsFullGrantAndNoEphemeralObservationTimestamp() throws Exception {
        var vector = vector();
        var decoded = decode(target());
        assertEquals(vector.get("canonical").textValue(), decoded.canonicalIntent());
        assertEquals(vector.get("digest").textValue(), decoded.intentDigest());
        assertEquals(2, decoded.request().schemaVersion());
        assertEquals(2, decoded.request().grantDefinition().target().eligibilityEpoch().tenantGeneration());
        assertNull(decoded.request().legacyBinding());
        assertNull(decoded.request().expectedHeadControlVersion());
    }

    @Test void pureV1TargetIsNeverAcceptedAsNativeExecutionEnvelope() throws Exception {
        var pure = CalendarTestInputs.vector().get("publicationTargetOriginal").textValue();
        assertThrows(IllegalArgumentException.class, () -> CalendarPublicationExecutionCodec.decode(
                TENANT, INSTITUTION, ISSUER, pure.getBytes(StandardCharsets.UTF_8)));
        var changed = target(); changed.put("schemaVersion", 1);
        assertThrows(IllegalArgumentException.class, () -> decode(changed));
    }

    @Test void grantHeadControlEpochAndReferenceChangesCannotSubstituteStaticApproval() throws Exception {
        for (String field : List.of("expectedGrantHeadVersion", "expectedGrantControlVersion")) {
            var changed = target(); changed.put(field, -1);
            assertThrows(IllegalArgumentException.class, () -> decode(changed));
        }
        var head = target(); head.put("expectedGrantHeadVersion", 2);
        assertThrows(IllegalArgumentException.class, () -> decode(head));
        var digest = target(); ((ObjectNode) digest.at("/publication/definition/grant")).put("digest", "a".repeat(64));
        assertThrows(IllegalArgumentException.class, () -> decode(digest));
        var epoch = target(); ((ObjectNode) epoch.at("/grantDefinition/target/eligibilityEpoch")).put("tenantGeneration", 99);
        assertThrows(IllegalArgumentException.class, () -> decode(epoch));
        var control = target(); control.put("expectedGrantControlVersion", 1);
        assertNotEquals(decode(target()).intentDigest(), decode(control).intentDigest());
    }

    @Test void nativeInstitutionCannotCarryInventedLegacyEnrollmentOrHeadControl() throws Exception {
        var changed = target(); changed.put("expectedHeadControlVersion", 0);
        assertThrows(IllegalArgumentException.class, () -> decode(changed));
        var legacy = target(); legacy.set("legacyBinding", JSON.createObjectNode()
                .put("bankId", SOURCE).put("ownershipGeneration", 1).put("sourceGeneration", 0)
                .put("enrollmentId", PUBLICATION).put("enrollmentVersion", 1)
                .put("enrollmentDigest", "a".repeat(64)).put("enrollmentControlVersion", 0));
        assertThrows(IllegalArgumentException.class, () -> decode(legacy));
    }

    @Test void closedRecordsRejectExtraMissingDuplicateCoercionAndObservationFacts() throws Exception {
        for (String path : List.of("", "/publication", "/grantDefinition", "/grantDefinition/target/grant")) {
            var changed = target(); var node = (ObjectNode) (path.isEmpty() ? changed : changed.at(path));
            node.put("observedAtEpochMillis", 1792713600123L);
            assertThrows(IllegalArgumentException.class, () -> decode(changed));
        }
        var missing = target(); missing.remove("legacyBinding");
        assertThrows(IllegalArgumentException.class, () -> decode(missing));
        var coercion = target(); coercion.put("expectedGrantHeadVersion", "1");
        assertThrows(IllegalArgumentException.class, () -> decode(coercion));
        var duplicate = vector().get("original").textValue().replace("\"schemaVersion\":2,", "\"schemaVersion\":2,\"schemaVersion\":2,");
        assertThrows(IllegalArgumentException.class, () -> CalendarPublicationExecutionCodec.decode(TENANT, INSTITUTION, ISSUER, duplicate.getBytes(StandardCharsets.UTF_8)));
    }

    @Test void refreshedObservationMustMatchApprovedStaticFactsAndStillBeUsable() throws Exception {
        var target = decode(target()).request();
        var observation = ConfigurationGrantObservationVerifierTest.observation();
        var verified = ConfigurationGrantObservationVerifierTest.verify(observation);
        CalendarPublicationExecutionCodec.requireObservation(target, verified, ConfigurationGrantObservationVerifierTest.RECEIVED);
        assertThrows(IllegalStateException.class, () -> CalendarPublicationExecutionCodec.requireObservation(target, verified,
                verified.usableUntilEpochMillis()));
        ((ObjectNode) observation.get("control")).put("controlVersion", 1);
        var changed = ConfigurationGrantObservationVerifierTest.verify(observation);
        assertThrows(IllegalArgumentException.class, () -> CalendarPublicationExecutionCodec.requireObservation(target, changed,
                ConfigurationGrantObservationVerifierTest.RECEIVED));
    }
    @Test void existingHeadControlIsRequiredAndEveryControlChangeChangesApprovalDigest() throws Exception {
        var existing = target();
        ((ObjectNode) existing.get("publication")).put("version", 2).put("expectedSeriesHeadVersion", 1);
        assertThrows(IllegalArgumentException.class, () -> decode(existing));
        existing.put("expectedHeadControlVersion", 0);
        var initial = decode(existing);
        existing.put("expectedHeadControlVersion", 1);
        assertNotEquals(initial.intentDigest(), decode(existing).intentDigest());
        existing.put("expectedHeadControlVersion", Long.MAX_VALUE);
        assertThrows(IllegalArgumentException.class, () -> decode(existing));
    }

    @Test void legacyProfileRequiresExactEnrollmentBankAndNonnegativeSourceGenerations() throws Exception {
        var nativeTarget = decode(target()).request();
        String bank = "00000000-0000-0000-0000-000000000010";
        String calendar = "00000000-0000-0000-0000-000000000011";
        String enrollment = "00000000-0000-0000-0000-000000000012";
        var scope = new FinancialCalendarTarget(CalendarTargetKind.BANK, bank);
        var snapshot = new FinancialCalendarSourceSnapshot(bank, calendar, bank, true, scope,
                List.of(), List.of(), null, List.of());
        var draft = new CalendarPublicationDraft(null, 0, nativeTarget.publication().definition().grant(),
                new LegacyBankCalendarDraft("LEGACY_BANK_CALENDAR", enrollment, 1, "b".repeat(64), calendar,
                        scope, OrganizationAuthorityIntentCodec.sourceSnapshotDigest(snapshot)), draft().rules());
        var definition = new LegacyBankCalendarSourcePreparer().prepare(TENANT, INSTITUTION, ISSUER, draft, snapshot);
        var pure = nativeTarget.publication();
        var publication = new CalendarPublicationTarget(pure.actionId(), pure.proposalId(), pure.proposalRevision(),
                pure.publicationId(), pure.version(), pure.expectedSeriesHeadVersion(), definition, pure.preparedAtEpochMillis());
        var binding = new LegacyCalendarAuthorityBinding(bank, 1, 0, enrollment, 1, "b".repeat(64), 0);
        var full = new CalendarPublicationExecutionTarget(2, publication, nativeTarget.grantDefinition(), 1, 0, null, binding);
        var node = (ObjectNode) JSON.readTree(JSON.writeValueAsBytes(full));
        assertEquals(bank, decode(node).request().legacyBinding().bankId());
        for (String field : List.of("bankId", "enrollmentId", "enrollmentVersion", "enrollmentDigest",
                "ownershipGeneration", "sourceGeneration", "enrollmentControlVersion")) {
            var changed = node.deepCopy(); var legacy = (ObjectNode) changed.get("legacyBinding");
            if (field.endsWith("Id")) legacy.put(field, SOURCE);
            else if (field.endsWith("Digest")) legacy.put(field, "c".repeat(64));
            else legacy.put(field, field.equals("enrollmentVersion") ? 2 : -1);
            assertThrows(IllegalArgumentException.class, () -> decode(changed), field);
        }
        var missing = node.deepCopy(); missing.putNull("legacyBinding");
        assertThrows(IllegalArgumentException.class, () -> decode(missing));
        var restored = node.deepCopy(); ((ObjectNode) restored.get("legacyBinding")).put("sourceGeneration", 2);
        assertNotEquals(decode(node).intentDigest(), decode(restored).intentDigest());
    }

    @Test void routeScopeAndMalformedOrOversizeInputCannotEnterExecutionCodec() throws Exception {
        byte[] original = vector().get("original").textValue().getBytes(StandardCharsets.UTF_8);
        for (String[] scope : List.of(new String[]{SOURCE, INSTITUTION, ISSUER},
                new String[]{TENANT, SOURCE, ISSUER}, new String[]{TENANT, INSTITUTION, SOURCE})) {
            assertThrows(IllegalArgumentException.class, () -> CalendarPublicationExecutionCodec.decode(
                    scope[0], scope[1], scope[2], original));
        }
        assertThrows(IllegalArgumentException.class, () -> CalendarPublicationExecutionCodec.decode(
                TENANT, INSTITUTION, ISSUER, new byte[]{(byte) 0xc3, 0x28}));
        assertThrows(IllegalArgumentException.class, () -> CalendarPublicationExecutionCodec.decode(
                TENANT, INSTITUTION, ISSUER, new byte[2097153]));
    }

}
