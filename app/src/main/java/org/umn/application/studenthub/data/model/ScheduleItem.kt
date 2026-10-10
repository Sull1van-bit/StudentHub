package org.umn.application.studenthub.data.model

data class ScheduleItem(
    val title: String,
    val timeInfo: String,
    val room: String,
    val remainingTasks: Int,
)
