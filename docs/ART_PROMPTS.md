# 제미나이 그림 프롬프트 (픽셀 아트 에셋)

지금 앱에서 이모지나 단색 도형으로 대신하고 있는 그림들을 캐릭터와 같은 픽셀 스타일로 바꾸기 위한 프롬프트입니다.

## 사용법

1. 제미나이에서 **새 대화**를 열고, 캐릭터 그림 1~2장(예: `raw/kung_2_kungnyangmon.png`, `raw/nodeok_1_kongdeokmon.png`)을 첨부한 뒤 아래 **0. 스타일 지정**을 먼저 보냅니다.
2. 같은 대화에서 1번부터 하나씩 보냅니다. 마음에 안 들면 "다시"라고 해서 다시 뽑으세요.
3. 받은 그림을 **표에 적힌 파일 이름**으로 저장해서 GitHub 저장소의 `raw/ui/` 폴더에 올립니다.
   저장소 → `Add file` → `Upload files` → 파일 이름 앞에 `raw/ui/` 를 붙여서 올리면 폴더가 만들어집니다.
4. 저에게 "올렸어"라고 하면 흰 배경을 지우고, 여러 개가 한 장에 있는 그림은 잘라서 앱에 넣습니다.

**꼭 지킬 것:** 배경은 흰색(투명 아님), 글자 없음, 격자 그림은 칸 사이를 넉넉히. 해상도는 상관없습니다.

---

## 전체 목록 (체크리스트)

> 보스 6마리는 한 장(`bosses_sheet.png`, 3열×2행: 빗자루·로봇청소기·드라이기 / 강아지·목욕통·가방)으로 받아도 됩니다.
> 받은 그림 정리: `python3 tools/process_ui.py` → `sprites/ui/`, 캐릭터 프레임 시트(`raw/ui/frames_<형태>.png`)는 `raw/<형태>_walk1.png` 등으로 잘림
> → `python3 tools/process_sprites.py` 가 크기(원래 그림 키)와 방향(원래 그림과 같은 쪽)을 맞춰 `sprites/small/` 로

지금 앱에서 이모지·단색 도형으로 대신하거나 프로그램이 자동으로 만든 그림을 모두 모았습니다.
**우선순위**: ★★★ 화면에서 제일 많이 보임 → ★ 있으면 좋음

