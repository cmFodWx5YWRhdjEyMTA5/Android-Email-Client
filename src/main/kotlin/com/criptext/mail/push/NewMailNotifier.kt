package com.criptext.mail.push

import android.app.Notification
import android.content.Context
import com.criptext.mail.R
import com.criptext.mail.androidui.CriptextNotification

/**
 * Created by gabriel on 8/21/17.
 */

sealed class NewMailNotifier(val data: PushData.NewMail): Notifier {
    companion object {
        private val type = PushTypes.newMail
    }


    private fun postNotification(ctx: Context, isPostNougat: Boolean) {
        val cn = CriptextNotification(ctx)
        val notification = buildNotification(ctx, cn)
        cn.notify(notification.first, notification.second, CriptextNotification.ACTION_INBOX)
    }

    private fun postHeaderNotification(ctx: Context){
        val pendingIntent = ActivityIntentFactory.buildSceneActivityPendingIntent(ctx, PushTypes.openActivity,
                data.threadId, data.isPostNougat)
        val cn = CriptextNotification(ctx)
        cn.showHeaderNotification(data.title, R.drawable.push_icon,
                CriptextNotification.ACTION_INBOX, pendingIntent)
    }

    protected abstract fun buildNotification(ctx: Context, cn: CriptextNotification): Pair<Int, Notification>

    override fun notifyPushEvent(ctx: Context) {
        if (data.shouldPostNotification){
            if(data.isPostNougat) {
                postHeaderNotification(ctx)
            }
            postNotification(ctx, data.isPostNougat)
        }
    }

    class Single(data: PushData.NewMail): NewMailNotifier(data) {

        override fun buildNotification(ctx: Context, cn: CriptextNotification): Pair<Int, Notification> {
            val pendingIntent = ActivityIntentFactory.buildSceneActivityPendingIntent(ctx, type,
                data.threadId, data.isPostNougat)

            val notificationId = if(data.isPostNougat) type.requestCodeRandom() else type.requestCode()

            return Pair(notificationId, cn.createNewMailNotification(clickIntent = pendingIntent, threadId = data.threadId,
                    title = data.title, body = data.body, metadataKey = data.metadataKey,
                    notificationId = notificationId))

        }
    }

}