package com.eightk.tv.ui

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.eightk.tv.data.AuthException
import com.eightk.tv.data.Category
import com.eightk.tv.data.Library
import com.eightk.tv.data.LiveStream
import com.eightk.tv.data.SavedItem
import com.eightk.tv.data.VodInfo
import com.eightk.tv.data.XtreamClient
import com.eightk.tv.data.loginAny
import com.eightk.tv.player.PlayItem
import com.eightk.tv.player.Playback
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class Section(val label: String, val icon: ImageVector) {
    SEARCH("Kërko", Icons.Filled.Search),
    HOME("Kreu", Icons.Filled.Home),
    FAVORITES("Të preferuarat", Icons.Filled.Favorite),
    LIVE("Live TV", TvIcons.LiveTv),
    MOVIES("Filma", TvIcons.Movie),
    SERIES("Seriale", TvIcons.Series),
    HISTORY("Historiku", TvIcons.History),
    ACCOUNT("Llogaria", Icons.Filled.Person),
}

private const val ALL = "__all"
private const val FAV = "__fav"

/** Film ose serial i hapur, me listën "Më shumë si kjo" */
data class OpenItem(val item: PosterItem, val similar: List<PosterItem>)

fun SavedItem.toPoster() = PosterItem(id, title, image, ext = ext)
fun SavedItem.toLive() = LiveStream(id, title, image)

fun playLive(ctx: Context, client: XtreamClient, list: List<LiveStream>, i: Int) =
    Playback.open(
        ctx, client,
        list.map { PlayItem(it.name, client.liveUrl(it.id), it.id, it.icon) }, i, live = true,
    )

fun playMovie(ctx: Context, client: XtreamClient, p: PosterItem) {
    Library.addHistory(SavedItem(Library.MOVIE, p.id, p.title, p.image, p.ext))
    Playback.open(ctx, client, listOf(PlayItem(p.title, client.vodUrl(p.id, p.ext ?: "mp4"))), 0, live = false)
}

