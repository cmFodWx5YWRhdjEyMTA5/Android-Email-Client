package com.criptext.mail.scenes.signin.data

import android.accounts.NetworkErrorException
import com.criptext.mail.R
import com.criptext.mail.api.HttpClient
import com.criptext.mail.api.HttpErrorHandlingHelper
import com.criptext.mail.bgworker.BackgroundWorker
import com.criptext.mail.bgworker.ProgressReporter
import com.criptext.mail.db.KeyValueStorage
import com.criptext.mail.db.SignInLocalDB
import com.criptext.mail.db.dao.AccountDao
import com.criptext.mail.db.dao.SignUpDao
import com.criptext.mail.db.models.Account
import com.criptext.mail.scenes.signup.data.StoreAccountTransaction
import com.criptext.mail.services.MessagingInstance
import com.criptext.mail.signal.SignalKeyGenerator
import com.criptext.mail.utils.UIMessage
import com.github.kittinunf.result.Result
import com.github.kittinunf.result.flatMap
import com.github.kittinunf.result.mapError
import org.json.JSONException
import org.json.JSONObject

/**
 * Created by sebas on 2/28/18.
 */
class AuthenticateUserWorker(
        private val db: SignInLocalDB,
        signUpDao: SignUpDao,
        httpClient: HttpClient,
        private val accountDao: AccountDao,
        private val keyValueStorage: KeyValueStorage,
        private val keyGenerator: SignalKeyGenerator,
        private val username: String,
        private val password: String,
        private val messagingInstance: MessagingInstance,
        override val publishFn: (SignInResult.AuthenticateUser) -> Unit)
    : BackgroundWorker<SignInResult.AuthenticateUser> {

    private val apiClient = SignInAPIClient(httpClient)
    private val storeAccountTransaction = StoreAccountTransaction(signUpDao, keyValueStorage)
    override val canBeParallelized = false

    override fun catchException(ex: Exception): SignInResult.AuthenticateUser {
        val message = createErrorMessage(ex)
        return SignInResult.AuthenticateUser.Failure(message, ex)
    }

    private val shouldKeepData: Boolean by lazy {
        keyValueStorage.getString(KeyValueStorage.StringKey.LastLoggedUser, "") == username
    }

    private fun authenticateUser(): String {
        val responseString = apiClient.authenticateUser(username, password)
        keyValueStorage.putString(KeyValueStorage.StringKey.SignInSession, responseString)
        return responseString
    }

    private fun getSignInSession(): SignInSession {
        var storedValue = keyValueStorage.getString(KeyValueStorage.StringKey.SignInSession, "")
        val lastLoggedUser = keyValueStorage.getString(KeyValueStorage.StringKey.LastLoggedUser, "")
        if(lastLoggedUser.isNotEmpty()) {
            if(!shouldKeepData){
                keyValueStorage.clearAll()
                db.deleteDatabase()
            }
            storedValue = ""
        }
        val jsonString = if (storedValue.isEmpty()) authenticateUser() else storedValue
        val jsonObject = JSONObject(jsonString)
        return SignInSession.fromJSON(jsonObject)

    }

    fun signInOperation(): Result<SignInSession, Exception> =
        Result.of { getSignInSession() }
                .mapError(HttpErrorHandlingHelper.httpExceptionsToNetworkExceptions)

    val signalRegistrationOperation
            : (SignInSession) ->
            Result<Pair<SignalKeyGenerator.RegistrationBundles, Account>, Exception> = {
        signInSession ->
        Result.of {
            val registrationBundles = keyGenerator.register(username, signInSession.deviceId)
            val privateBundle = registrationBundles.privateBundle
            val account = Account(recipientId = username, deviceId = signInSession.deviceId,
                    name = signInSession.name, registrationId = privateBundle.registrationId,
                    identityKeyPairB64 = privateBundle.identityKeyPair, jwt = signInSession.token,
                    signature = "")
            Pair(registrationBundles, account)
        }
    }

    val storeAccountOperation
            : (Pair<SignalKeyGenerator.RegistrationBundles, Account>) -> Result<Unit, Exception> = {
        (registrationBundles, account) ->
        Result.of {
            val postKeyBundleStep = Runnable {
                account.jwt = apiClient.postKeybundle(bundle = registrationBundles.uploadBundle,
                        jwt = account.jwt)
                if(messagingInstance.token != null)
                    apiClient.putFirebaseToken(messagingInstance.token ?: "", account.jwt)
                accountDao.updateJwt(username, account.jwt)
            }

            storeAccountTransaction.run(account = account,
                                        keyBundle = registrationBundles.privateBundle,
                                        extraSteps = postKeyBundleStep, keepData = shouldKeepData)
        }

    }

    override fun work(reporter: ProgressReporter<SignInResult.AuthenticateUser>): SignInResult.AuthenticateUser? {

        val result = signInOperation()
                .flatMap(signalRegistrationOperation)
                .flatMap(storeAccountOperation)

        return when(result) {
            is Result.Success -> {
                SignInResult.AuthenticateUser.Success()
            }
            is Result.Failure -> {
                SignInResult.AuthenticateUser.Failure(
                        message = createErrorMessage(result.error),
                        exception = result.error)
            }
        }
    }

    override fun cancel() {
        TODO("not implemented") //To change body of created functions use CRFile | Settings | CRFile Templates.
    }

    private val createErrorMessage: (ex: Exception) -> UIMessage = { ex ->
        when (ex) {
            is NetworkErrorException ->
                UIMessage(resId = R.string.login_network_error_exception)
            is JSONException ->
                    UIMessage(resId = R.string.login_json_error_exception)
            else -> UIMessage(resId = R.string.login_fail_try_again_error_exception)
        }
    }
}
