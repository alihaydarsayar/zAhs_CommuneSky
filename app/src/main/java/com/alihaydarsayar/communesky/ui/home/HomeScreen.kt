package com.alihaydarsayar.communesky.ui.home

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alihaydarsayar.communesky.R
import com.alihaydarsayar.communesky.model.SkyTheme
import com.alihaydarsayar.communesky.model.WeatherCondition
import com.alihaydarsayar.communesky.model.WeatherScene
import com.alihaydarsayar.communesky.model.WeatherSnapshot
import com.alihaydarsayar.communesky.ui.common.GlassCard
import com.alihaydarsayar.communesky.ui.common.GlassIconButton
import com.alihaydarsayar.communesky.ui.common.WeatherIcon
import com.alihaydarsayar.communesky.ui.common.asTemperature
import com.alihaydarsayar.communesky.ui.theme.TextPrimary
import com.alihaydarsayar.communesky.ui.theme.TextSecondary
import com.alihaydarsayar.communesky.ui.theme.TextTertiary
import java.time.LocalTime

private const val LocationPermission = Manifest.permission.ACCESS_COARSE_LOCATION

// Hassas konum da istenir ama zorunlu değil: kullanıcı "Yaklaşık"ı seçerse uygulama yine çalışır.
private val LocationPermissions = arrayOf(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION,
)

/** ViewModel'e bağlı ekran: durumu dinler, izin akışını yönetir ve çizilecek içeriğe aktarır. */
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onOpenPlaces: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = LocalActivity.current

    // Android'in standart izin penceresini açar ve sonucu ViewModel'e iletir.
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        val granted = results.values.any { it }
        // Kullanıcı iki kez reddederse Android pencereyi bir daha göstermez; bunu buradan anlarız.
        val canAskAgain = activity != null &&
            ActivityCompat.shouldShowRequestPermissionRationale(activity, LocationPermission)
        viewModel.onLocationPermissionResult(granted, canAskAgain)
    }

    // Uygulama açıldığında izin yoksa bir kez sor. rememberSaveable sayesinde ekran dönünce tekrar sormaz.
    var askedOnLaunch by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(context, LocationPermission) ==
            PackageManager.PERMISSION_GRANTED
        if (!askedOnLaunch && !granted) {
            askedOnLaunch = true
            permissionLauncher.launch(LocationPermissions)
        }
    }

    // Ekran açık kaldıkça istasyon ölçümü 5 dakikada bir tazelenir; uygulama arka plandayken durur.
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                delay(5 * 60_000L)
                viewModel.refreshObservations()
            }
        }
    }

    // Uygulamaya geri dönülünce: veri eskidiyse ya da izin Ayarlar'dan verildiyse yenile.
    LifecycleResumeEffect(viewModel) {
        viewModel.onResume()
        onPauseOrDispose { }
    }

    HomeContent(
        uiState = uiState,
        onRefresh = { viewModel.refresh(userInitiated = true) },
        onPageSelected = viewModel::onPageSelected,
        onOpenPlaces = onOpenPlaces,
        onOpenSettings = onOpenSettings,
        onLocationAction = { status ->
            when (status) {
                is LocationStatus.NoPermission ->
                    if (status.canAskAgain) {
                        permissionLauncher.launch(LocationPermissions)
                    } else {
                        context.startActivity(appSettingsIntent(context))
                    }
                LocationStatus.Unavailable -> viewModel.refresh(userInitiated = true)
                LocationStatus.Current -> Unit
            }
        },
    )
}

/** Telefon ayarlarında bu uygulamanın sayfasını açar (izinler oradan verilebilir). */
private fun appSettingsIntent(context: Context) = Intent(
    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
    Uri.fromParts("package", context.packageName, null),
)

/**
 * Sadece durumu çizen ekran: yerler arasında yatay kaydırılan sayfalar, üstte Yerler ve
 * Ayarlar düğmeleri, altta sayfa noktaları. Gökyüzü arkada, seçili yere göre çizilir (MainActivity).
 */
