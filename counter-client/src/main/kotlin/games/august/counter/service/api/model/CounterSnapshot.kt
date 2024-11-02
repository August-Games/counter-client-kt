package games.august.counter.service.api.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class CounterSnapshot(
    @SerialName("timestamp")
    val timestamp: String,
    @SerialName("time_resolution_seconds")
    val timeResolutionSeconds: Int,
    @SerialName("count")
    val total: BigNumber,
)