@Composable
fun HomeScreen(client: XtreamClient, onLogout: () -> Unit, onClientChanged: (XtreamClient) -> Unit) {
    var section by rememberSaveable { mutableStateOf(Section.HOME) }
    var openSeries by remember { mutableStateOf<OpenItem?>(null) }
    var openMovie by remember { mutableStateOf<OpenItem?>(null) }
    val liveSel = remember { mutableStateOf<Category?>(null) }
    val vodSel = remember { mutableStateOf<Category?>(null) }
    val seriesSel = remember { mutableStateOf<Category?>(null) }

    // Në çdo hapje provon të gjithë serverët dhe merr më të shpejtin që punon
    // (me VPN ose pa VPN). Gjatë përdorimit, nëse serveri bie, kalon vetë te tjetri.
    LaunchedEffect(client) {
        try {
            val best = loginAny(client.user, client.pass)
            if (best.server != client.server) onClientChanged(best)
        } catch (e: AuthException) {
            onLogout()
        } catch (e: Exception) {
            // pa internet tani; thirrjet do provojnë përsëri vetë
        }
    }

    fun go(s: Section) { section = s; openSeries = null; openMovie = null }
    val detailOpen = openSeries != null || openMovie != null

    Row(Modifier.fillMaxSize()) {
        // ---- Shiriti i ngushtë majtas me ikona ----
        Column(
            Modifier.width(186.dp).fillMaxHeight().background(Color.Black.copy(alpha = .25f))
                .padding(vertical = 18.dp, horizontal = 10.dp),
        ) {
            Row(Modifier.padding(start = 12.dp, bottom = 8.dp)) {
                Text("8K", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Black)
                Text("tv", color = Accent, fontSize = 26.sp, fontWeight = FontWeight.Black)
            }
            Spacer(Modifier.weight(1f))
            Section.entries.filter { it != Section.ACCOUNT }.forEach { s ->
                val sel = s == section && !detailOpen
                TvCard(
                    Modifier.padding(vertical = 3.dp).fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(10.dp),
                    bg = if (sel) CatSel else Color.Transparent,
                    focusBg = Color.White.copy(alpha = .18f),
                    focusBorder = false,
                    scaleOnFocus = 1.04f,
                    onFocus = { go(s) },
                    onClick = { go(s) },
                ) { f ->
                    Row(
                        Modifier.fillMaxSize().padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.width(3.dp).height(22.dp).background(if (sel) Purple else Color.Transparent))
                        Spacer(Modifier.width(9.dp))
                        Icon(s.icon, s.label, Modifier.size(24.dp), tint = if (sel || f) Color.White else TextDim)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            s.label, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            color = if (sel || f) Color.White else TextDim,
                            fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium,
                        )
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            val accSel = section == Section.ACCOUNT && !detailOpen
            TvCard(
                Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(10.dp),
                bg = if (accSel) CatSel else Color.Transparent, focusBg = Color.White.copy(alpha = .18f),
                focusBorder = false, scaleOnFocus = 1.04f,
                onFocus = { go(Section.ACCOUNT) }, onClick = { go(Section.ACCOUNT) },
            ) {
                Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(32.dp).clip(CircleShape).background(Color(0xFFF2C14E)), contentAlignment = Alignment.Center) {
                        Text(client.user.take(1).uppercase(), color = Color(0xFF5A3E00), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                    Spacer(Modifier.width(10.dp))
                    Text("Llogaria", fontSize = 15.sp, color = Color.White, maxLines = 1)
                }
            }
        }

        Box(Modifier.weight(1f).fillMaxHeight()) {
            val series = openSeries
            val movie = openMovie
            val toMovie: (OpenItem) -> Unit = { openMovie = it; openSeries = null }
            val toSeries: (OpenItem) -> Unit = { openSeries = it; openMovie = null }
            when {
                movie != null -> MovieDetail(client, movie, onBack = { openMovie = null }, onOpen = toMovie)
                series != null -> SeriesDetail(client, series, onBack = { openSeries = null }, onOpen = toSeries)
                else -> when (section) {
                    Section.SEARCH -> SearchTab(client, toSeries, toMovie)
                    Section.HOME -> HomeTab(client, toSeries, toMovie)
                    Section.LIVE -> LiveTab(client, liveSel)
                    Section.MOVIES -> MoviesTab(client, vodSel, toMovie)
                    Section.SERIES -> SeriesTab(client, seriesSel, toSeries)
                    Section.HISTORY -> SavedTab(
                        client, "Historiku", Library.history.toList(),
                        "Ende nuk ke shikuar asgjë.", toSeries, toMovie,
                    )
                    Section.FAVORITES -> SavedTab(
                        client, "Të preferuarat", Library.favorites.toList(),
                        "Mbaj shtypur OK mbi një kanal, ose shtyp ♡ te një film/serial, për ta shtuar këtu.",
                        toSeries, toMovie,
                    )
                    Section.ACCOUNT -> AccountTab(client, onLogout)
                }
            }
        }
    }
}

// ================= KRYEFAQJA (vetëm filma dhe seriale, me slide) =================

data class Slide(val item: PosterItem, val isSeries: Boolean)

@Composable
fun HomeTab(client: XtreamClient, onOpenSeries: (OpenItem) -> Unit, onOpenMovie: (OpenItem) -> Unit) {
    val movies = rememberLoad("home-vod") {
        client.allVod().sortedByDescending { it.added }.take(40)
            .map { PosterItem(it.id, it.name, it.icon, null, it.rating, it.ext) }
    }
    val series = rememberLoad("home-series") {
        client.allSeries().sortedByDescending { it.added }.take(40)
            .map { PosterItem(it.id, it.name, it.cover, it.plot) }
    }
    val mList = (movies as? Load.Ok)?.value.orEmpty()
    val sList = (series as? Load.Ok)?.value.orEmpty()
    val slides = remember(mList, sList) {
        val m = mList.take(6).map { Slide(it, false) }
        val ss = sList.take(4).map { Slide(it, true) }
        buildList { for (i in 0 until maxOf(m.size, ss.size)) { m.getOrNull(i)?.let { add(it) }; ss.getOrNull(i)?.let { add(it) } } }
    }
    val heroFocus = remember { FocusRequester() }
    LaunchedEffect(slides.isNotEmpty()) {
        if (slides.isNotEmpty()) { delay(200); runCatching { heroFocus.requestFocus() } }
    }
    fun open(sl: Slide) {
        if (sl.isSeries) onOpenSeries(OpenItem(sl.item, sList)) else onOpenMovie(OpenItem(sl.item, mList))
    }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().weight(0.58f)) {
            if (slides.isEmpty()) {
                LoadView(if (movies is Load.Err) movies else Load.Loading) {}
            } else {
                HeroSlider(client, slides, heroFocus, ::open)
            }
        }
        LazyColumn(
            Modifier.fillMaxWidth().weight(0.42f),
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 32.dp, top = 4.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            val recent = Library.history.toList().filter { it.type != Library.LIVE }
            if (recent.isNotEmpty()) {
                item {
                    RowTitle("Vazhdo të shikosh")
                    PosterRow(recent.map { it.toPoster() }) { p ->
                        val s = recent.first { it.id == p.id }
                        if (s.type == Library.MOVIE) onOpenMovie(OpenItem(p, emptyList()))
                        else onOpenSeries(OpenItem(p, emptyList()))
                    }
                }
            }
            item {
                RowTitle("Filma të rinj")
                LoadView(movies, Modifier.fillMaxWidth().height(240.dp)) { list ->
                    PosterRow(list) { p -> onOpenMovie(OpenItem(p, list)) }
                }
            }
            item {
                RowTitle("Seriale të reja")
                LoadView(series, Modifier.fillMaxWidth().height(240.dp)) { list ->
                    PosterRow(list) { p -> onOpenSeries(OpenItem(p, list)) }
                }
            }
        }
    }
}

