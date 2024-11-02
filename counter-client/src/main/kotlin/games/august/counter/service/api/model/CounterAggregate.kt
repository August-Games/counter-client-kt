package games.august.counter.service.api.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class CounterAggregate(
    @SerialName("tag")
    val tag: String,
    @SerialName("span")
    val span: TimeSpan,
    @SerialName("total")
    val total: BigNumber,
    @SerialName("added")
    val added: BigNumber,
    @SerialName("removed")
    val removed: BigNumber,
    @SerialName("snapshots")
    val snapshots: List<CounterSnapshot>? = null,
)
