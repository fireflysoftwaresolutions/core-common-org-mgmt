/*
 * Copyright 2026 Firefly Software Foundation
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.fireflysoftware.core.organization.core.authority;

import io.fireflysoftware.core.organization.interfaces.authority.*;
import io.fireflysoftware.core.organization.models.entities.Bank;
import io.fireflysoftware.core.organization.models.entities.authority.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static io.fireflysoftware.core.organization.core.authority.ConfigurationGrantObservationVerifierTest.*;
import static io.fireflysoftware.core.organization.core.authority.OrganizationEnrollmentAuthorizationTest.*;
import static io.fireflysoftware.core.organization.core.authority.OrganizationProposalAuthorizationTest.*;
import static io.fireflysoftware.core.organization.core.authority.OwnedBankEnrollmentFixture.*;

@EnabledIfEnvironmentVariable(named="ORG_BANK_NATIVE_STAGE",matches="seed")
class BankEnrollmentNativeSeedIT {
    @Test void seedOnlyHistoricalFixtureDraftsBeforeNativeGuardsWithoutAnyApproval()throws Exception {
        try(var f=new OwnedBankEnrollmentFixture()){
            long now=System.currentTimeMillis();var original=ConfigurationGrantObservationVerifierTest.verify(observation()).observation().definition();var g=original.target().grant();
            var grant=new ConfigurationGrantObservation.Grant(g.actionId(),g.proposalId(),1,g.grantId(),INSTITUTION,ISSUER,1,0,List.of(FinancialCalendarUse.ACCOUNTING_BOOK,FinancialCalendarUse.PRODUCT_ISSUANCE),now-3600000,now+3600000,"Owned protocol fixture; no upstream grant acceptance",List.of("fixture://owned-bank-native"));
            var target=new ConfigurationGrantObservation.Target(grant,original.target().eligibilityEpoch());String grantCanonical=AuthorityWire.token("FF_TENANT_INSTITUTION_GRANT_V2")+AuthorityWire.recordToken(TENANT,"GRANT_ISSUE",target);
            String definitionCanonical=AuthorityWire.token("FF_TENANT_INSTITUTION_GRANT_DEFINITION_V2")+AuthorityWire.recordToken(TENANT,grantCanonical,now-120000);
            var definition=new ConfigurationGrantObservation.Definition(TENANT,target,"FF_TENANT_INSTITUTION_GRANT_DEFINITION_V2",definitionCanonical,AuthorityWire.sha(definitionCanonical),now-120000);ConfigurationGrantObservationVerifier.definition(TENANT,INSTITUTION,ISSUER,definition);
            MAPPER.writeValue(f.run.resolve("definition.json").toFile(),definition);
            var operations=f.adminOperations();var tx=TransactionalOperator.create(new R2dbcTransactionManager(operations.getDatabaseClient().getConnectionFactory()));
            for(int index=0;index<12;index++){
                UUID bank=id(1000+index),proposal=id(2000+index),prepare=id(3000+index),enrollment=id(4000+index),execution=id(5000+index),receipt=id(6000+index),audit=id(7000+index),event=id(8000+index);
                long recorded=now-60000,deadline=index==7?now-1000:now+300000;
                var request=new BankEnrollmentTarget(2,execution.toString(),BankEnrollmentTarget.Mode.ENROLL,bank.toString(),enrollment.toString(),TENANT,INSTITUTION,ISSUER,FinancialCalendarUse.ACCOUNTING_BOOK,0,0,null,null,null,definition,1,0,deadline);
                var encoded=BankEnrollmentIntentCodec.decode(MAPPER.writeValueAsBytes(request));var revision=new BankEnrollmentRevision(2,proposal.toString(),1,0,null,encoded.originalJson(),encoded.canonicalIntent(),encoded.intentDigest(),"Owned setup DRAFT only",List.of(FinancialCalendarUse.ACCOUNTING_BOOK,FinancialCalendarUse.PRODUCT_ISSUANCE),List.of("fixture://owned-setup-no-approval"));
                var full=BankEnrollmentRevisionCodec.decode(MAPPER.writeValueAsBytes(revision));var key=new EnrollmentActionKey(UUID.fromString(TENANT),prepare);var proposalKey=new EnrollmentProposalKey(UUID.fromString(TENANT),proposal);
                String raw=MAPPER.createObjectNode().put("fixtureOnly",true).put("state","DRAFT").put("noSeededApproval",true).toString();byte[] bytes=raw.getBytes(StandardCharsets.UTF_8);String canonical=AuthorityWire.token("FF_OWNED_FIXTURE_DRAFT_V0")+AuthorityWire.recordToken(index,bank.toString(),proposal.toString());String digest=AuthorityWire.sha(canonical);
                var bankRow=Bank.builder().id(bank).code("native-bank-"+index).name("Owned native fixture "+index).isActive(true).createdBy(UUID.fromString(MAKER)).createdAt(LocalDateTime.ofInstant(Instant.ofEpochMilli(recorded),ZoneOffset.UTC)).build();
                var action=new EnrollmentActionRow(key,bank,"FIXTURE_DRAFT_SETUP","OWNED FIXTURE ONLY",bytes,"FF_OWNED_FIXTURE_DRAFT_V0",canonical,digest,"COMMAND",bytes,"FF_OWNED_FIXTURE_DRAFT_V0",canonical,digest,"STAFF",IDP,"retained-maker",UUID.fromString(PLATFORM),REALM,UUID.fromString(MAKER),"historical-maker-console",proposal,1L,full.intentDigest(),"APPLIED",recorded);
                var header=new EnrollmentProposalRow(proposalKey,bank,UUID.fromString(INSTITUTION),UUID.fromString(ISSUER),"ENROLL",enrollment,"STAFF",IDP,"retained-maker",UUID.fromString(PLATFORM),REALM,UUID.fromString(MAKER),"historical-maker-console",prepare,recorded);
                var row=new EnrollmentProposalRevisionRow(new EnrollmentProposalRevisionKey(UUID.fromString(TENANT),proposal,1L),"ENROLL",execution,0,0,0,null,deadline,revision.allowedUses().stream().map(Enum::name).toList(),full.originalJson().getBytes(StandardCharsets.UTF_8),ProposalTerminationEvidenceCodec.REVISION_ENCODING,full.canonicalIntent(),full.intentDigest(),recorded);
                Mono.defer(()->operations.insert(bankRow)).then(Mono.defer(()->operations.insert(new BankGateRow(bank,0,0,0,null,null,null,null,null,null,null))))
                        .then(Mono.defer(()->operations.insert(action))).then(Mono.defer(()->operations.insert(header))).then(Mono.defer(()->operations.insert(row)))
                        .then(Mono.defer(()->operations.insert(new EnrollmentProposalControlRow(proposalKey,"DRAFT",1,0,null,null,prepare))))
                        .then(Mono.defer(()->operations.insert(new EnrollmentReceiptRow(key,receipt,bytes,"FF_OWNED_FIXTURE_DRAFT_V0",canonical,digest,recorded))))
                        .then(Mono.defer(()->operations.insert(new EnrollmentAuditRow(key,audit,bank,bytes,"FF_OWNED_FIXTURE_DRAFT_V0",canonical,digest,recorded))))
                        .then(Mono.defer(()->operations.insert(new EnrollmentOutboxRow(key,event,bank,bytes,"FF_OWNED_FIXTURE_DRAFT_V0",canonical,digest,recorded))))
                        .then(Mono.defer(()->operations.insert(new EnrollmentOutboxDeliveryRow(new EnrollmentOutboxEventKey(UUID.fromString(TENANT),event),0,"PENDING",0,null,null,recorded,null,null))))
                        .as(tx::transactional).block(WAIT);
                MAPPER.writeValue(f.run.resolve("case-"+index+".json").toFile(),new Case(index,bank,proposal,full.originalJson(),recorded));
            }
            try(var connection=f.admin();var statement=connection.createStatement();var result=statement.executeQuery("SELECT count(*) FROM public.org_native_decision")){assertTrue(result.next());assertEquals(0,result.getInt(1));}
        }
    }
}
