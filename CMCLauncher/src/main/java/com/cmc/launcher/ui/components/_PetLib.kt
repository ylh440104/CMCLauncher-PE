package com.cmc.launcher.ui.components

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import com.movtery.petlib.ArmModel
import com.movtery.petlib.PetAnimation
import com.movtery.petlib.PetController
import com.movtery.petlib.PetGazeRouter
import com.movtery.petlib.PetPreview
import com.movtery.petlib.PetTapReaction

fun PetController.setSkinDefaultable(
    context: Context,
    skin: ByteArray?,
    arm: ArmModel?,
    onError: ((String) -> Unit)? = null
) {
    val nonNullSkin = skin ?: context.loadDefaultSkin(arm)
    setSkin(nonNullSkin, arm, onError)
}

private fun Context.loadDefaultSkin(arm: ArmModel?): ByteArray = if (arm == ArmModel.Slim) {
    assets.open("skin/alex.png")
} else {
    assets.open("skin/steve.png")
}.use { it.readBytes() }

@Composable
fun PetModelView(
    modifier: Modifier = Modifier,
    skin: ByteArray? = null,
    cape: ByteArray? = null,
    arm: ArmModel? = null,
    yaw: Float = 0f,
    animation: PetAnimation? = PetAnimation.Idle,
    animationSpeed: Float = 1f,
    tapReaction: PetTapReaction = PetTapReaction.Squash,
    dragYaw: Boolean = true,
    gazeEnabled: Boolean = true,
) {
    val context = LocalContext.current
    val petController = remember {
        PetController(skin ?: context.loadDefaultSkin(arm), arm)
    }
    DisposableEffect(petController) {
        onDispose { petController.close() }
    }

    LaunchedEffect(petController, skin, arm) {
        petController.setSkinDefaultable(context, skin, arm)
    }
    LaunchedEffect(petController, cape) {
        petController.setCape(cape)
    }
    LaunchedEffect(petController, animation, animationSpeed) {
        if (animation != null) {
            petController.playAnimation(animation, animationSpeed)
        }
    }
    LaunchedEffect(petController, yaw) {
        petController.setModelYaw(yaw)
    }

    var previewBounds by remember { mutableStateOf<Rect?>(null) }
    DisposableEffect(petController, gazeEnabled) {
        if (!gazeEnabled) {
            onDispose { }
        } else {
            val gazeTarget = object : PetGazeRouter.Target {
                override fun onCursor(x: Float, y: Float) {
                    val bounds = previewBounds ?: return
                    petController.setGaze(Pair(x - bounds.left, y - bounds.top))
                }

                override fun onGazeExpired() {
                    petController.setGaze(null)
                }
            }
            PetGazeRouter.register(gazeTarget)
            onDispose { PetGazeRouter.unregister(gazeTarget) }
        }
    }

    PetPreview(
        controller = petController,
        modifier = modifier.onGloballyPositioned { previewBounds = it.boundsInWindow() },
        tapReaction = tapReaction,
        dragYaw = dragYaw
    )
}
