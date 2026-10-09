package com.alihaydarsayar.communesky.ui.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.alihaydarsayar.communesky.R
import com.alihaydarsayar.communesky.data.places.PlacesRepository
import com.alihaydarsayar.communesky.data.settings.SettingsRepository
import com.alihaydarsayar.communesky.model.AppSettings
import com.alihaydarsayar.communesky.model.TemperatureUnit
import com.alihaydarsayar.communesky.model.ThemeMode
import com.alihaydarsayar.communesky.model.WindUnit
import com.alihaydarsayar.communesky.ui.common.GlassCard
import com.alihaydarsayar.communesky.ui.common.GlassSegmented
import com.alihaydarsayar.communesky.ui.common.ScreenTopBar
import com.alihaydarsayar.communesky.ui.common.labelRes
import com.alihaydarsayar.communesky.ui.theme.TextPrimary
import com.alihaydarsayar.communesky.ui.theme.TextSecondary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    placesRepository: PlacesRepository,
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    val placeCount: StateFlow<Int> = placesRepository.places.map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun setTemperatureUnit(unit: TemperatureUnit) = viewModelScope.launch { settingsRepository.setTemperatureUnit(unit) }

    fun setWindUnit(unit: WindUnit) = viewModelScope.launch { settingsRepository.setWindUnit(unit) }

    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch { settingsRepository.setThemeMode(mode) }
}

/**
 * Uygulama dili. Android'in "uygulama dili" özelliğiyle saklanır (AppCompat, Android 12 ve
 * altında da çalışır). Android 13+ sistem ayarlarındaki "Uygulama dilleri" ile aynı ayardır.
 */
enum class AppLanguage(val tag: String) {
    System(""),
    Turkish("tr"),
    English("en");

    companion object {
        fun current(): AppLanguage {
            val tag = AppCompatDelegate.getApplicationLocales()[0]?.language.orEmpty()
            return entries.firstOrNull { it.tag == tag && it != System } ?: System
        }

        fun apply(language: AppLanguage) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language.tag))
        }
    }
}

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onManagePlaces: () -> Unit,
    onOpenHomeLocation: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val placeCount by viewModel.placeCount.collectAsStateWithLifecycle()
    var language by remember { mutableStateOf(AppLanguage.current()) }
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(stringResource(R.string.settings_title), onBack)
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = bottom + 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                GlassCard(Modifier.fillMaxWidth(), title = stringResource(R.string.settings_places)) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.medium)
                            .clickable(onClick = onManagePlaces)
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.manage_places), color = TextPrimary, style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (placeCount == 0) {
                                    stringResource(R.string.no_saved_places_short)
                                } else {
                                    pluralStringResource(R.plurals.saved_places_count, placeCount, placeCount)
                                },
                                color = TextSecondary,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null, tint = TextSecondary, modifier = Modifier.size(24.dp))
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.medium)
                            .clickable(onClick = onOpenHomeLocation)
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.home_settings_title), color = TextPrimary, style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(R.string.home_settings_summary), color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                        }
                        Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null, tint = TextSecondary, modifier = Modifier.size(24.dp))
                    }
                }
            }
            item {
                GlassCard(Modifier.fillMaxWidth(), title = stringResource(R.string.settings_units)) {
                    Label(stringResource(R.string.settings_temperature))
                    GlassSegmented(
                        options = listOf(stringResource(R.string.unit_celsius), stringResource(R.string.unit_fahrenheit)),
                        selectedIndex = settings.temperatureUnit.ordinal,
                        onSelect = { viewModel.setTemperatureUnit(TemperatureUnit.entries[it]) },
                    )
                    Spacer(Modifier.height(16.dp))
                    Label(stringResource(R.string.settings_wind))
                    GlassSegmented(
                        options = WindUnit.entries.map { stringResource(it.labelRes) },
                        selectedIndex = settings.windUnit.ordinal,
                        onSelect = { viewModel.setWindUnit(WindUnit.entries[it]) },
                    )
                }
            }
            item {
                GlassCard(Modifier.fillMaxWidth(), title = stringResource(R.string.settings_appearance)) {
                    Label(stringResource(R.string.settings_theme))
                    GlassSegmented(
                        options = listOf(
                            stringResource(R.string.theme_system),
                            stringResource(R.string.theme_light),
                            stringResource(R.string.theme_dark),
                        ),
                        selectedIndex = settings.themeMode.ordinal,
                        onSelect = { viewModel.setThemeMode(ThemeMode.entries[it]) },
                    )
                    Text(
                        stringResource(R.string.settings_theme_hint),
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp, start = 4.dp),
                    )
                    Spacer(Modifier.height(16.dp))
                    Label(stringResource(R.string.settings_language))
                    GlassSegmented(
                        options = listOf(
                            stringResource(R.string.language_system),
                            stringResource(R.string.language_turkish),
                            stringResource(R.string.language_english),
                        ),
                        selectedIndex = language.ordinal,
                        onSelect = { index ->
                            language = AppLanguage.entries[index]
                            // Ekran yeni dilde yeniden oluşturulur.
                            AppLanguage.apply(language)
                        },
                    )
                }
            }
            item {
                GlassCard(Modifier.fillMaxWidth(), title = stringResource(R.string.settings_about)) {
                    Text(
                        stringResource(R.string.settings_data_sources),
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(
        text,
        color = TextPrimary,
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
    )
}
