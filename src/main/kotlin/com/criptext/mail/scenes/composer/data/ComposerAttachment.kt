package com.criptext.mail.scenes.composer.data

import com.criptext.mail.db.AttachmentTypes
import com.criptext.mail.utils.file.FileUtils

data class ComposerAttachment(val filepath: String, var uploadProgress: Int,
                              var filetoken: String, val type: AttachmentTypes, var size: Long) {
    constructor(filepath: String, size: Long): this (filepath, -1, filetoken = "",
            type = FileUtils.getAttachmentTypeFromPath(filepath), size = size)
}