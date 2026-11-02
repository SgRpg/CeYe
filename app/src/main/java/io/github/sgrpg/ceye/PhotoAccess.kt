package io.github.sgrpg.ceye

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/** The system remains responsible for the photo permission dialog. */
internal object PhotoAccess {
    fun permissions(): Array<String> = when {
        Build.VERSION.SDK_INT >= 34 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
        Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
        else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    /** 0 = denied, 1 = selected photos, 2 = full library. */
    fun level(context: Context): Int {
        fun granted(permission: String) = context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
        return when {
            granted(permissions().first()) -> 2
            Build.VERSION.SDK_INT >= 34 && granted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) -> 1
            else -> 0
        }
    }
}
