package com.eightk.tv.ui

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
import com.eightk.tv.data.LiveStream
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
    ACCOUNT("Llogaria", Icons.Filled.Person),
}

private const val ALL = "__all"

fun playLive(ctx: android.content.Context, client: XtreamClient, list: List<LiveStream>, i: Int) =
    Playback.open(
        ctx, client,
        list.map { PlayItem(it.name, client.liveUrl(it.id), it.id, it.icon) }, i, live = true,
    )

@Composable
fun HomeScreen(client: XtreamClient, onLogout: () -> Unit, onClientChanged: (XtreamClient) -> Unit) {
    var section by rememberSaveable { mutableStateOf(Section.HOME) }
    var openSeries by remember { mutableStateOf<PosterItem?>(null) }
    val liveSel = remember { mutableStateOf<Category?>(null) }
    val vodSel = remember { mutableStateOf<Category?>(null) }
    val seriesSel = remember { mutableStateOf<Category?>(null) }

    // Kontrollon llogarinë; nëse serveri i ruajtur nuk punon, kalon te një server tjetër
    LaunchedEffect(client) {
        if (client.account == null) {
            try {
                client.login()
            } catch (e: AuthException) {
                onLogout()
            } catch (e: Exception) {
                runCatching { loginAny(client.user, client.pass) }.getOrNull()
                    ?.takeIf { it.server != client.server }
                    ?.let(onClientChanged)
            }
        }
    }

    Row(Modifier.fillMaxSize()) {
        // ---- Shiriti majtas me ikona ----
        Column(
            Modifier.width(88.dp).fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Section.entries.forEach { s ->
                val sel = s == section && openSeries == null
                TvCard(
                    Modifier.size(52.dp),
                    shape = CircleShape,
                    bg = Color.Transparent,
                    focusBg = Color.White.copy(alpha = .15f),
                    onFocus = { section = s; openSeries = null },
                    onClick = { section = s; openSeries = null },
                ) { f ->
                    Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(s.icon, s.label, Modifier.size(26.dp), tint = if (sel || f) Color.White else TextDim)
                        Box(
                            Modifier.padding(top = 4.dp).height(2.dp).width(22.dp)
                                .background(if (sel) Purple else Color.Transparent)
                        )
                    }
                }
            }
        }

        Box(Modifier.weight(1f).fillMaxHeight()) {
            val series = openSeries
            if (series != null) {
                SeriesDetail(client, series, onBack = { openSeries = null })
            } else when (section) {
                Section.SEARCH -> SearchTab(client) { openSeries = it }
                Section.HOME -> HomeTab(client) { openSeries = it }
                Section.LIVE -> LiveTab(client, liveSel)
                Section.MOVIES -> MoviesTab(client, vodSel)
                Section.SERIES -> SeriesTab(client, seriesSel) { openSeries = it }
                Section.ACCOUNT -> AccountTab(client, onLogout)
            }
        }
    }
}

// ================= KRYEFAQJA =================

data class HeroData(val title: String, val time: String?, val desc: String?, val image: String?, val isLogo: Boolean)

private fun hm(sec: Long) = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(sec * 1000))

@Composable
fun HomeTab(client: XtreamClient, onOpenSeries: (PosterItem) -> Unit) {
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
                                Modifier.width(220.dp).let { if (i == 0) it.focusRequester(firstFocus) else it },
                                onFocus = { focusCh = ch },
                                onClick = { playLive(ctx, client, list, i) },
                            )
                        }
                    }
                }
            }
            item {
                RowTitle("Filma")
                LoadView(movies, Modifier.fillMaxWidth().height(240.dp)) { list ->
                    PosterRow(list.map { PosterItem(it.id, it.name, it.icon, null, it.rating) },
                        onFocus = { p -> focusCh = null; hero = HeroData(p.title, p.rating?.let { "★ $it" }, null, p.image, false) },
                        onClick = { p ->
                            list.firstOrNull { it.id == p.id }?.let { v ->
                                Playback.open(ctx, client, listOf(PlayItem(v.name, client.vodUrl(v.id, v.ext))), 0, live = false)
                            }
                        })
                }
            }
            item {
                RowTitle("Seriale")
                LoadView(series, Modifier.fillMaxWidth().height(240.dp)) { list ->
                    PosterRow(list.map { PosterItem(it.id, it.name, it.cover, it.plot) },
                        onFocus = { p -> focusCh = null; hero = HeroData(p.title, null, p.info, p.image, false) },
                        onClick = onOpenSeries)
                }
            }
        }
    }
}

@Composable
fun PosterRow(items: List<PosterItem>, onFocus: (PosterItem) -> Unit = {}, onClick: (PosterItem) -> Unit) {
    if (items.isEmpty()) { Empty(); return }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(8.dp)) {
        itemsIndexed(items) { _, p ->
            PosterCard(p, Modifier.width(150.dp), onFocus = { onFocus(p) }, onClick = { onClick(p) })
        }
    }
}

