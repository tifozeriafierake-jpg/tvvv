package com.eightk.tv.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.eightk.tv.data.Category
import com.eightk.tv.data.LiveStream
import kotlinx.coroutines.CancellationException
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.platform.LocalContext
import com.eightk.tv.data.Library
import com.eightk.tv.data.SavedItem
import kotlinx.coroutines.delay
import com.eightk.tv.data.EpgNow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.LaunchedEffect

// ---- Ngjyrat (stil Nimi TV) ----
val Accent = Color(0xFFE4252B)      // e kuqe
val Purple = Color(0xFF8E7CFF)      // vija e zgjedhjes
val Bg = Color(0xFF161618)
val CardBg = Color(0xFF2B2B2E)
val CatSel = Color(0xFF3A3A3E)
val TextDim = Color(0xFFA8A8AE)

val BgBrush = Brush.linearGradient(
    listOf(Color(0xFF121214), Color(0xFF19191C), Color(0xFF1F1F23)),
    start = Offset(0f, Float.POSITIVE_INFINITY),
    end = Offset(Float.POSITIVE_INFINITY, 0f),
)

fun hm(sec: Long): String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(sec * 1000))

fun progressOf(e: EpgNow): Float {
    if (e.end <= e.start) return 0f
    val now = System.currentTimeMillis() / 1000
    return ((now - e.start).toFloat() / (e.end - e.start)).coerceIn(0f, 1f)
}

/** Shirit progresi (sa ka ecur programi) */
@Composable
fun ProgressLine(frac: Float, modifier: Modifier = Modifier) {
    Box(modifier.height(3.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = .18f))) {
        Box(Modifier.fillMaxWidth(frac).fillMaxHeight().background(Purple))
    }
}

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(primary = Accent, secondary = Purple, background = Bg, surface = CardBg)) {
        Surface(Modifier.fillMaxSize().background(BgBrush), color = Color.Transparent, contentColor = Color.White) {
            content()
        }
    }
}

/** Kartë e fokusueshme me telekomandë: zmadhohet dhe merr kornizë të bardhë kur fokusohet */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TvCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(8.dp),
    bg: Color = CardBg,
    focusBg: Color = bg,
    scaleOnFocus: Float = 1.07f,
    focusBorder: Boolean = true,
    onFocus: () -> Unit = {},
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
    content: @Composable BoxScope.(focused: Boolean) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) scaleOnFocus else 1f, label = "scale")
    Box(
        modifier
            .zIndex(if (focused) 1f else 0f)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .background(if (focused) focusBg else bg)
            .then(if (focused && focusBorder) Modifier.border(3.dp, Color.White, shape) else Modifier)
            .onFocusChanged { focused = it.isFocused; if (it.isFocused) onFocus() }
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onLongClick = onLongClick,
                onClick = onClick,
            )
    ) { content(focused) }
}

/** Skedat e kategorive (si "All  LAJME  SPORT  FEMIJE") */
@Composable
fun CategoryTabs(cats: List<Category>, selectedId: String?, onSelect: (Category) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(cats) { c ->
            val sel = c.id == selectedId
            TvCard(
                bg = if (sel) Color.White.copy(alpha = .12f) else Color.Transparent,
                focusBg = Color.White.copy(alpha = .18f),
                shape = RoundedCornerShape(6.dp),
                scaleOnFocus = 1.03f,
                onFocus = { onSelect(c) },
                onClick = { onSelect(c) },
            ) { f ->
                Column(
                    Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        c.name.uppercase(), fontSize = 14.sp, maxLines = 1,
                        fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium,
                        color = if (sel || f) Color.White else TextDim,
                    )
                    Box(
                        Modifier.padding(top = 4.dp).height(2.dp).width(22.dp)
                            .background(if (sel) Purple else Color.Transparent)
                    )
                }
            }
        }
    }
}

// ---- Shtetet dhe kategoritë ----

data class CatGroup(val key: String, val name: String, val cats: List<Category>)

private val prefixRe = Regex("""^\s*([A-Za-z0-9]{2,6}(?:-[A-Za-z0-9]{2,4})?)\s*(?:\||:|\s[-–])\s*(.+)$""")

