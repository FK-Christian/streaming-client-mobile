package com.streamlocal.app.ui.login

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.streamlocal.app.ui.theme.Background
import com.streamlocal.app.ui.theme.CardBorder
import com.streamlocal.app.ui.theme.Error
import com.streamlocal.app.ui.theme.OnBackground
import com.streamlocal.app.ui.theme.Primary
import com.streamlocal.app.ui.theme.Surface
import com.streamlocal.app.ui.theme.TextSecondary
import kotlin.math.roundToInt

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    onChangeServer: () -> Unit,
    viewModel: LoginViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val keyboard = LocalSoftwareKeyboardController.current

    // Shake animation
    val shakeOffset = remember { Animatable(0f) }

    LaunchedEffect(uiState.shakeError) {
        if (uiState.shakeError) {
            shakeOffset.animateTo(
                targetValue = 0f,
                animationSpec = keyframes {
                    durationMillis = 600
                    0f   at 0
                    -16f at 80
                    16f  at 160
                    -12f at 240
                    12f  at 320
                    -6f  at 400
                    6f   at 480
                    0f   at 560
                }
            )
        }
    }

    LaunchedEffect(uiState.isAuthenticated) {
        if (uiState.isAuthenticated) {
            viewModel.resetAuthenticated()
            onLoginSuccess()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .imePadding()
    ) {
        // Settings button (change server)
        IconButton(
            onClick  = onChangeServer,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
        ) {
            Icon(
                imageVector        = Icons.Default.Settings,
                contentDescription = "Changer de serveur",
                tint               = TextSecondary
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Lock icon
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(Surface, RoundedCornerShape(20.dp))
                    .border(1.dp, CardBorder, RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector        = Icons.Default.Lock,
                    contentDescription = null,
                    tint               = Primary,
                    modifier           = Modifier.size(40.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text  = "StreamLocal",
                style = MaterialTheme.typography.headlineLarge,
                color = Primary
            )

            if (uiState.serverUrl.isNotBlank()) {
                Text(
                    text  = uiState.serverUrl,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(48.dp))

            // PIN input with shake animation
            Box(
                modifier = Modifier
                    .offset { IntOffset(shakeOffset.value.roundToInt(), 0) }
                    .fillMaxWidth()
            ) {
                OutlinedTextField(
                    value         = uiState.pin,
                    onValueChange = viewModel::onPinChange,
                    label         = { Text("Code d'accès") },
                    placeholder   = { Text("• • • • • •", color = TextSecondary) },
                    leadingIcon   = {
                        Icon(
                            imageVector        = Icons.Default.Lock,
                            contentDescription = null,
                            tint               = if (uiState.errorMessage != null) Error else TextSecondary
                        )
                    },
                    modifier           = Modifier.fillMaxWidth(),
                    singleLine         = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions    = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction    = ImeAction.Done
                    ),
                    keyboardActions    = KeyboardActions(
                        onDone = {
                            keyboard?.hide()
                            viewModel.login()
                        }
                    ),
                    enabled = !uiState.isRateLimited,
                    isError = uiState.errorMessage != null,
                    colors  = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = if (uiState.errorMessage != null) Error else Primary,
                        unfocusedBorderColor = if (uiState.errorMessage != null) Error else CardBorder,
                        focusedLabelColor    = if (uiState.errorMessage != null) Error else Primary,
                        unfocusedLabelColor  = if (uiState.errorMessage != null) Error else TextSecondary,
                        cursorColor          = Primary,
                        focusedTextColor     = OnBackground,
                        unfocusedTextColor   = OnBackground,
                        errorBorderColor     = Error,
                        errorLabelColor      = Error
                    ),
                    shape = RoundedCornerShape(12.dp),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        letterSpacing = 8.sp,
                        fontFamily    = FontFamily.Monospace
                    )
                )
            }

            // Error message
            uiState.errorMessage?.let { msg ->
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset { IntOffset(shakeOffset.value.roundToInt(), 0) },
                    verticalAlignment      = Alignment.CenterVertically,
                    horizontalArrangement  = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector        = Icons.Default.Warning,
                        contentDescription = null,
                        tint               = Error,
                        modifier           = Modifier.size(14.dp)
                    )
                    Text(
                        text  = msg,
                        style = MaterialTheme.typography.bodySmall,
                        color = Error
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Login button
            Button(
                onClick  = {
                    keyboard?.hide()
                    viewModel.login()
                },
                enabled  = !uiState.isLoading && uiState.pin.isNotBlank() && !uiState.isRateLimited,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape    = RoundedCornerShape(12.dp),
                colors   = ButtonDefaults.buttonColors(
                    containerColor         = Primary,
                    contentColor           = Color(0xFF06080A),
                    disabledContainerColor = Primary.copy(alpha = 0.3f),
                    disabledContentColor   = Color(0xFF06080A).copy(alpha = 0.5f)
                )
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        modifier    = Modifier.size(20.dp),
                        color       = Color(0xFF06080A),
                        strokeWidth = 2.dp
                    )
                } else if (uiState.isRateLimited) {
                    Text(
                        text  = "Bloqué (${uiState.rateLimitCountdown}s)",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color(0xFF06080A)
                    )
                } else {
                    Text(
                        text  = "Entrer",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color(0xFF06080A)
                    )
                }
            }
        }
    }
}