@Composable
fun HomeContent(
    uiState: HomeUiState,
    onRefresh: () -> Unit,
    onPageSelected: (Long) -> Unit,
    onOpenPlaces: () -> Unit,
    onOpenSettings: () -> Unit,
    onLocationAction: (LocationStatus) -> Unit,
) {
    val pages = uiState.pages
    if (pages.isEmpty()) {
        LoadingContent()
        return
    }
    // Açılışta en son bakılan yerden başla (seçili yer zaten önbellekten hazır).
    val initialPage = remember { pages.indexOfFirst { it.placeId == uiState.selectedPlaceId }.coerceAtLeast(0) }
    val pagerState = rememberPagerState(initialPage = initialPage) { pages.size }

    // Kaydırma bitince seçili yeri kaydet; gökyüzü de bu yere göre değişir.
    val currentPages by rememberUpdatedState(pages)
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { index ->
            currentPages.getOrNull(index)?.let { onPageSelected(it.placeId) }
        }
    }
    // Yerler yeniden sıralandıysa ya da Yerler ekranında bir yer seçildiyse o sayfaya geç.
    LaunchedEffect(pages.map { it.placeId }, uiState.selectedPlaceId) {
        val index = pages.indexOfFirst { it.placeId == uiState.selectedPlaceId }
        if (index >= 0 && index != pagerState.settledPage && !pagerState.isScrollInProgress) {
            pagerState.scrollToPage(index)
        }
    }

    Box(Modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            key = { pages[it].placeId },
            // Komşu sayfa önceden hazırlansın; kaydırırken takılma olmasın.
            beyondViewportPageCount = 1,
            modifier = Modifier.fillMaxSize(),
        ) { index ->
            PlacePage(
                page = pages[index],
                uiState = uiState,
                animateEntrance = index == initialPage,
                hasPageIndicator = pages.size > 1,
                onRefresh = onRefresh,
                onLocationAction = onLocationAction,
            )
        }
        TopButtons(onOpenPlaces, onOpenSettings)
        if (pages.size > 1) {
            PageIndicator(
                pages = pages,
                pagerState = pagerState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
                    .padding(bottom = 10.dp),
            )
        }
    }
}

@Composable
private fun TopButtons(onOpenPlaces: () -> Unit, onOpenSettings: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        GlassIconButton(R.drawable.ic_list, stringResource(R.string.open_places), onOpenPlaces, size = 40)
        GlassIconButton(R.drawable.ic_settings, stringResource(R.string.open_settings), onOpenSettings, size = 40)
    }
}

