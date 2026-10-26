# Scoped metadata queries

Working calendars, calendar assignments, branches, bank regions, branch departments, branch positions, and branch hours use typed Spring Data repository fragments. Every scoped data and count query combines its mandatory parent predicate with the complete grouped client filter. Null or empty filters retain the parent predicate. Both queries use the same criteria and pagination options.

Calendar assignment filtering first checks that the calendar belongs to the requested Bank. A derived parent query holds a PostgreSQL SHARE lock across data and count in the reactive service transaction. Region and position parent lookups use the same scoped locking pattern. Missing or mismatched parents produce the existing not-found result before child queries. For these parent locks, the ordinary database role needs SELECT plus UPDATE(updated_at) on exactly working_calendar, bank_division, and branch_department. The locks stabilize parent membership; concurrent child-row changes still follow the configured transaction isolation.

Service interfaces remain in the services package; business implementations live in services.impl. Repository fragments use the configured R2dbcEntityOperations and transaction connection factory. HTTP paths, DTOs, entities, and V1–V4 migrations are preserved.

Run the normal verification and SDK generation from the repository root:

```bash
mvn verify
```

The SDK uses the producer's openapi.yml as its canonical specification. Maven process-resources creates api-spec/openapi.yml as an unfiltered compatibility copy with overwrite enabled. A classpath equality test checks that both resources contain identical bytes; the compatibility path has no separately maintained source file.
