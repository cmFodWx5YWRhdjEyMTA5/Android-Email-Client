package com.criptext.mail.scenes.signin

import android.os.Handler
import com.criptext.mail.IHostActivity
import com.criptext.mail.R
import com.criptext.mail.api.models.UntrustedDeviceInfo
import com.criptext.mail.db.KeyValueStorage
import com.criptext.mail.db.models.ActiveAccount
import com.criptext.mail.scenes.ActivityMessage
import com.criptext.mail.scenes.SceneController
import com.criptext.mail.scenes.params.MailboxParams
import com.criptext.mail.scenes.params.SignUpParams
import com.criptext.mail.scenes.signin.data.*
import com.criptext.mail.scenes.signin.holders.ConnectionHolder
import com.criptext.mail.scenes.signin.holders.LoginValidationHolder
import com.criptext.mail.scenes.signin.holders.SignInLayoutState
import com.criptext.mail.utils.KeyboardManager
import com.criptext.mail.utils.UIMessage
import com.criptext.mail.utils.generaldatasource.data.GeneralDataSource
import com.criptext.mail.utils.generaldatasource.data.GeneralRequest
import com.criptext.mail.utils.generaldatasource.data.GeneralResult
import com.criptext.mail.utils.sha256
import com.criptext.mail.validation.AccountDataValidator
import com.criptext.mail.validation.FormData
import com.criptext.mail.validation.ProgressButtonState
import com.criptext.mail.websocket.*

/**
 * Created by sebas on 2/15/18.
 */

