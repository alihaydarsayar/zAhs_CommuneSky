package com.alihaydarsayar.communesky.ui.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import com.alihaydarsayar.communesky.model.WeatherCondition

@Composable
fun WeatherIcon(
    condition: WeatherCondition,
    isNight: Boolean,
    size: Dp,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    Image(
        painter = painterResource(condition.iconRes(isNight)),
        contentDescription = contentDescription,
        modifier = modifier.size(size),
    )
}
