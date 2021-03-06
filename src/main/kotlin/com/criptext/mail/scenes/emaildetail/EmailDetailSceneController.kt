package com.criptext.mail.scenes.emaildetail

import android.Manifest
import android.content.pm.PackageManager
import com.criptext.mail.BaseActivity
import com.criptext.mail.ExternalActivityParams
import com.criptext.mail.IHostActivity
import com.criptext.mail.R
import com.criptext.mail.api.models.UntrustedDeviceInfo
import com.criptext.mail.bgworker.BackgroundWorkManager
import com.criptext.mail.db.DeliveryTypes
import com.criptext.mail.db.models.ActiveAccount
import com.criptext.mail.db.models.FileDetail
import com.criptext.mail.db.models.FullEmail
import com.criptext.mail.db.models.Label
import com.criptext.mail.scenes.ActivityMessage
import com.criptext.mail.scenes.SceneController
import com.criptext.mail.scenes.composer.data.ComposerType
import com.criptext.mail.scenes.emaildetail.data.EmailDetailRequest
import com.criptext.mail.scenes.emaildetail.data.EmailDetailResult
import com.criptext.mail.scenes.emaildetail.ui.EmailDetailUIObserver
import com.criptext.mail.scenes.emaildetail.ui.FullEmailListAdapter
import com.criptext.mail.scenes.label_chooser.LabelDataHandler
import com.criptext.mail.scenes.label_chooser.SelectedLabels
import com.criptext.mail.scenes.label_chooser.data.LabelWrapper
import com.criptext.mail.scenes.mailbox.OnDeleteEmailListener
import com.criptext.mail.scenes.mailbox.OnDeleteThreadListener
import com.criptext.mail.scenes.mailbox.OnMoveThreadsListener
import com.criptext.mail.scenes.params.ComposerParams
import com.criptext.mail.scenes.params.LinkingParams
import com.criptext.mail.scenes.params.MailboxParams
import com.criptext.mail.scenes.params.SignInParams
import com.criptext.mail.scenes.signin.data.LinkStatusData
import com.criptext.mail.utils.KeyboardManager
import com.criptext.mail.utils.UIMessage
import com.criptext.mail.utils.file.FileUtils
import com.criptext.mail.utils.generaldatasource.data.GeneralRequest
import com.criptext.mail.utils.generaldatasource.data.GeneralResult
import com.criptext.mail.utils.virtuallist.VirtualList
import com.criptext.mail.websocket.WebSocketEventListener
import com.criptext.mail.websocket.WebSocketEventPublisher

/**
 * Created by sebas on 3/12/18.
 */