private val countryNames = mapOf(
    "AL" to "Shqipëri", "ALB" to "Shqipëri", "KS" to "Kosovë", "KOS" to "Kosovë", "MK" to "Maqedoni",
    "GR" to "Greqi", "IT" to "Itali", "UK" to "Angli", "GB" to "Angli", "US" to "SHBA", "USA" to "SHBA",
    "DE" to "Gjermani", "FR" to "Francë", "ES" to "Spanjë", "TR" to "Turqi", "CH" to "Zvicër",
    "AT" to "Austri", "NL" to "Holandë", "BE" to "Belgjikë", "SE" to "Suedi", "NO" to "Norvegji",
    "DK" to "Danimarkë", "FI" to "Finlandë", "PT" to "Portugali", "PL" to "Poloni", "RO" to "Rumani",
    "BG" to "Bullgari", "HR" to "Kroaci", "RS" to "Serbi", "BA" to "Bosnjë", "ME" to "Mal i Zi",
    "SI" to "Slloveni", "HU" to "Hungari", "CZ" to "Çeki", "SK" to "Sllovaki", "RU" to "Rusi",
    "UA" to "Ukrainë", "AR" to "Arabe", "IN" to "Indi", "PK" to "Pakistan", "CA" to "Kanada",
    "AU" to "Australi", "BR" to "Brazil", "MX" to "Meksikë", "LAT" to "Amerika Latine", "IR" to "Iran",
    "IE" to "Irlandë", "CY" to "Qipro", "EX-YU" to "Ex-YU", "YU" to "Ex-YU", "AF" to "Afrikë",
)

/** Ndan kategoritë sipas shtetit nga prefiksi (p.sh. "AL| ALBANIA GOLD", "GR - ΚΙΝΗΜΑΤΟΓΡΑΦΟΣ") */
fun groupCategories(cats: List<Category>): List<CatGroup> {
    val order = LinkedHashMap<String, MutableList<Category>>()
    for (c in cats) {
        val m = prefixRe.find(c.name)
        val code = m?.groupValues?.get(1)?.uppercase() ?: "__other"
        val label = m?.groupValues?.get(2)?.trim()?.ifBlank { null } ?: c.name
        order.getOrPut(code) { mutableListOf() } += Category(c.id, label)
    }
    return order.map { (code, list) ->
        val name = when (code) {
            "__other" -> "Të tjera"
            else -> countryNames[code]?.let { "$it ($code)" } ?: code
        }
        CatGroup(code, name, list)
    }
}

@Composable
private fun CatRow(
    text: String,
    sel: Boolean,
    indent: Boolean,
    bold: Boolean = false,
    onFocus: () -> Unit = {},
    onClick: () -> Unit,
) {
    TvCard(
        Modifier.fillMaxWidth().padding(start = if (indent) 12.dp else 0.dp),
        bg = if (sel) CatSel else Color.Transparent,
        focusBg = Color(0xFF4A4A4F),
        shape = RoundedCornerShape(6.dp),
        scaleOnFocus = 1.02f,
        focusBorder = false,
        onFocus = onFocus,
        onClick = onClick,
    ) { f ->
        Text(
            text,
            Modifier.padding(horizontal = 14.dp, vertical = if (indent) 9.dp else 12.dp),
            fontSize = if (indent) 13.sp else 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            fontWeight = if (sel || bold) FontWeight.Bold else FontWeight.Medium,
            color = if (sel || f) Color.White else TextDim,
        )
    }
}

/**
 * Lista anash: sipër "All"/"Të preferuarat", pastaj shtetet.
 * Kur shtyp OK mbi një shtet, hapen poshtë tij kategoritë e atij shteti.
 */
