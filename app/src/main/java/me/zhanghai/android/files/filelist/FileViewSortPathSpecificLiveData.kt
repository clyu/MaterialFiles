/*
 * Copyright (c) 2019 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filelist

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import java8.nio.file.Path
import me.zhanghai.android.files.settings.PathSettings
import me.zhanghai.android.files.settings.SettingLiveData

class FileViewSortPathSpecificLiveData(pathLiveData: LiveData<Path>) : MediatorLiveData<Boolean>() {
    private lateinit var pathViewTypeLiveData: SettingLiveData<FileViewType?>
    private lateinit var pathSortOptionsLiveData: SettingLiveData<FileSortOptions?>

    private var pendingValue: Boolean? = null

    val hasPendingValue: Boolean
        get() = pendingValue != null

    private val savedValue: Boolean
        get() = pathViewTypeLiveData.value != null || pathSortOptionsLiveData.value != null

    private fun loadValue() {
        val value = pendingValue ?: savedValue
        if (this.value != value) {
            this.value = value
        }
    }

    fun putPendingValue(value: Boolean) {
        pendingValue = value.takeIf { it != savedValue }
        loadValue()
    }

    // The path specific values are put by FileViewTypeLiveData and FileSortOptionsLiveData.
    fun clearPendingValue() {
        pendingValue = null
        loadValue()
    }

    init {
        addSource(pathLiveData) { path: Path ->
            if (this::pathViewTypeLiveData.isInitialized) {
                removeSource(pathViewTypeLiveData)
            }
            if (this::pathSortOptionsLiveData.isInitialized) {
                removeSource(pathSortOptionsLiveData)
            }
            pathViewTypeLiveData = PathSettings.getFileListViewType(path)
            pathSortOptionsLiveData = PathSettings.getFileListSortOptions(path)
            // Discard any change not applied to the previous path.
            pendingValue = null
            addSource(pathViewTypeLiveData) { loadValue() }
            addSource(pathSortOptionsLiveData) { loadValue() }
        }
    }
}
