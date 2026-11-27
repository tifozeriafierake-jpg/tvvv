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
    HOME("Kryefaqja", Icons.Filled.Home),
    LIVE("Live TV", Icons.Filled.PlayArrow),
    MOVIES("Filma", Icons.Filled.Star),
    SERIES("Seriale", Icons.AutoMirrored.Filled.List),
    HISTORY("Historiku", Icons.Filled.DateRange),
    FAVORITES("Të preferuarat", Icons.Filled.Favorite),
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
    var section by rememberSaveable { mutableStateOf(Section.LIVE) }
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
            Modifier.width(72.dp).fillMaxHeight().padding(vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))
            Section.entries.filter { it != Section.ACCOUNT }.forEach { s ->
                val sel = s == section && !detailOpen
                TvCard(
                    Modifier.padding(vertical = 4.dp).size(48.dp),
                    shape = CircleShape,
                    bg = Color.Transparent,
                    focusBg = Color.White.copy(alpha = .15f),
                    focusBorder = false,
                    onFocus = { go(s) },
                    onClick = { go(s) },
                ) { f ->
                    Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(s.icon, s.label, Modifier.size(24.dp), tint = if (sel || f) Color.White else TextDim)
                        Box(
                            Modifier.padding(top = 4.dp).height(2.dp).width(18.dp)
                                .background(if (sel) Purple else Color.Transparent)
                        )
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            TvCard(
                Modifier.size(40.dp), shape = CircleShape, bg = Color(0xFFF2C14E),
                onFocus = { go(Section.ACCOUNT) }, onClick = { go(Section.ACCOUNT) },
            ) {
                Text(
                    client.user.take(1).uppercase(), Modifier.align(Alignment.Center),
                    color = Color(0xFF5A3E00), fontWeight = FontWeight.Bold, fontSize = 16.sp,
                )
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

// ================= KRYEFAQJA =================

data class HeroData(val title: String, val time: String?, val desc: String?, val image: String?, val isLogo: Boolean)

@Composable
fun HomeTab(client: XtreamClient, onOpenSeries: (OpenItem) -> Unit, onOpenMovie: (OpenItem) -> Unit) {
    val ctx = LocalContext.current
    val chans = rememberLoad("home-live") { client.allLive() }
    val movies = rememberLoad("home-vod") {
        client.categories("vod").firstOrNull()?.let { c -> client.vod(c.id).take(30) } ?: emptyList()
    }
    val series = rememberLoad("home-series") {
        client.categories("series").firstOrNull()?.let { c -> client.series(c.id).take(30) } ?: emptyList()
    }
    var focusCh by remember { mutableStateOf<LiveStream?>(null) }
    var hero by remember { mutableStateOf<HeroData?>(null) }
    val firstFocus = remember { FocusRequester() }

    LaunchedEffect(focusCh) {
        val ch = focusCh ?: return@LaunchedEffect
        hero = HeroData(ch.name, null, null, ch.icon, true)
        delay(350)
        val e = runCatching { client.epgNow(ch.id) }.getOrNull() ?: return@LaunchedEffect
        hero = HeroData(
            ch.name,
            if (e.start > 0) "${hm(e.start)} - ${hm(e.end)}" else null,
            listOf(e.title, e.desc).filter { it.isNotBlank() }.joinToString("\n"),
            ch.icon, true,
        )
    }

    Column(Modifier.fillMaxSize()) {
        HeroBanner(hero, Modifier.fillMaxWidth().weight(0.48f))
        LazyColumn(
            Modifier.fillMaxWidth().weight(0.52f),
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 32.dp, top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            item {
                RowTitle("Kanalet Live")
                LoadView(chans, Modifier.fillMaxWidth().height(140.dp)) { all ->
                    val list = all.take(60)
                    LaunchedEffect(list) {
                        if (list.isNotEmpty() && focusCh == null) {
                            focusCh = list.first()
                            delay(150)
                            runCatching { firstFocus.requestFocus() }
                        }
                    }
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        contentPadding = PaddingValues(8.dp),
                    ) {
                        itemsIndexed(list) { i, ch ->
                            ChannelCard(
                                i + 1, ch,
                                Modifier.width(210.dp).let { if (i == 0) it.focusRequester(firstFocus) else it },
                                epg = { client.epgNow(ch.id) },
                                onFocus = { focusCh = ch },
                                onClick = { playLive(ctx, client, list, i) },
                            )
                        }
                    }
                }
            }
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
                RowTitle("Filma")
                LoadView(movies, Modifier.fillMaxWidth().height(240.dp)) { list ->
                    val posters = remember(list) { list.map { PosterItem(it.id, it.name, it.icon, null, it.rating, it.ext) } }
                    PosterRow(posters,
                        onFocus = { p -> focusCh = null; hero = HeroData(p.title, p.rating?.let { "★ $it" }, null, p.image, false) },
                        onClick = { p -> onOpenMovie(OpenItem(p, posters)) })
                }
            }
            item {
                RowTitle("Seriale")
                LoadView(series, Modifier.fillMaxWidth().height(240.dp)) { list ->
                    val posters = remember(list) { list.map { PosterItem(it.id, it.name, it.cover, it.plot) } }
                    PosterRow(posters,
                        onFocus = { p -> focusCh = null; hero = HeroData(p.title, null, p.info, p.image, false) },
                        onClick = { p -> onOpenSeries(OpenItem(p, posters)) })
                }
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

@Composable
fun HeroBanner(hero: HeroData?, modifier: Modifier) {
    Box(modifier) {
        val img = hero?.image
        if (hero != null && img != null) {
            if (hero.isLogo) {
                AsyncImage(
                    img, null,
                    Modifier.align(Alignment.CenterEnd).padding(end = 90.dp, top = 40.dp)
                        .fillMaxHeight(0.6f).aspectRatio(1.6f),
                    contentScale = ContentScale.Fit,
                )
            } else {
                AsyncImage(
                    img, null,
                    Modifier.align(Alignment.CenterEnd).fillMaxHeight().fillMaxWidth(0.6f).alpha(0.55f),
                    contentScale = ContentScale.Crop,
                )
                Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0.35f to Bg, 0.75f to Color.Transparent)))
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.6f to Color.Transparent, 1f to Bg.copy(alpha = .9f))))
            }
        }
        Row(Modifier.align(Alignment.TopEnd).padding(28.dp)) {
            Text("8K", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Black)
            Text("tv", color = Accent, fontSize = 34.sp, fontWeight = FontWeight.Black)
        }
        Column(
            Modifier.align(Alignment.CenterStart).padding(start = 36.dp, top = 30.dp).fillMaxWidth(0.55f),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                hero?.title ?: "", fontSize = 46.sp, fontWeight = FontWeight.Bold,
                maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 52.sp,
            )
            hero?.time?.let { Text(it, color = TextDim, fontSize = 16.sp) }
            hero?.desc?.takeIf { it.isNotBlank() }?.let {
                Text(it, fontSize = 18.sp, lineHeight = 26.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

// ================= LIVE / FILMA / SERIALE =================

@Composable
fun BrowseTab(
    cats: Load<List<Category>>,
    selState: MutableState<Category?>,
    content: @Composable (Category) -> Unit,
) {
    var sel by selState
    LoadView(cats) { list ->
        if (list.isEmpty()) Empty() else {
            LaunchedEffect(list) { if (sel == null || list.none { it.id == sel?.id }) sel = list.first() }
            Row(Modifier.fillMaxSize()) {
                CategoryColumn(list, sel?.id) { sel = it }
                Box(Modifier.weight(1f).fillMaxHeight()) { sel?.let { content(it) } }
            }
        }
    }
}

@Composable
fun LiveTab(client: XtreamClient, selState: MutableState<Category?>) {
    val ctx = LocalContext.current
    val cats = rememberLoad("live-cats") {
        listOf(Category(ALL, "All"), Category(FAV, "♥ Të preferuarat")) + client.categories("live")
    }
    val favs = Library.favorites.filter { it.type == Library.LIVE }.map { it.toLive() }
    BrowseTab(cats, selState) { cat ->
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
    val cats = rememberLoad("vod-cats") { listOf(Category(ALL, "All")) + client.categories("vod") }
    BrowseTab(cats, selState) { cat ->
        val movies = rememberLoad("vod", cat.id) { if (cat.id == ALL) client.allVod() else client.vod(cat.id) }
        LoadView(movies) { list ->
            val posters = remember(list) { list.map { PosterItem(it.id, it.name, it.icon, null, it.rating, it.ext) } }
            PosterGrid(posters) { p -> onOpen(OpenItem(p, posters)) }
        }
    }
}

@Composable
fun SeriesTab(client: XtreamClient, selState: MutableState<Category?>, onOpen: (OpenItem) -> Unit) {
    val cats = rememberLoad("series-cats") { listOf(Category(ALL, "All")) + client.categories("series") }
    BrowseTab(cats, selState) { cat ->
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
