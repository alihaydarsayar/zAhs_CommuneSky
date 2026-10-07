package com.alihaydarsayar.communesky.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val CardShape = RoundedCornerShape(24.dp)

/**
 * Gökyüzünün üstünde duran yarı saydam kart. Gerçek bulanıklık (blur) pahalı olduğu için
 * şimdilik saydam beyaz + ince kenarlık kullanıyoruz; cam efektini 4. adımda geliştireceğiz.
 */
@Composable
fun GlassCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(Color.White.copy(alpha = 0.12f))
            .border(1.dp, Color.White.copy(alpha = 0.18f), CardShape)
            .padding(16.dp),
    ) {
        Text(
            text = title,
            color = Color.White.copy(alpha = 0.7f),
            style = MaterialTheme.typography.labelLarge,
        )
        Spacer(Modifier.height(12.dp))
        content()
    }
}
