package com.criptext.mail.utils.ui

import android.content.Context
import android.support.v4.content.ContextCompat
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.criptext.mail.R
import com.criptext.mail.utils.EmailAddressUtils

class ForgotPasswordDialog(val context: Context, val emailAddress: String) {
    private var dialog: AlertDialog? = null
    private val res = context.resources

    fun showForgotPasswordDialog() {
        val dialogBuilder = AlertDialog.Builder(context)
        val inflater = (context as AppCompatActivity).layoutInflater
        val dialogView = inflater.inflate(R.layout.forgot_password_dialog, null)

        dialogBuilder.setView(dialogView)

        dialog = createDialog(dialogView,
                dialogBuilder)
    }

    private fun createDialog(dialogView: View,
                             dialogBuilder: AlertDialog.Builder)
            : AlertDialog {
        val width = res.getDimension(R.dimen.password_login_dialog_width).toInt()
        val newPasswordLoginDialog = dialogBuilder.create()
        val window = newPasswordLoginDialog.window
        newPasswordLoginDialog.show()
        window.setLayout(width, LinearLayout.LayoutParams.WRAP_CONTENT)
        window.setGravity(Gravity.CENTER_VERTICAL)
        val drawableBackground = ContextCompat.getDrawable(
                dialogView.context, R.drawable.dialog_label_chooser_shape)
        newPasswordLoginDialog.window.setBackgroundDrawable(drawableBackground)

        val textView = dialogView.findViewById(R.id.message_text) as TextView
        textView.text = context.getString(R.string.forgot_password_message,
                EmailAddressUtils.hideEmailAddress(emailAddress))

        assignButtonEvents(dialogView,
                newPasswordLoginDialog)

        return newPasswordLoginDialog
    }

    private fun assignButtonEvents(view: View,
                                   dialog: AlertDialog) {

        val btnOk = view.findViewById(R.id.reset_password_ok) as Button

        btnOk.setOnClickListener {
            dialog.dismiss()
        }
    }
}
