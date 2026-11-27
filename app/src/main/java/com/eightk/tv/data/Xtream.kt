package com.eightk.tv.data

import android.content.Context
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import android.util.Base64
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Response
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

object Config {
    /** Serverët IPTV. Aplikacioni i provon të gjithë njëkohësisht dhe përdor të parin që punon. */
    val SERVERS = listOf(
        "http://8k.cms-only.ru",
        "http://apps.dakord.xyz",
        "http://not90904.matrix.dakord.xyz",
        "http://not90904.matrix.perty.xyz",
        "http://tv.business-cloud-8.ru",
        "http://pro.business-cloud-8.ru",
        "http://pro.dakord.xyz",
        "http://pro.perty.xyz",
        "http://cf.dakord.xyz",
        "http://cf.perty.xyz",
    )
    val SERVER = SERVERS.first()
    const val USER_AGENT = "IPTVSmartersPlayer"
}

class AuthException(msg: String) : IOException(msg)

data class VodInfo(
    val plot: String?, val genre: String?, val cast: String?, val director: String?,
    val year: String?, val duration: String?, val rating: String?, val backdrop: String?,
)

data class SeriesFull(val info: VodInfo, val episodes: List<Episode>)

data class EpgNow(val title: String, val desc: String, val start: Long, val end: Long)

/** Provon të gjithë serverët paralelisht; kthen klientin e parë që hyn me sukses. */
suspend fun loginAny(user: String, pass: String): XtreamClient = coroutineScope {
    val servers = Config.SERVERS
    val ch = Channel<Result<XtreamClient>>(servers.size)
    val jobs = servers.map { s ->
        launch { ch.send(runCatching { XtreamClient(s, user, pass).also { it.login() } }) }
    }
    var authError = false
    repeat(servers.size) {
        val r = ch.receive()
        r.getOrNull()?.let { ok -> jobs.forEach { it.cancel() }; return@coroutineScope ok }
        if (r.exceptionOrNull() is AuthException) authError = true
    }
    if (authError) throw AuthException("Username ose password i gabuar")
    throw IOException("Asnjë server nuk u përgjigj. Kontrollo internetin.")
}

/** Provon disa serverë njëkohësisht; kthen përgjigjen e parë të suksesshme. */
private suspend fun <T> raceServers(servers: List<String>, block: suspend (String) -> T): Pair<String, T> = coroutineScope {
    val ch = Channel<Result<Pair<String, T>>>(servers.size)
    val jobs = servers.map { s -> launch { ch.send(runCatching { s to block(s) }) } }
    var last: Throwable? = null
    repeat(servers.size) {
        val r = ch.receive()
        r.getOrNull()?.let { ok -> jobs.forEach { it.cancel() }; return@coroutineScope ok }
        last = r.exceptionOrNull()
    }
    throw (last as? IOException) ?: IOException("Asnjë server nuk u përgjigj")
}

data class AccountInfo(
    val status: String,
    val expDate: Long?,
    val maxConnections: String,
    val activeConnections: String,
)

data class Category(val id: String, val name: String)
data class LiveStream(val id: Int, val name: String, val icon: String?)
data class Vod(val id: Int, val name: String, val icon: String?, val ext: String, val rating: String?)
data class Series(val id: Int, val name: String, val cover: String?, val plot: String?)
data class Episode(val id: String, val title: String, val season: Int, val num: Int, val ext: String, val image: String?)

private val http = OkHttpClient.Builder()
    .connectTimeout(10, TimeUnit.SECONDS)
    .readTimeout(60, TimeUnit.SECONDS)
    .followRedirects(true)
    .followSslRedirects(true)
    .build()

private fun JSONObject.str(key: String): String? =
    optString(key).takeIf { it.isNotBlank() && it != "null" }

private inline fun <T> JSONArray.objects(f: (JSONObject) -> T): List<T> =
    (0 until length()).mapNotNull { i -> optJSONObject(i)?.let(f) }

/** Klienti për Xtream Codes API (player_api.php) */
class XtreamClient(server: String, val user: String, val pass: String) {

    /** Serveri aktiv. Ndryshon vetë nëse ky nuk përgjigjet (p.sh. kur ndizet/fiket VPN). */
    var server: String = server.trim().trimEnd('/').let {
        if (it.startsWith("http://") || it.startsWith("https://")) it else "http://$it"
    }
        private set

