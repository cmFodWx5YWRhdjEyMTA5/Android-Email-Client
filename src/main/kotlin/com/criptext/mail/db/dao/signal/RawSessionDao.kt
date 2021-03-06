package com.criptext.mail.db.dao.signal

import android.arch.persistence.room.*
import com.criptext.mail.db.models.KnownAddress
import com.criptext.mail.db.models.signal.CRSessionRecord

/**
 * Created by gabriel on 3/5/18.
 */

@Dao
interface RawSessionDao {

    @Insert
    fun insert(crSessionRecord: CRSessionRecord)

    @Delete
    fun delete(crSessionRecord: CRSessionRecord)

    /**
     * Each time signal calls storeSession it should replace any existing value, which isn't
     * exactly supported by SQL so let's delete first and then insertIgnoringConflicts in a single transaction.
     */
    @Transaction
    fun store(crSessionRecord: CRSessionRecord) {
        delete(crSessionRecord)
        insert(crSessionRecord)
    }

    @Query("""SELECT * FROM raw_session
              WHERE recipientId = :recipientId AND deviceId = :deviceId LIMIT 1""")
    fun find(recipientId: String, deviceId: Int): CRSessionRecord?

    @Query("""SELECT recipientId, deviceId FROM raw_session
              WHERE recipientId in (:recipients)""")
    fun getKnownAddresses(recipients: List<String>): List<KnownAddress>
    @Query("""DELETE FROM raw_session
              WHERE recipientId = :recipientId""")
    fun deleteByRecipientId(recipientId: String)

    @Query("""SELECT deviceId FROM raw_session
              WHERE recipientId = :recipientId""")
    fun findActiveDevicesByRecipientId(recipientId: String): List<Int>

    @Query("DELETE FROM raw_session")
    fun deleteAll()
}