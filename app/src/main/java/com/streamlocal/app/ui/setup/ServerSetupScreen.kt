package com.streamlocal.app.ui.setup

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.streamlocal.app.ui.theme.Background
import com.streamlocal.app.ui.theme.CardBorder
import com.streamlocal.app.ui.theme.Error
import com.streamlocal.app.ui.theme.OnBackground
import com.streamlocal.app.ui.theme.Primary
import com.streamlocal.app.ui.theme.Surface
import com.streamlocal.app.ui.theme.TextSecondary

@Composable
fun ServerSetupScreen(
    onSetupComplete: () -> Unit,
    viewModel: ServerSetupViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(uiState.isSetupComplete) {
        if (uiState.isSetupComplete) {
            viewModel.resetSetupComplete()
            onSetupComplete()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .imePadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Logo / Icon area
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(Surface, RoundedCornerShape(20.dp))
                    .border(1.dp, CardBorder, RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector         = Icons.Default.Language,
                    contentDescription  = null,
                    tint                = Primary,
                    modifier            = Modifier.size(44.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text  = "StreamLocal",
                style = MaterialTheme.typography.headlineLarge,
                color = Primary
            )
            Text(
                text  = "Configuration du serveur",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(40.dp))

            // Server URL field
            OutlinedTextField(
                value       = uiState.serverUrl,
                onValueChange = viewModel::onUrlChange,
                label       = { Text("URL du serveur") },
                placeholder = { Text("https://192.168.1.100:8888", color = TextSecondary) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Language,
                        contentDescription = null,
                        tint = TextSecondary
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction    = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        keyboard?.hide()
                        viewModel.testAndSave()
                    }
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = Primary,
                    unfocusedBorderColor = CardBorder,
                    focusedLabelColor    = Primary,
                    unfocusedLabelColor  = TextSecondary,
                    cursorColor          = Primary,
                    focusedTextColor     = OnBackground,
                    unfocusedTextColor   = OnBackground
                ),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Trust self-signed switch
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Surface, RoundedCornerShape(12.dp))
                    .border(1.dp, CardBorder, RoundedCornerShape(12.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment    = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text  = "Certificats auto-signés",
                        style = MaterialTheme.typography.bodyMedium,
                        color = OnBackground
                    )
                    Text(
                        text  = "Requis pour les serveurs locaux TLS",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
                Switch(
                    checked         = uiState.trustAllCerts,
                    onCheckedChange = viewModel::onTrustAllCertsChange,
                    colors          = SwitchDefaults.colors(
                        checkedThumbColor        = OnBackground,
                        checkedTrackColor        = Primary,
                        uncheckedThumbColor      = TextSecondary,
                        uncheckedTrackColor      = Surface
                    )
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Error message
            AnimatedVisibility(
                visible = uiState.errorMessage != null,
                enter   = fadeIn(),
                exit    = fadeOut()
            ) {
                uiState.errorMessage?.let { msg ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Error.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector        = Icons.Default.Warning,
                            contentDescription = null,
                            tint               = Error,
                            modifier           = Modifier.size(18.dp)
                        )
                        Text(
                            text  = msg,
                            style = MaterialTheme.typography.bodySmall,
                            color = Error
                        )
                    }
                }
            }

            // Success message
            AnimatedVisibility(
                visible = uiState.successMessage != null,
                enter   = fadeIn(),
                exit    = fadeOut()
            ) {
                uiState.successMessage?.let { msg ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Primary.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector        = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint               = Primary,
                            modifier           = Modifier.size(18.dp)
                        )
                        Text(
                            text  = msg,
                            style = MaterialTheme.typography.bodySmall,
                            color = Primary
                        )
                    }
                }
            }

            if (uiState.errorMessage != null || uiState.successMessage != null) {
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Test & Continue button
            Button(
                onClick  = {
                    keyboard?.hide()
                    viewModel.testAndSave()
                },
                enabled  = !uiState.isLoading && uiState.serverUrl.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape    = RoundedCornerShape(12.dp),
                colors   = ButtonDefaults.buttonColors(
                    containerColor         = Primary,
                    contentColor           = Color(0xFF06080A),
                    disabledContainerColor = Primary.copy(alpha = 0.4f),
                    disabledContentColor   = Color(0xFF06080A).copy(alpha = 0.5f)
                )
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        modifier  = Modifier.size(20.dp),
                        color     = Color(0xFF06080A),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text  = "Tester & Continuer",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color(0xFF06080A)
                    )
                }
            }
        }
    }
}
