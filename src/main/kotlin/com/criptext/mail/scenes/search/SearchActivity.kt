package com.criptext.mail.scenes.search

import com.criptext.mail.BaseActivity
import com.criptext.mail.R
import com.criptext.mail.bgworker.AsyncTaskWorkRunner
import com.criptext.mail.db.AppDatabase
import com.criptext.mail.db.KeyValueStorage
import com.criptext.mail.db.SearchLocalDB
import com.criptext.mail.db.models.ActiveAccount
import com.criptext.mail.scenes.SceneController
import com.criptext.mail.scenes.search.data.SearchDataSource
import com.criptext.mail.utils.KeyboardManager

/**
 * Created by danieltigse on 2/2/18.
 */

class SearchActivity : BaseActivity() {

    override val layoutId = R.layout.search_layout
    override val toolbarId = R.id.mailbox_toolbar

    override fun initController(receivedModel: Any): SceneController {
        val appDB = AppDatabase.getAppDatabase(this)
        val db : SearchLocalDB.Default = SearchLocalDB.Default(appDB)
        val model = receivedModel as SearchSceneModel
        val scene = SearchScene.SearchSceneView(findViewById(R.id.rootView), KeyboardManager(this))
        return SearchSceneController(
                scene = scene,
                model = model,
                host = this,
                storage = KeyValueStorage.SharedPrefs(this.applicationContext),
                activeAccount = ActiveAccount.loadFromStorage(this)!!,
                dataSource = SearchDataSource(db, AsyncTaskWorkRunner()))
    }
}