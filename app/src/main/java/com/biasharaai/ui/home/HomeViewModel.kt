package com.biasharaai.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.biasharaai.data.local.db.Alert
import com.biasharaai.data.local.db.AlertDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val alertDao: AlertDao,
) : ViewModel() {

    val activeLossAlerts: StateFlow<List<Alert>> = alertDao.getActiveLossAlerts()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = emptyList(),
        )

    fun dismissAlert(alertId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            alertDao.dismissAlert(alertId)
        }
    }
}
