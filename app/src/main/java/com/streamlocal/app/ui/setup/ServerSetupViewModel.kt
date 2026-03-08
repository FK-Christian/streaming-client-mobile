package com.streamlocal.app.ui.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streamlocal.app.StreamLocalApp
import com.streamlocal.app.data.api.NetworkClient
import com.streamlocal.app.data.preferences.AppPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

data class SetupUiState(
    val serverUrl:      String  = "",
    val trustAllCerts:  Boolean = true,
    val isLoading:      Boolean = false,
    val errorMessage:   String? = null,
    val successMessage: String? = null,
    val isSetupComplete: Boolean = false
)

class ServerSetupViewModel : ViewModel() {

    private val preferences = AppPreferences(StreamLocalApp.instance)

    private val _uiState = MutableStateFlow(SetupUiState())
    val uiState: StateFlow<SetupUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val savedUrl   = preferences.serverUrlFlow.first()
            val trustCerts = preferences.trustAllCertsFlow.first()
            _uiState.value = _uiState.value.copy(
                serverUrl     = savedUrl ?: "",
                trustAllCerts = trustCerts
            )
        }
    }

    fun onUrlChange(url: String) {
        _uiState.value = _uiState.value.copy(
            serverUrl    = url,
            errorMessage = null,
            successMessage = null
        )
    }

    fun onTrustAllCertsChange(trust: Boolean) {
        _uiState.value = _uiState.value.copy(trustAllCerts = trust)
    }

    fun testAndSave() {
        val url = _uiState.value.serverUrl.trim()
        if (url.isBlank()) {
            _uiState.value = _uiState.value.copy(errorMessage = "Veuillez entrer une URL")
            return
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            _uiState.value = _uiState.value.copy(errorMessage = "L'URL doit commencer par https://")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading      = true,
                errorMessage   = null,
                successMessage = null
            )

            val trust = _uiState.value.trustAllCerts
            val testResult = withContext(Dispatchers.IO) { testConnection(url, trust) }

            if (testResult) {
                // Save to preferences
                preferences.saveServerUrl(url)
                preferences.saveTrustAllCerts(trust)
                // Configure NetworkClient
                NetworkClient.configure(url, trust)

                _uiState.value = _uiState.value.copy(
                    isLoading      = false,
                    successMessage = "Connexion réussie !",
                    isSetupComplete = true
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    isLoading    = false,
                    errorMessage = "Impossible de se connecter au serveur. Vérifiez l'URL."
                )
            }
        }
    }

    private fun testConnection(baseUrl: String, trustAllCerts: Boolean): Boolean {
        return try {
            val normalizedUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
            val testUrl = "${normalizedUrl}api/v1/files/videos"

            val clientBuilder = OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)

            if (trustAllCerts) {
                val trustManager = object : X509TrustManager {
                    override fun checkClientTrusted(chain: Array<out X509Certificate>, authType: String) = Unit
                    override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String) = Unit
                    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
                }
                val sslContext = SSLContext.getInstance("TLS").apply {
                    init(null, arrayOf<TrustManager>(trustManager), SecureRandom())
                }
                clientBuilder.sslSocketFactory(sslContext.socketFactory, trustManager)
                clientBuilder.hostnameVerifier { _, _ -> true }
            }

            val client = clientBuilder.build()
            val request = Request.Builder().url(testUrl).build()
            val response = client.newCall(request).execute()
            // Accept 200 (success) or 401 (server alive but needs auth)
            val code = response.code
            response.close()
            code == 200 || code == 401 || code == 403
        } catch (e: Exception) {
            false
        }
    }

    fun resetSetupComplete() {
        _uiState.value = _uiState.value.copy(isSetupComplete = false)
    }
}
