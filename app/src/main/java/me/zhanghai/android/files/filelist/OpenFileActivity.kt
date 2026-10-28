/*
 * Copyright (c) 2019 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filelist

import android.content.Intent
import android.os.Bundle
import java8.nio.file.Path
import me.zhanghai.android.files.BuildConfig
import me.zhanghai.android.files.app.AppActivity
import me.zhanghai.android.files.app.application
import me.zhanghai.android.files.file.MimeType
import me.zhanghai.android.files.file.asMimeTypeOrNull
import me.zhanghai.android.files.file.fileProviderUri
import me.zhanghai.android.files.filejob.FileJobService
import me.zhanghai.android.files.provider.archive.isArchivePath
import me.zhanghai.android.files.util.KeyStoreHelper
import me.zhanghai.android.files.util.createViewIntent
import me.zhanghai.android.files.util.extraPath
import me.zhanghai.android.files.util.startActivitySafe
import me.zhanghai.android.files.util.toBase64
import me.zhanghai.android.files.util.withoutSaveAsActivity
import java.security.MessageDigest

class OpenFileActivity : AppActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val intent = intent
        val path = intent.extraPath
        val mimeType = intent.type?.asMimeTypeOrNull()
        if (path != null && mimeType != null) {
            // This activity is exported for launcher shortcuts, so only grant access to the file
            // for intents created by ourselves, and otherwise just show the file in file list.
            if (intent.hasValidSignature(path, mimeType)) {
                openFile(path, mimeType)
            } else {
                startActivitySafe(FileListActivity.createViewIntent(path.parent ?: path))
            }
        }
        finish()
    }

    private fun openFile(path: Path, mimeType: MimeType) {
        if (path.isArchivePath) {
            FileJobService.open(path, mimeType, false, this)
        } else {
            val intent = path.fileProviderUri.createViewIntent(mimeType)
                .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                .apply { extraPath = path }
                .withoutSaveAsActivity()
            startActivitySafe(intent)
        }
    }

    companion object {
        private const val ACTION_OPEN_FILE = "me.zhanghai.android.files.intent.action.OPEN_FILE"

        private const val EXTRA_SIGNATURE = "${BuildConfig.APPLICATION_ID}.extra.SIGNATURE"

        private const val SIGNATURE_KEY_ALIAS = "open_file_intent"

        fun createIntent(path: Path, mimeType: MimeType): Intent =
            Intent(ACTION_OPEN_FILE)
                .setPackage(application.packageName)
                .setType(mimeType.value)
                .apply {
                    extraPath = path
                    try {
                        putExtra(EXTRA_SIGNATURE, sign(path, mimeType))
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

        private fun Intent.hasValidSignature(path: Path, mimeType: MimeType): Boolean {
            val signature = getStringExtra(EXTRA_SIGNATURE) ?: return false
            val expectedSignature = try {
                sign(path, mimeType)
            } catch (e: Exception) {
                e.printStackTrace()
                return false
            }
            return MessageDigest.isEqual(signature.toByteArray(), expectedSignature.toByteArray())
        }

        private fun sign(path: Path, mimeType: MimeType): String =
            KeyStoreHelper.hmac(
                SIGNATURE_KEY_ALIAS, "${path.toUri()}\n${mimeType.value}".toByteArray()
            ).toBase64().value
    }
}
