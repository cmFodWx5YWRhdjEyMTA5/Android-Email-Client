package com.email.scenes.signin

import android.os.AsyncTask

/**
 * Created by sebas on 2/21/18.
 */
interface ProgressSignInListener {
    fun onStart()
    fun onFinish()
}

class ProgressSignInTask(val progressSignInListener:
                         ProgressSignInListener) :
        AsyncTask<Void, Void, String>() {
    override fun doInBackground(vararg p0: Void?): String {
        Thread.sleep(2000)
        return ""
    }

    override fun onPreExecute() {
        progressSignInListener.onStart()
    }

    override fun onPostExecute(result: String?) {
        progressSignInListener.onFinish()
    }
}
