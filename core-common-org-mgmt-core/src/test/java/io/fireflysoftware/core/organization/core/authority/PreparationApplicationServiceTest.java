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

import io.fireflysoftware.core.organization.core.services.authority.impl.BankEnrollmentPreparationServiceImpl;
import io.fireflysoftware.core.organization.interfaces.authority.*;
import io.fireflysoftware.core.organization.models.entities.Bank;
import io.fireflysoftware.core.organization.models.repositories.BankRepository;
import io.fireflysoftware.core.organization.models.repositories.authority.*;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import io.r2dbc.spi.ConnectionFactory;
import io.fireflysoftware.core.organization.models.entities.authority.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static io.fireflysoftware.core.organization.core.authority.ConfigurationGrantObservationVerifierTest.*;
import static io.fireflysoftware.core.organization.core.authority.OrganizationEnrollmentAuthorizationTest.*;
import static io.fireflysoftware.core.organization.core.authority.OrganizationProposalAuthorizationTest.*;

class PreparationApplicationServiceTest {
    static class Fixture extends PreparationCommandServiceTest.Fixture {
        final io.fireflysoftware.core.organization.core.services.authority.BankEnrollmentPreparationObservationService observations=mock(io.fireflysoftware.core.organization.core.services.authority.BankEnrollmentPreparationObservationService.class);
        Fixture()throws Exception {when(observations.observeForPreparation(any())).thenReturn(Mono.just(BankEnrollmentPreparationPlanTest.evidence()));}
        BankEnrollmentPreparationServiceImpl application(){return new BankEnrollmentPreparationServiceImpl(preparation,capture(),observations,writer());}
        EnrollmentPreparedProposal run(){return application().prepare(BankEnrollmentPreparationRequest.Mode.ENROLL,UUID.fromString(TENANT),UUID.fromString(BANK),bytes(BankEnrollmentPreparationCodecTest.desired())).block();}
    }
    @Test void actualApplicationClosesCaptureBeforeObservationAndBindsAllocatedFullTargetBeforeMappedPrepare()throws Exception {var f=new Fixture();when(f.observations.observeForPreparation(any())).thenAnswer(i->{verify(f.connection).commitTransaction();return Mono.just(BankEnrollmentPreparationPlanTest.evidence());});var result=f.run();assertNotNull(result);assertEquals("DRAFT",result.outcome().state());assertEquals(ACTION,result.outcome().actionId());var full=BankEnrollmentRevisionCodec.decode(result.revision().originalJson().getBytes(java.nio.charset.StandardCharsets.UTF_8));var target=BankEnrollmentRevisionCodec.enrollmentTarget(full.request()).request();assertEquals(result.outcome().proposalId(),full.request().proposalId());assertEquals(7,full.request().expectedBankGeneration());assertEquals(9,target.expectedSourceGeneration());assertEquals(4,new HashSet<>(List.of(ACTION,full.request().proposalId(),target.actionId(),target.enrollmentId())).size());verify(f.preparations).beginCapture(any(),anyString());verify(f.preparations).beginPreparation(any(),anyString());verify(f.connection,times(2)).commitTransaction();assertEquals(8,f.writes.size());}
    @Test void withdrawalDuringObservationCannotReachFullPreparationOrWritableNativeContext()throws Exception {var f=new Fixture();when(f.observations.observeForPreparation(any())).thenAnswer(i->{f.current.policy.set(org.fireflyframework.security.api.domain.Decision.deny("withdrawn during observation"));return Mono.just(BankEnrollmentPreparationPlanTest.evidence());});assertEquals(403,assertThrows(ResponseStatusException.class,f::run).getStatusCode().value());verify(f.preparations,never()).beginPreparation(any(),anyString());assertTrue(f.writes.isEmpty());verify(f.connection).commitTransaction();}
    @Test void deniedFullTargetCannotBeSubstitutedByEarlierDesiredScopePermission()throws Exception {var f=new Fixture();f.current.duringPolicy=()->{if("PREPARE_FULL".equals(f.current.context.get("stage")))f.current.policy.set(org.fireflyframework.security.api.domain.Decision.deny("full target not authorized"));};assertEquals(403,assertThrows(ResponseStatusException.class,f::run).getStatusCode().value());verify(f.preparations,never()).beginPreparation(any(),anyString());assertTrue(f.writes.isEmpty());verify(f.observations).observeForPreparation(any());}
    @Test void incompleteObservationSetCannotAllocateAPersistedProposalOrReturnSuccess()throws Exception {var f=new Fixture();when(f.observations.observeForPreparation(any())).thenReturn(Mono.just(List.of()));assertEquals(409,assertThrows(ResponseStatusException.class,f::run).getStatusCode().value());verify(f.preparations,never()).beginPreparation(any(),anyString());assertTrue(f.writes.isEmpty());}
}
