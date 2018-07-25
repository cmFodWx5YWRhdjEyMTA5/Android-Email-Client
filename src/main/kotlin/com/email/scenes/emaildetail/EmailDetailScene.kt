package com.email.scenes.emaildetail

import android.support.v4.content.ContextCompat
import android.support.v4.graphics.drawable.DrawableCompat
import android.support.v7.widget.RecyclerView
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import com.email.IHostActivity
import com.email.R
import com.email.SecureEmail
import com.email.db.LabelTypes
import com.email.db.models.FileDetail
import com.email.db.models.FullEmail
import com.email.db.models.Label
import com.email.scenes.emaildetail.ui.EmailDetailUIObserver
import com.email.scenes.label_chooser.LabelChooserDialog
import com.email.scenes.emaildetail.ui.FullEmailListAdapter
import com.email.scenes.emaildetail.ui.FullEmailRecyclerView
import com.email.scenes.emaildetail.ui.holders.FullEmailHolder
import com.email.scenes.emaildetail.ui.labels.LabelsRecyclerView
import com.email.scenes.label_chooser.LabelDataHandler
import com.email.scenes.mailbox.DeleteThreadDialog
import com.email.scenes.mailbox.MoveToDialog
import com.email.scenes.mailbox.OnDeleteThreadListener
import com.email.scenes.mailbox.OnMoveThreadsListener
import com.email.utils.EmailThreadValidator
import com.email.utils.virtuallist.VirtualList
import com.email.utils.UIMessage
import com.email.utils.getLocalizedUIMessage
import com.squareup.picasso.Callback
import com.squareup.picasso.Picasso

/**
 * Created by sebas on 3/12/18.
 */

interface EmailDetailScene {

    var observer: EmailDetailUIObserver?
    fun attachView(
            fullEmailEventListener: FullEmailListAdapter.OnFullEmailEventListener,
            fullEmailList : VirtualList<FullEmail>, fileDetailList: Map<Long, List<FileDetail>>,
            observer: EmailDetailUIObserver)

    fun showError(message : UIMessage)
    fun notifyFullEmailListChanged()
    fun notifyFullEmailChanged(position: Int)
    fun showDialogLabelsChooser(labelDataHandler: LabelDataHandler)
    fun showDialogMoveTo(onMoveThreadsListener: OnMoveThreadsListener)
    fun showDialogDeleteThread(onDeleteThreadListener: OnDeleteThreadListener)
    fun onFetchedSelectedLabels(
            selectedLabels: List<Label>,
            allLabels: List<Label>)

    fun onDecryptedBody(decryptedText: String)
    fun updateAttachmentProgress(emailPosition: Int, attachmentPosition: Int)
    fun onUnsendProgressEnd(emailPosition: Int)

