package com.streamlocal.app.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Extension property qui crée un DataStore unique par Context (singleton par processus).
 * Nom du fichier de préférences : "streamlocal_prefs.preferences_pb"
 */
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "streamlocal_prefs")

/**
 * Couche de persistance des préférences utilisateur via Jetpack DataStore.
 *
 * DataStore est l'alternative moderne à SharedPreferences :
 * - Accès asynchrone via Kotlin Flow (pas de lecture bloquante sur le thread principal)
 * - Stockage typé et sûr via [Preferences.Key]
 * - Transactions atomiques via [edit]
 *
 * Préférences stockées :
 * - [KEY_SERVER_URL]      : URL complète du serveur (ex: "http://192.168.1.10:5000")
 * - [KEY_TRUST_ALL_CERTS] : Accepter les certificats SSL auto-signés (défaut : true)
 */
class AppPreferences(private val context: Context) {

    companion object {
        /** Clé de l'URL du serveur dans le DataStore. */
        val KEY_SERVER_URL       = stringPreferencesKey("server_url")

        /** Clé du flag SSL. true = accepter tous les certificats (réseau local). */
        val KEY_TRUST_ALL_CERTS  = booleanPreferencesKey("trust_all_certs")
    }

    /** Flow émettant l'URL du serveur à chaque modification. null si jamais configurée. */
    val serverUrlFlow: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[KEY_SERVER_URL]
    }

    /** Flow émettant la préférence SSL. Vaut true par défaut (réseau local = auto-signé fréquent). */
    val trustAllCertsFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_TRUST_ALL_CERTS] ?: true
    }

    /** Sauvegarde l'URL du serveur de façon atomique. */
    suspend fun saveServerUrl(url: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SERVER_URL] = url
        }
    }

    /** Sauvegarde la préférence SSL de façon atomique. */
    suspend fun saveTrustAllCerts(trust: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_TRUST_ALL_CERTS] = trust
        }
    }

    /** Supprime l'URL sauvegardée (utilisé si on souhaite forcer la re-configuration). */
    suspend fun clearServerUrl() {
        context.dataStore.edit { prefs ->
            prefs.remove(KEY_SERVER_URL)
        }
    }
}
