package com.criptext.mail.db.models

/**
 * Created by sebas on 3/12/18.
 */

data class FullEmail(
        val email: Email,
        val labels: List<Label>,
        val cc: List<Contact>,
        val to: List<Contact>,
        val bcc: List<Contact>,
        val from: Contact,
        val files: List<CRFile>,
        val fileKey: String?) {

    var viewOpen = false
    var isUnsending = false

}
