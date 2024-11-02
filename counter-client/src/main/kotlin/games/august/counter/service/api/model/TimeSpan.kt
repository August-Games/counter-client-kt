package games.august.counter.service.api.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class TimeSpan(
    @SerialName("start_time")
    val startTime: Long,
    @SerialName("end_time")
    val endTime: Int,
)
