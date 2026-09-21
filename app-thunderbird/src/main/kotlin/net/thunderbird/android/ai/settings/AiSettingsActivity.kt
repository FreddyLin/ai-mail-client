package net.thunderbird.android.ai.settings

import android.os.Bundle
import androidx.activity.compose.setContent
import com.fsck.k9.ui.base.BaseActivity
import net.thunderbird.core.ui.theme.api.FeatureThemeProvider
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel

class AiSettingsActivity : BaseActivity() {
    private val themeProvider: FeatureThemeProvider by inject()
    private val viewModel: AiSettingsViewModel by viewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            themeProvider.WithTheme {
                AiSettingsScreen(
                    viewModel = viewModel,
                    onBack = ::finish,
                )
            }
        }
    }
}
