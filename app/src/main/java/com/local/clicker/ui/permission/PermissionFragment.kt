package com.local.clicker.ui.permission

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import com.local.clicker.ui.MainActivity
import com.local.clicker.ui.theme.ClickerTheme

class PermissionFragment : Fragment() {
    private var gate by mutableStateOf(PermissionGate(false, false, false))

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            gate = permissionGate(requireContext())
            setContent {
                val requestSms = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
                    refreshPermissions()
                }
                ClickerTheme {
                    PermissionScreen(
                        gate = gate,
                        onAccessibility = { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                        onAlarms = {
                            startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                data = Uri.parse("package:${requireContext().packageName}")
                            })
                        },
                        onBatterySettings = {
                            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.parse("package:${requireContext().packageName}")
                            })
                        },
                        onSms = {
                            if (gate.sms) {
                                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.parse("package:${requireContext().packageName}")
                                })
                            } else {
                                requestSms.launch(Manifest.permission.RECEIVE_SMS)
                            }
                        },
                    )
                }
            }
        }

    override fun onResume() {
        super.onResume()
        refreshPermissions()
    }

    private fun refreshPermissions() {
        gate = permissionGate(requireContext())
        (activity as? MainActivity)?.refreshPermissions()
    }
}
