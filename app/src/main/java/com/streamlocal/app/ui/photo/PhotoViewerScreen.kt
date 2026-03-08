package com.streamlocal.app.ui.photo

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.streamlocal.app.ui.theme.Error
import com.streamlocal.app.ui.theme.Primary
import com.streamlocal.app.ui.theme.TextSecondary

@Composable
fun PhotoViewerScreen(
    initialIndex: Int,
    onBack:       () -> Unit,
    viewModel:    PhotoViewerViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) { viewModel.initialize(initialIndex) }

    // Direction for slide transitions: 1 = forward, -1 = backward
    var navDirection by remember { mutableIntStateOf(1) }

    // Progress animatable: 0f → 1f over slideshowInterval seconds, resets on each tick
    val progress = remember { Animatable(0f) }
    LaunchedEffect(uiState.slideshowTick, uiState.isSlideshowPlaying, uiState.slideshowInterval) {
        if (uiState.isSlideshowPlaying) {
            progress.snapTo(0f)
            progress.animateTo(
                targetValue    = 1f,
                animationSpec  = tween(uiState.slideshowInterval * 1000, easing = LinearEasing)
            )
        } else {
            progress.snapTo(0f)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {

        // ── Loading ───────────────────────────────────────────────────────────
        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Primary)
            }
        }

        // ── Error ─────────────────────────────────────────────────────────────
        uiState.errorMessage?.let {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(it, color = Error, style = MaterialTheme.typography.bodyMedium)
            }
        }

        // ── Photo ─────────────────────────────────────────────────────────────
        uiState.currentPhoto?.let { photo ->
            val photoUrl = viewModel.getPhotoUrl(photo)

            var scale   by remember(uiState.currentIndex) { mutableFloatStateOf(1f) }
            var offsetX by remember(uiState.currentIndex) { mutableFloatStateOf(0f) }
            var offsetY by remember(uiState.currentIndex) { mutableFloatStateOf(0f) }

            AnimatedContent(
                targetState    = uiState.currentIndex,
                transitionSpec = {
                    if (navDirection >= 0) {
                        (slideInHorizontally { it } + fadeIn(tween(200))) togetherWith
                                (slideOutHorizontally { -it } + fadeOut(tween(200)))
                    } else {
                        (slideInHorizontally { -it } + fadeIn(tween(200))) togetherWith
                                (slideOutHorizontally { it } + fadeOut(tween(200)))
                    }
                },
                modifier = Modifier.fillMaxSize(),
                label    = "photo_slide"
            ) { _ ->
                var dragStartX by remember { mutableFloatStateOf(0f) }
                var swipeFired by remember { mutableStateOf(false) }

                SubcomposeAsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(photoUrl)
                        .crossfade(300)
                        .build(),
                    contentDescription = photo.name,
                    contentScale       = ContentScale.Fit,
                    loading = {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = Primary, modifier = Modifier.size(32.dp))
                        }
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offsetX, translationY = offsetY)
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(1f, 5f)
                                if (scale > 1f) {
                                    offsetX += pan.x * scale
                                    offsetY += pan.y * scale
                                } else {
                                    offsetX = 0f; offsetY = 0f
                                }
                            }
                        }
                        .pointerInput(uiState.currentIndex) {
                            detectHorizontalDragGestures(
                                onDragStart = { start -> dragStartX = start.x; swipeFired = false },
                                onDragEnd   = { swipeFired = false },
                                onHorizontalDrag = { change, _ ->
                                    if (scale <= 1f && !swipeFired) {
                                        val delta = change.position.x - dragStartX
                                        if (delta < -80.dp.toPx()) {
                                            swipeFired = true; navDirection = 1
                                            viewModel.goToNext()
                                        } else if (delta > 80.dp.toPx()) {
                                            swipeFired = true; navDirection = -1
                                            viewModel.goToPrevious()
                                        }
                                    }
                                }
                            )
                        }
                )
            }
        }

        // ── Top progress bar (slideshow) ──────────────────────────────────────
        AnimatedVisibility(
            visible  = uiState.isSlideshowPlaying,
            enter    = fadeIn(tween(300)),
            exit     = fadeOut(tween(300)),
            modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth()
        ) {
            LinearProgressIndicator(
                progress      = { progress.value },
                modifier      = Modifier.fillMaxWidth().height(2.dp),
                color         = Primary,
                trackColor    = Color.White.copy(alpha = 0.12f),
                strokeCap     = StrokeCap.Square,
            )
        }

        // ── Top bar ───────────────────────────────────────────────────────────
        if (!uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.verticalGradient(listOf(Color.Black.copy(0.78f), Color.Transparent))
                    )
                    .statusBarsPadding()
                    .padding(start = 4.dp, end = 16.dp, top = 6.dp, bottom = 20.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector        = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Retour",
                            tint               = Color.White
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text     = uiState.currentPhoto?.name ?: "",
                            style    = MaterialTheme.typography.titleMedium,
                            color    = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        uiState.currentPhoto?.let { photo ->
                            Text(
                                text  = "${photo.formattedSize()} · ${uiState.currentIndex + 1} / ${uiState.photos.size}",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }
        }

        // ── Bottom controls ───────────────────────────────────────────────────
        if (!uiState.isLoading && uiState.photos.isNotEmpty()) {
            Column(
                modifier            = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.90f)))
                    )
                    .navigationBarsPadding()
                    .padding(start = 20.dp, end = 20.dp, top = 32.dp, bottom = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {

                // Dot indicators
                if (uiState.photos.size <= 9) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        uiState.photos.indices.forEach { i ->
                            val active = i == uiState.currentIndex
                            Box(
                                modifier = Modifier
                                    .size(if (active) 8.dp else 5.dp)
                                    .background(
                                        color = if (active) Primary else Color.White.copy(0.35f),
                                        shape = CircleShape
                                    )
                                    .clickable {
                                        navDirection = if (i > uiState.currentIndex) 1 else -1
                                        viewModel.goTo(i)
                                    }
                            )
                        }
                    }
                } else {
                    Text(
                        text  = "${uiState.currentIndex + 1} / ${uiState.photos.size}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(0.65f)
                    )
                }

                // Control card
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color.White.copy(0.07f))
                        .border(1.dp, Color.White.copy(0.10f), RoundedCornerShape(24.dp))
                ) {
                    // Progress bar at top of card
                    LinearProgressIndicator(
                        progress      = { progress.value },
                        modifier      = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)),
                        color         = Primary,
                        trackColor    = Color.Transparent,
                        strokeCap     = StrokeCap.Square,
                    )

                    Row(
                        modifier              = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment     = Alignment.CenterVertically
                    ) {

                        // Loop toggle
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(
                                    if (uiState.isLooping) Primary.copy(0.15f) else Color.Transparent
                                )
                                .clickable { viewModel.toggleLoop() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector        = Icons.Default.Repeat,
                                contentDescription = "Boucle",
                                tint               = if (uiState.isLooping) Primary else Color.White.copy(0.4f),
                                modifier           = Modifier.size(22.dp)
                            )
                        }

                        // Previous
                        IconButton(
                            onClick  = { navDirection = -1; viewModel.goToPrevious() },
                            enabled  = uiState.hasPrevious
                        ) {
                            Icon(
                                imageVector        = Icons.Default.SkipPrevious,
                                contentDescription = "Précédente",
                                tint               = if (uiState.hasPrevious) Color.White else Color.White.copy(0.2f),
                                modifier           = Modifier.size(30.dp)
                            )
                        }

                        // Play / Pause — big amber button
                        Box(
                            modifier         = Modifier
                                .size(64.dp)
                                .background(Primary, CircleShape)
                                .clickable { viewModel.toggleSlideshow() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector        = if (uiState.isSlideshowPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (uiState.isSlideshowPlaying) "Pause" else "Démarrer le diaporama",
                                tint               = Color.Black,
                                modifier           = Modifier.size(34.dp)
                            )
                        }

                        // Next
                        IconButton(
                            onClick  = { navDirection = 1; viewModel.goToNext() },
                            enabled  = uiState.hasNext
                        ) {
                            Icon(
                                imageVector        = Icons.Default.SkipNext,
                                contentDescription = "Suivante",
                                tint               = if (uiState.hasNext) Color.White else Color.White.copy(0.2f),
                                modifier           = Modifier.size(30.dp)
                            )
                        }

                        // Interval selector — cycles through 2s / 4s / 8s / 15s
                        Box(
                            modifier         = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White.copy(0.10f))
                                .border(1.dp, Color.White.copy(0.08f), RoundedCornerShape(12.dp))
                                .clickable { viewModel.cycleInterval() }
                                .padding(horizontal = 14.dp, vertical = 9.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text       = "${uiState.slideshowInterval}s",
                                color      = if (uiState.isSlideshowPlaying) Primary else Color.White,
                                style      = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}
