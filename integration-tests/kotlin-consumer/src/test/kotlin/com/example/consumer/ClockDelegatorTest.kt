package com.example.consumer

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/** Exercises the delegator generated from a bare `@Delegator` on the interface itself. */
@DisplayName("Generated ClockDelegator (self delegation, KSP)")
class ClockDelegatorTest {

    private class FixedClock(override val zoneId: String, private val millis: Long) : Clock {
        override fun currentTimeMillis(): Long = millis
    }

    private class Shifted(
        override val clockDelegate: Clock,
        private val offset: Long,
    ) : ClockDelegator {
        override fun currentTimeMillis(): Long = clockDelegate.currentTimeMillis() + offset
    }

    @Test
    @DisplayName("forwards to the delegate")
    fun forwardsToDelegate() {
        val clock: Clock = Shifted(FixedClock("UTC", 1_000L), 0L)

        assertThat(clock.currentTimeMillis()).isEqualTo(1_000L)
        assertThat(clock.zoneId).isEqualTo("UTC")
    }

    @Test
    @DisplayName("lets implementors override individual members")
    fun allowsSelectiveOverride() {
        val clock: Clock = Shifted(FixedClock("UTC", 1_000L), 500L)

        assertThat(clock.currentTimeMillis()).isEqualTo(1_500L)
        assertThat(clock.zoneId).isEqualTo("UTC")
    }
}
