package com.pumpernickel.domain.progresspic

sealed class UnlockResult {
    data object Success : UnlockResult()
    data object Cancelled : UnlockResult()
    data object Failed : UnlockResult()
    data class Error(val message: String) : UnlockResult()
}
