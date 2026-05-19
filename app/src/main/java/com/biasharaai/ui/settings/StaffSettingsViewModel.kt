package com.biasharaai.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.biasharaai.data.local.db.StaffMember
import com.biasharaai.data.local.db.StaffMemberDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class StaffSettingsViewModel @Inject constructor(
    private val staffMemberDao: StaffMemberDao,
) : ViewModel() {

    val staff = staffMemberDao.getActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun addStaff(name: String) {
        viewModelScope.launch {
            staffMemberDao.insert(StaffMember(name = name.trim()))
        }
    }

    fun deactivate(member: StaffMember) {
        viewModelScope.launch {
            staffMemberDao.update(member.copy(isActive = false))
        }
    }
}
