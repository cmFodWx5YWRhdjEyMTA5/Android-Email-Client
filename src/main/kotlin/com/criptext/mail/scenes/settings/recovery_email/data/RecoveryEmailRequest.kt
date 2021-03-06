package com.criptext.mail.scenes.settings.recovery_email.data

sealed class RecoveryEmailRequest{
    class ResendConfirmationLink: RecoveryEmailRequest()
    data class CheckPassword(val password: String): RecoveryEmailRequest()
    data class ChangeRecoveryEmail(val password: String, val newRecoveryEmail: String): RecoveryEmailRequest()
}