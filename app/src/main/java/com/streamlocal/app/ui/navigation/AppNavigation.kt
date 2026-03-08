package com.streamlocal.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.streamlocal.app.ui.home.HomeScreen
import com.streamlocal.app.ui.login.LoginScreen
import com.streamlocal.app.ui.login.LoginViewModel
import com.streamlocal.app.ui.photo.PhotoViewerScreen
import com.streamlocal.app.ui.player.VideoPlayerScreen
import com.streamlocal.app.ui.setup.ServerSetupScreen
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Définition centralisée de toutes les routes de navigation.
 *
 * Les routes paramétrées (VIDEO_PLAYER, PHOTO_VIEWER) utilisent un index entier
 * pour identifier le fichier sélectionné dans la liste déjà chargée par le HomeViewModel.
 */
object Routes {
    const val SERVER_SETUP  = "server_setup"
    const val LOGIN         = "login"
    const val HOME          = "home"
    const val VIDEO_PLAYER  = "video_player/{fileIndex}"
    const val PHOTO_VIEWER  = "photo_viewer/{fileIndex}"

    /** Construit la route concrète vers le lecteur vidéo avec l'index donné. */
    fun videoPlayer(index: Int) = "video_player/$index"

    /** Construit la route concrète vers la visionneuse photo avec l'index donné. */
    fun photoViewer(index: Int) = "photo_viewer/$index"
}

/**
 * Graphe de navigation complet de l'application.
 *
 * Flux principal :
 * ```
 * SERVER_SETUP → LOGIN → HOME → VIDEO_PLAYER
 *                             → PHOTO_VIEWER
 * ```
 *
 * Chaque transition utilise `popUpTo { inclusive = true }` pour éviter
 * d'empiler les écrans dans la back-stack (pas de retour possible vers
 * l'écran de login après authentification, par exemple).
 *
 * @param startDestination Destination initiale déterminée par [AppEntryPoint].
 */
@Composable
fun AppNavigation(startDestination: String) {
    val navController = rememberNavController()

    NavHost(
        navController    = navController,
        startDestination = startDestination
    ) {

        // ── Configuration du serveur ──────────────────────────────────────────
        composable(Routes.SERVER_SETUP) {
            ServerSetupScreen(
                onSetupComplete = {
                    // Remplace SERVER_SETUP par LOGIN dans la back-stack
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(Routes.SERVER_SETUP) { inclusive = true }
                    }
                }
            )
        }

        // ── Connexion PIN ─────────────────────────────────────────────────────
        composable(Routes.LOGIN) {
            LoginScreen(
                onLoginSuccess = {
                    // Remplace LOGIN par HOME : impossible de revenir en arrière
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                },
                onChangeServer = {
                    // Retour à la configuration, efface LOGIN de la stack
                    navController.navigate(Routes.SERVER_SETUP) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                }
            )
        }

        // ── Accueil ───────────────────────────────────────────────────────────
        composable(Routes.HOME) {
            HomeScreen(
                onVideoClick = { index ->
                    navController.navigate(Routes.videoPlayer(index))
                },
                onPhotoClick = { index ->
                    navController.navigate(Routes.photoViewer(index))
                },
                onLogout = {
                    // Déconnexion : retour au LOGIN, efface HOME de la stack
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(Routes.HOME) { inclusive = true }
                    }
                }
            )
        }

        // ── Lecteur vidéo ─────────────────────────────────────────────────────
        composable(
            route     = Routes.VIDEO_PLAYER,
            arguments = listOf(navArgument("fileIndex") { type = NavType.IntType })
        ) { backStackEntry ->
            val index = backStackEntry.arguments?.getInt("fileIndex") ?: 0
            VideoPlayerScreen(
                initialIndex = index,
                onBack       = { navController.popBackStack() }
            )
        }

        // ── Visionneuse photo ─────────────────────────────────────────────────
        composable(
            route     = Routes.PHOTO_VIEWER,
            arguments = listOf(navArgument("fileIndex") { type = NavType.IntType })
        ) { backStackEntry ->
            val index = backStackEntry.arguments?.getInt("fileIndex") ?: 0
            PhotoViewerScreen(
                initialIndex = index,
                onBack       = { navController.popBackStack() }
            )
        }
    }
}
