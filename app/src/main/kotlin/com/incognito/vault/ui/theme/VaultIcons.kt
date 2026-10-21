package com.incognito.vault.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import com.incognito.vault.R

/**
 * High-performance, zero-bloat vector icon resources.
 * Replaces androidx.compose.material:material-icons-extended (34MB)
 * with lightweight, standalone vector drawables (< 12KB total).
 */
object VaultIcons {
    val Shield: Painter @Composable get() = painterResource(R.drawable.ic_shield)
    val Lock: Painter @Composable get() = painterResource(R.drawable.ic_lock)
    val Fingerprint: Painter @Composable get() = painterResource(R.drawable.ic_fingerprint)
    val WifiOff: Painter @Composable get() = painterResource(R.drawable.ic_wifi_off)
    val Security: Painter @Composable get() = painterResource(R.drawable.ic_security)
    val CameraSwitch: Painter @Composable get() = painterResource(R.drawable.ic_cameraswitch)
    val FlashOn: Painter @Composable get() = painterResource(R.drawable.ic_flash_on)
    val FlashOff: Painter @Composable get() = painterResource(R.drawable.ic_flash_off)
    val FlashAuto: Painter @Composable get() = painterResource(R.drawable.ic_flash_auto)
    val Highlight: Painter @Composable get() = painterResource(R.drawable.ic_highlight)
    val PhotoLibrary: Painter @Composable get() = painterResource(R.drawable.ic_photo_library)
    val Photo: Painter @Composable get() = painterResource(R.drawable.ic_photo)
    val Videocam: Painter @Composable get() = painterResource(R.drawable.ic_videocam)
    val ArrowBack: Painter @Composable get() = painterResource(R.drawable.ic_arrow_back)
    val CameraAlt: Painter @Composable get() = painterResource(R.drawable.ic_camera_alt)
    val CheckCircle: Painter @Composable get() = painterResource(R.drawable.ic_check_circle)
    val RadioUnchecked: Painter @Composable get() = painterResource(R.drawable.ic_radio_unchecked)
    val Play: Painter @Composable get() = painterResource(R.drawable.ic_play)
    val Pause: Painter @Composable get() = painterResource(R.drawable.ic_pause)
    val Replay: Painter @Composable get() = painterResource(R.drawable.ic_replay)
    val DeleteForever: Painter @Composable get() = painterResource(R.drawable.ic_delete_forever)
    val Download: Painter @Composable get() = painterResource(R.drawable.ic_download)
    val SelectAll: Painter @Composable get() = painterResource(R.drawable.ic_select_all)
    val AddPhoto: Painter @Composable get() = painterResource(R.drawable.ic_add_photo)
    val RotateRight: Painter @Composable get() = painterResource(R.drawable.ic_rotate_right)
    val VolumeUp: Painter @Composable get() = painterResource(R.drawable.ic_volume_up)
    val VolumeOff: Painter @Composable get() = painterResource(R.drawable.ic_volume_off)
    val Info: Painter @Composable get() = painterResource(R.drawable.ic_info)
    val Visibility: Painter @Composable get() = painterResource(R.drawable.ic_visibility)
    val VisibilityOff: Painter @Composable get() = painterResource(R.drawable.ic_visibility_off)
}
