package games.august.counter.service.mapping

import games.august.counter.common.model.BigNumber
import games.august.counter.service.api.model.UpdateCounterRequest
import games.august.counter.service.model.CounterUpdate
import kotlinx.datetime.Instant
import kotlinx.datetime.toJavaInstant
import java.time.format.DateTimeFormatter

internal fun CounterUpdate.toRequest(): UpdateCounterRequest =
    UpdateCounterRequest(
        id = id,
        tag = tag,
        timestamp = formatAsRfc3339(timestamp),
        addedCount = added.toRequest(),
        removedCount = removed.toRequest(),
        idempotencyKey = idempotencyKey,
    )

internal fun BigNumber.toRequest(): games.august.counter.service.api.model.BigNumber =
    games.august.counter.service.api.model.BigNumber(
        billions = billions,
        remaining = remaining,
    )

private fun formatAsRfc3339(instant: Instant): String {
    val formatter = DateTimeFormatter.ISO_INSTANT
    return formatter.format(instant.toJavaInstant())
}