/** Alttaki sayfa noktaları. "Bulunduğum yer" küçük bir konum işaretiyle gösterilir. */
@Composable
private fun PageIndicator(pages: List<HomePage>, pagerState: PagerState, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.page_indicator, pagerState.currentPage + 1, pages.size)
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 7.dp)
            .semantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        pages.forEachIndexed { index, page ->
            val selected = index == pagerState.currentPage
            val color by animateColorAsState(
                if (selected) Color.White else Color.White.copy(alpha = 0.4f),
                label = "dot",
            )
            if (page.isDevice) {
                Icon(painterResource(R.drawable.ic_location), contentDescription = null, tint = color, modifier = Modifier.size(10.dp))
            } else {
                Box(
                    Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(color),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlacePage(
    page: HomePage,
    uiState: HomeUiState,
    animateEntrance: Boolean,
    hasPageIndicator: Boolean,
    onRefresh: () -> Unit,
    onLocationAction: (LocationStatus) -> Unit,
) {
    val weather = page.weather
    when {
        weather != null -> Box(Modifier.fillMaxSize()) {
            val pullState = rememberPullToRefreshState()
            val listState = rememberLazyListState()
            val isPulling = uiState.isRefreshing && uiState.isUserRefresh
            PullToRefreshBox(
                isRefreshing = isPulling,
                onRefresh = onRefresh,
                state = pullState,
                modifier = Modifier.fillMaxSize(),
                indicator = {
                    PullToRefreshDefaults.Indicator(
                        state = pullState,
                        isRefreshing = isPulling,
                        containerColor = Color.White,
                        color = Color(0xFF1B3A66),
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top)),
                    )
                },
            ) {
                ForecastList(
                    page = page,
                    weather = weather,
                    uiState = uiState,
                    listState = listState,
                    animateEntrance = animateEntrance,
                    extraBottomPadding = if (hasPageIndicator) 32.dp else 0.dp,
                    onLocationAction = onLocationAction,
                )
            }
            CompactTopBar(page.title(weather), weather, listState)
        }
        // Önbellekte veri yok ve yenileme başarısız: tam ekran hata.
        uiState.error != null && !uiState.isRefreshing -> ErrorContent(uiState.error, onRefresh)
        else -> LoadingContent()
    }
}

/** Kayıtlı yerde kullanıcının seçtiği ad; "Bulunduğum yer"de konumdan bulunan şehir adı. */
private fun HomePage.title(weather: WeatherSnapshot): String? = name ?: weather.city.name

@Composable
private fun ForecastList(
    page: HomePage,
    weather: WeatherSnapshot,
    uiState: HomeUiState,
    listState: LazyListState,
    animateEntrance: Boolean,
    extraBottomPadding: Dp,
    onLocationAction: (LocationStatus) -> Unit,
) {
    val forecast = weather.forecast
    val now = remember(weather) { forecast.localNow() }
    val today = remember(weather) { forecast.today(now) }
    val hours = remember(weather) { forecast.upcomingHours(now) }
    val days = remember(weather) { forecast.upcomingDays(now) }
    val status = when {
        uiState.isRefreshing -> HeaderStatus.Refreshing
        uiState.error != null -> HeaderStatus.Failed
        else -> HeaderStatus.Idle
    }

    // İçerik ilk geldiğinde hafifçe aşağıdan yukarı süzülerek belirir (sadece açılıştaki sayfa).
    val entrance = remember { Animatable(if (animateEntrance) 0f else 1f) }
    LaunchedEffect(Unit) { entrance.animateTo(1f, tween(700, easing = FastOutSlowInEasing)) }
    val density = LocalDensity.current

    val insets = WindowInsets.safeDrawing.asPaddingValues()
    // Küçük üst çubuk görünürken solma alanı onun altına kadar uzar; kartlar çubuğun arkasında karışmaz.
    val compactBarVisible by remember { derivedStateOf { listState.isCompactBarVisible() } }
    val fadeHeight by animateDpAsState(if (compactBarVisible) 84.dp else 52.dp, label = "topFade")
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
            .graphicsLayer {
                alpha = entrance.value
                translationY = (1f - entrance.value) * with(density) { 32.dp.toPx() }
                // Maske uygulayabilmek için içerik ayrı bir katmanda çizilir.
                compositingStrategy = CompositingStrategy.Offscreen
            }
            // Kaydırılan içerik durum çubuğunun ve üst düğmelerin altına girerken yumuşakça
            // kaybolsun; saat ve pil simgeleriyle üst üste binmesin.
            .drawWithContent {
                drawContent()
                val statusBar = insets.calculateTopPadding().toPx()
                drawRect(
                    brush = Brush.verticalGradient(
                        0f to Color.Transparent,
                        1f to Color.Black,
                        startY = statusBar * 0.6f,
                        endY = statusBar + fadeHeight.toPx(),
                    ),
                    blendMode = BlendMode.DstIn,
                )
            },
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = insets.calculateTopPadding() + 16.dp,
            bottom = insets.calculateBottomPadding() + 24.dp + extraBottomPadding,
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(key = "header") {
            CurrentHeader(
                weather = weather,
                title = page.title(weather),
                isHome = page.isHome,
                today = today,
                status = status,
                // Konum düğmesi sadece "Bulunduğum yer" sayfasında anlamlı.
                locationStatus = if (page.isDevice) uiState.locationStatus else LocationStatus.Current,
                onLocationAction = onLocationAction,
                // Kaydırırken başlık yavaşça kaybolur ve geride kalır (parallax).
                modifier = Modifier.graphicsLayer {
                    val offset = if (listState.firstVisibleItemIndex == 0) {
                        listState.firstVisibleItemScrollOffset.toFloat()
                    } else {
                        size.height
                    }
                    val progress = (offset / (size.height * 0.7f)).coerceIn(0f, 1f)
                    alpha = 1f - progress
                    translationY = offset * 0.45f
                },
            )
        }
        item(key = "hourly") { HourlyCard(forecast.current, hours) }
        item(key = "daily") { DailyCard(days, forecast.current.temperature) }
        item(key = "details") { DetailTiles(forecast.current, today, now) }
        item(key = "attribution") { Attribution() }
    }
}

