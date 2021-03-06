package com.criptext.mail.scenes.composer.data

import com.criptext.mail.api.HttpClient
import org.json.JSONArray
import org.json.JSONObject

/**
 * Created by gabriel on 3/15/18.
 */

class ComposerAPIClient(private val httpClient: HttpClient, private val token: String) {

    fun findKeyBundles(recipients: List<String>, knownAddresses: Map<String, List<Int>>): String {
        val jsonObject = JSONObject()
        jsonObject.put("recipients", JSONArray(recipients))
        jsonObject.put("knownAddresses", JSONObject(knownAddresses))
        return httpClient.post(path = "/keybundle/find", authToken = token, body = jsonObject)
    }

    fun postEmail(postEmailBody: PostEmailBody): String {
        return httpClient.post(path = "/email", authToken = token, body = postEmailBody.toJSON())
    }

}
