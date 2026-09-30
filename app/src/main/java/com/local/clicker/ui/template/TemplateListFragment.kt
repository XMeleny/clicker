package com.local.clicker.ui.template

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import com.local.clicker.ui.theme.ClickerTheme

class TemplateListFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                ClickerTheme {
                    TemplateListScreen(
                        onOpen = { startActivity(TemplateEditActivity.intent(requireContext(), it)) },
                        onCreate = { startActivity(TemplateEditActivity.intent(requireContext(), 0L)) },
                    )
                }
            }
        }
}
