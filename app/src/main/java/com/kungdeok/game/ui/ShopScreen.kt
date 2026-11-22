package com.kungdeok.game.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kungdeok.game.data.GameState
import com.kungdeok.game.logic.ShopItem
import com.kungdeok.game.logic.ShopKind
import com.kungdeok.game.logic.ShopRules
import com.kungdeok.game.model.CatId

/** 상점 (SPEC 9, 3단계): 간식, 장난감, 꾸미기 아이템 */
@Composable
fun ShopScreen(
    state: GameState,
    onBuy: (ShopItem) -> Unit,
    onTogglePlace: (ShopItem) -> Unit,
    onToggleWear: (CatId, ShopItem) -> Unit,
) {
    val g = state.game
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Row(Modifier.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("🛒 상점   ", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                ArtIcon("🪙", 24.dp)
                Text(" ${g.gold}", fontWeight = FontWeight.Bold, fontSize = 20.sp)
            }
            Text(
                "골드는 매일 선물, 1만 보 걷기, 캣타워에서 모을 수 있어요.",
                fontSize = 12.sp,
                color = Color.Gray,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
        for (kind in ShopKind.entries) {
            item { Text(kind.label, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(top = 8.dp, start = 4.dp)) }
            items(ShopItem.entries.filter { it.kind == kind }) { item ->
                val owned = item.kind != ShopKind.CONSUMABLE && item.id in g.owned
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF9F1))) {
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ArtIcon(item.icon, 48.dp)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(item.label, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text(item.desc, fontSize = 12.sp, color = Color.Gray)
                                if (item.kind == ShopKind.CONSUMABLE) {
                                    Text("가방에 ${ShopRules.bagCount(g, item)}개", fontSize = 12.sp)
                                }
                            }
                            if (owned) {
                                Text("보유", color = Color(0xFF5BAA6A), fontWeight = FontWeight.Bold)
                            } else {
                                Button(onClick = { onBuy(item) }, enabled = g.gold >= item.price) {
                                    ArtIcon("🪙", 16.dp)
                                    Text(" ${item.price}")
                                }
                            }
                        }
                        if (owned && item.kind == ShopKind.FURNITURE) {
                            val placed = item.id in g.placed
                            OutlinedButton(onClick = { onTogglePlace(item) }, modifier = Modifier.padding(top = 6.dp)) {
                                Text(if (placed) "방에서 치우기" else "방에 놓기")
                            }
                        }
                        if (owned && item.kind == ShopKind.ACCESSORY) {
                            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                for (id in CatId.entries) {
                                    val wearing = state.cat(id).accessory == item.id
                                    if (wearing) {
                                        FilledTonalButton(onClick = { onToggleWear(id, item) }) { Text("${id.catName} 벗기기") }
                                    } else {
                                        OutlinedButton(onClick = { onToggleWear(id, item) }) { Text("${id.catName}에게 씌우기") }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
