package com.example.orbit.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.orbit.data.local.entity.AttendanceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AttendanceDao {

    /** IGNORE kao na serveru: ostaje prvo vreme dolaska */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(row: AttendanceEntity)

    @Query("DELETE FROM attendances")
    suspend fun deleteAll()

    /** Posle sync-a lokalni dolasci su isti kao na serveru */
    @Transaction
    suspend fun replaceAll(rows: List<AttendanceEntity>) {
        deleteAll()
        rows.forEach { insert(it) }
    }

    @Query("SELECT EXISTS(SELECT 1 FROM attendances WHERE eventId = :eventId)")
    fun observeHasAttended(eventId: String): Flow<Boolean>

    /** F-36: istorija, najnoviji dolazak prvi; blokirani organizatori ostaju jer je to moj zapis */
    @Query(
        "SELECT e.*, a.checkedInAt AS checkedInAt, r.value AS myRating FROM attendances a " +
            "INNER JOIN events e ON e.id = a.eventId " +
            "LEFT JOIN ratings r ON r.eventId = a.eventId AND r.userId = :userId " +
            "ORDER BY a.checkedInAt DESC"
    )
    fun observeAttendedEvents(userId: String): Flow<List<AttendedEventRow>>
}
