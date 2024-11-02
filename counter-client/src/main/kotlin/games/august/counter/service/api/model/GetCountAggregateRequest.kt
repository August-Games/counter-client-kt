package games.august.counter.service.api.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class GetCountAggregateRequest(
    @SerialName("span")
    val span: TimeSpan,
    @SerialName("include_snapshots")
    val includeSnapshots: Boolean,
)