@Composable
fun CountryCategoryColumn(
    top: List<Category>,
    cats: List<Category>,
    selectedId: String?,
    onSelect: (Category) -> Unit,
) {
    val groups = remember(cats) { groupCategories(cats) }
    val flat = groups.size <= 1
    var open by remember(groups) {
        mutableStateOf(groups.firstOrNull { g -> g.cats.any { it.id == selectedId } }?.key)
    }
    LazyColumn(
        Modifier.width(230.dp).fillMaxHeight(),
        contentPadding = PaddingValues(start = 6.dp, end = 10.dp, top = 24.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        items(top, key = { "t-" + it.id }) { c ->
            CatRow(c.name.uppercase(), c.id == selectedId, false, onFocus = { onSelect(c) }, onClick = { onSelect(c) })
        }
        if (flat) {
            items(cats, key = { "c-" + it.id }) { c ->
                CatRow(c.name.uppercase(), c.id == selectedId, false, onFocus = { onSelect(c) }, onClick = { onSelect(c) })
            }
        } else {
            groups.forEach { g ->
                val isOpen = open == g.key
                val hasSel = g.cats.any { it.id == selectedId }
                item(key = "g-" + g.key) {
                    CatRow(
                        "${if (isOpen) "▾" else "▸"}  ${g.name}  ·  ${g.cats.size}",
                        sel = hasSel && !isOpen, indent = false, bold = isOpen,
                        onClick = { open = if (isOpen) null else g.key },
                    )
                }
                if (isOpen) {
                    items(g.cats, key = { "c-" + it.id }) { c ->
                        CatRow(c.name, c.id == selectedId, true, onFocus = { onSelect(c) }, onClick = { onSelect(c) })
                    }
                }
            }
        }
    }
}

/** Channel card: logo + number. On focus it shows what is airing now (EPG) with a progress bar. */
@Composable
fun ChannelCard(
    number: Int,
    ch: LiveStream,
    modifier: Modifier = Modifier,
    epg: (suspend () -> EpgNow?)? = null,
    onFocus: () -> Unit = {},
    onClick: () -> Unit,
) {
    val ctx = LocalContext.current
    var now by remember(ch.id) { mutableStateOf<EpgNow?>(null) }
    val fav = Library.isFav(Library.LIVE, ch.id)
    TvCard(
        modifier.aspectRatio(1.35f), onFocus = onFocus, onClick = onClick, scaleOnFocus = 1.06f,
        onLongClick = {
            val added = Library.toggleFav(SavedItem(Library.LIVE, ch.id, ch.name, ch.icon))
            Toast.makeText(ctx, if (added) "♥ U shtua te të preferuarat" else "U hoq nga të preferuarat", Toast.LENGTH_SHORT).show()
        },
    ) { focused ->
        if (fav) Text("♥", Modifier.align(Alignment.TopStart).padding(8.dp), color = Accent, fontSize = 14.sp)
        if (epg != null) {
            LaunchedEffect(focused, ch.id) {
                if (focused) { delay(250); now = runCatching { epg() }.getOrNull() }
            }
        }
        val e = now
        if (focused && e != null) {
            Column(Modifier.fillMaxSize().padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(ch.icon, null, Modifier.size(46.dp, 28.dp), contentScale = ContentScale.Fit)
                    Spacer(Modifier.width(8.dp))
                    Text("$number.${ch.name}", fontSize = 12.sp, color = TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(6.dp))
                Text(e.title, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 19.sp)
                Text("${hm(e.start)} - ${hm(e.end)}", fontSize = 12.sp, color = TextDim)
                Spacer(Modifier.weight(1f))
                ProgressLine(progressOf(e), Modifier.fillMaxWidth())
            }
        } else {
            if (ch.icon != null) {
                AsyncImage(
                    ch.icon, ch.name,
                    Modifier.fillMaxSize().padding(horizontal = 26.dp, vertical = 24.dp),
                    contentScale = ContentScale.Fit,
                )
            } else {
                Text(
                    ch.name, Modifier.align(Alignment.Center).padding(10.dp),
                    fontSize = 16.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                "$number", Modifier.align(Alignment.BottomEnd).padding(10.dp),
                fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White,
            )
        }
    }
}

data class PosterItem(
    val id: Int, val title: String, val image: String?, val info: String? = null,
    val rating: String? = null, val ext: String? = null,
)

@Composable
fun PosterCard(
    p: PosterItem,
    modifier: Modifier = Modifier,
    onFocus: () -> Unit = {},
    onClick: () -> Unit,
) {
    TvCard(modifier.aspectRatio(2f / 3f), onFocus = onFocus, onClick = onClick) { focused ->
        AsyncImage(p.image, p.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        if (focused || p.image == null) {
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(0.5f to Color.Transparent, 1f to Color.Black.copy(alpha = .9f))
                )
            )
            Text(
                p.title, Modifier.align(Alignment.BottomStart).padding(8.dp),
                fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun RowTitle(text: String, modifier: Modifier = Modifier) =
    Text(text, modifier.padding(start = 8.dp, bottom = 10.dp), fontSize = 20.sp, fontWeight = FontWeight.SemiBold)

// ---- Ngarkimi i të dhënave ----

sealed interface Load<out T> {
    data object Loading : Load<Nothing>
    data class Ok<T>(val value: T) : Load<T>
    data class Err(val msg: String) : Load<Nothing>
}

@Composable
fun <T> rememberLoad(vararg keys: Any?, block: suspend () -> T): Load<T> {
    val state by produceState<Load<T>>(Load.Loading, *keys) {
        value = Load.Loading
        value = try {
            Load.Ok(block())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Load.Err(e.message ?: "Gabim në lidhje")
        }
    }
    return state
}

@Composable
fun <T> LoadView(state: Load<T>, modifier: Modifier = Modifier.fillMaxSize(), content: @Composable (T) -> Unit) {
    when (state) {
        Load.Loading -> Box(modifier, Alignment.Center) { CircularProgressIndicator(color = Accent) }
        is Load.Err -> Box(modifier, Alignment.Center) { Text("⚠ ${state.msg}", color = Color(0xFFFF6B6B)) }
        is Load.Ok -> content(state.value)
    }
}

@Composable
fun Empty(text: String = "Nuk ka përmbajtje") =
    Box(Modifier.fillMaxWidth().padding(40.dp), Alignment.Center) { Text(text, color = TextDim) }
