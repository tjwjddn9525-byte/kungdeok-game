package com.kungdeok.game.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * 화면 안에 그리는 대화상자. 안드로이드 Dialog 창을 따로 띄우지 않으므로
 * 앱 화면에서도, 다른 앱 위에 띄운 오버레이 메뉴 안에서도 똑같이 동작한다.
 * (오버레이 창 안에서 AlertDialog 를 열면 창 토큰이 없어서 죽는다.)
 */
@Composable
fun InlineDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    title: @Composable () -> Unit,
    text: @Composable () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0x88000000))
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onDismissRequest),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .padding(24.dp)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(24.dp))
                // 안쪽을 눌러도 닫히지 않게 클릭을 먹는다
                .clickable(remember { MutableInteractionSource() }, indication = null) {}
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ProvideTextStyle(MaterialTheme.typography.headlineSmall) { title() }
            ProvideTextStyle(MaterialTheme.typography.bodyMedium) { text() }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { confirmButton() }
        }
    }
}
