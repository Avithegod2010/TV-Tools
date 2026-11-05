package com.tvtools.app.ui.remote

// Shared remote key codes and mode enum.
// Android KEYCODEs
internal const val KEY_HOME = 3
internal const val KEY_BACK = 4
internal const val KEY_DPAD_UP = 19
internal const val KEY_DPAD_DOWN = 20
internal const val KEY_DPAD_LEFT = 21
internal const val KEY_DPAD_RIGHT = 22
internal const val KEY_DPAD_CENTER = 23
internal const val KEY_VOL_UP = 24
internal const val KEY_VOL_DOWN = 25
internal const val KEY_POWER = 26
internal const val KEY_MENU = 82
internal const val KEY_MEDIA_PLAY_PAUSE = 85
internal const val KEY_MEDIA_NEXT = 87
internal const val KEY_MEDIA_PREV = 88
internal const val KEY_MEDIA_REWIND = 89
internal const val KEY_MEDIA_FF = 90
internal const val KEY_SLEEP = 223
internal const val KEY_WAKE = 224
internal const val KEY_MUTE = 164
internal const val KEY_CH_UP = 166
internal const val KEY_CH_DOWN = 167
internal const val KEY_GAMEPAD_A = 96
internal const val KEY_GAMEPAD_B = 97
internal const val KEY_GAMEPAD_X = 99
internal const val KEY_GAMEPAD_Y = 100
internal const val KEY_GAMEPAD_START = 108
internal const val KEY_GAMEPAD_SELECT = 109

internal enum class RemoteMode { DPAD, TOUCHPAD, GAMEPAD, GESTURE }
