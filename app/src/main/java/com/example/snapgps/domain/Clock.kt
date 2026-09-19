package com.example.snapgps.domain

/** Wall-clock source, injectable so time-dependent logic (staleness, throttling) is testable. */
fun interface Clock {
    fun nowMs(): Long

    companion object {
        val System = Clock { java.lang.System.currentTimeMillis() }
    }
}
