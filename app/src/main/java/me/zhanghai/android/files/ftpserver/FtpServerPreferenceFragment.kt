/*
 * Copyright (c) 2019 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.ftpserver

import android.os.Bundle
import android.view.View
import me.zhanghai.android.files.R
import me.zhanghai.android.files.settings.PasswordPreference
import me.zhanghai.android.files.settings.Settings
import me.zhanghai.android.files.ui.PreferenceFragmentCompat

class FtpServerPreferenceFragment : PreferenceFragmentCompat() {
    private lateinit var passwordPreference: PasswordPreference

    override fun onCreatePreferencesFix(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.ftp_server)

        passwordPreference =
            preferenceScreen.findPreference(getString(R.string.pref_key_ftp_server_password))!!
        passwordPreference.setOnPreferenceChangeListener { _, newValue ->
            Settings.FTP_SERVER_PASSWORD.putValue(newValue as String)
            true
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        Settings.FTP_SERVER_PASSWORD.observe(viewLifecycleOwner) { passwordPreference.text = it }
    }
}
