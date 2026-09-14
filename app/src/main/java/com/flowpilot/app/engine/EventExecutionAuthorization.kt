package com.flowpilot.app.engine

/** Process-local generation gate for transient events accepted while engine is enabled. */
internal class EventExecutionAuthorization {
    private var generation = 0L

    @Synchronized
    fun authorize(engineEnabled: Boolean): Token? = if (engineEnabled) Token(generation) else null

    @Synchronized
    fun invalidate() {
        generation += 1
    }

    @Synchronized
    fun isAuthorized(token: Token): Boolean = token.generation == generation

    @Synchronized
    fun <T> executeIfAuthorized(token: Token, execute: () -> T): T? =
        if (token.generation == generation) execute() else null

    internal data class Token internal constructor(val generation: Long)
}