/** Slide i madh që ndërrohet vetë çdo 7 sekonda; ◀ ▶ me telekomandë për ta ndërruar */
@Composable
fun HeroSlider(client: XtreamClient, slides: List<Slide>, focus: FocusRequester, onOpen: (Slide) -> Unit) {
    var idx by remember(slides) { mutableStateOf(0) }
    LaunchedEffect(slides, idx) {
        delay(7000)
        if (slides.size > 1) idx = (idx + 1) % slides.size
    }
    val sl = slides[idx.coerceIn(0, slides.lastIndex)]
    val info = rememberLoad("slide", sl.isSeries, sl.item.id) {
        if (sl.isSeries) client.seriesFull(sl.item.id).info else client.vodInfo(sl.item.id)
    }
    val inf = (info as? Load.Ok)?.value

    Box(Modifier.fillMaxSize()) {
        AsyncImage(
            inf?.backdrop ?: sl.item.image, null,
            Modifier.align(Alignment.TopEnd).fillMaxHeight().fillMaxWidth(0.72f).alpha(0.75f),
            contentScale = ContentScale.Crop,
        )
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0.28f to Bg, 0.7f to Color.Transparent)))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.55f to Color.Transparent, 1f to Bg)))

        Column(
            Modifier.align(Alignment.CenterStart).padding(start = 36.dp, top = 24.dp).fillMaxWidth(0.5f),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(if (sl.isSeries) "SERIAL" else "FILM", color = Accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text(sl.item.title, fontSize = 42.sp, fontWeight = FontWeight.Bold, lineHeight = 48.sp,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            val meta = listOfNotNull(inf?.year, inf?.duration, (inf?.rating ?: sl.item.rating)?.let { "★ $it" }, inf?.genre)
                .joinToString("   ·   ")
            if (meta.isNotBlank()) Text(meta, color = TextDim, fontSize = 15.sp, maxLines = 1)
            (inf?.plot ?: sl.item.info)?.let {
                Text(it, fontSize = 16.sp, lineHeight = 23.sp, maxLines = 3, overflow = TextOverflow.Ellipsis,
                    color = Color(0xFFD6D6DA))
            }
            ActionButton(
                if (sl.isSeries) "▶   Shiko serialin" else "▶   Shiko filmin", Accent,
                Modifier.focusRequester(focus).width(230.dp).onPreviewKeyEvent { e ->
                    if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (e.key) {
                        Key.DirectionRight -> { idx = (idx + 1) % slides.size; true }
                        Key.DirectionLeft -> if (idx > 0) { idx -= 1; true } else false
                        else -> false
                    }
                },
            ) { onOpen(sl) }
        }
        // pikat e slide-ve
        Row(
            Modifier.align(Alignment.BottomEnd).padding(end = 36.dp, bottom = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            slides.indices.forEach { i ->
                Box(
                    Modifier.height(4.dp).width(if (i == idx) 22.dp else 8.dp).clip(RoundedCornerShape(2.dp))
                        .background(if (i == idx) Color.White else Color.White.copy(alpha = .35f))
                )
            }
        }
    }
}

@Composable
fun PosterRow(items: List<PosterItem>, onFocus: (PosterItem) -> Unit = {}, onClick: (PosterItem) -> Unit) {
    if (items.isEmpty()) Empty() else
        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(8.dp)) {
            itemsIndexed(items) { _, p ->
                PosterCard(p, Modifier.width(150.dp), onFocus = { onFocus(p) }, onClick = { onClick(p) })
            }
        }
}

