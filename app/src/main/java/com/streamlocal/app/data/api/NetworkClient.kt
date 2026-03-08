package com.streamlocal.app.data.api

import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.lang.reflect.Type
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * Gson configuré pour tolérer les champs numériques envoyés comme strings par le serveur.
 *
 * Exemple : le serveur Flask peut retourner `"size": "3.9 Mo"` ou `"size": 1234`.
 * Les TypeAdapters ci-dessous essaient d'abord de lire un nombre natif, puis
 * tombent en repli sur une conversion depuis string.
 */
private val gson = GsonBuilder()
    .registerTypeAdapter(Long::class.java, object : JsonDeserializer<Long> {
        override fun deserialize(json: JsonElement, type: Type, ctx: JsonDeserializationContext): Long =
            runCatching { json.asLong }.getOrElse { json.asString.toLong() }
    })
    .registerTypeAdapter(Int::class.java, object : JsonDeserializer<Int> {
        override fun deserialize(json: JsonElement, type: Type, ctx: JsonDeserializationContext): Int =
            runCatching { json.asInt }.getOrElse { json.asString.toInt() }
    })
    .create()

/**
 * Singleton gérant OkHttp, Retrofit et la session Flask.
 *
 * Doit être initialisé via [configure] avant tout appel réseau.
 * Expose [okHttpClient] pour Coil (images) et [buildExoPlayerClient] pour ExoPlayer (vidéos).
 *
 * Fonctionnalités :
 * - **CookieJar en mémoire** : conserve le cookie de session Flask entre les requêtes.
 * - **Trust-all SSL** : accepte les certificats auto-signés quand [trustAllCerts] = true.
 * - **Logging** : log complet des requêtes/réponses HTTP en debug.
 */
object NetworkClient {

    private var _okHttpClient: OkHttpClient? = null
    private var _retrofit: Retrofit? = null
    private var _api: StreamApi? = null

    /**
     * OkHttpClient partagé avec Coil pour que les images utilisent
     * les mêmes cookies de session et la même configuration SSL.
     * Retourne un client minimal si [configure] n'a pas encore été appelé.
     */
    val okHttpClient: OkHttpClient
        get() = _okHttpClient ?: buildDefaultClient()

    /**
     * Interface Retrofit générée. Lance une IllegalStateException si [configure]
     * n'a pas été appelé au préalable.
     */
    val api: StreamApi
        get() = _api ?: error("NetworkClient not configured. Call configure() first.")

    // ── CookieJar en mémoire ──────────────────────────────────────────────────

    /**
     * CookieJar stockant les cookies par host dans une ConcurrentHashMap.
     *
     * À chaque réponse du serveur, les nouveaux cookies remplacent les anciens
     * portant le même nom (comportement classique de gestion de session).
     * Appelé [clearCookies] à la déconnexion.
     */
    private val inMemoryCookieJar = object : CookieJar {
        private val store = ConcurrentHashMap<String, MutableList<Cookie>>()

        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            val key = url.host
            store.getOrPut(key) { mutableListOf() }.apply {
                // Remplace les cookies existants du même nom (évite les doublons)
                cookies.forEach { newCookie ->
                    removeAll { it.name == newCookie.name }
                    add(newCookie)
                }
            }
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            return store[url.host]?.toList() ?: emptyList()
        }

        fun clear() { store.clear() }
    }

    // ── TrustManager "accept all" ─────────────────────────────────────────────

    /**
     * TrustManager qui accepte tous les certificats SSL sans vérification.
     *
     * ATTENTION : n'utiliser qu'en réseau local de confiance.
     * Ne jamais déployer ce comportement vers des serveurs publics.
     */
    private val trustAllManager = object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>, authType: String) = Unit
        override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String) = Unit
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }

    // ── Configuration principale ──────────────────────────────────────────────

    /**
     * Initialise OkHttp et Retrofit avec l'URL du serveur.
     *
     * Doit être appelé :
     * 1. Au démarrage de l'app (depuis [StreamLocalApp]) si une URL est sauvegardée.
     * 2. Après que l'utilisateur a configuré/modifié l'URL serveur.
     *
     * @param baseUrl      URL de base du serveur (ex: "http://192.168.1.10:5000")
     * @param trustAllCerts true = accepter les certificats auto-signés
     */
    fun configure(baseUrl: String, trustAllCerts: Boolean) {
        // Assure que l'URL se termine par "/" (requis par Retrofit)
        val normalizedUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"

        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        val clientBuilder = OkHttpClient.Builder()
            .cookieJar(inMemoryCookieJar)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)    // plus long pour les listes volumineuses
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(loggingInterceptor)

        if (trustAllCerts) {
            applyTrustAllCerts(clientBuilder)
        }

        _okHttpClient = clientBuilder.build()

        _retrofit = Retrofit.Builder()
            .baseUrl(normalizedUrl)
            .client(_okHttpClient!!)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()

        _api = _retrofit!!.create(StreamApi::class.java)
    }

    /** Client minimal utilisé avant que [configure] soit appelé (ex: chargement des prefs). */
    private fun buildDefaultClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .cookieJar(inMemoryCookieJar)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
            .also { _okHttpClient = it }
    }

    /**
     * Injecte le [trustAllManager] dans le builder OkHttp.
     * Désactive également la vérification du hostname.
     */
    private fun applyTrustAllCerts(builder: OkHttpClient.Builder) {
        try {
            val sslContext = SSLContext.getInstance("TLS").apply {
                init(null, arrayOf<TrustManager>(trustAllManager), SecureRandom())
            }
            builder.sslSocketFactory(sslContext.socketFactory, trustAllManager)
            builder.hostnameVerifier { _, _ -> true }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /** Efface tous les cookies de session (appelé à la déconnexion). */
    fun clearCookies() {
        inMemoryCookieJar.clear()
    }

    /**
     * Crée un OkHttpClient dédié à ExoPlayer.
     *
     * Différences avec le client principal :
     * - Pas de timeout de lecture (`readTimeout = 0`) : nécessaire pour le streaming continu.
     * - Partage le même [CookieJar] : ExoPlayer envoie le cookie de session Flask.
     *
     * @param trustAllCerts Même valeur que celle utilisée lors de la configuration globale.
     */
    fun buildExoPlayerClient(trustAllCerts: Boolean): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .cookieJar(inMemoryCookieJar)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.SECONDS) // 0 = pas de timeout pour le streaming

        if (trustAllCerts) {
            applyTrustAllCerts(builder)
        }
        return builder.build()
    }
}
