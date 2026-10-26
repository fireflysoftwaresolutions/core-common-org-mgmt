-- BEGIN SOURCE SECTION 01-schema.sql
-- First proposal/preparation Flyway source candidate. Separate startup qualification is required.
-- Owner admission/control/financial effects remain outside this migration.
CREATE SCHEMA org_private;
REVOKE ALL ON SCHEMA org_private FROM PUBLIC;

CREATE TABLE public.org_native_source_catalogue_gate (
    singleton BOOLEAN NOT NULL,
    format_version INTEGER NOT NULL,
    PRIMARY KEY (singleton)
);

CREATE TABLE public.org_native_scope_gate (
    tenant_id UUID NOT NULL,
    institution_id UUID NOT NULL,
    PRIMARY KEY (tenant_id, institution_id),
    CHECK (tenant_id IS NULL OR tenant_id<>'00000000-0000-0000-0000-000000000000'::uuid),
    CHECK (institution_id IS NULL OR institution_id<>'00000000-0000-0000-0000-000000000000'::uuid)
);

CREATE TABLE public.org_native_bank_gate (
    bank_id UUID NOT NULL,
    bank_generation BIGINT NOT NULL,
    ownership_generation BIGINT NOT NULL,
    source_generation BIGINT NOT NULL,
    current_tenant_id UUID,
    current_institution_id UUID,
    current_issuer_id UUID,
    current_enrollment_id UUID,
    current_enrollment_version BIGINT,
    last_owner_action_id UUID,
    last_owner_action_tenant_id UUID,
    PRIMARY KEY (bank_id),
    CHECK (bank_id IS NULL OR bank_id<>'00000000-0000-0000-0000-000000000000'::uuid),
    CHECK (bank_generation IS NULL OR (bank_generation>=0 AND bank_generation<9223372036854775807)),
    CHECK (ownership_generation IS NULL OR (ownership_generation>=0 AND ownership_generation<9223372036854775807)),
    CHECK (source_generation IS NULL OR (source_generation>=0 AND source_generation<9223372036854775807)),
    CHECK (current_tenant_id IS NULL OR current_tenant_id<>'00000000-0000-0000-0000-000000000000'::uuid),
    CHECK (current_institution_id IS NULL OR current_institution_id<>'00000000-0000-0000-0000-000000000000'::uuid),
    CHECK (current_issuer_id IS NULL OR current_issuer_id<>'00000000-0000-0000-0000-000000000000'::uuid),
    CHECK (current_enrollment_id IS NULL OR current_enrollment_id<>'00000000-0000-0000-0000-000000000000'::uuid),
    CHECK (current_enrollment_version IS NULL OR (current_enrollment_version>=0 AND current_enrollment_version<9223372036854775807)),
    CHECK (last_owner_action_id IS NULL OR last_owner_action_id<>'00000000-0000-0000-0000-000000000000'::uuid),
    CHECK (last_owner_action_tenant_id IS NULL OR last_owner_action_tenant_id<>'00000000-0000-0000-0000-000000000000'::uuid)
);

INSERT INTO public.org_native_bank_gate(bank_id,bank_generation,ownership_generation,source_generation)
SELECT id,0,0,0 FROM public.bank WHERE id<>'00000000-0000-0000-0000-000000000000'::uuid;

CREATE TABLE public.org_native_source_change (
    bank_id UUID NOT NULL,
    source_generation BIGINT NOT NULL,
    bank_generation_before BIGINT NOT NULL,
    bank_generation_after BIGINT NOT NULL,
    relation_kind TEXT NOT NULL,
    row_id UUID NOT NULL,
    row_operation TEXT NOT NULL,
    old_facts JSONB,
    new_facts JSONB,
    db_session_user NAME NOT NULL,
    db_current_user NAME NOT NULL,
    recorded_at_ms BIGINT NOT NULL,
    PRIMARY KEY (bank_id, source_generation),
    CHECK (bank_id IS NULL OR bank_id<>'00000000-0000-0000-0000-000000000000'::uuid),
    CHECK (source_generation IS NULL OR (source_generation>=0 AND source_generation<9223372036854775807)),
    CHECK (bank_generation_before IS NULL OR (bank_generation_before>=0 AND bank_generation_before<9223372036854775807)),
    CHECK (bank_generation_after IS NULL OR (bank_generation_after>=0 AND bank_generation_after<9223372036854775807)),
    CHECK (recorded_at_ms IS NULL OR (recorded_at_ms>0 AND recorded_at_ms<=253402300799999))
);

CREATE TABLE public.org_native_bank_enrollment_version (
    tenant_id UUID NOT NULL,
    enrollment_id UUID NOT NULL,
    version BIGINT NOT NULL,
    bank_id UUID NOT NULL,
    institution_id UUID NOT NULL,
    issuer_id UUID NOT NULL,
    allowed_uses TEXT[] NOT NULL,
    proposal_id UUID NOT NULL,
    proposal_revision BIGINT NOT NULL,
    execution_action_id UUID NOT NULL,
    ownership_generation_at_admission BIGINT NOT NULL,
    bank_generation_at_admission BIGINT NOT NULL,
    source_generation_at_admission BIGINT NOT NULL,
    previous_tenant_id UUID,
    previous_enrollment_id UUID,
    previous_version BIGINT,
    approved_revision_original BYTEA NOT NULL,
    approved_revision_encoding VARCHAR(80) NOT NULL,
    approved_revision_canonical TEXT NOT NULL,
    approved_revision_digest CHAR(64) NOT NULL,
    recorded_at_ms BIGINT NOT NULL,
    PRIMARY KEY (tenant_id, enrollment_id, version),
    CHECK (tenant_id IS NULL OR tenant_id<>'00000000-0000-0000-0000-000000000000'::uuid),
    CHECK (enrollment_id IS NULL OR enrollment_id<>'00000000-0000-0000-0000-000000000000'::uuid),
    CHECK (version IS NULL OR (version>0 AND version<9223372036854775807)),
    CHECK (bank_id IS NULL OR bank_id<>'00000000-0000-0000-0000-000000000000'::uuid),
    CHECK (institution_id IS NULL OR institution_id<>'00000000-0000-0000-0000-000000000000'::uuid),
    CHECK (issuer_id IS NULL OR issuer_id<>'00000000-0000-0000-0000-000000000000'::uuid),
    CHECK (allowed_uses IN (ARRAY['ACCOUNTING_BOOK']::text[],ARRAY['CASH_OPERATIONS']::text[],ARRAY['PRODUCT_ISSUANCE']::text[],ARRAY['ACCOUNTING_BOOK','CASH_OPERATIONS']::text[],ARRAY['ACCOUNTING_BOOK','PRODUCT_ISSUANCE']::text[],ARRAY['CASH_OPERATIONS','PRODUCT_ISSUANCE']::text[],ARRAY['ACCOUNTING_BOOK','CASH_OPERATIONS','PRODUCT_ISSUANCE']::text[])),
    CHECK (proposal_id IS NULL OR proposal_id<>'00000000-0000-0000-0000-000000000000'::uuid),
    CHECK (proposal_revision IS NULL OR (proposal_revision>0 AND proposal_revision<9223372036854775807)),
    CHECK (execution_action_id IS NULL OR execution_action_id<>'00000000-0000-0000-0000-000000000000'::uuid),
    CHECK (ownership_generation_at_admission IS NULL OR (ownership_generation_at_admission>=0 AND ownership_generation_at_admission<9223372036854775807)),
    CHECK (bank_generation_at_admission IS NULL OR (bank_generation_at_admission>=0 AND bank_generation_at_admission<9223372036854775807)),
    CHECK (source_generation_at_admission IS NULL OR (source_generation_at_admission>=0 AND source_generation_at_admission<9223372036854775807)),
    CHECK (previous_tenant_id IS NULL OR previous_tenant_id<>'00000000-0000-0000-0000-000000000000'::uuid),
    CHECK (previous_enrollment_id IS NULL OR previous_enrollment_id<>'00000000-0000-0000-0000-000000000000'::uuid),
    CHECK (previous_version IS NULL OR (previous_version>0 AND previous_version<9223372036854775807)),
    CHECK (octet_length(approved_revision_original) BETWEEN 1 AND 4194304),
    CHECK (approved_revision_digest IS NULL OR approved_revision_digest ~ '^[0-9a-f]{64}$'),
    CHECK (recorded_at_ms IS NULL OR (recorded_at_ms>0 AND recorded_at_ms<=253402300799999))
);

CREATE TABLE public.org_native_bank_enrollment_control (
    tenant_id UUID NOT NULL,
    enrollment_id UUID NOT NULL,
    version BIGINT NOT NULL,
    state TEXT NOT NULL,
    control_version BIGINT NOT NULL,
    admission_action_id UUID NOT NULL,
    last_action_id UUID NOT NULL,
    admission_action_tenant_id UUID NOT NULL,
    last_action_tenant_id UUID NOT NULL,
    PRIMARY KEY (tenant_id, enrollment_id, version),
    CHECK (tenant_id IS NULL OR tenant_id<>'00000000-0000-0000-0000-000000000000'::uuid),
    CHECK (enrollment_id IS NULL OR enrollment_id<>'00000000-0000-0000-0000-000000000000'::uuid),
    CHECK (version IS NULL OR (version>0 AND version<9223372036854775807)),
    CHECK (control_version IS NULL OR (control_version>=0 AND control_version<9223372036854775807)),
    CHECK (admission_action_id IS NULL OR admission_action_id<>'00000000-0000-0000-0000-000000000000'::uuid),
    CHECK (last_action_id IS NULL OR last_action_id<>'00000000-0000-0000-0000-000000000000'::uuid),
    CHECK (admission_action_tenant_id IS NULL OR admission_action_tenant_id<>'00000000-0000-0000-0000-000000000000'::uuid),
    CHECK (last_action_tenant_id IS NULL OR last_action_tenant_id<>'00000000-0000-0000-0000-000000000000'::uuid)
);

CREATE TABLE public.org_native_proposal (
    tenant_id UUID NOT NULL,
    proposal_id UUID NOT NULL,
    bank_id UUID NOT NULL,
    institution_id UUID NOT NULL,
    issuer_id UUID NOT NULL,
    target_kind TEXT NOT NULL,
    allocated_enrollment_id UUID NOT NULL,
    maker_kind TEXT NOT NULL,
    maker_issuer VARCHAR(500) NOT NULL,
    maker_subject VARCHAR(255) NOT NULL,
    maker_tenant_id UUID NOT NULL,
    maker_staff_realm VARCHAR(255),
    maker_staff_id UUID,
    maker_client_id VARCHAR(255),
    prepare_action_id UUID NOT NULL,
    recorded_at_ms BIGINT NOT NULL,
    PRIMARY KEY (tenant_id, proposal_id),
    CHECK (tenant_id IS NULL OR tenant_id<>'00000000-0000-0000-0000-000000000000'::uuid),
    CHECK (proposal_id IS NULL OR proposal_id<>'00000000-0000-0000-0000-000000000000'::uuid),
    CHECK (bank_id IS NULL OR bank_id<>'00000000-0000-0000-0000-000000000000'::uuid),
    CHECK (institution_id IS NULL OR institution_id<>'00000000-0000-0000-0000-000000000000'::uuid),
    CHECK (issuer_id IS NULL OR issuer_id<>'00000000-0000-0000-0000-000000000000'::uuid),
    CHECK (allocated_enrollment_id IS NULL OR allocated_enrollment_id<>'00000000-0000-0000-0000-000000000000'::uuid),
    CHECK (maker_tenant_id IS NULL OR maker_tenant_id<>'00000000-0000-0000-0000-000000000000'::uuid),
    CHECK (maker_staff_id IS NULL OR maker_staff_id<>'00000000-0000-0000-0000-000000000000'::uuid),
    CHECK (prepare_action_id IS NULL OR prepare_action_id<>'00000000-0000-0000-0000-000000000000'::uuid),
    CHECK (recorded_at_ms IS NULL OR (recorded_at_ms>0 AND recorded_at_ms<=253402300799999))
);

CREATE TABLE public.org_native_proposal_revision (
    tenant_id UUID NOT NULL,
    proposal_id UUID NOT NULL,
    revision BIGINT NOT NULL,
    target_kind TEXT NOT NULL,
    reserved_execution_action_id UUID NOT NULL,
    expected_bank_generation BIGINT NOT NULL,
    expected_ownership_generation BIGINT NOT NULL,
    expected_source_generation BIGINT NOT NULL,
    expected_control_version BIGINT,
    not_after_ms BIGINT NOT NULL,
    allowed_uses TEXT[] NOT NULL,
    revision_original BYTEA NOT NULL,
    revision_encoding VARCHAR(80) NOT NULL,
    revision_canonical TEXT NOT NULL,
    revision_digest CHAR(64) NOT NULL,
    recorded_at_ms BIGINT NOT NULL,
    PRIMARY KEY (tenant_id, proposal_id, revision),
    CHECK (tenant_id IS NULL OR tenant_id<>'00000000-0000-0000-0000-000000000000'::uuid),
    CHECK (proposal_id IS NULL OR proposal_id<>'00000000-0000-0000-0000-000000000000'::uuid),
    CHECK (revision IS NULL OR (revision>0 AND revision<9223372036854775807)),
    CHECK (reserved_execution_action_id IS NULL OR reserved_execution_action_id<>'00000000-0000-0000-0000-000000000000'::uuid),
    CHECK (expected_bank_generation IS NULL OR (expected_bank_generation>=0 AND expected_bank_generation<9223372036854775807)),
    CHECK (expected_ownership_generation IS NULL OR (expected_ownership_generation>=0 AND expected_ownership_generation<9223372036854775807)),
    CHECK (expected_source_generation IS NULL OR (expected_source_generation>=0 AND expected_source_generation<9223372036854775807)),
    CHECK (expected_control_version IS NULL OR (expected_control_version>=0 AND expected_control_version<9223372036854775807)),
    CHECK (not_after_ms IS NULL OR (not_after_ms>0 AND not_after_ms<=253402300799999)),
    CHECK (allowed_uses IN (ARRAY['ACCOUNTING_BOOK']::text[],ARRAY['CASH_OPERATIONS']::text[],ARRAY['PRODUCT_ISSUANCE']::text[],ARRAY['ACCOUNTING_BOOK','CASH_OPERATIONS']::text[],ARRAY['ACCOUNTING_BOOK','PRODUCT_ISSUANCE']::text[],ARRAY['CASH_OPERATIONS','PRODUCT_ISSUANCE']::text[],ARRAY['ACCOUNTING_BOOK','CASH_OPERATIONS','PRODUCT_ISSUANCE']::text[])),
    CHECK (octet_length(revision_original) BETWEEN 1 AND 4194304),
    CHECK (revision_digest IS NULL OR revision_digest ~ '^[0-9a-f]{64}$'),
    CHECK (recorded_at_ms IS NULL OR (recorded_at_ms>0 AND recorded_at_ms<=253402300799999))
);

CREATE TABLE public.org_native_proposal_control (
    tenant_id UUID NOT NULL,
    proposal_id UUID NOT NULL,
    state TEXT NOT NULL,
    current_revision BIGINT NOT NULL,
    control_version BIGINT NOT NULL,
    approval_action_id UUID,
    approval_until_ms BIGINT,
    last_action_id UUID NOT NULL,
    PRIMARY KEY (tenant_id, proposal_id),
    CHECK (tenant_id IS NULL OR tenant_id<>'00000000-0000-0000-0000-000000000000'::uuid),
    CHECK (proposal_id IS NULL OR proposal_id<>'00000000-0000-0000-0000-000000000000'::uuid),
    CHECK (current_revision IS NULL OR (current_revision>0 AND current_revision<9223372036854775807)),
    CHECK (control_version IS NULL OR (control_version>=0 AND control_version<9223372036854775807)),
    CHECK (approval_action_id IS NULL OR approval_action_id<>'00000000-0000-0000-0000-000000000000'::uuid),
    CHECK (approval_until_ms IS NULL OR (approval_until_ms>0 AND approval_until_ms<=253402300799999)),
    CHECK (last_action_id IS NULL OR last_action_id<>'00000000-0000-0000-0000-000000000000'::uuid)
);

CREATE TABLE public.org_native_decision (
    tenant_id UUID NOT NULL,
    action_id UUID NOT NULL,
    proposal_id UUID NOT NULL,
    proposal_revision BIGINT NOT NULL,
    target_digest CHAR(64) NOT NULL,
    decision TEXT NOT NULL,
    approval_until_ms BIGINT,
    decision_actor_kind TEXT NOT NULL,
    decision_actor_issuer VARCHAR(500) NOT NULL,
    decision_actor_subject VARCHAR(255) NOT NULL,
    decision_actor_tenant_id UUID NOT NULL,
    decision_actor_staff_realm VARCHAR(255),
    decision_actor_staff_id UUID,
    decision_actor_client_id VARCHAR(255),
    reason VARCHAR(4096) NOT NULL,
    recorded_at_ms BIGINT NOT NULL,
    PRIMARY KEY (tenant_id, action_id),
    CHECK (tenant_id IS NULL OR tenant_id<>'00000000-0000-0000-0000-000000000000'::uuid),
    CHECK (action_id IS NULL OR action_id<>'00000000-0000-0000-0000-000000000000'::uuid),
    CHECK (proposal_id IS NULL OR proposal_id<>'00000000-0000-0000-0000-000000000000'::uuid),
    CHECK (proposal_revision IS NULL OR (proposal_revision>0 AND proposal_revision<9223372036854775807)),
    CHECK (target_digest IS NULL OR target_digest ~ '^[0-9a-f]{64}$'),
    CHECK (approval_until_ms IS NULL OR (approval_until_ms>0 AND approval_until_ms<=253402300799999)),
    CHECK (decision_actor_tenant_id IS NULL OR decision_actor_tenant_id<>'00000000-0000-0000-0000-000000000000'::uuid),
    CHECK (decision_actor_staff_id IS NULL OR decision_actor_staff_id<>'00000000-0000-0000-0000-000000000000'::uuid),
    CHECK (recorded_at_ms IS NULL OR (recorded_at_ms>0 AND recorded_at_ms<=253402300799999))
);

CREATE TABLE public.org_native_action (
    tenant_id UUID NOT NULL,
    action_id UUID NOT NULL,
    bank_id UUID NOT NULL,
    intended_operation TEXT NOT NULL,
    intended_route TEXT NOT NULL,
    request_original BYTEA NOT NULL,
    request_encoding VARCHAR(80) NOT NULL,
    request_canonical TEXT NOT NULL,
    request_digest CHAR(64) NOT NULL,
    resolution_kind TEXT NOT NULL,
    resolution_original BYTEA NOT NULL,
    resolution_encoding VARCHAR(80) NOT NULL,
    resolution_canonical TEXT NOT NULL,
    resolution_digest CHAR(64) NOT NULL,
    resolver_kind TEXT NOT NULL,
    resolver_issuer VARCHAR(500) NOT NULL,
    resolver_subject VARCHAR(255) NOT NULL,
    resolver_tenant_id UUID NOT NULL,
    resolver_staff_realm VARCHAR(255),
    resolver_staff_id UUID,
    resolver_client_id VARCHAR(255),
    proposal_id UUID,
    proposal_revision BIGINT,
    target_digest CHAR(64),
    outcome TEXT NOT NULL,
    recorded_at_ms BIGINT NOT NULL,
    PRIMARY KEY (tenant_id, action_id),
    CHECK (tenant_id IS NULL OR tenant_id<>'00000000-0000-0000-0000-000000000000'::uuid),
    CHECK (action_id IS NULL OR action_id<>'00000000-0000-0000-0000-000000000000'::uuid),
    CHECK (bank_id IS NULL OR bank_id<>'00000000-0000-0000-0000-000000000000'::uuid),
    CHECK (octet_length(request_original) BETWEEN 1 AND 4194304),
    CHECK (request_digest IS NULL OR request_digest ~ '^[0-9a-f]{64}$'),
    CHECK (octet_length(resolution_original) BETWEEN 1 AND 4194304),
    CHECK (resolution_digest IS NULL OR resolution_digest ~ '^[0-9a-f]{64}$'),
    CHECK (resolver_tenant_id IS NULL OR resolver_tenant_id<>'00000000-0000-0000-0000-000000000000'::uuid),
    CHECK (resolver_staff_id IS NULL OR resolver_staff_id<>'00000000-0000-0000-0000-000000000000'::uuid),
    CHECK (proposal_id IS NULL OR proposal_id<>'00000000-0000-0000-0000-000000000000'::uuid),
    CHECK (proposal_revision IS NULL OR (proposal_revision>0 AND proposal_revision<9223372036854775807)),
    CHECK (target_digest IS NULL OR target_digest ~ '^[0-9a-f]{64}$'),
    CHECK (recorded_at_ms IS NULL OR (recorded_at_ms>0 AND recorded_at_ms<=253402300799999))
);

CREATE TABLE public.org_native_receipt (
    tenant_id UUID NOT NULL,
    action_id UUID NOT NULL,
    receipt_id UUID NOT NULL,
    result_original BYTEA NOT NULL,
    result_encoding VARCHAR(80) NOT NULL,
    result_canonical TEXT NOT NULL,
    result_digest CHAR(64) NOT NULL,
    recorded_at_ms BIGINT NOT NULL,
    PRIMARY KEY (tenant_id, action_id),
    CHECK (tenant_id IS NULL OR tenant_id<>'00000000-0000-0000-0000-000000000000'::uuid),
    CHECK (action_id IS NULL OR action_id<>'00000000-0000-0000-0000-000000000000'::uuid),
    CHECK (receipt_id IS NULL OR receipt_id<>'00000000-0000-0000-0000-000000000000'::uuid),
    CHECK (octet_length(result_original) BETWEEN 1 AND 4194304),
    CHECK (result_digest IS NULL OR result_digest ~ '^[0-9a-f]{64}$'),
    CHECK (recorded_at_ms IS NULL OR (recorded_at_ms>0 AND recorded_at_ms<=253402300799999))
);

CREATE TABLE public.org_native_audit (
    tenant_id UUID NOT NULL,
    action_id UUID NOT NULL,
    audit_id UUID NOT NULL,
    bank_id UUID NOT NULL,
    event_original BYTEA NOT NULL,
    event_encoding VARCHAR(80) NOT NULL,
    event_canonical TEXT NOT NULL,
    event_digest CHAR(64) NOT NULL,
    recorded_at_ms BIGINT NOT NULL,
    PRIMARY KEY (tenant_id, action_id),
    CHECK (tenant_id IS NULL OR tenant_id<>'00000000-0000-0000-0000-000000000000'::uuid),
    CHECK (action_id IS NULL OR action_id<>'00000000-0000-0000-0000-000000000000'::uuid),
    CHECK (audit_id IS NULL OR audit_id<>'00000000-0000-0000-0000-000000000000'::uuid),
    CHECK (bank_id IS NULL OR bank_id<>'00000000-0000-0000-0000-000000000000'::uuid),
    CHECK (octet_length(event_original) BETWEEN 1 AND 4194304),
    CHECK (event_digest IS NULL OR event_digest ~ '^[0-9a-f]{64}$'),
    CHECK (recorded_at_ms IS NULL OR (recorded_at_ms>0 AND recorded_at_ms<=253402300799999))
);

CREATE TABLE public.org_native_outbox (
    tenant_id UUID NOT NULL,
    action_id UUID NOT NULL,
    event_id UUID NOT NULL,
    bank_id UUID NOT NULL,
    event_original BYTEA NOT NULL,
    event_encoding VARCHAR(80) NOT NULL,
    event_canonical TEXT NOT NULL,
    event_digest CHAR(64) NOT NULL,
    recorded_at_ms BIGINT NOT NULL,
    PRIMARY KEY (tenant_id, action_id),
    CHECK (tenant_id IS NULL OR tenant_id<>'00000000-0000-0000-0000-000000000000'::uuid),
    CHECK (action_id IS NULL OR action_id<>'00000000-0000-0000-0000-000000000000'::uuid),
    CHECK (event_id IS NULL OR event_id<>'00000000-0000-0000-0000-000000000000'::uuid),
    CHECK (bank_id IS NULL OR bank_id<>'00000000-0000-0000-0000-000000000000'::uuid),
    CHECK (octet_length(event_original) BETWEEN 1 AND 4194304),
    CHECK (event_digest IS NULL OR event_digest ~ '^[0-9a-f]{64}$'),
    CHECK (recorded_at_ms IS NULL OR (recorded_at_ms>0 AND recorded_at_ms<=253402300799999))
);

CREATE TABLE public.org_native_outbox_delivery (
    tenant_id UUID NOT NULL,
    event_id UUID NOT NULL,
    control_version BIGINT NOT NULL,
    state TEXT NOT NULL,
    attempt_count INTEGER NOT NULL,
    lease_owner VARCHAR(255),
    lease_until_ms BIGINT,
    next_attempt_ms BIGINT NOT NULL,
    last_error_code VARCHAR(80),
    last_ack_at_ms BIGINT,
    PRIMARY KEY (tenant_id, event_id),
    CHECK (tenant_id IS NULL OR tenant_id<>'00000000-0000-0000-0000-000000000000'::uuid),
    CHECK (event_id IS NULL OR event_id<>'00000000-0000-0000-0000-000000000000'::uuid),
    CHECK (control_version IS NULL OR (control_version>=0 AND control_version<9223372036854775807)),
    CHECK (lease_until_ms IS NULL OR (lease_until_ms>0 AND lease_until_ms<=253402300799999)),
    CHECK (next_attempt_ms IS NULL OR (next_attempt_ms>0 AND next_attempt_ms<=253402300799999)),
    CHECK (last_ack_at_ms IS NULL OR (last_ack_at_ms>0 AND last_ack_at_ms<=253402300799999))
);

CREATE TABLE org_private.enrollment_context (
    nonce UUID NOT NULL,
    xid XID8 NOT NULL,
    backend_pid INTEGER NOT NULL,
    db_session_user NAME NOT NULL,
    operation TEXT NOT NULL,
    tenant_id UUID NOT NULL,
    bank_id UUID NOT NULL,
    action_id UUID NOT NULL,
    purpose TEXT NOT NULL,
    proposal_id UUID,
    proposal_revision BIGINT,
    context_digest CHAR(64) NOT NULL,
    locked BOOLEAN NOT NULL DEFAULT false,
    canonical_request_digest CHAR(64) NOT NULL,
    full_context BYTEA NOT NULL,
    authority_until_ms BIGINT NOT NULL,
    command_until_ms BIGINT NOT NULL,
    recorded_at_ms BIGINT,
    PRIMARY KEY (nonce),
    CHECK (nonce IS NULL OR nonce<>'00000000-0000-0000-0000-000000000000'::uuid),
    CHECK (tenant_id IS NULL OR tenant_id<>'00000000-0000-0000-0000-000000000000'::uuid),
    CHECK (bank_id IS NULL OR bank_id<>'00000000-0000-0000-0000-000000000000'::uuid),
    CHECK (action_id IS NULL OR action_id<>'00000000-0000-0000-0000-000000000000'::uuid),
    CHECK (canonical_request_digest IS NULL OR canonical_request_digest ~ '^[0-9a-f]{64}$'),
    CHECK (octet_length(full_context) BETWEEN 1 AND 4194304),
    CHECK (purpose IN ('ACTION_READ','PROPOSAL_DISCOVERY','MUTATION','PREPARATION_DISCOVERY','PREPARATION_MUTATION')),
    CHECK ((purpose IN ('ACTION_READ','PREPARATION_DISCOVERY') AND proposal_id IS NULL AND proposal_revision IS NULL)
        OR (purpose IN ('PROPOSAL_DISCOVERY','MUTATION','PREPARATION_MUTATION') AND proposal_id IS NOT NULL AND proposal_revision>0 AND proposal_revision<9223372036854775807)),
    CHECK (context_digest ~ '^[0-9a-f]{64}$'),
    CHECK ((locked AND recorded_at_ms IS NOT NULL) OR (NOT locked AND recorded_at_ms IS NULL)),
    CHECK (authority_until_ms IS NULL OR (authority_until_ms>0 AND authority_until_ms<=253402300799999)),
    CHECK (command_until_ms IS NULL OR (command_until_ms>0 AND command_until_ms<=253402300799999)),
    CHECK (recorded_at_ms IS NULL OR (recorded_at_ms>0 AND recorded_at_ms<=253402300799999))
);

CREATE TABLE org_private.enrollment_row_witness (
    ordinal BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL,
    context_nonce UUID NOT NULL,
    relation_kind TEXT NOT NULL,
    row_key JSONB NOT NULL,
    operation TEXT NOT NULL,
    old_row JSONB,
    new_row JSONB,
    PRIMARY KEY (ordinal),
    CHECK (context_nonce IS NULL OR context_nonce<>'00000000-0000-0000-0000-000000000000'::uuid)
);

CREATE TABLE org_private.legacy_source_witness (
    ordinal BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL,
    xid XID8 NOT NULL,
    backend_pid INTEGER NOT NULL,
    db_session_user NAME NOT NULL,
    relation_kind TEXT NOT NULL,
    row_id UUID NOT NULL,
    operation TEXT NOT NULL,
    old_bank_ids UUID[] NOT NULL,
    new_bank_ids UUID[] NOT NULL,
    old_facts JSONB,
    new_facts JSONB,
    bank_identity_changed BOOLEAN NOT NULL,
    flushed BOOLEAN NOT NULL,
    PRIMARY KEY (ordinal)
);

ALTER TABLE public.org_native_source_catalogue_gate ADD CHECK (singleton AND format_version=1);
INSERT INTO public.org_native_source_catalogue_gate(singleton,format_version) VALUES(true,1);

ALTER TABLE public.org_native_bank_gate
    ADD CHECK (bank_generation>=ownership_generation),
    ADD CHECK ((current_tenant_id IS NULL AND current_institution_id IS NULL AND current_issuer_id IS NULL AND current_enrollment_id IS NULL AND current_enrollment_version IS NULL)
        OR (current_tenant_id IS NOT NULL AND current_institution_id IS NOT NULL AND current_issuer_id IS NOT NULL AND current_enrollment_id IS NOT NULL AND current_enrollment_version IS NOT NULL AND current_enrollment_version>0)),
    ADD CHECK ((last_owner_action_id IS NULL)=(last_owner_action_tenant_id IS NULL)),
    ADD CHECK ((ownership_generation=0 AND current_enrollment_id IS NULL AND last_owner_action_id IS NULL)
        OR (ownership_generation>0 AND last_owner_action_id IS NOT NULL));

ALTER TABLE public.org_native_bank_enrollment_version
    ADD CHECK ((previous_tenant_id IS NULL AND previous_enrollment_id IS NULL AND previous_version IS NULL)
        OR (previous_tenant_id IS NOT NULL AND previous_enrollment_id IS NOT NULL AND previous_version IS NOT NULL AND previous_version>0)),
    ADD UNIQUE (tenant_id,execution_action_id),
    ADD UNIQUE (tenant_id,institution_id,issuer_id,enrollment_id,version),
    ADD CHECK (approved_revision_digest=encode(sha256(convert_to(approved_revision_canonical,'UTF8')),'hex'));
ALTER TABLE public.org_native_bank_enrollment_control ADD CHECK (state IN ('ACTIVE','SUSPENDED','REVOKED','SUPERSEDED'));
ALTER TABLE public.org_native_proposal
    ADD CHECK (maker_kind='STAFF' AND maker_staff_realm IS NOT NULL AND maker_staff_id IS NOT NULL),
    ADD CHECK (target_kind IN ('ENROLL','TRANSFER','REENROLL','READMIT','RESUME','UNENROLL'));
ALTER TABLE public.org_native_proposal_revision
    ADD UNIQUE (tenant_id,reserved_execution_action_id),
    ADD CHECK (not_after_ms>recorded_at_ms AND not_after_ms<=recorded_at_ms+86400000),
    ADD CHECK (revision_digest=encode(sha256(convert_to(revision_canonical,'UTF8')),'hex'));
ALTER TABLE public.org_native_proposal_control
    ADD CHECK (state IN ('DRAFT','SUBMITTED','APPROVED','REJECTED','WITHDRAWN','CANCELLED','EXECUTED','CLOSED_NO_EFFECT')),
    ADD CHECK ((state='APPROVED' AND approval_action_id IS NOT NULL AND approval_until_ms IS NOT NULL)
        OR (state<>'APPROVED' AND approval_action_id IS NULL AND approval_until_ms IS NULL));
ALTER TABLE public.org_native_decision
    ADD CHECK (decision IN ('APPROVE','REJECT','WITHDRAW')),
    ADD CHECK (decision_actor_kind='STAFF' AND decision_actor_staff_realm IS NOT NULL AND decision_actor_staff_id IS NOT NULL),
    ADD CHECK ((decision='APPROVE')=(approval_until_ms IS NOT NULL));
ALTER TABLE public.org_native_action
    ADD CHECK (outcome IN ('APPLIED','NO_EFFECT_FINAL')),
    ADD CHECK (resolver_kind IN ('STAFF','MACHINE')),
    ADD CHECK ((resolver_kind='STAFF' AND resolver_staff_realm IS NOT NULL AND resolver_staff_id IS NOT NULL)
        OR (resolver_kind='MACHINE' AND resolver_staff_realm IS NULL AND resolver_staff_id IS NULL AND resolver_client_id IS NOT NULL)),
    ADD CHECK ((proposal_id IS NULL AND proposal_revision IS NULL AND target_digest IS NULL)
        OR (proposal_id IS NOT NULL AND proposal_revision IS NOT NULL AND proposal_revision>0 AND target_digest IS NOT NULL)),
    ADD CHECK (request_digest=encode(sha256(convert_to(request_canonical,'UTF8')),'hex')),
    ADD CHECK (resolution_digest=encode(sha256(convert_to(resolution_canonical,'UTF8')),'hex'));
