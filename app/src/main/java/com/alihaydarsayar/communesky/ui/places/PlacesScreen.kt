package com.alihaydarsayar.communesky.ui.places

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alihaydarsayar.communesky.R
import com.alihaydarsayar.communesky.data.local.WeatherCacheEntity.Companion.DEVICE_PLACE_ID
import com.alihaydarsayar.communesky.model.PlaceSearchResult
import com.alihaydarsayar.communesky.model.SavedPlace
import com.alihaydarsayar.communesky.ui.common.GlassShape
import com.alihaydarsayar.communesky.ui.common.LocalSkyIsLight
import com.alihaydarsayar.communesky.ui.common.ScreenTopBar
import com.alihaydarsayar.communesky.ui.common.SectionTitle
import com.alihaydarsayar.communesky.ui.theme.TextPrimary
import com.alihaydarsayar.communesky.ui.theme.TextSecondary
import com.alihaydarsayar.communesky.ui.theme.TextTertiary
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

private const val LocationPermission = Manifest.permission.ACCESS_COARSE_LOCATION

/** Yerleri yönet: ara ve ekle, sil, sürükleyerek sırala, "Ev" olarak işaretle. */
@Composable
fun PlacesScreen(
    onBack: () -> Unit,
    onPlaceSelected: () -> Unit,
    viewModel: PlacesViewModel = hiltViewModel(),
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val searchState by viewModel.searchState.collectAsStateWithLifecycle()
    val places by viewModel.places.collectAsStateWithLifecycle()
    val deviceName by viewModel.deviceName.collectAsStateWithLifecycle()
    val hasPermission by viewModel.hasPermission.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = LocalActivity.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Arama, uygulamanın o anki dilinde yapılsın (sistem dilinden farklı seçilmiş olabilir).
    val locale = LocalConfiguration.current.locales[0]
    LaunchedEffect(locale) { viewModel.setLocale(locale) }
    LifecycleResumeEffect(viewModel) {
        viewModel.onResume()
        onPauseOrDispose { }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { viewModel.onResume() }
    val requestPermission = {
        val canAsk = activity != null &&
            (ActivityCompat.shouldShowRequestPermissionRationale(activity, LocationPermission) ||
                !hasAskedBefore(context))
        if (canAsk) {
            markAsked(context)
            permissionLauncher.launch(LocationPermission)
        } else {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
            )
        }
    }

    val addedMessage = stringResource(R.string.place_added, "%s")
    val removedMessage = stringResource(R.string.place_removed, "%s")
    val undoLabel = stringResource(R.string.undo)
    LaunchedEffect(viewModel) {
        viewModel.eventFlow.collect { event ->
            when (event) {
                is PlacesEvent.Added -> scope.launch {
                    snackbarHostState.currentSnackbarData?.dismiss()
                    snackbarHostState.showSnackbar(addedMessage.replace("%s", event.name))
                }
                is PlacesEvent.Removed -> scope.launch {
                    snackbarHostState.currentSnackbarData?.dismiss()
                    val result = snackbarHostState.showSnackbar(
                        message = removedMessage.replace("%s", event.place.name),
                        actionLabel = undoLabel,
                        duration = SnackbarDuration.Long,
                    )
                    if (result == SnackbarResult.ActionPerformed) viewModel.undoRemove(event.place)
                }
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .imePadding(),
        ) {
            ScreenTopBar(stringResource(R.string.places_title), onBack)
            SearchField(
                query = query,
                isLoading = searchState is SearchState.Loading,
                onQueryChange = viewModel::onQueryChange,
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
            if (searchState == SearchState.Idle) {
                SavedPlacesList(
                    places = places,
                    deviceName = deviceName,
                    hasPermission = hasPermission,
                    onRequestPermission = requestPermission,
                    onSelect = { id ->
                        viewModel.select(id)
                        onPlaceSelected()
                    },
                    onToggleHome = viewModel::toggleHome,
                    onRemove = viewModel::remove,
                    onReorder = viewModel::reorder,
                )
            } else {
                SearchResults(searchState, onAdd = viewModel::add)
            }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .imePadding()
                .padding(16.dp),
        ) { data ->
            Snackbar(
                snackbarData = data,
                shape = RoundedCornerShape(20.dp),
                containerColor = Color(0xFF14284A),
                contentColor = TextPrimary,
                actionColor = Color(0xFF9FD4FF),
            )
        }
    }
}

// Android, izin hiç sorulmamışken de "gerekçe gösterme" için false döner; bu yüzden sorulup
// sorulmadığını ayrıca hatırlıyoruz. Reddedilip "bir daha sorma" denmişse Ayarlar'a yönlendiririz.
private fun hasAskedBefore(context: android.content.Context): Boolean =
    context.getSharedPreferences("permissions", 0).getBoolean("location_asked", false)

private fun markAsked(context: android.content.Context) {
    context.getSharedPreferences("permissions", 0).edit().putBoolean("location_asked", true).apply()
}

@Composable
private fun SearchField(
    query: String,
    isLoading: Boolean,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    val isLight = LocalSkyIsLight.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(CircleShape)
            .background(if (isLight) Color(0xFF0A1B36).copy(alpha = 0.20f) else Color.White.copy(alpha = 0.10f))
            .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape)
            .padding(start = 16.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(R.drawable.ic_search), contentDescription = null, tint = TextSecondary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) {
                Text(
                    stringResource(R.string.search_places_hint),
                    color = TextTertiary,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = TextPrimary),
                cursorBrush = SolidColor(Color.White),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (isLoading) {
            CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.padding(10.dp).size(20.dp))
        }
        if (query.isNotEmpty()) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.Button) { onQueryChange("") },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(R.drawable.ic_close),
                    contentDescription = stringResource(R.string.clear_search),
                    tint = TextSecondary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun SearchResults(state: SearchState, onAdd: (PlaceSearchResult) -> Unit) {
    // Yeni arama sürerken önceki sonuçlar ekranda kalsın; liste her harfte boşalıp dolmasın.
    var lastResults by remember { mutableStateOf<List<PlaceSearchResult>>(emptyList()) }
    if (state is SearchState.Results) lastResults = state.results
    val results = if (state is SearchState.Failed) emptyList() else lastResults
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = bottom + 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when {
            state is SearchState.Failed -> item { Message(stringResource(R.string.search_failed)) }
            state is SearchState.Results && results.isEmpty() -> item { Message(stringResource(R.string.search_no_results)) }
        }
        items(results, key = { "${it.latitude},${it.longitude},${it.name}" }) { result ->
            PlaceRowSurface(onClick = { onAdd(result) }) {
                Column(Modifier.weight(1f)) {
                    Text(result.name, color = TextPrimary, style = MaterialTheme.typography.titleMedium)
                    result.region?.let {
                        Text(it, color = TextSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                    }
                }
            }
        }
        item {
            Text(
                stringResource(R.string.search_attribution),
                color = TextTertiary,
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            )
        }
    }
}

@Composable
private fun SavedPlacesList(
    places: List<SavedPlace>,
    deviceName: String?,
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
    onSelect: (Long) -> Unit,
    onToggleHome: (SavedPlace) -> Unit,
    onRemove: (SavedPlace) -> Unit,
    onReorder: (List<Long>) -> Unit,
) {
    // Sürüklerken liste anında yer değiştirsin; veritabanına bırakınca yazılır.
    var order by remember { mutableStateOf(places) }
    LaunchedEffect(places) { order = places }
    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        val fromIndex = order.indexOfFirst { it.id == from.key }
        val toIndex = order.indexOfFirst { it.id == to.key }
        // "Bulunduğum yer" ve başlıklar sürüklenemez, üzerlerine de bırakılamaz.
        if (fromIndex >= 0 && toIndex >= 0) {
            order = order.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
        }
    }
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = bottom + 96.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "device") {
            PlaceRowSurface(onClick = if (hasPermission) ({ onSelect(DEVICE_PLACE_ID) }) else onRequestPermission) {
                Icon(painterResource(R.drawable.ic_location), contentDescription = null, tint = TextPrimary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.current_location), color = TextPrimary, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = when {
                            !hasPermission -> stringResource(R.string.device_place_no_permission)
                            deviceName != null -> deviceName
                            else -> stringResource(R.string.device_place_locating)
                        },
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        item(key = "title") {
            SectionTitle(stringResource(R.string.saved_places), Modifier.padding(start = 8.dp, top = 16.dp, bottom = 4.dp))
        }
        if (order.isEmpty()) {
            item(key = "empty") { Message(stringResource(R.string.no_saved_places)) }
        }
        items(order, key = { it.id }) { place ->
            ReorderableItem(reorderState, key = place.id) { isDragging ->
                PlaceRowSurface(onClick = { onSelect(place.id) }, lifted = isDragging) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                place.name,
                                color = TextPrimary,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            if (place.isHome) {
                                Spacer(Modifier.width(8.dp))
                                HomeBadge()
                            }
                        }
                        place.region?.let {
                            Text(it, color = TextSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    RowIcon(
                        icon = if (place.isHome) R.drawable.ic_home else R.drawable.ic_home_outline,
                        description = stringResource(if (place.isHome) R.string.unset_home else R.string.set_home, place.name),
                        onClick = { onToggleHome(place) },
                    )
                    RowIcon(
                        icon = R.drawable.ic_delete,
                        description = stringResource(R.string.delete_place, place.name),
                        onClick = { onRemove(place) },
                    )
                    Icon(
                        painterResource(R.drawable.ic_drag),
                        contentDescription = stringResource(R.string.reorder_place),
                        tint = TextSecondary,
                        modifier = Modifier
                            .draggableHandle(onDragStopped = { onReorder(order.map { it.id }) })
                            .size(44.dp)
                            .padding(10.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeBadge() {
    Row(
        Modifier
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.18f))
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(R.drawable.ic_home), contentDescription = null, tint = TextPrimary, modifier = Modifier.size(12.dp))
        Spacer(Modifier.width(4.dp))
        Text(stringResource(R.string.home_place), color = TextPrimary, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun RowIcon(icon: Int, description: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), contentDescription = description, tint = TextPrimary, modifier = Modifier.size(20.dp))
    }
}

/** Cam kartla aynı görünümde, tıklanabilir bir satır. Sürüklenirken hafifçe parlar. */
@Composable
private fun PlaceRowSurface(
    onClick: () -> Unit,
    lifted: Boolean = false,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    val isLight = LocalSkyIsLight.current
    val base = if (isLight) Color(0xFF0A1B36).copy(alpha = 0.17f) else Color.White.copy(alpha = 0.075f)
    val fill by animateColorAsState(if (lifted) Color.White.copy(alpha = 0.22f) else base, label = "rowFill")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(GlassShape)
            .background(fill)
            .border(1.dp, Color.White.copy(alpha = if (lifted) 0.4f else 0.14f), GlassShape)
            .clickable(onClick = onClick)
            .heightIn(min = 64.dp)
            .padding(start = 18.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
private fun Message(text: String) {
    Text(
        text,
        color = TextSecondary,
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
    )
}
