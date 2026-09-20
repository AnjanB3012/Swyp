package com.tapchoice.demo.state

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class TapState { READY, READING, AUTHORIZING, PAID, ERROR }

object PaymentSessionStore {
    private val mutableState = MutableStateFlow(TapState.READY)
    val state = mutableState.asStateFlow()

    private val mutableHceReady = MutableStateFlow(false)
    val hceReady = mutableHceReady.asStateFlow()

    fun update(value: TapState) { mutableState.value = value }
    fun updateHceReady(value: Boolean) { mutableHceReady.value = value }
}
