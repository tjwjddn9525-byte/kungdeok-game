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
