package net.thunderbird.android

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test
import net.thunderbird.android.provider.TbAppIconNotificationProvider

class TbAppIconNotificationProviderTest {
    @Test
    fun `provides correct Linus Mail notification icon`() {
        val provider = TbAppIconNotificationProvider()
        val icon = provider.pushNotificationIcon

        assertThat(icon)
            .isEqualTo(net.thunderbird.android.R.drawable.ic_logo_linusmail_white)
    }
}
