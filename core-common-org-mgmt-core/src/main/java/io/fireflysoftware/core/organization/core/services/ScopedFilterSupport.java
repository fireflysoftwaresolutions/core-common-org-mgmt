/*
 * Copyright 2025 Firefly Software Foundation
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


package io.fireflysoftware.core.organization.core.services;

import org.fireflyframework.core.filters.FilterRequest;
import org.fireflyframework.core.filters.FilterUtils;
import org.fireflyframework.core.queries.PaginationRequest;
import org.fireflyframework.core.queries.PaginationResponse;
import org.fireflyframework.core.queries.PaginationUtils;
import org.springframework.data.domain.Pageable;
import org.springframework.data.relational.core.query.Criteria;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.util.function.BiFunction;
import java.util.function.Function;

public final class ScopedFilterSupport {
    private ScopedFilterSupport() { }

    public static <E, D> Mono<PaginationResponse<D>> filter(
            Class<E> entityType, Function<E, D> mapper, FilterRequest<D> request,
            BiFunction<Criteria, Pageable, Flux<E>> data, Function<Criteria, Mono<Long>> count) {
        FilterRequest<D> normalized = request == null ? new FilterRequest<>() : request;
        Criteria criteria = normalized.getOptions() == null
                ? FilterUtils.createFilter(entityType, mapper).getCriteria(normalized)
                : FilterUtils.createFilter(entityType, mapper, normalized.getOptions()).getCriteria(normalized);
        PaginationRequest pagination = normalized.getPagination() == null
                ? new PaginationRequest() : normalized.getPagination();
        return PaginationUtils.paginateQuery(pagination, mapper,
                pageable -> data.apply(criteria, pageable), () -> count.apply(criteria));
    }
}