/**
 * Başlık kaydırılıp gözden kaybolunca üstte beliren küçük çubuk: şehir, sıcaklık ve ikon.
 * Görünürlük derivedStateOf ile hesaplanır; sadece eşik aşıldığında yeniden çizim olur.
 */
@Composable
private fun CompactTopBar(title: String?, weather: WeatherSnapshot, listState: LazyListState) {
    val visible by remember {
        derivedStateOf { listState.isCompactBarVisible() }
    }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInVertically { -it / 2 },
        exit = fadeOut() + slideOutVertically { -it / 2 },
    ) {
        val current = weather.forecast.current
        Box(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
                // Yanlardaki Yerler ve Ayarlar düğmeleriyle çakışmasın.
                .padding(top = 4.dp, start = 64.dp, end = 64.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            GlassCard(contentPadding = PaddingValues(horizontal = 18.dp, vertical = 9.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title ?: stringResource(R.string.my_location),
                        color = TextPrimary,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.width(10.dp))
                    WeatherIcon(current.condition, isNight = !current.isDay, size = 22.dp, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.temperature_value, current.temperature.asTemperature()),
                        color = TextPrimary,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun Attribution() {
    // Open-Meteo verileri CC BY 4.0 lisanslı; kaynağı belirtmek gerekiyor.
    val uriHandler = LocalUriHandler.current
    Text(
        text = stringResource(R.string.data_attribution),
        color = TextTertiary,
        style = MaterialTheme.typography.labelMedium,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .clickable { uriHandler.openUri("https://open-meteo.com/") }
            .padding(vertical = 12.dp),
    )
}

@Composable
private fun LoadingContent() {
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.app_name),
            color = TextPrimary,
            style = MaterialTheme.typography.displaySmall,
        )
        Spacer(Modifier.height(20.dp))
        CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
        Spacer(Modifier.height(14.dp))
        Text(stringResource(R.string.loading_weather), color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ErrorContent(error: LoadError, onRetry: () -> Unit) {
    val message = when (error) {
        LoadError.Network -> stringResource(R.string.error_network)
        is LoadError.Server -> stringResource(R.string.error_server, error.code)
        LoadError.Unknown -> stringResource(R.string.error_unknown)
    }
    Column(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        WeatherIcon(WeatherCondition.Cloudy, isNight = false, size = 72.dp, contentDescription = null)
        Spacer(Modifier.height(16.dp))
        Text(
            text = message,
            color = TextPrimary,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(20.dp))
        Text(
            text = stringResource(R.string.retry),
            color = Color(0xFF1B3A66),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier
                .clip(CircleShape)
                .background(Color.White)
                .clickable(onClick = onRetry)
                .padding(horizontal = 24.dp, vertical = 12.dp),
        )
    }
}

/** Henüz hiç veri yokken telefonun saatine göre gündüz ya da gece gökyüzü. */
fun placeholderScene(): WeatherScene {
    val hour = LocalTime.now().hour
    val isDay = hour in 7..18
    return WeatherScene(
        theme = if (isDay) SkyTheme.ClearDay else SkyTheme.ClearNight,
        condition = WeatherCondition.Clear,
        isNight = !isDay,
    )
}

/** Büyük başlık kaydırılıp gözden kaybolduysa üstteki küçük çubuk gösterilir. */
private fun LazyListState.isCompactBarVisible(): Boolean =
    firstVisibleItemIndex > 0 || firstVisibleItemScrollOffset > 600
