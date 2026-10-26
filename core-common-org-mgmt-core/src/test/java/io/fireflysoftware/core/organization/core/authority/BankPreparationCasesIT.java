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

@EnabledIfEnvironmentVariable(named="ORG_BANK_NATIVE_STAGE",matches="prepare-cases")
class BankPreparationCasesIT {
    @Test void createAllCompatibilityDraftsThroughActualPreparationServiceWithoutSeedingAnyFinancialRow()throws Exception {
        try(var f=new OwnedBankPreparationFixture()){
            f.staff(true,"native-prepare-maker");for(int index=0;index<12;index++){UUID bank=id(1000+index);var result=f.prepare(bank,UUID.randomUUID(),index==7?10000:300000);
                assertEquals("DRAFT",result.outcome().state());assertEquals(0,result.outcome().controlVersion());var proposal=UUID.fromString(result.outcome().proposalId());
                MAPPER.writeValue(f.base.run.resolve("case-"+index+".json").toFile(),new Case(index,bank,proposal,result.revision().originalJson(),result.outcome().recordedAtEpochMillis()));
            }
            try(var connection=f.base.admin();var statement=connection.createStatement();var row=statement.executeQuery("SELECT (SELECT count(*) FROM public.org_native_proposal),(SELECT count(*) FROM public.org_native_decision),(SELECT count(*) FROM public.org_native_bank_enrollment_version),(SELECT count(*) FROM public.org_native_proposal_control WHERE state='DRAFT' AND current_revision=1 AND control_version=0 AND approval_action_id IS NULL AND approval_until_ms IS NULL),(SELECT count(*) FROM public.org_native_action WHERE intended_operation='PREPARE')")){assertTrue(row.next());assertEquals(12,row.getLong(1));assertEquals(0,row.getLong(2));assertEquals(0,row.getLong(3));assertEquals(12,row.getLong(4));assertEquals(12,row.getLong(5));}
        }
    }
}
