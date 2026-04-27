package com.infocar.pokermaster.core.model

import kotlinx.serialization.Serializable

/**
 * 한국식 7스터드 Hi-Lo 선언 방향.
 *
 *  - [HI]: 하이 사이드만 경쟁.
 *  - [LO]: 로우 사이드만 경쟁 (8-or-better 자격 제한 없음 — 한국식).
 *  - [BOTH]: 양방향(scoop) 선언. 두 방향 모두 단독(또는 동률 단일) 우승해야 팟 전체.
 *    한 방향이라도 지거나, 본인이 1등이 아닌 동률 외 케이스에 걸리면 0.
 */
@Serializable
enum class DeclareDirection { HI, LO, BOTH }
