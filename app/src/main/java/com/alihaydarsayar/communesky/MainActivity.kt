package com.alihaydarsayar.communesky

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.alihaydarsayar.communesky.ui.home.HomeScreen
import com.alihaydarsayar.communesky.ui.theme.CommuneSkyTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Arka plan hep koyu gökyüzü olduğu için durum çubuğu ikonları beyaz kalsın.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContent {
            CommuneSkyTheme {
                HomeScreen()
            }
        }
    }
}
