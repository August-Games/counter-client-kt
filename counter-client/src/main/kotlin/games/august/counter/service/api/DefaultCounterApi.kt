package games.august.counter.service.api

import games.august.counter.service.api.model.BatchUpdateCounterRequest
import games.august.counter.service.api.model.CounterAggregate
import games.august.counter.service.api.model.GetCountAggregateRequest
import games.august.counter.service.api.model.UpdateCounterRequest

internal class DefaultCounterApi : CounterApi {
    private val baseUrl = "https://"

    override suspend fun updateCounter(updateCounterRequest: UpdateCounterRequest): Result<Unit> {
        TODO("Not yet implemented")
    }

    override suspend fun batchUpdateCounters(batchUpdateCounterRequest: BatchUpdateCounterRequest): Result<Unit> {
        TODO("Not yet implemented")
    }

    override suspend fun getCountAggregate(
        tag: String,
        updateCounterRequest: GetCountAggregateRequest,
    ): Result<CounterAggregate> {
        TODO("Not yet implemented")
    }
}
