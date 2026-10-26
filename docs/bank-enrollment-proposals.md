# Bank enrollment proposals

The organization owner can prepare a retained Bank enrollment proposal, apply governed proposal decisions, and read the action history. A prepared proposal is DRAFT. These routes do not execute Bank ownership admission, publish an immutable calendar, or authorize a financial transaction. Bank metadata and customer LegalEntity records do not confer institution authority or a regulatory license.

## Requests and recovery

Use the [producer-generated specification](../core-common-org-mgmt-sdk/src/main/resources/openapi.yml) for exact closed request fields and response envelopes. The platform routes are scoped by tenant and Bank:

- `POST /api/v1/platform/tenants/{tenantId}/banks/{bankId}/enrollment-proposals` prepares a complete target.
- Proposal routes ending in `/{proposalId}/submit`, `/approve`, `/reject`, `/withdraw`, `/cancel`, or `/emergency-cancel` apply the corresponding governed command.
- `GET /api/v1/platform/tenants/{tenantId}/banks/{bankId}/enrollment-actions/{actionId}` reads retained history with independent current READ permission.
- `POST` to that action route ending in `/recover-preparation` recovers the retained result for the exact original request without reallocating identities or extending the original horizon.

Retain the original action ID and full request for recovery. A timeout or missing action is not proof of no effect. Reads and recovery require fresh authorization, including after the original target expires. Native POST handlers use durable owner receipts rather than the general HTTP response cache.

The original verified caller must have a nonnull tenant and current permission for the exact operation, target tenant, Bank, and intent. The platform actor tenant remains distinct from the resource tenant. A checker is a distinct verified staff identity. Historical approval and digests do not replace current authority.

## Configuration observation

Preparation observes the current configuration owner's institution grant through its genuine generated SDK. Configure a tenant-bound OAuth2 client-credentials registration and the explicit issuer, audience, client, subject, and signed target-tenant profile. The observer acts as its actual MACHINE identity; it does not impersonate the caller. No incoming token is relayed to configuration.

Observation is finite evidence, bounded to 30 seconds. It is not a cross-database transaction or an instantaneous revocation guarantee. Native source generations and final local checks remain separate. Retained history and exact preparation recovery can proceed with current local authorization after the original configuration service becomes unavailable.

## Deployment prerequisites

Provision the exact cluster-global roles required by V5 before migration: `org_native_owner`, `org_native_guard`, `org_native_runtime`, and the restricted `org_flyway_migrator`. Use separately authorized administration for bootstrap, separate migration and runtime credentials, and the migration's exact role attributes and memberships. V5 attests existing roles; it does not silently repair arbitrary preexisting roles. Database-scoped Flyway locking does not provision cluster-global roles.

Apply original V1–V4 and V5 under the restricted migrator before serving these routes. The optional `native-enrollment` and `native-enrollment-commands` profiles disable automatic Flyway by default; enable migration explicitly in an authorized deployment phase. Set the issuer/JWKS, audience, caller profiles, platform tenant, current policy provider, and observer settings declared in those profile files. Enabled features fail closed when required security configuration is absent.

This source uses shared JWT tenant projection, UTC auditing, strict OPA decisions, and Boot 4 property-based OAuth2 client registration. Qualification used genuine parent and framework source modules with explicit dependency selectors. Coordinated released dependencies containing those capabilities remain an adoption prerequisite; historical binaries with the same version label are not substitutes. SDK generation stays in the original producer workflow, and the packaged `api-spec/openapi.yml` path is a build-derived alias of canonical `openapi.yml`.