// ================= LIVE / FILMA / SERIALE =================

@Composable
fun BrowseTab(
    top: List<Category>,
    cats: Load<List<Category>>,
    selState: MutableState<Category?>,
    content: @Composable (Category) -> Unit,
) {
    var sel by selState
    LoadView(cats) { list ->
        val all = top + list
        LaunchedEffect(list) { if (sel == null || all.none { it.id == sel?.id }) sel = all.firstOrNull() }
        Row(Modifier.fillMaxSize()) {
            CountryCategoryColumn(top, list, sel?.id) { sel = it }
            Box(Modifier.weight(1f).fillMaxHeight()) { sel?.let { content(it) } }
        }
    }
}

@Composable
fun LiveTab(client: XtreamClient, selState: MutableState<Category?>) {
    val ctx = LocalContext.current
    val cats = rememberLoad("live-cats") { client.categories("live") }
    val top = remember { listOf(Category(ALL, "All"), Category(FAV, "♥ Të preferuarat")) }
    val favs = Library.favorites.filter { it.type == Library.LIVE }.map { it.toLive() }
    BrowseTab(top, cats, selState) { cat ->
        val streams = rememberLoad("live", cat.id, if (cat.id == FAV) favs else null) {
            when (cat.id) {
                ALL -> client.allLive()
                FAV -> favs
                else -> client.liveStreams(cat.id)
            }
        }
        LoadView(streams) { chans ->
            if (chans.isEmpty()) {
                Empty(if (cat.id == FAV) "Mbaj shtypur OK mbi një kanal për ta shtuar te të preferuarat." else "Nuk ka kanale")
            } else {
                LazyVerticalGrid(
                    GridCells.Fixed(4), Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 8.dp, end = 28.dp, top = 24.dp, bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    itemsIndexed(chans) { i, ch ->
                        ChannelCard(i + 1, ch, epg = { client.epgNow(ch.id) }, onClick = { playLive(ctx, client, chans, i) })
                    }
                }
            }
        }
    }
}

@Composable
fun PosterGrid(items: List<PosterItem>, onClick: (PosterItem) -> Unit) {
    if (items.isEmpty()) Empty() else
        LazyVerticalGrid(
            GridCells.Fixed(6), Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 8.dp, end = 28.dp, top = 24.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            itemsIndexed(items) { _, p -> PosterCard(p, onClick = { onClick(p) }) }
        }
}

@Composable
fun MoviesTab(client: XtreamClient, selState: MutableState<Category?>, onOpen: (OpenItem) -> Unit) {
    val cats = rememberLoad("vod-cats") { client.categories("vod") }
    BrowseTab(remember { listOf(Category(ALL, "All")) }, cats, selState) { cat ->
        val movies = rememberLoad("vod", cat.id) { if (cat.id == ALL) client.allVod() else client.vod(cat.id) }
        LoadView(movies) { list ->
            val posters = remember(list) { list.map { PosterItem(it.id, it.name, it.icon, null, it.rating, it.ext) } }
            PosterGrid(posters) { p -> onOpen(OpenItem(p, posters)) }
        }
    }
}

@Composable
fun SeriesTab(client: XtreamClient, selState: MutableState<Category?>, onOpen: (OpenItem) -> Unit) {
    val cats = rememberLoad("series-cats") { client.categories("series") }
    BrowseTab(remember { listOf(Category(ALL, "All")) }, cats, selState) { cat ->
        val series = rememberLoad("series", cat.id) { if (cat.id == ALL) client.allSeries() else client.series(cat.id) }
        LoadView(series) { list ->
            val posters = remember(list) { list.map { PosterItem(it.id, it.name, it.cover, it.plot) } }
            PosterGrid(posters) { p -> onOpen(OpenItem(p, posters)) }
        }
    }
}

