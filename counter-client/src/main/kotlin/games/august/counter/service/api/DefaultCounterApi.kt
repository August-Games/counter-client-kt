package games.august.counter.service.api

import games.august.counter.service.api.model.BatchUpdateCounterRequest
import games.august.counter.service.api.model.CounterAggregate
import games.august.counter.service.api.model.GetCountAggregateRequest
import games.august.counter.service.api.model.UpdateCounterRequest
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.headers
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

internal class DefaultCounterApi(
    private val httpClient: HttpClient =
        HttpClient(CIO) {
            install(ContentNegotiation) {
                json(
                    Json {
                        prettyPrint = true
                        isLenient = true
                    },
                )
            }
        },
) : CounterApi {
    private val baseUrl = "https://counter-np.august.games"

    override suspend fun updateCounter(
        apiToken: String,
        updateCounterRequest: UpdateCounterRequest,
    ): Result<Unit> =
        runCatching {
            val response =
                httpClient.request("$baseUrl/counter/update") {
                    method = HttpMethod.Post
                    headers {
                        append(HttpHeaders.Accept, "application/json")
                        append("X-Api-Key", apiToken)
                        append(HttpHeaders.UserAgent, "ktor client")
                    }
                    setBody(updateCounterRequest)
                }
            when {
                response.status.isSuccess() -> Unit
                else -> error("Failed to update counter for tag \"${updateCounterRequest.tag}\": ${response.bodyAsText()}")
            }
        }

    override suspend fun batchUpdateCounters(
        apiToken: String,
        batchUpdateCounterRequest: BatchUpdateCounterRequest,
    ): Result<Unit> =
        runCatching {
            val response =
                httpClient.request("$baseUrl/counter/batch-update") {
                    method = HttpMethod.Post
                    headers {
                        append(HttpHeaders.Accept, "application/json")
                        append("X-Api-Key", apiToken)
                        append(HttpHeaders.UserAgent, "ktor client")
                    }
                    setBody(batchUpdateCounterRequest)
                }
            when {
                response.status.isSuccess() -> Unit
                else -> error("Failed to batch update counters: ${response.bodyAsText()}")
            }
        }

    override suspend fun getCountAggregate(
        apiToken: String,
        tag: String,
        updateCounterRequest: GetCountAggregateRequest,
    ): Result<CounterAggregate> =
        runCatching {
            val response =
                httpClient.request("$baseUrl/$tag/get") {
                    method = HttpMethod.Post
                    headers {
                        append(HttpHeaders.Accept, "application/json")
                        append("X-Api-Key", apiToken)
                        append(HttpHeaders.UserAgent, "ktor client")
                    }
                    setBody(updateCounterRequest)
                }
            when {
                response.status.isSuccess() -> response.body()
                else -> error("Failed to get count aggregate for tag \"$tag\": ${response.bodyAsText()}")
            }
        }
}
