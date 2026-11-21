package com.kungdeok.game.model

/** 두 고양이. id 는 DB 키로 쓴다. */
enum class CatId(val id: String, val catName: String) {
    KUNG("kung", "쿵"),
    NODEOK("nodeok", "노덕");

    val other: CatId get() = if (this == KUNG) NODEOK else KUNG

    companion object {
        fun of(id: String): CatId = entries.first { it.id == id }
    }
}

/** 진화 단계 (SPEC 3-1). overlayDp 는 화면 위 오버레이 크기 (SPEC 3-3). */
enum class Stage(val level: Int, val label: String, val overlayDp: Int) {
    BABY(1, "유년기", 40),
    CHILD(2, "성장기", 52),
    ADULT(3, "성숙기", 64),
    PERFECT(4, "완전체", 76),
    ULTIMATE(5, "궁극체", 88),
    FUSION(6, "합체 궁극체", 100);

    companion object {
        fun of(level: Int): Stage = entries.first { it.level == level }
    }
}

/** 형태 하나 = 그림 한 장 (SPEC 3-1). asset 은 sprites/large|small 안의 파일 이름. */
enum class Form(
    val owner: CatId?,
    val stage: Stage,
    val monName: String,
    val look: String,
    val skill: String,
    val asset: String,
) {
    KUNG_1(CatId.KUNG, Stage.BABY, "솜쿵몬", "은빛 털과 하얀 배의 둥근 아기 솜뭉치", "솜털 굴리기", "kung_1_somkungmon.png"),
    KUNG_2(CatId.KUNG, Stage.CHILD, "쿵냥몬", "두 발로 서서 주먹을 쥔 용감한 아기 고양이", "쿵 박치기", "kung_2_kungnyangmon.png"),
    KUNG_3(CatId.KUNG, Stage.ADULT, "갈기쿵몬", "두툼한 흰 갈기와 전투 문양의 네 발 맹수", "시큰둥 눈빛", "kung_3_galgikungmon.png"),
    KUNG_4(CatId.KUNG, Stage.PERFECT, "사자쿵몬", "은색 클로 건틀릿을 낀 포효하는 사자 수인 전사", "대하품포", "kung_4_sajakungmon.png"),
    KUNG_5(CatId.KUNG, Stage.ULTIMATE, "은하쿵몬", "별빛 날개와 은하수 망토의 별빛 기사", "메테오 쿵", "kung_5_eunhakungmon.png"),
    NODEOK_1(CatId.NODEOK, Stage.BABY, "콩덕몬", "시큰둥한 표정의 크림·초콜릿 아기 솜뭉치", "아기 째림", "nodeok_1_kongdeokmon.png"),
    NODEOK_2(CatId.NODEOK, Stage.CHILD, "노덕몬", "시크한 매력의 두 발 아기 고양이", "초코 젤리 펀치", "nodeok_2_nodeokmon.png"),
    NODEOK_3(CatId.NODEOK, Stage.ADULT, "묵직덕몬", "쇠 징 목걸이를 찬 육중한 탱커 거수", "묵직하게 깔고 앉기", "nodeok_3_mukjikdeokmon.png"),
    NODEOK_4(CatId.NODEOK, Stage.PERFECT, "구름덕몬", "구름 목도리와 곰발 건틀릿의 수인 격투가", "대자 낮잠", "nodeok_4_gureumdeokmon.png"),
    NODEOK_5(CatId.NODEOK, Stage.ULTIMATE, "덕황몬", "황금 갑옷과 왕관, 홀을 든 황제 기사", "왕좌 내려찍기", "nodeok_5_deokhwangmon.png"),
    FUSION(null, Stage.FUSION, "쿵덕몬", "별빛 검과 황제의 창을 함께 든 초월 성기사", "쿵덕방아", "fusion_kungdeokmon.png");

    val largeAsset: String get() = "large/$asset"
    val smallAsset: String get() = "small/$asset"

    companion object {
        fun of(cat: CatId, stage: Int): Form =
            entries.first { it.owner == cat && it.stage.level == stage }
    }
}
