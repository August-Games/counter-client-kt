package games.august.counter.service.model

import games.august.counter.common.model.BigNumber
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import java.util.UUID

/**
 * @param idempotencyKey Generated at construction-time of this [games.august.counter.service.model.CounterUpdate] to
 * prevent duplicate requests from adding/removing more for the given [tag].
 * [idempotencyKey]s are kept server-side for a fixed period of time to avoid issues with e.g. client-side having issues
 * and retrying requests even when they've already been processed. In that case, the updates are de-duped server-side
 * when the [idempotencyKey] matches one that's held in the remote database.
 */
data class CounterUpdate(
    val tag: String,
    val added: BigNumber = BigNumber.create(0),
    val removed: BigNumber = BigNumber.create(0),
    val timestamp: Instant = Clock.System.now(),
    val idempotencyKey: String = UUID.randomUUID().toString(),
)
