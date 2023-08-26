package com.github.grishberg.profiler.ui

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project

class NotificationHelperImpl(private val project: Project) {

    fun supportInfo(title: String, message: String) {
        NotificationGroupManager.getInstance().getNotificationGroup("Support YAMP Notification")
            .createNotification(title, escapeString(message), NotificationType.INFORMATION).notify(project)
    }

    private fun escapeString(string: String) = string.replace("\n".toRegex(), "\n<br />")
}
