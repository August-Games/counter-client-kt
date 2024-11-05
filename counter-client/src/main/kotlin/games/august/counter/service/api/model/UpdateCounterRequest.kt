package games.august.counter.service.api.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class UpdateCounterRequest(
    @SerialName("id")
    val id: String,
    @SerialName("tag")
    val tag: String,
    @SerialName("timestamp")
    val timestamp: String,
    @SerialName("added_count")
    val addedCount: BigNumber,
    @SerialName("removed_count")
    val removedCount: BigNumber,
    @SerialName("idempotency_key")
    val idempotencyKey: String,
)
