package games.august.counter.service.model

import games.august.counter.common.model.BigNumber
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

data class CounterUpdate(
    val tag: String,
    val added: BigNumber = BigNumber.create(0),
    val removed: BigNumber = BigNumber.create(0),
    val timestamp: Instant = Clock.System.now(),
)
