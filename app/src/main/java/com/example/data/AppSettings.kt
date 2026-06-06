package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_settings")
data class AppSettings(
    @PrimaryKey val id: Int = 1,
    val selectedTheme: String = "Frost Blue",
    val buttonSize: Int = 60, // size in dp, 40 to 80
    val dockedButtonSize: Int = 20, // size in dp shown when docked, 10 to 45
    val opacityActive: Float = 0.85f, // active transparency level (0.3 to 1.0)
    val opacityIdle: Float = 0.40f, // idle/docked transparency level (0.1 to 0.8)
    val hapticFeedback: Boolean = true,
    val hideToCornerWhenIdle: Boolean = true,
    val idleTimeoutSeconds: Int = 5, // time in seconds before docking (2 to 10)
    // Saved button position (in pixel offset or fraction of screen, -1 means default center-right)
    val lastPositionX: Int = -1,
    val lastPositionY: Int = -1,
    val isServiceRunning: Boolean = false
)
