package games.august.counter.service.api.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class BatchUpdateCounterRequest(
    @SerialName("updates")
    val updates: List<UpdateCounterRequest>,
)
