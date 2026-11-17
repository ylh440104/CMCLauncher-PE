package com.movtery.layer_controller.observable

sealed class InteractionBehavior {

    abstract val releaseOnOutOfBounds: Boolean

    abstract val canBeSwipedTo: Boolean

    abstract val blocksSwipeChain: Boolean

    data object Press : InteractionBehavior() {
        override val releaseOnOutOfBounds: Boolean get() = false
        override val canBeSwipedTo: Boolean get() = false
        override val blocksSwipeChain: Boolean get() = false
    }

    data object Swipable : InteractionBehavior() {
        override val releaseOnOutOfBounds: Boolean get() = true
        override val canBeSwipedTo: Boolean get() = true
        override val blocksSwipeChain: Boolean get() = false
    }

    data object Toggle : InteractionBehavior() {
        override val releaseOnOutOfBounds: Boolean get() = false
        override val canBeSwipedTo: Boolean get() = false
        override val blocksSwipeChain: Boolean get() = true
    }

    companion object {
        fun from(isSwipple: Boolean, isToggleable: Boolean): InteractionBehavior = when {
            isToggleable -> Toggle
            isSwipple -> Swipable
            else -> Press
        }
    }
}