ALTER TABLE public.org_native_receipt ADD UNIQUE(receipt_id), ADD CHECK(result_digest=encode(sha256(convert_to(result_canonical,'UTF8')),'hex'));
ALTER TABLE public.org_native_audit ADD UNIQUE(audit_id), ADD CHECK(event_digest=encode(sha256(convert_to(event_canonical,'UTF8')),'hex'));
ALTER TABLE public.org_native_outbox ADD UNIQUE(event_id), ADD UNIQUE(tenant_id,event_id), ADD CHECK(event_digest=encode(sha256(convert_to(event_canonical,'UTF8')),'hex'));
ALTER TABLE public.org_native_outbox_delivery
    ADD CHECK (state IN ('PENDING','LEASED','ACKNOWLEDGED') AND attempt_count>=0),
    ADD CHECK ((state='LEASED' AND lease_owner IS NOT NULL AND lease_until_ms IS NOT NULL)
        OR (state<>'LEASED' AND lease_owner IS NULL AND lease_until_ms IS NULL)),
    ADD CHECK ((state='ACKNOWLEDGED')=(last_ack_at_ms IS NOT NULL));

ALTER TABLE public.org_native_bank_gate ADD FOREIGN KEY (current_tenant_id,current_institution_id,current_issuer_id,current_enrollment_id,current_enrollment_version) REFERENCES public.org_native_bank_enrollment_version (tenant_id,institution_id,issuer_id,enrollment_id,version) MATCH FULL DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE public.org_native_bank_gate ADD FOREIGN KEY (last_owner_action_tenant_id,last_owner_action_id) REFERENCES public.org_native_action (tenant_id,action_id) MATCH FULL DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE public.org_native_bank_enrollment_version ADD FOREIGN KEY (tenant_id,proposal_id,proposal_revision) REFERENCES public.org_native_proposal_revision (tenant_id,proposal_id,revision) DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE public.org_native_bank_enrollment_version ADD FOREIGN KEY (previous_tenant_id,previous_enrollment_id,previous_version) REFERENCES public.org_native_bank_enrollment_version (tenant_id,enrollment_id,version) MATCH FULL DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE public.org_native_bank_enrollment_version ADD FOREIGN KEY (tenant_id,execution_action_id) REFERENCES public.org_native_action (tenant_id,action_id) DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE public.org_native_bank_enrollment_control ADD FOREIGN KEY (tenant_id,enrollment_id,version) REFERENCES public.org_native_bank_enrollment_version (tenant_id,enrollment_id,version) DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE public.org_native_bank_enrollment_control ADD FOREIGN KEY (admission_action_tenant_id,admission_action_id) REFERENCES public.org_native_action (tenant_id,action_id) DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE public.org_native_bank_enrollment_control ADD FOREIGN KEY (last_action_tenant_id,last_action_id) REFERENCES public.org_native_action (tenant_id,action_id) DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE public.org_native_proposal ADD FOREIGN KEY (tenant_id,prepare_action_id) REFERENCES public.org_native_action (tenant_id,action_id) DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE public.org_native_proposal_revision ADD FOREIGN KEY (tenant_id,proposal_id) REFERENCES public.org_native_proposal (tenant_id,proposal_id) DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE public.org_native_proposal_control ADD FOREIGN KEY (tenant_id,proposal_id) REFERENCES public.org_native_proposal (tenant_id,proposal_id) DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE public.org_native_proposal_control ADD FOREIGN KEY (tenant_id,approval_action_id) REFERENCES public.org_native_decision (tenant_id,action_id) DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE public.org_native_proposal_control ADD FOREIGN KEY (tenant_id,last_action_id) REFERENCES public.org_native_action (tenant_id,action_id) DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE public.org_native_decision ADD FOREIGN KEY (tenant_id,proposal_id,proposal_revision) REFERENCES public.org_native_proposal_revision (tenant_id,proposal_id,revision) DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE public.org_native_decision ADD FOREIGN KEY (tenant_id,action_id) REFERENCES public.org_native_action (tenant_id,action_id) DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE public.org_native_receipt ADD FOREIGN KEY (tenant_id,action_id) REFERENCES public.org_native_action (tenant_id,action_id) DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE public.org_native_audit ADD FOREIGN KEY (tenant_id,action_id) REFERENCES public.org_native_action (tenant_id,action_id) DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE public.org_native_outbox ADD FOREIGN KEY (tenant_id,action_id) REFERENCES public.org_native_action (tenant_id,action_id) DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE public.org_native_outbox_delivery ADD FOREIGN KEY (tenant_id,event_id) REFERENCES public.org_native_outbox (tenant_id,event_id) DEFERRABLE INITIALLY DEFERRED;

CREATE INDEX org_native_context_tx ON org_private.enrollment_context(xid,backend_pid,db_session_user);
CREATE INDEX org_native_row_witness_context ON org_private.enrollment_row_witness(context_nonce,relation_kind);
CREATE INDEX org_native_legacy_witness_tx ON org_private.legacy_source_witness(xid,backend_pid,db_session_user,flushed);
-- END SOURCE SECTION 01-schema.sql

-- BEGIN SOURCE SECTION 02-roles.sql
-- Cluster roles are preprovisioned by explicit administrative deployment before Flyway.
-- This migration validates existing roles and never creates, alters, or grants cluster memberships.
DO $$
DECLARE expected_name TEXT; found_role RECORD;
BEGIN
    FOREACH expected_name IN ARRAY ARRAY['org_native_owner','org_native_guard','org_native_runtime'] LOOP
        SELECT * INTO found_role FROM pg_catalog.pg_roles WHERE rolname=expected_name;
        IF NOT FOUND OR found_role.rolsuper OR found_role.rolbypassrls OR found_role.rolinherit
            OR found_role.rolcreatedb OR found_role.rolcreaterole OR found_role.rolreplication
            OR found_role.rolcanlogin IS DISTINCT FROM (expected_name='org_native_runtime') THEN
            RAISE EXCEPTION 'Explicit restricted organization cluster role bootstrap required' USING ERRCODE='42501';
        END IF;
        IF EXISTS(SELECT 1 FROM pg_catalog.pg_auth_members WHERE member=found_role.oid) THEN
            RAISE EXCEPTION 'Restricted organization role membership required' USING ERRCODE='42501';
        END IF;
    END LOOP;
    IF pg_catalog.has_schema_privilege('org_native_owner','public','CREATE')
        OR pg_catalog.has_schema_privilege('org_native_guard','public','CREATE')
        OR pg_catalog.has_schema_privilege('org_native_guard','org_private','CREATE') THEN
        RAISE EXCEPTION 'Explicit migration-only schema assignment privilege required' USING ERRCODE='42501';
    END IF;
    IF current_user='org_native_runtime'
        OR NOT pg_catalog.pg_has_role(current_user,'org_native_owner','USAGE')
        OR NOT pg_catalog.pg_has_role(current_user,'org_native_guard','USAGE')
        OR NOT pg_catalog.pg_has_role(current_user,'org_native_owner','SET')
        OR NOT pg_catalog.pg_has_role(current_user,'org_native_guard','SET') THEN
        RAISE EXCEPTION 'Separately authorized organization migration role required' USING ERRCODE='42501';
    END IF;
END $$;
GRANT USAGE,CREATE ON SCHEMA public TO org_native_owner,org_native_guard;
GRANT CREATE ON SCHEMA org_private TO org_native_guard;
REVOKE ALL ON SCHEMA org_private FROM PUBLIC,org_native_runtime;
GRANT USAGE ON SCHEMA org_private TO org_native_guard;
ALTER TABLE public.org_native_source_catalogue_gate OWNER TO org_native_owner;
ALTER TABLE public.org_native_source_catalogue_gate ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.org_native_source_catalogue_gate FORCE ROW LEVEL SECURITY;
REVOKE ALL ON public.org_native_source_catalogue_gate FROM PUBLIC,org_native_runtime;
CREATE POLICY guard_only ON public.org_native_source_catalogue_gate TO org_native_guard USING(true) WITH CHECK(true);
GRANT SELECT ON public.org_native_source_catalogue_gate TO org_native_guard;
ALTER TABLE public.org_native_scope_gate OWNER TO org_native_owner;
ALTER TABLE public.org_native_scope_gate ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.org_native_scope_gate FORCE ROW LEVEL SECURITY;
REVOKE ALL ON public.org_native_scope_gate FROM PUBLIC,org_native_runtime;
CREATE POLICY guard_only ON public.org_native_scope_gate TO org_native_guard USING(true) WITH CHECK(true);
GRANT SELECT ON public.org_native_scope_gate TO org_native_guard;
ALTER TABLE public.org_native_bank_gate OWNER TO org_native_owner;
ALTER TABLE public.org_native_bank_gate ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.org_native_bank_gate FORCE ROW LEVEL SECURITY;
REVOKE ALL ON public.org_native_bank_gate FROM PUBLIC,org_native_runtime;
CREATE POLICY guard_only ON public.org_native_bank_gate TO org_native_guard USING(true) WITH CHECK(true);
GRANT SELECT ON public.org_native_bank_gate TO org_native_guard;
ALTER TABLE public.org_native_source_change OWNER TO org_native_owner;
ALTER TABLE public.org_native_source_change ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.org_native_source_change FORCE ROW LEVEL SECURITY;
REVOKE ALL ON public.org_native_source_change FROM PUBLIC,org_native_runtime;
CREATE POLICY guard_only ON public.org_native_source_change TO org_native_guard USING(true) WITH CHECK(true);
GRANT SELECT ON public.org_native_source_change TO org_native_guard;
ALTER TABLE public.org_native_bank_enrollment_version OWNER TO org_native_owner;
ALTER TABLE public.org_native_bank_enrollment_version ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.org_native_bank_enrollment_version FORCE ROW LEVEL SECURITY;
REVOKE ALL ON public.org_native_bank_enrollment_version FROM PUBLIC,org_native_runtime;
CREATE POLICY guard_only ON public.org_native_bank_enrollment_version TO org_native_guard USING(true) WITH CHECK(true);
GRANT SELECT ON public.org_native_bank_enrollment_version TO org_native_guard;
ALTER TABLE public.org_native_bank_enrollment_control OWNER TO org_native_owner;
ALTER TABLE public.org_native_bank_enrollment_control ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.org_native_bank_enrollment_control FORCE ROW LEVEL SECURITY;
REVOKE ALL ON public.org_native_bank_enrollment_control FROM PUBLIC,org_native_runtime;
CREATE POLICY guard_only ON public.org_native_bank_enrollment_control TO org_native_guard USING(true) WITH CHECK(true);
GRANT SELECT ON public.org_native_bank_enrollment_control TO org_native_guard;
ALTER TABLE public.org_native_proposal OWNER TO org_native_owner;
ALTER TABLE public.org_native_proposal ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.org_native_proposal FORCE ROW LEVEL SECURITY;
REVOKE ALL ON public.org_native_proposal FROM PUBLIC,org_native_runtime;
CREATE POLICY guard_only ON public.org_native_proposal TO org_native_guard USING(true) WITH CHECK(true);
GRANT SELECT ON public.org_native_proposal TO org_native_guard;
ALTER TABLE public.org_native_proposal_revision OWNER TO org_native_owner;
ALTER TABLE public.org_native_proposal_revision ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.org_native_proposal_revision FORCE ROW LEVEL SECURITY;
REVOKE ALL ON public.org_native_proposal_revision FROM PUBLIC,org_native_runtime;
CREATE POLICY guard_only ON public.org_native_proposal_revision TO org_native_guard USING(true) WITH CHECK(true);
GRANT SELECT ON public.org_native_proposal_revision TO org_native_guard;
ALTER TABLE public.org_native_proposal_control OWNER TO org_native_owner;
ALTER TABLE public.org_native_proposal_control ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.org_native_proposal_control FORCE ROW LEVEL SECURITY;
REVOKE ALL ON public.org_native_proposal_control FROM PUBLIC,org_native_runtime;
CREATE POLICY guard_only ON public.org_native_proposal_control TO org_native_guard USING(true) WITH CHECK(true);
GRANT SELECT ON public.org_native_proposal_control TO org_native_guard;
ALTER TABLE public.org_native_decision OWNER TO org_native_owner;
ALTER TABLE public.org_native_decision ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.org_native_decision FORCE ROW LEVEL SECURITY;
REVOKE ALL ON public.org_native_decision FROM PUBLIC,org_native_runtime;
CREATE POLICY guard_only ON public.org_native_decision TO org_native_guard USING(true) WITH CHECK(true);
GRANT SELECT ON public.org_native_decision TO org_native_guard;
ALTER TABLE public.org_native_action OWNER TO org_native_owner;
ALTER TABLE public.org_native_action ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.org_native_action FORCE ROW LEVEL SECURITY;
REVOKE ALL ON public.org_native_action FROM PUBLIC,org_native_runtime;
CREATE POLICY guard_only ON public.org_native_action TO org_native_guard USING(true) WITH CHECK(true);
GRANT SELECT ON public.org_native_action TO org_native_guard;
ALTER TABLE public.org_native_receipt OWNER TO org_native_owner;
ALTER TABLE public.org_native_receipt ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.org_native_receipt FORCE ROW LEVEL SECURITY;
REVOKE ALL ON public.org_native_receipt FROM PUBLIC,org_native_runtime;
CREATE POLICY guard_only ON public.org_native_receipt TO org_native_guard USING(true) WITH CHECK(true);
GRANT SELECT ON public.org_native_receipt TO org_native_guard;
ALTER TABLE public.org_native_audit OWNER TO org_native_owner;
ALTER TABLE public.org_native_audit ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.org_native_audit FORCE ROW LEVEL SECURITY;
REVOKE ALL ON public.org_native_audit FROM PUBLIC,org_native_runtime;
CREATE POLICY guard_only ON public.org_native_audit TO org_native_guard USING(true) WITH CHECK(true);
GRANT SELECT ON public.org_native_audit TO org_native_guard;
ALTER TABLE public.org_native_outbox OWNER TO org_native_owner;
ALTER TABLE public.org_native_outbox ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.org_native_outbox FORCE ROW LEVEL SECURITY;
REVOKE ALL ON public.org_native_outbox FROM PUBLIC,org_native_runtime;
CREATE POLICY guard_only ON public.org_native_outbox TO org_native_guard USING(true) WITH CHECK(true);
GRANT SELECT ON public.org_native_outbox TO org_native_guard;
ALTER TABLE public.org_native_outbox_delivery OWNER TO org_native_owner;
ALTER TABLE public.org_native_outbox_delivery ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.org_native_outbox_delivery FORCE ROW LEVEL SECURITY;
REVOKE ALL ON public.org_native_outbox_delivery FROM PUBLIC,org_native_runtime;
CREATE POLICY guard_only ON public.org_native_outbox_delivery TO org_native_guard USING(true) WITH CHECK(true);
GRANT SELECT ON public.org_native_outbox_delivery TO org_native_guard;
ALTER TABLE org_private.enrollment_context OWNER TO org_native_guard;
ALTER TABLE org_private.enrollment_context ENABLE ROW LEVEL SECURITY;
ALTER TABLE org_private.enrollment_context FORCE ROW LEVEL SECURITY;
REVOKE ALL ON org_private.enrollment_context FROM PUBLIC,org_native_runtime;
CREATE POLICY guard_only ON org_private.enrollment_context TO org_native_guard USING(true) WITH CHECK(true);
ALTER TABLE org_private.enrollment_row_witness OWNER TO org_native_guard;
ALTER TABLE org_private.enrollment_row_witness ENABLE ROW LEVEL SECURITY;
ALTER TABLE org_private.enrollment_row_witness FORCE ROW LEVEL SECURITY;
REVOKE ALL ON org_private.enrollment_row_witness FROM PUBLIC,org_native_runtime;
CREATE POLICY guard_only ON org_private.enrollment_row_witness TO org_native_guard USING(true) WITH CHECK(true);
ALTER TABLE org_private.legacy_source_witness OWNER TO org_native_guard;
ALTER TABLE org_private.legacy_source_witness ENABLE ROW LEVEL SECURITY;
ALTER TABLE org_private.legacy_source_witness FORCE ROW LEVEL SECURITY;
REVOKE ALL ON org_private.legacy_source_witness FROM PUBLIC,org_native_runtime;
CREATE POLICY guard_only ON org_private.legacy_source_witness TO org_native_guard USING(true) WITH CHECK(true);
-- Exact proposed runtime grants below remain commented until all native guards/policies are complete and reviewed.
-- GRANT INSERT ON public.org_native_bank_enrollment_version TO org_native_runtime;
-- GRANT INSERT ON public.org_native_bank_enrollment_control TO org_native_runtime;
-- GRANT INSERT ON public.org_native_proposal TO org_native_runtime;
-- GRANT INSERT ON public.org_native_proposal_revision TO org_native_runtime;
-- GRANT INSERT ON public.org_native_proposal_control TO org_native_runtime;
-- GRANT INSERT ON public.org_native_decision TO org_native_runtime;
-- GRANT INSERT ON public.org_native_action TO org_native_runtime;
-- GRANT INSERT ON public.org_native_receipt TO org_native_runtime;
-- GRANT INSERT ON public.org_native_audit TO org_native_runtime;
-- GRANT INSERT ON public.org_native_outbox TO org_native_runtime;
-- GRANT INSERT ON public.org_native_outbox_delivery TO org_native_runtime;
-- GRANT UPDATE(bank_generation,ownership_generation,current_tenant_id,current_institution_id,current_issuer_id,current_enrollment_id,current_enrollment_version,last_owner_action_id,last_owner_action_tenant_id) ON public.org_native_bank_gate TO org_native_runtime;
-- GRANT UPDATE(state,control_version,admission_action_id,last_action_id,admission_action_tenant_id,last_action_tenant_id) ON public.org_native_bank_enrollment_control TO org_native_runtime;
-- GRANT UPDATE(state,current_revision,control_version,approval_action_id,approval_until_ms,last_action_id) ON public.org_native_proposal_control TO org_native_runtime;
-- GRANT UPDATE(control_version,state,attempt_count,lease_owner,lease_until_ms,next_attempt_ms,last_error_code,last_ack_at_ms) ON public.org_native_outbox_delivery TO org_native_runtime;
-- END SOURCE SECTION 02-roles.sql

-- BEGIN SOURCE SECTION 03-wire.sql
-- Native codec SOURCE candidate only; not in Flyway and not executed.
-- Pure helpers have no cross-service table/function dependency.
-- Preserve raw json until closed-shape and lexical checks finish; jsonb would erase duplicate keys.
CREATE FUNCTION public.org_native_invalid() RETURNS VOID
LANGUAGE plpgsql IMMUTABLE SET search_path=pg_catalog,pg_temp AS $$
BEGIN RAISE EXCEPTION 'Invalid native organization enrollment intent' USING ERRCODE='22023'; END $$;

CREATE FUNCTION public.org_native_shape(p_value JSON,p_fields TEXT[]) RETURNS VOID
LANGUAGE plpgsql IMMUTABLE SET search_path=pg_catalog,pg_temp AS $$
DECLARE n BIGINT; unique_n BIGINT; actual TEXT[]; expected TEXT[];
BEGIN
    IF p_value IS NULL OR json_typeof(p_value) IS DISTINCT FROM 'object' THEN PERFORM public.org_native_invalid(); END IF;
    SELECT count(*),count(DISTINCT e.key COLLATE "C"),array_agg(e.key ORDER BY e.key COLLATE "C") INTO n,unique_n,actual FROM json_each(p_value) e;
    SELECT array_agg(f ORDER BY f COLLATE "C") INTO expected FROM unnest(p_fields) f;
    IF n<>cardinality(p_fields) OR unique_n<>n OR actual IS DISTINCT FROM expected THEN PERFORM public.org_native_invalid(); END IF;
END $$;

CREATE FUNCTION public.org_native_s(p_value TEXT) RETURNS TEXT
LANGUAGE sql IMMUTABLE STRICT SET search_path=pg_catalog,pg_temp AS $$
SELECT 'S'||octet_length(convert_to(p_value,'UTF8'))::text||':'||p_value $$;

CREATE FUNCTION public.org_native_string(p_value JSON,p_max INTEGER) RETURNS TEXT
LANGUAGE plpgsql IMMUTABLE SET search_path=pg_catalog,pg_temp AS $$
DECLARE value TEXT; units BIGINT; code INTEGER; blank BOOLEAN:=true;
BEGIN
    IF p_value IS NULL OR json_typeof(p_value) IS DISTINCT FROM 'string' THEN PERFORM public.org_native_invalid(); END IF;
    value:=p_value#>>'{}';
    IF value IS NULL OR btrim(value)='' THEN PERFORM public.org_native_invalid(); END IF;
    units:=0;
    FOR i IN 1..char_length(value) LOOP
        code:=ascii(substr(value,i,1));
        IF code BETWEEN 0 AND 31 OR code BETWEEN 127 AND 159 OR code BETWEEN 55296 AND 57343 OR code=65533 THEN PERFORM public.org_native_invalid(); END IF;
        units:=units+CASE WHEN code>65535 THEN 2 ELSE 1 END;
        IF code NOT IN (32,5760,8192,8193,8194,8195,8196,8197,8198,8200,8201,8202,8232,8233,8287,12288) THEN blank:=false; END IF;
    END LOOP;
    IF units>p_max OR blank THEN PERFORM public.org_native_invalid(); END IF;
    RETURN public.org_native_s(value);
END $$;

CREATE FUNCTION public.org_native_uuid(p_value JSON) RETURNS TEXT
LANGUAGE plpgsql IMMUTABLE SET search_path=pg_catalog,pg_temp AS $$
DECLARE value TEXT;
BEGIN
    IF p_value IS NULL OR json_typeof(p_value) IS DISTINCT FROM 'string' THEN PERFORM public.org_native_invalid(); END IF;
    value:=p_value#>>'{}';
    IF value !~ '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$' OR value='00000000-0000-0000-0000-000000000000' THEN PERFORM public.org_native_invalid(); END IF;
    RETURN public.org_native_s(value);
END $$;

CREATE FUNCTION public.org_native_long(p_value JSON,p_min BIGINT,p_max BIGINT) RETURNS TEXT
LANGUAGE plpgsql IMMUTABLE SET search_path=pg_catalog,pg_temp AS $$
DECLARE value BIGINT; raw TEXT;
BEGIN
    IF p_value IS NULL OR json_typeof(p_value) IS DISTINCT FROM 'number' THEN PERFORM public.org_native_invalid(); END IF;
    raw:=btrim(p_value::text);
    IF raw !~ '^(0|-?[1-9][0-9]*)$' THEN PERFORM public.org_native_invalid(); END IF;
    BEGIN value:=raw::bigint; EXCEPTION WHEN numeric_value_out_of_range OR invalid_text_representation THEN PERFORM public.org_native_invalid(); END;
    IF value<p_min OR value>p_max THEN PERFORM public.org_native_invalid(); END IF;
    RETURN 'I'||value::text||';';
END $$;

CREATE FUNCTION public.org_native_bool(p_value JSON) RETURNS TEXT
LANGUAGE plpgsql IMMUTABLE SET search_path=pg_catalog,pg_temp AS $$
BEGIN
    IF p_value IS NULL OR json_typeof(p_value) IS DISTINCT FROM 'boolean' THEN PERFORM public.org_native_invalid(); END IF;
    RETURN CASE WHEN p_value::text='true' THEN 'B1;' ELSE 'B0;' END;
END $$;

CREATE FUNCTION public.org_native_evidence(p_value JSON) RETURNS TEXT
LANGUAGE plpgsql IMMUTABLE SET search_path=pg_catalog,pg_temp AS $$
DECLARE result TEXT; item JSON; seen TEXT[]:=ARRAY[]::text[]; value TEXT;
BEGIN
    IF p_value IS NULL OR json_typeof(p_value) IS DISTINCT FROM 'array' OR json_array_length(p_value)>32 THEN PERFORM public.org_native_invalid(); END IF;
    result:='A'||json_array_length(p_value)::text||':';
    FOR item IN SELECT x FROM json_array_elements(p_value) x LOOP
        result:=result||public.org_native_string(item,500);value:=item#>>'{}';
        IF value=ANY(seen) THEN PERFORM public.org_native_invalid(); END IF;
        seen:=array_append(seen,value);
    END LOOP;
    RETURN result;
END $$;

CREATE FUNCTION public.org_native_uses(p_value JSON) RETURNS TEXT
LANGUAGE plpgsql IMMUTABLE SET search_path=pg_catalog,pg_temp AS $$
DECLARE result TEXT; item JSON; previous TEXT; value TEXT;
BEGIN
    IF p_value IS NULL OR json_typeof(p_value) IS DISTINCT FROM 'array' OR json_array_length(p_value) NOT BETWEEN 1 AND 3 THEN PERFORM public.org_native_invalid(); END IF;
    result:='A'||json_array_length(p_value)::text||':';
    FOR item IN SELECT x FROM json_array_elements(p_value) x LOOP
        IF json_typeof(item) IS DISTINCT FROM 'string' THEN PERFORM public.org_native_invalid(); END IF;
        value:=item#>>'{}';
        IF value NOT IN ('ACCOUNTING_BOOK','CASH_OPERATIONS','PRODUCT_ISSUANCE') OR previous IS NOT NULL AND previous COLLATE "C">=value COLLATE "C" THEN PERFORM public.org_native_invalid(); END IF;
        result:=result||public.org_native_s(value);previous:=value;
    END LOOP;
    RETURN result;
END $$;

CREATE FUNCTION public.org_native_epoch_intent(p_value JSON) RETURNS TEXT
LANGUAGE plpgsql IMMUTABLE SET search_path=pg_catalog,pg_temp AS $$
BEGIN
    PERFORM public.org_native_shape(p_value,ARRAY['tenantGeneration','statusId','statusGeneration']);
    RETURN 'R3:'||public.org_native_long(p_value->'tenantGeneration',0,9223372036854775806)||public.org_native_uuid(p_value->'statusId')||public.org_native_long(p_value->'statusGeneration',0,9223372036854775806);
END $$;

CREATE FUNCTION public.org_native_grant_v2_intent(p_tenant UUID,p_value JSON) RETURNS TEXT
LANGUAGE plpgsql IMMUTABLE SET search_path=pg_catalog,pg_temp AS $$
DECLARE grant_value JSON; grant_token TEXT; epoch_token TEXT; expected BIGINT; version BIGINT;
BEGIN
    PERFORM public.org_native_uuid(to_json(p_tenant::text));
    PERFORM public.org_native_shape(p_value,ARRAY['grant','eligibilityEpoch']);grant_value:=p_value->'grant';
    PERFORM public.org_native_shape(grant_value,ARRAY['actionId','proposalId','proposalRevision','grantId','institutionId','issuerAuthorityId','version','expectedGrantHeadVersion','uses','effectiveFromEpochMillis','effectiveToEpochMillis','reason','evidenceReferences']);
    grant_token:='R13:'||public.org_native_uuid(grant_value->'actionId')||public.org_native_uuid(grant_value->'proposalId')||public.org_native_long(grant_value->'proposalRevision',1,9223372036854775807)||public.org_native_uuid(grant_value->'grantId')||public.org_native_uuid(grant_value->'institutionId')||public.org_native_uuid(grant_value->'issuerAuthorityId')||public.org_native_long(grant_value->'version',1,9223372036854775807)||public.org_native_long(grant_value->'expectedGrantHeadVersion',0,9223372036854775806)||public.org_native_uses(grant_value->'uses')||public.org_native_long(grant_value->'effectiveFromEpochMillis',-2208988800000,253402300799999)||public.org_native_long(grant_value->'effectiveToEpochMillis',-2208988800000,253402300799999)||public.org_native_string(grant_value->'reason',500)||public.org_native_evidence(grant_value->'evidenceReferences');
    expected:=(grant_value->>'expectedGrantHeadVersion')::bigint;version:=(grant_value->>'version')::bigint;
    IF version<>expected+1 OR (grant_value->>'effectiveFromEpochMillis')::bigint>=(grant_value->>'effectiveToEpochMillis')::bigint THEN PERFORM public.org_native_invalid(); END IF;
    epoch_token:=public.org_native_epoch_intent(p_value->'eligibilityEpoch');
    RETURN public.org_native_s('FF_TENANT_INSTITUTION_GRANT_V2')||'R3:'||public.org_native_s(p_tenant::text)||public.org_native_s('GRANT_ISSUE')||'R2:'||grant_token||epoch_token;
END $$;


CREATE FUNCTION public.org_native_nullable_uuid(p_value JSON) RETURNS TEXT
LANGUAGE plpgsql IMMUTABLE SET search_path=pg_catalog,pg_temp AS $$
BEGIN
    IF p_value IS NULL THEN PERFORM public.org_native_invalid(); END IF;
    IF json_typeof(p_value)='null' THEN RETURN 'N;'; END IF;
    RETURN public.org_native_uuid(p_value);
END $$;

CREATE FUNCTION public.org_native_nullable_long(p_value JSON,p_min BIGINT,p_max BIGINT) RETURNS TEXT
LANGUAGE plpgsql IMMUTABLE SET search_path=pg_catalog,pg_temp AS $$
BEGIN
    IF p_value IS NULL THEN PERFORM public.org_native_invalid(); END IF;
    IF json_typeof(p_value)='null' THEN RETURN 'N;'; END IF;
    RETURN public.org_native_long(p_value,p_min,p_max);
END $$;

