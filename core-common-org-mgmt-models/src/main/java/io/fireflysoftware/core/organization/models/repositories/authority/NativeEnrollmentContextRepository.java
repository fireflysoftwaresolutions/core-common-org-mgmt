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
package io.fireflysoftware.core.organization.models.repositories.authority;

import io.fireflysoftware.core.organization.models.entities.authority.BankGateRow;
import io.fireflysoftware.core.organization.models.entities.authority.EnrollmentNativeContextStamp;
import org.springframework.data.repository.Repository;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.query.Param;
import reactor.core.publisher.Mono;
import java.util.UUID;

public interface NativeEnrollmentContextRepository extends Repository<BankGateRow,UUID> {
    @Query("SELECT public.org_native_enrollment_begin_proposal_read_context(:nonce,:contextOriginal)")
    Mono<UUID> beginProposalRead(@Param("nonce") UUID nonce,@Param("contextOriginal") String contextOriginal);
    @Query("SELECT public.org_native_enrollment_begin_read_context(:nonce,:contextOriginal)")
    Mono<UUID> beginRead(@Param("nonce") UUID nonce,@Param("contextOriginal") String contextOriginal);
    @Query("SELECT public.org_native_enrollment_begin_mutation_context(:nonce,:contextOriginal)")
    Mono<UUID> beginMutation(@Param("nonce") UUID nonce,@Param("contextOriginal") String contextOriginal);
    @Query("SELECT context_nonce,db_now_ms,authority_until_ms,command_until_ms,context_digest FROM public.org_native_enrollment_lock_context(:nonce)")
    Mono<EnrollmentNativeContextStamp> lockContext(@Param("nonce") UUID nonce);
    @Query("SELECT public.org_native_enrollment_check_context(:nonce)")
    Mono<Boolean> contextCurrent(@Param("nonce") UUID nonce);
    @Query("SELECT public.org_native_enrollment_validate_complete(:nonce)")
    Mono<Boolean> validateComplete(@Param("nonce") UUID nonce);
}
