package me.zhanghai.android.files.viewer.audio

import android.os.Bundle
import android.view.View
import androidx.fragment.app.commit
import me.zhanghai.android.files.app.AppActivity
import me.zhanghai.android.files.util.putArgs

class AudioPlayerActivity : AppActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Calls ensureSubDecor().
        findViewById<View>(android.R.id.content)
        if (savedInstanceState == null) {
            val fragment = AudioPlayerFragment().putArgs(AudioPlayerFragment.Args(intent))
            supportFragmentManager.commit { add(android.R.id.content, fragment) }
        }
    }
}
