package com.example.fitwell.viewmodel

import android.app.Application
import com.example.fitwell.FitLifeApp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

internal fun Application.fitLife() = this as FitLifeApp

/** Emits the current time every minute so "next dose" / "time remaining" labels stay fresh. */
fun minuteTicker(): Flow<Long> = flow {
    while (true) {
        emit(System.currentTimeMillis())
        delay(60_000)
    }
}