class SignInSceneController(
        private val webSocketFactory: CriptextWebSocketFactory,
        private val model: SignInSceneModel,
        private val scene: SignInScene,
        private val host: IHostActivity,
        private val storage: KeyValueStorage,
        private val generalDataSource: GeneralDataSource,
        private val dataSource: SignInDataSource,
        private val keyboard: KeyboardManager): SceneController() {

    override val menuResourceId: Int? = null

    private var tempWebSocket: WebSocketEventPublisher? = null
    private var webSocket: WebSocketEventPublisher? = null

    private val dataSourceListener = { result: SignInResult ->
        when (result) {
            is SignInResult.AuthenticateUser -> onUserAuthenticated(result)
            is SignInResult.CheckUsernameAvailability -> onCheckUsernameAvailability(result)
            is SignInResult.LinkBegin -> onLinkBegin(result)
            is SignInResult.LinkAuth -> onLinkAuth(result)
            is SignInResult.CreateSessionFromLink -> onCreateSessionFromLink(result)
            is SignInResult.LinkDataReady -> onLinkDataReady(result)
            is SignInResult.LinkData -> onLinkData(result)
            is SignInResult.LinkStatus -> onLinkStatus(result)
        }
    }

    private val generalDataSourceListener = { result: GeneralResult ->
        when (result) {
            is GeneralResult.ResetPassword -> onForgotPassword(result)
            is GeneralResult.DeviceRemoved -> onDeviceRemoved(result)
        }
    }

    private fun onAuthenticationFailed(result: SignInResult.AuthenticateUser.Failure) {
        scene.showError(result.message)

        val currentState = model.state
        if (currentState is SignInLayoutState.InputPassword) {
            model.state = currentState.copy(password = "",
                    buttonState = ProgressButtonState.disabled)
            scene.resetInput()
        }
    }

    private fun onForgotPassword(result: GeneralResult.ResetPassword){
        scene.toggleForgotPasswordClickable(true)
        when(result){
            is GeneralResult.ResetPassword.Success -> {
                scene.showResetPasswordDialog(result.email)
            }
            is GeneralResult.ResetPassword.Failure -> scene.showError(result.message)
        }
    }

    private fun onDeviceRemoved(result: GeneralResult.DeviceRemoved){
        when(result){
            is GeneralResult.DeviceRemoved.Success -> {
                val currentState = model.state as SignInLayoutState.WaitForApproval
                model.state = SignInLayoutState.Start(currentState.username, firstTime = false)
                resetLayout()
            }
        }
    }

    private fun onCheckUsernameAvailability(result: SignInResult.CheckUsernameAvailability) {
        when (result) {
            is SignInResult.CheckUsernameAvailability.Success -> {
                if(result.userExists) {
                    keyboard.hideKeyboard()

                    //LINK DEVICE FEATURE
                    model.state = SignInLayoutState.LoginValidation(username = result.username,
                            hasTwoFA = model.hasTwoFA)
                    dataSource.submitRequest(SignInRequest.LinkBegin(result.username))
                }
                else{
                    scene.drawInputError(UIMessage(R.string.username_doesnt_exist))
                }
            }
            is SignInResult.CheckUsernameAvailability.Failure -> scene.showError(UIMessage(R.string.error_checking_availability))
        }
        scene.setSubmitButtonState(ProgressButtonState.enabled)
    }

    private fun onLinkBegin(result: SignInResult.LinkBegin) {
        when (result) {
            is SignInResult.LinkBegin.Success -> {

                val currentState = model.state as SignInLayoutState.LoginValidation
                model.ephemeralJwt = result.ephemeralJwt
                model.hasTwoFA = result.hasTwoFA
                if(model.hasTwoFA){
                    onAcceptPasswordLogin(currentState.username)
                }else{
                    scene.initLayout(model.state, uiObserver)
                    handleNewTemporalWebSocket()
                    dataSource.submitRequest(SignInRequest.LinkAuth(currentState.username,
                            model.ephemeralJwt))
                }
            }
            is SignInResult.LinkBegin.Failure -> returnToStart(result.message)
            is SignInResult.LinkBegin.NoDevicesAvailable -> {
                val currentState = model.state as SignInLayoutState.LoginValidation
                onAcceptPasswordLogin(currentState.username)
            }
        }
    }

    private fun returnToStart(message: UIMessage){
        val currentState = model.state as SignInLayoutState.LoginValidation
        model.state = SignInLayoutState.Start(currentState.username, false)
        scene.initLayout(model.state, uiObserver)
        scene.showError(message)
    }

    private fun onLinkAuth(result: SignInResult.LinkAuth) {
        scene.toggleResendClickable(true)
        when (result) {
            is SignInResult.LinkAuth.Success -> {
                model.linkDeviceState = LinkDeviceState.Auth()
                val handler = Handler()
                host.runOnUiThread(Runnable {
                    handler.postDelayed({
                        if(model.retryTimeLinkStatus < RETRY_TIMES_DEFAULT) {
                            if (model.linkDeviceState is LinkDeviceState.Auth)
                                dataSource.submitRequest(SignInRequest.LinkStatus(model.ephemeralJwt))
                            model.retryTimeLinkStatus++
                        }
                    }, RETRY_TIME_DEFAULT)
                })

            }
            is SignInResult.LinkAuth.Failure -> {
                if(model.hasTwoFA){
                    val resultData = SignInResult.AuthenticateUser.Failure(result.message,
                            result.exception)
                    onAuthenticationFailed(resultData)
                }else {
                    val currentState = model.state as SignInLayoutState.LoginValidation
                    scene.showError(UIMessage(R.string.server_error_exception))
                }
            }
        }
    }

    private fun onCreateSessionFromLink(result: SignInResult.CreateSessionFromLink) {
        when (result) {
            is SignInResult.CreateSessionFromLink.Success -> {
                scene.setLinkProgress(UIMessage(R.string.waiting_for_mailbox), WAITING_FOR_MAILBOX_PERCENTAGE)
                model.activeAccount = result.activeAccount
                stopTempWebSocket()
                handleNewWebSocket()
                val handler = Handler()
                handler.postDelayed({
                    if(model.retryTimeLinkDataReady < RETRY_TIMES_DATA_READY) {
                        if (model.linkDeviceState !is LinkDeviceState.WaitingForDownload)
                            dataSource.submitRequest(SignInRequest.LinkDataReady())
                        model.retryTimeLinkDataReady++
                    }
                }, RETRY_TIME_DEFAULT)
            }
            is SignInResult.CreateSessionFromLink.Failure -> {
                scene.showSyncRetryDialog(result)
            }
        }
    }

    private fun onLinkDataReady(result: SignInResult.LinkDataReady) {
        when (result) {
            is SignInResult.LinkDataReady.Success -> {
                if(model.linkDeviceState !is LinkDeviceState.WaitingForDownload) {
                    model.linkDeviceState = LinkDeviceState.WaitingForDownload()
                    model.key = result.key
                    model.dataAddress = result.dataAddress
                    dataSource.submitRequest(SignInRequest.LinkData(model.key, model.dataAddress,
                            model.authorizerId))
                }
            }
            is SignInResult.LinkDataReady.Failure -> {
                val handler = Handler()
                handler.postDelayed({
                    if(model.retryTimeLinkDataReady < RETRY_TIMES_DATA_READY) {
                        if (model.linkDeviceState !is LinkDeviceState.WaitingForDownload)
                            dataSource.submitRequest(SignInRequest.LinkDataReady())
                        model.retryTimeLinkDataReady++
                    }
                }, RETRY_TIME_DATA_READY)
            }
        }
    }

    private fun onLinkData(result: SignInResult.LinkData) {
        when (result) {
            is SignInResult.LinkData.Success -> {
                scene.setLinkProgress(UIMessage(R.string.sync_complete), SYNC_COMPLETE_PERCENTAGE)
                scene.startLinkSucceedAnimation()
            }
            is SignInResult.LinkData.Progress -> {
                if(result.progress > DOWNLOADING_MAILBOX_PERCENTAGE)
                    scene.disableCancelSync()
                scene.setLinkProgress(result.message, result.progress)
            }
            is SignInResult.LinkData.Failure -> {
                scene.showSyncRetryDialog(result)
            }
        }
    }

    private fun onLinkStatus(result: SignInResult.LinkStatus) {
        when (result) {
            is SignInResult.LinkStatus.Success -> {
                if(model.linkDeviceState is LinkDeviceState.Auth) {
                    model.linkDeviceState = LinkDeviceState.Accepted()
                    val currentState = model.state as SignInLayoutState.LoginValidation
                    model.name = result.linkStatusData.name
                    model.randomId = result.linkStatusData.deviceId
                    model.authorizerId = result.linkStatusData.authorizerId
                    model.authorizerType = result.linkStatusData.authorizerType
                    model.state = SignInLayoutState.WaitForApproval(currentState.username, model.authorizerType)
                    scene.initLayout(model.state, uiObserver)
                    scene.setLinkProgress(UIMessage(R.string.sending_keys), SENDING_KEYS_PERCENTAGE)
                    dataSource.submitRequest(SignInRequest.CreateSessionFromLink(name = model.name,
                            username = currentState.username,
                            randomId = model.randomId, ephemeralJwt = model.ephemeralJwt))
                }

            }
            is SignInResult.LinkStatus.Waiting -> {
                val handler = Handler()
                host.runOnUiThread(Runnable {
                    handler.postDelayed({
                        if(model.retryTimeLinkStatus < RETRY_TIMES_DEFAULT) {
                            if (model.linkDeviceState is LinkDeviceState.Auth)
                                dataSource.submitRequest(SignInRequest.LinkStatus(model.ephemeralJwt))
                            model.retryTimeLinkStatus++
                        }
                    }, RETRY_TIME_DEFAULT)
                })
            }
            is SignInResult.LinkStatus.Denied -> {
                if(model.linkDeviceState !is LinkDeviceState.Denied) {
                    model.linkDeviceState = LinkDeviceState.Denied()
                    scene.showLinkAuthError()
                }
            }
        }
    }

    private fun onUserAuthenticated(result: SignInResult.AuthenticateUser) {
        when (result) {
            is SignInResult.AuthenticateUser.Success -> {
                scene.showKeyGenerationHolder()
            }
            is SignInResult.AuthenticateUser.Failure -> onAuthenticationFailed(result)
        }
    }

    private fun onAcceptPasswordLogin(username: String){
        model.state = SignInLayoutState.InputPassword(
                username = username,
                password = "",
                buttonState = ProgressButtonState.disabled,
                hasTwoFA = model.hasTwoFA)
        scene.initLayout(model.state, uiObserver)
    }

    private val passwordLoginDialogListener = object : OnPasswordLoginDialogListener {

        override fun acceptPasswordLogin(username: String) {
            onAcceptPasswordLogin(username)
        }

        override fun cancelPasswordLogin() {
        }
    }

    private fun handleNewTemporalWebSocket(){
        tempWebSocket = webSocketFactory.createTemporalWebSocket(model.ephemeralJwt)
        tempWebSocket?.setListener(webSocketEventListener)
    }

    private fun handleNewWebSocket(){
        webSocket = webSocketFactory.createWebSocket(model.activeAccount!!.jwt)
        webSocket?.setListener(webSocketEventListener)
    }

    private fun onSignInButtonClicked(currentState: SignInLayoutState.Start) {
        val userInput = AccountDataValidator.validateUsername(currentState.username)
        when (userInput) {
            is FormData.Valid -> {
                val newRequest = SignInRequest.CheckUserAvailability(userInput.value)
                dataSource.submitRequest(newRequest)
                scene.setSubmitButtonState(ProgressButtonState.waiting)
            }
            is FormData.Error ->
                scene.drawInputError(userInput.message)
        }
    }

    private fun onSignInButtonClicked(currentState: SignInLayoutState.InputPassword) {
        if (currentState.password.isNotEmpty()) {
            val newButtonState = ProgressButtonState.waiting
            model.state = currentState.copy(buttonState = newButtonState)
            scene.setSubmitButtonState(newButtonState)

            val hashedPassword = currentState.password.sha256()
            val req = SignInRequest.AuthenticateUser(
                    username = currentState.username,
                    password = hashedPassword
            )
            dataSource.submitRequest(req)
        }
    }

    private val webSocketEventListener = object : WebSocketEventListener {
        override fun onDeviceDataUploaded(key: String, dataAddress: String, authorizerId: Int) {
            host.runOnUiThread(Runnable {
                if(model.linkDeviceState !is LinkDeviceState.WaitingForDownload) {
                    model.linkDeviceState = LinkDeviceState.WaitingForDownload()
                    model.key = key
                    model.dataAddress = dataAddress
                    model.authorizerId = authorizerId
                    dataSource.submitRequest(SignInRequest.LinkData(key, dataAddress, authorizerId))
                }
            })
        }

        override fun onDeviceLinkAuthDeny() {
            host.runOnUiThread(Runnable {
                if(model.linkDeviceState !is LinkDeviceState.Denied) {
                    model.linkDeviceState = LinkDeviceState.Denied()
                    scene.showLinkAuthError()
                }
            })
        }

        override fun onDeviceLinkAuthAccept(linkStatusData: LinkStatusData) {
            if(model.linkDeviceState is LinkDeviceState.Auth) {
                host.runOnUiThread(Runnable {

                    model.linkDeviceState = LinkDeviceState.Accepted()
                    val currentState = model.state as SignInLayoutState.LoginValidation
                    model.name = linkStatusData.name
                    model.randomId = linkStatusData.deviceId
                    model.authorizerId = linkStatusData.authorizerId
                    model.authorizerType = linkStatusData.authorizerType
                    model.state = SignInLayoutState.WaitForApproval(currentState.username, model.authorizerType)
                    scene.initLayout(model.state, uiObserver)
                    scene.setLinkProgress(UIMessage(R.string.sending_keys), SENDING_KEYS_PERCENTAGE)
                    dataSource.submitRequest(SignInRequest.CreateSessionFromLink(name = linkStatusData.name,
                            username = currentState.username,
                            randomId = linkStatusData.deviceId, ephemeralJwt = model.ephemeralJwt))
                })
            }
        }

        override fun onKeyBundleUploaded(deviceId: Int) {

        }

        override fun onDeviceLinkAuthRequest(untrustedDeviceInfo: UntrustedDeviceInfo) {

        }

        override fun onNewEvent() {

        }

        override fun onRecoveryEmailChanged(newEmail: String) {

        }

        override fun onRecoveryEmailConfirmed() {

        }

        override fun onDeviceLocked() {

        }

        override fun onDeviceRemoved() {

        }

        override fun onError(uiMessage: UIMessage) {
            scene.showError(uiMessage)
        }
    }

    private val uiObserver = object : SignInUIObserver {
        override fun onRetrySyncOk(result: SignInResult) {
            when(result){
                is SignInResult.CreateSessionFromLink -> {
                    val currentState = model.state as SignInLayoutState.LoginValidation
                    model.state = SignInLayoutState.WaitForApproval(currentState.username, model.authorizerType)
                    scene.initLayout(model.state, this)
                    scene.setLinkProgress(UIMessage(R.string.sending_keys), SENDING_KEYS_PERCENTAGE)
                    dataSource.submitRequest(SignInRequest.CreateSessionFromLink(name = model.name,
                            username = currentState.username,
                            randomId = model.randomId, ephemeralJwt = model.ephemeralJwt))
                }
                is SignInResult.LinkData -> {
                    dataSource.submitRequest(SignInRequest.LinkData(model.key, model.dataAddress, model.authorizerId))
                }
            }
        }

        override fun onRetrySyncCancel() {
            if(ActiveAccount.loadFromStorage(storage) == null){
                val currentState = model.state as SignInLayoutState.WaitForApproval
                model.state = SignInLayoutState.Start(currentState.username, firstTime = false)
                resetLayout()
            }else{
                host.exitToScene(MailboxParams(), null, false, true)
            }
        }

        override fun onCancelSync() {
            if(ActiveAccount.loadFromStorage(storage) == null){
                val currentState = model.state as? SignInLayoutState.WaitForApproval
                if(currentState != null) {
                    model.state = SignInLayoutState.Start(currentState.username, firstTime = false)
                    resetLayout()
                }
            }else{
                host.exitToScene(MailboxParams(), null, false, true)
            }
        }

        override fun onResendDeviceLinkAuth(username: String) {
            dataSource.submitRequest(SignInRequest.LinkAuth(username, model.ephemeralJwt,
                    model.realSecurePassword))
        }

        override fun onProgressHolderFinish() {
            host.goToScene(MailboxParams(), false)
        }

        override fun onBackPressed() {
            model.linkDeviceState = LinkDeviceState.Begin()
            this@SignInSceneController.onBackPressed()
        }

        override fun onSubmitButtonClicked() {
            val state = model.state
            when (state) {
                is SignInLayoutState.Start -> onSignInButtonClicked(state)
                is SignInLayoutState.InputPassword -> {
                    if(model.hasTwoFA){
                        val currentState = model.state as SignInLayoutState.InputPassword
                        model.realSecurePassword = currentState.password.sha256()
                        model.state = SignInLayoutState.LoginValidation(currentState.username, model.hasTwoFA)
                        scene.initLayout(model.state, this)
                        handleNewTemporalWebSocket()
                        dataSource.submitRequest(SignInRequest.LinkAuth(currentState.username,
                                model.ephemeralJwt, model.realSecurePassword))
                    }else{
                        onSignInButtonClicked(state)
                    }
                }
            }
        }

        override fun onForgotPasswordClick() {
            scene.toggleForgotPasswordClickable(false)
            val currentState = model.state as SignInLayoutState.InputPassword
            generalDataSource.submitRequest(GeneralRequest.ResetPassword(currentState.username))
        }

        override fun onCantAccessDeviceClick(){
            scene.showPasswordLoginDialog(
                    onPasswordLoginDialogListener = this@SignInSceneController.passwordLoginDialogListener)
        }
        override fun userLoginReady() {
            host.goToScene(MailboxParams(), false, true)
        }

        override fun toggleUsernameFocusState(isFocused: Boolean) {
        }

        override fun onPasswordChangeListener(newPassword: String) {
            val currentState = model.state
            if (currentState is SignInLayoutState.InputPassword) {
                val newButtonState = if (newPassword.isEmpty()) ProgressButtonState.disabled
                                     else ProgressButtonState.enabled
                model.state = currentState.copy(
                        password = newPassword,
                        buttonState = newButtonState)
                scene.setSubmitButtonState(state = newButtonState)
            }
        }
        override fun onUsernameTextChanged(newUsername: String) {
            model.state = SignInLayoutState.Start(username = newUsername, firstTime = false)
            val buttonState = if (newUsername.isNotEmpty()) ProgressButtonState.enabled
                              else ProgressButtonState.disabled
            scene.setSubmitButtonState(buttonState)
        }

        override fun onSignUpLabelClicked() {
            host.goToScene(SignUpParams(), false)
        }
    }

    private fun resetLayout() {
        scene.initLayout(model.state, uiObserver)
    }

    private fun stopTempWebSocket(){
        if(tempWebSocket != null) {
            tempWebSocket?.clearListener(webSocketEventListener)
            tempWebSocket?.disconnectWebSocket()
        }
    }

    override fun onStart(activityMessage: ActivityMessage?): Boolean {
        dataSource.listener = dataSourceListener
        generalDataSource.listener = generalDataSourceListener
        scene.initLayout(state = model.state, signInUIObserver = uiObserver)
        if(activityMessage != null && activityMessage is ActivityMessage.ShowUIMessage){
            scene.showError(activityMessage.message)
        }

        return false
    }

    override fun onStop() {
        scene.signInUIObserver = null
        if(webSocket != null) {
            webSocket?.clearListener(webSocketEventListener)
            webSocket?.disconnectWebSocket()
        }
    }

    override fun onBackPressed(): Boolean {
        val currentState = model.state
        return when (currentState) {
            is SignInLayoutState.Start -> true
            is SignInLayoutState.LoginValidation -> {
                model.state = SignInLayoutState.Start(currentState.username, firstTime = false)
                resetLayout()
                false
            }
            is SignInLayoutState.InputPassword -> {
                model.state = SignInLayoutState.Start(currentState.username, firstTime = false)
                resetLayout()
                false
            }
            is SignInLayoutState.WaitForApproval -> {
                false
            }
        }
    }

    override fun onMenuChanged(menu: IHostActivity.IActivityMenu) {}

    override fun onOptionsItemSelected(itemId: Int) {
    }

    override fun requestPermissionResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
    }

    interface SignInUIObserver {
        fun onSubmitButtonClicked()
        fun toggleUsernameFocusState(isFocused: Boolean)
        fun onSignUpLabelClicked()
        fun userLoginReady()
        fun onCantAccessDeviceClick()
        fun onResendDeviceLinkAuth(username: String)
        fun onPasswordChangeListener(newPassword: String)
        fun onUsernameTextChanged(newUsername: String)
        fun onForgotPasswordClick()
        fun onBackPressed()
        fun onProgressHolderFinish()
        fun onCancelSync()
        fun onRetrySyncOk(result: SignInResult)
        fun onRetrySyncCancel()
    }

    companion object {
        const val RETRY_TIME_DEFAULT = 5000L
        const val RETRY_TIME_DATA_READY = 10000L
        const val RETRY_TIMES_DEFAULT = 12
        const val RETRY_TIMES_DATA_READY = 18

        //Sync Process Percentages
        const val  SENDING_KEYS_PERCENTAGE = 10
        const val  WAITING_FOR_MAILBOX_PERCENTAGE = 40
        const val  DOWNLOADING_MAILBOX_PERCENTAGE = 70
        const val  SYNC_COMPLETE_PERCENTAGE = 100
    }
}