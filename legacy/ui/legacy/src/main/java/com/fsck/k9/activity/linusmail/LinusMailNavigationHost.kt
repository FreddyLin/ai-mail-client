package com.fsck.k9.activity.linusmail

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.view.isVisible
import com.fsck.k9.ui.R
import net.thunderbird.components.ui.bolt.atom.icon.Icons as BoltIcons

/**
 * App-specific navigation shell for Linus Mail.
 *
 * The existing message host remains responsible for all mail behavior. This host only controls
 * the new outer navigation surface and temporary placeholder destinations.
 */
class LinusMailNavigationHost(
    private val context: Context,
    private val root: ViewGroup,
    private val mailContent: View,
) {
    private var destination by mutableStateOf(LinusMailDestination.Mail)
    private lateinit var placeholderView: ComposeView

    fun install() {
        placeholderView = ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            isVisible = false
            setContent {
                LinusMailTheme {
                    LinusMailPlaceholder(destination = destination)
                }
            }
        }
        root.addView(
            placeholderView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )

        val navigationView = ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                LinusMailTheme {
                    LinusMailNavigation(
                        destination = destination,
                        onDestinationSelected = ::selectDestination,
                    )
                }
            }
        }
        root.addView(
            navigationView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                android.view.Gravity.BOTTOM,
            ),
        )
    }

    fun showMail(): Boolean {
        if (destination == LinusMailDestination.Mail) return false
        selectDestination(LinusMailDestination.Mail)
        return true
    }

    private fun selectDestination(newDestination: LinusMailDestination) {
        destination = newDestination
        val showMail = newDestination == LinusMailDestination.Mail
        mailContent.isVisible = showMail
        placeholderView.isVisible = !showMail
    }
}

private enum class LinusMailDestination {
    Mail,
    Actions,
    Search,
}

@Composable
private fun LinusMailNavigation(
    destination: LinusMailDestination,
    onDestinationSelected: (LinusMailDestination) -> Unit,
) {
    NavigationBar(
        modifier = Modifier.navigationBarsPadding(),
    ) {
        LinusMailNavigationItem(
            selected = destination == LinusMailDestination.Mail,
            onClick = { onDestinationSelected(LinusMailDestination.Mail) },
            icon = { Icon(BoltIcons.Outlined.Inbox, contentDescription = null) },
            label = { Text(stringResource(R.string.linus_mail_nav_mail)) },
        )
        LinusMailNavigationItem(
            selected = destination == LinusMailDestination.Actions,
            onClick = { onDestinationSelected(LinusMailDestination.Actions) },
            icon = { Icon(BoltIcons.Outlined.Rocket, contentDescription = null) },
            label = { Text(stringResource(R.string.linus_mail_nav_actions)) },
        )
        LinusMailNavigationItem(
            selected = destination == LinusMailDestination.Search,
            onClick = { onDestinationSelected(LinusMailDestination.Search) },
            icon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            label = { Text(stringResource(R.string.linus_mail_nav_search)) },
        )
    }
}

@Composable
private fun RowScope.LinusMailNavigationItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    label: @Composable () -> Unit,
) {
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    CompositionLocalProvider(LocalContentColor provides contentColor) {
        Box(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onClick)
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                icon()
                label()
            }
        }
    }
}

@Composable
private fun LinusMailPlaceholder(destination: LinusMailDestination) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = when (destination) {
                    LinusMailDestination.Actions -> stringResource(R.string.linus_mail_nav_actions)
                    LinusMailDestination.Search -> stringResource(R.string.linus_mail_nav_search)
                    LinusMailDestination.Mail -> stringResource(R.string.linus_mail_nav_mail)
                },
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = stringResource(R.string.linus_mail_placeholder_description),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}
