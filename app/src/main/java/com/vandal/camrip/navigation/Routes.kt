package com.vandal.camrip.navigation

object Routes {
    const val HOME = "home"
    const val SETTINGS = "settings"
    const val EDITOR = "editor/{projectId}"
    const val EFFECTS = "effects/{projectId}/{clipId}"

    fun editor(projectId: Long) = "editor/$projectId"
    fun effects(projectId: Long, clipId: Long) = "effects/$projectId/$clipId"
}
