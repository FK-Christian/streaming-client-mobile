package com.streamlocal.app.ui.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.streamlocal.app.R
import com.streamlocal.app.ui.theme.Background
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * Écran de démarrage affiché pendant le chargement des préférences.
 *
 * Animation : le logo apparaît en scale 0.6 → 1.0 + fade 0 → 1 simultanément.
 * Le fond noir de l'app entoure une carte blanche arrondie contenant le logo,
 * assurant un rendu optimal puisque le logo a un fond blanc.
 */
@Composable
fun SplashScreen() {
    val scale = remember { Animatable(0.55f) }
    val alpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        coroutineScope {
            launch {
                scale.animateTo(
                    targetValue   = 1f,
                    animationSpec = tween(650, easing = FastOutSlowInEasing)
                )
            }
            launch {
                alpha.animateTo(
                    targetValue   = 1f,
                    animationSpec = tween(450)
                )
            }
        }
    }

    Box(
        modifier         = Modifier
            .fillMaxSize()
            .background(Background),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .graphicsLayer(
                    scaleX = scale.value,
                    scaleY = scale.value,
                    alpha  = alpha.value
                )
                .shadow(elevation = 32.dp, shape = RoundedCornerShape(36.dp))
                .clip(RoundedCornerShape(36.dp))
                .background(Color.White)
        ) {
            Image(
                painter            = painterResource(R.drawable.logo_app),
                contentDescription = "LocalStream",
                contentScale       = ContentScale.Fit,
                modifier           = Modifier.size(260.dp)
            )
        }
    }
}
