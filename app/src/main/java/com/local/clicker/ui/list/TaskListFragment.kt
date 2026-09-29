package com.local.clicker.ui.list

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import com.local.clicker.ui.edit.TaskEditActivity
import com.local.clicker.ui.theme.ClickerTheme

class TaskListFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                ClickerTheme {
                    TaskListScreen(
                        onOpen = { startActivity(TaskEditActivity.intent(requireContext(), it)) },
                        onCreate = { startActivity(TaskEditActivity.intent(requireContext(), 0L)) },
                    )
                }
            }
        }
}
