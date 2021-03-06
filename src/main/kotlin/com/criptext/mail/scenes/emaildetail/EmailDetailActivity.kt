package com.criptext.mail.scenes.emaildetail

import com.criptext.mail.BaseActivity
import com.criptext.mail.R
import com.criptext.mail.api.Hosts
import com.criptext.mail.api.HttpClient
import com.criptext.mail.bgworker.AsyncTaskWorkRunner
import com.criptext.mail.db.AppDatabase
import com.criptext.mail.db.EmailDetailLocalDB
import com.criptext.mail.db.EventLocalDB
import com.criptext.mail.db.KeyValueStorage
import com.criptext.mail.db.models.ActiveAccount
import com.criptext.mail.scenes.SceneController
import com.criptext.mail.scenes.emaildetail.data.EmailDetailDataSource
import com.criptext.mail.signal.SignalClient
import com.criptext.mail.signal.SignalStoreCriptext
import com.criptext.mail.utils.KeyboardManager
import com.criptext.mail.utils.file.AndroidFs
import com.criptext.mail.utils.generaldatasource.data.GeneralDataSource
import com.criptext.mail.websocket.WebSocketSingleton

/**
 * Created by sebas on 3/12/18.
 */

class  EmailDetailActivity: BaseActivity() {

    override val layoutId = R.layout.activity_emails_detail
    override val toolbarId = R.id.email_detail_toolbar

    override fun initController(receivedModel: Any): SceneController {

        val appDB = AppDatabase.getAppDatabase(this.applicationContext)
        val filesHttpClient = HttpClient.Default(Hosts.fileServiceUrl, HttpClient.AuthScheme.jwt, 14000L, 7000L)
        val db: EmailDetailLocalDB.Default =
                EmailDetailLocalDB.Default(appDB)
        val emailDetailModel = receivedModel as EmailDetailSceneModel
        val httpClient = HttpClient.Default()
        val signalClient = SignalClient.Default(SignalStoreCriptext(appDB))

        val emailDetailSceneView = EmailDetailScene.EmailDetailSceneView(
                findViewById(R.id.include_emails_detail), this)

        val activeAccount = ActiveAccount.loadFromStorage(this)!!
        val webSocketEvents = WebSocketSingleton.getInstance(
                activeAccount = activeAccount)
        val downloadDir = AndroidFs.getDownloadsCacheDir(this).absolutePath
        val remoteChangeDataSource = GeneralDataSource(
                signalClient = signalClient,
                eventLocalDB = EventLocalDB(appDB),
                storage = KeyValueStorage.SharedPrefs(this),
                db = appDB,
                runner = AsyncTaskWorkRunner(),
                activeAccount = activeAccount,
                httpClient = httpClient
        )

        return  EmailDetailSceneController(
                model = emailDetailModel,
                scene = emailDetailSceneView,
                host = this,
                activeAccount = activeAccount,
                websocketEvents = webSocketEvents,
                keyboard = KeyboardManager(this),
                generalDataSource = remoteChangeDataSource,
                dataSource = EmailDetailDataSource(
                        runner = AsyncTaskWorkRunner(),
                        emailDao = appDB.emailDao(),
                        emailContactDao = appDB.emailContactDao(),
                        httpClient = httpClient,
                        activeAccount = activeAccount,
                        filesHttpClient= filesHttpClient,
                        emailDetailLocalDB = db,
                        downloadDir = downloadDir
                )
        )
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        controller.requestPermissionResult(requestCode, permissions, grantResults)
    }

}
