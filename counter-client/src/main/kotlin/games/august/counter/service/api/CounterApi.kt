package games.august.counter.service.api

import games.august.counter.service.api.model.BatchUpdateCounterRequest
import games.august.counter.service.api.model.CounterAggregate
import games.august.counter.service.api.model.GetCountAggregateRequest
import games.august.counter.service.api.model.UpdateCounterRequest

internal interface CounterApi {
    suspend fun updateCounter(
        apiToken: String,
        updateCounterRequest: UpdateCounterRequest,
    ): Result<Unit>

    suspend fun batchUpdateCounters(
        apiToken: String,
        batchUpdateCounterRequest: BatchUpdateCounterRequest,
    ): Result<Unit>

    suspend fun getCountAggregate(
        apiToken: String,
        tag: String,
        updateCounterRequest: GetCountAggregateRequest,
    ): Result<CounterAggregate>
}
