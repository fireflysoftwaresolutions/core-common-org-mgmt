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

@EnabledIfEnvironmentVariable(named="ORG_BANK_NATIVE_STAGE",matches="metadata")
class BankPreparationMetadataSeedIT {
    @Test void seedOnlyOrdinaryBanksAndControlledUpstreamProtocolDefinition()throws Exception {
        try(var f=new OwnedBankEnrollmentFixture()){
            long now=System.currentTimeMillis();var original=ConfigurationGrantObservationVerifierTest.verify(observation()).observation().definition();var g=original.target().grant();
            var grant=new ConfigurationGrantObservation.Grant(g.actionId(),g.proposalId(),1,g.grantId(),INSTITUTION,ISSUER,1,0,List.of(FinancialCalendarUse.ACCOUNTING_BOOK,FinancialCalendarUse.PRODUCT_ISSUANCE),now-3600000,now+3600000,"Owned protocol fixture; no upstream grant acceptance",List.of("fixture://owned-bank-native"));
            var target=new ConfigurationGrantObservation.Target(grant,original.target().eligibilityEpoch());String grantCanonical=AuthorityWire.token("FF_TENANT_INSTITUTION_GRANT_V2")+AuthorityWire.recordToken(TENANT,"GRANT_ISSUE",target);
            String definitionCanonical=AuthorityWire.token("FF_TENANT_INSTITUTION_GRANT_DEFINITION_V2")+AuthorityWire.recordToken(TENANT,grantCanonical,now-120000);
            var definition=new ConfigurationGrantObservation.Definition(TENANT,target,"FF_TENANT_INSTITUTION_GRANT_DEFINITION_V2",definitionCanonical,AuthorityWire.sha(definitionCanonical),now-120000);ConfigurationGrantObservationVerifier.definition(TENANT,INSTITUTION,ISSUER,definition);
            MAPPER.writeValue(f.run.resolve("definition.json").toFile(),definition);
            var operations=f.adminOperations();for(int number:OwnedBankPreparationFixture.BANK_NUMBERS){var bank=Bank.builder().id(id(number)).code("prepare-bank-"+number).name("Owned preparation fixture "+number).isActive(true).createdBy(UUID.fromString(MAKER)).createdAt(LocalDateTime.now(ZoneOffset.UTC)).build();operations.insert(bank).block(WAIT);}
            try(var connection=f.admin();var statement=connection.createStatement();var rows=statement.executeQuery("SELECT count(*) FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='public' AND c.relkind='r' AND c.relname <> 'flyway_schema_history'")){assertTrue(rows.next());assertEquals(12,rows.getInt(1));}
        }
    }
}
