package io.fireflysoftware.core.organization.core.authority;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.fireflysoftware.core.organization.interfaces.authority.*;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ConfigurationGrantObservationVerifierTest {
    static final JsonMapper JSON=new JsonMapper();
    static final String TENANT="00000000-0000-0000-0000-000000000001",INSTITUTION="00000000-0000-0000-0000-000000000002",ISSUER="00000000-0000-0000-0000-000000000006";
    static final long OBSERVED=1792713600123L,RECEIVED=OBSERVED+100;
    static ObjectNode observation() throws Exception {
        JsonNode vector;try(var in=ConfigurationGrantObservationVerifierTest.class.getResourceAsStream("/authority/configuration-grant-definition-v2-golden.json")){vector=JSON.readTree(in);}
        var node=JSON.createObjectNode().put("tenantId",TENANT).put("institutionId",INSTITUTION).put("issuerAuthorityId",ISSUER).put("requestedUse","ACCOUNTING_BOOK").put("state","CURRENT_AUTHORIZED").put("headVersion",1).put("observedAtEpochMillis",OBSERVED).put("validUntilEpochMillis",OBSERVED+30000).putNull("denialReason");node.set("definition",JSON.readTree(vector.get("original").textValue()));node.set("control",JSON.createObjectNode().put("state","ACTIVE").put("controlVersion",0).put("lastActionId","00000000-0000-0000-0000-000000000003"));node.set("tenantEligibility",JSON.createObjectNode().put("tenantId",TENANT).put("tenantGeneration",2).put("statusId","00000000-0000-0000-0000-000000000010").put("statusGeneration",3).put("tenantActive",true).put("statusEnabled",true).put("statusCode","ACTIVE").put("eligibleNow",true).putNull("lastActionId"));return node;
    }
    static byte[] bytes(JsonNode value){return value.toString().getBytes(StandardCharsets.UTF_8);}
    static VerifiedInstitutionGrantObservation verify(JsonNode value) {return ConfigurationGrantObservationVerifier.verify(TENANT,INSTITUTION,ISSUER,FinancialCalendarUse.ACCOUNTING_BOOK,bytes(value),RECEIVED);}
    static InstitutionAuthorityGrantReference reference(JsonNode node){return new InstitutionAuthorityGrantReference("00000000-0000-0000-0000-000000000005",1,node.get("definition").get("definitionDigest").textValue(),FinancialCalendarUse.ACCOUNTING_BOOK);}
    @Test void literalFullV2DefinitionAndLiveFactsArePreservedAndReferenceBound() throws Exception {
        var node=observation();var evidence=verify(node);assertEquals(node.toString(),evidence.originalObservationJson());assertEquals(node.get("definition").get("canonicalDefinition").textValue(),evidence.observation().definition().canonicalDefinition());assertEquals(2,evidence.observation().definition().target().eligibilityEpoch().tenantGeneration());assertEquals(3,evidence.observation().tenantEligibility().statusGeneration());evidence.requireCurrentAt(RECEIVED);evidence.requireReference(reference(node));assertThrows(UnsupportedOperationException.class,()->evidence.observation().definition().target().grant().uses().clear());
    }
    @Test void requestedIdentityUseAndExpectedReferenceCannotBeSubstituted() throws Exception {
        var node=observation();for(var field:List.of("tenantId","institutionId","issuerAuthorityId","requestedUse")){var changed=node.deepCopy();changed.put(field,field.equals("requestedUse")?"PRODUCT_ISSUANCE":"00000000-0000-0000-0000-000000000099");assertThrows(IllegalArgumentException.class,()->verify(changed),field);}
        var evidence=verify(node);for(var ref:List.of(new InstitutionAuthorityGrantReference(reference(node).grantId(),2,reference(node).digest(),FinancialCalendarUse.ACCOUNTING_BOOK),new InstitutionAuthorityGrantReference(reference(node).grantId(),1,"a".repeat(64),FinancialCalendarUse.ACCOUNTING_BOOK),new InstitutionAuthorityGrantReference(reference(node).grantId(),1,reference(node).digest(),FinancialCalendarUse.PRODUCT_ISSUANCE)))assertThrows(IllegalArgumentException.class,()->evidence.requireReference(ref));
    }
    @Test void headControlEpochAndEligibilityMustAllAuthorizeTheSameGrant() throws Exception {
        var node=observation();for(var state:List.of("SUSPENDED","REVOKED")){var changed=node.deepCopy();((ObjectNode)changed.get("control")).put("state",state);assertThrows(IllegalArgumentException.class,()->verify(changed));}
        for(var field:List.of("tenantGeneration","statusGeneration","tenantActive","statusEnabled","eligibleNow","statusCode","statusId")){var changed=node.deepCopy();var live=(ObjectNode)changed.get("tenantEligibility");if(field.endsWith("Generation"))live.put(field,99);else if(field.equals("statusCode"))live.put(field,"DISABLED");else if(field.equals("statusId"))live.put(field,"00000000-0000-0000-0000-000000000099");else live.put(field,false);assertThrows(IllegalArgumentException.class,()->verify(changed),field);}
        var changed=node.deepCopy();changed.put("headVersion",2);assertThrows(IllegalArgumentException.class,()->verify(changed));
    }
    @Test void canonicalEncodingDigestAndTargetTamperingCannotBeAccepted() throws Exception {
        var node=observation();for(var field:List.of("canonicalDefinition","definitionEncoding","definitionDigest")){var changed=node.deepCopy();((ObjectNode)changed.get("definition")).put(field,field.equals("definitionDigest")?"a".repeat(64):"changed");assertThrows(IllegalArgumentException.class,()->verify(changed),field);}
        var changed=node.deepCopy();((ObjectNode)changed.get("definition").get("target").get("eligibilityEpoch")).put("tenantGeneration",9);assertThrows(IllegalArgumentException.class,()->verify(changed));
    }
    @Test void responseIsClosedAtEveryRecordAndRejectsOmissionsDuplicatesAndCoercion() throws Exception {
        var node=observation();for(var path:List.of("","definition","definition/target","definition/target/grant","definition/target/eligibilityEpoch","control","tenantEligibility")){var changed=node.deepCopy();var target=(ObjectNode)(path.isEmpty()?changed:changed.at("/"+path));target.put("unexpected",true);assertThrows(IllegalArgumentException.class,()->verify(changed),path);}
        var missing=node.deepCopy();missing.remove("denialReason");assertThrows(IllegalArgumentException.class,()->verify(missing));var coerced=node.deepCopy();coerced.put("headVersion","1");assertThrows(IllegalArgumentException.class,()->verify(coerced));
        var duplicate=node.toString().replace("\"headVersion\":1","\"headVersion\":1,\"headVersion\":1");assertThrows(IllegalArgumentException.class,()->ConfigurationGrantObservationVerifier.verify(TENANT,INSTITUTION,ISSUER,FinancialCalendarUse.ACCOUNTING_BOOK,duplicate.getBytes(StandardCharsets.UTF_8),RECEIVED));
        assertThrows(IllegalArgumentException.class,()->ConfigurationGrantObservationVerifier.verify(TENANT,INSTITUTION,ISSUER,FinancialCalendarUse.ACCOUNTING_BOOK,new byte[]{(byte)0xc3,0x28},RECEIVED));
    }
    @Test void validityIsFiniteNeverExtendedAndMustBeCurrentAgainAfterWaits() throws Exception {
        var node=observation();var evidence=verify(node);assertEquals(OBSERVED+30000,evidence.usableUntilEpochMillis());assertThrows(IllegalStateException.class,()->evidence.requireCurrentAt(OBSERVED+30000));
        for(var until:new long[]{RECEIVED,OBSERVED+30001,Long.MAX_VALUE}){var changed=node.deepCopy();changed.put("validUntilEpochMillis",until);assertThrows(IllegalArgumentException.class,()->verify(changed));}
        var future=node.deepCopy();future.put("observedAtEpochMillis",RECEIVED+1000).put("validUntilEpochMillis",RECEIVED+31000);assertEquals(RECEIVED+30000,verify(future).usableUntilEpochMillis());
    }
    @Test void noCurrentAuthorityRemainsDenialAndCannotAuthorizeUse() throws Exception {
        var node=observation();node.put("state","NO_CURRENT_AUTHORITY").putNull("validUntilEpochMillis").put("denialReason","TENANT_DISABLED");((ObjectNode)node.get("tenantEligibility")).put("tenantActive",false).put("eligibleNow",false);var evidence=verify(node);assertEquals("TENANT_DISABLED",evidence.observation().denialReason());assertThrows(IllegalStateException.class,()->evidence.requireCurrentAt(RECEIVED));
    }
}
