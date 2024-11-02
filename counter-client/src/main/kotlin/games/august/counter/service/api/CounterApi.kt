package games.august.counter.service.api

import games.august.counter.service.api.model.BatchUpdateCounterRequest
import games.august.counter.service.api.model.CounterAggregate
import games.august.counter.service.api.model.GetCountAggregateRequest
import games.august.counter.service.api.model.UpdateCounterRequest

internal interface CounterApi {
    suspend fun updateCounter(updateCounterRequest: UpdateCounterRequest): Result<Unit>

    suspend fun batchUpdateCounters(batchUpdateCounterRequest: BatchUpdateCounterRequest): Result<Unit>

    suspend fun getCountAggregate(
        tag: String,
        updateCounterRequest: GetCountAggregateRequest,
    ): Result<CounterAggregate>
}
