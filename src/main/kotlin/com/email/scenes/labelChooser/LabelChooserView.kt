package com.email.scenes.labelChooser

import android.support.v7.widget.RecyclerView
import com.email.androidui.labelwrapper.LabelWrapperListView
import com.email.androidui.labelwrapper.LabelWrapperRecyclerView
import com.email.R
import android.view.View
import android.widget.ProgressBar
import com.email.scenes.labelChooser.data.LabelWrapper
import com.email.utils.VirtualList


/**
 * Created by sebas on 2/2/18.
 */

interface LabelChooserScene: LabelWrapperListView {
    fun attachView(labelThreadEventListener: LabelWrapperAdapter.OnLabelWrapperEventListener)
    fun onFetchedLabels()

    class LabelChooserView(private val labelChooserView: View,
                           private val labelsList: VirtualList<LabelWrapper>)
        : LabelChooserScene {

        private lateinit var labelWrapperRecyclerView: LabelWrapperRecyclerView

        var labelWrapperListener: LabelWrapperAdapter.OnLabelWrapperEventListener? = null
            set(value) {
                labelWrapperRecyclerView.setLabelWrapperListener(value)
                field = value
            }

        override fun notifyLabelWrapperSetChanged() {
            labelWrapperRecyclerView.notifyLabelWrapperSetChanged()
        }

        override fun notifyLabelWrapperRemoved(position: Int) {
            labelWrapperRecyclerView.notifyLabelWrapperRemoved(position)
        }

        override fun notifyLabelWrapperRangeInserted(positionStart: Int, itemCount: Int) {
            labelWrapperRecyclerView.notifyLabelWrapperRangeInserted(positionStart, itemCount)
        }

        override fun attachView(labelThreadEventListener: LabelWrapperAdapter.OnLabelWrapperEventListener) {
            val recycler = labelChooserView.findViewById<RecyclerView>(R.id.label_recycler)
            labelWrapperRecyclerView = LabelWrapperRecyclerView(recycler,
                    labelThreadEventListener,
                    labelsList)
            this.labelWrapperListener= labelThreadEventListener
        }

        override fun notifyLabelWrapperChanged(position: Int) {
            labelWrapperRecyclerView.notifyLabelWrapperChanged(position)
        }

        override fun onFetchedLabels() {
            notifyLabelWrapperSetChanged()
            val progressBar = labelChooserView.findViewById<ProgressBar>(R.id.label_chooser_progress_loading)
            progressBar.visibility = View.GONE

            val recycler = labelChooserView.findViewById<RecyclerView>(R.id.label_recycler)
            recycler.visibility = View.VISIBLE
        }
    }

}