@Composable
fun HeroBanner(hero: HeroData?, modifier: Modifier) {
    Box(modifier) {
        if (hero?.image != null) {
            if (hero.isLogo) {
                AsyncImage(
                    hero.image, null,
                    Modifier.align(Alignment.CenterEnd).padding(end = 90.dp, top = 40.dp)
                        .fillMaxHeight(0.6f).aspectRatio(1.6f),
                    contentScale = ContentScale.Fit,
                )
            } else {
                AsyncImage(
                    hero.image, null,
                    Modifier.align(Alignment.CenterEnd).fillMaxHeight().fillMaxWidth(0.6f).alpha(0.55f),
                    contentScale = ContentScale.Crop,
                )
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.horizontalGradient(0.35f to Bg, 0.75f to Color.Transparent)
                    )
                )
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(0.6f to Color.Transparent, 1f to Bg.copy(alpha = .9f))
                    )
                )
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
    title: String,
    cats: Load<List<Category>>,
    selState: MutableState<Category?>,
    content: @Composable (Category) -> Unit,
) {
    var sel by selState
    Column(Modifier.fillMaxSize().padding(top = 28.dp, start = 12.dp, end = 24.dp)) {
        Text(title, Modifier.padding(start = 8.dp, bottom = 8.dp), fontSize = 30.sp, fontWeight = FontWeight.Bold)
        LoadView(cats) { list ->
            if (list.isEmpty()) Empty() else {
                LaunchedEffect(list) { if (sel == null || list.none { it.id == sel?.id }) sel = list.first() }
                CategoryTabs(list, sel?.id) { sel = it }
                Spacer(Modifier.height(10.dp))
                Box(Modifier.fillMaxSize()) { sel?.let { content(it) } }
            }
        }
    }
}

@Composable
fun LiveTab(client: XtreamClient, selState: MutableState<Category?>) {
    val ctx = LocalContext.current
    val cats = rememberLoad("live-cats") { listOf(Category(ALL, "Të gjitha")) + client.categories("live") }
    BrowseTab("Kanalet Live", cats, selState) { cat ->
        val streams = rememberLoad("live", cat.id) {
            if (cat.id == ALL) client.allLive() else client.liveStreams(cat.id)
        }
        LoadView(streams) { chans ->
            if (chans.isEmpty()) Empty() else
                LazyVerticalGrid(
                    GridCells.Fixed(6), Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    itemsIndexed(chans) { i, ch ->
                        ChannelCard(i + 1, ch, onClick = { playLive(ctx, client, chans, i) })
                    }
                }
        }
    }
}

