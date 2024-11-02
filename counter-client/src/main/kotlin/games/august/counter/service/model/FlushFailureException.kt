package games.august.counter.service.model

class FlushFailureException(
    override val cause: Throwable?,
    message: String,
) : RuntimeException(message)
