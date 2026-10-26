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
import io.fireflysoftware.core.organization.models.repositories.authority.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.data.relational.core.query.Query;
import org.springframework.data.relational.core.query.Update;
import org.springframework.transaction.reactive.TransactionalOperator;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static io.fireflysoftware.core.organization.core.authority.ConfigurationGrantObservationVerifierTest.*;
import static io.fireflysoftware.core.organization.core.authority.OrganizationEnrollmentAuthorizationTest.*;
import static io.fireflysoftware.core.organization.core.authority.OrganizationProposalAuthorizationTest.*;
import static io.fireflysoftware.core.organization.core.authority.OwnedBankEnrollmentFixture.*;

@EnabledIfEnvironmentVariable(named="ORG_BANK_NATIVE_LEASE",matches="approved")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class BankEnrollmentNativeIT {
    OwnedBankEnrollmentFixture f;
    @BeforeAll void openOwnedExactFactory()throws Exception {f=new OwnedBankEnrollmentFixture();}
    @AfterAll void close(){if(f!=null)f.close();}
    @BeforeEach void currentPolicy(){f.policyCurrent.set(true);f.duringObservation=()->{};}
    String state(Case c)throws Exception {try(var connection=f.admin();var statement=connection.prepareStatement("SELECT state FROM public.org_native_proposal_control WHERE tenant_id=? AND proposal_id=?")){statement.setObject(1,UUID.fromString(TENANT));statement.setObject(2,c.proposal());try(var row=statement.executeQuery()){assertTrue(row.next());return row.getString(1);}}}
    long source(UUID bank)throws Exception {try(var connection=f.admin();var statement=connection.prepareStatement("SELECT source_generation FROM public.org_native_bank_gate WHERE bank_id=?")){statement.setObject(1,bank);try(var row=statement.executeQuery()){assertTrue(row.next());return row.getLong(1);}}}
    void deniedSql(String sql)throws Exception {try(var connection=f.runtime();var statement=connection.createStatement()){var error=assertThrows(SQLException.class,()->statement.execute(sql));assertEquals("42501",error.getSQLState());}}
    @Test @Order(1) void firstProtectedMappedCommandCanReachNamedNativeContext()throws Exception {
        f.staff(true,"current-maker-console");
        if("baseline-red".equals(System.getenv("ORG_BANK_NATIVE_STAGE"))){
            var body=MAPPER.createObjectNode().put("schemaVersion",2).put("actionId",UUID.randomUUID().toString()).put("operation","SUBMIT").put("expectedRevision",1).put("expectedControlVersion",0).put("expectedBankGeneration",0).put("expectedTargetDigest","a".repeat(64)).put("commandNotAfterEpochMillis",System.currentTimeMillis()+6000).putNull("approvalUntilEpochMillis").put("reason","Original-schema missing native function probe");
            try{assertNotNull(f.application.command(EnrollmentProposalCommand.Operation.SUBMIT,UUID.fromString(TENANT),id(1000),id(2000),1,MAPPER.writeValueAsBytes(body)).block(WAIT));}
            catch(RuntimeException failure){MAPPER.writeValue(f.run.resolve("schema-red-observation.json").toFile(),Map.of("actualMissingFunctionSqlState42883",hasSqlState(failure,"42883"),"namedFunctionObserved",hasMessage(failure,"org_native_enrollment_begin_proposal_read_context"),"scope","Actual protected application with original V1-V4 only; no native effect"));throw failure;}
        }else {var result=f.command(f.caseAt(0),EnrollmentProposalCommand.Operation.SUBMIT);assertEquals("SUBMITTED",result.state());assertEquals(1,result.controlVersion());}
    }
    @Test @Order(2) void literalJavaVectorHasExactPostgresCanonicalAndDigest()throws Exception {
        try(var in=getClass().getResourceAsStream("/authority/bank-enrollment-revision-v2-golden.json")){var vector=MAPPER.readTree(in);
            try(var connection=f.admin();var statement=connection.prepareStatement("SELECT public.org_native_revision(?::json)")){statement.setString(1,vector.get("original").textValue());try(var row=statement.executeQuery()){assertTrue(row.next());assertEquals(vector.get("canonical").textValue(),row.getString(1));assertEquals(vector.get("digest").textValue(),AuthorityWire.sha(row.getString(1)));}}
        }
    }
    @Test @Order(3) void actualMappedApprovalRetainsDistinctCheckerAndProtectedReceipt()throws Exception {
        var c=f.caseAt(1);f.staff(true,"maker-console");f.command(c,EnrollmentProposalCommand.Operation.SUBMIT);f.staff(false,"checker-console");var result=f.command(c,EnrollmentProposalCommand.Operation.APPROVE);assertEquals("APPROVED",result.state());assertEquals(2,result.controlVersion());
        var history=f.reader.read(UUID.fromString(TENANT),c.bank(),UUID.fromString(result.actionId())).block(WAIT);assertEquals("APPLIED",history.outcome());assertEquals("APPROVE",history.intendedOperation());assertEquals(STAFF,history.resolvedBy().staffActorId());assertEquals("checker-console",history.resolvedBy().clientId());assertEquals(result.receiptId(),history.receipt().originalJson().contains(result.receiptId())?result.receiptId():null);
        try(var connection=f.admin();var statement=connection.prepareStatement("SELECT decision_actor_staff_id,approval_until_ms FROM public.org_native_decision WHERE tenant_id=? AND action_id=?")){statement.setObject(1,UUID.fromString(TENANT));statement.setObject(2,UUID.fromString(result.actionId()));try(var row=statement.executeQuery()){assertTrue(row.next());assertEquals(UUID.fromString(STAFF),row.getObject(1));assertTrue(row.getLong(2)>result.recordedAtEpochMillis());}}
    }
    @Test @Order(4) void sameHumanChangingClientsCannotBecomeAnIndependentChecker()throws Exception {
        var c=f.caseAt(2);f.staff(true,"maker-console");f.command(c,EnrollmentProposalCommand.Operation.SUBMIT);f.staff(true,"different-console-same-human");assertEquals(403,assertThrows(ResponseStatusException.class,()->f.command(c,EnrollmentProposalCommand.Operation.APPROVE)).getStatusCode().value());assertEquals("SUBMITTED",state(c));f.staff(false,"real-checker-console");assertEquals("APPROVED",f.command(c,EnrollmentProposalCommand.Operation.APPROVE).state());
    }
    @Test @Order(5) void actualRejectWithdrawAndCancellationHaveTheirExactDecisionShape()throws Exception {
        var rejected=f.caseAt(3);f.staff(true,"maker");f.command(rejected,EnrollmentProposalCommand.Operation.SUBMIT);f.staff(false,"checker");assertEquals("REJECTED",f.command(rejected,EnrollmentProposalCommand.Operation.REJECT).state());f.staff(true,"maker");assertEquals("CANCELLED",f.command(rejected,EnrollmentProposalCommand.Operation.CANCEL).state());
        var withdrawn=f.caseAt(10);f.staff(true,"maker");f.command(withdrawn,EnrollmentProposalCommand.Operation.SUBMIT);f.staff(false,"checker");f.command(withdrawn,EnrollmentProposalCommand.Operation.APPROVE);f.staff(true,"maker-new-console");assertEquals("WITHDRAWN",f.command(withdrawn,EnrollmentProposalCommand.Operation.WITHDRAW).state());assertEquals("CANCELLED",f.command(withdrawn,EnrollmentProposalCommand.Operation.CANCEL).state());
        var cancelled=f.caseAt(11);f.staff(true,"maker");f.command(cancelled,EnrollmentProposalCommand.Operation.SUBMIT);assertEquals("WITHDRAWN",f.command(cancelled,EnrollmentProposalCommand.Operation.WITHDRAW).state());assertEquals("CANCELLED",f.command(cancelled,EnrollmentProposalCommand.Operation.CANCEL).state());
    }
    @Test @Order(6) void bankDisableAndRestorationCannotReviveOldProposalButFreshCancellationWorks()throws Exception {
        var c=f.caseAt(4);f.staff(true,"maker");f.command(c,EnrollmentProposalCommand.Operation.SUBMIT);long before=source(c.bank());
        for(boolean active:List.of(false,true))assertEquals(1L,f.operations.update(Bank.class).matching(Query.query(Criteria.where("id").is(c.bank()))).apply(Update.update("isActive",active)).block(WAIT));
        assertEquals(before+2,source(c.bank()));f.staff(false,"checker");var stale=assertThrows(RuntimeException.class,()->f.command(c,EnrollmentProposalCommand.Operation.APPROVE));assertTrue(hasSqlState(stale,"P0001"));assertTrue(hasMessage(stale,"live bank source changed"));assertEquals("SUBMITTED",state(c));f.staff(true,"maker");assertEquals("CANCELLED",f.command(c,EnrollmentProposalCommand.Operation.CANCEL).state());
    }
    @Test @Order(7) void restrictedActualLoginCannotMutateProtectedHistoryOrSchema()throws Exception {
        try(var connection=f.runtime();var statement=connection.createStatement();var row=statement.executeQuery("SELECT r.rolsuper,r.rolbypassrls,r.rolcreaterole,r.rolcreatedb,r.rolinherit,has_schema_privilege(current_user,'public','CREATE'),has_schema_privilege(current_user,'org_private','USAGE'),(SELECT count(*) FROM pg_auth_members WHERE member=r.oid) FROM pg_roles r WHERE r.rolname=current_user")){assertTrue(row.next());for(int i=1;i<=7;i++)assertFalse(row.getBoolean(i));assertEquals(0,row.getLong(8));}
        for(String sql:List.of("UPDATE public.org_native_audit SET event_digest=event_digest","DELETE FROM public.org_native_audit","TRUNCATE public.org_native_audit","CREATE TABLE public.org_forbidden_fixture(id integer)","SELECT * FROM org_private.enrollment_context","SET ROLE org_native_guard"))deniedSql(sql);
        deniedSql("UPDATE public.org_native_proposal_control SET control_version=control_version+1");
    }
    @Test @Order(8) void wrongTenantAndBankReadDoNotProduceAnOutcomeOrNoEffectClaim()throws Exception {
        var c=f.caseAt(6);f.staff(true,"maker");var result=f.command(c,EnrollmentProposalCommand.Operation.SUBMIT);UUID action=UUID.fromString(result.actionId());
        assertEquals(403,assertThrows(ResponseStatusException.class,()->f.reader.read(id(99999),c.bank(),action).block(WAIT)).getStatusCode().value());
        assertEquals(404,assertThrows(ResponseStatusException.class,()->f.reader.read(UUID.fromString(TENANT),id(1005),action).block(WAIT)).getStatusCode().value());assertEquals("APPLIED",f.reader.read(UUID.fromString(TENANT),c.bank(),action).block(WAIT).outcome());
    }
    @Test @Order(9) void expiredDeletedBankTargetCanBeTerminatedAndReadByActualMachine()throws Exception {
        var c=f.caseAt(7);assertTrue(BankEnrollmentRevisionCodec.enrollmentTarget(c.full().request()).request().commandDeadlineEpochMillis()<System.currentTimeMillis());
        assertEquals(1L,f.operations.delete(Bank.class).matching(Query.query(Criteria.where("id").is(c.bank()))).all().block(WAIT));f.machine();int before=f.sdkCalls.get();var result=f.command(c,EnrollmentProposalCommand.Operation.CANCEL_EMERGENCY);assertEquals("CANCELLED",result.state());assertEquals(before,f.sdkCalls.get());
        var value=f.reader.read(UUID.fromString(TENANT),c.bank(),UUID.fromString(result.actionId())).block(WAIT);assertEquals("MACHINE",value.resolvedBy().kind());assertNull(value.resolvedBy().staffActorId());assertEquals("actual-emergency-client",value.resolvedBy().clientId());
    }
    @Test @Order(10) void currentPolicyWithdrawalDuringSdkIoPreventsNativeMutation()throws Exception {
        var c=f.caseAt(5);f.staff(true,"maker");f.policyCurrent.set(false);assertEquals(403,assertThrows(ResponseStatusException.class,()->f.command(c,EnrollmentProposalCommand.Operation.SUBMIT)).getStatusCode().value());f.policyCurrent.set(true);
        f.duringObservation=()->f.policyCurrent.set(false);assertEquals(403,assertThrows(ResponseStatusException.class,()->f.command(c,EnrollmentProposalCommand.Operation.SUBMIT)).getStatusCode().value());assertEquals("DRAFT",state(c));
    }
    @Test @Order(11) void nativeFinalClockRejectsExpiredCommandAfterActualMutationLockWait()throws Exception {
        var c=f.caseAt(8);f.staff(true,"maker");var held=new AtomicReference<java.sql.Connection>();f.duringObservation=()->{if(held.get()!=null)return;try{var connection=f.admin();connection.setAutoCommit(false);try(var statement=connection.prepareStatement("SELECT bank_id FROM public.org_native_bank_gate WHERE bank_id=? FOR UPDATE")){statement.setObject(1,c.bank());statement.executeQuery().close();}held.set(connection);}catch(Exception error){throw new AssertionError(error);}};
        var executor=Executors.newSingleThreadExecutor();try{
            byte[] body=f.commandBody(c,EnrollmentProposalCommand.Operation.SUBMIT,1000);var future=executor.submit(()->f.application.command(EnrollmentProposalCommand.Operation.SUBMIT,UUID.fromString(TENANT),c.bank(),c.proposal(),1,body).block(WAIT));
            long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);boolean waiting=false;while(System.nanoTime()<until){try(var connection=f.admin();var statement=connection.createStatement();var row=statement.executeQuery("SELECT EXISTS(SELECT 1 FROM pg_stat_activity WHERE usename='org_native_runtime' AND wait_event_type='Lock' AND query LIKE '%org_native_enrollment_lock_context%')")){row.next();waiting=row.getBoolean(1);}if(waiting)break;Thread.sleep(20);}
            assertTrue(waiting);Thread.sleep(1200);held.get().commit();held.get().close();held.set(null);var expired=assertThrows(ExecutionException.class,()->future.get(10,TimeUnit.SECONDS));assertTrue(hasSqlState(expired,"42501"));assertTrue(hasMessage(expired,"expired after wait"));assertEquals("DRAFT",state(c));
        }finally{if(held.get()!=null){held.get().rollback();held.get().close();}executor.shutdownNow();}
    }
    @Test @Order(12) void deferredActualGraphRejectsMappedControlWithoutRequiredEvidenceRows()throws Exception {
        var c=f.caseAt(9);f.staff(true,"maker");var scope=new OrganizationProposalAuthorization.ProposalScope(TENANT,c.bank().toString(),c.proposal().toString(),1,0,0,BankEnrollmentLifecycle.ProposalState.DRAFT,c.full(),new OrganizationProposalAuthorization.Maker(IDP,"retained-maker",PLATFORM,REALM,MAKER));
        var admission=f.authorization.admit(EnrollmentProposalCommand.Operation.SUBMIT,scope,f.commandBody(c,EnrollmentProposalCommand.Operation.SUBMIT,6000)).block(WAIT);var evidence=f.observer.observeForProposal(admission).block(WAIT);UUID nonce=UUID.randomUUID();var packet=ProposalTerminationEvidenceCodec.observedContext(admission,nonce,evidence);
        var contexts=f.context.getBean(NativeEnrollmentContextRepository.class);var controls=f.context.getBean(NativeEnrollmentProposalControlRepository.class);var key=new EnrollmentProposalKey(UUID.fromString(TENANT),c.proposal());
        var partial=contexts.beginMutation(nonce,ProposalTerminationEvidenceCodec.json(packet)).flatMap(contexts::lockContext).flatMap(stamp->{
            var actor=admission.actor();var command=admission.command();var actionId=UUID.fromString(command.request().actionId());byte[] request=command.originalJson().getBytes(StandardCharsets.UTF_8);
            var action=new EnrollmentActionRow(new EnrollmentActionKey(key.tenantId(),actionId),c.bank(),"SUBMIT",ProposalTerminationEvidenceCodec.route(admission),request,ProposalTerminationEvidenceCodec.COMMAND_ENCODING,command.canonicalIntent(),command.intentDigest(),"COMMAND",request,ProposalTerminationEvidenceCodec.COMMAND_ENCODING,command.canonicalIntent(),command.intentDigest(),actor.kind(),actor.issuer(),actor.subject(),UUID.fromString(actor.tenantId()),admission.staffRealm(),UUID.fromString(actor.staffActorId()),actor.clientId(),c.proposal(),1L,c.full().intentDigest(),"APPLIED",stamp.dbNowMillis());
            return controls.findById(key).flatMap(before->controls.compareAndSet(before,new EnrollmentProposalControlRow(key,"SUBMITTED",1,1,null,null,actionId))).then(Mono.defer(()->f.operations.insert(action)));
        });
        var incomplete=assertThrows(RuntimeException.class,()->partial.as(TransactionalOperator.create(f.manager)::transactional).block(WAIT));assertTrue(hasSqlState(incomplete,"P0001"));assertTrue(hasMessage(incomplete,"graph witness count mismatch"));assertEquals("DRAFT",state(c));
    }
    @Test @Order(13) void forgedNonceAndNullHorizonAreRejectedWithoutPrivateStateAccess()throws Exception {
        try(var connection=f.runtime();var statement=connection.createStatement()){connection.setAutoCommit(false);UUID nonce=UUID.randomUUID();statement.execute("SELECT set_config('firefly.organization.enrollment_nonce','"+nonce+"',true)");try(var row=statement.executeQuery("SELECT public.org_native_enrollment_check_context('"+nonce+"'::uuid)")){row.next();assertFalse(row.getBoolean(1));}connection.rollback();}
        f.staff(false,"reader");var admission=f.historyAuthorization.admit(TENANT,id(1000).toString(),UUID.randomUUID().toString()).block(WAIT);UUID nonce=UUID.randomUUID();var packet=ProposalTerminationEvidenceCodec.json(EnrollmentActionHistoryCodec.context(admission,nonce));var parsed=MAPPER.readTree(packet);((com.fasterxml.jackson.databind.node.ObjectNode)parsed.get("target")).putNull("authorityUntilEpochMillis");
        try(var connection=f.runtime();var statement=connection.prepareStatement("SELECT public.org_native_enrollment_begin_read_context(?,?)")){statement.setObject(1,nonce);statement.setString(2,parsed.toString());assertEquals("22023",assertThrows(SQLException.class,statement::execute).getSQLState());}
    }
    @Test @Order(14) void cascadeWitnessesRetainDeletedBranchAncestryAndCalendarOwnerBank()throws Exception {
        f.staff(true,"metadata-source-fixture");UUID a=id(99000),b=id(99001),division=id(99100),region=id(99200),branch=id(99300),calendar=id(99400),assignment=id(99500);
        for(var bank:List.of(a,b))f.operations.insert(Bank.builder().id(bank).code("cascade-"+bank).name("Owned cascade fixture").isActive(true).createdAt(java.time.LocalDateTime.now(java.time.ZoneOffset.UTC)).createdBy(UUID.fromString(MAKER)).build()).block(WAIT);
        try(var connection=f.runtime()){
            for(var entry:List.of(
                    new Object[]{"INSERT INTO public.bank_division(id,bank_id,code,name,created_by) VALUES(?,?,'fixture','Fixture',?)",division,a},
                    new Object[]{"INSERT INTO public.bank_region(id,division_id,code,name,created_by) VALUES(?,?,'fixture','Fixture',?)",region,division},
                    new Object[]{"INSERT INTO public.working_calendar(id,bank_id,name,created_by) VALUES(?,?,'Fixture',?)",calendar,b})){
                try(var statement=connection.prepareStatement((String)entry[0])){statement.setObject(1,entry[1]);statement.setObject(2,entry[2]);statement.setObject(3,UUID.fromString(MAKER));assertEquals(1,statement.executeUpdate());}
            }
            try(var statement=connection.prepareStatement("INSERT INTO public.branch(id,bank_id,region_id,code,name,created_by) VALUES(?,?,?,'fixture','Fixture',?)")){statement.setObject(1,branch);statement.setObject(2,a);statement.setObject(3,region);statement.setObject(4,UUID.fromString(MAKER));assertEquals(1,statement.executeUpdate());}
            try(var statement=connection.prepareStatement("INSERT INTO public.calendar_assignment(id,calendar_id,branch_id,effective_from,created_by) VALUES(?,?,?,CURRENT_TIMESTAMP,?)")){statement.setObject(1,assignment);statement.setObject(2,calendar);statement.setObject(3,branch);statement.setObject(4,UUID.fromString(MAKER));assertEquals(1,statement.executeUpdate());}
            long beforeA=source(a),beforeB=source(b);try(var statement=connection.prepareStatement("DELETE FROM public.bank WHERE id=?")){statement.setObject(1,a);assertEquals(1,statement.executeUpdate());}assertTrue(source(a)>beforeA);assertTrue(source(b)>beforeB);
        }
        try(var connection=f.admin();var statement=connection.prepareStatement("SELECT (SELECT count(*) FROM public.calendar_assignment WHERE id=?),(SELECT count(*) FROM public.working_calendar WHERE id=?),(SELECT ownership_generation FROM public.org_native_bank_gate WHERE bank_id=?)")){statement.setObject(1,assignment);statement.setObject(2,calendar);statement.setObject(3,a);try(var row=statement.executeQuery()){assertTrue(row.next());assertEquals(0,row.getLong(1));assertEquals(1,row.getLong(2));assertEquals(0,row.getLong(3));}}
    }
}
