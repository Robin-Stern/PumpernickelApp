package com.pumpernickel.domain.ai

import platform.Foundation.NSUUID
import platform.UIKit.UIApplication
import platform.UIKit.UIBackgroundTaskInvalid
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNTimeIntervalNotificationTrigger
import platform.UserNotifications.UNUserNotificationCenter

actual class NotificationService {

    init {
        // Request permission once at init instead of on every showNotification call.
        UNUserNotificationCenter.currentNotificationCenter()
            .requestAuthorizationWithOptions(
                UNAuthorizationOptionAlert or UNAuthorizationOptionSound or UNAuthorizationOptionBadge
            ) { _, _ -> }
    }

    actual suspend fun showNotification(title: String, message: String) {
        val content = UNMutableNotificationContent().apply {
            setTitle(title)
            setBody(message)
        }
        val trigger = UNTimeIntervalNotificationTrigger.triggerWithTimeInterval(1.0, false)
        val request = UNNotificationRequest.requestWithIdentifier(
            identifier = NSUUID().UUIDString,
            content = content,
            trigger = trigger
        )
        UNUserNotificationCenter.currentNotificationCenter()
            .addNotificationRequest(request) { _ -> }
    }
}

actual class BackgroundTaskManager {

    // UIBackgroundTaskIdentifier is a typealias for ULong (NSUInteger). The expect-class
    // surface uses Long for KMP-friendliness, so we convert at the boundary.
    private var uiTaskId: ULong = UIBackgroundTaskInvalid

    actual fun beginTask(onExpired: () -> Unit): Long {
        uiTaskId = UIApplication.sharedApplication.beginBackgroundTaskWithExpirationHandler {
            // iOS is revoking background time (~30s elapsed).
            // Cancel the generation so the state machine stays consistent.
            onExpired()
            endUiTask()
        }
        // Schedule a BGProcessingTask as a longer-lived fallback.
        // The OS decides when to run it; requires registerAiBackgroundTask() at app launch.
        AiBgTaskHolder.schedule()
        return uiTaskId.toLong()
    }

    actual fun endTask(id: Long) {
        AiBgTaskHolder.complete(success = true)
        endUiTask()
    }

    private fun endUiTask() {
        if (uiTaskId != UIBackgroundTaskInvalid) {
            UIApplication.sharedApplication.endBackgroundTask(uiTaskId)
            uiTaskId = UIBackgroundTaskInvalid
        }
    }
}
