package com.example.consumer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Exercises the delegator generated from a bare {@code @Delegator} on the interface itself. */
@DisplayName("Generated ClockDelegator (self delegation)")
class ClockDelegatorTest {

    private record FixedClock(long currentTimeMillis, String zoneId) implements Clock {}

    private record FrozenAt(Clock getClockDelegate, long offset) implements ClockDelegator {
        @Override
        public long currentTimeMillis() {
            return getClockDelegate().currentTimeMillis() + offset();
        }
    }

    @Test
    @DisplayName("forwards to the delegate")
    void forwardsToDelegate() {
        Clock clock = new FrozenAt(new FixedClock(1_000L, "UTC"), 0L);

        assertThat(clock.currentTimeMillis()).isEqualTo(1_000L);
        assertThat(clock.zoneId()).isEqualTo("UTC");
    }

    @Test
    @DisplayName("lets implementors override individual methods")
    void allowsSelectiveOverride() {
        Clock clock = new FrozenAt(new FixedClock(1_000L, "UTC"), 500L);

        assertThat(clock.currentTimeMillis()).isEqualTo(1_500L);
        // Not overridden, so still delegated verbatim.
        assertThat(clock.zoneId()).isEqualTo("UTC");
    }
}
