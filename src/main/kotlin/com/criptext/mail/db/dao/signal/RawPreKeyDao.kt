package com.criptext.mail.db.dao.signal

import android.arch.persistence.room.Dao
import android.arch.persistence.room.Insert
import android.arch.persistence.room.Query
import com.criptext.mail.db.models.signal.CRPreKey

/**
 * Created by gabriel on 3/5/18.
 */

@Dao
interface RawPreKeyDao {

    @Query("DELETE from raw_prekey where id = :id")
    fun deleteById(id: Int)


    @Insert
    fun insertAll(prekeys : List<CRPreKey>)

    @Query("DELETE from raw_prekey")
    fun deleteAll()

    @Query("SELECT * from raw_prekey where id = :id")
    fun find(id: Int): CRPreKey?

    @Insert
    fun insert(crPreKey: CRPreKey)
}