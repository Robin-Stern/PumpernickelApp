package com.example.pumpernickelapp

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform