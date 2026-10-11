package com.alihaydarsayar.communesky

import android.graphics.Color
import android.content.Intent
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.glance.action.ActionParameters
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.alihaydarsayar.communesky.model.AppSettings
import com.alihaydarsayar.communesky.model.ThemeMode
import com.alihaydarsayar.communesky.model.currentScene
import com.alihaydarsayar.communesky.ui.common.LocalAppSettings
import com.alihaydarsayar.communesky.ui.common.LocalDarkTheme
import com.alihaydarsayar.communesky.ui.common.LocalSkyIsLight
import com.alihaydarsayar.communesky.ui.home.HomeScreen
import com.alihaydarsayar.communesky.ui.home.HomeViewModel
import com.alihaydarsayar.communesky.ui.home.placeholderScene
import com.alihaydarsayar.communesky.ui.places.PlacesScreen
import com.alihaydarsayar.communesky.ui.settings.HomeLocationScreen
import com.alihaydarsayar.communesky.ui.settings.MyWidgetsScreen
import com.alihaydarsayar.communesky.ui.settings.SettingsScreen
import com.alihaydarsayar.communesky.ui.sky.SkyBackground
import com.alihaydarsayar.communesky.ui.theme.CommuneSkyTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.serialization.Serializable

@Serializable private object HomeRoute
@Serializable private object PlacesRoute
@Serializable private object SettingsRoute
@Serializable private object HomeLocationRoute
@Serializable private object MyWidgetsRoute
@Serializable private object TodayRoute

// AppCompatActivity: uygulama içinden dil değiştirmenin Android 12 ve altında da çalışması için.
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private val viewModel: HomeViewModel by viewModels()

    /** Widget "Evi ayarla" notuna dokunulunca doğrudan Ayarlar > Ev ve konum açılır. */
    private val openHomeSettings = mutableStateOf(false)

    /** "Bugün için" widget'ına ya da sabah özetine dokunulunca uygulama o bölümle açılır. */
    private val openToday = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        // Açılış ekranı, önbellekteki hava durumu ve ayarlar okunana kadar (genelde birkaç on
        // milisaniye) ekranda kalır; böylece kullanıcı boş ekran yerine doğrudan dolu ekranı görür.
        installSplashScreen().setKeepOnScreenCondition {
            viewModel.uiState.value.isLoading || viewModel.settings.value == null
        }
        super.onCreate(savedInstanceState)
        // Widget'lar uygulama kapalıyken de seçili dili bilsin (bkz. WidgetLanguage).
        com.alihaydarsayar.communesky.widget.WidgetLanguage.remember(applicationContext, com.alihaydarsayar.communesky.ui.settings.AppLanguage.current().tag)
        if (savedInstanceState == null) {
            openHomeSettings.value = intent.wantsHomeSettings()
            openToday.value = intent.wantsToday()
            intent.selectWidgetPlace()
        }
        // Arka plan hep renkli bir gökyüzü olduğu için durum çubuğu ikonları beyaz kalsın.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContent {
            CommuneSkyTheme {
                CommuneSkyApp(
                    viewModel = viewModel,
                    openHomeSettings = openHomeSettings.value,
                    onHomeSettingsOpened = { openHomeSettings.value = false },
                    openToday = openToday.value,
                    onTodayOpened = { openToday.value = false },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.wantsHomeSettings()) openHomeSettings.value = true
        if (intent.wantsToday()) openToday.value = true
        intent.selectWidgetPlace()
    }

    /** Widget'ta havaya dokunulunca uygulama o widget'ın yeriyle açılır. */
    private fun Intent?.selectWidgetPlace() {
        val placeId = this?.getLongExtra(OpenPlaceKey.name, NO_PLACE) ?: NO_PLACE
        if (placeId == NO_PLACE) return
        // Debug sürümünde hangi yerin istendiği loglanır (yalnız kimlik numarası; denetim için).
        if (com.alihaydarsayar.communesky.widget.WidgetTiming.enabled(this@MainActivity)) android.util.Log.d("WidgetTap", "open_place=$placeId")
        viewModel.onPageSelected(placeId)
    }

    private fun Intent?.wantsHomeSettings(): Boolean = this?.getBooleanExtra(OpenHomeSettingsKey.name, false) == true

    private fun Intent?.wantsToday(): Boolean = this?.getBooleanExtra(OpenTodayKey.name, false) == true

    companion object {
        /** Widget'tan ya da bildirimden uygulamayı "Bugün için" bölümüyle açmak için. */
        val OpenTodayKey = ActionParameters.Key<Boolean>("open_today")

        /** Widget'tan uygulamayı açarken Ev ve konum ayarına gitmek için. */
        val OpenHomeSettingsKey = ActionParameters.Key<Boolean>("open_home_settings")

        /** Widget'tan uygulamayı açarken seçilecek yerin kimliği. */
        val OpenPlaceKey = ActionParameters.Key<Long>("open_place")

        private const val NO_PLACE = Long.MIN_VALUE
    }
}

