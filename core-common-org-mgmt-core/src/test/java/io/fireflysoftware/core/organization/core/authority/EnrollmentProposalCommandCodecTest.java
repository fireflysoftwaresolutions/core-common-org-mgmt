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

import io.fireflysoftware.core.organization.interfaces.authority.EnrollmentProposalCommand;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static io.fireflysoftware.core.organization.core.authority.ConfigurationGrantObservationVerifierTest.*;
import static io.fireflysoftware.core.organization.core.authority.OrganizationEnrollmentAuthorizationTest.*;

class EnrollmentProposalCommandCodecTest {
    static final String PROPOSAL="00000000-0000-0000-0000-000000000303",COMMAND="00000000-0000-0000-0000-000000000304";
    static OrganizationAuthorityIntent<io.fireflysoftware.core.organization.interfaces.authority.BankEnrollmentTarget> target() throws Exception {return BankEnrollmentIntentCodec.decode(bytes(intent()));}
    static com.fasterxml.jackson.databind.node.ObjectNode command(String operation)throws Exception{return JSON.createObjectNode().put("schemaVersion",2).put("actionId",COMMAND).put("operation",operation).put("expectedRevision",1).put("expectedControlVersion",2).put("expectedBankGeneration",7).put("expectedTargetDigest",target().intentDigest()).put("commandNotAfterEpochMillis",RECEIVED+5000).put("approvalUntilEpochMillis",RECEIVED+15000).put("reason","Independent approval of exact retained target");}
    @Test void fullRetainedTargetAndBankGenerationBindAnActorFreeClosedAction() throws Exception {
        var request=command("APPROVE");var result=EnrollmentProposalCommandCodec.decode(TENANT,BANK,PROPOSAL,EnrollmentProposalCommand.Operation.APPROVE,1,target(),RECEIVED,bytes(request));assertEquals(7,result.request().expectedBankGeneration());assertTrue(result.canonicalIntent().contains(target().canonicalIntent()));assertEquals(request.toString(),result.originalJson());assertNotEquals(target().intentDigest(),result.intentDigest());
    }
    @Test void durableApprovalMayOutliveTheWriteCommandButNeverTheRetainedTargetDeadline() throws Exception {
        var n=command("APPROVE");var result=EnrollmentProposalCommandCodec.decode(TENANT,BANK,PROPOSAL,EnrollmentProposalCommand.Operation.APPROVE,1,target(),RECEIVED,bytes(n));assertEquals(RECEIVED+15000,result.request().approvalUntilEpochMillis());n.put("approvalUntilEpochMillis",RECEIVED+20001);assertThrows(IllegalArgumentException.class,()->EnrollmentProposalCommandCodec.decode(TENANT,BANK,PROPOSAL,EnrollmentProposalCommand.Operation.APPROVE,1,target(),RECEIVED,bytes(n)));
    }
    @Test void unknownHumanClaimsCoercionsRouteAndTargetMismatchReject() throws Exception {
        var actor=command("APPROVE");actor.put("staffActorId",STAFF);assertThrows(IllegalArgumentException.class,()->EnrollmentProposalCommandCodec.decode(TENANT,BANK,PROPOSAL,EnrollmentProposalCommand.Operation.APPROVE,1,target(),RECEIVED,bytes(actor)));
        var wrong=command("APPROVE");wrong.put("expectedBankGeneration","7");assertThrows(IllegalArgumentException.class,()->EnrollmentProposalCommandCodec.decode(TENANT,BANK,PROPOSAL,EnrollmentProposalCommand.Operation.APPROVE,1,target(),RECEIVED,bytes(wrong)));
        var route=command("APPROVE");assertThrows(IllegalArgumentException.class,()->EnrollmentProposalCommandCodec.decode(TENANT,BANK,PROPOSAL,EnrollmentProposalCommand.Operation.WITHDRAW,1,target(),RECEIVED,bytes(route)));
        var digest=command("APPROVE");digest.put("expectedTargetDigest","b".repeat(64));assertThrows(IllegalArgumentException.class,()->EnrollmentProposalCommandCodec.decode(TENANT,BANK,PROPOSAL,EnrollmentProposalCommand.Operation.APPROVE,1,target(),RECEIVED,bytes(digest)));
    }
    @Test void forgedRetainedCanonicalReferenceIsNotAcceptedAsFullIntent() throws Exception {
        var actual=target();var forged=new OrganizationAuthorityIntent<>(actual.originalJson(),actual.request(),"forged","a".repeat(64));assertThrows(IllegalArgumentException.class,()->EnrollmentProposalCommandCodec.decode(TENANT,BANK,PROPOSAL,EnrollmentProposalCommand.Operation.APPROVE,1,forged,RECEIVED,bytes(command("APPROVE"))));
    }
    @Test void lifecycleDecisionCannotConsumeTheReservedExecutionActionIdentity() throws Exception {
        var n=command("APPROVE");n.put("actionId",ACTION);
        assertThrows(IllegalArgumentException.class,()->EnrollmentProposalCommandCodec.decode(TENANT,BANK,PROPOSAL,EnrollmentProposalCommand.Operation.APPROVE,1,target(),RECEIVED,bytes(n)));
    }
    @Test void semanticallyIdenticalJsonRetainsOriginalBytesButHasOneCanonicalAction() throws Exception {
        var n=command("APPROVE");var a=EnrollmentProposalCommandCodec.decode(TENANT,BANK,PROPOSAL,EnrollmentProposalCommand.Operation.APPROVE,1,target(),RECEIVED,bytes(n));
        var formatted=JSON.writerWithDefaultPrettyPrinter().writeValueAsBytes(n);var b=EnrollmentProposalCommandCodec.decode(TENANT,BANK,PROPOSAL,EnrollmentProposalCommand.Operation.APPROVE,1,target(),RECEIVED,formatted);
        assertEquals(a.canonicalIntent(),b.canonicalIntent());assertEquals(a.intentDigest(),b.intentDigest());assertNotEquals(a.originalJson(),b.originalJson());
        var changed=n.deepCopy().put("expectedBankGeneration",8);assertNotEquals(a.intentDigest(),EnrollmentProposalCommandCodec.decode(TENANT,BANK,PROPOSAL,EnrollmentProposalCommand.Operation.APPROVE,1,target(),RECEIVED,bytes(changed)).intentDigest());
    }
    @Test void decisionDeadlineNullableApprovalScopeAndExactRevisionFailClosed() throws Exception {
        var absent=command("APPROVE").putNull("approvalUntilEpochMillis");assertThrows(IllegalArgumentException.class,()->EnrollmentProposalCommandCodec.decode(TENANT,BANK,PROPOSAL,EnrollmentProposalCommand.Operation.APPROVE,1,target(),RECEIVED,bytes(absent)));
        var wrong=command("WITHDRAW");assertThrows(IllegalArgumentException.class,()->EnrollmentProposalCommandCodec.decode(TENANT,BANK,PROPOSAL,EnrollmentProposalCommand.Operation.WITHDRAW,1,target(),RECEIVED,bytes(wrong)));
        wrong.putNull("approvalUntilEpochMillis");assertNotNull(EnrollmentProposalCommandCodec.decode(TENANT,BANK,PROPOSAL,EnrollmentProposalCommand.Operation.WITHDRAW,1,target(),RECEIVED,bytes(wrong)));
        var expired=command("APPROVE").put("commandNotAfterEpochMillis",RECEIVED);assertThrows(IllegalArgumentException.class,()->EnrollmentProposalCommandCodec.decode(TENANT,BANK,PROPOSAL,EnrollmentProposalCommand.Operation.APPROVE,1,target(),RECEIVED,bytes(expired)));
        assertThrows(IllegalArgumentException.class,()->EnrollmentProposalCommandCodec.decode(PLATFORM,BANK,PROPOSAL,EnrollmentProposalCommand.Operation.APPROVE,1,target(),RECEIVED,bytes(command("APPROVE"))));
        assertThrows(IllegalArgumentException.class,()->EnrollmentProposalCommandCodec.decode(TENANT,BANK,PROPOSAL,EnrollmentProposalCommand.Operation.APPROVE,2,target(),RECEIVED,bytes(command("APPROVE"))));
    }
}
