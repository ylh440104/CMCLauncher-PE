package com.cmc.launcher.ui.screens.splash

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.entryProvider
import com.cmc.launcher.R
import com.cmc.launcher.components.InstallableItem
import com.cmc.launcher.ui.base.BaseScreen
import com.cmc.launcher.ui.screens.NavStateHost
import com.cmc.launcher.ui.screens.NestedNavDisplay
import com.cmc.launcher.ui.screens.rememberTransitionSpec
import com.cmc.launcher.ui.theme.cmc.Cmc
import com.cmc.launcher.viewmodel.SplashBackStackViewModel

@Composable
fun SplashScreen(
    startAllTask: () -> Unit,
    unpackItems: List<InstallableItem>,
    screenViewModel: SplashBackStackViewModel
) {
    NavStateHost(rootKey = screenViewModel.splashScreen) {
        NavigationUI(
            modifier = Modifier.fillMaxSize(),
            startAllTask = startAllTask,
            unpackItems = unpackItems,
            screenViewModel = screenViewModel
        )
    }
}

@Composable
private fun NavigationUI(
    modifier: Modifier = Modifier,
    startAllTask: () -> Unit,
    unpackItems: List<InstallableItem>,
    screenViewModel: SplashBackStackViewModel
) {
    val backStack = screenViewModel.splashScreen.backStack
    val currentKey = backStack.lastOrNull()
    LaunchedEffect(currentKey) {
        screenViewModel.splashScreen.currentKey = currentKey
    }

    if (backStack.isNotEmpty()) {
        NestedNavDisplay(
            ownerKey = screenViewModel.splashScreen,
            backStack = backStack,
            modifier = modifier,
            onBack = {},
            transitionSpec = rememberTransitionSpec(),
            popTransitionSpec = rememberTransitionSpec(),
            entryProvider = entryProvider {
                entry<SplashOperation.Loading> {
                    LoadingScreen(screenViewModel = screenViewModel)
                }
                entry<SplashOperation.Unpacking> {
                    UnpackScreen(
                        items = unpackItems,
                        screenViewModel = screenViewModel,
                        onAgreeClick = startAllTask
                    )
                }
            }
        )
    } else {
        Box(modifier)
    }
}

@Composable
private fun LoadingScreen(screenViewModel: SplashBackStackViewModel) {
    BaseScreen(
        screenKey = SplashOperation.Loading,
        currentKey = screenViewModel.splashScreen.currentKey
    ) { isVisible ->
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                Image(
                    modifier = Modifier.size(72.dp),
                    painter = painterResource(R.drawable.ic_cmc_logo),
                    contentDescription = stringResource(R.string.generic_loading)
                )
                Spacer(Modifier.height(18.dp))
                Text(
                    text = Cmc.NAME,
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = Cmc.TAGLINE,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall
                )
                Spacer(Modifier.height(28.dp))
                LinearProgressIndicator(modifier = Modifier.width(132.dp))
            }
        }
    }
}
