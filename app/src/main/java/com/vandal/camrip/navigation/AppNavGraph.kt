package com.vandal.camrip.navigation

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.vandal.camrip.data.EffectItem
import com.vandal.camrip.data.ProjectRepository
import com.vandal.camrip.data.SettingsRepository
import com.vandal.camrip.ui.editor.EditorScreen
import com.vandal.camrip.ui.effects.EffectsScreen
import com.vandal.camrip.ui.home.HomeScreen
import com.vandal.camrip.ui.settings.SettingsScreen

@Composable
fun AppNavGraph() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val repo = remember { ProjectRepository(context) }
    val settings = remember { SettingsRepository(context) }

    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
        enterTransition = {
            fadeIn(animationSpec = tween(300)) + scaleIn(initialScale = 0.92f, animationSpec = tween(300, easing = FastOutSlowInEasing))
        },
        exitTransition = {
            fadeOut(animationSpec = tween(300)) + scaleOut(targetScale = 1.05f, animationSpec = tween(300, easing = FastOutSlowInEasing))
        },
        popEnterTransition = {
            fadeIn(animationSpec = tween(300)) + scaleIn(initialScale = 1.05f, animationSpec = tween(300, easing = FastOutSlowInEasing))
        },
        popExitTransition = {
            fadeOut(animationSpec = tween(300)) + scaleOut(targetScale = 0.92f, animationSpec = tween(300, easing = FastOutSlowInEasing))
        }
    ) {
        composable(Routes.HOME) {
            HomeScreen(
                repo = repo,
                onOpenProject = { id -> navController.navigate(Routes.editor(id)) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) }
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(settings = settings, onBack = { navController.popBackStack() })
        }

        composable(
            route = Routes.EDITOR,
            arguments = listOf(navArgument("projectId") { type = NavType.LongType })
        ) { backStackEntry ->
            val projectId = backStackEntry.arguments?.getLong("projectId") ?: 0L
            val applied = backStackEntry.savedStateHandle.get<String>("applied_effect")
            val appliedEffect = remember(applied) { applied?.let { decodeEffect(it) } }
            LaunchedEffect(applied) { if (applied != null) backStackEntry.savedStateHandle.remove<String>("applied_effect") }

            EditorScreen(
                projectId = projectId,
                repo = repo,
                settings = settings,
                appliedEffect = appliedEffect,
                onBack = { navController.popBackStack() },
                onOpenEffects = { clipId -> navController.navigate(Routes.effects(projectId, clipId)) }
            )
        }

        composable(
            route = Routes.EFFECTS,
            arguments = listOf(
                navArgument("projectId") { type = NavType.LongType },
                navArgument("clipId") { type = NavType.LongType }
            )
        ) { backStackEntry ->
            val clipId = backStackEntry.arguments?.getLong("clipId") ?: 0L
            EffectsScreen(
                clipId = clipId,
                onApply = { effect ->
                    navController.previousBackStackEntry?.savedStateHandle?.set("applied_effect", encodeEffect(effect))
                    navController.popBackStack()
                },
                onBack = { navController.popBackStack() }
            )
        }
    }
}

fun encodeEffect(e: EffectItem): String =
    listOf(e.id, e.clipId, e.type, e.intensity, e.angleX, e.angleY, e.angleZ, e.scale, e.speed).joinToString("|")

fun decodeEffect(s: String): EffectItem? = try {
    val p = s.split("|")
    EffectItem(p[0].toLong(), p[1].toLong(), p[2], p[3].toFloat(), p[4].toFloat(), p[5].toFloat(), p[6].toFloat(), p[7].toFloat(), p[8].toFloat())
} catch (_: Exception) { null }
