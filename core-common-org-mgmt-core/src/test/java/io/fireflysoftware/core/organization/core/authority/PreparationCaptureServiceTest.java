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

import io.fireflysoftware.core.organization.core.services.authority.impl.BankEnrollmentPreparationCaptureServiceImpl;
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

class PreparationCaptureServiceTest {
    static class Fixture extends ProposalTerminationServiceTest.Fixture {
        final OrganizationPreparationAuthorization preparation=new OrganizationPreparationAuthorization(()->Mono.justOrEmpty(current.caller.get()),(p,a,r,c)->{current.calls.incrementAndGet();current.action=a;current.resource=r;current.context=c;current.duringPolicy.run();return Mono.justOrEmpty(current.policy.get());},current.clock,PLATFORM,Map.of(IDP,new OrganizationActorIssuerProfile(IDP,"tenant_id","org_kind","org_staff","org_client","employee","service")),Map.of(IDP,REALM));
        final NativeEnrollmentPreparationContextRepository preparations=mock(NativeEnrollmentPreparationContextRepository.class);
        final BankRepository banks=mock(BankRepository.class);final NativeBankGateRepository gates=mock(NativeBankGateRepository.class);
        final NativeBankEnrollmentControlRepository ownerControls=mock(NativeBankEnrollmentControlRepository.class);final NativeBankEnrollmentOwnerIdentityRepository versions=mock(NativeBankEnrollmentOwnerIdentityRepository.class);
        Fixture(){doAnswer(i->{var p=packet.get();long now=current.now.get();return Mono.just(new EnrollmentNativeContextStamp(i.getArgument(0),now,Math.min(now+30000,p.get("target").get("authorityUntilEpochMillis").longValue()),Math.min(now+30000,p.get("target").get("commandUntilEpochMillis").longValue()),p.get("contextDigest").textValue()));}).when(contexts).lockContext(any());when(preparations.beginCapture(any(),anyString())).thenAnswer(i->{packet.set(JSON.readTree((String)i.getArgument(1)));return Mono.just(i.getArgument(0));});when(banks.findById(any(UUID.class))).thenReturn(Mono.just(Bank.builder().id(UUID.fromString(BANK)).isActive(true).build()));when(gates.findById(any())).thenReturn(Mono.just(new BankGateRow(UUID.fromString(BANK),7,0,9,null,null,null,null,null,null,null)));}
        OrganizationPreparationAuthorization.Discovery discovery(){return discovery(BankEnrollmentPreparationRequest.Mode.ENROLL);}
        OrganizationPreparationAuthorization.Discovery discovery(BankEnrollmentPreparationRequest.Mode mode){return preparation.discover(TENANT,BANK,mode,bytes(BankEnrollmentPreparationCodecTest.desired().put("mode",mode.name()))).block();}
        BankEnrollmentVersionKey currentOwner(String state){
            UUID priorTenant=UUID.fromString("00000000-0000-0000-0000-000000000097"),action=UUID.randomUUID();var key=new BankEnrollmentVersionKey(priorTenant,UUID.fromString(ENROLLMENT),1L);
            when(gates.findById(any())).thenReturn(Mono.just(new BankGateRow(UUID.fromString(BANK),8,2,9,priorTenant,UUID.fromString(INSTITUTION),UUID.fromString(ISSUER),key.enrollmentId(),1L,action,priorTenant)));
            when(versions.findById(any())).thenReturn(Mono.just(new BankEnrollmentOwnerIdentityRow(key,UUID.fromString(BANK),UUID.fromString(INSTITUTION),UUID.fromString(ISSUER),2)));
            when(ownerControls.findById(any())).thenReturn(Mono.just(new BankEnrollmentControlRow(key,state,5,action,action,priorTenant,priorTenant)));return key;
        }
        BankEnrollmentPreparationCaptureServiceImpl capture(){return new BankEnrollmentPreparationCaptureServiceImpl(preparation,preparations,contexts,banks,gates,versions,ownerControls,operations,manager);}
    }
    @Test void nativeScopeAndMappedReadsCloseBeforeCurrentCallerPromotion(){var f=new Fixture();var d=f.discovery();var read=new java.util.concurrent.atomic.AtomicBoolean();when(f.gates.findById(any())).thenAnswer(i->{read.set(true);return Mono.just(new BankGateRow(UUID.fromString(BANK),7,0,9,null,null,null,null,null,null,null));});f.current.duringPolicy=()->{if(read.get())verify(f.connection).commitTransaction();};var result=f.capture().capture(d).block();assertNotNull(result);assertEquals(7,result.local().bankGeneration());assertEquals(9,result.local().sourceGeneration());assertEquals(0,result.local().ownershipGeneration());assertNull(result.local().previousOwner());assertEquals("PREPARE_CAPTURED",f.current.context.get("stage"));assertEquals(PLATFORM,f.current.context.get("actorTenantId"));assertEquals(TENANT,f.current.context.get("resourceTenantId"));var ordered=inOrder(f.preparations,f.contexts,f.banks,f.gates);ordered.verify(f.preparations).beginCapture(any(),anyString());ordered.verify(f.contexts).lockContext(any());ordered.verify(f.banks).findById(UUID.fromString(BANK));ordered.verify(f.gates).findById(UUID.fromString(BANK));verify(f.ownerControls,never()).findById(any());verify(f.connection).commitTransaction();}
    @Test void revokedOrForeignDiscoveryCannotOpenNativeOrMappedReads(){var f=new Fixture();var d=f.discovery();f.current.policy.set(org.fireflyframework.security.api.domain.Decision.deny("withdrawn"));denied(()->f.capture().capture(d).block());verify(f.preparations,never()).beginCapture(any(),anyString());verify(f.banks,never()).findById(any(UUID.class));var other=new Fixture();denied(()->other.capture().capture(d).block());verify(other.preparations,never()).beginCapture(any(),anyString());}
    @Test void absentInactiveAndPreviouslyOwnedTombstoneDoNotBecomeVirginAdmissions(){var f=new Fixture();var d=f.discovery();when(f.banks.findById(any(UUID.class))).thenReturn(Mono.empty());assertEquals(404,assertThrows(ResponseStatusException.class,()->f.capture().capture(d).block()).getStatusCode().value());verify(f.connection).rollbackTransaction();var g=new Fixture();var e=g.discovery();when(g.banks.findById(any(UUID.class))).thenReturn(Mono.just(Bank.builder().id(UUID.fromString(BANK)).isActive(false).build()));assertEquals(409,assertThrows(ResponseStatusException.class,()->g.capture().capture(e).block()).getStatusCode().value());var h=new Fixture();var v=h.discovery();when(h.gates.findById(any())).thenReturn(Mono.just(new BankGateRow(UUID.fromString(BANK),8,2,9,null,null,null,null,null,UUID.randomUUID(),UUID.fromString(TENANT))));assertThrows(ResponseStatusException.class,()->h.capture().capture(v).block());}
    @Test void contextMismatchOrCurrentContextLossRollsBackBeforeCaptureAdmission(){var f=new Fixture();var d=f.discovery();doReturn(Mono.just(new EnrollmentNativeContextStamp(UUID.randomUUID(),RECEIVED,RECEIVED+20000,RECEIVED+20000,"a".repeat(64)))).when(f.contexts).lockContext(any());assertThrows(ResponseStatusException.class,()->f.capture().capture(d).block());verify(f.banks,never()).findById(any(UUID.class));verify(f.connection).rollbackTransaction();var g=new Fixture();var e=g.discovery();when(g.contexts.contextCurrent(any())).thenReturn(Mono.just(false));assertThrows(ResponseStatusException.class,()->g.capture().capture(e).block());verify(g.connection).rollbackTransaction();}
    @Test void independentlyCurrentWithdrawalAfterIoDoesNotReturnCapturedAuthority(){var f=new Fixture();var d=f.discovery();when(f.gates.findById(any())).thenAnswer(i->{f.current.policy.set(org.fireflyframework.security.api.domain.Decision.deny("withdrawn after read"));return Mono.just(new BankGateRow(UUID.fromString(BANK),7,0,9,null,null,null,null,null,null,null));});assertEquals(403,assertThrows(ResponseStatusException.class,()->f.capture().capture(d).block()).getStatusCode().value());verify(f.connection).commitTransaction();assertTrue(f.writes.isEmpty());}
    @Test void captureRequiresTheExactRepositoryTemplateTransactionFactory(){var f=new Fixture();var other=mock(ConnectionFactory.class);when(other.getMetadata()).thenReturn(()->"PostgreSQL");assertThrows(IllegalStateException.class,()->new BankEnrollmentPreparationCaptureServiceImpl(f.preparation,f.preparations,f.contexts,f.banks,f.gates,f.versions,f.ownerControls,f.operations,new R2dbcTransactionManager(other)));}
    @Test void transferCapturesFullCurrentOwnerUsingItsOwnTenantKey(){for(var state:List.of("ACTIVE","SUSPENDED")){var f=new Fixture();var key=f.currentOwner(state);var d=f.discovery(BankEnrollmentPreparationRequest.Mode.TRANSFER);var result=f.capture().capture(d).block();assertNotNull(result);var owner=result.local().previousOwner();assertEquals(key.tenantId().toString(),owner.tenantId());assertEquals(key.enrollmentId().toString(),owner.enrollmentId());assertEquals(1,owner.version());assertEquals(5,owner.controlVersion());assertEquals(2,result.local().ownershipGeneration());verify(f.versions).findById(key);verify(f.ownerControls).findById(key);assertEquals("organization.bank.enrollment.transfer.prepare",f.current.action);assertEquals(TENANT,f.current.context.get("resourceTenantId"));assertEquals(PLATFORM,f.current.context.get("actorTenantId"));assertNotEquals(TENANT,owner.tenantId());assertFalse(f.current.context.containsKey("crossTenant"));}}
    @Test void absentOrTerminalCurrentOwnerControlCannotBeCapturedForTransfer(){for(var state:List.of("REVOKED","SUPERSEDED")){var f=new Fixture();f.currentOwner(state);var d=f.discovery(BankEnrollmentPreparationRequest.Mode.TRANSFER);assertEquals(409,assertThrows(ResponseStatusException.class,()->f.capture().capture(d).block()).getStatusCode().value());verify(f.connection).rollbackTransaction();}var f=new Fixture();f.currentOwner("ACTIVE");doReturn(Mono.empty()).when(f.ownerControls).findById(any());var d=f.discovery(BankEnrollmentPreparationRequest.Mode.TRANSFER);assertThrows(ResponseStatusException.class,()->f.capture().capture(d).block());verify(f.connection).rollbackTransaction();}
    @Test void captureWireBindsVerifiedActorDesiredScopeAndFixedHorizonWithoutSecrets(){var f=new Fixture();var d=f.discovery();var nonce=UUID.randomUUID();var packet=PreparationCaptureEvidenceCodec.context(d,nonce);var target=packet.target();assertEquals("PREPARATION_DISCOVERY",target.purpose());assertEquals(PLATFORM,target.actor().tenantId());assertEquals(TENANT,target.tenantId());assertEquals(REALM,target.actor().staffRealm());assertEquals(d.desired().canonicalIntent(),target.canonicalDesiredRequest());assertEquals(d.desired().intentDigest(),target.desiredRequestDigest());assertEquals(d.validUntilEpochMillis(),target.authorityUntilEpochMillis());assertEquals(AuthorityWire.sha(packet.canonicalContext()),packet.contextDigest());assertNotEquals(packet.contextDigest(),PreparationCaptureEvidenceCodec.context(d,UUID.randomUUID()).contextDigest());assertFalse(ProposalTerminationEvidenceCodec.json(packet).contains("Bearer"));}
    @Test void nativeStampCannotExtendTheOriginalDiscoveryOrDatabaseWindow(){var f=new Fixture();var d=f.discovery();doAnswer(i->{var p=f.packet.get();return Mono.just(new EnrollmentNativeContextStamp(i.getArgument(0),RECEIVED,d.validUntilEpochMillis()+1,RECEIVED+30000,p.get("contextDigest").textValue()));}).when(f.contexts).lockContext(any());assertThrows(ResponseStatusException.class,()->f.capture().capture(d).block());verify(f.banks,never()).findById(any(UUID.class));verify(f.connection).rollbackTransaction();}

}
