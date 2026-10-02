package com.cleanguard.app.ui

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cleanguard.app.CleanGuardApplication
import com.cleanguard.app.di.AppContainer
import com.cleanguard.app.ui.apps.AppsViewModel
import com.cleanguard.app.ui.dashboard.DashboardViewModel
import com.cleanguard.app.ui.details.AppDetailViewModel
import com.cleanguard.app.ui.security.SecurityViewModel
import com.cleanguard.app.ui.settings.SettingsViewModel
import com.cleanguard.app.ui.storage.StorageViewModel

/** Fábrica única de ViewModels (injeção manual a partir do [AppContainer]). */
object AppViewModelProvider {
    val Factory: ViewModelProvider.Factory = viewModelFactory {
        initializer { DashboardViewModel(container()) }
        initializer { AppsViewModel(container(), createSavedStateHandle()) }
        initializer { StorageViewModel(container()) }
        initializer { SecurityViewModel(container()) }
        initializer { AppDetailViewModel(container(), createSavedStateHandle()) }
        initializer { SettingsViewModel(container()) }
    }
}

private fun CreationExtras.container(): AppContainer =
    (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as CleanGuardApplication).container
