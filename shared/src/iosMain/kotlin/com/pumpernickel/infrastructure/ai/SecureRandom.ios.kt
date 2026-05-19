@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.pumpernickel.infrastructure.ai

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import platform.Security.SecRandomCopyBytes
import platform.Security.kSecRandomDefault

internal actual fun secureRandomBytes(length: Int): ByteArray {
    if (length <= 0) return ByteArray(0)
    val out = ByteArray(length)
    out.usePinned { pinned ->
        SecRandomCopyBytes(kSecRandomDefault, length.convert(), pinned.addressOf(0))
    }
    return out
}
