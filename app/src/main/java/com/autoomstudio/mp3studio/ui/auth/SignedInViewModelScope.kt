package com.autoomstudio.mp3studio.ui.auth

import androidx.activity.ComponentActivity
import androidx.lifecycle.HasDefaultViewModelProviderFactory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.CreationExtras

/**
 * Holds the ViewModels of the signed-in screens (player, library, playlists, separation…) in their own store, so
 * signing out clears them, including the player's MediaController connection, while rotation keeps them.
 */
class SignedInViewModelScope : ViewModel() {
    private var userId: String? = null
    private var store = ViewModelStore()

    /** The store for [id]; a different user (or null when signed out) clears the previous one. */
    fun storeFor(id: String?): ViewModelStore {
        if (id != userId) {
            store.clear()
            store = ViewModelStore()
            userId = id
        }
        return store
    }

    override fun onCleared() = store.clear()
}

/** Lets `viewModel()` inside the signed-in UI use [store] with the activity's factory and extras. */
class SignedInViewModelOwner(
    override val viewModelStore: ViewModelStore,
    private val activity: ComponentActivity,
) : ViewModelStoreOwner, HasDefaultViewModelProviderFactory {
    override val defaultViewModelProviderFactory: ViewModelProvider.Factory
        get() = activity.defaultViewModelProviderFactory

    override val defaultViewModelCreationExtras: CreationExtras
        get() = activity.defaultViewModelCreationExtras
}
