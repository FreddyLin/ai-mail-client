package net.thunderbird.android.provider

import app.k9mail.core.android.common.provider.NotificationIconResourceProvider

class TbAppIconNotificationProvider : NotificationIconResourceProvider {
    override val pushNotificationIcon: Int
        get() = net.thunderbird.android.R.drawable.ic_logo_linusmail_white
}