CREATE FUNCTION public.org_native_digest(p_value JSON) RETURNS TEXT
LANGUAGE plpgsql IMMUTABLE SET search_path=pg_catalog,pg_temp AS $$
BEGIN
    IF p_value IS NULL OR json_typeof(p_value) IS DISTINCT FROM 'string' OR (p_value#>>'{}') !~ '^[0-9a-f]{64}$' THEN PERFORM public.org_native_invalid(); END IF;
    RETURN public.org_native_s(p_value#>>'{}');
END $$;

CREATE FUNCTION public.org_native_texts(p_value JSON,p_min INTEGER,p_max INTEGER,p_units INTEGER) RETURNS TEXT
LANGUAGE plpgsql IMMUTABLE SET search_path=pg_catalog,pg_temp AS $$
DECLARE result TEXT; item JSON; seen TEXT[]:=ARRAY[]::TEXT[]; value TEXT;
BEGIN
    IF p_value IS NULL OR json_typeof(p_value) IS DISTINCT FROM 'array' OR json_array_length(p_value) NOT BETWEEN p_min AND p_max THEN PERFORM public.org_native_invalid(); END IF;
    result:='A'||json_array_length(p_value)::text||':';
    FOR item IN SELECT v FROM json_array_elements(p_value) v LOOP
        result:=result||public.org_native_string(item,p_units);value:=item#>>'{}';
        IF value COLLATE "C"=ANY(seen) THEN PERFORM public.org_native_invalid(); END IF;
        seen:=array_append(seen,value);
    END LOOP;
    RETURN result;
END $$;

CREATE FUNCTION public.org_native_definition_token(p_tenant UUID,p_institution UUID,p_issuer UUID,p_value JSON) RETURNS TEXT
LANGUAGE plpgsql IMMUTABLE SET search_path=pg_catalog,pg_temp AS $$
DECLARE target_intent TEXT; definition_canonical TEXT; grant_value JSON; grant_token TEXT; target_token TEXT;
BEGIN
    PERFORM public.org_native_shape(p_value,ARRAY['tenantId','target','definitionEncoding','canonicalDefinition','definitionDigest','recordedAtEpochMillis']);
    PERFORM public.org_native_uuid(p_value->'tenantId');PERFORM public.org_native_uuid(to_json(p_institution::text));PERFORM public.org_native_uuid(to_json(p_issuer::text));
    target_intent:=public.org_native_grant_v2_intent(p_tenant,p_value->'target');grant_value:=p_value->'target'->'grant';
    IF p_value->>'tenantId' IS DISTINCT FROM p_tenant::text OR grant_value->>'institutionId' IS DISTINCT FROM p_institution::text
            OR grant_value->>'issuerAuthorityId' IS DISTINCT FROM p_issuer::text OR p_value->>'definitionEncoding' IS DISTINCT FROM 'FF_TENANT_INSTITUTION_GRANT_DEFINITION_V2' THEN PERFORM public.org_native_invalid(); END IF;
    PERFORM public.org_native_long(p_value->'recordedAtEpochMillis',-2208988800000,253402300799999);
    definition_canonical:=public.org_native_s('FF_TENANT_INSTITUTION_GRANT_DEFINITION_V2')||'R3:'||public.org_native_s(p_tenant::text)||public.org_native_s(target_intent)||public.org_native_long(p_value->'recordedAtEpochMillis',-2208988800000,253402300799999);
    PERFORM public.org_native_string(p_value->'canonicalDefinition',2097152);PERFORM public.org_native_digest(p_value->'definitionDigest');
    IF p_value->>'canonicalDefinition' IS DISTINCT FROM definition_canonical OR p_value->>'definitionDigest' IS DISTINCT FROM encode(sha256(convert_to(definition_canonical,'UTF8')),'hex') THEN PERFORM public.org_native_invalid(); END IF;
    grant_token:='R13:'||public.org_native_uuid(grant_value->'actionId')||public.org_native_uuid(grant_value->'proposalId')||public.org_native_long(grant_value->'proposalRevision',1,9223372036854775807)||public.org_native_uuid(grant_value->'grantId')||public.org_native_uuid(grant_value->'institutionId')||public.org_native_uuid(grant_value->'issuerAuthorityId')||public.org_native_long(grant_value->'version',1,9223372036854775807)||public.org_native_long(grant_value->'expectedGrantHeadVersion',0,9223372036854775806)||public.org_native_uses(grant_value->'uses')||public.org_native_long(grant_value->'effectiveFromEpochMillis',-2208988800000,253402300799999)||public.org_native_long(grant_value->'effectiveToEpochMillis',-2208988800000,253402300799999)||public.org_native_string(grant_value->'reason',500)||public.org_native_evidence(grant_value->'evidenceReferences');
    target_token:='R2:'||grant_token||public.org_native_epoch_intent(p_value->'target'->'eligibilityEpoch');
    RETURN 'R6:'||public.org_native_uuid(p_value->'tenantId')||target_token||public.org_native_s(p_value->>'definitionEncoding')||public.org_native_s(definition_canonical)||public.org_native_digest(p_value->'definitionDigest')||public.org_native_long(p_value->'recordedAtEpochMillis',-2208988800000,253402300799999);
END $$;

CREATE FUNCTION public.org_native_bank_target(p_value JSON) RETURNS TEXT
LANGUAGE plpgsql IMMUTABLE SET search_path=pg_catalog,pg_temp AS $$
DECLARE mode TEXT; use_value TEXT; grant_value JSON; definition_token TEXT;
BEGIN
    PERFORM public.org_native_shape(p_value,ARRAY['schemaVersion','actionId','mode','bankId','enrollmentId','destinationTenantId','institutionId','issuerAuthorityId','use','expectedOwnershipGeneration','expectedSourceGeneration','existingEnrollmentId','expectedEnrollmentVersion','expectedEnrollmentControlVersion','grantDefinition','expectedGrantHeadVersion','expectedGrantControlVersion','commandDeadlineEpochMillis']);
    PERFORM public.org_native_long(p_value->'schemaVersion',2,2);PERFORM public.org_native_long(p_value->'expectedOwnershipGeneration',0,9223372036854775806);
    PERFORM public.org_native_string(p_value->'mode',32);PERFORM public.org_native_string(p_value->'use',32);
    mode:=p_value->>'mode';use_value:=p_value->>'use';
    IF mode NOT IN ('ENROLL','TRANSFER') OR use_value NOT IN ('ACCOUNTING_BOOK','CASH_OPERATIONS','PRODUCT_ISSUANCE') THEN PERFORM public.org_native_invalid(); END IF;
    IF mode='ENROLL' THEN
        IF (p_value->>'expectedOwnershipGeneration')::bigint<>0 OR json_typeof(p_value->'existingEnrollmentId') IS DISTINCT FROM 'null' OR json_typeof(p_value->'expectedEnrollmentVersion') IS DISTINCT FROM 'null' OR json_typeof(p_value->'expectedEnrollmentControlVersion') IS DISTINCT FROM 'null' THEN PERFORM public.org_native_invalid(); END IF;
    ELSE
        IF (p_value->>'expectedOwnershipGeneration')::bigint=0 OR json_typeof(p_value->'existingEnrollmentId') IS DISTINCT FROM 'string'
                OR json_typeof(p_value->'expectedEnrollmentVersion') IS DISTINCT FROM 'number' OR json_typeof(p_value->'expectedEnrollmentControlVersion') IS DISTINCT FROM 'number'
                OR p_value->>'existingEnrollmentId' IS NOT DISTINCT FROM p_value->>'enrollmentId' THEN PERFORM public.org_native_invalid(); END IF;
    END IF;
    definition_token:=public.org_native_definition_token((p_value->>'destinationTenantId')::uuid,(p_value->>'institutionId')::uuid,(p_value->>'issuerAuthorityId')::uuid,p_value->'grantDefinition');grant_value:=p_value->'grantDefinition'->'target'->'grant';
    PERFORM public.org_native_long(p_value->'expectedGrantHeadVersion',1,9223372036854775807);
    IF (p_value->>'expectedGrantHeadVersion')::bigint<>(grant_value->>'version')::bigint OR NOT EXISTS(SELECT 1 FROM json_array_elements_text(grant_value->'uses') v WHERE v=use_value) THEN PERFORM public.org_native_invalid(); END IF;
    RETURN public.org_native_s('FF_ORGANIZATION_BANK_ENROLLMENT_TARGET_V2')||'R18:'||public.org_native_long(p_value->'schemaVersion',2,2)||public.org_native_uuid(p_value->'actionId')||public.org_native_s(mode)||public.org_native_uuid(p_value->'bankId')||public.org_native_uuid(p_value->'enrollmentId')||public.org_native_uuid(p_value->'destinationTenantId')||public.org_native_uuid(p_value->'institutionId')||public.org_native_uuid(p_value->'issuerAuthorityId')||public.org_native_s(use_value)||public.org_native_long(p_value->'expectedOwnershipGeneration',0,9223372036854775806)||public.org_native_long(p_value->'expectedSourceGeneration',0,9223372036854775806)||public.org_native_nullable_uuid(p_value->'existingEnrollmentId')||public.org_native_nullable_long(p_value->'expectedEnrollmentVersion',1,9223372036854775806)||public.org_native_nullable_long(p_value->'expectedEnrollmentControlVersion',0,9223372036854775806)||definition_token||public.org_native_long(p_value->'expectedGrantHeadVersion',1,9223372036854775807)||public.org_native_long(p_value->'expectedGrantControlVersion',0,9223372036854775806)||public.org_native_long(p_value->'commandDeadlineEpochMillis',1,253402300799999);
END $$;

CREATE FUNCTION public.org_native_owner_token(p_value JSON) RETURNS TEXT
LANGUAGE plpgsql IMMUTABLE SET search_path=pg_catalog,pg_temp AS $$
BEGIN
    IF p_value IS NULL THEN PERFORM public.org_native_invalid(); END IF;
    IF json_typeof(p_value)='null' THEN RETURN 'N;'; END IF;
    PERFORM public.org_native_shape(p_value,ARRAY['tenantId','institutionId','issuerAuthorityId','enrollmentId','version','controlVersion']);
    RETURN 'R6:'||public.org_native_uuid(p_value->'tenantId')||public.org_native_uuid(p_value->'institutionId')||public.org_native_uuid(p_value->'issuerAuthorityId')||public.org_native_uuid(p_value->'enrollmentId')||public.org_native_long(p_value->'version',1,9223372036854775806)||public.org_native_long(p_value->'controlVersion',0,9223372036854775806);
END $$;

CREATE FUNCTION public.org_native_revision(p_value JSON) RETURNS TEXT
LANGUAGE plpgsql IMMUTABLE SET search_path=pg_catalog,pg_temp AS $$
DECLARE target_value JSON; original_target TEXT; target_canonical TEXT; previous JSON; uses_token TEXT; refs_token TEXT; item TEXT;
BEGIN
    PERFORM public.org_native_shape(p_value,ARRAY['schemaVersion','proposalId','revision','expectedBankGeneration','previousOwner','originalEnrollmentTargetJson','canonicalEnrollmentTarget','enrollmentTargetDigest','reason','allowedUses','evidenceReferences']);
    PERFORM public.org_native_long(p_value->'schemaVersion',2,2);PERFORM public.org_native_uuid(p_value->'proposalId');PERFORM public.org_native_long(p_value->'revision',1,9223372036854775806);PERFORM public.org_native_long(p_value->'expectedBankGeneration',0,9223372036854775806);
    IF json_typeof(p_value->'originalEnrollmentTargetJson') IS DISTINCT FROM 'string' THEN PERFORM public.org_native_invalid(); END IF;
    original_target:=p_value->>'originalEnrollmentTargetJson';IF octet_length(convert_to(original_target,'UTF8')) NOT BETWEEN 1 AND 2097152 THEN PERFORM public.org_native_invalid(); END IF;
    target_value:=original_target::json;target_canonical:=public.org_native_bank_target(target_value);
    PERFORM public.org_native_string(p_value->'canonicalEnrollmentTarget',2097152);PERFORM public.org_native_digest(p_value->'enrollmentTargetDigest');
    IF p_value->>'canonicalEnrollmentTarget' IS DISTINCT FROM target_canonical OR p_value->>'enrollmentTargetDigest' IS DISTINCT FROM encode(sha256(convert_to(target_canonical,'UTF8')),'hex')
            OR (p_value->>'expectedBankGeneration')::bigint<(target_value->>'expectedOwnershipGeneration')::bigint THEN PERFORM public.org_native_invalid(); END IF;
    previous:=p_value->'previousOwner';PERFORM public.org_native_owner_token(previous);
    IF target_value->>'mode'='ENROLL' THEN
        IF json_typeof(previous) IS DISTINCT FROM 'null' THEN PERFORM public.org_native_invalid(); END IF;
    ELSE
        IF json_typeof(previous) IS DISTINCT FROM 'object' OR previous->>'enrollmentId' IS DISTINCT FROM target_value->>'existingEnrollmentId'
                OR (previous->>'version')::bigint IS DISTINCT FROM (target_value->>'expectedEnrollmentVersion')::bigint
                OR (previous->>'controlVersion')::bigint IS DISTINCT FROM (target_value->>'expectedEnrollmentControlVersion')::bigint THEN PERFORM public.org_native_invalid(); END IF;
    END IF;
    uses_token:=public.org_native_uses(p_value->'allowedUses');refs_token:=public.org_native_texts(p_value->'evidenceReferences',1,32,512);
    IF NOT EXISTS(SELECT 1 FROM json_array_elements_text(p_value->'allowedUses') v WHERE v=target_value->>'use') THEN PERFORM public.org_native_invalid(); END IF;
    FOR item IN SELECT v FROM json_array_elements_text(p_value->'allowedUses') v LOOP
        IF NOT EXISTS(SELECT 1 FROM json_array_elements_text(target_value->'grantDefinition'->'target'->'grant'->'uses') v WHERE v=item) THEN PERFORM public.org_native_invalid(); END IF;
    END LOOP;
    RETURN public.org_native_s('FF_ORGANIZATION_BANK_ENROLLMENT_REVISION_V2')||'R8:'||public.org_native_uuid(p_value->'proposalId')||public.org_native_long(p_value->'revision',1,9223372036854775806)||public.org_native_long(p_value->'expectedBankGeneration',0,9223372036854775806)||public.org_native_owner_token(previous)||public.org_native_s(target_canonical)||public.org_native_string(p_value->'reason',4096)||uses_token||refs_token;
END $$;

CREATE FUNCTION public.org_native_command(p_operation TEXT,p_revision BIGINT,p_value JSON,p_now BIGINT) RETURNS TEXT
LANGUAGE plpgsql IMMUTABLE SET search_path=pg_catalog,pg_temp AS $$
BEGIN
    PERFORM public.org_native_shape(p_value,ARRAY['schemaVersion','actionId','operation','expectedRevision','expectedControlVersion','expectedBankGeneration','expectedTargetDigest','commandNotAfterEpochMillis','approvalUntilEpochMillis','reason']);
    IF p_operation IS NULL OR p_operation NOT IN ('SUBMIT','APPROVE','REJECT','WITHDRAW','CANCEL','CANCEL_EMERGENCY') OR p_revision IS NULL OR p_revision<=0 OR p_revision=9223372036854775807 OR p_now IS NULL OR p_now<=0 THEN PERFORM public.org_native_invalid(); END IF;
    PERFORM public.org_native_string(p_value->'operation',32);PERFORM public.org_native_long(p_value->'commandNotAfterEpochMillis',1,253402300799999);
    IF p_value->>'operation' IS DISTINCT FROM p_operation OR (p_value->>'expectedRevision')::bigint IS DISTINCT FROM p_revision OR (p_value->>'commandNotAfterEpochMillis')::bigint<=p_now THEN PERFORM public.org_native_invalid(); END IF;
    IF p_operation='APPROVE' THEN
        IF json_typeof(p_value->'approvalUntilEpochMillis') IS DISTINCT FROM 'number' THEN PERFORM public.org_native_invalid(); END IF;
        PERFORM public.org_native_long(p_value->'approvalUntilEpochMillis',1,253402300799999);
        IF (p_value->>'approvalUntilEpochMillis')::bigint<=p_now THEN PERFORM public.org_native_invalid(); END IF;
    ELSIF json_typeof(p_value->'approvalUntilEpochMillis') IS DISTINCT FROM 'null' THEN PERFORM public.org_native_invalid(); END IF;
    RETURN 'R10:'||public.org_native_long(p_value->'schemaVersion',2,2)||public.org_native_uuid(p_value->'actionId')||public.org_native_s(p_operation)||public.org_native_long(p_value->'expectedRevision',1,9223372036854775806)||public.org_native_long(p_value->'expectedControlVersion',0,9223372036854775806)||public.org_native_long(p_value->'expectedBankGeneration',0,9223372036854775806)||public.org_native_digest(p_value->'expectedTargetDigest')||public.org_native_long(p_value->'commandNotAfterEpochMillis',1,253402300799999)||public.org_native_nullable_long(p_value->'approvalUntilEpochMillis',1,253402300799999)||public.org_native_string(p_value->'reason',4096);
END $$;

CREATE FUNCTION public.org_native_full_command(p_tenant UUID,p_bank UUID,p_proposal UUID,p_revision JSON,p_operation TEXT,p_command JSON,p_now BIGINT) RETURNS TEXT
LANGUAGE plpgsql IMMUTABLE SET search_path=pg_catalog,pg_temp AS $$
DECLARE revision_canonical TEXT; command_token TEXT; target_value JSON;
BEGIN
    PERFORM public.org_native_uuid(to_json(p_tenant::text));PERFORM public.org_native_uuid(to_json(p_bank::text));PERFORM public.org_native_uuid(to_json(p_proposal::text));
    revision_canonical:=public.org_native_revision(p_revision);target_value:=(p_revision->>'originalEnrollmentTargetJson')::json;
    command_token:=public.org_native_command(p_operation,(p_revision->>'revision')::bigint,p_command,p_now);
    IF target_value->>'destinationTenantId' IS DISTINCT FROM p_tenant::text OR target_value->>'bankId' IS DISTINCT FROM p_bank::text OR p_revision->>'proposalId' IS DISTINCT FROM p_proposal::text
            OR p_command->>'expectedTargetDigest' IS DISTINCT FROM encode(sha256(convert_to(revision_canonical,'UTF8')),'hex')
            OR (p_command->>'expectedBankGeneration')::bigint IS DISTINCT FROM (p_revision->>'expectedBankGeneration')::bigint OR p_command->>'actionId' IS NOT DISTINCT FROM target_value->>'actionId' THEN PERFORM public.org_native_invalid(); END IF;
    IF p_operation IN ('SUBMIT','APPROVE') AND ((p_command->>'commandNotAfterEpochMillis')::bigint>(target_value->>'commandDeadlineEpochMillis')::bigint
            OR json_typeof(p_command->'approvalUntilEpochMillis')<>'null' AND (p_command->>'approvalUntilEpochMillis')::bigint>(target_value->>'commandDeadlineEpochMillis')::bigint) THEN PERFORM public.org_native_invalid(); END IF;
    RETURN public.org_native_s('FF_ORGANIZATION_BANK_ENROLLMENT_REVISION_COMMAND_V2')||'R6:'||public.org_native_s(p_tenant::text)||public.org_native_s(p_bank::text)||public.org_native_s(p_proposal::text)||public.org_native_long(p_revision->'revision',1,9223372036854775806)||public.org_native_s(revision_canonical)||command_token;
END $$;
REVOKE ALL ON FUNCTION public.org_native_invalid() FROM PUBLIC;
REVOKE ALL ON FUNCTION public.org_native_shape(JSON,TEXT[]) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.org_native_s(TEXT) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.org_native_string(JSON,INTEGER) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.org_native_uuid(JSON) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.org_native_long(JSON,BIGINT,BIGINT) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.org_native_bool(JSON) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.org_native_evidence(JSON) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.org_native_uses(JSON) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.org_native_epoch_intent(JSON) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.org_native_grant_v2_intent(UUID,JSON) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.org_native_nullable_uuid(JSON) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.org_native_nullable_long(JSON,BIGINT,BIGINT) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.org_native_digest(JSON) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.org_native_texts(JSON,INTEGER,INTEGER,INTEGER) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.org_native_definition_token(UUID,UUID,UUID,JSON) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.org_native_bank_target(JSON) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.org_native_owner_token(JSON) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.org_native_revision(JSON) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.org_native_command(TEXT,BIGINT,JSON,BIGINT) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.org_native_full_command(UUID,UUID,UUID,JSON,TEXT,JSON,BIGINT) FROM PUBLIC;
-- END SOURCE SECTION 03-wire.sql

-- BEGIN SOURCE SECTION 04-observation-wire.sql
-- Native observation codec SOURCE candidate only. Not in Flyway; never executed.
CREATE FUNCTION public.org_native_actor_token(p_value JSON) RETURNS TEXT
LANGUAGE plpgsql IMMUTABLE SET search_path=pg_catalog,pg_temp AS $$
DECLARE kind_value TEXT; realm_token TEXT; staff_token TEXT; client_token TEXT;
BEGIN
    PERFORM public.org_native_shape(p_value,ARRAY['kind','issuer','subject','tenantId','staffRealm','staffActorId','clientId','expiresAtEpochMillis']);
    PERFORM public.org_native_string(p_value->'kind',16);kind_value:=p_value->>'kind';
    IF kind_value='STAFF' THEN
        realm_token:=public.org_native_string(p_value->'staffRealm',255);staff_token:=public.org_native_uuid(p_value->'staffActorId');
        IF json_typeof(p_value->'clientId')='null' THEN client_token:='N;';ELSE client_token:=public.org_native_string(p_value->'clientId',255);END IF;
    ELSIF kind_value='MACHINE' THEN
        IF json_typeof(p_value->'staffRealm') IS DISTINCT FROM 'null' OR json_typeof(p_value->'staffActorId') IS DISTINCT FROM 'null' THEN PERFORM public.org_native_invalid(); END IF;
        realm_token:='N;';staff_token:='N;';client_token:=public.org_native_string(p_value->'clientId',255);
    ELSE PERFORM public.org_native_invalid(); END IF;
    RETURN 'R8:'||public.org_native_s(kind_value)||public.org_native_string(p_value->'issuer',500)||public.org_native_string(p_value->'subject',255)||public.org_native_uuid(p_value->'tenantId')||realm_token||staff_token||client_token||public.org_native_long(p_value->'expiresAtEpochMillis',1,253402300799999);
END $$;

CREATE FUNCTION public.org_native_observation_token(p_tenant UUID,p_institution UUID,p_issuer UUID,p_value JSON) RETURNS TEXT
LANGUAGE plpgsql IMMUTABLE SET search_path=pg_catalog,pg_temp AS $$
DECLARE definition_token TEXT; control_token TEXT; eligibility_token TEXT; eligibility JSON; control_value JSON; grant_value JSON; epoch_value JSON; use_value TEXT;
BEGIN
    PERFORM public.org_native_shape(p_value,ARRAY['tenantId','institutionId','issuerAuthorityId','requestedUse','state','definition','control','headVersion','tenantEligibility','observedAtEpochMillis','validUntilEpochMillis','denialReason']);
    PERFORM public.org_native_uuid(p_value->'tenantId');PERFORM public.org_native_uuid(p_value->'institutionId');PERFORM public.org_native_uuid(p_value->'issuerAuthorityId');
    PERFORM public.org_native_string(p_value->'requestedUse',32);use_value:=p_value->>'requestedUse';
    IF p_value->>'tenantId' IS DISTINCT FROM p_tenant::text OR p_value->>'institutionId' IS DISTINCT FROM p_institution::text OR p_value->>'issuerAuthorityId' IS DISTINCT FROM p_issuer::text
            OR use_value NOT IN ('ACCOUNTING_BOOK','CASH_OPERATIONS','PRODUCT_ISSUANCE') OR p_value->>'state' IS DISTINCT FROM 'CURRENT_AUTHORIZED'
            OR json_typeof(p_value->'denialReason') IS DISTINCT FROM 'null' THEN PERFORM public.org_native_invalid(); END IF;
    definition_token:=public.org_native_definition_token(p_tenant,p_institution,p_issuer,p_value->'definition');
    control_value:=p_value->'control';PERFORM public.org_native_shape(control_value,ARRAY['state','controlVersion','lastActionId']);
    IF control_value->>'state' IS DISTINCT FROM 'ACTIVE' THEN PERFORM public.org_native_invalid(); END IF;
    control_token:='R3:'||public.org_native_s('ACTIVE')||public.org_native_long(control_value->'controlVersion',0,9223372036854775807)||public.org_native_uuid(control_value->'lastActionId');
    eligibility:=p_value->'tenantEligibility';PERFORM public.org_native_shape(eligibility,ARRAY['tenantId','tenantGeneration','statusId','statusGeneration','tenantActive','statusEnabled','statusCode','eligibleNow','lastActionId']);
    PERFORM public.org_native_uuid(eligibility->'tenantId');PERFORM public.org_native_long(eligibility->'tenantGeneration',0,9223372036854775806);PERFORM public.org_native_uuid(eligibility->'statusId');PERFORM public.org_native_long(eligibility->'statusGeneration',0,9223372036854775806);
    IF eligibility->>'tenantId' IS DISTINCT FROM p_tenant::text OR eligibility->>'statusCode' IS DISTINCT FROM 'ACTIVE' OR public.org_native_bool(eligibility->'tenantActive')<>'B1;'
            OR public.org_native_bool(eligibility->'statusEnabled')<>'B1;' OR public.org_native_bool(eligibility->'eligibleNow')<>'B1;' THEN PERFORM public.org_native_invalid(); END IF;
    eligibility_token:='R9:'||public.org_native_uuid(eligibility->'tenantId')||public.org_native_long(eligibility->'tenantGeneration',0,9223372036854775806)||public.org_native_uuid(eligibility->'statusId')||public.org_native_long(eligibility->'statusGeneration',0,9223372036854775806)||public.org_native_bool(eligibility->'tenantActive')||public.org_native_bool(eligibility->'statusEnabled')||public.org_native_string(eligibility->'statusCode',500)||public.org_native_bool(eligibility->'eligibleNow')||public.org_native_nullable_uuid(eligibility->'lastActionId');
    PERFORM public.org_native_long(p_value->'headVersion',0,9223372036854775807);PERFORM public.org_native_long(p_value->'observedAtEpochMillis',-2208988800000,253402300799999);PERFORM public.org_native_long(p_value->'validUntilEpochMillis',-2208988800000,253402300799999);
    grant_value:=p_value->'definition'->'target'->'grant';epoch_value:=p_value->'definition'->'target'->'eligibilityEpoch';
    IF (p_value->>'headVersion')::bigint<>(grant_value->>'version')::bigint OR NOT EXISTS(SELECT 1 FROM json_array_elements_text(grant_value->'uses') v WHERE v=use_value)
            OR (epoch_value->>'tenantGeneration')::bigint<>(eligibility->>'tenantGeneration')::bigint OR epoch_value->>'statusId' IS DISTINCT FROM eligibility->>'statusId' OR (epoch_value->>'statusGeneration')::bigint<>(eligibility->>'statusGeneration')::bigint
            OR (p_value->>'observedAtEpochMillis')::bigint<(grant_value->>'effectiveFromEpochMillis')::bigint OR (p_value->>'observedAtEpochMillis')::bigint>=(grant_value->>'effectiveToEpochMillis')::bigint
            OR (p_value->>'validUntilEpochMillis')::bigint<=(p_value->>'observedAtEpochMillis')::bigint OR (p_value->>'validUntilEpochMillis')::bigint>LEAST((p_value->>'observedAtEpochMillis')::bigint+30000,(grant_value->>'effectiveToEpochMillis')::bigint) THEN PERFORM public.org_native_invalid(); END IF;
    RETURN 'R12:'||public.org_native_uuid(p_value->'tenantId')||public.org_native_uuid(p_value->'institutionId')||public.org_native_uuid(p_value->'issuerAuthorityId')||public.org_native_s(use_value)||public.org_native_s('CURRENT_AUTHORIZED')||definition_token||control_token||public.org_native_long(p_value->'headVersion',0,9223372036854775807)||eligibility_token||public.org_native_long(p_value->'observedAtEpochMillis',-2208988800000,253402300799999)||public.org_native_long(p_value->'validUntilEpochMillis',-2208988800000,253402300799999)||'N;';
END $$;

CREATE FUNCTION public.org_native_observed_packets(p_revision JSON,p_evidence JSON,p_now BIGINT)
RETURNS TABLE(canonical_observations TEXT,valid_until_ms BIGINT)
LANGUAGE plpgsql IMMUTABLE SET search_path=pg_catalog,pg_temp AS $$
DECLARE target_value JSON; evidence JSON; observed JSON; original_observed JSON; original_text TEXT; observer JSON;
    p_tenant UUID; p_institution UUID; p_issuer UUID; definition_token TEXT; observation_token TEXT; observer_token TEXT; expected_definition_token TEXT;
    use_value TEXT; seen TEXT[]:=ARRAY[]::TEXT[]; received BIGINT; usable BIGINT; credential_expiry BIGINT; verified BIGINT;
BEGIN
    PERFORM public.org_native_revision(p_revision);target_value:=(p_revision->>'originalEnrollmentTargetJson')::json;
    IF p_now IS NULL OR p_now<=0 OR p_evidence IS NULL OR json_typeof(p_evidence) IS DISTINCT FROM 'array'
            OR json_array_length(p_evidence)<>json_array_length(p_revision->'allowedUses') THEN PERFORM public.org_native_invalid(); END IF;
    p_tenant:=(target_value->>'destinationTenantId')::uuid;p_institution:=(target_value->>'institutionId')::uuid;p_issuer:=(target_value->>'issuerAuthorityId')::uuid;
    expected_definition_token:=public.org_native_definition_token(p_tenant,p_institution,p_issuer,target_value->'grantDefinition');
    valid_until_ms:=(target_value->>'commandDeadlineEpochMillis')::bigint;IF valid_until_ms<=p_now THEN PERFORM public.org_native_invalid(); END IF;
    canonical_observations:='I'||json_array_length(p_evidence)::text||';';
    FOR evidence IN SELECT v FROM json_array_elements(p_evidence) v LOOP
        PERFORM public.org_native_shape(evidence,ARRAY['originalObservationJson','observation','observer','receivedAtEpochMillis','usableUntilEpochMillis']);
        observed:=evidence->'observation';observation_token:=public.org_native_observation_token(p_tenant,p_institution,p_issuer,observed);
        use_value:=observed->>'requestedUse';
        IF use_value=ANY(seen) OR NOT EXISTS(SELECT 1 FROM json_array_elements_text(p_revision->'allowedUses') v WHERE v=use_value) THEN PERFORM public.org_native_invalid(); END IF;seen:=array_append(seen,use_value);
        definition_token:=public.org_native_definition_token(p_tenant,p_institution,p_issuer,observed->'definition');
        IF definition_token IS DISTINCT FROM expected_definition_token OR (observed->>'headVersion')::bigint<>(target_value->>'expectedGrantHeadVersion')::bigint OR (observed->'control'->>'controlVersion')::bigint<>(target_value->>'expectedGrantControlVersion')::bigint THEN PERFORM public.org_native_invalid(); END IF;
        IF json_typeof(evidence->'originalObservationJson') IS DISTINCT FROM 'string' THEN PERFORM public.org_native_invalid(); END IF;
        original_text:=evidence->>'originalObservationJson';IF octet_length(convert_to(original_text,'UTF8')) NOT BETWEEN 1 AND 4194304 THEN PERFORM public.org_native_invalid(); END IF;
        original_observed:=original_text::json;
        IF public.org_native_observation_token(p_tenant,p_institution,p_issuer,original_observed) IS DISTINCT FROM observation_token THEN PERFORM public.org_native_invalid(); END IF;
        observer:=evidence->'observer';PERFORM public.org_native_shape(observer,ARRAY['issuer','subject','clientId','tenantId','audience','verifiedAtEpochMillis','credentialExpiresAtEpochMillis']);
        PERFORM public.org_native_uuid(observer->'tenantId');PERFORM public.org_native_long(observer->'verifiedAtEpochMillis',1,253402300799999);PERFORM public.org_native_long(observer->'credentialExpiresAtEpochMillis',1,253402300799999);
        observer_token:='R7:'||public.org_native_string(observer->'issuer',500)||public.org_native_string(observer->'subject',255)||public.org_native_string(observer->'clientId',255)||public.org_native_uuid(observer->'tenantId')||public.org_native_string(observer->'audience',500)||public.org_native_long(observer->'verifiedAtEpochMillis',1,253402300799999)||public.org_native_long(observer->'credentialExpiresAtEpochMillis',1,253402300799999);
        PERFORM public.org_native_long(evidence->'receivedAtEpochMillis',1,253402300799999);PERFORM public.org_native_long(evidence->'usableUntilEpochMillis',1,253402300799999);
        received:=(evidence->>'receivedAtEpochMillis')::bigint;usable:=(evidence->>'usableUntilEpochMillis')::bigint;credential_expiry:=(observer->>'credentialExpiresAtEpochMillis')::bigint;verified:=(observer->>'verifiedAtEpochMillis')::bigint;
        IF observer->>'tenantId' IS DISTINCT FROM p_tenant::text OR usable<=received OR usable>credential_expiry OR usable>(observed->>'validUntilEpochMillis')::bigint OR usable>received+30000
                OR p_now<received OR p_now>=usable OR p_now>=(observed->'definition'->'target'->'grant'->>'effectiveToEpochMillis')::bigint OR p_now<(observed->'definition'->'target'->'grant'->>'effectiveFromEpochMillis')::bigint
                OR verified>received OR verified>=credential_expiry THEN PERFORM public.org_native_invalid(); END IF;
        valid_until_ms:=LEAST(valid_until_ms,usable);
        canonical_observations:=canonical_observations||'R4:'||observation_token||observer_token||public.org_native_long(evidence->'receivedAtEpochMillis',1,253402300799999)||public.org_native_long(evidence->'usableUntilEpochMillis',1,253402300799999);
    END LOOP;
    RETURN NEXT;
END $$;
REVOKE ALL ON FUNCTION public.org_native_actor_token(JSON) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.org_native_observation_token(UUID,UUID,UUID,JSON) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.org_native_observed_packets(JSON,JSON,BIGINT) FROM PUBLIC;
-- END SOURCE SECTION 04-observation-wire.sql

-- BEGIN SOURCE SECTION 11-preparation-wire.sql
-- Additive PREPARE wire source. No runtime grants, table changes or execution approval.
CREATE FUNCTION public.org_native_preparation_desired(p_tenant UUID,p_bank UUID,p_mode TEXT,p_value JSON,p_now BIGINT) RETURNS TEXT
LANGUAGE plpgsql IMMUTABLE SET search_path=pg_catalog,pg_temp AS $$
DECLARE request_token TEXT; until_ms BIGINT;
BEGIN
    PERFORM public.org_native_uuid(to_json(p_tenant::text));PERFORM public.org_native_uuid(to_json(p_bank::text));
    IF p_mode IS NULL OR p_mode NOT IN ('ENROLL','TRANSFER') OR p_now IS NULL OR p_now<=0 OR p_now>253402300799999 THEN PERFORM public.org_native_invalid(); END IF;
    PERFORM public.org_native_shape(p_value,ARRAY['schemaVersion','actionId','mode','bankId','destinationTenantId','institutionId','issuerAuthorityId','allowedUses','notAfterEpochMillis','reason','evidenceReferences']);
    request_token:='R11:'||public.org_native_long(p_value->'schemaVersion',2,2)||public.org_native_uuid(p_value->'actionId')||public.org_native_string(p_value->'mode',32)
        ||public.org_native_uuid(p_value->'bankId')||public.org_native_uuid(p_value->'destinationTenantId')||public.org_native_uuid(p_value->'institutionId')||public.org_native_uuid(p_value->'issuerAuthorityId')
        ||public.org_native_uses(p_value->'allowedUses')||public.org_native_long(p_value->'notAfterEpochMillis',1,253402300799999)||public.org_native_string(p_value->'reason',4096)||public.org_native_texts(p_value->'evidenceReferences',1,32,512);
    until_ms:=(p_value->>'notAfterEpochMillis')::bigint;
    IF p_value->>'mode' IS DISTINCT FROM p_mode OR p_value->>'bankId' IS DISTINCT FROM p_bank::text OR p_value->>'destinationTenantId' IS DISTINCT FROM p_tenant::text
            OR until_ms<=p_now OR until_ms>p_now+86400000 THEN PERFORM public.org_native_invalid(); END IF;
    RETURN public.org_native_s('FF_ORGANIZATION_BANK_PREPARATION_REQUEST_V2')||'R4:'||public.org_native_s(p_tenant::text)||public.org_native_s(p_bank::text)||public.org_native_s(p_mode)||request_token;
END $$;

CREATE FUNCTION public.org_native_preparation_command(p_tenant UUID,p_bank UUID,p_action UUID,p_operation TEXT,p_value JSON,p_now BIGINT) RETURNS JSONB
LANGUAGE plpgsql IMMUTABLE SET search_path=pg_catalog,pg_temp AS $$
DECLARE desired JSON; revision_value JSON; target_value JSON; desired_canonical TEXT; desired_digest TEXT; revision_canonical TEXT; revision_digest TEXT;
    mode_value TEXT; expected_operation TEXT; route_value TEXT; canonical TEXT; n INTEGER;
BEGIN
    PERFORM public.org_native_uuid(to_json(p_action::text));
    PERFORM public.org_native_shape(p_value,ARRAY['schemaVersion','tenantId','bankId','intendedRoute','originalDesiredJson','canonicalDesiredRequest','desiredRequestDigest','originalRevisionJson','canonicalRevision','revisionDigest']);
    PERFORM public.org_native_long(p_value->'schemaVersion',2,2);PERFORM public.org_native_uuid(p_value->'tenantId');PERFORM public.org_native_uuid(p_value->'bankId');
    IF json_typeof(p_value->'originalDesiredJson') IS DISTINCT FROM 'string' OR octet_length(convert_to(p_value->>'originalDesiredJson','UTF8')) NOT BETWEEN 1 AND 131072
            OR json_typeof(p_value->'originalRevisionJson') IS DISTINCT FROM 'string' OR octet_length(convert_to(p_value->>'originalRevisionJson','UTF8')) NOT BETWEEN 1 AND 4194304 THEN PERFORM public.org_native_invalid(); END IF;
    desired:=(p_value->>'originalDesiredJson')::json;revision_value:=(p_value->>'originalRevisionJson')::json;mode_value:=desired->>'mode';
    desired_canonical:=public.org_native_preparation_desired(p_tenant,p_bank,mode_value,desired,p_now);desired_digest:=encode(sha256(convert_to(desired_canonical,'UTF8')),'hex');
    revision_canonical:=public.org_native_revision(revision_value);revision_digest:=encode(sha256(convert_to(revision_canonical,'UTF8')),'hex');
    target_value:=(revision_value->>'originalEnrollmentTargetJson')::json;
    expected_operation:=CASE mode_value WHEN 'ENROLL' THEN 'PREPARE' WHEN 'TRANSFER' THEN 'TRANSFER_PREPARE' ELSE NULL END;
    route_value:='POST /api/v1/platform/tenants/'||p_tenant::text||'/banks/'||p_bank::text||'/enrollment-proposals';
    PERFORM public.org_native_string(p_value->'intendedRoute',2000);PERFORM public.org_native_string(p_value->'canonicalDesiredRequest',2097152);PERFORM public.org_native_digest(p_value->'desiredRequestDigest');
    PERFORM public.org_native_string(p_value->'canonicalRevision',2097152);PERFORM public.org_native_digest(p_value->'revisionDigest');
    IF p_operation IS DISTINCT FROM expected_operation OR p_value->>'tenantId' IS DISTINCT FROM p_tenant::text OR p_value->>'bankId' IS DISTINCT FROM p_bank::text
            OR p_value->>'intendedRoute' IS DISTINCT FROM route_value OR desired->>'actionId' IS DISTINCT FROM p_action::text
            OR p_value->>'canonicalDesiredRequest' IS DISTINCT FROM desired_canonical OR p_value->>'desiredRequestDigest' IS DISTINCT FROM desired_digest
            OR p_value->>'canonicalRevision' IS DISTINCT FROM revision_canonical OR p_value->>'revisionDigest' IS DISTINCT FROM revision_digest
            OR (revision_value->>'revision')::bigint<>1 OR target_value->>'mode' IS DISTINCT FROM mode_value OR target_value->>'bankId' IS DISTINCT FROM p_bank::text
            OR target_value->>'destinationTenantId' IS DISTINCT FROM p_tenant::text OR target_value->>'institutionId' IS DISTINCT FROM desired->>'institutionId'
            OR target_value->>'issuerAuthorityId' IS DISTINCT FROM desired->>'issuerAuthorityId' OR (target_value->>'commandDeadlineEpochMillis')::bigint IS DISTINCT FROM (desired->>'notAfterEpochMillis')::bigint
            OR revision_value->>'reason' IS DISTINCT FROM desired->>'reason' OR (revision_value->'allowedUses')::jsonb IS DISTINCT FROM (desired->'allowedUses')::jsonb
            OR (revision_value->'evidenceReferences')::jsonb IS DISTINCT FROM (desired->'evidenceReferences')::jsonb THEN PERFORM public.org_native_invalid(); END IF;
    SELECT count(DISTINCT value) INTO n FROM unnest(ARRAY[p_action::text,revision_value->>'proposalId',target_value->>'enrollmentId',target_value->>'actionId']) value;
    IF n<>4 THEN PERFORM public.org_native_invalid(); END IF;
    canonical:=public.org_native_s('FF_ORGANIZATION_BANK_PREPARE_COMMAND_V2')||'R8:'||public.org_native_long(p_value->'schemaVersion',2,2)||public.org_native_s(p_tenant::text)||public.org_native_s(p_bank::text)||public.org_native_s(route_value)
        ||public.org_native_s(desired_canonical)||public.org_native_s(desired_digest)||public.org_native_s(revision_canonical)||public.org_native_s(revision_digest);
    RETURN jsonb_build_object('canonical',canonical,'digest',encode(sha256(convert_to(canonical,'UTF8')),'hex'),'desired',desired::jsonb,'revision',revision_value::jsonb,'route',route_value);
END $$;

CREATE FUNCTION public.org_native_preparation_context_packet(p_nonce UUID,p_original TEXT,p_purpose TEXT,p_now BIGINT) RETURNS JSONB
LANGUAGE plpgsql IMMUTABLE SET search_path=pg_catalog,pg_temp AS $$
DECLARE packet JSON; target_value JSON; actor_value JSON; desired JSON; command_value JSON; command_checked JSONB; revision_value JSON;
    operation_value TEXT; permission_value TEXT; mode_value TEXT; encoding_value TEXT; actor_token TEXT; canonical TEXT; request_canonical TEXT; request_digest TEXT;
    tenant_value UUID; bank_value UUID; action_value UUID; proposal_value UUID; revision_number BIGINT; authority_until BIGINT; command_until BIGINT; observed_until BIGINT; observed RECORD;
    evidence JSON; eligibility JSONB; first_eligibility JSONB;
BEGIN
    PERFORM public.org_native_uuid(to_json(p_nonce::text));
    IF p_original IS NULL OR p_now IS NULL OR p_now<=0 OR p_now>253402300799999 OR octet_length(convert_to(p_original,'UTF8')) NOT BETWEEN 1 AND 4194304
            OR p_purpose IS NULL OR p_purpose NOT IN ('PREPARATION_DISCOVERY','PREPARATION_MUTATION') THEN PERFORM public.org_native_invalid(); END IF;
    packet:=p_original::json;target_value:=packet->'target';encoding_value:=packet->>'contextEncoding';
    IF p_purpose='PREPARATION_DISCOVERY' THEN
        PERFORM public.org_native_shape(packet,ARRAY['target','contextEncoding','canonicalContext','contextDigest']);
        PERFORM public.org_native_shape(target_value,ARRAY['schemaVersion','nonce','purpose','operation','permission','tenantId','bankId','actionId','actor','authorityUntilEpochMillis','commandUntilEpochMillis','originalDesiredJson','canonicalDesiredRequest','desiredRequestDigest']);
    ELSE
        PERFORM public.org_native_shape(packet,ARRAY['target','observations','observationsUntilEpochMillis','contextEncoding','canonicalContext','contextDigest']);
        PERFORM public.org_native_shape(target_value,ARRAY['schemaVersion','nonce','purpose','operation','intendedRoute','permission','tenantId','bankId','proposalId','revision','actionId','actor','authorityUntilEpochMillis','commandUntilEpochMillis','originalCommandJson','canonicalCommand','commandDigest']);
    END IF;
    PERFORM public.org_native_long(target_value->'schemaVersion',2,2);PERFORM public.org_native_uuid(target_value->'nonce');PERFORM public.org_native_uuid(target_value->'tenantId');PERFORM public.org_native_uuid(target_value->'bankId');PERFORM public.org_native_uuid(target_value->'actionId');
    IF target_value->>'nonce' IS DISTINCT FROM p_nonce::text OR target_value->>'purpose' IS DISTINCT FROM p_purpose THEN PERFORM public.org_native_invalid(); END IF;
    tenant_value:=(target_value->>'tenantId')::uuid;bank_value:=(target_value->>'bankId')::uuid;action_value:=(target_value->>'actionId')::uuid;
    actor_value:=target_value->'actor';actor_token:=public.org_native_actor_token(actor_value);
    IF actor_value->>'kind' IS DISTINCT FROM 'STAFF' THEN PERFORM public.org_native_invalid(); END IF;
    PERFORM public.org_native_string(target_value->'operation',32);PERFORM public.org_native_string(target_value->'permission',255);operation_value:=target_value->>'operation';permission_value:=target_value->>'permission';
    PERFORM public.org_native_long(target_value->'authorityUntilEpochMillis',1,253402300799999);PERFORM public.org_native_long(target_value->'commandUntilEpochMillis',1,253402300799999);
    authority_until:=(target_value->>'authorityUntilEpochMillis')::bigint;command_until:=(target_value->>'commandUntilEpochMillis')::bigint;
    IF authority_until<=p_now OR command_until<=p_now OR authority_until>(actor_value->>'expiresAtEpochMillis')::bigint THEN PERFORM public.org_native_invalid(); END IF;
    IF p_purpose='PREPARATION_DISCOVERY' THEN
        IF encoding_value IS DISTINCT FROM 'FF_ORGANIZATION_BANK_PREPARATION_READ_CONTEXT_V2' OR operation_value NOT IN ('ENROLL','TRANSFER')
                OR json_typeof(target_value->'originalDesiredJson') IS DISTINCT FROM 'string' OR octet_length(convert_to(target_value->>'originalDesiredJson','UTF8')) NOT BETWEEN 1 AND 131072 THEN PERFORM public.org_native_invalid(); END IF;
        mode_value:=operation_value;desired:=(target_value->>'originalDesiredJson')::json;request_canonical:=public.org_native_preparation_desired(tenant_value,bank_value,mode_value,desired,p_now);request_digest:=encode(sha256(convert_to(request_canonical,'UTF8')),'hex');
        PERFORM public.org_native_string(target_value->'canonicalDesiredRequest',2097152);PERFORM public.org_native_digest(target_value->'desiredRequestDigest');
        IF desired->>'actionId' IS DISTINCT FROM action_value::text OR command_until IS DISTINCT FROM (desired->>'notAfterEpochMillis')::bigint
                OR target_value->>'canonicalDesiredRequest' IS DISTINCT FROM request_canonical OR target_value->>'desiredRequestDigest' IS DISTINCT FROM request_digest THEN PERFORM public.org_native_invalid(); END IF;
        canonical:=public.org_native_s(encoding_value)||'R13:'||public.org_native_long(target_value->'schemaVersion',2,2)||public.org_native_uuid(target_value->'nonce')||public.org_native_s(p_purpose)||public.org_native_s(operation_value)||public.org_native_s(permission_value)
            ||public.org_native_s(tenant_value::text)||public.org_native_s(bank_value::text)||public.org_native_s(action_value::text)||actor_token||public.org_native_long(target_value->'authorityUntilEpochMillis',1,253402300799999)||public.org_native_long(target_value->'commandUntilEpochMillis',1,253402300799999)||public.org_native_s(request_canonical)||public.org_native_s(request_digest);
    ELSE
        IF encoding_value IS DISTINCT FROM 'FF_ORGANIZATION_BANK_PREPARATION_CONTEXT_V2' OR operation_value NOT IN ('PREPARE','TRANSFER_PREPARE')
                OR json_typeof(target_value->'originalCommandJson') IS DISTINCT FROM 'string' OR octet_length(convert_to(target_value->>'originalCommandJson','UTF8')) NOT BETWEEN 1 AND 4194304 THEN PERFORM public.org_native_invalid(); END IF;
        command_value:=(target_value->>'originalCommandJson')::json;command_checked:=public.org_native_preparation_command(tenant_value,bank_value,action_value,operation_value,command_value,p_now);
        desired:=(command_value->>'originalDesiredJson')::json;revision_value:=(command_value->>'originalRevisionJson')::json;mode_value:=desired->>'mode';
        PERFORM public.org_native_uuid(target_value->'proposalId');PERFORM public.org_native_long(target_value->'revision',1,1);proposal_value:=(target_value->>'proposalId')::uuid;revision_number:=1;
        PERFORM public.org_native_string(target_value->'intendedRoute',2000);PERFORM public.org_native_string(target_value->'canonicalCommand',2097152);PERFORM public.org_native_digest(target_value->'commandDigest');
        request_canonical:=command_checked->>'canonical';request_digest:=command_checked->>'digest';
        IF target_value->>'proposalId' IS DISTINCT FROM revision_value->>'proposalId' OR target_value->>'intendedRoute' IS DISTINCT FROM command_checked->>'route'
                OR target_value->>'canonicalCommand' IS DISTINCT FROM request_canonical OR target_value->>'commandDigest' IS DISTINCT FROM request_digest OR command_until IS DISTINCT FROM (desired->>'notAfterEpochMillis')::bigint THEN PERFORM public.org_native_invalid(); END IF;
        SELECT * INTO observed FROM public.org_native_observed_packets(revision_value,packet->'observations',p_now);
        FOR evidence IN SELECT e FROM json_array_elements(packet->'observations') e LOOP
            eligibility:=(evidence->'observation'->'tenantEligibility')::jsonb;
            IF first_eligibility IS NULL THEN first_eligibility:=eligibility;ELSIF first_eligibility IS DISTINCT FROM eligibility THEN PERFORM public.org_native_invalid(); END IF;
        END LOOP;
        PERFORM public.org_native_long(packet->'observationsUntilEpochMillis',1,253402300799999);observed_until:=(packet->>'observationsUntilEpochMillis')::bigint;
        IF observed_until IS DISTINCT FROM LEAST(authority_until,observed.valid_until_ms) OR observed_until<=p_now THEN PERFORM public.org_native_invalid(); END IF;
        canonical:=public.org_native_s(encoding_value)||'R17:'||public.org_native_long(target_value->'schemaVersion',2,2)||public.org_native_uuid(target_value->'nonce')||public.org_native_s(p_purpose)||public.org_native_s(operation_value)||public.org_native_s(target_value->>'intendedRoute')||public.org_native_s(permission_value)
            ||public.org_native_s(tenant_value::text)||public.org_native_s(bank_value::text)||public.org_native_s(proposal_value::text)||public.org_native_long(target_value->'revision',1,1)||public.org_native_s(action_value::text)||actor_token||public.org_native_long(target_value->'authorityUntilEpochMillis',1,253402300799999)||public.org_native_long(target_value->'commandUntilEpochMillis',1,253402300799999)||public.org_native_s(request_canonical)||public.org_native_s(request_digest)||public.org_native_long(packet->'observationsUntilEpochMillis',1,253402300799999)||observed.canonical_observations;
        authority_until:=LEAST(authority_until,observed_until);
    END IF;
    IF permission_value IS DISTINCT FROM (CASE mode_value WHEN 'ENROLL' THEN 'organization.bank.enrollment.prepare' WHEN 'TRANSFER' THEN 'organization.bank.enrollment.transfer.prepare' ELSE NULL END) THEN PERFORM public.org_native_invalid(); END IF;
    PERFORM public.org_native_string(packet->'canonicalContext',2097152);PERFORM public.org_native_digest(packet->'contextDigest');
    IF canonical IS NULL OR packet->>'canonicalContext' IS DISTINCT FROM canonical OR packet->>'contextDigest' IS DISTINCT FROM encode(sha256(convert_to(canonical,'UTF8')),'hex') THEN PERFORM public.org_native_invalid(); END IF;
    RETURN jsonb_build_object('nonce',p_nonce,'purpose',p_purpose,'operation',operation_value,'tenantId',tenant_value,'bankId',bank_value,'proposalId',proposal_value,'revision',revision_number,'actionId',action_value,'requestDigest',request_digest,'contextDigest',packet->>'contextDigest','authorityUntil',LEAST(authority_until,p_now+30000),'commandUntil',LEAST(command_until,p_now+30000));
END $$;
REVOKE ALL ON FUNCTION public.org_native_preparation_desired(UUID,UUID,TEXT,JSON,BIGINT) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.org_native_preparation_command(UUID,UUID,UUID,TEXT,JSON,BIGINT) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.org_native_preparation_context_packet(UUID,TEXT,TEXT,BIGINT) FROM PUBLIC;
-- END SOURCE SECTION 11-preparation-wire.sql

-- BEGIN SOURCE SECTION 05-context-wire.sql
-- Closed context codec SOURCE candidate only. No native admission function or grant is enabled here.
CREATE FUNCTION public.org_native_permission(p_operation TEXT) RETURNS TEXT
LANGUAGE plpgsql IMMUTABLE SET search_path=pg_catalog,pg_temp AS $$
BEGIN
    RETURN 'organization.bank.enrollment.proposal.'||CASE p_operation WHEN 'SUBMIT' THEN 'submit' WHEN 'APPROVE' THEN 'approve' WHEN 'REJECT' THEN 'reject' WHEN 'WITHDRAW' THEN 'withdraw' WHEN 'CANCEL' THEN 'cancel' WHEN 'CANCEL_EMERGENCY' THEN 'cancel.emergency' ELSE NULL END;
END $$;

CREATE FUNCTION public.org_native_command_route(p_tenant UUID,p_bank UUID,p_proposal UUID,p_operation TEXT) RETURNS TEXT
LANGUAGE plpgsql IMMUTABLE SET search_path=pg_catalog,pg_temp AS $$
DECLARE suffix TEXT;
BEGIN
    suffix:=CASE p_operation WHEN 'SUBMIT' THEN 'submit' WHEN 'APPROVE' THEN 'approve' WHEN 'REJECT' THEN 'reject' WHEN 'WITHDRAW' THEN 'withdraw' WHEN 'CANCEL' THEN 'cancel' WHEN 'CANCEL_EMERGENCY' THEN 'emergency-cancel' ELSE NULL END;
    IF suffix IS NULL THEN PERFORM public.org_native_invalid(); END IF;
    RETURN 'POST /api/v1/platform/tenants/'||p_tenant::text||'/banks/'||p_bank::text||'/enrollment-proposals/'||p_proposal::text||'/'||suffix;
END $$;

CREATE FUNCTION public.org_native_context_packet(p_nonce UUID,p_original TEXT,p_purpose TEXT,p_now BIGINT) RETURNS JSONB
LANGUAGE plpgsql IMMUTABLE SET search_path=pg_catalog,pg_temp AS $$
DECLARE packet JSON; target_value JSON; actor_value JSON; actor_token TEXT; operation_value TEXT; permission_value TEXT;
    tenant_value UUID; bank_value UUID; proposal_value UUID; action_value UUID; revision_value BIGINT;
    canonical_request TEXT; request_digest TEXT; canonical_context TEXT; base_canonical TEXT; context_digest TEXT;
    revision_canonical TEXT; revision_digest TEXT; revision_json JSON; command_json JSON; command_token TEXT; encoding_value TEXT;
    authority_until BIGINT; command_until BIGINT; observed_until BIGINT; observed RECORD; observed_wrapper BOOLEAN:=false;
BEGIN
    IF p_purpose IN ('PREPARATION_DISCOVERY','PREPARATION_MUTATION') THEN RETURN public.org_native_preparation_context_packet(p_nonce,p_original,p_purpose,p_now); END IF;
    PERFORM public.org_native_uuid(to_json(p_nonce::text));
    IF p_original IS NULL OR p_now IS NULL OR p_now<=0 OR octet_length(convert_to(p_original,'UTF8')) NOT BETWEEN 1 AND 4194304
            OR p_purpose IS NULL OR p_purpose NOT IN ('ACTION_READ','PROPOSAL_DISCOVERY','MUTATION') THEN PERFORM public.org_native_invalid(); END IF;
    packet:=p_original::json;encoding_value:=packet->>'contextEncoding';
    IF p_purpose='MUTATION' AND encoding_value='FF_ORGANIZATION_NATIVE_OBSERVED_PROPOSAL_CONTEXT_V2' THEN
        PERFORM public.org_native_shape(packet,ARRAY['target','observations','observationsUntilEpochMillis','contextEncoding','canonicalContext','contextDigest']);observed_wrapper:=true;
    ELSE PERFORM public.org_native_shape(packet,ARRAY['target','contextEncoding','canonicalContext','contextDigest']); END IF;
    target_value:=packet->'target';
    IF p_purpose='ACTION_READ' THEN
        PERFORM public.org_native_shape(target_value,ARRAY['schemaVersion','nonce','purpose','operation','permission','tenantId','bankId','actionId','actor','authorityUntilEpochMillis','commandUntilEpochMillis','canonicalReadRequest','readRequestDigest']);
    ELSIF p_purpose='PROPOSAL_DISCOVERY' THEN
        PERFORM public.org_native_shape(target_value,ARRAY['schemaVersion','nonce','purpose','operation','permission','tenantId','bankId','proposalId','revision','actionId','actor','authorityUntilEpochMillis','commandUntilEpochMillis','originalCommandJson','canonicalDiscoveryRequest','discoveryRequestDigest']);
    ELSE
        PERFORM public.org_native_shape(target_value,ARRAY['schemaVersion','nonce','purpose','operation','intendedRoute','permission','tenantId','bankId','proposalId','revision','actionId','actor','authorityUntilEpochMillis','commandUntilEpochMillis','originalRevisionJson','canonicalRevision','revisionDigest','originalCommandJson','canonicalCommand','commandDigest']);
    END IF;
    PERFORM public.org_native_long(target_value->'schemaVersion',2,2);PERFORM public.org_native_uuid(target_value->'nonce');PERFORM public.org_native_uuid(target_value->'tenantId');PERFORM public.org_native_uuid(target_value->'bankId');PERFORM public.org_native_uuid(target_value->'actionId');
    IF target_value->>'nonce' IS DISTINCT FROM p_nonce::text OR target_value->>'purpose' IS DISTINCT FROM p_purpose THEN PERFORM public.org_native_invalid(); END IF;
    tenant_value:=(target_value->>'tenantId')::uuid;bank_value:=(target_value->>'bankId')::uuid;action_value:=(target_value->>'actionId')::uuid;
    actor_value:=target_value->'actor';actor_token:=public.org_native_actor_token(actor_value);
    PERFORM public.org_native_string(target_value->'operation',32);PERFORM public.org_native_string(target_value->'permission',255);
    operation_value:=target_value->>'operation';permission_value:=target_value->>'permission';
    PERFORM public.org_native_long(target_value->'authorityUntilEpochMillis',1,253402300799999);PERFORM public.org_native_long(target_value->'commandUntilEpochMillis',1,253402300799999);
    authority_until:=(target_value->>'authorityUntilEpochMillis')::bigint;command_until:=(target_value->>'commandUntilEpochMillis')::bigint;
    IF authority_until<=p_now OR command_until<=p_now OR authority_until>(actor_value->>'expiresAtEpochMillis')::bigint THEN PERFORM public.org_native_invalid(); END IF;
    IF p_purpose='ACTION_READ' THEN
        IF encoding_value IS DISTINCT FROM 'FF_ORGANIZATION_BANK_ACTION_READ_CONTEXT_V2' OR operation_value<>'ACTION_READ'
                OR permission_value<>'organization.bank.enrollment.action.read' OR authority_until<>command_until THEN PERFORM public.org_native_invalid(); END IF;
        canonical_request:=public.org_native_s('FF_ORGANIZATION_BANK_ENROLLMENT_ACTION_READ_V2')||'R3:'||public.org_native_s(tenant_value::text)||public.org_native_s(bank_value::text)||public.org_native_s(action_value::text);
        request_digest:=encode(sha256(convert_to(canonical_request,'UTF8')),'hex');
        PERFORM public.org_native_string(target_value->'canonicalReadRequest',2097152);PERFORM public.org_native_digest(target_value->'readRequestDigest');
        IF target_value->>'canonicalReadRequest' IS DISTINCT FROM canonical_request OR target_value->>'readRequestDigest' IS DISTINCT FROM request_digest THEN PERFORM public.org_native_invalid(); END IF;
        canonical_context:=public.org_native_s(encoding_value)||'R13:'||public.org_native_long(target_value->'schemaVersion',2,2)||public.org_native_uuid(target_value->'nonce')||public.org_native_s(p_purpose)||public.org_native_s(operation_value)||public.org_native_s(permission_value)||public.org_native_s(tenant_value::text)||public.org_native_s(bank_value::text)||public.org_native_s(action_value::text)||actor_token||public.org_native_long(target_value->'authorityUntilEpochMillis',1,253402300799999)||public.org_native_long(target_value->'commandUntilEpochMillis',1,253402300799999)||public.org_native_s(canonical_request)||public.org_native_s(request_digest);
    ELSE
        PERFORM public.org_native_uuid(target_value->'proposalId');PERFORM public.org_native_long(target_value->'revision',1,9223372036854775806);proposal_value:=(target_value->>'proposalId')::uuid;revision_value:=(target_value->>'revision')::bigint;
        IF public.org_native_permission(operation_value) IS NULL OR permission_value IS DISTINCT FROM public.org_native_permission(operation_value)
                OR operation_value<>'CANCEL_EMERGENCY' AND actor_value->>'kind'<>'STAFF' THEN PERFORM public.org_native_invalid(); END IF;
        IF json_typeof(target_value->'originalCommandJson') IS DISTINCT FROM 'string' OR octet_length(convert_to(target_value->>'originalCommandJson','UTF8')) NOT BETWEEN 1 AND 16384 THEN PERFORM public.org_native_invalid(); END IF;
        command_json:=(target_value->>'originalCommandJson')::json;command_token:=public.org_native_command(operation_value,revision_value,command_json,p_now);
        IF command_json->>'actionId' IS DISTINCT FROM action_value::text OR (command_json->>'commandNotAfterEpochMillis')::bigint<>command_until THEN PERFORM public.org_native_invalid(); END IF;
        IF p_purpose='PROPOSAL_DISCOVERY' THEN
            IF encoding_value IS DISTINCT FROM 'FF_ORGANIZATION_BANK_PROPOSAL_READ_CONTEXT_V2' THEN PERFORM public.org_native_invalid(); END IF;
            canonical_request:=public.org_native_s('FF_ORGANIZATION_BANK_PROPOSAL_DISCOVERY_V2')||'R5:'||public.org_native_s(tenant_value::text)||public.org_native_s(bank_value::text)||public.org_native_s(proposal_value::text)||public.org_native_long(target_value->'revision',1,9223372036854775806)||command_token;
            request_digest:=encode(sha256(convert_to(canonical_request,'UTF8')),'hex');PERFORM public.org_native_string(target_value->'canonicalDiscoveryRequest',2097152);PERFORM public.org_native_digest(target_value->'discoveryRequestDigest');
            IF target_value->>'canonicalDiscoveryRequest' IS DISTINCT FROM canonical_request OR target_value->>'discoveryRequestDigest' IS DISTINCT FROM request_digest THEN PERFORM public.org_native_invalid(); END IF;
            canonical_context:=public.org_native_s(encoding_value)||'R15:'||public.org_native_long(target_value->'schemaVersion',2,2)||public.org_native_uuid(target_value->'nonce')||public.org_native_s(p_purpose)||public.org_native_s(operation_value)||public.org_native_s(permission_value)||public.org_native_s(tenant_value::text)||public.org_native_s(bank_value::text)||public.org_native_s(proposal_value::text)||public.org_native_long(target_value->'revision',1,9223372036854775806)||public.org_native_s(action_value::text)||actor_token||public.org_native_long(target_value->'authorityUntilEpochMillis',1,253402300799999)||public.org_native_long(target_value->'commandUntilEpochMillis',1,253402300799999)||public.org_native_s(canonical_request)||public.org_native_s(request_digest);
        ELSE
            IF observed_wrapper IS DISTINCT FROM (operation_value IN ('SUBMIT','APPROVE')) OR NOT observed_wrapper AND encoding_value IS DISTINCT FROM 'FF_ORGANIZATION_NATIVE_MUTATION_CONTEXT_V2' THEN PERFORM public.org_native_invalid(); END IF;
            IF json_typeof(target_value->'originalRevisionJson') IS DISTINCT FROM 'string' OR octet_length(convert_to(target_value->>'originalRevisionJson','UTF8')) NOT BETWEEN 1 AND 4194304 THEN PERFORM public.org_native_invalid(); END IF;
            revision_json:=(target_value->>'originalRevisionJson')::json;revision_canonical:=public.org_native_revision(revision_json);revision_digest:=encode(sha256(convert_to(revision_canonical,'UTF8')),'hex');
            IF revision_json->>'proposalId' IS DISTINCT FROM proposal_value::text OR (revision_json->>'revision')::bigint<>revision_value THEN PERFORM public.org_native_invalid(); END IF;
            PERFORM public.org_native_string(target_value->'canonicalRevision',2097152);PERFORM public.org_native_digest(target_value->'revisionDigest');
            IF target_value->>'canonicalRevision' IS DISTINCT FROM revision_canonical OR target_value->>'revisionDigest' IS DISTINCT FROM revision_digest THEN PERFORM public.org_native_invalid(); END IF;
            canonical_request:=public.org_native_full_command(tenant_value,bank_value,proposal_value,revision_json,operation_value,command_json,p_now);request_digest:=encode(sha256(convert_to(canonical_request,'UTF8')),'hex');
            PERFORM public.org_native_string(target_value->'canonicalCommand',2097152);PERFORM public.org_native_digest(target_value->'commandDigest');PERFORM public.org_native_string(target_value->'intendedRoute',2000);
            IF target_value->>'canonicalCommand' IS DISTINCT FROM canonical_request OR target_value->>'commandDigest' IS DISTINCT FROM request_digest OR target_value->>'intendedRoute' IS DISTINCT FROM public.org_native_command_route(tenant_value,bank_value,proposal_value,operation_value) THEN PERFORM public.org_native_invalid(); END IF;
            base_canonical:=public.org_native_s('FF_ORGANIZATION_NATIVE_MUTATION_CONTEXT_V2')||'R18:'||public.org_native_long(target_value->'schemaVersion',2,2)||public.org_native_uuid(target_value->'nonce')||public.org_native_s(p_purpose)||public.org_native_s(operation_value)||public.org_native_s(target_value->>'intendedRoute')||public.org_native_s(permission_value)||public.org_native_s(tenant_value::text)||public.org_native_s(bank_value::text)||public.org_native_s(proposal_value::text)||public.org_native_long(target_value->'revision',1,9223372036854775806)||public.org_native_s(action_value::text)||actor_token||public.org_native_long(target_value->'authorityUntilEpochMillis',1,253402300799999)||public.org_native_long(target_value->'commandUntilEpochMillis',1,253402300799999)||public.org_native_s(revision_canonical)||public.org_native_s(revision_digest)||public.org_native_s(canonical_request)||public.org_native_s(request_digest);
            IF observed_wrapper THEN
                SELECT * INTO observed FROM public.org_native_observed_packets(revision_json,packet->'observations',p_now);
                PERFORM public.org_native_long(packet->'observationsUntilEpochMillis',1,253402300799999);observed_until:=(packet->>'observationsUntilEpochMillis')::bigint;
                IF observed_until IS DISTINCT FROM LEAST(authority_until,observed.valid_until_ms) OR observed_until<=p_now THEN PERFORM public.org_native_invalid(); END IF;
                canonical_context:=public.org_native_s(encoding_value)||'R3:'||public.org_native_s(base_canonical)||public.org_native_s(encode(sha256(convert_to(base_canonical,'UTF8')),'hex'))||public.org_native_long(packet->'observationsUntilEpochMillis',1,253402300799999)||observed.canonical_observations;
                authority_until:=LEAST(authority_until,observed_until);
            ELSE canonical_context:=base_canonical; END IF;
        END IF;
    END IF;
    PERFORM public.org_native_string(packet->'canonicalContext',2097152);PERFORM public.org_native_digest(packet->'contextDigest');context_digest:=encode(sha256(convert_to(canonical_context,'UTF8')),'hex');
    IF canonical_context IS NULL OR packet->>'canonicalContext' IS DISTINCT FROM canonical_context OR packet->>'contextDigest' IS DISTINCT FROM context_digest THEN PERFORM public.org_native_invalid(); END IF;
    -- Reject NULL/expired values above; LEAST must never turn absent input into a usable horizon.
    RETURN jsonb_build_object('nonce',p_nonce,'purpose',p_purpose,'operation',operation_value,'tenantId',tenant_value,'bankId',bank_value,'proposalId',proposal_value,'revision',revision_value,'actionId',action_value,
            'requestDigest',request_digest,'contextDigest',context_digest,'authorityUntil',LEAST(authority_until,p_now+30000),'commandUntil',LEAST(command_until,p_now+30000));
END $$;
REVOKE ALL ON FUNCTION public.org_native_permission(TEXT) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.org_native_command_route(UUID,UUID,UUID,TEXT) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.org_native_context_packet(UUID,TEXT,TEXT,BIGINT) FROM PUBLIC;
-- END SOURCE SECTION 05-context-wire.sql

-- BEGIN SOURCE SECTION 06-context.sql
-- Native context/lock SOURCE candidate only. No runtime execution or native acceptance.
CREATE FUNCTION org_private.require_runtime() RETURNS VOID
LANGUAGE plpgsql VOLATILE SECURITY DEFINER SET search_path=pg_catalog,pg_temp AS $$
BEGIN
    IF session_user<>'org_native_runtime' OR current_setting('role')<>'none' OR current_setting('transaction_isolation')<>'read committed' THEN
        RAISE EXCEPTION 'Native organization runtime context required' USING ERRCODE='42501';
    END IF;
END $$;

CREATE FUNCTION org_private.begin_enrollment_context(p_nonce UUID,p_original TEXT,p_purpose TEXT) RETURNS UUID
LANGUAGE plpgsql VOLATILE SECURITY DEFINER SET search_path=pg_catalog,pg_temp AS $$
DECLARE now_ms BIGINT; validated JSONB;
BEGIN
    PERFORM org_private.require_runtime();
    IF EXISTS(SELECT 1 FROM org_private.enrollment_context c WHERE c.xid=pg_current_xact_id() AND c.backend_pid=pg_backend_pid() AND c.db_session_user=session_user)
            OR EXISTS(SELECT 1 FROM org_private.legacy_source_witness w WHERE w.xid=pg_current_xact_id() AND w.backend_pid=pg_backend_pid() AND w.db_session_user=session_user) THEN
        RAISE EXCEPTION 'Mixed or repeated native organization context' USING ERRCODE='42501';
    END IF;
    now_ms:=floor(extract(epoch FROM clock_timestamp())*1000)::bigint;
    validated:=public.org_native_context_packet(p_nonce,p_original,p_purpose,now_ms);
    INSERT INTO org_private.enrollment_context(nonce,xid,backend_pid,db_session_user,operation,tenant_id,bank_id,action_id,purpose,proposal_id,proposal_revision,context_digest,locked,canonical_request_digest,full_context,authority_until_ms,command_until_ms,recorded_at_ms)
    VALUES(p_nonce,pg_current_xact_id(),pg_backend_pid(),session_user,validated->>'operation',(validated->>'tenantId')::uuid,(validated->>'bankId')::uuid,(validated->>'actionId')::uuid,
        p_purpose,(validated->>'proposalId')::uuid,(validated->>'revision')::bigint,validated->>'contextDigest',false,validated->>'requestDigest',convert_to(p_original,'UTF8'),(validated->>'authorityUntil')::bigint,(validated->>'commandUntil')::bigint,NULL);
    PERFORM set_config('firefly.organization.enrollment_nonce',p_nonce::text,true);
    RETURN p_nonce;
END $$;

CREATE FUNCTION public.org_native_enrollment_begin_read_context(p_nonce UUID,p_original TEXT) RETURNS UUID
LANGUAGE sql VOLATILE SECURITY DEFINER SET search_path=pg_catalog,pg_temp AS $$
SELECT org_private.begin_enrollment_context(p_nonce,p_original,'ACTION_READ') $$;
CREATE FUNCTION public.org_native_enrollment_begin_proposal_read_context(p_nonce UUID,p_original TEXT) RETURNS UUID
LANGUAGE sql VOLATILE SECURITY DEFINER SET search_path=pg_catalog,pg_temp AS $$
SELECT org_private.begin_enrollment_context(p_nonce,p_original,'PROPOSAL_DISCOVERY') $$;
CREATE FUNCTION public.org_native_enrollment_begin_mutation_context(p_nonce UUID,p_original TEXT) RETURNS UUID
LANGUAGE sql VOLATILE SECURITY DEFINER SET search_path=pg_catalog,pg_temp AS $$
SELECT org_private.begin_enrollment_context(p_nonce,p_original,'MUTATION') $$;

CREATE FUNCTION public.org_native_enrollment_begin_preparation_read_context(p_nonce UUID,p_original TEXT) RETURNS UUID
LANGUAGE sql VOLATILE SECURITY DEFINER SET search_path=pg_catalog,pg_temp AS $$
SELECT org_private.begin_enrollment_context(p_nonce,p_original,'PREPARATION_DISCOVERY') $$;
CREATE FUNCTION public.org_native_enrollment_begin_preparation_context(p_nonce UUID,p_original TEXT) RETURNS UUID
LANGUAGE sql VOLATILE SECURITY DEFINER SET search_path=pg_catalog,pg_temp AS $$
SELECT org_private.begin_enrollment_context(p_nonce,p_original,'PREPARATION_MUTATION') $$;

CREATE FUNCTION public.org_native_enrollment_check_context(p_nonce UUID) RETURNS BOOLEAN
LANGUAGE plpgsql VOLATILE SECURITY DEFINER SET search_path=pg_catalog,pg_temp AS $$
DECLARE now_ms BIGINT;
BEGIN
    PERFORM org_private.require_runtime();
    IF p_nonce IS NULL OR current_setting('firefly.organization.enrollment_nonce',true) IS DISTINCT FROM p_nonce::text THEN RETURN false; END IF;
    now_ms:=floor(extract(epoch FROM clock_timestamp())*1000)::bigint;
    RETURN EXISTS(SELECT 1 FROM org_private.enrollment_context c WHERE c.nonce=p_nonce AND c.xid=pg_current_xact_id() AND c.backend_pid=pg_backend_pid() AND c.db_session_user=session_user AND c.locked
        AND c.authority_until_ms>now_ms AND c.command_until_ms>now_ms AND c.recorded_at_ms IS NOT NULL AND c.recorded_at_ms<=now_ms);
END $$;

CREATE FUNCTION public.org_native_enrollment_lock_context(p_nonce UUID)
RETURNS TABLE(context_nonce UUID,db_now_ms BIGINT,authority_until_ms BIGINT,command_until_ms BIGINT,context_digest TEXT)
LANGUAGE plpgsql VOLATILE SECURITY DEFINER SET search_path=pg_catalog,pg_temp AS $$
DECLARE c org_private.enrollment_context%ROWTYPE; captured_bank public.org_native_bank_gate%ROWTYPE; actual_bank public.org_native_bank_gate%ROWTYPE;
    captured_header public.org_native_proposal%ROWTYPE; actual_header public.org_native_proposal%ROWTYPE;
    captured_revision public.org_native_proposal_revision%ROWTYPE; actual_revision public.org_native_proposal_revision%ROWTYPE;
    proposal_control public.org_native_proposal_control%ROWTYPE; enrollment_control public.org_native_bank_enrollment_control%ROWTYPE;
    scope_row RECORD; packet JSON; target_value JSON; revision_value JSON; enrollment_target JSON; actor_value JSON;
    scope_keys JSONB; after_scope_keys JSONB; now_ms BIGINT; expected_permission TEXT;
    prepared_desired JSON; prepared_command JSON; captured_missing BOOLEAN:=false; absent_bank BOOLEAN:=false;
BEGIN
    PERFORM org_private.require_runtime();
    IF p_nonce IS NULL OR current_setting('firefly.organization.enrollment_nonce',true) IS DISTINCT FROM p_nonce::text THEN
        RAISE EXCEPTION 'Native organization context unavailable' USING ERRCODE='42501';
    END IF;
    SELECT * INTO c FROM org_private.enrollment_context e WHERE e.nonce=p_nonce AND e.xid=pg_current_xact_id() AND e.backend_pid=pg_backend_pid() AND e.db_session_user=session_user;
    IF NOT FOUND OR c.locked THEN RAISE EXCEPTION 'Native organization context unavailable' USING ERRCODE='42501'; END IF;
    packet:=convert_from(c.full_context,'UTF8')::json;target_value:=packet->'target';actor_value:=target_value->'actor';
    IF c.purpose='PREPARATION_DISCOVERY' THEN prepared_desired:=(target_value->>'originalDesiredJson')::json;
    ELSIF c.purpose='PREPARATION_MUTATION' THEN prepared_command:=(target_value->>'originalCommandJson')::json;prepared_desired:=(prepared_command->>'originalDesiredJson')::json;
        revision_value:=(prepared_command->>'originalRevisionJson')::json;enrollment_target:=(revision_value->>'originalEnrollmentTargetJson')::json;
    END IF;
    SELECT * INTO captured_bank FROM public.org_native_bank_gate g WHERE g.bank_id=c.bank_id;
    captured_missing:=NOT FOUND;
    IF captured_missing AND c.purpose<>'PREPARATION_DISCOVERY' THEN RAISE EXCEPTION 'Native organization bank gate unavailable' USING ERRCODE='P0001'; END IF;
    scope_keys:='[]'::jsonb;
    IF prepared_desired IS NOT NULL THEN scope_keys:=scope_keys||jsonb_build_array(jsonb_build_object('tenantId',c.tenant_id,'institutionId',(prepared_desired->>'institutionId')::uuid)); END IF;
    IF c.purpose IN ('PROPOSAL_DISCOVERY','MUTATION') THEN
        SELECT * INTO captured_header FROM public.org_native_proposal h WHERE h.tenant_id=c.tenant_id AND h.proposal_id=c.proposal_id;
        IF NOT FOUND OR captured_header.bank_id<>c.bank_id THEN RAISE EXCEPTION 'Native organization proposal unavailable' USING ERRCODE='P0001'; END IF;
        SELECT * INTO captured_revision FROM public.org_native_proposal_revision r WHERE r.tenant_id=c.tenant_id AND r.proposal_id=c.proposal_id AND r.revision=c.proposal_revision;
        IF NOT FOUND THEN RAISE EXCEPTION 'Native organization revision unavailable' USING ERRCODE='P0001'; END IF;
        scope_keys:=scope_keys||jsonb_build_array(jsonb_build_object('tenantId',c.tenant_id,'institutionId',captured_header.institution_id));
    END IF;
    IF captured_bank.current_tenant_id IS NOT NULL THEN
        scope_keys:=scope_keys||jsonb_build_array(jsonb_build_object('tenantId',captured_bank.current_tenant_id,'institutionId',captured_bank.current_institution_id));
    END IF;
    -- Complete old/new parent set is captured before the first catalogue/Bank/scope lock.
    PERFORM 1 FROM public.org_native_source_catalogue_gate g WHERE g.singleton FOR SHARE;
    IF NOT FOUND THEN RAISE EXCEPTION 'Native organization catalogue gate unavailable' USING ERRCODE='P0001'; END IF;
    IF captured_missing THEN
        INSERT INTO public.org_native_bank_gate(bank_id,bank_generation,ownership_generation,source_generation)
        SELECT b.id,0,0,0 FROM public.bank b WHERE b.id=c.bank_id ON CONFLICT DO NOTHING;
    END IF;
    SELECT * INTO actual_bank FROM public.org_native_bank_gate g WHERE g.bank_id=c.bank_id FOR UPDATE;
    IF NOT FOUND THEN
        IF c.purpose='PREPARATION_DISCOVERY' AND NOT EXISTS(SELECT 1 FROM public.bank b WHERE b.id=c.bank_id) THEN absent_bank:=true;
        ELSE RAISE EXCEPTION 'Native organization bank gate unavailable' USING ERRCODE='P0001'; END IF;
    END IF;
    IF NOT absent_bank THEN
        IF captured_missing THEN
            IF actual_bank.bank_generation<>0 OR actual_bank.ownership_generation<>0 OR actual_bank.source_generation<>0 OR actual_bank.current_tenant_id IS NOT NULL THEN RAISE EXCEPTION 'Native organization captured parents changed' USING ERRCODE='40001'; END IF;
        ELSIF to_jsonb(captured_bank) IS DISTINCT FROM to_jsonb(actual_bank) THEN RAISE EXCEPTION 'Native organization captured parents changed' USING ERRCODE='40001'; END IF;
    END IF;
    FOR scope_row IN SELECT DISTINCT (v->>'tenantId')::uuid AS tenant_id,(v->>'institutionId')::uuid AS institution_id FROM jsonb_array_elements(scope_keys) v ORDER BY tenant_id,institution_id LOOP
        INSERT INTO public.org_native_scope_gate(tenant_id,institution_id) VALUES(scope_row.tenant_id,scope_row.institution_id) ON CONFLICT DO NOTHING;
        PERFORM 1 FROM public.org_native_scope_gate g WHERE g.tenant_id=scope_row.tenant_id AND g.institution_id=scope_row.institution_id FOR UPDATE;
    END LOOP;
    IF c.purpose IN ('PROPOSAL_DISCOVERY','MUTATION') THEN
        SELECT * INTO actual_header FROM public.org_native_proposal h WHERE h.tenant_id=c.tenant_id AND h.proposal_id=c.proposal_id;
        SELECT * INTO actual_revision FROM public.org_native_proposal_revision r WHERE r.tenant_id=c.tenant_id AND r.proposal_id=c.proposal_id AND r.revision=c.proposal_revision;
        IF to_jsonb(captured_header) IS DISTINCT FROM to_jsonb(actual_header) OR to_jsonb(captured_revision) IS DISTINCT FROM to_jsonb(actual_revision) THEN
            RAISE EXCEPTION 'Native organization captured proposal changed' USING ERRCODE='40001';
        END IF;
    END IF;
    -- Parent controls are locked before proposal/action gates. A read never discovers an earlier parent afterward.
    IF actual_bank.current_tenant_id IS NOT NULL THEN
        SELECT * INTO enrollment_control FROM public.org_native_bank_enrollment_control e WHERE e.tenant_id=actual_bank.current_tenant_id AND e.enrollment_id=actual_bank.current_enrollment_id AND e.version=actual_bank.current_enrollment_version FOR UPDATE;
        IF NOT FOUND THEN RAISE EXCEPTION 'Native organization owner control unavailable' USING ERRCODE='P0001'; END IF;
    END IF;
    IF c.purpose IN ('PROPOSAL_DISCOVERY','MUTATION') THEN
        SELECT * INTO proposal_control FROM public.org_native_proposal_control p WHERE p.tenant_id=c.tenant_id AND p.proposal_id=c.proposal_id FOR UPDATE;
        IF NOT FOUND OR proposal_control.current_revision<>c.proposal_revision THEN RAISE EXCEPTION 'Native organization proposal control changed' USING ERRCODE='P0001'; END IF;
    END IF;
    PERFORM pg_advisory_xact_lock(hashtextextended('org-native-action/'||c.tenant_id::text||'/'||c.action_id::text,0));
    now_ms:=floor(extract(epoch FROM clock_timestamp())*1000)::bigint;
    IF c.authority_until_ms<=now_ms OR c.command_until_ms<=now_ms THEN RAISE EXCEPTION 'Native organization context expired after wait' USING ERRCODE='42501'; END IF;
    -- Reparse exact retained bytes after all waits without extending either stored horizon.
    PERFORM public.org_native_context_packet(p_nonce,convert_from(c.full_context,'UTF8'),c.purpose,now_ms);
    IF c.purpose='MUTATION' THEN
        revision_value:=(target_value->>'originalRevisionJson')::json;enrollment_target:=(revision_value->>'originalEnrollmentTargetJson')::json;
        IF captured_revision.revision_canonical IS DISTINCT FROM target_value->>'canonicalRevision' OR captured_revision.revision_digest IS DISTINCT FROM target_value->>'revisionDigest'
                OR convert_from(captured_revision.revision_original,'UTF8') IS DISTINCT FROM target_value->>'originalRevisionJson'
                OR proposal_control.control_version IS DISTINCT FROM (((target_value->>'originalCommandJson')::json)->>'expectedControlVersion')::bigint THEN
            RAISE EXCEPTION 'Native organization exact retained target changed' USING ERRCODE='P0001';
        END IF;
        IF captured_header.maker_kind<>'STAFF' OR actor_value->>'tenantId' IS DISTINCT FROM captured_header.maker_tenant_id::text THEN
            RAISE EXCEPTION 'Native organization proposal actor scope denied' USING ERRCODE='42501';
        END IF;
        IF c.operation<>'CANCEL_EMERGENCY' THEN
            IF actor_value->>'kind' IS DISTINCT FROM 'STAFF' OR actor_value->>'staffRealm' IS DISTINCT FROM captured_header.maker_staff_realm THEN RAISE EXCEPTION 'Native organization staff realm denied' USING ERRCODE='42501'; END IF;
            IF c.operation IN ('APPROVE','REJECT') THEN
                IF actor_value->>'staffActorId'=captured_header.maker_staff_id::text OR (actor_value->>'issuer'=captured_header.maker_issuer AND actor_value->>'subject'=captured_header.maker_subject) THEN
                    RAISE EXCEPTION 'Native organization independent checker required' USING ERRCODE='42501';
                END IF;
            ELSIF actor_value->>'staffActorId' IS DISTINCT FROM captured_header.maker_staff_id::text OR actor_value->>'issuer' IS DISTINCT FROM captured_header.maker_issuer OR actor_value->>'subject' IS DISTINCT FROM captured_header.maker_subject THEN
                RAISE EXCEPTION 'Native organization current maker required' USING ERRCODE='42501';
            END IF;
        END IF;
        IF (c.operation='SUBMIT' AND proposal_control.state<>'DRAFT') OR (c.operation IN ('APPROVE','REJECT') AND proposal_control.state<>'SUBMITTED')
                OR (c.operation='WITHDRAW' AND proposal_control.state NOT IN ('SUBMITTED','APPROVED')) OR (c.operation IN ('CANCEL','CANCEL_EMERGENCY') AND proposal_control.state NOT IN ('DRAFT','SUBMITTED','APPROVED','REJECTED','WITHDRAWN')) THEN
            RAISE EXCEPTION 'Native organization proposal state changed' USING ERRCODE='P0001';
        END IF;
        IF c.operation IN ('SUBMIT','APPROVE') THEN
            IF actual_bank.bank_generation<>(revision_value->>'expectedBankGeneration')::bigint OR actual_bank.ownership_generation<>(enrollment_target->>'expectedOwnershipGeneration')::bigint
                    OR actual_bank.source_generation<>(enrollment_target->>'expectedSourceGeneration')::bigint OR NOT EXISTS(SELECT 1 FROM public.bank b WHERE b.id=c.bank_id AND b.is_active IS TRUE) THEN
                RAISE EXCEPTION 'Native organization live bank source changed' USING ERRCODE='P0001';
            END IF;
            IF enrollment_target->>'mode'='ENROLL' THEN
                IF actual_bank.ownership_generation<>0 OR actual_bank.current_tenant_id IS NOT NULL THEN RAISE EXCEPTION 'Native organization virgin bank required' USING ERRCODE='P0001'; END IF;
            ELSE
                IF actual_bank.current_tenant_id::text IS DISTINCT FROM revision_value->'previousOwner'->>'tenantId' OR actual_bank.current_institution_id::text IS DISTINCT FROM revision_value->'previousOwner'->>'institutionId'
                        OR actual_bank.current_issuer_id::text IS DISTINCT FROM revision_value->'previousOwner'->>'issuerAuthorityId' OR actual_bank.current_enrollment_id::text IS DISTINCT FROM enrollment_target->>'existingEnrollmentId'
                        OR actual_bank.current_enrollment_version IS DISTINCT FROM (enrollment_target->>'expectedEnrollmentVersion')::bigint OR enrollment_control.control_version IS DISTINCT FROM (enrollment_target->>'expectedEnrollmentControlVersion')::bigint
                        OR enrollment_control.state='SUPERSEDED' THEN RAISE EXCEPTION 'Native organization previous owner changed' USING ERRCODE='P0001'; END IF;
            END IF;
        END IF;
    END IF;
    IF c.purpose='PREPARATION_MUTATION' THEN
        IF absent_bank OR NOT EXISTS(SELECT 1 FROM public.bank b WHERE b.id=c.bank_id AND b.is_active IS TRUE)
                OR actual_bank.bank_generation IS DISTINCT FROM (revision_value->>'expectedBankGeneration')::bigint
                OR actual_bank.ownership_generation IS DISTINCT FROM (enrollment_target->>'expectedOwnershipGeneration')::bigint
                OR actual_bank.source_generation IS DISTINCT FROM (enrollment_target->>'expectedSourceGeneration')::bigint THEN RAISE EXCEPTION 'Native organization live preparation source changed' USING ERRCODE='P0001'; END IF;
        IF prepared_desired->>'mode'='ENROLL' THEN
            IF actual_bank.ownership_generation<>0 OR actual_bank.current_tenant_id IS NOT NULL THEN RAISE EXCEPTION 'Native organization virgin bank required' USING ERRCODE='P0001'; END IF;
        ELSE
            IF actual_bank.current_tenant_id::text IS DISTINCT FROM revision_value->'previousOwner'->>'tenantId'
                    OR actual_bank.current_institution_id::text IS DISTINCT FROM revision_value->'previousOwner'->>'institutionId'
                    OR actual_bank.current_issuer_id::text IS DISTINCT FROM revision_value->'previousOwner'->>'issuerAuthorityId'
                    OR actual_bank.current_enrollment_id::text IS DISTINCT FROM revision_value->'previousOwner'->>'enrollmentId'
                    OR actual_bank.current_enrollment_version IS DISTINCT FROM (revision_value->'previousOwner'->>'version')::bigint
                    OR enrollment_control.control_version IS DISTINCT FROM (revision_value->'previousOwner'->>'controlVersion')::bigint
                    OR enrollment_control.state NOT IN ('ACTIVE','SUSPENDED') THEN RAISE EXCEPTION 'Native organization previous owner changed' USING ERRCODE='P0001'; END IF;
        END IF;
        IF EXISTS(SELECT 1 FROM public.org_native_proposal h WHERE h.tenant_id=c.tenant_id AND h.proposal_id=c.proposal_id) THEN RAISE EXCEPTION 'Native organization allocated proposal already exists' USING ERRCODE='P0001'; END IF;
    END IF;
    UPDATE org_private.enrollment_context e SET locked=true,recorded_at_ms=now_ms WHERE e.nonce=p_nonce;
    RETURN QUERY SELECT c.nonce,now_ms,c.authority_until_ms,c.command_until_ms,c.context_digest::text;
END $$;

REVOKE ALL ON FUNCTION org_private.require_runtime() FROM PUBLIC;
REVOKE ALL ON FUNCTION org_private.begin_enrollment_context(UUID,TEXT,TEXT) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.org_native_enrollment_begin_read_context(UUID,TEXT) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.org_native_enrollment_begin_proposal_read_context(UUID,TEXT) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.org_native_enrollment_begin_mutation_context(UUID,TEXT) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.org_native_enrollment_check_context(UUID) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.org_native_enrollment_lock_context(UUID) FROM PUBLIC;

REVOKE ALL ON FUNCTION public.org_native_enrollment_begin_preparation_read_context(UUID,TEXT) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.org_native_enrollment_begin_preparation_context(UUID,TEXT) FROM PUBLIC;
-- END SOURCE SECTION 06-context.sql

-- BEGIN SOURCE SECTION 07-mapped-guards.sql
-- Guard/witness SOURCE candidate for the first six proposal operations. No enrollment-owner writes are admitted.
CREATE FUNCTION org_private.current_enrollment_context() RETURNS org_private.enrollment_context
LANGUAGE plpgsql VOLATILE SECURITY DEFINER SET search_path=pg_catalog,pg_temp AS $$
DECLARE value org_private.enrollment_context%ROWTYPE; nonce_value UUID;
BEGIN
    PERFORM org_private.require_runtime();
    BEGIN nonce_value:=current_setting('firefly.organization.enrollment_nonce',true)::uuid;
    EXCEPTION WHEN invalid_text_representation THEN RAISE EXCEPTION 'Native organization context unavailable' USING ERRCODE='42501'; END;
    IF nonce_value IS NULL OR NOT public.org_native_enrollment_check_context(nonce_value) THEN RAISE EXCEPTION 'Native organization context unavailable' USING ERRCODE='42501'; END IF;
    SELECT * INTO value FROM org_private.enrollment_context c WHERE c.nonce=nonce_value AND c.xid=pg_current_xact_id() AND c.backend_pid=pg_backend_pid() AND c.db_session_user=session_user;
    IF NOT FOUND THEN RAISE EXCEPTION 'Native organization context unavailable' USING ERRCODE='42501'; END IF;
    RETURN value;
END $$;

CREATE FUNCTION public.org_native_enrollment_read_scope(p_tenant UUID,p_bank UUID,p_proposal UUID,p_revision BIGINT,p_action UUID,p_relation TEXT) RETURNS BOOLEAN
LANGUAGE plpgsql VOLATILE SECURITY DEFINER SET search_path=pg_catalog,pg_temp AS $$
DECLARE c org_private.enrollment_context%ROWTYPE;
BEGIN
    c:=org_private.current_enrollment_context();
    IF p_tenant IS DISTINCT FROM c.tenant_id OR p_bank IS NOT NULL AND p_bank IS DISTINCT FROM c.bank_id THEN RETURN false; END IF;
    IF c.purpose='ACTION_READ' THEN
        IF p_relation IN ('org_native_action','org_native_receipt') THEN RETURN p_action IS NOT DISTINCT FROM c.action_id; END IF;
        IF p_relation='org_native_proposal_revision' THEN
            RETURN EXISTS(SELECT 1 FROM public.org_native_action a WHERE a.tenant_id=c.tenant_id AND a.action_id=c.action_id AND a.bank_id=c.bank_id AND a.proposal_id=p_proposal AND a.proposal_revision=p_revision);
        END IF;
        RETURN false;
    END IF;
    IF p_relation IN ('org_native_proposal','org_native_proposal_revision','org_native_proposal_control') THEN
        RETURN p_proposal IS NOT DISTINCT FROM c.proposal_id AND (p_revision IS NULL OR p_revision=c.proposal_revision);
    END IF;
    IF p_relation IN ('org_native_action','org_native_receipt','org_native_decision','org_native_audit','org_native_outbox') AND c.purpose IN ('MUTATION','PREPARATION_MUTATION') THEN RETURN p_action IS NOT DISTINCT FROM c.action_id; END IF;
    RETURN false;
END $$;

CREATE FUNCTION public.org_native_enrollment_write_scope(p_tenant UUID,p_proposal UUID,p_action UUID,p_relation TEXT) RETURNS BOOLEAN
LANGUAGE plpgsql VOLATILE SECURITY DEFINER SET search_path=pg_catalog,pg_temp AS $$
DECLARE c org_private.enrollment_context%ROWTYPE;
BEGIN
    c:=org_private.current_enrollment_context();
    IF c.purpose='PREPARATION_MUTATION' THEN
        RETURN p_tenant IS NOT DISTINCT FROM c.tenant_id AND (p_proposal IS NULL OR p_proposal IS NOT DISTINCT FROM c.proposal_id)
            AND (p_action IS NULL OR p_action IS NOT DISTINCT FROM c.action_id)
            AND p_relation IN ('org_native_proposal','org_native_proposal_revision','org_native_proposal_control','org_native_action','org_native_receipt','org_native_audit','org_native_outbox','org_native_outbox_delivery');
    END IF;
    RETURN c.purpose IN ('MUTATION','PREPARATION_MUTATION') AND p_tenant IS NOT DISTINCT FROM c.tenant_id
        AND (p_proposal IS NULL OR p_proposal IS NOT DISTINCT FROM c.proposal_id)
        AND (p_action IS NULL OR p_action IS NOT DISTINCT FROM c.action_id)
        AND p_relation IN ('org_native_proposal_control','org_native_decision','org_native_action','org_native_receipt','org_native_audit','org_native_outbox','org_native_outbox_delivery');
END $$;

CREATE FUNCTION public.org_native_enrollment_guard_row() RETURNS TRIGGER
LANGUAGE plpgsql VOLATILE SECURITY DEFINER SET search_path=pg_catalog,pg_temp AS $$
DECLARE c org_private.enrollment_context%ROWTYPE; next_row JSONB; old_row JSONB; target_value JSON; command_value JSON;
    next_state TEXT; expected_approval UUID; expected_approval_until BIGINT;
BEGIN
    c:=org_private.current_enrollment_context();
    IF c.purpose='PREPARATION_MUTATION' THEN
        IF TG_TABLE_SCHEMA<>'public' OR TG_OP<>'INSERT' OR TG_TABLE_NAME NOT IN ('org_native_proposal','org_native_proposal_revision','org_native_proposal_control','org_native_action','org_native_receipt','org_native_audit','org_native_outbox','org_native_outbox_delivery') THEN RAISE EXCEPTION 'Native organization preparation is insert-only' USING ERRCODE='42501'; END IF;
        next_row:=to_jsonb(NEW);
        IF next_row->>'tenant_id' IS DISTINCT FROM c.tenant_id::text OR next_row ? 'bank_id' AND next_row->>'bank_id' IS DISTINCT FROM c.bank_id::text
                OR next_row ? 'proposal_id' AND next_row->>'proposal_id' IS DISTINCT FROM c.proposal_id::text
                OR next_row ? 'action_id' AND next_row->>'action_id' IS DISTINCT FROM c.action_id::text
                OR next_row ? 'recorded_at_ms' AND (next_row->>'recorded_at_ms')::bigint IS DISTINCT FROM c.recorded_at_ms THEN RAISE EXCEPTION 'Native organization preparation row scope denied' USING ERRCODE='42501'; END IF;
        IF TG_TABLE_NAME='org_native_proposal_control' AND next_row IS DISTINCT FROM jsonb_build_object('tenant_id',c.tenant_id,'proposal_id',c.proposal_id,'state','DRAFT','current_revision',1,'control_version',0,'approval_action_id',NULL,'approval_until_ms',NULL,'last_action_id',c.action_id) THEN RAISE EXCEPTION 'Native organization initial draft control denied' USING ERRCODE='42501'; END IF;
        IF TG_TABLE_NAME='org_native_proposal_revision' AND (next_row->>'revision')::bigint IS DISTINCT FROM c.proposal_revision THEN RAISE EXCEPTION 'Native organization initial revision denied' USING ERRCODE='42501'; END IF;
        IF TG_TABLE_NAME='org_native_proposal' AND next_row->>'prepare_action_id' IS DISTINCT FROM c.action_id::text THEN RAISE EXCEPTION 'Native organization prepare action denied' USING ERRCODE='42501'; END IF;
        IF TG_TABLE_NAME='org_native_outbox_delivery' AND (NOT EXISTS(SELECT 1 FROM public.org_native_outbox e WHERE e.tenant_id=c.tenant_id AND e.action_id=c.action_id AND e.event_id=(next_row->>'event_id')::uuid AND e.bank_id=c.bank_id)
                OR next_row IS DISTINCT FROM jsonb_build_object('tenant_id',c.tenant_id,'event_id',(next_row->>'event_id')::uuid,'control_version',0,'state','PENDING','attempt_count',0,'lease_owner',NULL,'lease_until_ms',NULL,'next_attempt_ms',c.recorded_at_ms,'last_error_code',NULL,'last_ack_at_ms',NULL)) THEN RAISE EXCEPTION 'Native organization initial delivery denied' USING ERRCODE='42501'; END IF;
        RETURN NEW;
    END IF;
    IF c.purpose<>'MUTATION' OR TG_TABLE_SCHEMA<>'public' OR TG_OP NOT IN ('INSERT','UPDATE') THEN RAISE EXCEPTION 'Native organization immutable mutation denied' USING ERRCODE='42501'; END IF;
    next_row:=to_jsonb(NEW);IF TG_OP='UPDATE' THEN old_row:=to_jsonb(OLD); END IF;
    IF next_row->>'tenant_id' IS DISTINCT FROM c.tenant_id::text THEN RAISE EXCEPTION 'Native organization row scope denied' USING ERRCODE='42501'; END IF;
    target_value:=(convert_from(c.full_context,'UTF8')::json)->'target';command_value:=(target_value->>'originalCommandJson')::json;
    IF TG_TABLE_NAME='org_native_proposal_control' THEN
        IF TG_OP<>'UPDATE' OR next_row->>'proposal_id' IS DISTINCT FROM c.proposal_id::text OR old_row->>'proposal_id' IS DISTINCT FROM c.proposal_id::text
                OR old_row->>'tenant_id' IS DISTINCT FROM c.tenant_id::text OR (old_row->>'control_version')::bigint IS DISTINCT FROM (command_value->>'expectedControlVersion')::bigint
                OR (old_row->>'current_revision')::bigint IS DISTINCT FROM c.proposal_revision OR (old_row->>'control_version')::bigint>=9223372036854775806 THEN
            RAISE EXCEPTION 'Native organization control compare-and-set denied' USING ERRCODE='42501';
        END IF;
        next_state:=CASE c.operation WHEN 'SUBMIT' THEN 'SUBMITTED' WHEN 'APPROVE' THEN 'APPROVED' WHEN 'REJECT' THEN 'REJECTED' WHEN 'WITHDRAW' THEN 'WITHDRAWN' WHEN 'CANCEL' THEN 'CANCELLED' WHEN 'CANCEL_EMERGENCY' THEN 'CANCELLED' ELSE NULL END;
        IF next_state IS NULL OR (c.operation='SUBMIT' AND old_row->>'state'<>'DRAFT') OR (c.operation IN ('APPROVE','REJECT') AND old_row->>'state'<>'SUBMITTED')
                OR (c.operation='WITHDRAW' AND old_row->>'state' NOT IN ('SUBMITTED','APPROVED')) OR (c.operation IN ('CANCEL','CANCEL_EMERGENCY') AND old_row->>'state' NOT IN ('DRAFT','SUBMITTED','APPROVED','REJECTED','WITHDRAWN')) THEN
            RAISE EXCEPTION 'Native organization control transition denied' USING ERRCODE='42501';
        END IF;
        expected_approval:=CASE WHEN c.operation='APPROVE' THEN c.action_id ELSE NULL END;
        expected_approval_until:=CASE WHEN c.operation='APPROVE' THEN (command_value->>'approvalUntilEpochMillis')::bigint ELSE NULL END;
        IF next_row IS DISTINCT FROM (old_row||jsonb_build_object('state',next_state,'control_version',(old_row->>'control_version')::bigint+1,'last_action_id',c.action_id,'approval_action_id',expected_approval,'approval_until_ms',expected_approval_until)) THEN
            RAISE EXCEPTION 'Native organization control shape denied' USING ERRCODE='42501';
        END IF;
    ELSE
        IF TG_OP<>'INSERT' OR TG_TABLE_NAME NOT IN ('org_native_decision','org_native_action','org_native_receipt','org_native_audit','org_native_outbox','org_native_outbox_delivery') THEN
            RAISE EXCEPTION 'Native organization immutable mutation denied' USING ERRCODE='42501';
        END IF;
        IF TG_TABLE_NAME='org_native_outbox_delivery' THEN
            IF NOT EXISTS(SELECT 1 FROM public.org_native_outbox e WHERE e.tenant_id=c.tenant_id AND e.action_id=c.action_id AND e.event_id=(next_row->>'event_id')::uuid AND e.bank_id=c.bank_id)
                    OR next_row IS DISTINCT FROM jsonb_build_object('tenant_id',c.tenant_id,'event_id',(next_row->>'event_id')::uuid,'control_version',0,'state','PENDING','attempt_count',0,'lease_owner',NULL,'lease_until_ms',NULL,'next_attempt_ms',c.recorded_at_ms,'last_error_code',NULL,'last_ack_at_ms',NULL) THEN
                RAISE EXCEPTION 'Native organization initial delivery denied' USING ERRCODE='42501';
            END IF;
        ELSIF next_row->>'action_id' IS DISTINCT FROM c.action_id::text OR (next_row ? 'bank_id' AND next_row->>'bank_id' IS DISTINCT FROM c.bank_id::text)
                OR (next_row ? 'proposal_id' AND next_row->>'proposal_id' IS DISTINCT FROM c.proposal_id::text) THEN
            RAISE EXCEPTION 'Native organization immutable row scope denied' USING ERRCODE='42501';
        END IF;
        IF TG_TABLE_NAME<>'org_native_outbox_delivery' AND (next_row->>'recorded_at_ms')::bigint IS DISTINCT FROM c.recorded_at_ms THEN RAISE EXCEPTION 'Native organization retained clock denied' USING ERRCODE='42501'; END IF;
    END IF;
    RETURN NEW;
END $$;

CREATE FUNCTION public.org_native_enrollment_witness_row() RETURNS TRIGGER
LANGUAGE plpgsql VOLATILE SECURITY DEFINER SET search_path=pg_catalog,pg_temp AS $$
DECLARE c org_private.enrollment_context%ROWTYPE; before_row JSONB; after_row JSONB; row_key JSONB;
BEGIN
    c:=org_private.current_enrollment_context();
    IF TG_OP='UPDATE' THEN before_row:=to_jsonb(OLD); END IF;after_row:=to_jsonb(NEW);
    row_key:=CASE WHEN TG_TABLE_NAME IN ('org_native_proposal','org_native_proposal_control') THEN jsonb_build_object('tenantId',c.tenant_id,'proposalId',c.proposal_id)
        WHEN TG_TABLE_NAME='org_native_proposal_revision' THEN jsonb_build_object('tenantId',c.tenant_id,'proposalId',c.proposal_id,'revision',c.proposal_revision)
        WHEN TG_TABLE_NAME='org_native_outbox_delivery' THEN jsonb_build_object('tenantId',c.tenant_id,'eventId',after_row->>'event_id')
        ELSE jsonb_build_object('tenantId',c.tenant_id,'actionId',c.action_id) END;
    INSERT INTO org_private.enrollment_row_witness(context_nonce,relation_kind,row_key,operation,old_row,new_row) VALUES(c.nonce,TG_TABLE_NAME,row_key,TG_OP,before_row,after_row);
    RETURN NULL;
END $$;

CREATE FUNCTION public.org_native_enrollment_deferred_check() RETURNS TRIGGER
LANGUAGE plpgsql VOLATILE SECURITY DEFINER SET search_path=pg_catalog,pg_temp AS $$
DECLARE c org_private.enrollment_context%ROWTYPE;
BEGIN
    c:=org_private.current_enrollment_context();
    PERFORM org_private.validate_enrollment_graph(c.nonce);
    RETURN NULL;
END $$;
REVOKE ALL ON FUNCTION org_private.current_enrollment_context() FROM PUBLIC;
REVOKE ALL ON FUNCTION public.org_native_enrollment_read_scope(UUID,UUID,UUID,BIGINT,UUID,TEXT) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.org_native_enrollment_write_scope(UUID,UUID,UUID,TEXT) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.org_native_enrollment_guard_row() FROM PUBLIC;
REVOKE ALL ON FUNCTION public.org_native_enrollment_witness_row() FROM PUBLIC;
REVOKE ALL ON FUNCTION public.org_native_enrollment_deferred_check() FROM PUBLIC;

CREATE FUNCTION public.org_native_enrollment_delivery_scope(p_tenant UUID,p_event UUID) RETURNS BOOLEAN
LANGUAGE plpgsql VOLATILE SECURITY DEFINER SET search_path=pg_catalog,pg_temp AS $$
DECLARE c org_private.enrollment_context%ROWTYPE;
BEGIN
    c:=org_private.current_enrollment_context();
    RETURN c.purpose IN ('MUTATION','PREPARATION_MUTATION') AND p_tenant IS NOT DISTINCT FROM c.tenant_id
        AND EXISTS(SELECT 1 FROM public.org_native_outbox e WHERE e.tenant_id=c.tenant_id AND e.action_id=c.action_id AND e.event_id=p_event AND e.bank_id=c.bank_id);
END $$;
REVOKE ALL ON FUNCTION public.org_native_enrollment_delivery_scope(UUID,UUID) FROM PUBLIC;

CREATE FUNCTION public.org_native_preparation_parent_read_scope(p_bank UUID,p_tenant UUID,p_enrollment UUID,p_version BIGINT,p_relation TEXT) RETURNS BOOLEAN
LANGUAGE plpgsql VOLATILE SECURITY DEFINER SET search_path=pg_catalog,pg_temp AS $$
DECLARE c org_private.enrollment_context%ROWTYPE; gate_row public.org_native_bank_gate%ROWTYPE; target_value JSON; desired JSON;
BEGIN
    c:=org_private.current_enrollment_context();
    IF c.purpose NOT IN ('PREPARATION_DISCOVERY','PREPARATION_MUTATION') THEN RETURN false; END IF;
    IF p_bank IS NOT NULL AND p_bank IS DISTINCT FROM c.bank_id THEN RETURN false; END IF;
    IF p_relation='BANK_GATE' THEN RETURN p_bank IS NOT DISTINCT FROM c.bank_id AND p_tenant IS NULL AND p_enrollment IS NULL AND p_version IS NULL; END IF;
    IF p_relation IS NULL OR p_relation NOT IN ('OWNER_IDENTITY','OWNER_CONTROL') OR p_tenant IS NULL OR p_enrollment IS NULL OR p_version IS NULL THEN RETURN false; END IF;
    target_value:=(convert_from(c.full_context,'UTF8')::json)->'target';
    IF c.purpose='PREPARATION_DISCOVERY' THEN desired:=(target_value->>'originalDesiredJson')::json;
    ELSE desired:=(((target_value->>'originalCommandJson')::json)->>'originalDesiredJson')::json; END IF;
    IF desired->>'mode'<>'TRANSFER' THEN RETURN false; END IF;
    SELECT * INTO gate_row FROM public.org_native_bank_gate g WHERE g.bank_id=c.bank_id;
    RETURN FOUND AND p_tenant IS NOT DISTINCT FROM gate_row.current_tenant_id AND p_enrollment IS NOT DISTINCT FROM gate_row.current_enrollment_id AND p_version IS NOT DISTINCT FROM gate_row.current_enrollment_version;
END $$;
REVOKE ALL ON FUNCTION public.org_native_preparation_parent_read_scope(UUID,UUID,UUID,BIGINT,TEXT) FROM PUBLIC;
-- END SOURCE SECTION 07-mapped-guards.sql

-- BEGIN SOURCE SECTION 08-integrity.sql
-- Actual-row complete-graph SOURCE candidate. Not executed or qualified against PostgreSQL yet.
CREATE FUNCTION public.org_native_proposal_result_token(p_value JSON) RETURNS TEXT
LANGUAGE plpgsql IMMUTABLE SET search_path=pg_catalog,pg_temp AS $$
BEGIN
    PERFORM public.org_native_shape(p_value,ARRAY['schemaVersion','actionId','tenantId','bankId','proposalId','revision','operation','state','controlVersion','targetDigest','receiptId','auditId','outboxEventId','recordedAtEpochMillis']);
    RETURN 'R14:'||public.org_native_long(p_value->'schemaVersion',2,2)||public.org_native_uuid(p_value->'actionId')||public.org_native_uuid(p_value->'tenantId')||public.org_native_uuid(p_value->'bankId')||public.org_native_uuid(p_value->'proposalId')||public.org_native_long(p_value->'revision',1,9223372036854775806)||public.org_native_string(p_value->'operation',32)||public.org_native_string(p_value->'state',32)||public.org_native_long(p_value->'controlVersion',1,9223372036854775806)||public.org_native_digest(p_value->'targetDigest')||public.org_native_uuid(p_value->'receiptId')||public.org_native_uuid(p_value->'auditId')||public.org_native_uuid(p_value->'outboxEventId')||public.org_native_long(p_value->'recordedAtEpochMillis',1,253402300799999);
END $$;

CREATE FUNCTION public.org_native_proposal_event_wire(p_packet JSON,p_value JSON,p_result JSON,p_now BIGINT) RETURNS JSONB
LANGUAGE plpgsql IMMUTABLE SET search_path=pg_catalog,pg_temp AS $$
DECLARE target_value JSON; event_value JSON; encoding_value TEXT; type_value TEXT; canonical TEXT; result_token TEXT; actor_token TEXT; op TEXT;
    observed RECORD; observed_until BIGINT; command_encoding CONSTANT TEXT:='FF_ORGANIZATION_BANK_ENROLLMENT_REVISION_COMMAND_V2';
    revision_encoding CONSTANT TEXT:='FF_ORGANIZATION_BANK_ENROLLMENT_REVISION_V2';
BEGIN
    target_value:=p_packet->'target';op:=target_value->>'operation';
    IF op IN ('SUBMIT','APPROVE') THEN
        PERFORM public.org_native_shape(p_value,ARRAY['proposal','observations','observationsUntilEpochMillis']);event_value:=p_value->'proposal';
        encoding_value:='FF_ORGANIZATION_BANK_OBSERVED_PROPOSAL_EVENT_V2';type_value:='organization.bank.enrollment.proposal.'||CASE op WHEN 'SUBMIT' THEN 'submitted' ELSE 'approved' END;
        SELECT * INTO observed FROM public.org_native_observed_packets((target_value->>'originalRevisionJson')::json,p_value->'observations',p_now);
        PERFORM public.org_native_long(p_value->'observationsUntilEpochMillis',1,253402300799999);observed_until:=(p_value->>'observationsUntilEpochMillis')::bigint;
        IF (p_value->'observations')::jsonb IS DISTINCT FROM (p_packet->'observations')::jsonb OR observed_until IS DISTINCT FROM (p_packet->>'observationsUntilEpochMillis')::bigint THEN PERFORM public.org_native_invalid(); END IF;
    ELSE event_value:=p_value;encoding_value:='FF_ORGANIZATION_BANK_PROPOSAL_EVENT_V2';type_value:='organization.bank.enrollment.proposal.terminated'; END IF;
    PERFORM public.org_native_shape(event_value,ARRAY['schemaVersion','eventType','result','actor','commandEncoding','originalCommandJson','canonicalCommand','commandDigest','revisionEncoding','originalRevisionJson','canonicalRevision','revisionDigest']);
    PERFORM public.org_native_long(event_value->'schemaVersion',2,2);result_token:=public.org_native_proposal_result_token(event_value->'result');actor_token:=public.org_native_actor_token(event_value->'actor');
    IF (event_value->'result')::jsonb IS DISTINCT FROM p_result::jsonb OR (event_value->'actor')::jsonb IS DISTINCT FROM (target_value->'actor')::jsonb
            OR event_value->>'eventType' IS DISTINCT FROM type_value OR event_value->>'commandEncoding' IS DISTINCT FROM command_encoding
            OR event_value->>'revisionEncoding' IS DISTINCT FROM revision_encoding OR event_value->>'originalCommandJson' IS DISTINCT FROM target_value->>'originalCommandJson'
            OR event_value->>'canonicalCommand' IS DISTINCT FROM target_value->>'canonicalCommand' OR event_value->>'commandDigest' IS DISTINCT FROM target_value->>'commandDigest'
            OR event_value->>'originalRevisionJson' IS DISTINCT FROM target_value->>'originalRevisionJson' OR event_value->>'canonicalRevision' IS DISTINCT FROM target_value->>'canonicalRevision'
            OR event_value->>'revisionDigest' IS DISTINCT FROM target_value->>'revisionDigest' THEN PERFORM public.org_native_invalid(); END IF;
    canonical:=public.org_native_s(encoding_value)||CASE WHEN op IN ('SUBMIT','APPROVE') THEN 'R11:' ELSE 'R10:' END
        ||public.org_native_long(event_value->'schemaVersion',2,2)||public.org_native_s(type_value)||result_token||actor_token||public.org_native_s(command_encoding)||public.org_native_s(target_value->>'canonicalCommand')||public.org_native_s(target_value->>'commandDigest')||public.org_native_s(revision_encoding)||public.org_native_s(target_value->>'canonicalRevision')||public.org_native_s(target_value->>'revisionDigest');
    IF op IN ('SUBMIT','APPROVE') THEN canonical:=canonical||public.org_native_long(p_value->'observationsUntilEpochMillis',1,253402300799999)||observed.canonical_observations; END IF;
    RETURN jsonb_build_object('encoding',encoding_value,'canonical',canonical,'digest',encode(sha256(convert_to(canonical,'UTF8')),'hex'));
END $$;

CREATE FUNCTION org_private.validate_enrollment_graph(p_nonce UUID) RETURNS VOID
LANGUAGE plpgsql VOLATILE SECURITY DEFINER SET search_path=pg_catalog,pg_temp AS $$
DECLARE c org_private.enrollment_context%ROWTYPE; packet JSON; target_value JSON; command_value JSON; actor_value JSON;
    action_row public.org_native_action%ROWTYPE; receipt public.org_native_receipt%ROWTYPE; audit_row public.org_native_audit%ROWTYPE;
    event_row public.org_native_outbox%ROWTYPE; delivery public.org_native_outbox_delivery%ROWTYPE; decision_row public.org_native_decision%ROWTYPE;
    control_row public.org_native_proposal_control%ROWTYPE; control_witness org_private.enrollment_row_witness%ROWTYPE;
    result_value JSON; result_canonical TEXT; event_wire JSONB; expected_state TEXT; witness RECORD; actual_final JSONB;
    expected_decisions INTEGER; expected_total INTEGER; now_ms BIGINT;
BEGIN
    c:=org_private.current_enrollment_context();
    IF c.purpose='PREPARATION_MUTATION' THEN PERFORM org_private.validate_preparation_graph(p_nonce);RETURN; END IF;
    IF c.nonce IS DISTINCT FROM p_nonce OR c.purpose<>'MUTATION' THEN RAISE EXCEPTION 'Native organization mutation graph required' USING ERRCODE='42501'; END IF;
    now_ms:=floor(extract(epoch FROM clock_timestamp())*1000)::bigint;packet:=convert_from(c.full_context,'UTF8')::json;target_value:=packet->'target';command_value:=(target_value->>'originalCommandJson')::json;actor_value:=target_value->'actor';
    PERFORM public.org_native_context_packet(p_nonce,convert_from(c.full_context,'UTF8'),c.purpose,now_ms);
    expected_decisions:=CASE WHEN c.operation IN ('APPROVE','REJECT','WITHDRAW') THEN 1 ELSE 0 END;expected_total:=6+expected_decisions;
    IF (SELECT count(*) FROM org_private.enrollment_row_witness w WHERE w.context_nonce=c.nonce)<>expected_total
            OR EXISTS(SELECT 1 FROM org_private.enrollment_row_witness w WHERE w.context_nonce=c.nonce AND w.relation_kind NOT IN ('org_native_proposal_control','org_native_decision','org_native_action','org_native_receipt','org_native_audit','org_native_outbox','org_native_outbox_delivery')) THEN
        RAISE EXCEPTION 'Native organization graph witness count mismatch' USING ERRCODE='P0001';
    END IF;
    FOR witness IN SELECT w.relation_kind,count(*) AS total FROM org_private.enrollment_row_witness w WHERE w.context_nonce=c.nonce GROUP BY w.relation_kind LOOP
        IF witness.total<>1 THEN RAISE EXCEPTION 'Native organization repeated graph mutation' USING ERRCODE='P0001'; END IF;
    END LOOP;
    IF (SELECT count(*) FROM org_private.enrollment_row_witness w WHERE w.context_nonce=c.nonce AND w.relation_kind='org_native_decision')<>expected_decisions
            OR EXISTS(SELECT 1 FROM org_private.enrollment_row_witness w WHERE w.context_nonce=c.nonce AND ((w.relation_kind='org_native_proposal_control' AND (w.operation<>'UPDATE' OR w.old_row IS NULL)) OR (w.relation_kind<>'org_native_proposal_control' AND (w.operation<>'INSERT' OR w.old_row IS NOT NULL)))) THEN
        RAISE EXCEPTION 'Native organization graph operation mismatch' USING ERRCODE='P0001';
    END IF;
    SELECT * INTO control_witness FROM org_private.enrollment_row_witness w WHERE w.context_nonce=c.nonce AND w.relation_kind='org_native_proposal_control';
    SELECT * INTO control_row FROM public.org_native_proposal_control p WHERE p.tenant_id=c.tenant_id AND p.proposal_id=c.proposal_id;
    IF NOT FOUND OR control_witness.new_row IS DISTINCT FROM to_jsonb(control_row) OR control_row.last_action_id IS DISTINCT FROM c.action_id
            OR control_row.current_revision IS DISTINCT FROM c.proposal_revision OR control_row.control_version IS DISTINCT FROM (command_value->>'expectedControlVersion')::bigint+1 THEN
        RAISE EXCEPTION 'Native organization final proposal control mismatch' USING ERRCODE='P0001';
    END IF;
    expected_state:=CASE c.operation WHEN 'SUBMIT' THEN 'SUBMITTED' WHEN 'APPROVE' THEN 'APPROVED' WHEN 'REJECT' THEN 'REJECTED' WHEN 'WITHDRAW' THEN 'WITHDRAWN' ELSE 'CANCELLED' END;
    IF control_row.state IS DISTINCT FROM expected_state THEN RAISE EXCEPTION 'Native organization final proposal state mismatch' USING ERRCODE='P0001'; END IF;
    SELECT * INTO action_row FROM public.org_native_action a WHERE a.tenant_id=c.tenant_id AND a.action_id=c.action_id;
    IF NOT FOUND OR action_row.bank_id IS DISTINCT FROM c.bank_id OR action_row.proposal_id IS DISTINCT FROM c.proposal_id OR action_row.proposal_revision IS DISTINCT FROM c.proposal_revision
            OR action_row.target_digest IS DISTINCT FROM target_value->>'revisionDigest' OR action_row.intended_operation IS DISTINCT FROM c.operation
            OR action_row.intended_route IS DISTINCT FROM target_value->>'intendedRoute' OR action_row.request_encoding IS DISTINCT FROM 'FF_ORGANIZATION_BANK_ENROLLMENT_REVISION_COMMAND_V2'
            OR convert_from(action_row.request_original,'UTF8') IS DISTINCT FROM target_value->>'originalCommandJson' OR action_row.request_canonical IS DISTINCT FROM target_value->>'canonicalCommand'
            OR action_row.request_digest IS DISTINCT FROM target_value->>'commandDigest' OR action_row.resolution_kind IS DISTINCT FROM 'COMMAND' OR action_row.resolution_original IS DISTINCT FROM action_row.request_original
            OR action_row.resolution_encoding IS DISTINCT FROM action_row.request_encoding OR action_row.resolution_canonical IS DISTINCT FROM action_row.request_canonical OR action_row.resolution_digest IS DISTINCT FROM action_row.request_digest
            OR action_row.outcome IS DISTINCT FROM 'APPLIED' OR action_row.recorded_at_ms IS DISTINCT FROM c.recorded_at_ms
            OR ROW(action_row.resolver_kind,action_row.resolver_issuer,action_row.resolver_subject,action_row.resolver_tenant_id::text,action_row.resolver_staff_realm,action_row.resolver_staff_id::text,action_row.resolver_client_id)
                IS DISTINCT FROM ROW(actor_value->>'kind',actor_value->>'issuer',actor_value->>'subject',actor_value->>'tenantId',actor_value->>'staffRealm',actor_value->>'staffActorId',actor_value->>'clientId') THEN
        RAISE EXCEPTION 'Native organization final action mismatch' USING ERRCODE='P0001';
    END IF;
    SELECT * INTO receipt FROM public.org_native_receipt r WHERE r.tenant_id=c.tenant_id AND r.action_id=c.action_id;
    IF NOT FOUND OR receipt.result_encoding<>'FF_ORGANIZATION_BANK_PROPOSAL_RESULT_V2' OR receipt.recorded_at_ms IS DISTINCT FROM c.recorded_at_ms THEN RAISE EXCEPTION 'Native organization receipt missing' USING ERRCODE='P0001'; END IF;
    result_value:=convert_from(receipt.result_original,'UTF8')::json;result_canonical:=public.org_native_s('FF_ORGANIZATION_BANK_PROPOSAL_RESULT_V2')||public.org_native_proposal_result_token(result_value);
    IF receipt.result_canonical IS DISTINCT FROM result_canonical OR receipt.result_digest IS DISTINCT FROM encode(sha256(convert_to(result_canonical,'UTF8')),'hex')
            OR result_value->>'actionId' IS DISTINCT FROM c.action_id::text OR result_value->>'tenantId' IS DISTINCT FROM c.tenant_id::text OR result_value->>'bankId' IS DISTINCT FROM c.bank_id::text
            OR result_value->>'proposalId' IS DISTINCT FROM c.proposal_id::text OR (result_value->>'revision')::bigint IS DISTINCT FROM c.proposal_revision OR result_value->>'operation' IS DISTINCT FROM c.operation
            OR result_value->>'state' IS DISTINCT FROM expected_state OR (result_value->>'controlVersion')::bigint IS DISTINCT FROM control_row.control_version
            OR result_value->>'targetDigest' IS DISTINCT FROM target_value->>'revisionDigest' OR result_value->>'receiptId' IS DISTINCT FROM receipt.receipt_id::text OR (result_value->>'recordedAtEpochMillis')::bigint IS DISTINCT FROM c.recorded_at_ms THEN
        RAISE EXCEPTION 'Native organization receipt binding mismatch' USING ERRCODE='P0001';
    END IF;
    SELECT * INTO audit_row FROM public.org_native_audit a WHERE a.tenant_id=c.tenant_id AND a.action_id=c.action_id;
    IF NOT FOUND OR audit_row.bank_id IS DISTINCT FROM c.bank_id OR audit_row.recorded_at_ms IS DISTINCT FROM c.recorded_at_ms OR result_value->>'auditId' IS DISTINCT FROM audit_row.audit_id::text THEN RAISE EXCEPTION 'Native organization audit missing' USING ERRCODE='P0001'; END IF;
    event_wire:=public.org_native_proposal_event_wire(packet,convert_from(audit_row.event_original,'UTF8')::json,result_value,now_ms);
    IF audit_row.event_encoding IS DISTINCT FROM event_wire->>'encoding' OR audit_row.event_canonical IS DISTINCT FROM event_wire->>'canonical' OR audit_row.event_digest IS DISTINCT FROM event_wire->>'digest' THEN RAISE EXCEPTION 'Native organization audit binding mismatch' USING ERRCODE='P0001'; END IF;
    SELECT * INTO event_row FROM public.org_native_outbox o WHERE o.tenant_id=c.tenant_id AND o.action_id=c.action_id;
    IF NOT FOUND OR event_row.bank_id IS DISTINCT FROM c.bank_id OR event_row.recorded_at_ms IS DISTINCT FROM c.recorded_at_ms OR result_value->>'outboxEventId' IS DISTINCT FROM event_row.event_id::text
            OR ROW(event_row.event_original,event_row.event_encoding,event_row.event_canonical,event_row.event_digest) IS DISTINCT FROM ROW(audit_row.event_original,audit_row.event_encoding,audit_row.event_canonical,audit_row.event_digest) THEN
        RAISE EXCEPTION 'Native organization outbox binding mismatch' USING ERRCODE='P0001';
    END IF;
    SELECT * INTO delivery FROM public.org_native_outbox_delivery d WHERE d.tenant_id=c.tenant_id AND d.event_id=event_row.event_id;
    IF NOT FOUND OR to_jsonb(delivery) IS DISTINCT FROM jsonb_build_object('tenant_id',c.tenant_id,'event_id',event_row.event_id,'control_version',0,'state','PENDING','attempt_count',0,'lease_owner',NULL,'lease_until_ms',NULL,'next_attempt_ms',c.recorded_at_ms,'last_error_code',NULL,'last_ack_at_ms',NULL) THEN
        RAISE EXCEPTION 'Native organization initial delivery missing' USING ERRCODE='P0001';
    END IF;
    IF expected_decisions=1 THEN
        SELECT * INTO decision_row FROM public.org_native_decision d WHERE d.tenant_id=c.tenant_id AND d.action_id=c.action_id;
        IF NOT FOUND OR decision_row.proposal_id IS DISTINCT FROM c.proposal_id OR decision_row.proposal_revision IS DISTINCT FROM c.proposal_revision OR decision_row.target_digest IS DISTINCT FROM target_value->>'revisionDigest'
                OR decision_row.decision IS DISTINCT FROM c.operation OR decision_row.recorded_at_ms IS DISTINCT FROM c.recorded_at_ms OR decision_row.reason IS DISTINCT FROM command_value->>'reason'
                OR decision_row.approval_until_ms IS DISTINCT FROM (CASE WHEN c.operation='APPROVE' THEN (command_value->>'approvalUntilEpochMillis')::bigint ELSE NULL END)
                OR ROW(decision_row.decision_actor_kind,decision_row.decision_actor_issuer,decision_row.decision_actor_subject,decision_row.decision_actor_tenant_id::text,decision_row.decision_actor_staff_realm,decision_row.decision_actor_staff_id::text,decision_row.decision_actor_client_id)
                    IS DISTINCT FROM ROW(actor_value->>'kind',actor_value->>'issuer',actor_value->>'subject',actor_value->>'tenantId',actor_value->>'staffRealm',actor_value->>'staffActorId',actor_value->>'clientId') THEN
            RAISE EXCEPTION 'Native organization checker decision binding mismatch' USING ERRCODE='P0001';
        END IF;
        IF c.operation='APPROVE' AND (control_row.approval_action_id IS DISTINCT FROM c.action_id OR control_row.approval_until_ms IS DISTINCT FROM decision_row.approval_until_ms) THEN RAISE EXCEPTION 'Native organization retained approval mismatch' USING ERRCODE='P0001'; END IF;
    ELSIF EXISTS(SELECT 1 FROM public.org_native_decision d WHERE d.tenant_id=c.tenant_id AND d.action_id=c.action_id) THEN RAISE EXCEPTION 'Native organization unexpected decision' USING ERRCODE='P0001'; END IF;
    FOR witness IN SELECT w.* FROM org_private.enrollment_row_witness w WHERE w.context_nonce=c.nonce LOOP
        CASE witness.relation_kind
            WHEN 'org_native_proposal_control' THEN actual_final:=to_jsonb(control_row);
            WHEN 'org_native_decision' THEN actual_final:=to_jsonb(decision_row);
            WHEN 'org_native_action' THEN actual_final:=to_jsonb(action_row);
            WHEN 'org_native_receipt' THEN actual_final:=to_jsonb(receipt);
            WHEN 'org_native_audit' THEN actual_final:=to_jsonb(audit_row);
            WHEN 'org_native_outbox' THEN actual_final:=to_jsonb(event_row);
            WHEN 'org_native_outbox_delivery' THEN actual_final:=to_jsonb(delivery);
            ELSE RAISE EXCEPTION 'Native organization unexpected witness' USING ERRCODE='P0001';
        END CASE;
        IF witness.new_row IS DISTINCT FROM actual_final THEN RAISE EXCEPTION 'Native organization final-row witness mismatch' USING ERRCODE='P0001'; END IF;
    END LOOP;
    IF NOT public.org_native_enrollment_check_context(c.nonce) THEN RAISE EXCEPTION 'Native organization final graph expired' USING ERRCODE='42501'; END IF;
END $$;

CREATE FUNCTION public.org_native_enrollment_validate_complete(p_nonce UUID) RETURNS BOOLEAN
LANGUAGE plpgsql VOLATILE SECURITY DEFINER SET search_path=pg_catalog,pg_temp AS $$
BEGIN PERFORM org_private.validate_enrollment_graph(p_nonce);RETURN true;END $$;
REVOKE ALL ON FUNCTION public.org_native_proposal_result_token(JSON) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.org_native_proposal_event_wire(JSON,JSON,JSON,BIGINT) FROM PUBLIC;
REVOKE ALL ON FUNCTION org_private.validate_enrollment_graph(UUID) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.org_native_enrollment_validate_complete(UUID) FROM PUBLIC;
-- END SOURCE SECTION 08-integrity.sql

-- BEGIN SOURCE SECTION 12-preparation-integrity.sql
-- PREPARE complete-graph source; mapped application inserts remain the only business writes.
CREATE FUNCTION public.org_native_preparation_result_token(p_value JSON) RETURNS TEXT
LANGUAGE plpgsql IMMUTABLE SET search_path=pg_catalog,pg_temp AS $$
BEGIN
    PERFORM public.org_native_shape(p_value,ARRAY['schemaVersion','actionId','tenantId','bankId','proposalId','revision','operation','state','controlVersion','targetDigest','receiptId','auditId','outboxEventId','recordedAtEpochMillis']);
    IF p_value->>'operation' NOT IN ('PREPARE','TRANSFER_PREPARE') OR p_value->>'state' IS DISTINCT FROM 'DRAFT' THEN PERFORM public.org_native_invalid(); END IF;
    RETURN 'R14:'||public.org_native_long(p_value->'schemaVersion',2,2)||public.org_native_uuid(p_value->'actionId')||public.org_native_uuid(p_value->'tenantId')||public.org_native_uuid(p_value->'bankId')||public.org_native_uuid(p_value->'proposalId')||public.org_native_long(p_value->'revision',1,1)||public.org_native_string(p_value->'operation',32)||public.org_native_s('DRAFT')||public.org_native_long(p_value->'controlVersion',0,0)||public.org_native_digest(p_value->'targetDigest')||public.org_native_uuid(p_value->'receiptId')||public.org_native_uuid(p_value->'auditId')||public.org_native_uuid(p_value->'outboxEventId')||public.org_native_long(p_value->'recordedAtEpochMillis',1,253402300799999);
END $$;

CREATE FUNCTION public.org_native_preparation_event_wire(p_packet JSON,p_value JSON,p_result JSON,p_now BIGINT) RETURNS JSONB
LANGUAGE plpgsql IMMUTABLE SET search_path=pg_catalog,pg_temp AS $$
DECLARE target_value JSON; command_value JSON; revision_value JSON; result_token TEXT; actor_token TEXT; observed RECORD; canonical TEXT;
BEGIN
    target_value:=p_packet->'target';command_value:=(target_value->>'originalCommandJson')::json;revision_value:=(command_value->>'originalRevisionJson')::json;
    PERFORM public.org_native_shape(p_value,ARRAY['schemaVersion','eventType','result','actor','commandEncoding','originalCommandJson','canonicalCommand','commandDigest','observations','observationsUntilEpochMillis']);
    PERFORM public.org_native_long(p_value->'schemaVersion',2,2);result_token:=public.org_native_preparation_result_token(p_value->'result');actor_token:=public.org_native_actor_token(p_value->'actor');
    PERFORM public.org_native_long(p_value->'observationsUntilEpochMillis',1,253402300799999);
    IF p_value->>'eventType' IS DISTINCT FROM 'organization.bank.enrollment.proposal.prepared' OR (p_value->'result')::jsonb IS DISTINCT FROM p_result::jsonb
            OR (p_value->'actor')::jsonb IS DISTINCT FROM (target_value->'actor')::jsonb OR p_value->>'commandEncoding' IS DISTINCT FROM 'FF_ORGANIZATION_BANK_PREPARE_COMMAND_V2'
            OR p_value->>'originalCommandJson' IS DISTINCT FROM target_value->>'originalCommandJson' OR p_value->>'canonicalCommand' IS DISTINCT FROM target_value->>'canonicalCommand' OR p_value->>'commandDigest' IS DISTINCT FROM target_value->>'commandDigest'
            OR (p_value->'observations')::jsonb IS DISTINCT FROM (p_packet->'observations')::jsonb OR (p_value->>'observationsUntilEpochMillis')::bigint IS DISTINCT FROM (p_packet->>'observationsUntilEpochMillis')::bigint THEN PERFORM public.org_native_invalid(); END IF;
    SELECT * INTO observed FROM public.org_native_observed_packets(revision_value,p_value->'observations',p_now);
    canonical:=public.org_native_s('FF_ORGANIZATION_BANK_PREPARED_PROPOSAL_EVENT_V2')||'R8:'||public.org_native_long(p_value->'schemaVersion',2,2)||public.org_native_s('organization.bank.enrollment.proposal.prepared')||result_token||actor_token||public.org_native_s('FF_ORGANIZATION_BANK_PREPARE_COMMAND_V2')||public.org_native_s(target_value->>'canonicalCommand')||public.org_native_s(target_value->>'commandDigest')||public.org_native_long(p_value->'observationsUntilEpochMillis',1,253402300799999)||observed.canonical_observations;
    RETURN jsonb_build_object('encoding','FF_ORGANIZATION_BANK_PREPARED_PROPOSAL_EVENT_V2','canonical',canonical,'digest',encode(sha256(convert_to(canonical,'UTF8')),'hex'));
END $$;

CREATE FUNCTION org_private.validate_preparation_graph(p_nonce UUID) RETURNS VOID
LANGUAGE plpgsql VOLATILE SECURITY DEFINER SET search_path=pg_catalog,pg_temp AS $$
DECLARE c org_private.enrollment_context%ROWTYPE; packet JSON; target_value JSON; command_value JSON; desired JSON; revision_value JSON; enrollment_target JSON; actor_value JSON;
    header_row public.org_native_proposal%ROWTYPE; revision_row public.org_native_proposal_revision%ROWTYPE; control_row public.org_native_proposal_control%ROWTYPE;
    action_row public.org_native_action%ROWTYPE; receipt public.org_native_receipt%ROWTYPE; audit_row public.org_native_audit%ROWTYPE; event_row public.org_native_outbox%ROWTYPE; delivery public.org_native_outbox_delivery%ROWTYPE;
    expected JSONB; expected_result JSONB; result_value JSON; result_token TEXT; result_canonical TEXT; event_wire JSONB; actual_rows JSONB; witness RECORD; now_ms BIGINT; gate_row public.org_native_bank_gate%ROWTYPE;
BEGIN
    c:=org_private.current_enrollment_context();
    IF c.nonce IS DISTINCT FROM p_nonce OR c.purpose<>'PREPARATION_MUTATION' THEN RAISE EXCEPTION 'Native organization preparation graph required' USING ERRCODE='42501'; END IF;
    now_ms:=floor(extract(epoch FROM clock_timestamp())*1000)::bigint;packet:=convert_from(c.full_context,'UTF8')::json;
    PERFORM public.org_native_context_packet(p_nonce,convert_from(c.full_context,'UTF8'),c.purpose,now_ms);
    target_value:=packet->'target';command_value:=(target_value->>'originalCommandJson')::json;desired:=(command_value->>'originalDesiredJson')::json;revision_value:=(command_value->>'originalRevisionJson')::json;enrollment_target:=(revision_value->>'originalEnrollmentTargetJson')::json;actor_value:=target_value->'actor';
    IF (SELECT count(*) FROM org_private.enrollment_row_witness w WHERE w.context_nonce=c.nonce)<>8
            OR EXISTS(SELECT 1 FROM org_private.enrollment_row_witness w WHERE w.context_nonce=c.nonce AND (w.relation_kind NOT IN ('org_native_proposal','org_native_proposal_revision','org_native_proposal_control','org_native_action','org_native_receipt','org_native_audit','org_native_outbox','org_native_outbox_delivery') OR w.operation<>'INSERT' OR w.old_row IS NOT NULL))
            OR EXISTS(SELECT 1 FROM org_private.enrollment_row_witness w WHERE w.context_nonce=c.nonce GROUP BY w.relation_kind HAVING count(*)<>1) THEN RAISE EXCEPTION 'Native organization preparation graph witness mismatch' USING ERRCODE='P0001'; END IF;
    SELECT * INTO gate_row FROM public.org_native_bank_gate g WHERE g.bank_id=c.bank_id;
    IF NOT FOUND OR gate_row.bank_generation IS DISTINCT FROM (revision_value->>'expectedBankGeneration')::bigint OR gate_row.ownership_generation IS DISTINCT FROM (enrollment_target->>'expectedOwnershipGeneration')::bigint OR gate_row.source_generation IS DISTINCT FROM (enrollment_target->>'expectedSourceGeneration')::bigint
            OR NOT EXISTS(SELECT 1 FROM public.bank b WHERE b.id=c.bank_id AND b.is_active IS TRUE) THEN RAISE EXCEPTION 'Native organization final preparation source changed' USING ERRCODE='P0001'; END IF;
    SELECT * INTO header_row FROM public.org_native_proposal h WHERE h.tenant_id=c.tenant_id AND h.proposal_id=c.proposal_id;
    IF NOT FOUND THEN RAISE EXCEPTION 'Native organization prepared header missing' USING ERRCODE='P0001'; END IF;
    expected:=jsonb_build_object('tenant_id',c.tenant_id,'proposal_id',c.proposal_id,'bank_id',c.bank_id,'institution_id',(desired->>'institutionId')::uuid,'issuer_id',(desired->>'issuerAuthorityId')::uuid,'target_kind',desired->>'mode','allocated_enrollment_id',(enrollment_target->>'enrollmentId')::uuid,'maker_kind',actor_value->>'kind','maker_issuer',actor_value->>'issuer','maker_subject',actor_value->>'subject','maker_tenant_id',(actor_value->>'tenantId')::uuid,'maker_staff_realm',actor_value->>'staffRealm','maker_staff_id',(actor_value->>'staffActorId')::uuid,'maker_client_id',actor_value->>'clientId','prepare_action_id',c.action_id,'recorded_at_ms',c.recorded_at_ms);
    IF to_jsonb(header_row) IS DISTINCT FROM expected THEN RAISE EXCEPTION 'Native organization prepared header mismatch' USING ERRCODE='P0001'; END IF;
    SELECT * INTO revision_row FROM public.org_native_proposal_revision r WHERE r.tenant_id=c.tenant_id AND r.proposal_id=c.proposal_id AND r.revision=1;
    expected:=jsonb_build_object('tenant_id',c.tenant_id,'proposal_id',c.proposal_id,'revision',1,'target_kind',desired->>'mode','reserved_execution_action_id',(enrollment_target->>'actionId')::uuid,'expected_bank_generation',(revision_value->>'expectedBankGeneration')::bigint,'expected_ownership_generation',(enrollment_target->>'expectedOwnershipGeneration')::bigint,'expected_source_generation',(enrollment_target->>'expectedSourceGeneration')::bigint,'expected_control_version',(enrollment_target->>'expectedEnrollmentControlVersion')::bigint,'not_after_ms',(desired->>'notAfterEpochMillis')::bigint,'allowed_uses',(revision_value->'allowedUses')::jsonb,'revision_original',convert_to(command_value->>'originalRevisionJson','UTF8'),'revision_encoding','FF_ORGANIZATION_BANK_ENROLLMENT_REVISION_V2','revision_canonical',command_value->>'canonicalRevision','revision_digest',command_value->>'revisionDigest','recorded_at_ms',c.recorded_at_ms);
    IF NOT FOUND OR to_jsonb(revision_row) IS DISTINCT FROM expected THEN RAISE EXCEPTION 'Native organization prepared revision mismatch' USING ERRCODE='P0001'; END IF;
    SELECT * INTO control_row FROM public.org_native_proposal_control p WHERE p.tenant_id=c.tenant_id AND p.proposal_id=c.proposal_id;
    expected:=jsonb_build_object('tenant_id',c.tenant_id,'proposal_id',c.proposal_id,'state','DRAFT','current_revision',1,'control_version',0,'approval_action_id',NULL,'approval_until_ms',NULL,'last_action_id',c.action_id);
    IF NOT FOUND OR to_jsonb(control_row) IS DISTINCT FROM expected THEN RAISE EXCEPTION 'Native organization prepared control mismatch' USING ERRCODE='P0001'; END IF;
    SELECT * INTO action_row FROM public.org_native_action a WHERE a.tenant_id=c.tenant_id AND a.action_id=c.action_id;
    expected:=jsonb_build_object('tenant_id',c.tenant_id,'action_id',c.action_id,'bank_id',c.bank_id,'intended_operation',c.operation,'intended_route',target_value->>'intendedRoute','request_original',convert_to(target_value->>'originalCommandJson','UTF8'),'request_encoding','FF_ORGANIZATION_BANK_PREPARE_COMMAND_V2','request_canonical',target_value->>'canonicalCommand','request_digest',target_value->>'commandDigest','resolution_kind','COMMAND','resolution_original',convert_to(target_value->>'originalCommandJson','UTF8'),'resolution_encoding','FF_ORGANIZATION_BANK_PREPARE_COMMAND_V2','resolution_canonical',target_value->>'canonicalCommand','resolution_digest',target_value->>'commandDigest','resolver_kind',actor_value->>'kind','resolver_issuer',actor_value->>'issuer','resolver_subject',actor_value->>'subject','resolver_tenant_id',(actor_value->>'tenantId')::uuid,'resolver_staff_realm',actor_value->>'staffRealm','resolver_staff_id',(actor_value->>'staffActorId')::uuid,'resolver_client_id',actor_value->>'clientId','proposal_id',c.proposal_id,'proposal_revision',1,'target_digest',command_value->>'revisionDigest','outcome','APPLIED','recorded_at_ms',c.recorded_at_ms);
    IF NOT FOUND OR to_jsonb(action_row) IS DISTINCT FROM expected THEN RAISE EXCEPTION 'Native organization prepared action mismatch' USING ERRCODE='P0001'; END IF;
    SELECT * INTO receipt FROM public.org_native_receipt r WHERE r.tenant_id=c.tenant_id AND r.action_id=c.action_id;
    IF NOT FOUND OR receipt.result_encoding IS DISTINCT FROM 'FF_ORGANIZATION_BANK_PROPOSAL_RESULT_V2' OR receipt.recorded_at_ms IS DISTINCT FROM c.recorded_at_ms THEN RAISE EXCEPTION 'Native organization prepared receipt missing' USING ERRCODE='P0001'; END IF;
    SELECT * INTO audit_row FROM public.org_native_audit a WHERE a.tenant_id=c.tenant_id AND a.action_id=c.action_id;
    IF NOT FOUND OR audit_row.bank_id IS DISTINCT FROM c.bank_id OR audit_row.recorded_at_ms IS DISTINCT FROM c.recorded_at_ms THEN RAISE EXCEPTION 'Native organization prepared audit missing' USING ERRCODE='P0001'; END IF;
    SELECT * INTO event_row FROM public.org_native_outbox e WHERE e.tenant_id=c.tenant_id AND e.action_id=c.action_id;
    IF NOT FOUND OR event_row.bank_id IS DISTINCT FROM c.bank_id OR event_row.recorded_at_ms IS DISTINCT FROM c.recorded_at_ms THEN RAISE EXCEPTION 'Native organization prepared event missing' USING ERRCODE='P0001'; END IF;
    expected_result:=jsonb_build_object('schemaVersion',2,'actionId',c.action_id,'tenantId',c.tenant_id,'bankId',c.bank_id,'proposalId',c.proposal_id,'revision',1,'operation',c.operation,'state','DRAFT','controlVersion',0,'targetDigest',command_value->>'revisionDigest','receiptId',receipt.receipt_id,'auditId',audit_row.audit_id,'outboxEventId',event_row.event_id,'recordedAtEpochMillis',c.recorded_at_ms);
    result_value:=convert_from(receipt.result_original,'UTF8')::json;result_token:=public.org_native_preparation_result_token(result_value);result_canonical:=public.org_native_s('FF_ORGANIZATION_BANK_PROPOSAL_RESULT_V2')||result_token;
    IF result_value::jsonb IS DISTINCT FROM expected_result OR receipt.result_canonical IS DISTINCT FROM result_canonical OR receipt.result_digest IS DISTINCT FROM encode(sha256(convert_to(result_canonical,'UTF8')),'hex') THEN RAISE EXCEPTION 'Native organization prepared result binding mismatch' USING ERRCODE='P0001'; END IF;
    event_wire:=public.org_native_preparation_event_wire(packet,convert_from(audit_row.event_original,'UTF8')::json,result_value,now_ms);
    IF audit_row.event_encoding IS DISTINCT FROM event_wire->>'encoding' OR audit_row.event_canonical IS DISTINCT FROM event_wire->>'canonical' OR audit_row.event_digest IS DISTINCT FROM event_wire->>'digest'
            OR ROW(event_row.event_original,event_row.event_encoding,event_row.event_canonical,event_row.event_digest) IS DISTINCT FROM ROW(audit_row.event_original,audit_row.event_encoding,audit_row.event_canonical,audit_row.event_digest) THEN RAISE EXCEPTION 'Native organization prepared audit outbox binding mismatch' USING ERRCODE='P0001'; END IF;
    SELECT * INTO delivery FROM public.org_native_outbox_delivery d WHERE d.tenant_id=c.tenant_id AND d.event_id=event_row.event_id;
    IF NOT FOUND OR to_jsonb(delivery) IS DISTINCT FROM jsonb_build_object('tenant_id',c.tenant_id,'event_id',event_row.event_id,'control_version',0,'state','PENDING','attempt_count',0,'lease_owner',NULL,'lease_until_ms',NULL,'next_attempt_ms',c.recorded_at_ms,'last_error_code',NULL,'last_ack_at_ms',NULL) THEN RAISE EXCEPTION 'Native organization prepared delivery mismatch' USING ERRCODE='P0001'; END IF;
    IF EXISTS(SELECT 1 FROM public.org_native_decision d WHERE d.tenant_id=c.tenant_id AND d.action_id=c.action_id) OR EXISTS(SELECT 1 FROM public.org_native_bank_enrollment_version v WHERE v.tenant_id=c.tenant_id AND v.proposal_id=c.proposal_id) THEN RAISE EXCEPTION 'Native organization preparation cannot approve or enroll an owner' USING ERRCODE='P0001'; END IF;
    actual_rows:=jsonb_build_object('org_native_proposal',to_jsonb(header_row),'org_native_proposal_revision',to_jsonb(revision_row),'org_native_proposal_control',to_jsonb(control_row),'org_native_action',to_jsonb(action_row),'org_native_receipt',to_jsonb(receipt),'org_native_audit',to_jsonb(audit_row),'org_native_outbox',to_jsonb(event_row),'org_native_outbox_delivery',to_jsonb(delivery));
    FOR witness IN SELECT w.* FROM org_private.enrollment_row_witness w WHERE w.context_nonce=c.nonce LOOP
        IF witness.new_row IS DISTINCT FROM actual_rows->witness.relation_kind THEN RAISE EXCEPTION 'Native organization prepared final-row witness mismatch' USING ERRCODE='P0001'; END IF;
    END LOOP;
    IF NOT public.org_native_enrollment_check_context(c.nonce) THEN RAISE EXCEPTION 'Native organization prepared graph expired' USING ERRCODE='42501'; END IF;
END $$;
REVOKE ALL ON FUNCTION public.org_native_preparation_result_token(JSON) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.org_native_preparation_event_wire(JSON,JSON,JSON,BIGINT) FROM PUBLIC;
REVOKE ALL ON FUNCTION org_private.validate_preparation_graph(UUID) FROM PUBLIC;
-- END SOURCE SECTION 12-preparation-integrity.sql

-- BEGIN SOURCE SECTION 10-legacy-source.sql
-- Legacy invalidation SOURCE candidate; not installed or executed. No issuer authority derives from legacy actors/names.
CREATE FUNCTION org_private.legacy_relevant_facts(p_kind TEXT,p_row JSONB) RETURNS JSONB
LANGUAGE plpgsql IMMUTABLE SET search_path=pg_catalog,pg_temp AS $$
DECLARE fields TEXT[]; result JSONB;
BEGIN
    IF p_row IS NULL THEN RETURN NULL; END IF;
    fields:=CASE p_kind
        WHEN 'bank' THEN ARRAY['id','code','country_id','time_zone_id','is_active']
        WHEN 'bank_division' THEN ARRAY['id','bank_id','is_active']
        WHEN 'bank_region' THEN ARRAY['id','division_id','is_active']
        WHEN 'branch' THEN ARRAY['id','bank_id','region_id','country_id','time_zone_id','is_active']
        WHEN 'working_calendar' THEN ARRAY['id','bank_id','time_zone_id']
        WHEN 'branch_department' THEN ARRAY['id','branch_id','is_active']
        WHEN 'branch_position' THEN ARRAY['id','department_id','is_active']
        WHEN 'calendar_assignment' THEN ARRAY['id','calendar_id','branch_id','department_id','position_id','effective_from','effective_to','is_active']
        WHEN 'bank_holiday' THEN ARRAY['id','bank_id','branch_id','country_id','name','date','is_recurring']
        WHEN 'branch_hours' THEN ARRAY['id','branch_id','day_of_week','open_time','close_time','is_closed'] ELSE NULL END;
    IF fields IS NULL THEN RAISE EXCEPTION 'Unknown legacy organization source relation' USING ERRCODE='22023'; END IF;
    SELECT jsonb_object_agg(e.key,e.value) INTO result FROM jsonb_each(p_row) e WHERE e.key=ANY(fields);
    RETURN result;
END $$;

CREATE FUNCTION org_private.legacy_parent_rows(p_kind TEXT,p_id UUID) RETURNS SETOF JSONB
LANGUAGE plpgsql VOLATILE SECURITY DEFINER SET search_path=pg_catalog,pg_temp AS $$
DECLARE current_row JSONB;
BEGIN
    IF p_id IS NULL THEN RETURN; END IF;
    CASE p_kind
        WHEN 'bank_division' THEN SELECT jsonb_build_object('id',t.id,'bank_id',t.bank_id,'is_active',t.is_active) INTO current_row FROM public.bank_division t WHERE t.id=p_id;
        WHEN 'bank_region' THEN SELECT jsonb_build_object('id',t.id,'division_id',t.division_id,'is_active',t.is_active) INTO current_row FROM public.bank_region t WHERE t.id=p_id;
        WHEN 'branch' THEN SELECT jsonb_build_object('id',t.id,'bank_id',t.bank_id,'region_id',t.region_id,'country_id',t.country_id,'time_zone_id',t.time_zone_id,'is_active',t.is_active) INTO current_row FROM public.branch t WHERE t.id=p_id;
        WHEN 'working_calendar' THEN SELECT jsonb_build_object('id',t.id,'bank_id',t.bank_id,'time_zone_id',t.time_zone_id) INTO current_row FROM public.working_calendar t WHERE t.id=p_id;
        WHEN 'branch_department' THEN SELECT jsonb_build_object('id',t.id,'branch_id',t.branch_id,'is_active',t.is_active) INTO current_row FROM public.branch_department t WHERE t.id=p_id;
        WHEN 'branch_position' THEN SELECT jsonb_build_object('id',t.id,'department_id',t.department_id,'is_active',t.is_active) INTO current_row FROM public.branch_position t WHERE t.id=p_id;
        ELSE RAISE EXCEPTION 'Unknown legacy organization parent relation' USING ERRCODE='22023';
    END CASE;
    IF current_row IS NOT NULL THEN RETURN NEXT org_private.legacy_relevant_facts(p_kind,current_row); END IF;
    -- BEFORE witnesses survive parent deletion/cascade and retain every old/new membership in this transaction.
    RETURN QUERY SELECT DISTINCT facts FROM (
        SELECT w.old_facts AS facts FROM org_private.legacy_source_witness w WHERE w.xid=pg_current_xact_id() AND w.backend_pid=pg_backend_pid() AND w.db_session_user=session_user AND w.relation_kind=p_kind
        UNION ALL SELECT w.new_facts AS facts FROM org_private.legacy_source_witness w WHERE w.xid=pg_current_xact_id() AND w.backend_pid=pg_backend_pid() AND w.db_session_user=session_user AND w.relation_kind=p_kind
    ) history WHERE facts IS NOT NULL AND facts->>'id'=p_id::text;
END $$;

CREATE FUNCTION org_private.legacy_ancestry_banks(p_kind TEXT,p_facts JSONB,p_depth INTEGER DEFAULT 0) RETURNS UUID[]
LANGUAGE plpgsql VOLATILE SECURITY DEFINER SET search_path=pg_catalog,pg_temp AS $$
DECLARE result UUID[]:=ARRAY[]::UUID[]; parent JSONB; parent_kind TEXT; parent_id UUID; key_name TEXT;
BEGIN
    IF p_facts IS NULL THEN RETURN result; END IF;
    IF p_depth>8 THEN RAISE EXCEPTION 'Legacy organization ancestry exceeded' USING ERRCODE='22023'; END IF;
    IF p_kind='bank' THEN result:=array_append(result,(p_facts->>'id')::uuid);
    ELSIF p_kind IN ('bank_division','branch','working_calendar','bank_holiday') THEN result:=array_append(result,(p_facts->>'bank_id')::uuid); END IF;
    IF p_kind='bank_region' THEN parent_kind:='bank_division';parent_id:=(p_facts->>'division_id')::uuid;
    ELSIF p_kind='branch' THEN parent_kind:='bank_region';parent_id:=(p_facts->>'region_id')::uuid;
    ELSIF p_kind='branch_department' THEN parent_kind:='branch';parent_id:=(p_facts->>'branch_id')::uuid;
    ELSIF p_kind='branch_position' THEN parent_kind:='branch_department';parent_id:=(p_facts->>'department_id')::uuid;
    ELSIF p_kind IN ('branch_hours','bank_holiday') THEN parent_kind:='branch';parent_id:=(p_facts->>'branch_id')::uuid;
    END IF;
    IF parent_kind IS NOT NULL THEN
        FOR parent IN SELECT v FROM org_private.legacy_parent_rows(parent_kind,parent_id) v LOOP result:=result||org_private.legacy_ancestry_banks(parent_kind,parent,p_depth+1); END LOOP;
    END IF;
    IF p_kind='calendar_assignment' THEN
        FOR key_name,parent_kind IN SELECT * FROM (VALUES('calendar_id','working_calendar'),('branch_id','branch'),('department_id','branch_department'),('position_id','branch_position')) AS refs(key_name,parent_kind) LOOP
            parent_id:=(p_facts->>key_name)::uuid;
            FOR parent IN SELECT v FROM org_private.legacy_parent_rows(parent_kind,parent_id) v LOOP result:=result||org_private.legacy_ancestry_banks(parent_kind,parent,p_depth+1); END LOOP;
        END LOOP;
    END IF;
    RETURN ARRAY(SELECT DISTINCT id FROM unnest(result) id WHERE id IS NOT NULL AND id<>'00000000-0000-0000-0000-000000000000'::uuid ORDER BY id);
END $$;

CREATE FUNCTION org_private.legacy_affected_banks(p_kind TEXT,p_facts JSONB) RETURNS UUID[]
LANGUAGE plpgsql VOLATILE SECURITY DEFINER SET search_path=pg_catalog,pg_temp AS $$
DECLARE result UUID[]; id_value UUID; child JSONB;
BEGIN
    result:=org_private.legacy_ancestry_banks(p_kind,p_facts,0);IF p_facts IS NULL THEN RETURN result; END IF;id_value:=(p_facts->>'id')::uuid;
    IF p_kind IN ('bank','bank_division','bank_region') THEN
        FOR child IN SELECT jsonb_build_object('id',b.id,'bank_id',b.bank_id,'region_id',b.region_id,'country_id',b.country_id,'time_zone_id',b.time_zone_id,'is_active',b.is_active) FROM public.branch b JOIN public.bank_region r ON r.id=b.region_id JOIN public.bank_division d ON d.id=r.division_id
                WHERE (p_kind='bank' AND (b.bank_id=id_value OR d.bank_id=id_value)) OR (p_kind='bank_division' AND d.id=id_value) OR (p_kind='bank_region' AND r.id=id_value) LOOP
            result:=result||org_private.legacy_ancestry_banks('branch',org_private.legacy_relevant_facts('branch',child),0);
        END LOOP;
    END IF;
    IF p_kind IN ('bank','bank_division','bank_region','branch','branch_department','branch_position','working_calendar') THEN
        FOR child IN SELECT jsonb_build_object('id',a.id,'calendar_id',a.calendar_id,'branch_id',a.branch_id,'department_id',a.department_id,'position_id',a.position_id,'effective_from',a.effective_from,'effective_to',a.effective_to,'is_active',a.is_active) FROM public.calendar_assignment a
                LEFT JOIN public.branch_department department ON department.id=a.department_id
                LEFT JOIN public.branch_position position_row ON position_row.id=a.position_id
                LEFT JOIN public.branch_department position_department ON position_department.id=position_row.department_id
                LEFT JOIN public.branch b ON b.id=COALESCE(a.branch_id,department.branch_id,position_department.branch_id)
                LEFT JOIN public.bank_region r ON r.id=b.region_id LEFT JOIN public.bank_division d ON d.id=r.division_id
                JOIN public.working_calendar calendar_row ON calendar_row.id=a.calendar_id
                WHERE (p_kind='bank' AND (b.bank_id=id_value OR d.bank_id=id_value OR calendar_row.bank_id=id_value)) OR (p_kind='bank_division' AND d.id=id_value)
                    OR (p_kind='bank_region' AND r.id=id_value) OR (p_kind='branch' AND b.id=id_value) OR (p_kind='branch_department' AND (department.id=id_value OR position_department.id=id_value))
                    OR (p_kind='branch_position' AND position_row.id=id_value) OR (p_kind='working_calendar' AND calendar_row.id=id_value) LOOP
            result:=result||org_private.legacy_ancestry_banks('calendar_assignment',org_private.legacy_relevant_facts('calendar_assignment',child),0);
        END LOOP;
    END IF;
    RETURN ARRAY(SELECT DISTINCT id FROM unnest(result) id WHERE id IS NOT NULL AND id<>'00000000-0000-0000-0000-000000000000'::uuid ORDER BY id);
END $$;

CREATE FUNCTION public.org_native_legacy_statement_gate() RETURNS TRIGGER
LANGUAGE plpgsql VOLATILE SECURITY DEFINER SET search_path=pg_catalog,pg_temp AS $$
BEGIN
    IF TG_OP='TRUNCATE' THEN RAISE EXCEPTION 'Legacy source truncation requires an offline migration' USING ERRCODE='42501'; END IF;
    IF EXISTS(SELECT 1 FROM org_private.enrollment_context c WHERE c.xid=pg_current_xact_id() AND c.backend_pid=pg_backend_pid() AND c.db_session_user=session_user)
            OR EXISTS(SELECT 1 FROM org_private.legacy_source_witness w WHERE w.xid=pg_current_xact_id() AND w.backend_pid=pg_backend_pid() AND w.db_session_user=session_user AND w.flushed) THEN
        RAISE EXCEPTION 'Mixed or late legacy organization source mutation denied' USING ERRCODE='42501';
    END IF;
    PERFORM 1 FROM public.org_native_source_catalogue_gate g WHERE g.singleton FOR SHARE;
    IF NOT FOUND THEN RAISE EXCEPTION 'Legacy organization catalogue unavailable' USING ERRCODE='P0001'; END IF;
    RETURN NULL;
END $$;

CREATE FUNCTION public.org_native_legacy_witness_row() RETURNS TRIGGER
LANGUAGE plpgsql VOLATILE SECURITY DEFINER SET search_path=pg_catalog,pg_temp AS $$
DECLARE before_facts JSONB; after_facts JSONB; old_ids UUID[]; new_ids UUID[]; row_id_value UUID;
BEGIN
    IF TG_TABLE_SCHEMA<>'public' THEN RAISE EXCEPTION 'Legacy organization source schema denied' USING ERRCODE='42501'; END IF;
    IF EXISTS(SELECT 1 FROM org_private.legacy_source_witness w WHERE w.xid=pg_current_xact_id() AND w.backend_pid=pg_backend_pid() AND w.db_session_user=session_user AND w.flushed) THEN
        RAISE EXCEPTION 'Late legacy organization row mutation denied' USING ERRCODE='42501';
    END IF;
    IF TG_OP<>'INSERT' THEN before_facts:=org_private.legacy_relevant_facts(TG_TABLE_NAME,to_jsonb(OLD)); END IF;
    IF TG_OP<>'DELETE' THEN after_facts:=org_private.legacy_relevant_facts(TG_TABLE_NAME,to_jsonb(NEW)); END IF;
    IF before_facts IS NOT DISTINCT FROM after_facts THEN IF TG_OP='DELETE' THEN RETURN OLD; ELSE RETURN NEW; END IF; END IF;
    old_ids:=org_private.legacy_affected_banks(TG_TABLE_NAME,before_facts);new_ids:=org_private.legacy_affected_banks(TG_TABLE_NAME,after_facts);
    row_id_value:=COALESCE((after_facts->>'id')::uuid,(before_facts->>'id')::uuid);
    INSERT INTO org_private.legacy_source_witness(xid,backend_pid,db_session_user,relation_kind,row_id,operation,old_bank_ids,new_bank_ids,old_facts,new_facts,bank_identity_changed,flushed)
    VALUES(pg_current_xact_id(),pg_backend_pid(),session_user,TG_TABLE_NAME,row_id_value,TG_OP,old_ids,new_ids,before_facts,after_facts,TG_TABLE_NAME='bank',false);
    IF TG_OP='DELETE' THEN RETURN OLD; ELSE RETURN NEW; END IF;
END $$;

CREATE FUNCTION public.org_native_legacy_flush() RETURNS TRIGGER
LANGUAGE plpgsql VOLATILE SECURITY DEFINER SET search_path=pg_catalog,pg_temp AS $$
DECLARE ids UUID[]; id_value UUID; witness org_private.legacy_source_witness%ROWTYPE; gate public.org_native_bank_gate%ROWTYPE; stamp BIGINT; delta BIGINT;
BEGIN
    SELECT ARRAY(SELECT DISTINCT id FROM org_private.legacy_source_witness w CROSS JOIN LATERAL unnest(w.old_bank_ids||w.new_bank_ids) id
        WHERE w.xid=pg_current_xact_id() AND w.backend_pid=pg_backend_pid() AND w.db_session_user=session_user AND NOT w.flushed ORDER BY id) INTO ids;
    IF cardinality(ids)=0 THEN RETURN NULL; END IF;
    -- The complete Bank set is collected before the first Bank lock and sorted by PostgreSQL UUID order.
    FOREACH id_value IN ARRAY ids LOOP
        INSERT INTO public.org_native_bank_gate(bank_id,bank_generation,ownership_generation,source_generation) VALUES(id_value,0,0,0) ON CONFLICT DO NOTHING;
        PERFORM 1 FROM public.org_native_bank_gate g WHERE g.bank_id=id_value FOR UPDATE;
    END LOOP;
    FOR witness IN SELECT w.* FROM org_private.legacy_source_witness w WHERE w.xid=pg_current_xact_id() AND w.backend_pid=pg_backend_pid() AND w.db_session_user=session_user AND NOT w.flushed ORDER BY w.ordinal LOOP
        FOR id_value IN SELECT DISTINCT id FROM unnest(witness.old_bank_ids||witness.new_bank_ids) id ORDER BY id LOOP
            SELECT * INTO gate FROM public.org_native_bank_gate g WHERE g.bank_id=id_value;
            delta:=CASE WHEN witness.bank_identity_changed THEN 1 ELSE 0 END;
            IF gate.source_generation>=9223372036854775806 OR gate.bank_generation>=9223372036854775806 AND delta=1 THEN RAISE EXCEPTION 'Native organization source generation exhausted' USING ERRCODE='P0001'; END IF;
            stamp:=floor(extract(epoch FROM clock_timestamp())*1000)::bigint;
            UPDATE public.org_native_bank_gate g SET source_generation=g.source_generation+1,bank_generation=g.bank_generation+delta WHERE g.bank_id=id_value;
            INSERT INTO public.org_native_source_change(bank_id,source_generation,bank_generation_before,bank_generation_after,relation_kind,row_id,row_operation,old_facts,new_facts,db_session_user,db_current_user,recorded_at_ms)
            VALUES(id_value,gate.source_generation+1,gate.bank_generation,gate.bank_generation+delta,witness.relation_kind,witness.row_id,witness.operation,witness.old_facts,witness.new_facts,session_user,current_user,stamp);
        END LOOP;
    END LOOP;
    UPDATE org_private.legacy_source_witness w SET flushed=true WHERE w.xid=pg_current_xact_id() AND w.backend_pid=pg_backend_pid() AND w.db_session_user=session_user AND NOT w.flushed;
    RETURN NULL;
END $$;
REVOKE ALL ON FUNCTION org_private.legacy_relevant_facts(TEXT,JSONB) FROM PUBLIC;
REVOKE ALL ON FUNCTION org_private.legacy_parent_rows(TEXT,UUID) FROM PUBLIC;
REVOKE ALL ON FUNCTION org_private.legacy_ancestry_banks(TEXT,JSONB,INTEGER) FROM PUBLIC;
REVOKE ALL ON FUNCTION org_private.legacy_affected_banks(TEXT,JSONB) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.org_native_legacy_statement_gate() FROM PUBLIC;
REVOKE ALL ON FUNCTION public.org_native_legacy_witness_row() FROM PUBLIC;
REVOKE ALL ON FUNCTION public.org_native_legacy_flush() FROM PUBLIC;
-- END SOURCE SECTION 10-legacy-source.sql

-- BEGIN SOURCE SECTION 09-install-and-grants.sql
-- Complete separate PREPARE candidate installer source; root runtime review remains required.
ALTER FUNCTION public.org_native_preparation_desired(UUID,UUID,TEXT,JSON,BIGINT) OWNER TO org_native_guard;
REVOKE ALL ON FUNCTION public.org_native_preparation_desired(UUID,UUID,TEXT,JSON,BIGINT) FROM PUBLIC,org_native_runtime;
ALTER FUNCTION public.org_native_preparation_command(UUID,UUID,UUID,TEXT,JSON,BIGINT) OWNER TO org_native_guard;
REVOKE ALL ON FUNCTION public.org_native_preparation_command(UUID,UUID,UUID,TEXT,JSON,BIGINT) FROM PUBLIC,org_native_runtime;
ALTER FUNCTION public.org_native_preparation_context_packet(UUID,TEXT,TEXT,BIGINT) OWNER TO org_native_guard;
REVOKE ALL ON FUNCTION public.org_native_preparation_context_packet(UUID,TEXT,TEXT,BIGINT) FROM PUBLIC,org_native_runtime;
ALTER FUNCTION public.org_native_enrollment_begin_preparation_read_context(UUID,TEXT) OWNER TO org_native_guard;
REVOKE ALL ON FUNCTION public.org_native_enrollment_begin_preparation_read_context(UUID,TEXT) FROM PUBLIC,org_native_runtime;
ALTER FUNCTION public.org_native_enrollment_begin_preparation_context(UUID,TEXT) OWNER TO org_native_guard;
REVOKE ALL ON FUNCTION public.org_native_enrollment_begin_preparation_context(UUID,TEXT) FROM PUBLIC,org_native_runtime;
ALTER FUNCTION public.org_native_preparation_parent_read_scope(UUID,UUID,UUID,BIGINT,TEXT) OWNER TO org_native_guard;
REVOKE ALL ON FUNCTION public.org_native_preparation_parent_read_scope(UUID,UUID,UUID,BIGINT,TEXT) FROM PUBLIC,org_native_runtime;
ALTER FUNCTION public.org_native_preparation_result_token(JSON) OWNER TO org_native_guard;
REVOKE ALL ON FUNCTION public.org_native_preparation_result_token(JSON) FROM PUBLIC,org_native_runtime;
ALTER FUNCTION public.org_native_preparation_event_wire(JSON,JSON,JSON,BIGINT) OWNER TO org_native_guard;
REVOKE ALL ON FUNCTION public.org_native_preparation_event_wire(JSON,JSON,JSON,BIGINT) FROM PUBLIC,org_native_runtime;
ALTER FUNCTION org_private.validate_preparation_graph(UUID) OWNER TO org_native_guard;
REVOKE ALL ON FUNCTION org_private.validate_preparation_graph(UUID) FROM PUBLIC,org_native_runtime;
-- Exact first proposal-command installation SOURCE candidate. No execution approval.
-- Ownership/control/execution/reconciliation operations remain outside this first native primitive set.
ALTER FUNCTION public.org_native_invalid() OWNER TO org_native_guard;
REVOKE ALL ON FUNCTION public.org_native_invalid() FROM PUBLIC,org_native_runtime;
ALTER FUNCTION public.org_native_shape(JSON,TEXT[]) OWNER TO org_native_guard;
REVOKE ALL ON FUNCTION public.org_native_shape(JSON,TEXT[]) FROM PUBLIC,org_native_runtime;
ALTER FUNCTION public.org_native_s(TEXT) OWNER TO org_native_guard;
REVOKE ALL ON FUNCTION public.org_native_s(TEXT) FROM PUBLIC,org_native_runtime;
ALTER FUNCTION public.org_native_string(JSON,INTEGER) OWNER TO org_native_guard;
REVOKE ALL ON FUNCTION public.org_native_string(JSON,INTEGER) FROM PUBLIC,org_native_runtime;
ALTER FUNCTION public.org_native_uuid(JSON) OWNER TO org_native_guard;
REVOKE ALL ON FUNCTION public.org_native_uuid(JSON) FROM PUBLIC,org_native_runtime;
ALTER FUNCTION public.org_native_long(JSON,BIGINT,BIGINT) OWNER TO org_native_guard;
REVOKE ALL ON FUNCTION public.org_native_long(JSON,BIGINT,BIGINT) FROM PUBLIC,org_native_runtime;
ALTER FUNCTION public.org_native_bool(JSON) OWNER TO org_native_guard;
REVOKE ALL ON FUNCTION public.org_native_bool(JSON) FROM PUBLIC,org_native_runtime;
ALTER FUNCTION public.org_native_evidence(JSON) OWNER TO org_native_guard;
REVOKE ALL ON FUNCTION public.org_native_evidence(JSON) FROM PUBLIC,org_native_runtime;
ALTER FUNCTION public.org_native_uses(JSON) OWNER TO org_native_guard;
REVOKE ALL ON FUNCTION public.org_native_uses(JSON) FROM PUBLIC,org_native_runtime;
ALTER FUNCTION public.org_native_epoch_intent(JSON) OWNER TO org_native_guard;
REVOKE ALL ON FUNCTION public.org_native_epoch_intent(JSON) FROM PUBLIC,org_native_runtime;
ALTER FUNCTION public.org_native_grant_v2_intent(UUID,JSON) OWNER TO org_native_guard;
REVOKE ALL ON FUNCTION public.org_native_grant_v2_intent(UUID,JSON) FROM PUBLIC,org_native_runtime;
ALTER FUNCTION public.org_native_nullable_uuid(JSON) OWNER TO org_native_guard;
REVOKE ALL ON FUNCTION public.org_native_nullable_uuid(JSON) FROM PUBLIC,org_native_runtime;
ALTER FUNCTION public.org_native_nullable_long(JSON,BIGINT,BIGINT) OWNER TO org_native_guard;
REVOKE ALL ON FUNCTION public.org_native_nullable_long(JSON,BIGINT,BIGINT) FROM PUBLIC,org_native_runtime;
ALTER FUNCTION public.org_native_digest(JSON) OWNER TO org_native_guard;
REVOKE ALL ON FUNCTION public.org_native_digest(JSON) FROM PUBLIC,org_native_runtime;
ALTER FUNCTION public.org_native_texts(JSON,INTEGER,INTEGER,INTEGER) OWNER TO org_native_guard;
REVOKE ALL ON FUNCTION public.org_native_texts(JSON,INTEGER,INTEGER,INTEGER) FROM PUBLIC,org_native_runtime;
ALTER FUNCTION public.org_native_definition_token(UUID,UUID,UUID,JSON) OWNER TO org_native_guard;
REVOKE ALL ON FUNCTION public.org_native_definition_token(UUID,UUID,UUID,JSON) FROM PUBLIC,org_native_runtime;
ALTER FUNCTION public.org_native_bank_target(JSON) OWNER TO org_native_guard;
REVOKE ALL ON FUNCTION public.org_native_bank_target(JSON) FROM PUBLIC,org_native_runtime;
ALTER FUNCTION public.org_native_owner_token(JSON) OWNER TO org_native_guard;
REVOKE ALL ON FUNCTION public.org_native_owner_token(JSON) FROM PUBLIC,org_native_runtime;
ALTER FUNCTION public.org_native_revision(JSON) OWNER TO org_native_guard;
REVOKE ALL ON FUNCTION public.org_native_revision(JSON) FROM PUBLIC,org_native_runtime;
ALTER FUNCTION public.org_native_command(TEXT,BIGINT,JSON,BIGINT) OWNER TO org_native_guard;
REVOKE ALL ON FUNCTION public.org_native_command(TEXT,BIGINT,JSON,BIGINT) FROM PUBLIC,org_native_runtime;
ALTER FUNCTION public.org_native_full_command(UUID,UUID,UUID,JSON,TEXT,JSON,BIGINT) OWNER TO org_native_guard;
REVOKE ALL ON FUNCTION public.org_native_full_command(UUID,UUID,UUID,JSON,TEXT,JSON,BIGINT) FROM PUBLIC,org_native_runtime;
ALTER FUNCTION public.org_native_actor_token(JSON) OWNER TO org_native_guard;
REVOKE ALL ON FUNCTION public.org_native_actor_token(JSON) FROM PUBLIC,org_native_runtime;
ALTER FUNCTION public.org_native_observation_token(UUID,UUID,UUID,JSON) OWNER TO org_native_guard;
REVOKE ALL ON FUNCTION public.org_native_observation_token(UUID,UUID,UUID,JSON) FROM PUBLIC,org_native_runtime;
ALTER FUNCTION public.org_native_observed_packets(JSON,JSON,BIGINT) OWNER TO org_native_guard;
REVOKE ALL ON FUNCTION public.org_native_observed_packets(JSON,JSON,BIGINT) FROM PUBLIC,org_native_runtime;
ALTER FUNCTION public.org_native_permission(TEXT) OWNER TO org_native_guard;
REVOKE ALL ON FUNCTION public.org_native_permission(TEXT) FROM PUBLIC,org_native_runtime;
ALTER FUNCTION public.org_native_command_route(UUID,UUID,UUID,TEXT) OWNER TO org_native_guard;
REVOKE ALL ON FUNCTION public.org_native_command_route(UUID,UUID,UUID,TEXT) FROM PUBLIC,org_native_runtime;
ALTER FUNCTION public.org_native_context_packet(UUID,TEXT,TEXT,BIGINT) OWNER TO org_native_guard;
REVOKE ALL ON FUNCTION public.org_native_context_packet(UUID,TEXT,TEXT,BIGINT) FROM PUBLIC,org_native_runtime;
ALTER FUNCTION org_private.require_runtime() OWNER TO org_native_guard;
REVOKE ALL ON FUNCTION org_private.require_runtime() FROM PUBLIC,org_native_runtime;
ALTER FUNCTION org_private.begin_enrollment_context(UUID,TEXT,TEXT) OWNER TO org_native_guard;
REVOKE ALL ON FUNCTION org_private.begin_enrollment_context(UUID,TEXT,TEXT) FROM PUBLIC,org_native_runtime;
ALTER FUNCTION public.org_native_enrollment_begin_read_context(UUID,TEXT) OWNER TO org_native_guard;
REVOKE ALL ON FUNCTION public.org_native_enrollment_begin_read_context(UUID,TEXT) FROM PUBLIC,org_native_runtime;
ALTER FUNCTION public.org_native_enrollment_begin_proposal_read_context(UUID,TEXT) OWNER TO org_native_guard;
REVOKE ALL ON FUNCTION public.org_native_enrollment_begin_proposal_read_context(UUID,TEXT) FROM PUBLIC,org_native_runtime;
ALTER FUNCTION public.org_native_enrollment_begin_mutation_context(UUID,TEXT) OWNER TO org_native_guard;
REVOKE ALL ON FUNCTION public.org_native_enrollment_begin_mutation_context(UUID,TEXT) FROM PUBLIC,org_native_runtime;
ALTER FUNCTION public.org_native_enrollment_check_context(UUID) OWNER TO org_native_guard;
REVOKE ALL ON FUNCTION public.org_native_enrollment_check_context(UUID) FROM PUBLIC,org_native_runtime;
ALTER FUNCTION public.org_native_enrollment_lock_context(UUID) OWNER TO org_native_guard;
REVOKE ALL ON FUNCTION public.org_native_enrollment_lock_context(UUID) FROM PUBLIC,org_native_runtime;
ALTER FUNCTION org_private.current_enrollment_context() OWNER TO org_native_guard;
REVOKE ALL ON FUNCTION org_private.current_enrollment_context() FROM PUBLIC,org_native_runtime;
ALTER FUNCTION public.org_native_enrollment_read_scope(UUID,UUID,UUID,BIGINT,UUID,TEXT) OWNER TO org_native_guard;
REVOKE ALL ON FUNCTION public.org_native_enrollment_read_scope(UUID,UUID,UUID,BIGINT,UUID,TEXT) FROM PUBLIC,org_native_runtime;
ALTER FUNCTION public.org_native_enrollment_write_scope(UUID,UUID,UUID,TEXT) OWNER TO org_native_guard;
REVOKE ALL ON FUNCTION public.org_native_enrollment_write_scope(UUID,UUID,UUID,TEXT) FROM PUBLIC,org_native_runtime;
ALTER FUNCTION public.org_native_enrollment_guard_row() OWNER TO org_native_guard;
REVOKE ALL ON FUNCTION public.org_native_enrollment_guard_row() FROM PUBLIC,org_native_runtime;
ALTER FUNCTION public.org_native_enrollment_witness_row() OWNER TO org_native_guard;
REVOKE ALL ON FUNCTION public.org_native_enrollment_witness_row() FROM PUBLIC,org_native_runtime;
ALTER FUNCTION public.org_native_enrollment_deferred_check() OWNER TO org_native_guard;
REVOKE ALL ON FUNCTION public.org_native_enrollment_deferred_check() FROM PUBLIC,org_native_runtime;
ALTER FUNCTION public.org_native_enrollment_delivery_scope(UUID,UUID) OWNER TO org_native_guard;
REVOKE ALL ON FUNCTION public.org_native_enrollment_delivery_scope(UUID,UUID) FROM PUBLIC,org_native_runtime;
ALTER FUNCTION public.org_native_proposal_result_token(JSON) OWNER TO org_native_guard;
REVOKE ALL ON FUNCTION public.org_native_proposal_result_token(JSON) FROM PUBLIC,org_native_runtime;
ALTER FUNCTION public.org_native_proposal_event_wire(JSON,JSON,JSON,BIGINT) OWNER TO org_native_guard;
REVOKE ALL ON FUNCTION public.org_native_proposal_event_wire(JSON,JSON,JSON,BIGINT) FROM PUBLIC,org_native_runtime;
ALTER FUNCTION org_private.validate_enrollment_graph(UUID) OWNER TO org_native_guard;
REVOKE ALL ON FUNCTION org_private.validate_enrollment_graph(UUID) FROM PUBLIC,org_native_runtime;
ALTER FUNCTION public.org_native_enrollment_validate_complete(UUID) OWNER TO org_native_guard;
REVOKE ALL ON FUNCTION public.org_native_enrollment_validate_complete(UUID) FROM PUBLIC,org_native_runtime;
ALTER FUNCTION org_private.legacy_relevant_facts(TEXT,JSONB) OWNER TO org_native_guard;
REVOKE ALL ON FUNCTION org_private.legacy_relevant_facts(TEXT,JSONB) FROM PUBLIC,org_native_runtime;
ALTER FUNCTION org_private.legacy_parent_rows(TEXT,UUID) OWNER TO org_native_guard;
REVOKE ALL ON FUNCTION org_private.legacy_parent_rows(TEXT,UUID) FROM PUBLIC,org_native_runtime;
ALTER FUNCTION org_private.legacy_ancestry_banks(TEXT,JSONB,INTEGER) OWNER TO org_native_guard;
REVOKE ALL ON FUNCTION org_private.legacy_ancestry_banks(TEXT,JSONB,INTEGER) FROM PUBLIC,org_native_runtime;
ALTER FUNCTION org_private.legacy_affected_banks(TEXT,JSONB) OWNER TO org_native_guard;
REVOKE ALL ON FUNCTION org_private.legacy_affected_banks(TEXT,JSONB) FROM PUBLIC,org_native_runtime;
ALTER FUNCTION public.org_native_legacy_statement_gate() OWNER TO org_native_guard;
REVOKE ALL ON FUNCTION public.org_native_legacy_statement_gate() FROM PUBLIC,org_native_runtime;
ALTER FUNCTION public.org_native_legacy_witness_row() OWNER TO org_native_guard;
REVOKE ALL ON FUNCTION public.org_native_legacy_witness_row() FROM PUBLIC,org_native_runtime;
ALTER FUNCTION public.org_native_legacy_flush() OWNER TO org_native_guard;
REVOKE ALL ON FUNCTION public.org_native_legacy_flush() FROM PUBLIC,org_native_runtime;
GRANT USAGE ON SCHEMA public TO org_native_guard,org_native_runtime;
GRANT UPDATE(format_version) ON public.org_native_source_catalogue_gate TO org_native_guard;
GRANT INSERT,UPDATE(institution_id) ON public.org_native_scope_gate TO org_native_guard;
GRANT INSERT,UPDATE(bank_generation,source_generation) ON public.org_native_bank_gate TO org_native_guard;
GRANT INSERT ON public.org_native_source_change TO org_native_guard;
GRANT UPDATE(control_version) ON public.org_native_bank_enrollment_control,public.org_native_proposal_control TO org_native_guard;
GRANT SELECT(id,code,country_id,time_zone_id,is_active) ON public.bank TO org_native_guard;
GRANT SELECT(id,bank_id,is_active) ON public.bank_division TO org_native_guard;
GRANT SELECT(id,division_id,is_active) ON public.bank_region TO org_native_guard;
GRANT SELECT(id,bank_id,region_id,country_id,time_zone_id,is_active) ON public.branch TO org_native_guard;
GRANT SELECT(id,bank_id,time_zone_id) ON public.working_calendar TO org_native_guard;
GRANT SELECT(id,branch_id,is_active) ON public.branch_department TO org_native_guard;
GRANT SELECT(id,department_id,is_active) ON public.branch_position TO org_native_guard;
GRANT SELECT(id,calendar_id,branch_id,department_id,position_id,effective_from,effective_to,is_active) ON public.calendar_assignment TO org_native_guard;
GRANT SELECT(id,bank_id,branch_id,country_id,name,date,is_recurring) ON public.bank_holiday TO org_native_guard;
GRANT SELECT(id,branch_id,day_of_week,open_time,close_time,is_closed) ON public.branch_hours TO org_native_guard;
-- Existing ordinary metadata CRUD is separate from native financial history and original-caller HTTP authorization.
GRANT SELECT,INSERT,UPDATE,DELETE ON public.bank TO org_native_runtime;
GRANT SELECT,INSERT,UPDATE,DELETE ON public.bank_division TO org_native_runtime;
GRANT SELECT,INSERT,UPDATE,DELETE ON public.bank_region TO org_native_runtime;
GRANT SELECT,INSERT,UPDATE,DELETE ON public.branch TO org_native_runtime;
GRANT SELECT,INSERT,UPDATE,DELETE ON public.working_calendar TO org_native_runtime;
GRANT SELECT,INSERT,UPDATE,DELETE ON public.branch_department TO org_native_runtime;
GRANT SELECT,INSERT,UPDATE,DELETE ON public.branch_position TO org_native_runtime;
GRANT SELECT,INSERT,UPDATE,DELETE ON public.calendar_assignment TO org_native_runtime;
GRANT SELECT,INSERT,UPDATE,DELETE ON public.bank_holiday TO org_native_runtime;
GRANT SELECT,INSERT,UPDATE,DELETE ON public.branch_hours TO org_native_runtime;
GRANT SELECT,INSERT,UPDATE,DELETE ON public.bank_audit_log TO org_native_runtime;
GRANT SELECT,INSERT,UPDATE,DELETE ON public.branch_audit_log TO org_native_runtime;
GRANT SELECT ON public.org_native_proposal TO org_native_runtime;
GRANT SELECT ON public.org_native_proposal_revision TO org_native_runtime;
GRANT SELECT ON public.org_native_proposal_control TO org_native_runtime;
GRANT SELECT ON public.org_native_action TO org_native_runtime;
GRANT SELECT ON public.org_native_receipt TO org_native_runtime;
GRANT SELECT ON public.org_native_decision TO org_native_runtime;
GRANT SELECT ON public.org_native_audit TO org_native_runtime;
GRANT SELECT ON public.org_native_outbox TO org_native_runtime;
GRANT SELECT ON public.org_native_outbox_delivery TO org_native_runtime;
GRANT INSERT ON public.org_native_decision TO org_native_runtime;
GRANT INSERT ON public.org_native_action TO org_native_runtime;
GRANT INSERT ON public.org_native_receipt TO org_native_runtime;
GRANT INSERT ON public.org_native_audit TO org_native_runtime;
GRANT INSERT ON public.org_native_outbox TO org_native_runtime;
GRANT INSERT ON public.org_native_outbox_delivery TO org_native_runtime;
GRANT UPDATE(state,current_revision,control_version,approval_action_id,approval_until_ms,last_action_id) ON public.org_native_proposal_control TO org_native_runtime;
CREATE POLICY runtime_read ON public.org_native_proposal FOR SELECT TO org_native_runtime USING(public.org_native_enrollment_read_scope(tenant_id,bank_id,proposal_id,NULL,NULL,'org_native_proposal'));
CREATE POLICY runtime_read ON public.org_native_proposal_revision FOR SELECT TO org_native_runtime USING(public.org_native_enrollment_read_scope(tenant_id,NULL,proposal_id,revision,NULL,'org_native_proposal_revision'));
CREATE POLICY runtime_read ON public.org_native_proposal_control FOR SELECT TO org_native_runtime USING(public.org_native_enrollment_read_scope(tenant_id,NULL,proposal_id,NULL,NULL,'org_native_proposal_control'));
CREATE POLICY runtime_read ON public.org_native_action FOR SELECT TO org_native_runtime USING(public.org_native_enrollment_read_scope(tenant_id,bank_id,proposal_id,proposal_revision,action_id,'org_native_action'));
CREATE POLICY runtime_read ON public.org_native_receipt FOR SELECT TO org_native_runtime USING(public.org_native_enrollment_read_scope(tenant_id,NULL,NULL,NULL,action_id,'org_native_receipt'));
CREATE POLICY runtime_read ON public.org_native_decision FOR SELECT TO org_native_runtime USING(public.org_native_enrollment_read_scope(tenant_id,NULL,proposal_id,proposal_revision,action_id,'org_native_decision'));
CREATE POLICY runtime_read ON public.org_native_audit FOR SELECT TO org_native_runtime USING(public.org_native_enrollment_read_scope(tenant_id,bank_id,NULL,NULL,action_id,'org_native_audit'));
CREATE POLICY runtime_read ON public.org_native_outbox FOR SELECT TO org_native_runtime USING(public.org_native_enrollment_read_scope(tenant_id,bank_id,NULL,NULL,action_id,'org_native_outbox'));
CREATE POLICY runtime_read ON public.org_native_outbox_delivery FOR SELECT TO org_native_runtime USING(public.org_native_enrollment_delivery_scope(tenant_id,event_id));
CREATE POLICY runtime_insert ON public.org_native_decision FOR INSERT TO org_native_runtime WITH CHECK(public.org_native_enrollment_write_scope(tenant_id,proposal_id,action_id,'org_native_decision'));
CREATE POLICY runtime_insert ON public.org_native_action FOR INSERT TO org_native_runtime WITH CHECK(public.org_native_enrollment_write_scope(tenant_id,NULL,action_id,'org_native_action'));
CREATE POLICY runtime_insert ON public.org_native_receipt FOR INSERT TO org_native_runtime WITH CHECK(public.org_native_enrollment_write_scope(tenant_id,NULL,action_id,'org_native_receipt'));
CREATE POLICY runtime_insert ON public.org_native_audit FOR INSERT TO org_native_runtime WITH CHECK(public.org_native_enrollment_write_scope(tenant_id,NULL,action_id,'org_native_audit'));
CREATE POLICY runtime_insert ON public.org_native_outbox FOR INSERT TO org_native_runtime WITH CHECK(public.org_native_enrollment_write_scope(tenant_id,NULL,action_id,'org_native_outbox'));
CREATE POLICY runtime_insert ON public.org_native_outbox_delivery FOR INSERT TO org_native_runtime WITH CHECK(public.org_native_enrollment_write_scope(tenant_id,NULL,NULL,'org_native_outbox_delivery') AND public.org_native_enrollment_delivery_scope(tenant_id,event_id));
CREATE POLICY runtime_update ON public.org_native_proposal_control FOR UPDATE TO org_native_runtime USING(public.org_native_enrollment_write_scope(tenant_id,proposal_id,NULL,'org_native_proposal_control')) WITH CHECK(public.org_native_enrollment_write_scope(tenant_id,proposal_id,NULL,'org_native_proposal_control'));
CREATE TRIGGER org_native_guard BEFORE INSERT OR UPDATE OR DELETE ON public.org_native_proposal_control FOR EACH ROW EXECUTE FUNCTION public.org_native_enrollment_guard_row();
CREATE TRIGGER org_native_witness AFTER INSERT OR UPDATE ON public.org_native_proposal_control FOR EACH ROW EXECUTE FUNCTION public.org_native_enrollment_witness_row();
CREATE CONSTRAINT TRIGGER org_native_complete AFTER INSERT OR UPDATE OR DELETE ON public.org_native_proposal_control DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION public.org_native_enrollment_deferred_check();
CREATE TRIGGER org_native_guard BEFORE INSERT OR UPDATE OR DELETE ON public.org_native_decision FOR EACH ROW EXECUTE FUNCTION public.org_native_enrollment_guard_row();
CREATE TRIGGER org_native_witness AFTER INSERT OR UPDATE ON public.org_native_decision FOR EACH ROW EXECUTE FUNCTION public.org_native_enrollment_witness_row();
CREATE CONSTRAINT TRIGGER org_native_complete AFTER INSERT OR UPDATE OR DELETE ON public.org_native_decision DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION public.org_native_enrollment_deferred_check();
CREATE TRIGGER org_native_guard BEFORE INSERT OR UPDATE OR DELETE ON public.org_native_action FOR EACH ROW EXECUTE FUNCTION public.org_native_enrollment_guard_row();
CREATE TRIGGER org_native_witness AFTER INSERT OR UPDATE ON public.org_native_action FOR EACH ROW EXECUTE FUNCTION public.org_native_enrollment_witness_row();
CREATE CONSTRAINT TRIGGER org_native_complete AFTER INSERT OR UPDATE OR DELETE ON public.org_native_action DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION public.org_native_enrollment_deferred_check();
CREATE TRIGGER org_native_guard BEFORE INSERT OR UPDATE OR DELETE ON public.org_native_receipt FOR EACH ROW EXECUTE FUNCTION public.org_native_enrollment_guard_row();
CREATE TRIGGER org_native_witness AFTER INSERT OR UPDATE ON public.org_native_receipt FOR EACH ROW EXECUTE FUNCTION public.org_native_enrollment_witness_row();
CREATE CONSTRAINT TRIGGER org_native_complete AFTER INSERT OR UPDATE OR DELETE ON public.org_native_receipt DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION public.org_native_enrollment_deferred_check();
CREATE TRIGGER org_native_guard BEFORE INSERT OR UPDATE OR DELETE ON public.org_native_audit FOR EACH ROW EXECUTE FUNCTION public.org_native_enrollment_guard_row();
CREATE TRIGGER org_native_witness AFTER INSERT OR UPDATE ON public.org_native_audit FOR EACH ROW EXECUTE FUNCTION public.org_native_enrollment_witness_row();
CREATE CONSTRAINT TRIGGER org_native_complete AFTER INSERT OR UPDATE OR DELETE ON public.org_native_audit DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION public.org_native_enrollment_deferred_check();
CREATE TRIGGER org_native_guard BEFORE INSERT OR UPDATE OR DELETE ON public.org_native_outbox FOR EACH ROW EXECUTE FUNCTION public.org_native_enrollment_guard_row();
CREATE TRIGGER org_native_witness AFTER INSERT OR UPDATE ON public.org_native_outbox FOR EACH ROW EXECUTE FUNCTION public.org_native_enrollment_witness_row();
CREATE CONSTRAINT TRIGGER org_native_complete AFTER INSERT OR UPDATE OR DELETE ON public.org_native_outbox DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION public.org_native_enrollment_deferred_check();
CREATE TRIGGER org_native_guard BEFORE INSERT OR UPDATE OR DELETE ON public.org_native_outbox_delivery FOR EACH ROW EXECUTE FUNCTION public.org_native_enrollment_guard_row();
CREATE TRIGGER org_native_witness AFTER INSERT OR UPDATE ON public.org_native_outbox_delivery FOR EACH ROW EXECUTE FUNCTION public.org_native_enrollment_witness_row();
CREATE CONSTRAINT TRIGGER org_native_complete AFTER INSERT OR UPDATE OR DELETE ON public.org_native_outbox_delivery DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION public.org_native_enrollment_deferred_check();
CREATE TRIGGER org_native_catalogue BEFORE INSERT OR UPDATE OR DELETE OR TRUNCATE ON public.bank FOR EACH STATEMENT EXECUTE FUNCTION public.org_native_legacy_statement_gate();
CREATE TRIGGER org_native_source_capture BEFORE INSERT OR UPDATE OR DELETE ON public.bank FOR EACH ROW EXECUTE FUNCTION public.org_native_legacy_witness_row();
CREATE CONSTRAINT TRIGGER org_native_source_flush AFTER INSERT OR UPDATE OR DELETE ON public.bank DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION public.org_native_legacy_flush();
CREATE TRIGGER org_native_catalogue BEFORE INSERT OR UPDATE OR DELETE OR TRUNCATE ON public.bank_division FOR EACH STATEMENT EXECUTE FUNCTION public.org_native_legacy_statement_gate();
CREATE TRIGGER org_native_source_capture BEFORE INSERT OR UPDATE OR DELETE ON public.bank_division FOR EACH ROW EXECUTE FUNCTION public.org_native_legacy_witness_row();
CREATE CONSTRAINT TRIGGER org_native_source_flush AFTER INSERT OR UPDATE OR DELETE ON public.bank_division DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION public.org_native_legacy_flush();
CREATE TRIGGER org_native_catalogue BEFORE INSERT OR UPDATE OR DELETE OR TRUNCATE ON public.bank_region FOR EACH STATEMENT EXECUTE FUNCTION public.org_native_legacy_statement_gate();
CREATE TRIGGER org_native_source_capture BEFORE INSERT OR UPDATE OR DELETE ON public.bank_region FOR EACH ROW EXECUTE FUNCTION public.org_native_legacy_witness_row();
CREATE CONSTRAINT TRIGGER org_native_source_flush AFTER INSERT OR UPDATE OR DELETE ON public.bank_region DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION public.org_native_legacy_flush();
CREATE TRIGGER org_native_catalogue BEFORE INSERT OR UPDATE OR DELETE OR TRUNCATE ON public.branch FOR EACH STATEMENT EXECUTE FUNCTION public.org_native_legacy_statement_gate();
CREATE TRIGGER org_native_source_capture BEFORE INSERT OR UPDATE OR DELETE ON public.branch FOR EACH ROW EXECUTE FUNCTION public.org_native_legacy_witness_row();
CREATE CONSTRAINT TRIGGER org_native_source_flush AFTER INSERT OR UPDATE OR DELETE ON public.branch DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION public.org_native_legacy_flush();
CREATE TRIGGER org_native_catalogue BEFORE INSERT OR UPDATE OR DELETE OR TRUNCATE ON public.working_calendar FOR EACH STATEMENT EXECUTE FUNCTION public.org_native_legacy_statement_gate();
CREATE TRIGGER org_native_source_capture BEFORE INSERT OR UPDATE OR DELETE ON public.working_calendar FOR EACH ROW EXECUTE FUNCTION public.org_native_legacy_witness_row();
CREATE CONSTRAINT TRIGGER org_native_source_flush AFTER INSERT OR UPDATE OR DELETE ON public.working_calendar DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION public.org_native_legacy_flush();
CREATE TRIGGER org_native_catalogue BEFORE INSERT OR UPDATE OR DELETE OR TRUNCATE ON public.branch_department FOR EACH STATEMENT EXECUTE FUNCTION public.org_native_legacy_statement_gate();
CREATE TRIGGER org_native_source_capture BEFORE INSERT OR UPDATE OR DELETE ON public.branch_department FOR EACH ROW EXECUTE FUNCTION public.org_native_legacy_witness_row();
CREATE CONSTRAINT TRIGGER org_native_source_flush AFTER INSERT OR UPDATE OR DELETE ON public.branch_department DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION public.org_native_legacy_flush();
CREATE TRIGGER org_native_catalogue BEFORE INSERT OR UPDATE OR DELETE OR TRUNCATE ON public.branch_position FOR EACH STATEMENT EXECUTE FUNCTION public.org_native_legacy_statement_gate();
CREATE TRIGGER org_native_source_capture BEFORE INSERT OR UPDATE OR DELETE ON public.branch_position FOR EACH ROW EXECUTE FUNCTION public.org_native_legacy_witness_row();
CREATE CONSTRAINT TRIGGER org_native_source_flush AFTER INSERT OR UPDATE OR DELETE ON public.branch_position DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION public.org_native_legacy_flush();
CREATE TRIGGER org_native_catalogue BEFORE INSERT OR UPDATE OR DELETE OR TRUNCATE ON public.calendar_assignment FOR EACH STATEMENT EXECUTE FUNCTION public.org_native_legacy_statement_gate();
CREATE TRIGGER org_native_source_capture BEFORE INSERT OR UPDATE OR DELETE ON public.calendar_assignment FOR EACH ROW EXECUTE FUNCTION public.org_native_legacy_witness_row();
CREATE CONSTRAINT TRIGGER org_native_source_flush AFTER INSERT OR UPDATE OR DELETE ON public.calendar_assignment DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION public.org_native_legacy_flush();
CREATE TRIGGER org_native_catalogue BEFORE INSERT OR UPDATE OR DELETE OR TRUNCATE ON public.bank_holiday FOR EACH STATEMENT EXECUTE FUNCTION public.org_native_legacy_statement_gate();
CREATE TRIGGER org_native_source_capture BEFORE INSERT OR UPDATE OR DELETE ON public.bank_holiday FOR EACH ROW EXECUTE FUNCTION public.org_native_legacy_witness_row();
CREATE CONSTRAINT TRIGGER org_native_source_flush AFTER INSERT OR UPDATE OR DELETE ON public.bank_holiday DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION public.org_native_legacy_flush();
CREATE TRIGGER org_native_catalogue BEFORE INSERT OR UPDATE OR DELETE OR TRUNCATE ON public.branch_hours FOR EACH STATEMENT EXECUTE FUNCTION public.org_native_legacy_statement_gate();
CREATE TRIGGER org_native_source_capture BEFORE INSERT OR UPDATE OR DELETE ON public.branch_hours FOR EACH ROW EXECUTE FUNCTION public.org_native_legacy_witness_row();
CREATE CONSTRAINT TRIGGER org_native_source_flush AFTER INSERT OR UPDATE OR DELETE ON public.branch_hours DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION public.org_native_legacy_flush();
GRANT EXECUTE ON FUNCTION public.org_native_enrollment_begin_read_context(UUID,TEXT) TO org_native_runtime;
GRANT EXECUTE ON FUNCTION public.org_native_enrollment_begin_proposal_read_context(UUID,TEXT) TO org_native_runtime;
GRANT EXECUTE ON FUNCTION public.org_native_enrollment_begin_mutation_context(UUID,TEXT) TO org_native_runtime;
GRANT EXECUTE ON FUNCTION public.org_native_enrollment_lock_context(UUID) TO org_native_runtime;
GRANT EXECUTE ON FUNCTION public.org_native_enrollment_check_context(UUID) TO org_native_runtime;
GRANT EXECUTE ON FUNCTION public.org_native_enrollment_validate_complete(UUID) TO org_native_runtime;
GRANT EXECUTE ON FUNCTION public.org_native_enrollment_read_scope(UUID,UUID,UUID,BIGINT,UUID,TEXT) TO org_native_runtime;
GRANT EXECUTE ON FUNCTION public.org_native_enrollment_write_scope(UUID,UUID,UUID,TEXT) TO org_native_runtime;
GRANT EXECUTE ON FUNCTION public.org_native_enrollment_delivery_scope(UUID,UUID) TO org_native_runtime;

GRANT SELECT(bank_id,bank_generation,ownership_generation,source_generation,current_tenant_id,current_institution_id,current_issuer_id,current_enrollment_id,current_enrollment_version,last_owner_action_id,last_owner_action_tenant_id) ON public.org_native_bank_gate TO org_native_runtime;
GRANT SELECT(tenant_id,enrollment_id,version,bank_id,institution_id,issuer_id,ownership_generation_at_admission) ON public.org_native_bank_enrollment_version TO org_native_runtime;
GRANT SELECT(tenant_id,enrollment_id,version,state,control_version,admission_action_id,last_action_id,admission_action_tenant_id,last_action_tenant_id) ON public.org_native_bank_enrollment_control TO org_native_runtime;
CREATE POLICY runtime_preparation_parent_read ON public.org_native_bank_gate FOR SELECT TO org_native_runtime USING(public.org_native_preparation_parent_read_scope(bank_id,NULL,NULL,NULL,'BANK_GATE'));
CREATE POLICY runtime_preparation_parent_read ON public.org_native_bank_enrollment_version FOR SELECT TO org_native_runtime USING(public.org_native_preparation_parent_read_scope(bank_id,tenant_id,enrollment_id,version,'OWNER_IDENTITY'));
CREATE POLICY runtime_preparation_parent_read ON public.org_native_bank_enrollment_control FOR SELECT TO org_native_runtime USING(public.org_native_preparation_parent_read_scope(NULL,tenant_id,enrollment_id,version,'OWNER_CONTROL'));
GRANT INSERT ON public.org_native_proposal,public.org_native_proposal_revision,public.org_native_proposal_control TO org_native_runtime;
CREATE POLICY runtime_insert ON public.org_native_proposal FOR INSERT TO org_native_runtime WITH CHECK(public.org_native_enrollment_write_scope(tenant_id,proposal_id,prepare_action_id,'org_native_proposal'));
CREATE POLICY runtime_insert ON public.org_native_proposal_revision FOR INSERT TO org_native_runtime WITH CHECK(public.org_native_enrollment_write_scope(tenant_id,proposal_id,NULL,'org_native_proposal_revision'));
CREATE POLICY runtime_insert ON public.org_native_proposal_control FOR INSERT TO org_native_runtime WITH CHECK(public.org_native_enrollment_write_scope(tenant_id,proposal_id,last_action_id,'org_native_proposal_control'));
CREATE TRIGGER org_native_guard BEFORE INSERT OR UPDATE OR DELETE ON public.org_native_proposal FOR EACH ROW EXECUTE FUNCTION public.org_native_enrollment_guard_row();
CREATE TRIGGER org_native_witness AFTER INSERT OR UPDATE ON public.org_native_proposal FOR EACH ROW EXECUTE FUNCTION public.org_native_enrollment_witness_row();
CREATE CONSTRAINT TRIGGER org_native_complete AFTER INSERT OR UPDATE OR DELETE ON public.org_native_proposal DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION public.org_native_enrollment_deferred_check();
CREATE TRIGGER org_native_guard BEFORE INSERT OR UPDATE OR DELETE ON public.org_native_proposal_revision FOR EACH ROW EXECUTE FUNCTION public.org_native_enrollment_guard_row();
CREATE TRIGGER org_native_witness AFTER INSERT OR UPDATE ON public.org_native_proposal_revision FOR EACH ROW EXECUTE FUNCTION public.org_native_enrollment_witness_row();
CREATE CONSTRAINT TRIGGER org_native_complete AFTER INSERT OR UPDATE OR DELETE ON public.org_native_proposal_revision DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION public.org_native_enrollment_deferred_check();
GRANT EXECUTE ON FUNCTION public.org_native_enrollment_begin_preparation_read_context(UUID,TEXT) TO org_native_runtime;
GRANT EXECUTE ON FUNCTION public.org_native_enrollment_begin_preparation_context(UUID,TEXT) TO org_native_runtime;
GRANT EXECUTE ON FUNCTION public.org_native_preparation_parent_read_scope(UUID,UUID,UUID,BIGINT,TEXT) TO org_native_runtime;
-- END SOURCE SECTION 09-install-and-grants.sql

-- Only the separately authorized migrator needed these transient assignment privileges.
REVOKE CREATE ON SCHEMA public FROM org_native_owner,org_native_guard;
REVOKE CREATE ON SCHEMA org_private FROM org_native_guard;
