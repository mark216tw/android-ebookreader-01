package com.minireader.app.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.minireader.app.MiniReaderApp
import com.minireader.app.data.prefs.ReaderPrefs
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val preferences = (application as MiniReaderApp).container.preferences

    val prefs: StateFlow<ReaderPrefs> = preferences.data.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ReaderPrefs(),
    )

    fun updatePrefs(prefs: ReaderPrefs) {
        viewModelScope.launch {
            preferences.update { prefs }
        }
    }
}
