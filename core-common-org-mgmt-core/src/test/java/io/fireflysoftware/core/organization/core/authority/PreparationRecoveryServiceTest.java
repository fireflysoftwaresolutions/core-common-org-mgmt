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

import io.fireflysoftware.core.organization.core.services.authority.impl.BankEnrollmentPreparationRecoveryServiceImpl;
import io.fireflysoftware.core.organization.interfaces.authority.*;
import io.fireflysoftware.core.organization.core.services.authority.impl.BankEnrollmentActionReadServiceImpl;
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

class PreparationRecoveryServiceTest {
    static class Fixture extends PreparationCommandServiceTest.Fixture {
        final NativeEnrollmentReceiptRepository receipts=mock(NativeEnrollmentReceiptRepository.class);EnrollmentPreparedProposal original;
        Fixture()throws Exception {original=writer().prepare(prepared()).block();var action=writes.stream().filter(EnrollmentActionRow.class::isInstance).map(EnrollmentActionRow.class::cast).findFirst().orElseThrow();var receipt=writes.stream().filter(EnrollmentReceiptRow.class::isInstance).map(EnrollmentReceiptRow.class::cast).findFirst().orElseThrow();var revision=writes.stream().filter(EnrollmentProposalRevisionRow.class::isInstance).map(EnrollmentProposalRevisionRow.class::cast).findFirst().orElseThrow();doReturn(Mono.just(action)).when(actions).findById(any());when(receipts.findById(any())).thenReturn(Mono.just(receipt));doReturn(Mono.just(revision)).when(revisions).findById(any());when(contexts.beginRead(any(),anyString())).thenAnswer(i->{packet.set(JSON.readTree((String)i.getArgument(1)));return Mono.just(i.getArgument(0));});}
        BankEnrollmentPreparationRecoveryServiceImpl recovery(){var history=new OrganizationEnrollmentHistoryAuthorization(()->Mono.justOrEmpty(current.caller.get()),(p,a,r,c)->{current.calls.incrementAndGet();current.action=a;current.context=c;current.duringPolicy.run();boolean exact=a.equals(OrganizationEnrollmentHistoryAuthorization.PERMISSION)&&r.equals("tenant/"+TENANT+"/BANK_ENROLLMENT/"+BANK+"/ACTION/"+ACTION);return Mono.just(exact?current.policy.get():org.fireflyframework.security.api.domain.Decision.deny("not independent exact read"));},current.clock,PLATFORM,Map.of(IDP,new OrganizationActorIssuerProfile(IDP,"tenant_id","org_kind","org_staff","org_client","employee","service")),Map.of(IDP,REALM));return new BankEnrollmentPreparationRecoveryServiceImpl(new BankEnrollmentActionReadServiceImpl(history,contexts,actions,receipts,revisions,operations,manager));}
        EnrollmentPreparedProposal recover(byte[] body){return recovery().recover(UUID.fromString(TENANT),UUID.fromString(BANK),UUID.fromString(ACTION),body).block();}
    }
    @Test void sameDesiredIntentReturnsRetainedAllocationsAndFullRevisionThroughIndependentRead()throws Exception {var f=new Fixture();var result=f.recover(bytes(BankEnrollmentPreparationCodecTest.desired()));assertEquals(f.original,result);assertEquals(OrganizationEnrollmentHistoryAuthorization.PERMISSION,f.current.action);verify(f.contexts).beginRead(any(),anyString());verify(f.preparations,times(1)).beginPreparation(any(),anyString());assertEquals(8,f.writes.size());var formatted=JSON.writerWithDefaultPrettyPrinter().writeValueAsBytes(BankEnrollmentPreparationCodecTest.desired());assertEquals(f.original,f.recover(formatted));assertEquals(8,f.writes.size());}
    @Test void changedDesiredIntentCannotRecoverAsTheOriginalPreparation()throws Exception {var f=new Fixture();for(String field:List.of("reason","actionId","institutionId")){var changed=BankEnrollmentPreparationCodecTest.desired().put(field,field.equals("reason")?"changed reason":PLATFORM);assertEquals(409,assertThrows(ResponseStatusException.class,()->f.recover(bytes(changed))).getStatusCode().value());}assertEquals(8,f.writes.size());verify(f.preparations,times(1)).beginPreparation(any(),anyString());}
    @Test void expiredDesiredAndUnavailableLiveBankNeedOnlyFreshCurrentReadAuthority()throws Exception {var f=new Fixture();f.current.now.set(RECEIVED+86400000);f.current.caller.set(principal(PLATFORM,Map.of("exp",java.time.Instant.ofEpochMilli(f.current.now.get()+60000))));when(f.banks.findById(any(UUID.class))).thenReturn(Mono.error(new IllegalStateException("live metadata unavailable")));when(f.gates.findById(any())).thenReturn(Mono.error(new IllegalStateException("live gate unavailable")));assertEquals(f.original,f.recover(bytes(BankEnrollmentPreparationCodecTest.desired())));assertTrue(f.original.outcome().recordedAtEpochMillis()<f.current.now.get());verify(f.banks,never()).findById(any(UUID.class));verify(f.gates,never()).findById(any());verify(f.preparations,times(1)).beginPreparation(any(),anyString());assertEquals(8,f.writes.size());}
    @Test void preparationPermissionCannotSubstituteForCurrentIndependentRead()throws Exception {var f=new Fixture();f.current.policy.set(org.fireflyframework.security.api.domain.Decision.deny("read withdrawn"));assertEquals(403,assertThrows(ResponseStatusException.class,()->f.recover(bytes(BankEnrollmentPreparationCodecTest.desired()))).getStatusCode().value());verify(f.contexts,never()).beginRead(any(),anyString());assertEquals(8,f.writes.size());}
    @Test void actionAbsenceIsOnlyNotFoundAndCannotProduceAnEffectOrAbsenceReceipt()throws Exception {var f=new Fixture();doReturn(Mono.empty()).when(f.actions).findById(any());assertEquals(404,assertThrows(ResponseStatusException.class,()->f.recover(bytes(BankEnrollmentPreparationCodecTest.desired()))).getStatusCode().value());assertEquals(8,f.writes.size());verify(f.preparations,times(1)).beginPreparation(any(),anyString());}
    @Test void currentReadWithdrawalDuringRetainedIoPreventsReturningRecoveredAllocations()throws Exception {var f=new Fixture();var receipt=f.writes.stream().filter(EnrollmentReceiptRow.class::isInstance).map(EnrollmentReceiptRow.class::cast).findFirst().orElseThrow();when(f.receipts.findById(any())).thenAnswer(i->{f.current.policy.set(org.fireflyframework.security.api.domain.Decision.deny("read withdrawn during IO"));return Mono.just(receipt);});assertEquals(403,assertThrows(ResponseStatusException.class,()->f.recover(bytes(BankEnrollmentPreparationCodecTest.desired()))).getStatusCode().value());verify(f.connection,times(2)).commitTransaction();verify(f.preparations,times(1)).beginPreparation(any(),anyString());assertEquals(8,f.writes.size());}

}
