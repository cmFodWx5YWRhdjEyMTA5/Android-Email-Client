package com.criptext.mail.scenes.settings.data

import com.criptext.mail.scenes.settings.devices.DeviceItem
import com.criptext.mail.utils.DateUtils
import org.json.JSONObject

data class UserSettingsData(val devices: List<DeviceItem>, val recoveryEmail: String,
                            val recoveryEmailConfirmationState: Boolean, val hasTwoFA: Boolean){
    companion object {
        fun fromJSON(metadataString: String): UserSettingsData {
            val metadataJson = JSONObject(metadataString)
            val devicesData = metadataJson.getJSONArray("devices")
            val devices = (0..(devicesData.length()-1))
                    .map {
                        val json = devicesData.getJSONObject(it)
                        val jsonDate = json.getJSONObject("lastActivity").optString("date")
                        val date = if(!jsonDate.isNullOrEmpty())
                            DateUtils.getDateFromString(jsonDate, null)
                        else
                            null
                        DeviceItem(json.getInt("deviceId"),
                                json.getInt("deviceType"), json.getString("deviceFriendlyName"),
                                json.getString("deviceName"), false, date)
                    }
            val general = metadataJson.getJSONObject("general")
            val recoveryEmail = general.getString("recoveryEmail")
            val recoveryEmailConfirmationState = general.getInt("recoveryEmailConfirmed") == 1
            val twoFactorAuth = general.getInt("twoFactorAuth") == 1
            return UserSettingsData(devices.filter { it.isCurrent } + devices.filter { !it.isCurrent }, recoveryEmail,
                    recoveryEmailConfirmationState, twoFactorAuth)
        }
    }
}