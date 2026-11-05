package com.kishan.kavach.core.permissions

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.kishan.kavach.domain.tools.ToolResult

inline fun withPermission(ctx: Context, perm: String, label: String, block: () -> ToolResult): ToolResult =
    if (ContextCompat.checkSelfPermission(ctx, perm) == PackageManager.PERMISSION_GRANTED) block()
    else ToolResult.Denied(label, "Ask the user to allow $label once (Settings > Apps > Draivo > Permissions), then retry.")
