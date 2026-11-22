package com.kungdeok.game.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items

import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kungdeok.game.data.GameState
import com.kungdeok.game.model.Form

/** 이 형태를 만나 봤는지. 진화는 한 줄로만 가므로 지금 단계 이하는 모두 만난 것. */
fun GameState.discovered(form: Form): Boolean {
    val owner = form.owner ?: return game.fusionSeen
    return cat(owner).stage >= form.stage.level
}

/** 도감 (SPEC 9): 11종 목록, 아직 못 만난 형태는 검은 실루엣과 "???" */
@Composable
fun DexScreen(state: GameState) {
    var selected by rememberSaveable { mutableStateOf<Form?>(null) }
    val found = Form.entries.count { state.discovered(it) }

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text(
                "📖 도감  $found / ${Form.entries.size}",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                modifier = Modifier.padding(4.dp),
            )
        }
        items(Form.entries) { form ->
            val seen = state.discovered(form)
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF9F1)),
                modifier = Modifier.clickable(enabled = seen) { selected = form },
            ) {
                Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(
                        rememberSprite(form.largeAsset),
                        contentDescription = if (seen) form.monName else "???",
                        modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                        colorFilter = if (seen) null else ColorFilter.tint(Color.Black),
                    )
                    Text(if (seen) form.monName else "???", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(form.stage.label, fontSize = 11.sp, color = Color.Gray)
                }
            }
        }
    }

    selected?.let { form ->
        InlineDialog(
            onDismissRequest = { selected = null },
            confirmButton = { TextButton(onClick = { selected = null }) { Text("닫기") } },
            title = { Text(form.monName, fontWeight = FontWeight.Bold) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(rememberSprite(form.largeAsset), form.monName, Modifier.fillMaxWidth().aspectRatio(1f))
                    Text("${form.owner?.catName ?: "합체"} · ${form.stage.label}", color = Color.Gray, fontSize = 13.sp)
                    Text(form.look, fontSize = 15.sp, modifier = Modifier.padding(top = 8.dp))
                    Text("기술: ${form.skill}", fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.padding(top = 4.dp))
                }
            },
        )
    }
}
