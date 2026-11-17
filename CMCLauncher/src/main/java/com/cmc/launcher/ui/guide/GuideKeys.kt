package com.cmc.launcher.ui.guide

import com.movtery.guide.GuideKey

sealed interface GuideKeys : GuideKey {

    sealed interface Keys {

        val version: Int
    }

    data object Main : Keys {
        override val version = 1

        sealed interface Step : GuideKey {

            data object Account : Step

            data object VersionList : Step

            data object CardDrag : Step

            data object CardTip : Step
        }
    }

    data object Editor : Keys {
        override val version = 2

        sealed interface Step : GuideKey {

            data object MenuBall : Step

            data object LayerList : Step

            data object CreateLayer : Step

            data object AddButtons : Step

            data object AddStyles : Step

            data object StyleCabinet : Step

            data object Preview : Step

            data object Save : Step
        }
    }

    data object JoystickEvents : Keys {
        override val version = 1

        sealed interface Step : GuideKey {

            data object Direction : Step

            data object Lock : Step

            data object Events : Step
        }
    }
}
