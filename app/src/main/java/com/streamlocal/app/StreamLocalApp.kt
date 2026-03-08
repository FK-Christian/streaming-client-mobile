package com.streamlocal.app

import android.app.Application
import coil.Coil
import coil.ImageLoader
import coil.util.DebugLogger
import com.streamlocal.app.data.api.NetworkClient
import com.streamlocal.app.data.preferences.AppPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Classe Application principale.
 *
 * Responsabilités :
 * - Fournir une instance globale accessible depuis n'importe quel ViewModel via [instance].
 * - Initialiser [NetworkClient] (OkHttp + Retrofit) avec l'URL et le mode SSL sauvegardés.
 * - Configurer Coil pour qu'il partage le même [okhttp3.OkHttpClient] que Retrofit,
 *   garantissant ainsi la cohérence des cookies de session et de la gestion SSL.
 */
class StreamLocalApp : Application() {

    /** Préférences persistantes (URL serveur, trust SSL). Initialisé de façon paresseuse. */
    val preferences by lazy { AppPreferences(this) }

    /**
     * Scope coroutine à durée de vie applicative.
     * [SupervisorJob] empêche qu'une coroutine enfant échouée n'annule les autres.
     */
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        // Stocke l'instance globale avant toute autre initialisation
        instance = this

        // Initialisation réseau sur le thread IO pour ne pas bloquer le thread principal
        applicationScope.launch(Dispatchers.IO) {
            val serverUrl     = preferences.serverUrlFlow.first()
            val trustAllCerts = preferences.trustAllCertsFlow.first()

            // Configure Retrofit/OkHttp uniquement si une URL a déjà été enregistrée
            if (!serverUrl.isNullOrBlank()) {
                NetworkClient.configure(serverUrl, trustAllCerts)
            }

            // Coil utilise le même OkHttpClient que Retrofit :
            // → les cookies de session Flask sont automatiquement envoyés avec chaque image
            // → les certificats auto-signés sont acceptés si trustAllCerts = true
            Coil.setImageLoader(
                ImageLoader.Builder(this@StreamLocalApp)
                    .okHttpClient { NetworkClient.okHttpClient }
                    .crossfade(true)
                    .build()
            )
        }
    }

    companion object {
        /** Référence globale à l'Application. Utilisée par les ViewModels pour accéder au contexte. */
        lateinit var instance: StreamLocalApp
            private set
    }
}
