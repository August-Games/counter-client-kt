package games.august.counter.service.model

import games.august.counter.common.model.BigNumber
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import java.nio.ByteBuffer
import java.util.UUID

/**
 * @param idempotencyKey Generated at construction-time of this [games.august.counter.service.model.CounterUpdate] to
 * prevent duplicate requests from adding/removing more for the given [tag].
 * [idempotencyKey]s are kept server-side for a fixed period of time to avoid issues with e.g. client-side having issues
 * and retrying requests even when they've already been processed. In that case, the updates are de-duped server-side
 * when the [idempotencyKey] matches one that's held in the remote database.
 */
data class CounterUpdate(
    val id: String = generateUUIDv7().toString(),
    val tag: String,
    val added: BigNumber = BigNumber.create(0),
    val removed: BigNumber = BigNumber.create(0),
    val timestamp: Instant = Clock.System.now(),
    val idempotencyKey: String = UUID.randomUUID().toString(),
)

/**
 * https://uuid7.com/
 */
private fun generateUUIDv7(): UUID {
    val now = Clock.System.now()

    // Get timestamp in milliseconds and convert to hex, fitting within 48 bits.
    val timestamp = now.toEpochMilliseconds() and 0xFFFFFFFFFFFFL // 48 bits
    val randomBits = ByteArray(10) // 80 bits for randomness
    java.security.SecureRandom().nextBytes(randomBits)

    // Build the UUID
    val buffer = ByteBuffer.allocate(16)

    // Time-based part (48 bits)
    buffer.putShort((timestamp shr 32).toShort())
    buffer.putInt(timestamp.toInt())

    // Add the version (UUIDv7 uses version 7)
    buffer.put((randomBits[0].toInt() and 0x0F or 0x70).toByte()) // Version 7

    // Remaining random bits (80 bits)
    buffer.put(randomBits.copyOfRange(1, 10))

    buffer.flip()
    return UUID(buffer.long, buffer.long)
}
