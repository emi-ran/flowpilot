package com.flowpilot.app.engine

import java.util.regex.Pattern

/**
 * Bounded evaluation of user-supplied regex patterns (SMS REGEX match mode).
 *
 * Java regex backtracking can take exponential time on crafted patterns (ReDoS).
 * Matching runs against a [CharSequence] wrapper that checks a wall-clock budget
 * every [CHECK_INTERVAL] reads. When the budget is spent the match is aborted and
 * treated as no-match. Input is also capped to [MAX_INPUT_CHARS] before matching.
 */
internal object SafeRegex {
    const val MAX_INPUT_CHARS = 4_096
    const val MATCH_BUDGET_NANOS = 100_000_000L
    private const val CHECK_INTERVAL = 256

    /**
     * Returns true when [pattern] (case-insensitive, same flags as `RegexOption.IGNORE_CASE`)
     * finds a match in [input]. Invalid patterns, budget overruns and unexpected failures
     * all return false.
     */
    fun containsMatch(
        pattern: String,
        input: CharSequence,
        budgetNanos: Long = MATCH_BUDGET_NANOS,
        nanoTime: () -> Long = System::nanoTime,
    ): Boolean {
        val compiled = try {
            Pattern.compile(pattern, Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE)
        } catch (_: Exception) {
            return false
        }
        val capped = if (input.length > MAX_INPUT_CHARS) input.subSequence(0, MAX_INPUT_CHARS) else input
        val budget = MatchBudget(nanoTime, nanoTime(), budgetNanos)
        return try {
            compiled.matcher(BudgetedCharSequence(capped, budget)).find()
        } catch (_: MatchBudgetExceededException) {
            false
        } catch (_: StackOverflowError) {
            false
        } catch (_: Exception) {
            false
        }
    }

    private class MatchBudget(
        val nanoTime: () -> Long,
        val startNanos: Long,
        val budgetNanos: Long,
    ) {
        private var reads = 0

        fun onRead() {
            if (++reads % CHECK_INTERVAL == 0 && nanoTime() - startNanos > budgetNanos) {
                throw MatchBudgetExceededException()
            }
        }
    }

    private class BudgetedCharSequence(
        private val delegate: CharSequence,
        private val budget: MatchBudget,
    ) : CharSequence {
        override val length: Int get() = delegate.length

        override fun get(index: Int): Char {
            budget.onRead()
            return delegate[index]
        }

        override fun subSequence(startIndex: Int, endIndex: Int): CharSequence =
            BudgetedCharSequence(delegate.subSequence(startIndex, endIndex), budget)

        override fun toString(): String = delegate.toString()
    }

    private class MatchBudgetExceededException : RuntimeException("regex match budget exceeded")
}
