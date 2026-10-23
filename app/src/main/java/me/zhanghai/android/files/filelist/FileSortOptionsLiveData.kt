/*
 * Copyright (c) 2018 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filelist

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import java8.nio.file.Path
import me.zhanghai.android.files.filelist.FileSortOptions.By
import me.zhanghai.android.files.filelist.FileSortOptions.Order
import me.zhanghai.android.files.settings.PathSettings
import me.zhanghai.android.files.settings.SettingLiveData
import me.zhanghai.android.files.settings.Settings
import me.zhanghai.android.files.util.valueCompat

class FileSortOptionsLiveData(pathLiveData: LiveData<Path>) : MediatorLiveData<FileSortOptions>() {
    private lateinit var pathSortOptionsLiveData: SettingLiveData<FileSortOptions?>

    private var pendingValue: FileSortOptions? = null

    val hasPendingValue: Boolean
        get() = pendingValue != null

    private val savedValue: FileSortOptions
        get() = pathSortOptionsLiveData.value ?: Settings.FILE_LIST_SORT_OPTIONS.valueCompat

    private fun loadValue() {
        if (!this::pathSortOptionsLiveData.isInitialized) {
            // Not yet initialized.
            return
        }
        val value = pendingValue ?: savedValue
        if (this.value != value) {
            this.value = value
        }
    }

    fun putPendingBy(by: By) {
        putPendingValue(valueCompat.copy(by = by))
    }

    fun putPendingOrder(order: Order) {
        putPendingValue(valueCompat.copy(order = order))
    }

    fun putPendingIsDirectoriesFirst(isDirectoriesFirst: Boolean) {
        putPendingValue(valueCompat.copy(isDirectoriesFirst = isDirectoriesFirst))
    }

    private fun putPendingValue(value: FileSortOptions) {
        pendingValue = value.takeIf { it != savedValue }
        loadValue()
    }

    // Shows the global value that will be used once the path specific value is removed.
    fun putPendingGlobalValue() {
        if (pathSortOptionsLiveData.value != null) {
            putPendingValue(Settings.FILE_LIST_SORT_OPTIONS.valueCompat)
        }
    }

    fun applyPendingValue(isPathSpecific: Boolean) {
        val value = valueCompat
        if (isPathSpecific) {
            pathSortOptionsLiveData.putValue(value)
        } else {
            // Put the global value before removing the path specific value to avoid flickering.
            Settings.FILE_LIST_SORT_OPTIONS.putValue(value)
            if (pathSortOptionsLiveData.value != null) {
                pathSortOptionsLiveData.putValue(null)
            }
        }
        pendingValue = null
        loadValue()
    }

    init {
        addSource(Settings.FILE_LIST_SORT_OPTIONS) { loadValue() }
        addSource(pathLiveData) { path: Path ->
            if (this::pathSortOptionsLiveData.isInitialized) {
                removeSource(pathSortOptionsLiveData)
            }
            pathSortOptionsLiveData = PathSettings.getFileListSortOptions(path)
            // Discard any change not applied to the previous path.
            pendingValue = null
            addSource(pathSortOptionsLiveData) { loadValue() }
        }
    }
}
