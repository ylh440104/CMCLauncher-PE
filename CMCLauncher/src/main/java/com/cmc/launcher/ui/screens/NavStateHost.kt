package com.cmc.launcher.ui.screens

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.SaveableStateHolderNavEntryDecorator
import androidx.navigation3.scene.Scene
import androidx.navigation3.ui.NavDisplay

class NavKeyStateHolders internal constructor() {
    internal val holders = HashMap<Any, SaveableStateHolder>()
}

val LocalNavKeyStateHolders = compositionLocalOf<NavKeyStateHolders?> { null }

@Composable
fun NavStateHost(
    rootKey: BackStackNavKey<*>,
    content: @Composable () -> Unit
) {
    val holders = remember { NavKeyStateHolders() }

    AnchorNavKeys(
        holders = holders,
        navKey = rootKey,
        visited = HashSet(),
        pinChildren = false
    )

    CompositionLocalProvider(LocalNavKeyStateHolders provides holders, content = content)
}

@Composable
private fun AnchorNavKeys(
    holders: NavKeyStateHolders,
    navKey: BackStackNavKey<*>,
    visited: MutableSet<BackStackNavKey<*>>,
    pinChildren: Boolean
) {
    if (!visited.add(navKey)) return
    key(navKey) {
        val holder = rememberSaveableStateHolder()

        holders.holders[navKey] = holder
        DisposableEffect(navKey) {
            onDispose {
                holders.holders.remove(navKey)
            }
        }

        if (pinChildren) {

            val pinned = remember { mutableStateListOf<BackStackNavKey<*>>() }
            navKey.backStack.forEach { subKey ->
                if (subKey is BackStackNavKey<*> && !pinned.contains(subKey)) {
                    pinned.add(subKey)
                }
            }
            pinned.forEach { subKey ->
                AnchorNavKeys(
                    holders = holders,
                    navKey = subKey,
                    visited = visited,
                    pinChildren = true
                )
            }
        } else {
            navKey.backStack.forEach { subKey ->
                if (subKey is BackStackNavKey<*>) {
                    AnchorNavKeys(
                        holders = holders,
                        navKey = subKey,
                        visited = visited,
                        pinChildren = true
                    )
                }
            }
        }
    }
}

@Composable
fun rememberNavKeyStateHolder(ownerKey: Any): SaveableStateHolder {
    val fallback = rememberSaveableStateHolder()
    val hoisted = LocalNavKeyStateHolders.current?.holders?.get(ownerKey)

    val frozen = remember { mutableStateOf(hoisted) }
    return frozen.value ?: fallback
}

@Composable
fun <E : TitledNavKey> NestedNavDisplay(
    ownerKey: Any,
    backStack: NavBackStack<E>,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = { backStack.removeLastOrNull() },
    extraDecorators: List<NavEntryDecorator<E>> = emptyList(),
    transitionSpec: AnimatedContentTransitionScope<Scene<E>>.() -> ContentTransform,
    popTransitionSpec: AnimatedContentTransitionScope<Scene<E>>.() -> ContentTransform,
    entryProvider: (key: E) -> NavEntry<E>
) {
    val stateHolder = rememberNavKeyStateHolder(ownerKey)
    val saveableDecorator = remember(stateHolder) {
        SaveableStateHolderNavEntryDecorator<E>(stateHolder)
    }

    NavDisplay(
        backStack = backStack,
        modifier = modifier,
        onBack = onBack,
        entryDecorators = listOf(saveableDecorator) + extraDecorators,
        transitionSpec = transitionSpec,
        popTransitionSpec = popTransitionSpec,
        entryProvider = entryProvider
    )
}
