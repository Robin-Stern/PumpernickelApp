@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package com.pumpernickel

import platform.Foundation.NSBundle
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.stringWithContentsOfFile

actual fun readResourceFile(fileName: String): String {
    val parts = fileName.split(".")
    val name = parts.dropLast(1).joinToString(".")
    val ext = parts.lastOrNull() ?: ""
    val path = NSBundle.mainBundle.pathForResource(name, ext)
        ?: error("Resource file not found: $fileName")
    return NSString.stringWithContentsOfFile(path, NSUTF8StringEncoding, null)
        ?: error("Could not read resource file: $fileName")
}
