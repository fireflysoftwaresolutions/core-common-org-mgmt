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
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static io.fireflysoftware.core.organization.core.authority.ConfigurationGrantObservationVerifierTest.*;
import static io.fireflysoftware.core.organization.core.authority.OrganizationEnrollmentAuthorizationTest.*;

class PreparationCommandCodecTest {
    static com.fasterxml.jackson.databind.JsonNode vector()throws Exception {try(var in=PreparationCommandCodecTest.class.getResourceAsStream("/authority/bank-preparation-command-v2-golden.json")){return JSON.readTree(in);}}
    static ProposalTerminationEvidenceCodec.Encoded decode(byte[] command,String action,String operation,String route)throws Exception {var value=JSON.readTree(command);var retained=BankEnrollmentRevisionCodec.decode(value.get("originalRevisionJson").textValue().getBytes(java.nio.charset.StandardCharsets.UTF_8));return PreparationWriteEvidenceCodec.retainedCommand(TENANT,BANK,action,operation,route,RECEIVED,command,retained);}
    @Test void independentLiteralUtf8CommandVectorBindsDesiredAndFullRetainedRevision()throws Exception {var vector=vector();var command=JSON.readTree(vector.get("original").textValue());var decoded=decode(vector.get("original").textValue().getBytes(java.nio.charset.StandardCharsets.UTF_8),vector.get("actionId").textValue(),vector.get("operation").textValue(),command.get("intendedRoute").textValue());assertEquals(vector.get("canonical").textValue(),decoded.canonical());assertEquals(vector.get("digest").textValue(),decoded.digest());assertEquals(vector.get("encoding").textValue(),decoded.encoding());}
    @Test void outerAndEmbeddedFormattingDoNotChangeTheFullCommandCanonicalIntent()throws Exception {var vector=vector();var command=(com.fasterxml.jackson.databind.node.ObjectNode)JSON.readTree(vector.get("original").textValue());command.put("originalDesiredJson",JSON.writerWithDefaultPrettyPrinter().writeValueAsString(JSON.readTree(command.get("originalDesiredJson").textValue())));command.put("originalRevisionJson",JSON.writerWithDefaultPrettyPrinter().writeValueAsString(JSON.readTree(command.get("originalRevisionJson").textValue())));var result=decode(JSON.writerWithDefaultPrettyPrinter().writeValueAsBytes(command),vector.get("actionId").textValue(),vector.get("operation").textValue(),command.get("intendedRoute").textValue());assertEquals(vector.get("canonical").textValue(),result.canonical());assertEquals(vector.get("digest").textValue(),result.digest());}
    @Test void closedScopeOperationAndEmbeddedDesiredChangesCannotReuseRetainedCommandBytes()throws Exception {var vector=vector();var base=JSON.readTree(vector.get("original").textValue());String action=vector.get("actionId").textValue(),op=vector.get("operation").textValue(),route=base.get("intendedRoute").textValue();for(String field:List.of("unknownField","tenantId","bankId","canonicalDesiredRequest","desiredRequestDigest")){var changed=(com.fasterxml.jackson.databind.node.ObjectNode)base.deepCopy();changed.put(field,PLATFORM);assertThrows(IllegalArgumentException.class,()->decode(bytes(changed),action,op,route));}assertThrows(IllegalArgumentException.class,()->decode(bytes(base),ACTION,op,route));assertThrows(IllegalArgumentException.class,()->decode(bytes(base),action,"TRANSFER_PREPARE",route));var changed=(com.fasterxml.jackson.databind.node.ObjectNode)base.deepCopy();var desired=(com.fasterxml.jackson.databind.node.ObjectNode)JSON.readTree(changed.get("originalDesiredJson").textValue());desired.put("reason","changed desired reason");changed.put("originalDesiredJson",desired.toString());assertThrows(IllegalArgumentException.class,()->decode(bytes(changed),action,op,route));}
}
