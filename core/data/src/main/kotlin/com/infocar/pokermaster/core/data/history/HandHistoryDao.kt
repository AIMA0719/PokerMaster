package com.infocar.pokermaster.core.data.history

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * 핸드 히스토리 DAO — M5-A.
 *
 * [observeRecent] 는 Flow 로 반환해 Room 이 자체 invalidation 으로 리스트 화면 재구성.
 * 실제 페이징은 Paging3 도입 전까지 LIMIT/OFFSET 기반 간이 버전 ([observeTop] / [observePage]).
 */
@Dao
interface HandHistoryDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: HandHistoryEntity): Long

    @Query("SELECT * FROM hand_history WHERE id = :id LIMIT 1")
    suspend fun byId(id: Long): HandHistoryEntity?

    /**
     * 최근순 상위 [limit] 개. 리스트 화면 초기 로드용.
     * 페이징 필요하면 [observePage] 로 offset 제공.
     */
    @Query("SELECT * FROM hand_history ORDER BY started_at DESC LIMIT :limit")
    fun observeTop(limit: Int): Flow<List<HandHistoryEntity>>

    @Query("SELECT * FROM hand_history ORDER BY started_at DESC LIMIT :limit OFFSET :offset")
    fun observePage(limit: Int, offset: Int): Flow<List<HandHistoryEntity>>

    @Query("SELECT * FROM hand_history WHERE mode = :mode ORDER BY started_at DESC LIMIT :limit")
    fun observeByMode(mode: String, limit: Int): Flow<List<HandHistoryEntity>>

    @Query("SELECT COUNT(*) FROM hand_history")
    suspend fun count(): Int

    @Query("DELETE FROM hand_history WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM hand_history")
    suspend fun clear()
}