// ================= DETAJET (FILM / SERIAL) =================

@Composable
private fun DetailBackdrop(image: String?) {
    Box(Modifier.fillMaxSize()) {
        AsyncImage(
            image, null,
            Modifier.align(Alignment.TopEnd).fillMaxWidth(0.65f).fillMaxHeight(0.7f).alpha(0.6f),
            contentScale = ContentScale.Crop,
        )
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0.3f to Bg, 0.75f to Color.Transparent)))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.3f to Color.Transparent, 0.7f to Bg)))
    }
}

/** Posteri, titulli, të dhënat, butonat dhe përshkrimi */
@Composable
private fun DetailHeader(
    p: PosterItem,
    inf: VodInfo?,
    loading: Boolean,
    favType: String,
    playLabel: String,
    playFocus: FocusRequester,
    onPlay: () -> Unit,
) {
    val fav = Library.isFav(favType, p.id)
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            AsyncImage(
                p.image, p.title,
                Modifier.width(150.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(10.dp)),
                contentScale = ContentScale.Crop,
            )
            Spacer(Modifier.width(28.dp))
            Column(Modifier.fillMaxWidth(0.6f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(p.title, fontSize = 40.sp, fontWeight = FontWeight.Bold, lineHeight = 46.sp,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                val meta = listOfNotNull(
                    inf?.year, inf?.duration, (inf?.rating ?: p.rating)?.let { "★ $it" }, inf?.genre,
                ).joinToString("   ·   ")
                if (meta.isNotBlank()) Text(meta, color = TextDim, fontSize = 16.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ActionButton(playLabel, Accent, Modifier.focusRequester(playFocus).width(200.dp), onClick = onPlay)
                    ActionButton(if (fav) "♥  Në të preferuarat" else "♡  Të preferuarat", Color.Transparent) {
                        Library.toggleFav(SavedItem(favType, p.id, p.title, p.image, p.ext))
                    }
                }
            }
        }
        Column(Modifier.fillMaxWidth(0.7f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Përshkrimi", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            Text(
                if (loading) "Duke ngarkuar…" else inf?.plot ?: p.info ?: "Nuk ka përshkrim.",
                fontSize = 17.sp, lineHeight = 26.sp, color = Color(0xFFD6D6DA),
            )
            inf?.director?.let { Text("Regjia: $it", fontSize = 15.sp, color = TextDim) }
            inf?.cast?.let { Text("Aktorët: $it", fontSize = 15.sp, color = TextDim, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        }
    }
}

@Composable
fun MovieDetail(client: XtreamClient, m: OpenItem, onBack: () -> Unit, onOpen: (OpenItem) -> Unit) {
    val ctx = LocalContext.current
    BackHandler(onBack = onBack)
    val p = m.item
    val info = rememberLoad("vinfo", p.id) { client.vodInfo(p.id) }
    val inf = (info as? Load.Ok)?.value
    val playFocus = remember { FocusRequester() }
    LaunchedEffect(p.id) { delay(120); runCatching { playFocus.requestFocus() } }
    val similar = remember(m) { m.similar.filter { it.id != p.id }.take(30) }

    Box(Modifier.fillMaxSize()) {
        DetailBackdrop(inf?.backdrop ?: p.image)
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 40.dp, end = 24.dp, top = 56.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item {
                DetailHeader(p, inf, info == Load.Loading, Library.MOVIE, "▶   Shiko", playFocus) {
                    playMovie(ctx, client, p)
                }
            }
            if (similar.isNotEmpty()) {
                item {
                    RowTitle("Më shumë si kjo")
                    PosterRow(similar) { onOpen(OpenItem(it, m.similar)) }
                }
            }
        }
    }
}

@Composable
fun SeriesDetail(client: XtreamClient, m: OpenItem, onBack: () -> Unit, onOpen: (OpenItem) -> Unit) {
    val ctx = LocalContext.current
    BackHandler(onBack = onBack)
    val s = m.item
    val full = rememberLoad("sfull", s.id) { client.seriesFull(s.id) }
    val data = (full as? Load.Ok)?.value
    val playFocus = remember { FocusRequester() }
    LaunchedEffect(s.id) { delay(120); runCatching { playFocus.requestFocus() } }
    val similar = remember(m) { m.similar.filter { it.id != s.id }.take(30) }

    val eps = data?.episodes.orEmpty()
    val seasons = remember(eps) { eps.map { it.season }.distinct() }
    var season by remember(eps) { mutableStateOf(seasons.firstOrNull() ?: 1) }
    val shown = eps.filter { it.season == season }

    fun playEp(i: Int) {
        if (shown.isEmpty()) return
        Library.addHistory(SavedItem(Library.SERIES, s.id, s.title, s.image))
        Playback.open(
            ctx, client,
            shown.map { PlayItem("${s.title} · S${it.season} E${it.num} ${it.title}", client.episodeUrl(it.id, it.ext)) },
            i, live = false,
        )
    }

    Box(Modifier.fillMaxSize()) {
        DetailBackdrop(data?.info?.backdrop ?: s.image)
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 40.dp, end = 24.dp, top = 56.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item {
                DetailHeader(s, data?.info, full == Load.Loading, Library.SERIES, "▶   Shiko S$season E1", playFocus) {
                    playEp(0)
                }
            }
            item {
                when {
                    full is Load.Err -> Text("⚠ ${full.msg}", color = Color(0xFFFF6B6B))
                    data == null -> Unit
                    eps.isEmpty() -> Empty("Nuk ka episode")
                    else -> Column {
                        val tabs = remember(seasons) { seasons.map { Category(it.toString(), "Sezoni $it") } }
                        CategoryTabs(tabs, season.toString()) { season = it.id.toInt() }
                        Text("Episodet  (${shown.size})", Modifier.padding(start = 8.dp, top = 12.dp, bottom = 8.dp),
                            fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(8.dp)) {
                            itemsIndexed(shown) { i, e ->
                                TvCard(Modifier.width(260.dp).aspectRatio(16f / 9f), onClick = { playEp(i) }) {
                                    AsyncImage(e.image ?: s.image, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                    Box(
                                        Modifier.fillMaxSize().background(
                                            Brush.verticalGradient(0.4f to Color.Transparent, 1f to Color.Black.copy(alpha = .9f))
                                        )
                                    )
                                    Column(Modifier.align(Alignment.BottomStart).padding(10.dp)) {
                                        Text("Episodi ${e.num}", fontSize = 12.sp, color = Accent, fontWeight = FontWeight.Bold)
                                        Text(e.title, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (similar.isNotEmpty()) {
                item {
                    RowTitle("Më shumë si kjo")
                    PosterRow(similar) { onOpen(OpenItem(it, m.similar)) }
                }
            }
        }
    }
}

// ================= HISTORIKU / TË PREFERUARAT =================

@Composable
fun SavedTab(
    client: XtreamClient,
    title: String,
    items: List<SavedItem>,
    emptyText: String,
    onOpenSeries: (OpenItem) -> Unit,
    onOpenMovie: (OpenItem) -> Unit,
) {
    val ctx = LocalContext.current
    val live = items.filter { it.type == Library.LIVE }.map { it.toLive() }
    val movies = items.filter { it.type == Library.MOVIE }.map { it.toPoster() }
    val series = items.filter { it.type == Library.SERIES }.map { it.toPoster() }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 32.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        item { Text(title, Modifier.padding(start = 8.dp), fontSize = 30.sp, fontWeight = FontWeight.Bold) }
        if (items.isEmpty()) item { Empty(emptyText) }
        if (live.isNotEmpty()) item {
            RowTitle("Kanalet")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(8.dp)) {
                itemsIndexed(live) { i, ch ->
                    ChannelCard(i + 1, ch, Modifier.width(210.dp), epg = { client.epgNow(ch.id) },
                        onClick = { playLive(ctx, client, live, i) })
                }
            }
        }
        if (movies.isNotEmpty()) item {
            RowTitle("Filma")
            PosterRow(movies) { onOpenMovie(OpenItem(it, movies)) }
        }
        if (series.isNotEmpty()) item {
            RowTitle("Seriale")
            PosterRow(series) { onOpenSeries(OpenItem(it, series)) }
        }
    }
}

// ================= KËRKIMI =================

@Composable
fun SearchTab(client: XtreamClient, onOpenSeries: (OpenItem) -> Unit, onOpenMovie: (OpenItem) -> Unit) {
    val ctx = LocalContext.current
    var q by rememberSaveable { mutableStateOf("") }
    val live = rememberLoad("s-live") { client.allLive() }
    val vod = rememberLoad("s-vod") { client.allVod() }
    val ser = rememberLoad("s-series") { client.allSeries() }
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { fr.requestFocus() } }
    val query = q.trim()

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 28.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item {
            OutlinedTextField(
                q, { q = it }, Modifier.fillMaxWidth(0.6f).focusRequester(fr),
                placeholder = { Text("Kërko kanale, filma, seriale…") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                singleLine = true,
            )
        }
        if (query.length >= 2) {
            item {
                RowTitle("Kanale")
                LoadView(live, Modifier.fillMaxWidth().height(140.dp)) { all ->
                    val res = remember(query, all) { all.filter { it.name.contains(query, true) }.take(40) }
                    if (res.isEmpty()) Empty("Asnjë kanal") else
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(8.dp)) {
                            itemsIndexed(res) { i, ch ->
                                ChannelCard(all.indexOf(ch) + 1, ch, Modifier.width(210.dp), epg = { client.epgNow(ch.id) },
                                    onClick = { playLive(ctx, client, res, i) })
                            }
                        }
                }
            }
            item {
                RowTitle("Filma")
                LoadView(vod, Modifier.fillMaxWidth().height(240.dp)) { all ->
                    val res = remember(query, all) { all.filter { it.name.contains(query, true) }.take(40) }
                    val posters = res.map { PosterItem(it.id, it.name, it.icon, null, it.rating, it.ext) }
                    PosterRow(posters) { p -> onOpenMovie(OpenItem(p, posters)) }
                }
            }
            item {
                RowTitle("Seriale")
                LoadView(ser, Modifier.fillMaxWidth().height(240.dp)) { all ->
                    val res = remember(query, all) { all.filter { it.name.contains(query, true) }.take(40) }
                    val posters = res.map { PosterItem(it.id, it.name, it.cover, it.plot) }
                    PosterRow(posters) { p -> onOpenSeries(OpenItem(p, posters)) }
                }
            }
        }
    }
}

// ================= LLOGARIA =================

@Composable
fun AccountTab(client: XtreamClient, onLogout: () -> Unit) {
    val info = rememberLoad("account") { client.account ?: client.login() }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            Modifier.width(560.dp).clip(RoundedCornerShape(16.dp)).background(CardBg).padding(36.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Llogaria", fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Text("Username: ${client.user}", fontSize = 18.sp)
            LoadView(info, Modifier.fillMaxWidth().height(90.dp)) { a ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Statusi: ${a.status.ifBlank { "Active" }}", color = Color(0xFF4ADE80), fontSize = 18.sp)
                    val exp = a.expDate?.let {
                        SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date(it * 1000))
                    } ?: "Pa afat"
                    Text("Skadon: $exp", fontSize = 18.sp)
                    Text("Lidhje: ${a.activeConnections} / ${a.maxConnections}", fontSize = 18.sp)
                }
            }
            Text(
                "Të preferuarat: ${Library.favorites.size}   ·   Historiku: ${Library.history.size}",
                color = TextDim, fontSize = 15.sp,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ActionButton("Rifresko listat", Color.Transparent) { client.clearCache() }
                ActionButton("Fshi historikun", Color.Transparent) { Library.clearHistory() }
                ActionButton("Dil", Accent, onClick = onLogout)
            }
        }
    }
}

@Composable
fun ActionButton(text: String, color: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    TvCard(
        modifier,
        bg = if (color.alpha == 0f) Color.White.copy(alpha = .1f) else color,
        shape = RoundedCornerShape(8.dp), scaleOnFocus = 1.05f, onClick = onClick,
    ) {
        Text(
            text, Modifier.align(Alignment.Center).padding(horizontal = 22.dp, vertical = 12.dp),
            fontWeight = FontWeight.SemiBold, maxLines = 1,
        )
    }
}
