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
package io.fireflysoftware.core.organization.web.controllers.authority;

import io.fireflysoftware.core.organization.core.services.authority.BankEnrollmentProposalApplicationService;
import io.fireflysoftware.core.organization.interfaces.authority.EnrollmentProposalCommand;
import io.fireflysoftware.core.organization.interfaces.authority.EnrollmentProposalCommandResult;
import io.swagger.v3.oas.annotations.media.Schema;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class BankEnrollmentProposalControllerTest {
    static final UUID TENANT=UUID.fromString("00000000-0000-0000-0000-000000000001"),BANK=UUID.fromString("00000000-0000-0000-0000-000000000002"),PROPOSAL=UUID.fromString("00000000-0000-0000-0000-000000000003");
    static byte[] body(String operation){return ("{\"schemaVersion\":2,\"actionId\":\"00000000-0000-0000-0000-000000000004\",\"operation\":\""+operation+"\",\"expectedRevision\":7,\"expectedControlVersion\":2,\"expectedBankGeneration\":3,\"expectedTargetDigest\":\""+"a".repeat(64)+"\",\"commandNotAfterEpochMillis\":1792713605000,\"approvalUntilEpochMillis\":null,\"reason\":\"Exact retained proposal\"}").getBytes(StandardCharsets.UTF_8);}
    @Test void everyKnownRoutePassesItsExactOperationAndUnmodifiedClosedJsonBodyToApplication()throws Exception {
        var calls=new AtomicInteger();BankEnrollmentProposalApplicationService service=(op,tenant,bank,proposal,revision,bytes)->{
            calls.incrementAndGet();assertEquals(TENANT,tenant);assertEquals(BANK,bank);assertEquals(PROPOSAL,proposal);assertEquals(7,revision);assertArrayEquals(body(op.name()),bytes);
            return Mono.just(new EnrollmentProposalCommandResult(2,"00000000-0000-0000-0000-000000000004",tenant.toString(),bank.toString(),proposal.toString(),revision,op.name(),"SUBMITTED",3,"a".repeat(64),"00000000-0000-0000-0000-000000000005","00000000-0000-0000-0000-000000000006","00000000-0000-0000-0000-000000000007",1792713600123L));};
        var controller=new BankEnrollmentProposalController(service);
        for(var op:EnrollmentProposalCommand.Operation.values()){
            var method=BankEnrollmentProposalController.class.getMethod(switch(op){case SUBMIT->"submitBankEnrollmentProposal";case APPROVE->"approveBankEnrollmentProposal";case REJECT->"rejectBankEnrollmentProposal";case WITHDRAW->"withdrawBankEnrollmentProposal";case CANCEL->"cancelBankEnrollmentProposal";case CANCEL_EMERGENCY->"emergencyCancelBankEnrollmentProposal";},UUID.class,UUID.class,UUID.class,byte[].class);
            @SuppressWarnings("unchecked") var result=((Mono<org.springframework.http.ResponseEntity<EnrollmentProposalCommandResult>>)method.invoke(controller,TENANT,BANK,PROPOSAL,body(op.name()))).block();assertEquals("no-store",result.getHeaders().getCacheControl());assertEquals(op.name(),result.getBody().operation());
            var spec=method.getParameters()[3].getAnnotation(io.swagger.v3.oas.annotations.parameters.RequestBody.class);assertNotNull(spec);assertEquals(EnrollmentProposalCommand.class,spec.content()[0].schema().implementation());
        }
        assertEquals(6,calls.get());
    }
    @Test void duplicateUnknownCoercedOrMissingRevisionFailsBeforeApplication(){
        var calls=new AtomicInteger();var controller=new BankEnrollmentProposalController((op,t,b,p,r,body)->{calls.incrementAndGet();return Mono.empty();});
        String valid=new String(body("SUBMIT"),StandardCharsets.UTF_8);
        for(var changed:java.util.List.of(valid.replace("\"expectedRevision\":7","\"expectedRevision\":\"7\""),valid.replace("\"expectedRevision\":7","\"expectedRevision\":7,\"expectedRevision\":7"),valid.replace("\"expectedRevision\":7,",""),valid.replace("\"reason\":","\"injected\":true,\"reason\":"))){
            var error=assertThrows(ResponseStatusException.class,()->controller.submitBankEnrollmentProposal(TENANT,BANK,PROPOSAL,changed.getBytes(StandardCharsets.UTF_8)).block());assertEquals(400,error.getStatusCode().value());
        }
        assertEquals(0,calls.get());
    }
}