| # | 파일 이름 (`raw/ui/`) | 내용 | 앱에서 쓰이는 곳 | 지금은 | 우선 | 완료 |
|---|---|---|---|---|---|---|
| 1 | `room_background.png` | 방 배경 | 방 탭 | 단색 벽·바닥·그린 창문 | ★★★ |✅ |
| 2 | `tower_background.png` | 캣타워 밤 배경 | 캣타워 전투 무대 | 단색 + 그린 창문·선반 | ★★ |✅ |
| 3 | `furniture_sheet.png` | 가구 5개 | 방, 상점 | 📦🪴🧺🪵🖼️ 이모지 | ★★ |✅ |
| 4 | `accessories_sheet.png` | 꾸미기 5개 | 고양이 머리 위, 상점 | 🎀🔔🌸🕶️👑 이모지 | ★★ |✅ |
| 5 | `items_sheet.png` | 아이템 6개 (밥·간식·털실·빗·선물·코인) | 상점, 가방, 돌봄 장면 소품, 선물 상자, 골드 표시 | 🍚🍤🧶🪮🎁🪙 이모지 | ★★ |✅ |
| 6 | `care_icons_sheet.png` | 돌봄 버튼 6개 | 방 돌봄 버튼, 화면 위 고양이 메뉴 | 🤚🍚🍤🧶🪮🌙 이모지 | ★★★ |✅ |
| 7 | `tab_icons_sheet.png` | 탭 6개 | 아래 탭 바 | 🏠📊📖🗼🛒⚙️ 이모지 | ★ |✅ |
| 8 | `mobs_sheet.png` | 바탕화면 잡몹 6마리 | 바탕화면 사냥 | 🐭🪰🦗🐞🕷️🦋 이모지 | ★★★ |✅ |
| 9 | `enemies_sheet.png` | 캣타워 일반 적 8마리 | 캣타워 | 🐭🪰🐦🦎🕷️🐦‍⬛🧦👻 이모지 | ★★★ |✅ |
| 10 | `boss_broom.png` | 보스: 빗자루 대장 (5층) | 캣타워 | 🧹 | ★★ |✅ |
| 11 | `boss_robot_vacuum.png` | 보스: 로봇청소기 (10층) | 캣타워 | 🤖 | ★★ |✅ |
| 12 | `boss_hair_dryer.png` | 보스: 드라이기 (15층) | 캣타워 | 💨 | ★★ |✅ |
| 13 | `boss_neighbor_dog.png` | 보스: 옆집 강아지 (20층) | 캣타워 | 🐕 | ★★ |✅ |
| 14 | `boss_bathtub.png` | 보스: 목욕통 (25층) | 캣타워 | 🛁 | ★★ |✅ |
| 15 | `boss_vet_carrier.png` | 보스: 동물병원 가방 (30층) | 캣타워 | 🧳 | ★★ |✅ |
| 16 | `effects_sheet.png` | 효과 8개 | 전투 💥⭐💫, 돌봄 ✨❤, 잠 z, 먼지 | 이모지·동그라미 | ★★ |✅ |
| 17 | `app_icon.png` | 앱 아이콘 | 폰 홈 화면 | 콩덕몬 그림 잘라 씀 | ★ |✅ |
| 18 | `status_icons_sheet.png` | 상태 아이콘 4개 (배고픔·기분·청결·졸림) | 상태 막대, 화면 위 메뉴 | 🍚😊🫧💤 이모지 | ★ |✅ |
| 19 | `<캐릭터 파일 이름>_walk1.png` 등 | 캐릭터 걷기 2장 + 잠자기 1장 × 11형태 = 33장 | 화면 위 고양이 걷기·잠 | 원래 그림을 기울이고 눌러서 자동 생성 | ★★ (형태마다) |✅ |

19번은 `raw/ui/`가 아니라 **`raw/`** 폴더에 올립니다 (예: `raw/kung_1_somkungmon_walk1.png`). 지금 키우는 형태부터 하면 됩니다.

| 형태 | walk1 | walk2 | sleep |
|---|---|---|---|
| kung_1_somkungmon (솜쿵몬) | ✅ | ✅ | ✅ |
| kung_2_kungnyangmon (쿵냥몬) | ✅ | ✅ | ✅ |
| kung_3_galgikungmon (갈기쿵몬) | ✅ | ✅ | ✅ |
| kung_4_sajakungmon (사자쿵몬) | ✅ | ✅ | ✅ |
| kung_5_eunhakungmon (은하쿵몬) | ✅ | ✅ | ✅ |
| nodeok_1_kongdeokmon (콩덕몬) | ✅ | ✅ | ✅ |
| nodeok_2_nodeokmon (노덕몬) | ✅ | ✅ | ✅ |
| nodeok_3_mukjikdeokmon (묵직덕몬) | ✅ | ✅ | ✅ |
| nodeok_4_gureumdeokmon (구름덕몬) | ✅ | ✅ | ✅ |
| nodeok_5_deokhwangmon (덕황몬) | ✅ | ✅ | ✅ |
| fusion_kungdeokmon (쿵덕몬) | ✅ | ✅ | ✅ |

---

## 0. 스타일 지정 (첫 메시지, 캐릭터 그림 첨부)

```
I'm making game art for a cozy Tamagotchi-style Android pet game about two cats (the attached images). For every image I ask for next, match the attached art style exactly:
- detailed pixel art that looks like a small sprite scaled up: every pixel crisp and square, no blur, no anti-aliasing, no painterly texture
- a thin dark-brown outline (#3B2A20) around every object
- soft 2–3 step cel shading, light from the top-left, warm cozy colors
- background: one flat pure-white color (#FFFFFF) with nothing on it — no floor, no drop shadow, no frame, no text, no letters, no watermark
- objects centered with generous white margin around them
Only make one image per request. Wait for my first request.
```

