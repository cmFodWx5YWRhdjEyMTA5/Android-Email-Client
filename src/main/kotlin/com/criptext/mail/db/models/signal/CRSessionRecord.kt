package com.criptext.mail.db.models.signal

import android.arch.persistence.room.ColumnInfo
import android.arch.persistence.room.Entity

/**
 * Created by gabriel on 3/5/18.
 */
@Entity(tableName = "raw_session", primaryKeys = [ "recipientId", "deviceId" ])
 class CRSessionRecord(
        @ColumnInfo(name = "recipientId")
        var recipientId : String,

        @ColumnInfo(name = "deviceId")
        var deviceId : Int,

        @ColumnInfo(name = "byteString")
        var byteString : String
)