package com.alihaydarsayar.communesky

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.alihaydarsayar.communesky.ui.home.HomeScreen
import com.alihaydarsayar.communesky.ui.home.HomeViewModel
import com.alihaydarsayar.communesky.ui.theme.CommuneSkyTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: HomeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        // Açılış ekranı, önbellekteki hava durumu okunana kadar (genelde birkaç on milisaniye)
        // ekranda kalır; böylece kullanıcı boş ekran yerine doğrudan dolu ekranı görür.
        installSplashScreen().setKeepOnScreenCondition { viewModel.uiState.value.isCacheLoading }
        super.onCreate(savedInstanceState)
        // Arka plan hep renkli bir gökyüzü olduğu için durum çubuğu ikonları beyaz kalsın.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContent {
            CommuneSkyTheme {
                HomeScreen(viewModel)
            }
        }
    }
}