@Composable
fun PosterGrid(items: List<PosterItem>, onClick: (PosterItem) -> Unit) {
    if (items.isEmpty()) Empty() else
        LazyVerticalGrid(
            GridCells.Fixed(7), Modifier.fillMaxSize(),
            contentPadding = PaddingValues(10.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            itemsIndexed(items) { _, p -> PosterCard(p, onClick = { onClick(p) }) }
        }
}

@Composable
fun MoviesTab(client: XtreamClient, selState: MutableState<Category?>) {
    val ctx = LocalContext.current
    val cats = rememberLoad("vod-cats") { client.categories("vod") }
    BrowseTab("Filma", cats, selState) { cat ->
        val movies = rememberLoad("vod", cat.id) { client.vod(cat.id) }
        LoadView(movies) { list ->
            PosterGrid(list.map { PosterItem(it.id, it.name, it.icon, null, it.rating) }) { p ->
                list.firstOrNull { it.id == p.id }?.let { v ->
                    Playback.open(ctx, client, listOf(PlayItem(v.name, client.vodUrl(v.id, v.ext))), 0, live = false)
                }
            }
        }
    }
}

@Composable
fun SeriesTab(client: XtreamClient, selState: MutableState<Category?>, onOpen: (PosterItem) -> Unit) {
    val cats = rememberLoad("series-cats") { client.categories("series") }
    BrowseTab("Seriale", cats, selState) { cat ->
        val series = rememberLoad("series", cat.id) { client.series(cat.id) }
        LoadView(series) { list ->
            PosterGrid(list.map { PosterItem(it.id, it.name, it.cover, it.plot) }, onOpen)
        }
    }
}

@Composable
fun SeriesDetail(client: XtreamClient, s: PosterItem, onBack: () -> Unit) {
    val ctx = LocalContext.current
    BackHandler(onBack = onBack)
    val eps = rememberLoad("eps", s.id) { client.episodes(s.id) }
    val firstFocus = remember { FocusRequester() }

    Box(Modifier.fillMaxSize()) {
        AsyncImage(
            s.image, null,
            Modifier.align(Alignment.TopEnd).fillMaxWidth(0.55f).fillMaxHeight(0.6f).alpha(0.5f),
            contentScale = ContentScale.Crop,
        )
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0.4f to Bg, 0.8f to Color.Transparent)))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.35f to Color.Transparent, 0.6f to Bg)))

        Column(Modifier.fillMaxSize().padding(start = 36.dp, top = 40.dp, end = 24.dp)) {
            Text(s.title, fontSize = 42.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            s.info?.let {
                Text(
                    it, Modifier.fillMaxWidth(0.55f).padding(top = 10.dp), fontSize = 17.sp,
                    lineHeight = 25.sp, maxLines = 4, overflow = TextOverflow.Ellipsis, color = TextDim,
                )
            }
            Spacer(Modifier.height(24.dp))
            LoadView(eps) { list ->
                if (list.isEmpty()) Empty("Nuk ka episode") else {
                    val seasons = remember(list) { list.map { it.season }.distinct() }
                    var season by remember(list) { mutableStateOf(seasons.first()) }
                    LaunchedEffect(list) { delay(150); runCatching { firstFocus.requestFocus() } }
                    val tabs = remember(seasons) { seasons.map { Category(it.toString(), "Sezoni $it") } }
                    CategoryTabs(tabs, season.toString()) { season = it.id.toInt() }
                    Spacer(Modifier.height(12.dp))
                    val shown = list.filter { it.season == season }
                    LazyVerticalGrid(
                        GridCells.Fixed(4), Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        itemsIndexed(shown) { i, e ->
                            TvCard(
                                (if (i == 0) Modifier.focusRequester(firstFocus) else Modifier).aspectRatio(16f / 9f),
                                onClick = {
                                    Playback.open(
                                        ctx, client,
                                        shown.map {
                                            PlayItem("${s.title} · S${it.season} E${it.num} ${it.title}", client.episodeUrl(it.id, it.ext))
                                        },
                                        i, live = false,
                                    )
                                },
                            ) {
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
    }
}

// ================= KËRKIMI =================

@Composable
fun SearchTab(client: XtreamClient, onOpenSeries: (PosterItem) -> Unit) {
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
                                ChannelCard(all.indexOf(ch) + 1, ch, Modifier.width(220.dp), onClick = { playLive(ctx, client, res, i) })
                            }
                        }
                }
            }
            item {
                RowTitle("Filma")
                LoadView(vod, Modifier.fillMaxWidth().height(240.dp)) { all ->
                    val res = remember(query, all) { all.filter { it.name.contains(query, true) }.take(40) }
                    PosterRow(res.map { PosterItem(it.id, it.name, it.icon) }) { p ->
                        res.firstOrNull { it.id == p.id }?.let { v ->
                            Playback.open(ctx, client, listOf(PlayItem(v.name, client.vodUrl(v.id, v.ext))), 0, live = false)
                        }
                    }
                }
            }
            item {
                RowTitle("Seriale")
                LoadView(ser, Modifier.fillMaxWidth().height(240.dp)) { all ->
                    val res = remember(query, all) { all.filter { it.name.contains(query, true) }.take(40) }
                    PosterRow(res.map { PosterItem(it.id, it.name, it.cover, it.plot) }, onClick = onOpenSeries)
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
            Modifier.width(520.dp).clip(RoundedCornerShape(16.dp)).background(CardBg).padding(36.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Llogaria", fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Text("Username: ${client.user}", fontSize = 18.sp)
            Text("Serveri: ${client.server}", color = TextDim)
            LoadView(info, Modifier.fillMaxWidth().height(90.dp)) { a ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Statusi: ${a.status}", color = Color(0xFF4ADE80), fontSize = 18.sp)
                    val exp = a.expDate?.let {
                        SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date(it * 1000))
                    } ?: "Pa afat"
                    Text("Skadon: $exp", fontSize = 18.sp)
                    Text("Lidhje: ${a.activeConnections} / ${a.maxConnections}", fontSize = 18.sp)
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ActionButton("Rifresko listat", CardBg.copy(alpha = 0f)) { client.clearCache() }
                ActionButton("Dil nga llogaria", Accent, onLogout)
            }
        }
    }
}

@Composable
fun ActionButton(text: String, color: Color, onClick: () -> Unit) {
    TvCard(
        bg = if (color.alpha == 0f) Color.White.copy(alpha = .1f) else color,
        shape = RoundedCornerShape(8.dp), scaleOnFocus = 1.05f, onClick = onClick,
    ) {
        Text(text, Modifier.padding(horizontal = 22.dp, vertical = 12.dp), fontWeight = FontWeight.SemiBold)
    }
}