    class EmailDetailSceneView(
            private val emailDetailView: View,
            private val hostActivity: IHostActivity)
        : EmailDetailScene {

        private val context = emailDetailView.context

        private lateinit var fullEmailsRecyclerView: FullEmailRecyclerView
        private lateinit var labelsRecyclerView: LabelsRecyclerView

        private val labelChooserDialog = LabelChooserDialog(context, emailDetailView)
        private val moveToDialog = MoveToDialog(context)
        private val deleteDialog = DeleteThreadDialog(context)

        private val recyclerView: RecyclerView by lazy {
            emailDetailView.findViewById<RecyclerView>(R.id.emails_detail_recycler)
        }

        private val recyclerLabelsView: RecyclerView by lazy {
            emailDetailView.findViewById<RecyclerView>(R.id.labels_recycler)
        }

        private val backButton: ImageView by lazy {
            emailDetailView.findViewById<ImageView>(R.id.mailbox_back_button)
        }

        private val textViewSubject: TextView by lazy {
            emailDetailView.findViewById<TextView>(R.id.textViewSubject)
        }

        private val starredImage: ImageView by lazy {
            emailDetailView.findViewById<ImageView>(R.id.starred)
        }

        override var observer: EmailDetailUIObserver? = null

        override fun attachView(
                fullEmailEventListener: FullEmailListAdapter.OnFullEmailEventListener,
                fullEmailList : VirtualList<FullEmail>, fileDetailList: Map<Long, List<FileDetail>>,
                observer: EmailDetailUIObserver){

            this.observer = observer
            textViewSubject.text = if (fullEmailList[0].email.subject.isEmpty())
                textViewSubject.context.getString(R.string.nosubject)
            else fullEmailList[0].email.subject

            val isStarred = EmailThreadValidator.isLabelInList(fullEmailList[0].labels, SecureEmail.LABEL_STARRED)
            if(isStarred){
                setIconAndColor(R.drawable.starred, R.color.starred)
            }

            labelsRecyclerView = LabelsRecyclerView(recyclerLabelsView, getLabelsFromEmails(fullEmailList))

            fullEmailsRecyclerView = FullEmailRecyclerView(
                    recyclerView,
                    fullEmailEventListener,
                    fullEmailList,
                    fileDetailList
                    )

            fullEmailsRecyclerView.scrollToLast()

            backButton.setOnClickListener {
                observer.onBackButtonPressed()
            }

            starredImage.setOnClickListener({
                observer.onStarredButtonPressed(!isStarred)
            })

        }

        private fun setIconAndColor(drawable: Int, color: Int){
            Picasso.with(context).load(drawable).into(starredImage, object : Callback {
                override fun onError() {}
                override fun onSuccess() {
                    DrawableCompat.setTint(starredImage.drawable,
                            ContextCompat.getColor(context, color))
                }
            })
        }

        private fun getLabelsFromEmails(
                emails: VirtualList<FullEmail>) : VirtualList<Label> {
            val labelSet = HashSet<Label>()
            for (i in 0 until emails.size) {
                labelSet.addAll(emails[i].labels)
            }
            val labelsList = ArrayList(labelSet).filter { it.type != LabelTypes.SYSTEM}
            return VirtualList.Map(labelsList, { t->t })
        }

        override fun notifyFullEmailListChanged() {
            fullEmailsRecyclerView.notifyFullEmailListChanged()
        }

        override fun notifyFullEmailChanged(position: Int) {
            fullEmailsRecyclerView.notifyFullEmailChanged(position = position)
        }

        override fun showDialogLabelsChooser(labelDataHandler: LabelDataHandler) {
            labelChooserDialog.showDialogLabelsChooser(dataHandler = labelDataHandler)
        }

        override fun onFetchedSelectedLabels(selectedLabels: List<Label>, allLabels: List<Label>) {
            labelChooserDialog.onFetchedLabels(
                    defaultSelectedLabels = selectedLabels,
                    allLabels = allLabels)
        }

        override fun showDialogMoveTo(onMoveThreadsListener: OnMoveThreadsListener) {
            moveToDialog.showMoveToDialog(
                    onMoveThreadsListener = onMoveThreadsListener,
                    currentFolder = SecureEmail.LABEL_ALL_MAIL)
        }

        override fun showDialogDeleteThread(onDeleteThreadListener: OnDeleteThreadListener) {
            deleteDialog.showDeleteThreadDialog(onDeleteThreadListener)
        }

        override fun onDecryptedBody(decryptedText: String) {
            
        }

        override fun onUnsendProgressEnd(emailPosition: Int) {
            val holder = recyclerView.findViewHolderForAdapterPosition(emailPosition)
                    as? FullEmailHolder ?: return
            holder.onUnsendProgressEnd()
        }

        override fun updateAttachmentProgress(emailPosition: Int, attachmentPosition: Int) {
            val holder = recyclerView.findViewHolderForAdapterPosition(emailPosition)
                    as? FullEmailHolder ?: return
            holder.updateAttachmentProgress(attachmentPosition)
        }

        override fun showError(message: UIMessage) {
            val duration = Toast.LENGTH_LONG
            val toast = Toast.makeText(
                    context,
                    context.getLocalizedUIMessage(message),
                    duration)
            toast.show()
        }
    }

}
