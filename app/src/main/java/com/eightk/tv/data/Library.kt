package com.eightk.tv.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateListOf
import org.json.JSONArray
import org.json.JSONObject

data class SavedItem(val type: String, val id: Int, val title: String, val image: String?, val ext: String? = null)

/** Të preferuarat dhe historiku, të ruajtura në pajisje */
object Library {
    const val LIVE = "live"
    const val MOVIE = "movie"
    const val SERIES = "series"

    val favorites = mutableStateListOf<SavedItem>()
    val history = mutableStateListOf<SavedItem>()
    private var sp: SharedPreferences? = null

    fun init(c: Context) {
        if (sp != null) return
        sp = c.applicationContext.getSharedPreferences("library", Context.MODE_PRIVATE)
        favorites.addAll(read("fav"))
        history.addAll(read("hist"))
    }

    private fun same(a: SavedItem, type: String, id: Int) = a.type == type && a.id == id

    fun isFav(type: String, id: Int) = favorites.any { same(it, type, id) }

    /** Kthen true nëse u shtua, false nëse u hoq */
    fun toggleFav(item: SavedItem): Boolean {
        val i = favorites.indexOfFirst { same(it, item.type, item.id) }
        val added = if (i >= 0) { favorites.removeAt(i); false } else { favorites.add(0, item); true }
        write("fav", favorites)
        return added
    }

    fun addHistory(item: SavedItem) {
        history.removeAll { same(it, item.type, item.id) }
        history.add(0, item)
        while (history.size > 60) history.removeAt(history.lastIndex)
        write("hist", history)
    }

    fun clearHistory() { history.clear(); write("hist", history) }

    private fun read(key: String): List<SavedItem> = runCatching {
        val a = JSONArray(sp?.getString(key, "[]") ?: "[]")
        (0 until a.length()).map { idx ->
            val o = a.getJSONObject(idx)
            SavedItem(
                o.getString("t"), o.getInt("i"), o.optString("n"),
                o.optString("img").ifBlank { null }, o.optString("e").ifBlank { null },
            )
        }
    }.getOrDefault(emptyList())

    private fun write(key: String, list: List<SavedItem>) {
        val a = JSONArray()
        list.forEach {
            a.put(JSONObject().put("t", it.type).put("i", it.id).put("n", it.title)
                .put("img", it.image ?: "").put("e", it.ext ?: ""))
        }
        sp?.edit()?.putString(key, a.toString())?.apply()
    }
}
