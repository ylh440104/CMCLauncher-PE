package com.cmc.launcher.ui.base

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.HasDefaultViewModelProviderFactory
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.cmc.launcher.game.version.installed.Version
import com.cmc.launcher.game.version.installed.VersionViewModelStores
import com.cmc.launcher.game.version.installed.VersionsManager

@Composable
fun VersionViewModelStoreScope(
    version: Version,
    content: @Composable () -> Unit
) {
    val host = checkNotNull(LocalViewModelStoreOwner.current)
    val storeOwner = remember(version, host) {
        VersionViewModelStores.acquire(version)
        VersionScopedViewModelStoreOwner(version, host)
    }

    DisposableEffect(version) {
        onDispose {
            VersionViewModelStores.releaseAndClearIfStale(
                version = version,
                currentVersions = VersionsManager.versions.value
            )
        }
    }

    CompositionLocalProvider(
        LocalViewModelStoreOwner provides storeOwner,
        content = content
    )
}

private class VersionScopedViewModelStoreOwner(
    private val version: Version,
    private val host: ViewModelStoreOwner
) : ViewModelStoreOwner, HasDefaultViewModelProviderFactory {
    private val hasDefault = host as? HasDefaultViewModelProviderFactory

    override val viewModelStore: ViewModelStore
        get() = VersionViewModelStores.storeOf(version)

    override val defaultViewModelProviderFactory: ViewModelProvider.Factory
        get() = checkNotNull(hasDefault) {
            "Host ViewModelStoreOwner must implement HasDefaultViewModelProviderFactory"
        }.defaultViewModelProviderFactory

    override val defaultViewModelCreationExtras: CreationExtras
        get() = hasDefault?.defaultViewModelCreationExtras ?: CreationExtras.Empty
}
