package com.criptext.mail.scenes.composer

import android.content.Intent
import android.view.ViewGroup
import com.criptext.mail.BaseActivity
import com.criptext.mail.R
import com.criptext.mail.api.Hosts
import com.criptext.mail.api.HttpClient
import com.criptext.mail.bgworker.AsyncTaskWorkRunner
import com.criptext.mail.db.AppDatabase
import com.criptext.mail.db.ComposerLocalDB
import com.criptext.mail.db.models.ActiveAccount
import com.criptext.mail.scenes.ActivityMessage
import com.criptext.mail.scenes.SceneController
import com.criptext.mail.scenes.composer.data.ComposerDataSource
import com.criptext.mail.utils.KeyboardManager
import droidninja.filepicker.FilePickerConst
import java.io.File

class ComposerActivity : BaseActivity() {

    override val layoutId = R.layout.activity_composer
    override val toolbarId = R.id.toolbar

    override fun initController(receivedModel: Any): SceneController {
        val httpClient = HttpClient.Default(Hosts.fileServiceUrl, HttpClient.AuthScheme.basic, 14000L, 7000L)
        val model = receivedModel as ComposerModel
        val view = findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
        val appDB = AppDatabase.getAppDatabase(this)
        val scene = ComposerScene.Default(view, KeyboardManager(this))
        val db = ComposerLocalDB(contactDao = appDB.contactDao(), emailDao = appDB.emailDao(),
                emailLabelDao = appDB.emailLabelDao(), emailContactDao = appDB.emailContactDao(),
                labelDao = appDB.labelDao(), accountDao = appDB.accountDao(),
                fileDao = appDB.fileDao(), fileKeyDao = appDB.fileKeyDao())
        val activeAccount = ActiveAccount.loadFromStorage(this)!!
        val dataSource = ComposerDataSource(
                httpClient = httpClient,
                composerLocalDB = db,
                activeAccount = activeAccount,
                emailInsertionDao = appDB.emailInsertionDao(),
                runner = AsyncTaskWorkRunner(), authToken = Hosts.fileServiceAuthToken)
        return ComposerController(
                model = model,
                scene = scene,
                activeAccount = activeAccount,
                dataSource = dataSource,
                host = this)
    }

    private fun setNewAttachmentsAsActivityMessage(data: Intent) {
        val selectedAttachments = data.getStringArrayListExtra(FilePickerConst.KEY_SELECTED_DOCS)
        val attachmentsList = selectedAttachments.map {
            val size = File(it).length()
            Pair(it, size)
        }
        setActivityMessage(ActivityMessage.AddAttachments(attachmentsList))
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == FilePickerConst.REQUEST_CODE_DOC && data != null) {
            setNewAttachmentsAsActivityMessage(data)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        controller.requestPermissionResult(requestCode, permissions, grantResults)
    }
}