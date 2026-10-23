/*
 * Copyright (c) 2023 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filelist

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import java8.nio.file.Path
import me.zhanghai.android.files.settings.PathSettings
import me.zhanghai.android.files.settings.SettingLiveData
import me.zhanghai.android.files.settings.Settings
import me.zhanghai.android.files.util.valueCompat

class FileViewTypeLiveData(pathLiveData: LiveData<Path>) : MediatorLiveData<FileViewType>() {
    private lateinit var pathViewTypeLiveData: SettingLiveData<FileViewType?>

    private var pendingValue: FileViewType? = null

    val hasPendingValue: Boolean
        get() = pendingValue != null

    private val savedValue: FileViewType
        get() = pathViewTypeLiveData.value ?: Settings.FILE_LIST_VIEW_TYPE.valueCompat

    private fun loadValue() {
        if (!this::pathViewTypeLiveData.isInitialized) {
            // Not yet initialized.
            return
        }
        val value = pendingValue ?: savedValue
        if (this.value != value) {
            this.value = value
        }
    }

    fun putPendingValue(value: FileViewType) {
        pendingValue = value.takeIf { it != savedValue }
        loadValue()
    }

    // Shows the global value that will be used once the path specific value is removed.
    fun putPendingGlobalValue() {
        if (pathViewTypeLiveData.value != null) {
            putPendingValue(Settings.FILE_LIST_VIEW_TYPE.valueCompat)
        }
    }

    fun applyPendingValue(isPathSpecific: Boolean) {
        val value = valueCompat
        if (isPathSpecific) {
            pathViewTypeLiveData.putValue(value)
        } else {
            // Put the global value before removing the path specific value to avoid flickering.
            Settings.FILE_LIST_VIEW_TYPE.putValue(value)
            if (pathViewTypeLiveData.value != null) {
                pathViewTypeLiveData.putValue(null)
            }
        }
        pendingValue = null
        loadValue()
    }

    init {
        addSource(Settings.FILE_LIST_VIEW_TYPE) { loadValue() }
        addSource(pathLiveData) { path: Path ->
            if (this::pathViewTypeLiveData.isInitialized) {
                removeSource(pathViewTypeLiveData)
            }
            pathViewTypeLiveData = PathSettings.getFileListViewType(path)
            // Discard any change not applied to the previous path.
            pendingValue = null
            addSource(pathViewTypeLiveData) { loadValue() }
        }
    }
}