@Composable
private fun CommuneSkyApp(
    viewModel: HomeViewModel,
    openHomeSettings: Boolean,
    onHomeSettingsOpened: () -> Unit,
    openToday: Boolean,
    onTodayOpened: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val appSettings = settings ?: AppSettings()
    val darkTheme = when (appSettings.themeMode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    // Gökyüzü bütün ekranların arkasında tek parça: seçili yerin havasına göre çizilir,
    // Ayarlar ve Yerler ekranına geçerken de kesilmez.
    val weather = uiState.selectedPage?.weather
    val scene = remember(weather) { weather?.currentScene() ?: placeholderScene() }
    val navController = rememberNavController()
    LaunchedEffect(openToday) {
        if (openToday) {
            navController.navigate(TodayRoute) { launchSingleTop = true }
            onTodayOpened()
        }
    }
    LaunchedEffect(openHomeSettings) {
        if (openHomeSettings) {
            navController.navigate(HomeLocationRoute)
            onHomeSettingsOpened()
        }
    }

    CompositionLocalProvider(
        LocalAppSettings provides appSettings,
        LocalDarkTheme provides darkTheme,
        LocalSkyIsLight provides scene.theme.isLight,
    ) {
        Box(Modifier.fillMaxSize()) {
            SkyBackground(scene, Modifier.fillMaxSize())
            NavHost(navController, startDestination = HomeRoute) {
                composable<HomeRoute> {
                    HomeScreen(
                        viewModel = viewModel,
                        onOpenPlaces = { navController.navigate(PlacesRoute) },
                        onOpenSettings = { navController.navigate(SettingsRoute) },
                        onOpenToday = { navController.navigate(TodayRoute) { launchSingleTop = true } },
                    )
                }
                composable<TodayRoute> {
                    com.alihaydarsayar.communesky.ui.today.TodayScreen(
                        weather = uiState.selectedPage?.weather,
                        onFeedback = viewModel::addWearFeedback,
                        onBack = { navController.popBackStack() },
                    )
                }
                composable<PlacesRoute> {
                    PlacesScreen(
                        onBack = { navController.popBackStack() },
                        onPlaceSelected = { navController.popBackStack(HomeRoute, inclusive = false) },
                    )
                }
                composable<SettingsRoute> {
                    SettingsScreen(
                        onBack = { navController.popBackStack() },
                        onManagePlaces = { navController.navigate(PlacesRoute) },
                        onOpenHomeLocation = { navController.navigate(HomeLocationRoute) },
                        onOpenMyWidgets = { navController.navigate(MyWidgetsRoute) },
                    )
                }
                composable<MyWidgetsRoute> {
                    MyWidgetsScreen(onBack = { navController.popBackStack() })
                }
                composable<HomeLocationRoute> {
                    HomeLocationScreen(
                        onBack = { navController.popBackStack() },
                        onManagePlaces = { navController.navigate(PlacesRoute) },
                    )
                }
            }
        }
    }
}
