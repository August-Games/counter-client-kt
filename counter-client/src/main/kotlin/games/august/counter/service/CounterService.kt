package games.august.counter.service

import games.august.counter.common.model.CounterConfig
import games.august.counter.service.api.DefaultCounterApi
import games.august.counter.service.model.CounterUpdate

interface CounterService {
    /**
     * Set up regular flushing of counters according to provided [CounterConfig].
     * Without calling this, [resume], [pause], [shutdown], and [updateCounter] will no-op.
     */
    fun start()

    /**
     * If [pause] has been called, this will start sending updates to the server again.
     */
    fun resume()

    /**
     * Pause sending updates to the server until [resume] is called.
     */
    fun pause()

    /**
     * Clean up resources, cancel coroutine scope, flush final updates if desired.
     */
    suspend fun shutdown(flushPendingUpdates: Boolean)

    /**
     * @return false if the [update] was not queued to be sent. Ensure this is monitored at the call-site. This is
     *  effectively a silent error where your counter updates are not being sent. This solely depends on the combination
     *  of values you set in [CounterConfig].
     */
    fun updateCounter(update: CounterUpdate): Boolean

    fun batchUpdateCounter(updates: List<CounterUpdate>): Boolean

    fun setListener(listener: CounterServiceListener?)

    companion object {
        fun createDefault(config: CounterConfig): CounterService =
            DefaultCounterService(
                config = config,
                counterApi = DefaultCounterApi(),
            )
    }
}
