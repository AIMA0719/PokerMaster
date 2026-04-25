package com.infocar.pokermaster.engine.decision

import kotlin.random.Random

/**
 * 다인 홀덤(3·4명+) 환경에서 NPC 슬롯에 페르소나를 결정적으로 분배하는 helper.
 *
 *  설계 원칙:
 *   - 첫 NPC 슬롯은 항상 [Persona.PRO] (대표 캐릭터 — 사용자가 가장 자주 보는 자리에 안정적인 균형형 배치).
 *   - 나머지 슬롯은 (PRO 제외) 8개 풀에서 seed 기반 결정적 셔플 후 stratified pick.
 *     같은 seed → 같은 출력 (테스트/replay 가능).
 *   - npcCount <= 8 일 때는 동일 페르소나 중복 없음 (8가지 풀 + PRO=1 = 최대 8 unique).
 *   - npcCount > 8 이면 풀이 소진된 뒤 wrap-around 으로 일부 중복 허용.
 */
object PersonaPool {

    /**
     * NPC 수에 맞는 페르소나 시퀀스를 결정적으로 생성.
     *
     * @param npcCount 분배할 NPC 슬롯 수 (1 이상).
     * @param seed 셔플 결정성 seed. 동일 seed → 동일 출력.
     * @return 길이 [npcCount] 의 페르소나 리스트. 첫 항목은 항상 [Persona.PRO].
     *         npcCount<=8 면 unique, npcCount>8 면 wrap-around 으로 중복 발생.
     */
    fun pickFor(npcCount: Int, seed: Long = 0L): List<Persona> {
        require(npcCount >= 1) { "npcCount must be >= 1, got $npcCount" }

        // 첫 자리는 항상 PRO
        if (npcCount == 1) return listOf(Persona.PRO)

        // PRO 제외 7개 페르소나를 seed 기반으로 셔플 (결정적)
        val rest = Persona.entries.filter { it != Persona.PRO }
        val shuffled = rest.toMutableList().also { it.shuffle(Random(seed)) }

        val out = ArrayList<Persona>(npcCount)
        out += Persona.PRO

        val needed = npcCount - 1
        // npcCount<=8 → shuffled (size=7) 에서 needed (<=7) 개 그대로 take
        // npcCount>8 → wrap-around 으로 중복 허용
        for (i in 0 until needed) {
            out += shuffled[i % shuffled.size]
        }
        return out
    }
}
