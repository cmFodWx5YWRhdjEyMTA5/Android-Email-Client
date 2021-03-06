package com.criptext.mail.signal

import com.criptext.mail.db.models.signal.CRPreKey
import com.criptext.mail.utils.DeviceUtils
import org.amshove.kluent.`should equal`
import org.json.JSONObject
import org.junit.Test

/**
 * Created by gabriel on 3/22/18.
 */

class PreKeyBundleShareDataTest {

    @Test
    fun `DownloadBundle should serialize and deserialize from JSON`() {
        val original = PreKeyBundleShareData.DownloadBundle(PreKeyBundleShareData(
                recipientId = "gabriel", deviceId = 1, registrationId = 125, signedPreKeyId = 564,
                signedPreKeyPublic = "ggiwt35WtoPl5", signedPreKeySignature = "aiL97Mh40Kr31Sbn",
                identityPublicKey = "ti9igMltw7Y"),
                preKey = CRPreKey(id = 5, byteString = "trey4mI0kR3dw8Iwq"))

        val serialized = original.toJSON().toString()
        val deserialized = PreKeyBundleShareData.DownloadBundle.fromJSON(JSONObject(serialized))

        deserialized `should equal` original
    }
}