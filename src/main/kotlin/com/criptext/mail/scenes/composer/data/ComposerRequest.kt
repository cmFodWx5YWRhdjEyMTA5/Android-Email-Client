package com.criptext.mail.scenes.composer.data

/**
 * Created by gabriel on 2/26/18.
 */

sealed class ComposerRequest {
    class GetAllContacts : ComposerRequest()
    class SaveEmailAsDraft(val threadId: String?, val emailId: Long?,
                           val composerInputData: ComposerInputData,
                           val onlySave: Boolean, val attachments: List<ComposerAttachment>,
                           val fileKey: String?): ComposerRequest()
    class DeleteDraft(val emailId: Long): ComposerRequest()
    class UploadAttachment(val filepath: String, val fileKey: String?): ComposerRequest()
    class LoadInitialData(val composerType: ComposerType, val emailId: Long): ComposerRequest()
}