class EmailDetailSceneController(private val scene: EmailDetailScene,
                                 private val model: EmailDetailSceneModel,
                                 private val host: IHostActivity,
                                 private val activeAccount: ActiveAccount,
                                 private val generalDataSource: BackgroundWorkManager<GeneralRequest, GeneralResult>,
                                 private val dataSource: BackgroundWorkManager<EmailDetailRequest, EmailDetailResult>,
                                 private val websocketEvents: WebSocketEventPublisher,
                                 private val keyboard: KeyboardManager) : SceneController() {


    private val remoteChangeDataSourceListener: (GeneralResult) -> Unit = { result ->
        when(result) {
            is GeneralResult.DeviceRemoved -> onDeviceRemovedRemotely(result)
            is GeneralResult.ConfirmPassword -> onPasswordChangedRemotely(result)
            is GeneralResult.UpdateMailbox -> onMailboxUpdate(result)
            is GeneralResult.LinkAccept -> onLinkAccept(result)
        }
    }

    private val dataSourceListener = { result: EmailDetailResult ->
        when (result) {
            is EmailDetailResult.LoadFullEmailsFromThreadId -> onFullEmailsLoaded(result)
            is EmailDetailResult.UnsendFullEmailFromEmailId -> onUnsendEmail(result)
            is EmailDetailResult.GetSelectedLabels -> onSelectedLabelsLoaded(result)
            is EmailDetailResult.UpdateEmailThreadsLabelsRelations -> onUpdatedLabels(result)
            is EmailDetailResult.UpdateUnreadStatus -> onUpdateUnreadStatus(result)
            is EmailDetailResult.MoveEmailThread -> onMoveEmailThread(result)
            is EmailDetailResult.DownloadFile -> onDownloadedFile(result)
            is EmailDetailResult.ReadEmails -> onReadEmails(result)
        }
    }

    private val emailDetailUIObserver = object: EmailDetailUIObserver{
        override fun onLinkAuthConfirmed(untrustedDeviceInfo: UntrustedDeviceInfo) {
            generalDataSource.submitRequest(GeneralRequest.LinkAccept(untrustedDeviceInfo))
        }

        override fun onLinkAuthDenied(untrustedDeviceInfo: UntrustedDeviceInfo) {
            generalDataSource.submitRequest(GeneralRequest.LinkDenied(untrustedDeviceInfo))
        }

        override fun onOkButtonPressed(password: String) {
            generalDataSource.submitRequest(GeneralRequest.ConfirmPassword(password))
        }

        override fun onCancelButtonPressed() {
            generalDataSource.submitRequest(GeneralRequest.DeviceRemoved(true))
        }

        override fun onBackButtonPressed() {
            host.exitToScene(
                    params = MailboxParams(),
                    activityMessage = ActivityMessage.UpdateThreadPreview(model.threadPreview),
                    forceAnimation = false
            )
        }
    }

    private fun onDeviceRemovedRemotely(result: GeneralResult.DeviceRemoved){
        when (result) {
            is GeneralResult.DeviceRemoved.Success -> {
                host.exitToScene(SignInParams(), ActivityMessage.ShowUIMessage(UIMessage(R.string.device_removed_remotely_exception)), true, true)
            }
        }
    }

    private fun onPasswordChangedRemotely(result: GeneralResult.ConfirmPassword){
        when (result) {
            is GeneralResult.ConfirmPassword.Success -> {
                scene.dismissConfirmPasswordDialog()
                scene.showError(UIMessage(R.string.update_password_success))
            }
            is GeneralResult.ConfirmPassword.Failure -> {
                scene.setConfirmPasswordError(UIMessage(R.string.password_enter_error))
            }
        }
    }

    private fun onLinkAccept(resultData: GeneralResult.LinkAccept){
        when (resultData) {
            is GeneralResult.LinkAccept.Success -> {
                host.exitToScene(LinkingParams(activeAccount.userEmail, resultData.deviceId,
                        resultData.uuid, resultData.deviceType), null,
                        false, true)
            }
            is GeneralResult.LinkAccept.Failure -> {
                scene.showError(resultData.message)
            }
        }
    }

    private fun onMailboxUpdate(result: GeneralResult.UpdateMailbox){
        when (result) {
            is GeneralResult.UpdateMailbox.Success -> {
                dataSource.submitRequest(EmailDetailRequest.LoadFullEmailsFromThreadId(
                        model.threadId, model.currentLabel))
            }
            is GeneralResult.UpdateMailbox.Unauthorized ->
                generalDataSource.submitRequest(GeneralRequest.DeviceRemoved(false))
            is GeneralResult.UpdateMailbox.Forbidden ->
                scene.showConfirmPasswordDialog(emailDetailUIObserver)
        }
    }

    private fun onSelectedLabelsLoaded(result: EmailDetailResult.GetSelectedLabels) {
        when (result) {
            is EmailDetailResult.GetSelectedLabels.Success -> {
                scene.onFetchedSelectedLabels(result.selectedLabels,
                        result.allLabels)
            }

            is EmailDetailResult.GetSelectedLabels.Failure -> {
                scene.showError(UIMessage(R.string.error_getting_labels))
            }
        }
    }

    private fun onUpdatedLabels(result: EmailDetailResult.UpdateEmailThreadsLabelsRelations) {

        when(result) {
            is EmailDetailResult.UpdateEmailThreadsLabelsRelations.Success ->  {
                model.threadPreview.isStarred = result.selectedLabels.contains(Label.defaultItems.starred)
                scene.notifyLabelsChanged(result.selectedLabels)
            } else -> {
                scene.showError(UIMessage(R.string.error_updating_labels))
            }
        }
    }

    private fun onUpdateUnreadStatus(result: EmailDetailResult.UpdateUnreadStatus){
        when(result) {
            is EmailDetailResult.UpdateUnreadStatus.Success ->  {
                val message = ActivityMessage.UpdateUnreadStatusThread(result.threadId, result.unread)
                host.exitToScene(
                        params = MailboxParams(),
                        activityMessage = message,
                        forceAnimation = false)
            }
            is EmailDetailResult.UpdateUnreadStatus.Failure -> {
                    scene.showError(UIMessage(R.string.error_updating_status))
            }
            is EmailDetailResult.UpdateUnreadStatus.Unauthorized -> {
                generalDataSource.submitRequest(GeneralRequest.DeviceRemoved(false))
            }
            is EmailDetailResult.UpdateUnreadStatus.Forbidden -> {
                scene.showConfirmPasswordDialog(emailDetailUIObserver)
            }
        }
    }

    private fun onMoveEmailThread(result: EmailDetailResult.MoveEmailThread){
        when(result) {
            is EmailDetailResult.MoveEmailThread.Success ->  {
                val message = ActivityMessage.MoveThread(result.threadId)
                host.exitToScene(
                        params = MailboxParams(),
                        activityMessage = message,
                        forceAnimation = false)
            }
            is EmailDetailResult.MoveEmailThread.Failure -> {
                    scene.showError(UIMessage(R.string.error_moving_emails))
            }
            is EmailDetailResult.MoveEmailThread.Unauthorized -> {
                generalDataSource.submitRequest(GeneralRequest.DeviceRemoved(false))
            }
            is EmailDetailResult.MoveEmailThread.Forbidden -> {
                scene.showConfirmPasswordDialog(emailDetailUIObserver)
            }
        }
    }

    private fun onUnsendEmail(result: EmailDetailResult.UnsendFullEmailFromEmailId) {
        when (result) {
            is EmailDetailResult.UnsendFullEmailFromEmailId.Success -> {
                if(result.position > -1){
                    model.emails[result.position].isUnsending = false
                    scene.notifyFullEmailChanged(result.position)
                }
                setEmailAtPositionAsUnsend(result.position)
            }

            is EmailDetailResult.UnsendFullEmailFromEmailId.Failure -> {
                if (result.position > -1) {
                    model.emails[result.position].isUnsending = false
                    scene.notifyFullEmailChanged(result.position)
                }
                scene.showError(result.message)
            }
            is EmailDetailResult.UnsendFullEmailFromEmailId.Unauthorized -> {
                generalDataSource.submitRequest(GeneralRequest.DeviceRemoved(false))
            }
            is EmailDetailResult.UnsendFullEmailFromEmailId.Forbidden -> {
                scene.showConfirmPasswordDialog(emailDetailUIObserver)
            }
        }
    }

    private fun setEmailAtPositionAsUnsend(position: Int) {
        val fullEmail = model.emails[position]
        fullEmail.email.delivered = DeliveryTypes.UNSEND
        for(file in fullEmail.files){
            file.status = 0
        }
        scene.notifyFullEmailChanged(position)

        val latestEmailWasUpdated = position == model.emails.size - 1
        if (latestEmailWasUpdated) {

            model.threadPreview = model.threadPreview.copy(
                    deliveryStatus = DeliveryTypes.UNSEND)
        }
    }

    private fun downloadFile(emailId: Long, fileToken: String, fileKey: String?){
        updateAttachmentProgress(emailId, fileToken, 0)
        dataSource.submitRequest(EmailDetailRequest.DownloadFile(fileToken = fileToken,
                emailId = emailId, fileKey = fileKey))
    }

    private fun onDownloadedFile(result: EmailDetailResult){
        when(result){
            is EmailDetailResult.DownloadFile.Success -> {
                updateAttachmentProgress(result.emailId, result.filetoken, 100)
                openFile(result.filepath)
            }
            is EmailDetailResult.DownloadFile.Failure -> {
                scene.showError(UIMessage(R.string.error_downloading_file))
            }
            is EmailDetailResult.DownloadFile.Progress -> {
                updateAttachmentProgress(result.emailId, result.filetoken, result.progress)
            }
            is EmailDetailResult.DownloadFile.Unauthorized -> {
                generalDataSource.submitRequest(GeneralRequest.DeviceRemoved(false))
            }
            is EmailDetailResult.DownloadFile.Forbidden -> {
                scene.showConfirmPasswordDialog(emailDetailUIObserver)
            }
        }
    }

    private fun onReadEmails(result: EmailDetailResult.ReadEmails){
        when(result){
            is EmailDetailResult.ReadEmails.Success -> {
                if(model.doReply){
                    emailHolderEventListener.onReplyBtnClicked()
                }
            }
        }
    }

    private fun updateAttachmentProgress(emailId: Long, filetoken: String, progress: Int){
        val emailIndex = model.emails.indexOfFirst { it.email.id == emailId }
        if (emailIndex < 0) return
        val attachmentIndex = model.emails[emailIndex].files.indexOfFirst { it.token == filetoken }
        if (attachmentIndex < 0) return
        if(model.fileDetails[emailId]!![attachmentIndex].progress != 100)
            model.fileDetails[emailId]!![attachmentIndex].progress = progress
        scene.updateAttachmentProgress(emailIndex, attachmentIndex)
    }

    private fun openFile(filepath: String){
        val mimeType = FileUtils.getMimeType(filepath)
        val params = ExternalActivityParams.FilePresent(filepath, mimeType)
        host.launchExternalActivityForResult(params)
    }

    private val onMoveThreadsListener = object : OnMoveThreadsListener {

        override fun onMoveToInboxClicked() {
            moveEmailThread(Label.LABEL_INBOX)
        }

        override fun onMoveToSpamClicked() {
            moveEmailThread(Label.LABEL_SPAM)
        }

        override fun onMoveToTrashClicked() {
            moveEmailThread(Label.LABEL_TRASH)
        }
    }

    private val onDeleteThreadListener = object : OnDeleteThreadListener {
        override fun onDeleteConfirmed() {
            moveEmailThread(chosenLabel = null)
        }
    }

    private val onDeleteEmailListener = object : OnDeleteEmailListener {
        override fun onDeleteConfirmed(fullEmail: FullEmail) {
            moveEmail(fullEmail, null)
        }
    }

    private val emailHolderEventListener = object : FullEmailListAdapter.OnFullEmailEventListener{
        override fun onStarredButtonPressed(isStarred: Boolean) {
            val selectedLabels = SelectedLabels()
            val nonSelectedLabels = SelectedLabels()
            val labelsWithoutFilter = model.emails.flatMap { it.labels }.toMutableList()
            val labels = if(isStarred){
                labelsWithoutFilter.add(Label.defaultItems.starred)
                labelsWithoutFilter
            }
            else{
                labelsWithoutFilter.filter { it.id != Label.defaultItems.starred.id }
            }
            selectedLabels.addMultipleSelected(labels.toSet().map { LabelWrapper(it) })
            updateThreadLabelsRelation(selectedLabels)
        }

        override fun onAttachmentSelected(emailPosition: Int, attachmentPosition: Int) {
            if (!host.checkPermissions(BaseActivity.RequestCode.writeAccess.ordinal,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE)){
                return
            }
            val email = model.emails[emailPosition]
            val attachment = email.files[attachmentPosition]
            if(attachment.status != 0) {
                downloadFile(email.email.id, attachment.token, email.fileKey)
            }
        }

        override fun onUnsendEmail(fullEmail: FullEmail, position: Int) {
            val req = EmailDetailRequest.UnsendFullEmailFromEmailId(
                    position = position,
                    emailId = fullEmail.email.id)
            fullEmail.isUnsending = true
            scene.notifyFullEmailChanged(position)
            dataSource.submitRequest(req)
        }
        override fun onForwardBtnClicked() {
            val type = ComposerType.Forward(originalId = model.emails.last().email.id,
                    threadPreview = model.threadPreview, currentLabel = model.currentLabel)
            host.goToScene(ComposerParams(type), false)
        }

        override fun onReplyBtnClicked() {
            val type = ComposerType.Reply(originalId = model.emails.last().email.id,
                    threadPreview = model.threadPreview, currentLabel = model.currentLabel)
            host.goToScene(ComposerParams(type), false)
        }

        override fun onReplyAllBtnClicked() {
            val type = ComposerType.ReplyAll(originalId = model.emails.last().email.id,
                    threadPreview = model.threadPreview, currentLabel = model.currentLabel)
            host.goToScene(ComposerParams(type), false)
        }

        override fun ontoggleViewOpen(fullEmail: FullEmail, position: Int, viewOpen: Boolean) {
            fullEmail.viewOpen = viewOpen
            scene.notifyFullEmailChanged(position)
        }

        override fun onReplyOptionSelected(fullEmail: FullEmail, position: Int, all: Boolean) {
            val type = ComposerType.Reply(originalId = fullEmail.email.id,
                    threadPreview = model.threadPreview, currentLabel = model.currentLabel)
            host.goToScene(ComposerParams(type), false)
        }

        override fun onReplyAllOptionSelected(fullEmail: FullEmail, position: Int, all: Boolean) {
            val type = ComposerType.ReplyAll(originalId = fullEmail.email.id,
                    threadPreview = model.threadPreview, currentLabel = model.currentLabel)
            host.goToScene(ComposerParams(type), false)
        }

        override fun onForwardOptionSelected(fullEmail: FullEmail, position: Int, all: Boolean) {
            val type = ComposerType.Forward(originalId = fullEmail.email.id,
                    threadPreview = model.threadPreview, currentLabel = model.currentLabel)
            host.goToScene(ComposerParams(type), false)
        }

        override fun onToggleReadOption(fullEmail: FullEmail, position: Int, markAsRead: Boolean) {
            dataSource.submitRequest(EmailDetailRequest.UpdateUnreadStatus(
                    threadId = model.threadId,
                    updateUnreadStatus = true,
                    currentLabel = model.currentLabel))
        }

        override fun onDeleteOptionSelected(fullEmail: FullEmail, position: Int) {
            if(!fullEmail.labels.contains(Label.defaultItems.trash))
                moveEmail(fullEmail, Label.LABEL_TRASH)
            else
                deleteSelectedEmail4Ever(fullEmail)
        }

        override fun onSpamOptionSelected(fullEmail: FullEmail, position: Int) {
            moveEmail(fullEmail, Label.LABEL_SPAM)
        }

        override fun onContinueDraftOptionSelected(fullEmail: FullEmail) {
            val type = ComposerType.Draft(draftId = fullEmail.email.id,
                    threadPreview = model.threadPreview, currentLabel = model.currentLabel)
            host.goToScene(ComposerParams(type), false)
        }
    }

    private fun readEmails(emails: List<FullEmail>) {
        val emailIds = emails.map { it.email.id }
        val metadataKeys = emails.map { it.email.metadataKey }

        dataSource.submitRequest(EmailDetailRequest.ReadEmails(
                emailIds = emailIds,
                metadataKeys = metadataKeys
        ))


    }

    private fun onFullEmailsLoaded(result: EmailDetailResult.LoadFullEmailsFromThreadId){
        when (result) {
            is EmailDetailResult.LoadFullEmailsFromThreadId.Success -> {
                if (result.fullEmailList.isEmpty()) {
                    host.exitToScene(
                            params = MailboxParams(),
                            activityMessage = ActivityMessage.UpdateMailBox(),
                            forceAnimation = false)
                } else {
                    val lastEmail = result.fullEmailList.last().email
                    model.threadPreview = model.threadPreview.copy(
                            unread = false,
                            count = result.fullEmailList.size,
                            bodyPreview = lastEmail.preview,
                            deliveryStatus = lastEmail.delivered)
                    model.emails.addAll(result.fullEmailList)
                    val fullEmailsList = VirtualList.Map(result.fullEmailList, { t -> t })
                    result.fullEmailList.forEach { fullEmail ->
                        model.fileDetails[fullEmail.email.id] = fullEmail.files.map { FileDetail(it) }
                    }

                    scene.attachView(
                            fullEmailList = fullEmailsList,
                            fullEmailEventListener = emailHolderEventListener,
                            fileDetailList = model.fileDetails,
                            observer = emailDetailUIObserver)

                    readEmails(result.fullEmailList)
                }
            }

            is EmailDetailResult.LoadFullEmailsFromThreadId.Failure -> {
                scene.showError(UIMessage(R.string.error_getting_email))
            }
        }
    }

    private fun loadEmails() {
        val req = EmailDetailRequest.LoadFullEmailsFromThreadId(
                threadId = model.threadId, currentLabel = model.currentLabel)

        dataSource.submitRequest(req)
    }

    override fun onStart(activityMessage: ActivityMessage?): Boolean {
        dataSource.listener = dataSourceListener
        generalDataSource.listener = remoteChangeDataSourceListener
        websocketEvents.setListener(webSocketEventListener)

        if (model.emails.isEmpty())
            loadEmails()

        keyboard.hideKeyboard()
        return false
    }

    override fun onStop() {
        dataSource.listener = null
        websocketEvents.clearListener(webSocketEventListener)
    }

    override fun onBackPressed(): Boolean {
        host.exitToScene(
                params = MailboxParams(),
                activityMessage = ActivityMessage.UpdateThreadPreview(model.threadPreview),
                forceAnimation = false
        )
        return true
    }

    override fun onMenuChanged(menu: IHostActivity.IActivityMenu) {}

    private fun removeCurrentLabelThread() {
        val req = EmailDetailRequest.UpdateEmailThreadsLabelsRelations(
                threadId = model.threadId,
                selectedLabels = SelectedLabels(),
                currentLabel = model.currentLabel,
                removeCurrentLabel = true)

        dataSource.submitRequest(req)
    }

    private fun deleteThread() {
        moveEmailThread(Label.LABEL_TRASH)
    }

    private fun updateUnreadStatusThread(){
        dataSource.submitRequest(EmailDetailRequest.UpdateUnreadStatus(
                threadId = model.threadId,
                updateUnreadStatus = true,
                currentLabel = model.currentLabel))
    }

    override fun onOptionsItemSelected(itemId: Int) {
        when(itemId) {
            R.id.mailbox_archive_selected_messages -> removeCurrentLabelThread()
            R.id.mailbox_delete_selected_messages -> deleteThread()
            R.id.mailbox_delete_selected_messages_4ever -> deleteSelectedEmailThreads4Ever()
            R.id.mailbox_not_spam -> removeCurrentLabelThread()
            R.id.mailbox_not_trash -> removeCurrentLabelThread()
            R.id.mailbox_spam -> moveEmailThread(Label.LABEL_SPAM)
            R.id.mailbox_message_toggle_read -> updateUnreadStatusThread()
            R.id.mailbox_move_to -> {
                scene.showDialogMoveTo(onMoveThreadsListener)
            }
            R.id.mailbox_add_labels -> {
                showLabelsDialog()
            }
        }
    }

    override fun requestPermissionResult(requestCode: Int, permissions: Array<out String>,
                                         grantResults: IntArray) {
        if (requestCode != BaseActivity.RequestCode.writeAccess.ordinal) return
        val indexOfPermission = permissions.indexOfFirst { it == Manifest.permission.WRITE_EXTERNAL_STORAGE }
        if (indexOfPermission < 0) return
        if (grantResults[indexOfPermission] != PackageManager.PERMISSION_GRANTED)
            scene.showError(UIMessage(R.string.permission_filepicker_rationale))
    }

    private fun showLabelsDialog() {
        val req = EmailDetailRequest.GetSelectedLabels(model.threadId)
        dataSource.submitRequest(req)
        scene.showDialogLabelsChooser(LabelDataHandler(this))
    }

    fun moveEmail(fullEmail: FullEmail, chosenLabel: String?){

        val req = EmailDetailRequest.MoveEmail(
                emailId = fullEmail.email.id,
                chosenLabel = chosenLabel,
                currentLabel = model.currentLabel)

        dataSource.submitRequest(req)
    }

    fun updateThreadLabelsRelation(selectedLabels: SelectedLabels) {

        val req = EmailDetailRequest.UpdateEmailThreadsLabelsRelations(
                threadId = model.threadId,
                selectedLabels = selectedLabels,
                currentLabel = model.currentLabel,
                removeCurrentLabel = false)

        dataSource.submitRequest(req)

    }

    private fun moveEmailThread(chosenLabel: String?) {
        val req = EmailDetailRequest.MoveEmailThread(
                threadId = model.threadId,
                chosenLabel = chosenLabel,
                currentLabel = model.currentLabel)

        dataSource.submitRequest(req)
    }

    private fun deleteSelectedEmailThreads4Ever() {
        scene.showDialogDeleteThread(onDeleteThreadListener)
    }

    private fun deleteSelectedEmail4Ever(fullEmail: FullEmail) {
        scene.showDialogDeleteEmail(onDeleteEmailListener, fullEmail)
    }

    override val menuResourceId: Int?
        get() = when {
            model.currentLabel == Label.defaultItems.draft -> R.menu.mailbox_menu_multi_mode_read_draft
            model.currentLabel == Label.defaultItems.spam -> R.menu.mailbox_menu_multi_mode_read_spam
            model.currentLabel == Label.defaultItems.trash -> R.menu.mailbox_menu_multi_mode_read_trash
            model.currentLabel.id < 0 -> R.menu.mailbox_menu_multi_mode_read_allmail
            else -> R.menu.mailbox_menu_multi_mode_read
        }

    private fun findEmailPositionByEmailId(emailId: Long): Int {
        return model.emails.indexOfFirst { it.email.id == emailId }
    }

    private fun markEmailAtPositionAsOpened(position: Int) {
        val fullEmail = model.emails[position]
        fullEmail.email.delivered = DeliveryTypes.READ
        scene.notifyFullEmailChanged(position)

        val latestEmailWasUpdated = position == model.emails.size - 1
        if (latestEmailWasUpdated)
            model.threadPreview = model.threadPreview.copy(
                    deliveryStatus = DeliveryTypes.READ)
    }

    private val webSocketEventListener = object : WebSocketEventListener {
        override fun onDeviceDataUploaded(key: String, dataAddress: String, authorizerId: Int) {

        }

        override fun onDeviceLinkAuthDeny() {

        }

        override fun onDeviceLinkAuthRequest(untrustedDeviceInfo: UntrustedDeviceInfo) {
            host.runOnUiThread(Runnable {
                scene.showLinkDeviceAuthConfirmation(untrustedDeviceInfo)
            })
        }

        override fun onDeviceLinkAuthAccept(linkStatusData: LinkStatusData) {

        }

        override fun onKeyBundleUploaded(deviceId: Int) {

        }

        override fun onNewEvent() {
            generalDataSource.submitRequest(GeneralRequest.UpdateMailbox(model.currentLabel, 1))
        }

        override fun onRecoveryEmailChanged(newEmail: String) {

        }

        override fun onRecoveryEmailConfirmed() {

        }

        override fun onDeviceLocked() {
            host.runOnUiThread(Runnable {
                scene.showConfirmPasswordDialog(emailDetailUIObserver)
            })
        }

        override fun onDeviceRemoved() {
            generalDataSource.submitRequest(GeneralRequest.DeviceRemoved(false))
        }

        override fun onError(uiMessage: UIMessage) {
            scene.showError(uiMessage)
        }
    }
}
