package com.example.pumpernickelapp.food.data.api

import io.ktor.client.HttpClient

expect fun createHttpClient(): HttpClient
