package com.email.scenes.mailbox

import com.email.SecureEmail
import com.email.db.LabelTypes
import com.email.db.models.Label

enum class NavigationMenuOptions {
    INBOX, SENT, DRAFT, STARRED, SPAM, TRASH, ALL_MAIL, LABELS, SETTINGS, SUPPORT;

    fun toLabel(): Label? = when(this) {
        INBOX -> Label.defaultItems.inbox
        SENT -> Label.defaultItems.sent
        DRAFT -> Label.defaultItems.draft
        STARRED -> Label.defaultItems.starred
        SPAM -> Label.defaultItems.spam
        TRASH -> Label.defaultItems.trash
        ALL_MAIL -> Label(id = -1, color = "000000", text = SecureEmail.LABEL_ALL_MAIL,
                visible = true, type = LabelTypes.SYSTEM)
        else -> null
    }
}
