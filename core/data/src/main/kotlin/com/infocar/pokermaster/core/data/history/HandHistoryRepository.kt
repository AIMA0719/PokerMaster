package com.infocar.pokermaster.core.data.history

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 핸드 히스토리 Repository — M5-A.
 *
 * 소비자는 DAO/Entity 를 알 필요가 없다. [HandHistoryRecord] 만 주고받는다.
 * Room 엔티티 ↔ record 변환은 내부 JSON 직렬화로 처리.
 */
interface HandHistoryRepository {
    suspend fun record(record: HandHistoryRecord): Long
    suspend fun byId(id: Long): HandHistoryRecord?
    fun observeRecent(limit: Int = DEFAULT_LIMIT): Flow<List<HandHistoryRecord>>
    suspend fun count(): Int
    suspend fun countSince(sinceEpochMs: Long): Int
    suspend fun delete(id: Long)
    suspend fun clear()

    companion object {
        const val DEFAULT_LIMIT: Int = 50
    }
}

/**
 * Room 기반 [HandHistoryRepository] 구현. DAO + JSON codec 을 감싸 record ↔ entity 변환.
 */
class RoomHandHistoryRepository(
    private val dao: HandHistoryDao,
    private val json: Json = DEFAULT_JSON,
) : HandHistoryRepository {

    override suspend fun record(record: HandHistoryRecord): Long =
        dao.insert(record.toEntity(json))

    override suspend fun byId(id: Long): HandHistoryRecord? =
        dao.byId(id)?.let { it.toRecordOrNull(json) }

    override fun observeRecent(limit: Int): Flow<List<HandHistoryRecord>> =
        dao.observeTop(limit).map { list -> list.mapNotNull { it.toRecordOrNull(json) } }

    override suspend fun count(): Int = dao.count()
    override suspend fun countSince(sinceEpochMs: Long): Int = dao.countSince(sinceEpochMs)
    override suspend fun delete(id: Long) = dao.deleteById(id)
    override suspend fun clear() = dao.clear()

    companion object {
        /** LLM 응답 파서와 동일 스타일 — 스키마 확장 내성 + leniency. */
        val DEFAULT_JSON: Json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }
}

/**
 * Entity → Record 손상 내성 변환. 한 row 의 JSON 이 깨져도 list flow 전체가 죽지 않도록
 * null 폴백. 손상 원인은 라이브러리 버전 변경 / 수동 DB 편집 / 부분 저장 등.
 */
internal fun HandHistoryEntity.toRecordOrNull(json: Json): HandHistoryRecord? =
    runCatching { toRecord(json) }
        .onFailure {
            android.util.Log.w("HandHistoryRepository", "row id=$id corrupted — skipping", it)
        }
        .getOrNull()

/** Entity → Record (JSON 역직렬화). 실패 시 예외 (저장된 데이터가 손상된 상태). */
internal fun HandHistoryEntity.toRecord(json: Json): HandHistoryRecord = HandHistoryRecord(
    id = id,
    mode = mode,
    handIndex = handIndex,
    startedAt = startedAt,
    endedAt = endedAt,
    seedCommitHex = seedCommitHex,
    serverSeedHex = serverSeedHex,
    clientSeedHex = clientSeedHex,
    nonce = nonce,
    initialState = json.decodeFromString(initialStateJson),
    actions = json.decodeFromString(actionsJson),
    resultJson = resultJson,
    winnerSeat = winnerSeat,
    potSize = potSize,
)

/** Record → Entity (JSON 직렬화). id=0 이면 Room 이 autoGenerate. */
internal fun HandHistoryRecord.toEntity(json: Json): HandHistoryEntity = HandHistoryEntity(
    id = id,
    mode = mode,
    handIndex = handIndex,
    startedAt = startedAt,
    endedAt = endedAt,
    seedCommitHex = seedCommitHex,
    serverSeedHex = serverSeedHex,
    clientSeedHex = clientSeedHex,
    nonce = nonce,
    initialStateJson = json.encodeToString(initialState),
    actionsJson = json.encodeToString(actions),
    resultJson = resultJson,
    winnerSeat = winnerSeat,
    potSize = potSize,
)
