package com.criptext.mail.db.dao

import android.arch.persistence.room.*
import com.criptext.mail.db.models.*

/**
 * Interface that encapsulates all database interactions needed by the mailbox scene.
 * Created by gabriel on 4/23/18.
 */
@Dao
interface EmailInsertionDao {

    @Insert
    fun insertEmail(email: Email): Long

    @Query("SELECT * FROM email")
    fun getAll(): List<Email>

    @Query("DELETE FROM email WHERE id = :draftEmailId" )
    fun deletePreviouslyCreatedDraft(draftEmailId: Long)

    @Query("Select * from email where messageId = :messageId")
    fun findEmailByMessageId(messageId: String): Email?

    @Query("Select * from email where metadataKey = :metadataKey")
    fun findEmailByMetadataKey(metadataKey: Long): Email?

    @Insert
    fun insertEmails(emails: List<Email>): List<Long>

    @Insert
    fun insertContacts(contacts: List<Contact>): List<Long>

    @Query("""UPDATE contact
            SET name=:name
            where id=:id""")
    fun updateContactName(id: Long, name: String)

    @Query("SELECT * FROM contact where email in (:emailAddresses)")
    fun findContactsByEmail(emailAddresses: List<String>): List<Contact>

    @Insert
    fun insertEmailLabelRelations(emailLabelRelations: List<EmailLabel>)

    @Query("""DELETE FROM email_label
        WHERE labelId= :labelId AND emailId=:emailId""")
    fun deleteByEmailLabelIds(labelId: Long, emailId: Long)

    @Insert
    fun insertEmailContactRelations(emailContactRelations: List<EmailContact>)

    @Insert
    fun insertEmailFiles(emailFiles: List<CRFile>)

    @Insert
    fun insertEmailFileKey(emailFileKey: FileKey)

    @Transaction
    fun runTransaction(insertFn: () -> Long): Long {
        return insertFn()
    }
}
