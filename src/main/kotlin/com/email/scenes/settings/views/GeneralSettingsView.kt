package com.email.scenes.settings.views

import android.view.View
import com.email.R
import com.email.scenes.settings.SettingsUIObserver
import com.email.utils.ui.TabView

class GeneralSettingsView(view: View, title: String): TabView(view, title) {

    private lateinit var settingsProfileName: View
    private lateinit var settingsSignature: View

    private var settingsUIObserver: SettingsUIObserver? = null

    override fun onCreateView(){

        settingsProfileName = view.findViewById(R.id.settings_profile_name)
        settingsSignature = view.findViewById(R.id.settings_signature)

        setButtonListeners()
    }

    fun setExternalListeners(settingsUIObserver: SettingsUIObserver?){
        this.settingsUIObserver = settingsUIObserver
    }

    private fun setButtonListeners(){
        settingsProfileName.setOnClickListener {
            settingsUIObserver?.onProfileNameClicked()
        }
        settingsSignature.setOnClickListener {
            settingsUIObserver?.onSignatureOptionClicked()
        }
    }

}