---

## 1. 방 배경 → `room_background.png`

```
Pixel art game background, portrait 9:16, filling the whole image (this one has no white background).
The inside of a small cozy Korean apartment room, seen straight from the front like a side-view game stage.
Top 45%: cream wallpaper (#F3E3CF) with a very subtle pattern, and a wooden-frame window in the upper middle showing a light-blue sky with one small cloud.
Bottom 55%: warm honey-colored wooden floor (#E2C9A6) with plank lines, and a large oval terracotta rug in the lower middle.
Keep the floor empty and calm — no furniture, no cats, no people, no text — because characters will stand on it.
```

## 2. 캣타워 전투 배경 → `tower_background.png`

```
Pixel art game battle background, portrait 3:4, filling the whole image (no white background).
The inside of a giant cat tower at night: deep purple walls, a window on the upper left with a full moon and a few stars, wooden shelves and platforms on the right, a sisal-wrapped pole at the far right.
A wide wooden floor across the bottom 30% where the fighters will stand — keep the center of the floor clear.
No characters, no text.
```

## 3. 방 가구 5개 → `furniture_sheet.png`

```
Five separate furniture items for a cat room, arranged in a grid of 3 columns and 2 rows with lots of white space between them (the bottom-right cell is empty). Same scale, front view, white background.
Order left to right, top to bottom:
1. an open cardboard box big enough for a cat, flaps open
2. a small terracotta pot of fresh green cat grass
3. a round fluffy cushion bed
4. a cardboard-and-wood cat scratcher post
5. a wooden picture frame with a tiny photo of a gray tabby cat and a cream cat with a chocolate face
```

## 4. 고양이 꾸미기 5개 → `accessories_sheet.png`

```
Five small cat accessories, arranged in a grid of 3 columns and 2 rows with lots of white space between them (bottom-right cell empty). Front view, sized to sit on a cat's head or neck, white background.
Order: 1. a red ribbon bow  2. a red collar with a golden bell  3. a pink flower hair pin  4. black cool sunglasses  5. a small golden crown with red and blue gems
```

## 5. 아이템 아이콘 6개 → `items_sheet.png`

```
Six game item icons, arranged in a grid of 3 columns and 2 rows with lots of white space between them. Same size, front view, white background.
Order: 1. a light-blue bowl full of rice and cat food  2. a crispy fried shrimp cat treat  3. a pink ball of yarn with a loose strand  4. a wooden grooming brush  5. a wrapped gift box with a red ribbon  6. a shiny gold coin with a cat paw print on it
```

## 6. 돌봄 버튼 아이콘 6개 → `care_icons_sheet.png`

```
Six small UI icons for care buttons, arranged in a grid of 3 columns and 2 rows with lots of white space between them. Simple, bold, readable at small size, white background.
Order: 1. an open hand gently petting (palm down)  2. a cat food bowl  3. a fish-shaped cat snack  4. a cat toy wand with a feather  5. a grooming brush with sparkles  6. a crescent moon with a small sleeping cloud
```

## 7. 탭 아이콘 6개 → `tab_icons_sheet.png`

```
Six small UI tab icons, arranged in a grid of 3 columns and 2 rows with lots of white space between them. Simple, bold, readable at small size, white background.
Order: 1. a cozy little house with a cat ear roof  2. a clipboard with a heart (status)  3. an open book with a paw print (collection)  4. a tall cat tower  5. a shop basket with a fish  6. a gear with a small paw print (settings)
```

## 8. 바탕화면 잡몹 6마리 → `mobs_sheet.png`

```
Six tiny critters for a desktop pet game, arranged in a grid of 3 columns and 2 rows with lots of white space between them. Same scale, full body, side view facing LEFT, cute but a little mischievous, white background.
Order: 1. a small gray mouse  2. a housefly with see-through wings  3. a brown cricket  4. a red ladybug  5. a black hanging spider (no thread)  6. an orange butterfly with wings open
```

## 9. 캣타워 일반 적 8마리 → `enemies_sheet.png`

