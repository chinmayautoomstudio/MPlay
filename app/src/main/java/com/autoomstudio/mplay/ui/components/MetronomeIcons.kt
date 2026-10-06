package com.autoomstudio.mplay.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** Material Icons has no metronome; same shape as res/drawable/ic_notification_metronome. */
object MetronomeIcons {
    val Default: ImageVector by lazy { build() }

    private fun build(): ImageVector = ImageVector.Builder(
        name = "Metronome",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    )
        .addPath(
            pathData = PathParser().parsePathString(
                "M9.2,3h5.6l3.7,17c0.1,0.55 -0.3,1 -0.85,1H6.35c-0.55,0 -0.95,-0.45 -0.85,-1zM10.4,5L8.1,16h7.8L13.6,5z",
            ).toNodes(),
            pathFillType = PathFillType.EvenOdd,
            fill = SolidColor(Color.Black),
        )
        .addPath(
            pathData = PathParser().parsePathString("M11.1,15.3L16.6,6.4l1.3,0.8 -5.5,8.9z").toNodes(),
            fill = SolidColor(Color.Black),
        )
        .build()
}
