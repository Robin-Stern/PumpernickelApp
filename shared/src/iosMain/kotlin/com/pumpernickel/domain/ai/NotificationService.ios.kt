package com.pumpernickel.domain.ai

import platform.UserNotifications.UNUserNotificationCenter
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNTimeIntervalNotificationTrigger
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UIKit.UIApplication
import platform.UIKit.UIBackgroundTaskIdentifierInvalid
import platform.Foundation.NSUUID

actual class NotificationService {
    actual suspend fun showNotification(title: String, message: String) {
        val center = UNUserNotificationCenter.currentNotificationCenter()
        
        center.requestAuthorizationWithOptions(UNAuthorizationOptionAlert or UNAuthorizationOptionSound or UNAuthorizationOptionBadge) { granted, error ->
            if (granted) {
                val content = UNMutableNotificationContent().apply {
                    setTitle(title)
                    setBody(message)
                }

                // Show immediately (after 1s delay to ensure it triggers even if app is just backgrounded)
                val trigger = UNTimeIntervalNotificationTrigger.triggerWithTimeInterval(1.0, false)
                val request = UNNotificationRequest.requestWithIdentifier(
                    identifier = NSUUID().UUIDString,
                    content = content,
                    trigger = trigger
                )

                center.addNotificationRequest(request) { _ -> }
            }
        }
    }
}

actual class BackgroundTaskManager {
    actual fun beginTask(): Long {
        return UIApplication.sharedApplication.beginBackgroundTaskWithExpirationHandler {
            // Task expired
        }
    }

    actual fun endTask(id: Long) {
        if (id != UIBackgroundTaskIdentifierInvalid) {
            UIApplication.sharedApplication.endBackgroundTask(id)
        }
    }
}