```
Eight small monsters for a cute turn-based battle, arranged in a grid of 4 columns and 2 rows with lots of white space between them. Same scale, full body, facing LEFT toward the player, cute-but-cheeky, white background.
Order: 1. a sneaky mouse with a tiny bandana  2. a giant buzzing fly  3. a puffed-up angry sparrow  4. a green lizard  5. a fuzzy spider  6. a black crow with a shiny trinket  7. a smelly inside-out sock monster with googly eyes  8. a floating ghost made of a white plastic bag
```

## 10~15. 캣타워 보스 6마리 (한 장에 하나씩)

공통 끝부분: `Full body, facing LEFT, a bit bigger and more detailed than normal enemies, menacing but still cute, white background.`

| 번호 | 파일 이름 | 프롬프트 앞부분 |
|---|---|---|
| 10 | `boss_broom.png` | `A pixel art boss monster: a broom general — a living broom with angry eyebrows, a tiny soldier helmet and a dustpan shield.` |
| 11 | `boss_robot_vacuum.png` | `A pixel art boss monster: a round robot vacuum cleaner with glowing red eyes, two little mechanical arms and spinning brushes.` |
| 12 | `boss_hair_dryer.png` | `A pixel art boss monster: a hair dryer with an angry face blowing a strong gust of hot wind.` |
| 13 | `boss_neighbor_dog.png` | `A pixel art boss monster: the big fluffy neighbor dog, a golden retriever grinning and drooling, standing tall.` |
| 14 | `boss_bathtub.png` | `A pixel art boss monster: a clawfoot bathtub monster overflowing with soap bubbles, a rubber duck on top, water splashing.` |
| 15 | `boss_vet_carrier.png` | `A pixel art boss monster: a pet carrier bag monster with a zipper mouth, holding a big vet syringe and a thermometer.` |

## 16. 효과 8개 → `effects_sheet.png`

```
Eight game effect sprites, arranged in a grid of 4 columns and 2 rows with lots of white space between them. White background.
Order: 1. a comic hit impact burst (orange and yellow)  2. a bright yellow star  3. a ring of dizzy stars and swirls  4. a blue sweat drop  5. a gray dust cloud puff  6. a cluster of white and yellow sparkles  7. a pink heart  8. a small sleepy cloud with two "Z" shapes
```

## 17. 앱 아이콘 → `app_icon.png`

```
A square app icon in pixel art, filling the whole image (no white background): the faces of the two attached cats side by side, cheek to cheek — the gray tabby on the left and the cream cat with the chocolate face on the right — on a warm cream (#F6EBDD) rounded-square background. No text.
```

## 18. 상태 아이콘 4개 → `status_icons_sheet.png`

```
Four tiny status icons for a pet game, arranged in a row of 4 with lots of white space between them. Simple, bold, readable at very small size, white background.
Order: 1. a cat food bowl (hunger)  2. a smiling cat face (mood)  3. soap bubbles (cleanliness)  4. a sleepy crescent moon (sleepiness)
```

## 19. 캐릭터 걷기·잠자기 프레임 (형태마다 따로, 새 대화 추천)

**해당 형태의 원래 그림 한 장만 첨부**하고 보냅니다. 세 장을 한 번에 받으면 크기가 같아서 좋습니다.

```
This is a character from my pixel art game (attached). Draw three animation frames of EXACTLY this same character — same design, same colors, same outline, same pixel size, same facing direction and the same overall size — in a row of 3 with lots of white space between them, white background, no text:
1. walking, left front paw and right back paw stepping forward
2. walking, the opposite paws stepping forward
3. sleeping: curled up lying down, eyes closed, peaceful
Do not add anything else (no ground, no shadow, no "Z" letters).
```

받은 그림은 저에게 주시면 세 장으로 잘라서 `raw/<형태 파일 이름>_walk1.png`, `_walk2.png`, `_sleep.png` 로 넣겠습니다.
(직접 자르실 필요 없이 한 장 그대로 `raw/ui/frames_<형태 파일 이름>.png` 로 올려 주셔도 됩니다.)
