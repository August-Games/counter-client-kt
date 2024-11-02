package games.august.counter.service.api.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class BigNumber(
    @SerialName("billions")
    val billions: Long,
    @SerialName("remaining")
    val remaining: Int,
)
