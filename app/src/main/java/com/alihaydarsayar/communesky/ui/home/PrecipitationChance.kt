package com.alihaydarsayar.communesky.ui.home

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.alihaydarsayar.communesky.R
import com.alihaydarsayar.communesky.ui.theme.RainAccent
import com.alihaydarsayar.communesky.ui.theme.TextTertiary

/** Bu ihtimalden itibaren yağış vurgulanır (mavi); altında soluk gösterilir. */
const val PrecipitationThreshold = 20

/** Damla ikonu + yağış ihtimali ("%10"). Düşük ihtimaller soluk, yüksekler mavi. */
@Composable
fun PrecipitationChance(
    probability: Int,
    style: TextStyle,
    modifier: Modifier = Modifier,
    iconSize: Dp = 10.dp,
) {
    val color = if (probability >= PrecipitationThreshold) RainAccent else TextTertiary
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(
            painter = painterResource(R.drawable.ic_drop),
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(iconSize),
        )
        Spacer(Modifier.width(2.dp))
        Text(
            text = stringResource(R.string.precipitation_value, probability),
            color = color,
            style = style,
        )
    }
}
