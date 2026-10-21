package com.incognito.vault.ui.navigation

sealed class Screen(val route: String) {
    object Lock : Screen("lock")
    object Camera : Screen("camera")
    object Gallery : Screen("gallery")
    object Viewer : Screen("viewer/{itemId}") {
        fun createRoute(itemId: String): String = "viewer/$itemId"
    }
}
