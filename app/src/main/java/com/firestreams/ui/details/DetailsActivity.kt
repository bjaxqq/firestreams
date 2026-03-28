package com.firestreams.ui.details

import android.os.Bundle
import android.view.KeyEvent
import androidx.fragment.app.FragmentActivity
import com.firestreams.ui.SoundManager

class DetailsActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(com.firestreams.R.layout.activity_details)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(com.firestreams.R.id.details_container, DetailsFragment())
                .commit()
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE) {
            SoundManager.playBack()
        }
        return super.onKeyDown(keyCode, event)
    }

    companion object {
        const val EXTRA_MATCH_ID = "match_id"
        const val EXTRA_MATCH_TITLE = "match_title"
        const val EXTRA_MATCH_CATEGORY = "match_category"
        const val EXTRA_MATCH_POSTER = "match_poster"
        const val EXTRA_MATCH_IS_LIVE = "match_is_live"
        const val EXTRA_MATCH_SOURCES = "match_sources"
    }
}
