package com.example.consumer

import eu.nitonfx.processors.delegation.Delegator

/**
 * Self-delegating interface: `@Delegator` without a value generates `ClockDelegator` right next
 * to it.
 */
@Delegator
interface Clock {
    val zoneId: String

    fun currentTimeMillis(): Long
}
