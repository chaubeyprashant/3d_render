package com.example.a3d_render.ui.access

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.a3d_render.domain.repository.AccessRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AccessGateUiState(
    val isLoading: Boolean = true,
    val isAccessEnabled: Boolean = false
)

class AccessGateViewModel(
    private val accessRepository: AccessRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(AccessGateUiState())
    val uiState: StateFlow<AccessGateUiState> = _uiState.asStateFlow()

    init {
        checkAccess()
    }

    fun checkAccess() {
        _uiState.value = AccessGateUiState(isLoading = true, isAccessEnabled = false)
        viewModelScope.launch {
            val accessEnabled = accessRepository.isAppAccessEnabled()
            _uiState.value = AccessGateUiState(
                isLoading = false,
                isAccessEnabled = accessEnabled
            )
        }
    }

    class Factory(
        private val accessRepository: AccessRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return AccessGateViewModel(accessRepository) as T
        }
    }
}