    private val cache = HashMap<String, Any>()
    var account: AccountInfo? = null
        private set

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private suspend fun request(base: String, action: String?, params: String): String {
        val url = buildString {
            append(base).append("/player_api.php?username=").append(enc(user))
            append("&password=").append(enc(pass))
            if (action != null) append("&action=").append(action)
            append(params)
        }
        val req = Request.Builder().url(url).header("User-Agent", Config.USER_AGENT).build()
        val okCall = http.newCall(req)
        return suspendCancellableCoroutine { cont ->
            cont.invokeOnCancellation { okCall.cancel() }
            okCall.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (cont.isActive) cont.resumeWithException(e)
                }
                override fun onResponse(call: Call, response: Response) {
                    response.use { r ->
                        try {
                            if (!r.isSuccessful) throw IOException("Serveri ktheu gabim ${r.code}")
                            val text = r.body?.string().orEmpty()
                            val t = text.trimStart()
                            // Faqe bllokimi (pa VPN) ose përgjigje e çuditshme -> provo një server tjetër
                            if (!t.startsWith("{") && !t.startsWith("[")) throw IOException("Përgjigje e pavlefshme nga serveri")
                            cont.resume(text)
                        } catch (e: Exception) {
                            if (cont.isActive) cont.resumeWithException(e)
                        }
                    }
                }
            })
        }
    }

    private suspend fun call(action: String?, params: String = ""): String {
        return try {
            request(server, action, params)
        } catch (e: IOException) {
            val others = Config.SERVERS.filter { it != server }
            if (others.isEmpty()) throw e
            val (s, text) = try {
                raceServers(others) { request(it, action, params) }
            } catch (ignored: IOException) {
                throw e
            }
            server = s
            text
        }
    }

    private fun array(json: String): JSONArray =
        json.trim().let { if (it.startsWith("[")) JSONArray(it) else JSONArray() }

    @Suppress("UNCHECKED_CAST")
    private suspend fun <T : Any> cached(key: String, load: suspend () -> T): T {
        cache[key]?.let { return it as T }
        return load().also { cache[key] = it }
    }

    fun clearCache() = cache.clear()

    suspend fun login(): AccountInfo {
        val body = call(null)
        val o = try { JSONObject(body) } catch (e: Exception) {
            throw IOException("Serveri nuk u përgjigj si duhet")
        }
        val ui = o.optJSONObject("user_info") ?: throw AuthException("Username ose password i gabuar")
        if (ui.optString("auth") != "1") throw AuthException("Username ose password i gabuar")
        val status = ui.optString("status")
        if (status.isNotBlank() && !status.equals("Active", true)) throw AuthException("Llogaria: $status")
        return AccountInfo(
            status = status,
            expDate = ui.str("exp_date")?.toLongOrNull(),
            maxConnections = ui.optString("max_connections"),
            activeConnections = ui.optString("active_cons"),
        ).also { account = it }
    }

    /** type = "live", "vod" ose "series" */
    suspend fun categories(type: String): List<Category> = cached("cat-$type") {
        array(call("get_${type}_categories")).objects {
            Category(it.optString("category_id"), it.optString("category_name"))
        }
    }

    suspend fun liveStreams(categoryId: String): List<LiveStream> = cached("live-$categoryId") {
        array(call("get_live_streams", "&category_id=${enc(categoryId)}")).objects {
            LiveStream(it.optInt("stream_id"), it.optString("name"), it.str("stream_icon"))
        }
    }

    /** Të gjitha kanalet (për faqen kryesore dhe kërkimin) */
    suspend fun allLive(): List<LiveStream> = cached("live-all") {
        array(call("get_live_streams")).objects {
            LiveStream(it.optInt("stream_id"), it.optString("name"), it.str("stream_icon"))
        }
    }

    suspend fun allVod(): List<Vod> = cached("vod-all") {
        array(call("get_vod_streams")).objects {
            Vod(it.optInt("stream_id"), it.optString("name"), it.str("stream_icon"),
                it.str("container_extension") ?: "mp4", it.str("rating"))
        }
    }

    suspend fun allSeries(): List<Series> = cached("series-all") {
        array(call("get_series")).objects {
            Series(it.optInt("series_id"), it.optString("name"), it.str("cover"), it.str("plot"))
        }
    }

    private val epgCache = HashMap<Int, Pair<Long, List<EpgNow>>>()

    /** Programi aktual dhe ai që vjen (EPG). Rifreskohet kur mbaron programi. */
    suspend fun epg(streamId: Int): List<EpgNow> {
        val now = System.currentTimeMillis() / 1000
        epgCache[streamId]?.let { (at, l) ->
            val valid = if (l.isEmpty()) now - at < 600 else l.first().end > now
            if (valid) return l
        }
        val o = JSONObject(call("get_short_epg", "&stream_id=$streamId&limit=4"))
        val list = (o.optJSONArray("epg_listings") ?: JSONArray()).objects {
            EpgNow(
                b64(it.optString("title")), b64(it.optString("description")),
                it.optString("start_timestamp").toLongOrNull() ?: 0L,
                it.optString("stop_timestamp").toLongOrNull() ?: 0L,
            )
        }.filter { it.end == 0L || it.end > now }.take(2)
        epgCache[streamId] = now to list
        return list
    }

    suspend fun epgNow(streamId: Int): EpgNow? = epg(streamId).firstOrNull()

    suspend fun vodInfo(id: Int): VodInfo = cached("vinfo-$id") {
        val o = JSONObject(call("get_vod_info", "&vod_id=$id"))
        parseInfo(o.optJSONObject("info") ?: JSONObject())
    }

    private fun parseInfo(i: JSONObject): VodInfo {
        val backdrop = when (val b = i.opt("backdrop_path")) {
            is JSONArray -> b.optString(0)
            is String -> b
            else -> null
        }?.takeIf { it.isNotBlank() && it != "null" }
        return VodInfo(
            plot = i.str("plot") ?: i.str("description"),
            genre = i.str("genre"),
            cast = i.str("cast") ?: i.str("actors"),
            director = i.str("director"),
            year = (i.str("releasedate") ?: i.str("releaseDate") ?: i.str("release_date"))?.take(4),
            duration = i.str("duration") ?: i.str("episode_run_time")?.let { "$it min" },
            rating = i.str("rating"),
            backdrop = backdrop,
        )
    }

    private fun b64(s: String): String = try {
        String(Base64.decode(s, Base64.DEFAULT), Charsets.UTF_8).trim()
    } catch (e: Exception) { s }

    suspend fun vod(categoryId: String): List<Vod> = cached("vod-$categoryId") {
        array(call("get_vod_streams", "&category_id=${enc(categoryId)}")).objects {
            Vod(
                it.optInt("stream_id"), it.optString("name"), it.str("stream_icon"),
                it.str("container_extension") ?: "mp4", it.str("rating"),
            )
        }
    }

    suspend fun series(categoryId: String): List<Series> = cached("series-$categoryId") {
        array(call("get_series", "&category_id=${enc(categoryId)}")).objects {
            Series(it.optInt("series_id"), it.optString("name"), it.str("cover"), it.str("plot"))
        }
    }

    suspend fun seriesFull(seriesId: Int): SeriesFull = cached("sfull-$seriesId") {
        val o = JSONObject(call("get_series_info", "&series_id=$seriesId"))
        val out = mutableListOf<Episode>()
        val parse: (JSONArray) -> Unit = { a ->
            out += a.objects { e ->
                Episode(
                    id = e.optString("id"),
                    title = e.str("title") ?: "Episodi",
                    season = e.optString("season").toIntOrNull() ?: 1,
                    num = e.optString("episode_num").toIntOrNull() ?: 0,
                    ext = e.str("container_extension") ?: "mp4",
                    image = e.optJSONObject("info")?.str("movie_image"),
                )
            }
        }
        when (val eps = o.opt("episodes")) {
            is JSONObject -> eps.keys().forEach { k -> eps.optJSONArray(k)?.let(parse) }
            is JSONArray -> for (i in 0 until eps.length()) eps.optJSONArray(i)?.let(parse)
        }
        SeriesFull(
            parseInfo(o.optJSONObject("info") ?: JSONObject()),
            out.sortedWith(compareBy({ it.season }, { it.num })),
        )
    }

    fun liveUrl(id: Int) = "$server/live/$user/$pass/$id.m3u8"
    fun vodUrl(id: Int, ext: String) = "$server/movie/$user/$pass/$id.$ext"
    fun episodeUrl(id: String, ext: String) = "$server/series/$user/$pass/$id.$ext"
}

object Prefs {
    private fun sp(c: Context) = c.getSharedPreferences("account", Context.MODE_PRIVATE)

    fun save(c: Context, x: XtreamClient) = sp(c).edit()
        .putString("server", x.server).putString("user", x.user).putString("pass", x.pass).apply()

    fun load(c: Context): XtreamClient? {
        val s = sp(c)
        val u = s.getString("user", null) ?: return null
        val p = s.getString("pass", null) ?: return null
        return XtreamClient(s.getString("server", null) ?: Config.SERVER, u, p)
    }

    fun clear(c: Context) = sp(c).edit().clear().apply()
}
