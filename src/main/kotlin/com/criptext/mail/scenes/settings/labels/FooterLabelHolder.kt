package com.criptext.mail.scenes.settings.labels

import android.support.v7.widget.RecyclerView
import android.view.View
import android.widget.Button
import com.criptext.mail.R

/**
 * Created by danieltigse on 28/6/18.
 */

class FooterLabelHolder(val view: View) : RecyclerView.ViewHolder(view) {

    private val buttonCreate: Button

    init {
        buttonCreate = view.findViewById(R.id.buttonCreateLabel) as Button
    }

    fun setOnCreateLabelClickedListener(onCreateLabelClicked: () -> Unit) {
        buttonCreate.setOnClickListener {
            onCreateLabelClicked()
        }
    }
}
