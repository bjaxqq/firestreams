package com.firestreams.ui.player

import android.os.Bundle
import androidx.fragment.app.FragmentActivity

class PlayerActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    companion object {
        const val EXTRA_MATCH_ID = "match_id"
        const val EXTRA_MATCH_TITLE = "match_title"
        const val EXTRA_SOURCES = "sources"  // Array<String>, each "sourceName:sourceId"
    }
}
