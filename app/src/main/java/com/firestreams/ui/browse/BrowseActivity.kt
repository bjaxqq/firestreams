package com.firestreams.ui.browse

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import com.firestreams.ui.SoundManager

class BrowseActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SoundManager.init(this)
        setContentView(com.firestreams.R.layout.activity_browse)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(com.firestreams.R.id.browse_container, ImmersiveBrowseFragment())
                .commit()
        }
    }

    companion object
}
