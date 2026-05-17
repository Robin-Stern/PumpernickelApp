package com.pumpernickel.domain.ai

import platform.BackgroundTasks.BGProcessingTask
import platform.BackgroundTasks.BGProcessingTaskRequest
import platform.BackgroundTasks.BGTaskScheduler

internal const val AI_BG_TASK_ID = "com.pumpernickel.ai_generation"

/**
 * Called from Swift in PumpernickelApp.init() — must run before
 * application(_:didFinishLaunchingWithOptions:) returns.
 */
fun registerAiBackgroundTask() {
    BGTaskScheduler.sharedScheduler.registerForTaskWithIdentifier(
        AI_BG_TASK_ID,
        usingQueue = null
    ) { task ->
        val bgTask = task as? BGProcessingTask ?: return@registerForTaskWithIdentifier
        AiBgTaskHolder.set(bgTask)
        bgTask.expirationHandler = {
            // OS is revoking the background slot — mark as failed.
            AiBgTaskHolder.complete(success = false)
        }
    }
}

internal object AiBgTaskHolder {
    private var task: BGProcessingTask? = null

    fun set(task: BGProcessingTask) {
        this.task = task
    }

    fun schedule() {
        val request = BGProcessingTaskRequest(AI_BG_TASK_ID)
        request.requiresNetworkConnectivity = true
        // Ignore scheduling errors (e.g. running in simulator where BGTask is unavailable).
        BGTaskScheduler.sharedScheduler.submitTaskRequest(request, null)
    }

    fun complete(success: Boolean) {
        task?.setTaskCompletedWithSuccess(success)
        task = null
    }
}
