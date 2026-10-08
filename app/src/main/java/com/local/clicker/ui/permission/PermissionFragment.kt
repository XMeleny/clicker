package com.local.clicker.ui.permission

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import com.local.clicker.ui.theme.ClickerTheme

class PermissionFragment : Fragment() {
    private var gate by mutableStateOf(PermissionGate(false, false))

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            gate = permissionGate(requireContext())
            setContent {
                ClickerTheme {
                    PermissionScreen(
                        gate = gate,
                        onAccessibility = { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                        onAlarms = {
                            startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                data = Uri.parse("package:${requireContext().packageName}")
                            })
                        },
                    )
                }
            }
        }

    override fun onResume() {
        super.onResume()
        gate = permissionGate(requireContext())
    }
}
