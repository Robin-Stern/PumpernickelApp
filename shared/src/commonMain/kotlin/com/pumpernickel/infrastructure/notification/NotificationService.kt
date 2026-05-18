package com.pumpernickel.infrastructure.notification

/**
 * Interface for showing local notifications.
 */
expect class NotificationService {
    suspend fun showNotification(title: String, message: String)
}

/**
 * Manager for requesting extended execution time from the OS.
 */
expect class BackgroundTaskManager {
    fun beginTask(onExpired: () -> Unit): Long
    fun endTask(id: Long)
}
