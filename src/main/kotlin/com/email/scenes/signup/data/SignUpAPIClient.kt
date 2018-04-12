package com.email.scenes.signup.data

import com.email.api.ApiCall
import com.email.signal.PreKeyBundleShareData
import com.email.scenes.signup.IncompleteAccount
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Created by sebas on 2/26/18.
 */

interface SignUpAPIClient {

    fun createUser(
            account: IncompleteAccount,
            keybundle : PreKeyBundleShareData.UploadBundle)
            : String

    class Default : SignUpAPIClient {

        override fun createUser(
                account: IncompleteAccount,
                keybundle : PreKeyBundleShareData.UploadBundle
        ): String {
            val request = ApiCall.createUser(
                    recipientId = account.username,
                    name = account.name,
                    password = account.password,
                    recoveryEmail = account.recoveryEmail,
                    keyBundle = keybundle
            )
            return ApiCall.executeRequest(request)
        }
    }
}
