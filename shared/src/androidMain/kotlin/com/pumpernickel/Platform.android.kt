package com.pumpernickel

actual fun readResourceFile(fileName: String): String {
    return Thread.currentThread().contextClassLoader!!
        .getResourceAsStream(fileName)!!
        .bufferedReader()
        .readText()
}
