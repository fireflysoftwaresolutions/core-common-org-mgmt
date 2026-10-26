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

import io.fireflysoftware.core.organization.core.services.authority.BankEnrollmentPreparationService;
import io.fireflysoftware.core.organization.core.services.authority.BankEnrollmentPreparationRecoveryService;
import io.fireflysoftware.core.organization.interfaces.authority.*;
import io.fireflysoftware.core.organization.interfaces.authority.EnrollmentProposalCommandResult;
import io.swagger.v3.oas.annotations.media.Schema;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class BankEnrollmentPreparationControllerTest {
    static final UUID TENANT=BankEnrollmentProposalControllerTest.TENANT,BANK=BankEnrollmentProposalControllerTest.BANK,ACTION=BankEnrollmentProposalControllerTest.PROPOSAL;
    static byte[] body(String mode){return ("{\"schemaVersion\":2,\"actionId\":\""+ACTION+"\",\"mode\":\""+mode+"\",\"bankId\":\""+BANK+"\",\"destinationTenantId\":\""+TENANT+"\",\"institutionId\":\"00000000-0000-0000-0000-000000000005\",\"issuerAuthorityId\":\"00000000-0000-0000-0000-000000000006\",\"allowedUses\":[\"ACCOUNTING_BOOK\"],\"notAfterEpochMillis\":1792714200000,\"reason\":\"Exact desired preparation\",\"evidenceReferences\":[\"fixture://source-controller\"]}").getBytes(StandardCharsets.UTF_8);}
    static EnrollmentPreparedProposal result(){return new EnrollmentPreparedProposal(2,new EnrollmentProposalCommandResult(2,ACTION.toString(),TENANT.toString(),BANK.toString(),"00000000-0000-0000-0000-000000000007",1,"PREPARE","DRAFT",0,"a".repeat(64),"00000000-0000-0000-0000-000000000008","00000000-0000-0000-0000-000000000009","00000000-0000-0000-0000-000000000010",1792713600000L),new EnrollmentActionHistory.Payload("fixture","{}","fixture","a".repeat(64)));}
    @Test void prepareRoutesExactExplicitModeAndOriginalJsonWithoutSchemaBase64()throws Exception {var calls=new AtomicInteger();BankEnrollmentPreparationService service=(mode,t,b,bytes)->{calls.incrementAndGet();assertEquals(TENANT,t);assertEquals(BANK,b);assertArrayEquals(body(mode.name()),bytes);return Mono.just(result());};var controller=new BankEnrollmentPreparationController(service);for(var mode:BankEnrollmentPreparationRequest.Mode.values()){var response=controller.prepareBankEnrollmentProposal(TENANT,BANK,body(mode.name())).block();assertEquals("no-store",response.getHeaders().getCacheControl());assertEquals(result(),response.getBody());}assertEquals(2,calls.get());var method=BankEnrollmentPreparationController.class.getMethod("prepareBankEnrollmentProposal",UUID.class,UUID.class,byte[].class);var spec=method.getParameters()[2].getAnnotation(io.swagger.v3.oas.annotations.parameters.RequestBody.class);assertEquals(BankEnrollmentPreparationRequest.class,spec.content()[0].schema().implementation());}
    @Test void closedInvalidModeOrDuplicateBodyCannotReachPreparationApplication(){var calls=new AtomicInteger();var controller=new BankEnrollmentPreparationController((m,t,b,bytes)->{calls.incrementAndGet();return Mono.empty();});String valid=new String(body("ENROLL"),StandardCharsets.UTF_8);for(var changed:java.util.List.of(valid.replace("\"mode\":\"ENROLL\"","\"mode\":\"UNKNOWN\""),valid.replace("\"mode\":\"ENROLL\"","\"mode\":\"ENROLL\",\"mode\":\"ENROLL\""),valid.replace("\"reason\":","\"injectedActor\":true,\"reason\":"))){assertEquals(400,assertThrows(ResponseStatusException.class,()->controller.prepareBankEnrollmentProposal(TENANT,BANK,changed.getBytes(StandardCharsets.UTF_8)).block()).getStatusCode().value());}assertEquals(0,calls.get());}
    @Test void recoveryDelegatesOriginalDesiredIntentToIndependentReadServiceWithoutFreshDeadlineParsing()throws Exception {var calls=new AtomicInteger();BankEnrollmentPreparationRecoveryService service=(t,b,a,bytes)->{calls.incrementAndGet();assertEquals(TENANT,t);assertEquals(BANK,b);assertEquals(ACTION,a);assertArrayEquals(body("ENROLL"),bytes);return Mono.just(result());};var controller=new BankEnrollmentPreparationRecoveryController(service);var response=controller.recoverBankEnrollmentPreparation(TENANT,BANK,ACTION,body("ENROLL")).block();assertEquals("no-store",response.getHeaders().getCacheControl());assertEquals(result(),response.getBody());assertEquals(1,calls.get());var method=BankEnrollmentPreparationRecoveryController.class.getMethod("recoverBankEnrollmentPreparation",UUID.class,UUID.class,UUID.class,byte[].class);var spec=method.getParameters()[3].getAnnotation(io.swagger.v3.oas.annotations.parameters.RequestBody.class);assertEquals(BankEnrollmentPreparationRequest.class,spec.content()[0].schema().implementation());assertEquals("firefly.organization.enrollment.action-read.enabled",BankEnrollmentPreparationRecoveryController.class.getAnnotation(org.springframework.boot.autoconfigure.condition.ConditionalOnProperty.class).name()[0]);}
}
