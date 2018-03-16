package com.email.scenes.emaildetail

import android.content.Context
import com.email.IHostActivity
import com.email.R
import com.email.db.models.FullEmail
import com.email.scenes.SceneController
import com.email.scenes.emaildetail.data.EmailDetailDataSource
import com.email.scenes.emaildetail.data.EmailDetailRequest
import com.email.scenes.emaildetail.data.EmailDetailResult
import com.email.scenes.emaildetail.ui.FullEmailListAdapter
import com.email.utils.VirtualList

/**
 * Created by sebas on 3/12/18.
 */

class EmailDetailSceneController(private val scene: EmailDetailScene,
                                 private val model: EmailDetailSceneModel,
                                 private val host: IHostActivity,
                                 private val dataSource: EmailDetailDataSource) : SceneController() {

    private val dataSourceListener = { result: EmailDetailResult ->
        when (result) {
            is EmailDetailResult.LoadFullEmailsFromThreadId -> onFullEmailsLoaded(result)
        }
    }

    private val emailHolderEventListener = object : FullEmailListAdapter.OnFullEmailEventListener{

        override fun ontoggleViewOpen(fullEmail: FullEmail, position: Int, viewOpen: Boolean) {
                fullEmail.viewOpen = viewOpen

                scene.notifyFullEmailChanged(position)
                scene.notifyFullEmailListChanged()
        }

        override fun onReplyOptionSelected(fullEmail: FullEmail, position: Int, all: Boolean) {
            TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        }

        override fun onToggleReadOption(fullEmail: FullEmail, position: Int, markAsRead: Boolean) {
            TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        }

        override fun onDeleteOptionSelected(fullEmail: FullEmail, position: Int) {
            TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        }

        override fun onToggleFullEmailSelection(context: Context, fullEmail: FullEmail, position: Int) {
        }
    }

    private fun onFullEmailsLoaded(result: EmailDetailResult.LoadFullEmailsFromThreadId){
        when (result) {
            is EmailDetailResult.LoadFullEmailsFromThreadId.Success -> {

                val fullEmailsList = VirtualList.Map(result.fullEmailList, { t -> t })
                model.fullEmailList = fullEmailsList

                scene.attachView(
                        fullEmailList = fullEmailsList,
                        fullEmailEventListener = emailHolderEventListener)
            }

            is EmailDetailResult.LoadFullEmailsFromThreadId.Failure -> {
            }
        }
    }
    override fun onStart() {
        dataSource.listener = dataSourceListener

        val req = EmailDetailRequest.LoadFullEmailsFromThreadId(
                threadId = model.threadId)

        dataSource.submitRequest(req)
    }

    override fun onStop() {
    }

    override fun onBackPressed(): Boolean {
        return true
    }

    override fun onOptionsItemSelected(itemId: Int) {
    }

    override val menuResourceId: Int?
        get() = R.menu.mailbox_menu_multi_mode_read
}