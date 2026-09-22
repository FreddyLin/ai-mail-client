package com.fsck.k9.ui.messageview

import android.content.ActivityNotFoundException
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.IntentSender.SendIntentException
import android.net.Uri
import android.os.Bundle
import android.os.Parcelable
import android.os.SystemClock
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResultListener
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import app.k9mail.core.android.common.activity.CreateDocumentResultContract
import app.k9mail.core.ui.legacy.designsystem.atom.icon.Icons
import app.k9mail.legacy.message.controller.MessageReference
import com.eygraber.uri.toKmpUri
import com.fsck.k9.activity.MessageCompose
import com.fsck.k9.activity.MessageLoaderHelper
import com.fsck.k9.activity.MessageLoaderHelper.MessageLoaderCallbacks
import com.fsck.k9.activity.MessageLoaderHelperFactory
import com.fsck.k9.activity.compose.MessageActions
import com.fsck.k9.controller.MessagingController
import com.fsck.k9.fragment.AttachmentDownloadDialogFragment
import com.fsck.k9.fragment.ConfirmationDialogFragment
import com.fsck.k9.fragment.ConfirmationDialogFragment.ConfirmationDialogFragmentListener
import com.fsck.k9.helper.HttpsUnsubscribeUri
import com.fsck.k9.helper.MailtoUnsubscribeUri
import com.fsck.k9.helper.UnsubscribeUri
import com.fsck.k9.mail.Message
import com.fsck.k9.mail.Part
import com.fsck.k9.mail.Address
import com.fsck.k9.message.html.HtmlConverter
import com.fsck.k9.mailstore.AttachmentViewInfo
import com.fsck.k9.mailstore.LocalMessage
import com.fsck.k9.mailstore.MessageViewInfo
import com.fsck.k9.provider.RawMessageProvider
import com.fsck.k9.helper.MessageHelper
import com.fsck.k9.ui.R
import com.fsck.k9.ui.base.extensions.withArguments
import com.fsck.k9.ui.choosefolder.ChooseFolderActivity
import com.fsck.k9.ui.choosefolder.ChooseFolderResultContract
import com.fsck.k9.ui.helper.SizeFormatter
import com.fsck.k9.ui.helper.RelativeDateTimeFormatter
import com.fsck.k9.ui.messagedetails.MessageDetailsFragment
import com.fsck.k9.ui.messagelist.smartcategory.SmartCategoryRepository
import com.fsck.k9.ui.messagesource.MessageSourceActivity
import com.fsck.k9.ui.messageview.MessageCryptoPresenter.MessageCryptoMvpView
import com.fsck.k9.ui.settings.account.AccountSettingsActivity
import com.fsck.k9.ui.share.ShareIntentBuilder
import java.util.Locale
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import net.thunderbird.core.android.account.LegacyAccountDto
import net.thunderbird.core.android.account.LegacyAccountDtoManager
import net.thunderbird.core.common.mail.Flag
import net.thunderbird.core.common.provider.AppNameProvider
import net.thunderbird.core.featureflag.FeatureFlagProvider
import net.thunderbird.core.featureflag.keys.GeneratedFeatureFlagKey
import net.thunderbird.core.logging.Logger
import net.thunderbird.core.preference.GeneralSettingsManager
import net.thunderbird.core.preference.interaction.InteractionSettings
import net.thunderbird.core.ui.contract.mvi.observe
import net.thunderbird.core.ui.theme.api.FeatureThemeProvider
import net.thunderbird.core.ui.theme.api.Theme
import net.thunderbird.core.ui.theme.manager.ThemeManager
import net.thunderbird.components.ui.bolt.atom.icon.Icons as BoltIcons
import net.thunderbird.feature.mail.folder.api.OutboxFolderManager
import net.thunderbird.feature.mail.message.export.MessageExporter
import net.thunderbird.feature.mail.message.export.MessageFileNameSuggester
import net.thunderbird.feature.mail.message.reader.api.domain.ReplyAction
import net.thunderbird.feature.mail.message.reader.api.strategy.ReplyActionStrategy
import net.thunderbird.feature.mail.message.reader.api.ui.MessageReaderViewContract
import net.thunderbird.feature.mail.message.reader.api.ui.MessageReaderViewContract.Effect
import net.thunderbird.feature.mail.message.reader.api.ui.MessageReaderViewContract.Event
import net.thunderbird.feature.mail.message.reader.api.ui.bridge.MessageReaderBottomSheet
import net.thunderbird.feature.mail.message.reader.api.ai.MessageReaderAiClassificationInput
import net.thunderbird.feature.mail.message.reader.api.ai.MessageReaderAiClassificationResult
import net.thunderbird.feature.mail.message.reader.api.ai.MessageReaderAiClassifier
import net.thunderbird.feature.mail.message.reader.api.ai.MessageReaderAiCategory
import net.thunderbird.feature.mail.message.reader.api.ai.MessageReaderAiCategoryAssigner
import net.thunderbird.feature.mail.message.reader.api.ai.MessageReaderAiCategoryAssignmentResult
import net.thunderbird.feature.mail.message.reader.api.ai.MessageReaderAiError
import net.thunderbird.feature.mail.message.reader.api.ai.MessageReaderAiSummarizationError
import net.thunderbird.feature.mail.message.reader.api.ai.MessageReaderAiSummarizationInput
import net.thunderbird.feature.mail.message.reader.api.ai.MessageReaderAiSummarizationResult
import net.thunderbird.feature.mail.message.reader.api.ai.MessageReaderAiSummarizer
import net.thunderbird.legacy.logging.Log
import org.koin.android.ext.android.inject
import org.koin.android.ext.android.getKoin
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.compose.koinInject
import org.openintents.openpgp.util.OpenPgpIntentStarter
import net.thunderbird.feature.mail.message.reader.api.R as MessageReaderR
import net.thunderbird.feature.mail.message.list.ui.state.SmartCategory

private const val LINUS_OVERFLOW_TOGGLE_FLAGGED = -1

@Suppress("LargeClass", "TooManyFunctions")
class MessageViewFragment :
    Fragment(),
    ConfirmationDialogFragmentListener,
    AttachmentDisplayController,
    AttachmentViewCallback {

    private val themeManager: ThemeManager by inject()
    private val themeProvider: FeatureThemeProvider by inject()
    private val messageLoaderHelperFactory: MessageLoaderHelperFactory by inject()
    private val accountManager: LegacyAccountDtoManager by inject()
    private val messagingController: MessagingController by inject()
    private val attachmentLoadingController: AttachmentLoadingController by inject()
    private val shareIntentBuilder: ShareIntentBuilder by inject()
    private val generalSettingsManager: GeneralSettingsManager by inject()
    private val outboxFolderManager: OutboxFolderManager by inject()
    private val featureFlagProvider: FeatureFlagProvider by inject()
    private val appNameProvider: AppNameProvider by inject()
    private val messageReaderViewModel: MessageReaderViewContract.ViewModel<Part> by viewModel()
    private val messageHelper: MessageHelper by inject()
    private val messageViewRecipientFormatter: MessageViewRecipientFormatter by inject()
    private val relativeDateTimeFormatter: RelativeDateTimeFormatter by inject()
    private val logger: Logger by inject()
    private val replayAllStrategy: ReplyActionStrategy<LegacyAccountDto, Message> by inject()
    private val aiClassifier: MessageReaderAiClassifier? by lazy { getKoin().getOrNull() }
    private val aiCategoryAssigner: MessageReaderAiCategoryAssigner? by lazy { getKoin().getOrNull() }
    private val aiSummarizer: MessageReaderAiSummarizer? by lazy { getKoin().getOrNull() }
    private val smartCategoryRepository: SmartCategoryRepository by inject()

    private val createDocumentLauncher: ActivityResultLauncher<CreateDocumentResultContract.Input> =
        registerForActivityResult(CreateDocumentResultContract()) { documentUri ->
            onCreateDocumentResult(documentUri)
        }
    private val openDocumentTreeLauncher: ActivityResultLauncher<Uri?> =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { directoryUri ->
            onOpenDocumentTreeResult(directoryUri)
        }
    private val chooseFolderForCopyLauncher: ActivityResultLauncher<ChooseFolderResultContract.Input> =
        registerForActivityResult(ChooseFolderResultContract(ChooseFolderActivity.Action.COPY)) { result ->
            onChooseFolderCopyResult(result)
        }
    private val chooseFolderForMoveLauncher: ActivityResultLauncher<ChooseFolderResultContract.Input> =
        registerForActivityResult(ChooseFolderResultContract(ChooseFolderActivity.Action.MOVE)) { result ->
            onChooseFolderMoveResult(result)
        }

    private lateinit var messageTopView: MessageTopView

    private var message: LocalMessage? = null
    private lateinit var messageLoaderHelper: MessageLoaderHelper
    private lateinit var messageCryptoPresenter: MessageCryptoPresenter
    private var showProgressThreshold: Long? = null
    private var mMessageViewInfo: MessageViewInfo? = null
    private var preferredUnsubscribeUri: UnsubscribeUri? = null

    private val messageExporter: MessageExporter by inject()

    private val fileNameSuggester: MessageFileNameSuggester by inject()

    /**
     * Used to temporarily store the destination folder for refile operations if a confirmation
     * dialog is shown.
     */
    private var destinationFolderId: Long? = null
    private lateinit var fragmentListener: MessageViewFragmentListener

    private lateinit var account: LegacyAccountDto
    lateinit var messageReference: MessageReference
    private var showAccountIndicator: Boolean = true

    private var currentAttachmentViewInfo: AttachmentViewInfo? = null
    private var isDeleteMenuItemDisabled: Boolean = false
    private var wasMessageMarkedAsOpened: Boolean = false
    private var linusReaderOverflowActions: LinusMessageReaderOverflowState? = null
    private var linusReaderReplyActions by mutableStateOf(LinusMessageReaderReplyActionsState())

    // Tracks whether the current Create Document flow is for exporting EML (and not for attachments)
    private var pendingEmlExport: Boolean = false

    private var isActive: Boolean = false
    private var useLinusMailReader: Boolean = false

    private val attachmentListBottomSheetState = MutableStateFlow(persistentListOf<AttachmentListItemModel>())
    private val linusReaderOverflowState = MutableStateFlow<LinusMessageReaderOverflowState?>(null)
    private val aiClassificationState = MutableStateFlow<MessageReaderAiClassificationResult?>(null)
    private val aiSummaryState = MutableStateFlow<MessageViewAiSummaryState>(MessageViewAiSummaryState.Idle)
    private var linusMessageHeaderUiModel by mutableStateOf<LinusMessageHeaderUiModel?>(null)
    private var aiSummaryJob: Job? = null

    private val interactionSettings: InteractionSettings
        get() = generalSettingsManager.getConfig().interaction

    override fun onAttach(context: Context) {
        super.onAttach(context)

        useLinusMailReader = context.resources.getBoolean(R.bool.linus_mail_reader_header_enabled)

        fragmentListener = try {
            activity as MessageViewFragmentListener
        } catch (_: ClassCastException) {
            throw ClassCastException("This fragment must be attached to a MessageViewFragmentListener")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Hide the toolbar menu when first creating this fragment. The menu will be set to visible once this fragment
        // becomes the active page of the view pager in MessageViewContainerFragment.
        if (savedInstanceState == null) {
            setMenuVisibility(false)
        }

        messageReference = MessageReference.parse(arguments?.getString(ARG_REFERENCE))
            ?: error("Invalid argument '$ARG_REFERENCE'")

        showAccountIndicator = arguments?.getBoolean(ARG_SHOW_ACCOUNT_INDICATOR)
            ?: error("Missing argument: '$ARG_SHOW_ACCOUNT_INDICATOR'")

        if (savedInstanceState != null) {
            wasMessageMarkedAsOpened = savedInstanceState.getBoolean(STATE_WAS_MESSAGE_MARKED_AS_OPENED)
            isActive = savedInstanceState.getBoolean(STATE_IS_ACTIVE)
        }

        messageCryptoPresenter = MessageCryptoPresenter(messageCryptoMvpView)
        messageLoaderHelper = messageLoaderHelperFactory.createForMessageView(
            context = requireContext().applicationContext,
            loaderManager = loaderManager,
            fragmentManager = parentFragmentManager,
            callback = messageLoaderCallbacks,
        )

        setFragmentResultListener(MessageDetailsFragment.FRAGMENT_RESULT_KEY, ::onMessageDetailsResult)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val messageViewThemeResourceId = themeManager.messageViewThemeResourceId
        val themedContext = ContextThemeWrapper(inflater.context, messageViewThemeResourceId)
        val layoutInflater = LayoutInflater.from(themedContext)

        val view = layoutInflater.inflate(R.layout.message, container, false)
        messageTopView = view.findViewById(R.id.message_view)

        initializeMessageTopView(messageTopView)

        return view
    }

    private fun initializeMessageTopView(messageTopView: MessageTopView) {
        messageTopView.setShowAccountIndicator(showAccountIndicator)
        val useLinusMessageHeader = resources.getBoolean(R.bool.linus_mail_reader_header_enabled)
        messageTopView.setUseLinusMessageHeader(useLinusMessageHeader)

        val readerShellComposeView = messageTopView.findViewById<ComposeView>(R.id.linus_reader_shell_compose_view)
        readerShellComposeView.isVisible = useLinusMessageHeader
        if (useLinusMessageHeader) {
            readerShellComposeView.apply {
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
                setContent {
                    themeProvider.WithTheme(darkTheme = themeManager.messageViewTheme === Theme.DARK) {
                        val summaryState by aiSummaryState.collectAsState()
                        LinusMessageReaderShell(
                            headerModel = linusMessageHeaderUiModel,
                            summaryState = summaryState,
                            isMessageRead = isMessageRead,
                            onBack = { requireActivity().onBackPressedDispatcher.onBackPressed() },
                            onDelete = ::onDelete,
                            onToggleRead = ::onToggleRead,
                            onOverflow = ::showLinusReaderOverflow,
                            onRecipientsClick = messageHeaderClickListener::onParticipantsContainerClick,
                            onStarClick = ::onToggleFlagged,
                            onSummaryRetry = ::startAiSummaryRequest,
                            onSummaryCollapse = ::collapseAiSummary,
                            onSummaryExpand = ::expandAiSummary,
                            onSummaryRegenerate = ::startAiSummaryRequest,
                        )
                    }
                }
            }
        }

        val sizeFormatter = SizeFormatter(resources)
        val replyActionsComposeView = messageTopView.findViewById<ComposeView>(R.id.linus_reader_reply_actions_compose_view)
        replyActionsComposeView.isVisible = useLinusMessageHeader
        if (useLinusMessageHeader) {
            replyActionsComposeView.apply {
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
                setContent {
                    themeProvider.WithTheme {
                        LinusMessageReaderReplyActions(
                            state = linusReaderReplyActions,
                            onReply = { onReply(forceReplyAction = true) },
                            onReplyAll = ::onReplyAll,
                            onForward = ::onForward,
                        )
                    }
                }
            }
        }

        val composeView = messageTopView.findViewById<ComposeView>(R.id.bottom_sheet_compose_view)
        composeView.apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                themeProvider.WithTheme {
                    val attachments by attachmentListBottomSheetState.collectAsState()
                    val linusOverflowState by linusReaderOverflowState.collectAsState()
                    val (stateHolder, dispatch) = messageReaderViewModel.observe { effect ->
                        when (effect) {
                            Effect.TriggerOnReplyAllListener -> onReplyAll()
                            Effect.TriggerOnReplyListener -> onReply(forceReplyAction = true)
                        }
                    }
                    val state by stateHolder
                    val messageReaderBottomSheetContent = koinInject<MessageReaderBottomSheet>()

                    when {
                        linusOverflowState != null -> {
                            LinusMessageReaderOverflowBottomSheet(
                                primaryActions = linusOverflowState!!.primaryActions,
                                secondaryActions = linusOverflowState!!.secondaryActions,
                                destructiveAction = linusOverflowState!!.destructiveAction,
                                onAction = ::onLinusReaderOverflowAction,
                                onDismissRequest = { linusReaderOverflowState.value = null },
                            )
                        }

                        attachments.isNotEmpty() -> {
                            AttachmentListModalBottomSheet(
                                attachments = attachments,
                                sizeFormatter = sizeFormatter,
                                onDismissRequest = {
                                    attachmentListBottomSheetState.update { persistentListOf() }
                                },
                                onAttachmentClick = { attachment ->
                                    attachmentListBottomSheetState.update { persistentListOf() }
                                    onViewAttachment(attachment)
                                },
                                onSaveClick = { attachment ->
                                    onSaveAttachment(attachment)
                                },
                                onSaveAllClick = { onSaveAllAttachments() },
                            )
                        }

                        state.showReaderActionsBottomSheet -> messageReaderBottomSheetContent.Content(
                            actions = state.messageReaderActions,
                            onClick = { action -> dispatch(Event.OnMessageReaderBottomSheetActionClick(action)) },
                            onDismiss = { dispatch(Event.CloseMessageReaderBottomSheet()) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    aiClassificationState.collectAsState().value?.let { result ->
                        MessageViewAiClassificationDialog(
                            result = result,
                            onDismiss = { aiClassificationState.value = null },
                            onApply = ::onApplyAiCategories,
                        )
                    }
                }
            }
        }

        messageTopView.setAttachmentCallback(this)
        messageTopView.setMessageReaderViewModel(messageReaderViewModel)
        messageTopView.setMessageCryptoPresenter(messageCryptoPresenter)

        messageTopView.setOnToggleFlagClickListener {
            onToggleFlagged()
        }

        messageTopView.setMessageHeaderClickListener(messageHeaderClickListener)

        messageTopView.setOnDownloadButtonClickListener {
            onDownloadButtonClicked()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        updateLinusReaderToolbarVisibility()

        val menuHost: MenuHost = requireActivity()
        menuHost.addMenuProvider(
            object : MenuProvider {
                override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                    if (!isActive) return
                    menuInflater.inflate(R.menu.message_view_option_menu, menu)
                    if (isLinusMailReader()) {
                        menu.findItem(R.id.linus_reader_overflow)?.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
                    } else {
                        menu.findItem(R.id.linus_reader_overflow)?.isVisible = false
                    }
                }

                override fun onPrepareMenu(menu: Menu) {
                    if (!isActive) return
                    prepareMenu(menu)
                }

                override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                    if (!isActive) return false
                    if (isLinusMailReader() && menuItem.itemId == R.id.linus_reader_overflow) {
                        showLinusReaderOverflow()
                        return true
                    }
                    return selectMenuItem(menuItem)
                }
            },
            viewLifecycleOwner,
            Lifecycle.State.RESUMED,
        )

        loadMessage(messageReference)
    }

    private fun loadMessage(messageReference: MessageReference) {
        Log.d("MessageViewFragment displaying message %s", messageReference)

        linusMessageHeaderUiModel = null
        linusReaderReplyActions = LinusMessageReaderReplyActionsState()

        account = accountManager.getAccount(messageReference.accountUuid)
            ?: error("Account ${messageReference.accountUuid} not found")

        messageLoaderHelper.asyncStartOrResumeLoadingMessage(messageReference, null)

        invalidateMenu()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_WAS_MESSAGE_MARKED_AS_OPENED, wasMessageMarkedAsOpened)
        outState.putBoolean(STATE_IS_ACTIVE, isActive)
    }

    override fun setMenuVisibility(menuVisible: Boolean) {
        isActive = menuVisible

        super.setMenuVisibility(menuVisible)
        updateLinusReaderToolbarVisibility()

        if (menuVisible) {
            messageLoaderHelper.resumeCryptoOperationIfNecessary()
        } else {
            // When the menu is hidden, the message associated with this fragment is no longer active. If the user
            // returns to it, we want to mark the message as opened again.
            wasMessageMarkedAsOpened = false
        }
    }

    override fun onResume() {
        super.onResume()
        markMessageAsOpened()
        messageCryptoPresenter.onResume()
    }

    override fun onDestroy() {
        super.onDestroy()

        if (requireActivity().isChangingConfigurations) {
            messageLoaderHelper.onDestroyChangingConfigurations()
        } else {
            messageLoaderHelper.onDestroy()
        }
    }

    @Suppress("LongMethod")
    private fun prepareMenu(menu: Menu) {
        menu.findItem(R.id.delete).apply {
            isVisible = generalSettingsManager.getConfig()
                .display.visualSettings.isMessageViewDeleteActionVisible
            isEnabled = !isDeleteMenuItemDisabled
        }

        val showToggleUnread = !isOutbox
        menu.findItem(R.id.toggle_unread).isVisible = showToggleUnread

        if (showToggleUnread) {
            // Set title of menu item to toggle the read state of the currently displayed message
            if (isMessageRead) {
                menu.findItem(R.id.toggle_unread).setTitle(R.string.mark_as_unread_action)
            } else {
                menu.findItem(R.id.toggle_unread).setTitle(R.string.mark_as_read_action)
            }

            val drawableId = if (isMessageRead) {
                Icons.Outlined.MarkEmailUnread
            } else {
                Icons.Outlined.MarkEmailRead
            }

            val drawable = ContextCompat.getDrawable(requireContext(), drawableId)
            menu.findItem(R.id.toggle_unread).icon = drawable
        }

        if (isMoveCapable) {
            val canMessageBeArchived = canMessageBeArchived()
            val canMessageBeMovedToSpam = canMessageBeMovedToSpam()
            menu.findItem(R.id.move).isVisible =
                generalSettingsManager.getConfig().display.visualSettings.isMessageViewMoveActionVisible

            menu.findItem(R.id.archive).isVisible =
                canMessageBeArchived &&
                    generalSettingsManager.getConfig()
                        .display
                        .visualSettings
                        .isMessageViewArchiveActionVisible

            menu.findItem(R.id.spam).isVisible =
                canMessageBeMovedToSpam &&
                    generalSettingsManager.getConfig()
                        .display
                        .visualSettings
                        .isMessageViewSpamActionVisible

            menu.findItem(R.id.refile_move).isVisible = true
            menu.findItem(R.id.refile_archive).isVisible = canMessageBeArchived
            menu.findItem(R.id.refile_spam).isVisible = canMessageBeMovedToSpam

            menu.findItem(R.id.refile).isVisible = true
        } else {
            menu.findItem(R.id.move).isVisible = false
            menu.findItem(R.id.archive).isVisible = false
            menu.findItem(R.id.spam).isVisible = false

            menu.findItem(R.id.refile).isVisible = false
        }

        menu.findItem(R.id.set_format_plain).isVisible = !isRenderPlainFormat()
        menu.findItem(R.id.set_format_html).isVisible = isRenderPlainFormat()

        if (isCopyCapable) {
            menu.findItem(R.id.copy).isVisible = generalSettingsManager.getConfig()
                .display.visualSettings.isMessageViewCopyActionVisible
            menu.findItem(R.id.refile_copy).isVisible = true
        } else {
            menu.findItem(R.id.copy).isVisible = false
            menu.findItem(R.id.refile_copy).isVisible = false
        }

        menu.findItem(R.id.move_to_drafts).isVisible = isOutbox
        menu.findItem(R.id.unsubscribe).isVisible = canMessageBeUnsubscribed()
        menu.findItem(R.id.show_headers).isVisible = true
        menu.findItem(R.id.export_eml).isVisible =
            featureFlagProvider.provide(GeneratedFeatureFlagKey.MESSAGE_VIEW_ACTION_EXPORT_EML).isEnabled()
        menu.findItem(R.id.print)?.isVisible = true
        menu.findItem(R.id.view_compose).isVisible = true
        menu.findItem(R.id.classify_with_ai).isVisible = aiClassifier != null
        menu.findItem(R.id.summarize_with_ai).isVisible = aiSummarizer != null

        val toggleTheme = menu.findItem(R.id.toggle_message_view_theme)
        if (generalSettingsManager.getConfig().display.coreSettings.fixedMessageViewTheme) {
            toggleTheme.isVisible = false
        } else {
            // Set title of menu item to switch to dark/light theme
            if (themeManager.messageViewTheme === Theme.DARK) {
                toggleTheme.setTitle(R.string.message_view_theme_action_light)
            } else {
                toggleTheme.setTitle(R.string.message_view_theme_action_dark)
            }
            toggleTheme.isVisible = true
        }

        if (isLinusMailReader()) {
            linusReaderOverflowActions = createLinusReaderOverflowState(menu)
            menu.findItem(R.id.linus_reader_overflow)?.isVisible = true
            hideLegacyOverflowItems(menu)
        }
    }

    override fun onDestroyView() {
        if (isLinusMailReader()) {
            activity?.findViewById<View>(R.id.toolbar)?.isVisible = true
        }
        super.onDestroyView()
    }

    private fun updateLinusReaderToolbarVisibility() {
        if (isLinusMailReader()) {
            activity?.findViewById<View>(R.id.toolbar)?.isVisible = !isActive
        }
    }

    private fun isLinusMailReader(): Boolean = useLinusMailReader

    private fun hideLegacyOverflowItems(menu: Menu) {
        listOf(
            R.id.refile,
            R.id.move_to_drafts,
            R.id.unsubscribe,
            R.id.show_headers,
            R.id.export_eml,
            R.id.print,
            R.id.set_format_html,
            R.id.set_format_plain,
            R.id.toggle_message_view_theme,
            R.id.view_compose,
            R.id.classify_with_ai,
            R.id.summarize_with_ai,
        ).forEach { itemId -> menu.findItem(itemId)?.isVisible = false }
    }

    private fun showLinusReaderOverflow() {
        linusReaderOverflowActions?.let { state ->
            linusReaderOverflowState.value = state
        }
    }

    private fun createLinusReaderOverflowState(menu: Menu): LinusMessageReaderOverflowState {
        val primaryActions = buildList {
            add(
                LinusMessageReaderOverflowAction(
                    id = LINUS_OVERFLOW_TOGGLE_FLAGGED,
                    labelResId = if (message?.isSet(Flag.FLAGGED) == true) {
                        R.string.linus_mail_reader_overflow_unmark
                    } else {
                        R.string.linus_mail_reader_overflow_mark
                    },
                    icon = BoltIcons.Outlined.Star,
                ),
            )
            menuAction(
                menu = menu,
                itemId = R.id.toggle_unread,
                labelResId = if (isMessageRead) {
                    R.string.linus_mail_reader_overflow_mark_unread
                } else {
                    R.string.linus_mail_reader_overflow_mark_read
                },
                icon = if (isMessageRead) BoltIcons.Outlined.MarkEmailUnread else BoltIcons.Outlined.MarkEmailRead,
            )?.let(::add)
            menuAction(menu, R.id.archive, R.string.linus_mail_reader_overflow_archive, BoltIcons.Outlined.Archive)?.let(::add)
            menuAction(menu, R.id.move, R.string.linus_mail_reader_overflow_move, BoltIcons.Outlined.DriveFileMove)?.let(::add)
            menuAction(menu, R.id.copy, R.string.linus_mail_reader_overflow_copy, BoltIcons.Outlined.DriveFileMove)?.let(::add)
            menuAction(menu, R.id.spam, R.string.linus_mail_reader_overflow_spam, BoltIcons.Outlined.Report)?.let(::add)
        }

        val secondaryActions = buildList {
            add(
                LinusMessageReaderOverflowAction(
                    id = R.id.share,
                    labelResId = R.string.linus_mail_reader_overflow_share,
                    icon = BoltIcons.Outlined.Upload,
                ),
            )
            menuAction(menu, R.id.print, R.string.linus_mail_reader_overflow_print, BoltIcons.Outlined.Description)
                ?.let(::add)
            menuAction(menu, R.id.show_headers, R.string.linus_mail_reader_overflow_details, BoltIcons.Outlined.Info)
                ?.let(::add)
            menuAction(menu, R.id.classify_with_ai, R.string.ai_classification_action, BoltIcons.Outlined.Spa)
                ?.let(::add)
            if (resources.getBoolean(R.bool.linus_mail_inbox_enabled)) {
                add(
                    LinusMessageReaderOverflowAction(
                        id = R.id.assign_smart_category,
                        labelResId = R.string.assign_smart_category_action,
                        icon = BoltIcons.Outlined.FavoriteFolder,
                    ),
                )
            }
        }

        val destructiveAction = menu.findItem(R.id.delete)?.takeIf { it.isVisible && it.isEnabled }?.let {
            LinusMessageReaderOverflowAction(
                id = R.id.delete,
                labelResId = R.string.linus_mail_reader_overflow_delete,
                icon = BoltIcons.Outlined.Delete,
                isDestructive = true,
            )
        }

        return LinusMessageReaderOverflowState(
            primaryActions = primaryActions,
            secondaryActions = secondaryActions,
            destructiveAction = destructiveAction,
        )
    }

    private fun menuAction(
        menu: Menu,
        itemId: Int,
        labelResId: Int,
        icon: androidx.compose.ui.graphics.vector.ImageVector,
    ): LinusMessageReaderOverflowAction? {
        val item = menu.findItem(itemId) ?: return null
        return if (item.isVisible && item.isEnabled) {
            LinusMessageReaderOverflowAction(itemId, labelResId, icon)
        } else {
            null
        }
    }

    private fun onLinusReaderOverflowAction(actionId: Int) {
        linusReaderOverflowState.value = null
        when (actionId) {
            LINUS_OVERFLOW_TOGGLE_FLAGGED -> onToggleFlagged()
            R.id.delete -> onDelete()
            R.id.share -> onSendAlternate()
            R.id.toggle_unread -> onToggleRead()
            R.id.archive -> onArchive()
            R.id.spam -> onSpam()
            R.id.move -> onMove()
            R.id.copy -> onCopy()
            R.id.print -> printMessage()
            R.id.show_headers -> onShowHeaders()
            R.id.classify_with_ai -> onClassifyWithAi()
            R.id.assign_smart_category -> showSmartCategoryPicker()
        }
    }

    private fun showSmartCategoryPicker() {
        viewLifecycleOwner.lifecycleScope.launch {
            val reference = messageReference.toIdentityString()
            val assignments = smartCategoryRepository.observeAssignments().first()[reference]
            val categories = SmartCategory.entries.filter { it != SmartCategory.ALL }
            val labels = categories.map { category ->
                val isAssigned = assignments?.assigned?.containsKey(category) == true
                (if (isAssigned) "✓ " else "") + getString(category.labelResource())
            }.toTypedArray()

            AlertDialog.Builder(requireContext())
                .setTitle(R.string.assign_smart_category_action)
                .setItems(labels) { _, index ->
                    val category = categories[index]
                    if (assignments?.assigned?.containsKey(category) == true) {
                        smartCategoryRepository.removeCategory(reference, category)
                    } else {
                        smartCategoryRepository.assignCategory(reference, category)
                    }
                }
                .show()
        }
    }

    private fun SmartCategory.labelResource(): Int = when (this) {
        SmartCategory.ALL -> net.thunderbird.feature.mail.message.list.R.string.smart_category_all
        SmartCategory.IMPORTANT -> net.thunderbird.feature.mail.message.list.R.string.smart_category_important
        SmartCategory.ACTION -> net.thunderbird.feature.mail.message.list.R.string.smart_category_action
        SmartCategory.INVOICE -> net.thunderbird.feature.mail.message.list.R.string.smart_category_invoice
        SmartCategory.ORDER -> net.thunderbird.feature.mail.message.list.R.string.smart_category_order
        SmartCategory.NEWSLETTER -> net.thunderbird.feature.mail.message.list.R.string.smart_category_newsletter
    }

    @Suppress("CyclomaticComplexMethod", "ReturnCount")
    private fun selectMenuItem(item: MenuItem): Boolean {
        if (message == null) return false

        when (item.itemId) {
            R.id.toggle_message_view_theme -> onToggleTheme()
            R.id.delete -> onDelete()
            R.id.reply -> onReply()
            R.id.reply_all -> onReplyAll()
            R.id.forward -> onForward()
            R.id.forward_as_attachment -> onForwardAsAttachment()
            R.id.edit_as_new_message -> onEditAsNewMessage()
            R.id.share -> onSendAlternate()
            R.id.toggle_unread -> onToggleRead()
            R.id.archive, R.id.refile_archive -> onArchive()
            R.id.spam, R.id.refile_spam -> onSpam()
            R.id.move, R.id.refile_move -> onMove()
            R.id.copy, R.id.refile_copy -> onCopy()
            R.id.move_to_drafts -> onMoveToDrafts()
            R.id.unsubscribe -> onUnsubscribe()
            R.id.show_headers -> onShowHeaders()
            R.id.print -> {
                printMessage()
                return true
            }

            R.id.export_eml -> if (
                featureFlagProvider.provide(GeneratedFeatureFlagKey.MESSAGE_VIEW_ACTION_EXPORT_EML).isEnabled()
            ) {
                onExportEml()
            } else {
                return true
            }

            R.id.set_format_plain -> onDisplayPlainText()
            R.id.set_format_html -> onDisplayHTML()
            R.id.view_compose -> MessageActions.actionCompose(requireActivity(), account)
            R.id.classify_with_ai -> onClassifyWithAi()
            R.id.summarize_with_ai -> onSummarizeWithAi()
            else -> return false
        }

        return true
    }

    private fun onClassifyWithAi() {
        val classifier = aiClassifier ?: return
        val loadedMessage = message ?: return
        if (aiClassificationState.value is MessageReaderAiClassificationResult.Loading) return

        aiClassificationState.value = MessageReaderAiClassificationResult.Loading
        viewLifecycleOwner.lifecycleScope.launch {
            val sender = loadedMessage.from?.let { addresses ->
                Address.toString(addresses).takeIf { it.isNotBlank() }
            }
            val result = classifier.classify(
                accountId = messageReference.accountUuid,
                input = MessageReaderAiClassificationInput(
                    sender = sender,
                    subject = loadedMessage.subject,
                    preview = loadedMessage.preview,
                ),
            )
            aiClassificationState.value = result
        }
    }

    private fun onSummarizeWithAi() {
        when (val state = aiSummaryState.value) {
            MessageViewAiSummaryState.Idle -> startAiSummaryRequest()
            is MessageViewAiSummaryState.Loading -> Unit
            is MessageViewAiSummaryState.Success -> {
                if (!state.isExpanded) expandAiSummary()
            }
            is MessageViewAiSummaryState.Error -> {
                if (state.previousSummary == null) startAiSummaryRequest()
            }
        }
    }

    private fun collapseAiSummary() {
        aiSummaryState.update { state ->
            if (state is MessageViewAiSummaryState.Success) state.copy(isExpanded = false) else state
        }
    }

    private fun expandAiSummary() {
        aiSummaryState.update { state ->
            if (state is MessageViewAiSummaryState.Success) state.copy(isExpanded = true) else state
        }
    }

    private fun startAiSummaryRequest() {
        val summarizer = aiSummarizer ?: return
        val loadedMessage = message ?: return
        if (aiSummaryState.value is MessageViewAiSummaryState.Loading) return

        val previousSummary = when (val state = aiSummaryState.value) {
            is MessageViewAiSummaryState.Success -> state.summary
            is MessageViewAiSummaryState.Error -> state.previousSummary
            else -> null
        }
        val currentMessageIdentity = messageReference.toIdentityString()
        val content = mMessageViewInfo?.text
            ?.let(HtmlConverter::htmlToText)
            ?.takeIf { it.isNotBlank() }
        val preview = createAiSummaryPreview(loadedMessage.preview, content)
        if (preview == null) {
            aiSummaryState.value = MessageViewAiSummaryState.Error(
                error = MessageReaderAiSummarizationError.INSUFFICIENT_DATA_ACCESS,
                previousSummary = previousSummary,
            )
            return
        }

        aiSummaryJob?.cancel()
        aiSummaryState.value = MessageViewAiSummaryState.Loading(previousSummary)
        aiSummaryJob = viewLifecycleOwner.lifecycleScope.launch {
            val sender = loadedMessage.from?.let { addresses ->
                Address.toString(addresses).takeIf { it.isNotBlank() }
            }
            val result = summarizer.summarize(
                accountId = messageReference.accountUuid,
                input = MessageReaderAiSummarizationInput(
                    sender = sender,
                    subject = loadedMessage.subject,
                    preview = preview,
                    content = content,
                ),
            )

            if (currentMessageIdentity != messageReference.toIdentityString()) return@launch
            aiSummaryState.value = when (result) {
                is MessageReaderAiSummarizationResult.Success -> {
                    MessageViewAiSummaryState.Success(result.summary)
                }
                is MessageReaderAiSummarizationResult.Failure -> {
                    MessageViewAiSummaryState.Error(
                        error = result.error,
                        previousSummary = previousSummary,
                    )
                }
            }
        }
    }

    private fun onApplyAiCategories(categories: Set<MessageReaderAiCategory>) {
        val assigner = aiCategoryAssigner ?: return
        if (aiClassificationState.value !is MessageReaderAiClassificationResult.Success) return

        aiClassificationState.value = MessageReaderAiClassificationResult.Saving
        viewLifecycleOwner.lifecycleScope.launch {
            when (assigner.assign(messageReference.toIdentityString(), categories)) {
                MessageReaderAiCategoryAssignmentResult.Success -> {
                    aiClassificationState.value = null
                    Toast.makeText(requireContext(), R.string.ai_classification_saved, Toast.LENGTH_SHORT).show()
                }
                MessageReaderAiCategoryAssignmentResult.Failure -> {
                    aiClassificationState.value = MessageReaderAiClassificationResult.Failure(
                        MessageReaderAiError.ASSIGNMENT_FAILED,
                    )
                }
            }
        }
    }

    private fun printMessage() {
        val messageViewInfo = mMessageViewInfo ?: return
        MessagePrinter(
            context = requireContext(),
            appName = appNameProvider.appName,
            noSubjectText = getString(R.string.general_no_subject),
        ).print(messageViewInfo)
    }

    private fun onShowHeaders() {
        val launchIntent = MessageSourceActivity.createLaunchIntent(requireActivity(), messageReference)
        startActivity(launchIntent)
    }

    private fun onToggleTheme() {
        themeManager.toggleMessageViewTheme()
        ActivityCompat.recreate(requireActivity())
    }

    private fun showMessage(messageViewInfo: MessageViewInfo) {
        hideKeyboard()

        val handledByCryptoPresenter = messageCryptoPresenter.maybeHandleShowMessage(
            messageTopView,
            account,
            messageViewInfo,
        )

        if (!handledByCryptoPresenter) {
            messageTopView.showMessage(account, messageViewInfo)

            if (account.isOpenPgpProviderConfigured) {
                messageTopView.messageHeaderView.setCryptoStatusDisabled()
            } else {
                messageTopView.messageHeaderView.hideCryptoStatus()
            }
        }

        if (messageViewInfo.subject != null) {
            displaySubject(messageViewInfo.subject)
            updateLinusMessageHeader(messageViewInfo.subject)
        }
    }

    private fun updateLinusMessageHeader(subject: String = message?.subject.orEmpty()) {
        val loadedMessage = message ?: return
        linusMessageHeaderUiModel = createLinusMessageHeaderUiModel(
            message = loadedMessage,
            account = account,
            subject = subject,
            messageHelper = messageHelper,
            recipientFormatter = messageViewRecipientFormatter,
            relativeDateTimeFormatter = relativeDateTimeFormatter,
        )
    }

    private fun hideKeyboard() {
        val activity = activity ?: return

        val inputMethodManager = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val decorView = activity.window.decorView
        inputMethodManager.hideSoftInputFromWindow(decorView.applicationWindowToken, 0)
    }

    private fun displayHeaderForLoadingMessage(message: LocalMessage) {
        val showStar = !isOutbox
        messageTopView.setHeaders(message, account, showStar)

        if (account.isOpenPgpProviderConfigured) {
            messageTopView.messageHeaderView.setCryptoStatusLoading()
        }

        displaySubject(message.subject)
        invalidateMenu()
    }

    private fun displaySubject(subject: String) {
        val displaySubject = subject.ifEmpty { getString(R.string.general_no_subject) }
        messageTopView.setSubject(displaySubject)
    }

    private val messageHeaderClickListener = object : MessageHeaderClickListener {
        override fun onParticipantsContainerClick() {
            val messageDetailsFragment = MessageDetailsFragment.create(messageReference)
            messageDetailsFragment.cryptoResult = messageCryptoPresenter.cryptoResultAnnotation
            messageDetailsFragment.show(parentFragmentManager, "message_details")
        }

        override fun onMenuItemClick(itemId: Int) {
            when (itemId) {
                R.id.reply -> onReply()
                R.id.reply_all -> onReplyAll()
                R.id.forward -> onForward()
                R.id.forward_as_attachment -> onForwardAsAttachment()
                R.id.edit_as_new_message -> onEditAsNewMessage()
                R.id.share -> onSendAlternate()
                else -> error("Missing handler for reply menu item $itemId")
            }
        }

        override fun onViewAllAttachmentsClick() {
            showAttachmentListBottomSheet()
        }
    }

    private fun showAttachmentListBottomSheet() {
        val messageViewInfo = mMessageViewInfo ?: return

        val nonInlineAttachments = messageViewInfo.attachments
            ?.filter { !it.inlineAttachment }
            ?.map { AttachmentListItemModel(attachment = it, isLocked = false) }
            .orEmpty()

        val extraNonInlineAttachments = messageViewInfo.extraAttachments
            ?.filter { !it.inlineAttachment }
            ?.map { AttachmentListItemModel(attachment = it, isLocked = true) }
            .orEmpty()

        val allAttachments = nonInlineAttachments + extraNonInlineAttachments
        if (allAttachments.isEmpty()) return

        attachmentListBottomSheetState.update { allAttachments.toPersistentList() }
    }

    private fun onDownloadButtonClicked() {
        messageTopView.disableDownloadButton()
        messageLoaderHelper.downloadCompleteMessage()
    }

    /**
     * Called from UI thread when user select Delete
     */
    fun onDelete() {
        val message = checkNotNull(message)

        if (interactionSettings.isConfirmDelete ||
            interactionSettings.isConfirmDeleteStarred &&
            message.isSet(Flag.FLAGGED)
        ) {
            showDialog(R.id.dialog_confirm_delete)
        } else {
            delete()
        }
    }

    private fun onDisplayPlainText() {
        messageTopView.renderPlainFormat = true
        mMessageViewInfo?.let { showMessage(it) }
    }

    private fun onDisplayHTML() {
        messageTopView.renderPlainFormat = false
        mMessageViewInfo?.let { showMessage(it) }
    }

    private fun isRenderPlainFormat(): Boolean {
        return messageTopView.renderPlainFormat
    }

    private fun delete() {
        disableDeleteMenuItem()

        fragmentListener.performNavigationAfterMessageRemoval()

        messagingController.deleteMessage(messageReference)
    }

    private fun disableDeleteMenuItem() {
        isDeleteMenuItemDisabled = true
        invalidateMenu()
    }

    private fun onRefile(destinationFolderId: Long?) {
        if (destinationFolderId == null || !messagingController.isMoveCapable(account)) {
            return
        }

        if (!messagingController.isMoveCapable(messageReference)) {
            Toast.makeText(activity, R.string.move_copy_cannot_copy_unsynced_message, Toast.LENGTH_LONG).show()
            return
        }

        if (destinationFolderId == account.spamFolderId && interactionSettings.isConfirmSpam) {
            this.destinationFolderId = destinationFolderId
            showDialog(R.id.dialog_confirm_spam)
        } else {
            refileMessage(destinationFolderId)
        }
    }

    private fun refileMessage(destinationFolderId: Long) {
        fragmentListener.performNavigationAfterMessageRemoval()

        val sourceFolderId = messageReference.folderId
        messagingController.moveMessage(account, sourceFolderId, messageReference, destinationFolderId)
    }

    fun onReply(forceReplyAction: Boolean = false) {
        val message = this.message ?: return

        val additionalActions = replayAllStrategy.getReplyActions(account, message).additionalActions
        if (!forceReplyAction && ReplyAction.REPLY_ALL in additionalActions) {
            messageReaderViewModel.event(Event.OpenMessageReaderBottomSheet())
        } else {
            fragmentListener.onReply(
                messageReference = message.makeMessageReference(),
                decryptionResultForReply = messageCryptoPresenter.decryptionResultForReply,
            )
        }
    }

    fun onReplyAll() {
        val message = checkNotNull(this.message)

        fragmentListener.onReplyAll(
            messageReference = message.makeMessageReference(),
            decryptionResultForReply = messageCryptoPresenter.decryptionResultForReply,
        )
    }

    fun onForward() {
        val message = checkNotNull(this.message)

        fragmentListener.onForward(
            messageReference = message.makeMessageReference(),
            decryptionResultForReply = messageCryptoPresenter.decryptionResultForReply,
        )
    }

    private fun onForwardAsAttachment() {
        val message = checkNotNull(this.message)

        fragmentListener.onForwardAsAttachment(
            messageReference = message.makeMessageReference(),
            decryptionResultForReply = messageCryptoPresenter.decryptionResultForReply,
        )
    }

    private fun onEditAsNewMessage() {
        val message = checkNotNull(this.message)

        fragmentListener.onEditAsNewMessage(message.makeMessageReference())
    }

    fun onMove() {
        check(messagingController.isMoveCapable(account))
        checkNotNull(message)

        if (!messagingController.isMoveCapable(messageReference)) {
            Toast.makeText(activity, R.string.move_copy_cannot_copy_unsynced_message, Toast.LENGTH_LONG).show()
            return
        }

        chooseFolderForMoveLauncher.launch(
            input = ChooseFolderResultContract.Input(
                accountUuid = account.uuid,
                currentFolderId = messageReference.folderId,
                scrollToFolderId = account.lastSelectedFolderId,
                messageReference = messageReference,
            ),
        )
    }

    fun onCopy() {
        check(messagingController.isCopyCapable(account))
        checkNotNull(message)

        if (!messagingController.isCopyCapable(messageReference)) {
            Toast.makeText(activity, R.string.move_copy_cannot_copy_unsynced_message, Toast.LENGTH_LONG).show()
            return
        }

        chooseFolderForCopyLauncher.launch(
            input = ChooseFolderResultContract.Input(
                accountUuid = account.uuid,
                currentFolderId = messageReference.folderId,
                scrollToFolderId = account.lastSelectedFolderId,
                messageReference = messageReference,
            ),
        )
    }

    private fun onMoveToDrafts() {
        fragmentListener.performNavigationAfterMessageRemoval()

        val account = account
        val folderId = messageReference.folderId
        val messages = listOf(messageReference)
        messagingController.moveToDraftsFolder(account, folderId, messages)
    }

    fun onArchive() {
        if (!account.hasArchiveFolder()) return

        if (!messagingController.isMoveCapable(messageReference)) {
            Toast.makeText(activity, R.string.move_copy_cannot_copy_unsynced_message, Toast.LENGTH_LONG).show()
            return
        }

        fragmentListener.performNavigationAfterMessageRemoval()
        messagingController.archiveMessage(messageReference)
    }

    private fun onSpam() {
        onRefile(account.spamFolderId)
    }

    @Deprecated("Switch to Activity Result API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode and REQUEST_MASK_LOADER_HELPER == REQUEST_MASK_LOADER_HELPER) {
            val maskedRequestCode = requestCode xor REQUEST_MASK_LOADER_HELPER
            messageLoaderHelper.onActivityResult(maskedRequestCode, resultCode, data)
        } else if (requestCode and REQUEST_MASK_CRYPTO_PRESENTER == REQUEST_MASK_CRYPTO_PRESENTER) {
            val maskedRequestCode = requestCode xor REQUEST_MASK_CRYPTO_PRESENTER
            messageCryptoPresenter.onActivityResult(maskedRequestCode, resultCode, data)
        }
    }

    private fun onMessageDetailsResult(requestKey: String, result: Bundle) {
        when (val action = result.getString(MessageDetailsFragment.RESULT_ACTION)) {
            MessageDetailsFragment.ACTION_SEARCH_KEYS -> {
                messageCryptoPresenter.onClickSearchKey()
            }

            MessageDetailsFragment.ACTION_SHOW_WARNING -> {
                messageCryptoPresenter.onClickShowCryptoWarningDetails()
            }

            else -> {
                error("Unsupported action: $action")
            }
        }
    }

    private fun onCreateDocumentResult(uri: Uri?) {
        if (uri == null) return
        require(uri.scheme == ContentResolver.SCHEME_CONTENT) { "content: URI required" }

        if (pendingEmlExport) {
            // Handle EML export via exporter and reset flag regardless of outcome
            val exportUri = uri
            pendingEmlExport = false
            viewLifecycleOwner.lifecycleScope.launch {
                val ctx = requireContext()
                val rawUri = RawMessageProvider.getRawMessageUri(messageReference)
                val result = messageExporter.export(
                    sourceUri = rawUri.toKmpUri(),
                    destinationUri = exportUri.toKmpUri(),
                )
                if (result.isFailure) {
                    Toast.makeText(ctx, R.string.message_view_status_attachment_not_saved, Toast.LENGTH_LONG).show()
                }
            }
            return
        }

        currentAttachmentViewInfo?.let {
            createAttachmentController(it).saveAttachmentTo(lifecycleScope, uri)
        }
    }

    private fun onOpenDocumentTreeResult(directoryUri: Uri?) {
        if (directoryUri == null) return

        val messageView = mMessageViewInfo ?: return
        val attachments = messageView.attachments.filter { !it.inlineAttachment }
        attachments.forEach {
            currentAttachmentViewInfo = it
            createAttachmentController(it)
                .saveAttachmentToDirectory(lifecycleScope, directoryUri)
        }
    }

    private fun onChooseFolderMoveResult(result: ChooseFolderResultContract.Result?) {
        if (result == null) return

        val destinationFolderId = result.folderId
        val messageReferenceString = result.messageReference
        val messageReference = MessageReference.parse(messageReferenceString)
        if (this.messageReference != messageReference) return

        account.setLastSelectedFolderId(destinationFolderId)

        fragmentListener.performNavigationAfterMessageRemoval()

        moveMessage(messageReference, destinationFolderId)
    }

    private fun onChooseFolderCopyResult(result: ChooseFolderResultContract.Result?) {
        if (result == null) return

        val destinationFolderId = result.folderId
        val messageReferenceString = result.messageReference
        val messageReference = MessageReference.parse(messageReferenceString)
        if (this.messageReference != messageReference) return

        account.setLastSelectedFolderId(destinationFolderId)

        copyMessage(messageReference, destinationFolderId)
    }

    @OptIn(ExperimentalTime::class)
    private fun onExportEml() {
        // Mark this flow as an EML export so the result handler doesn't touch attachment logic
        pendingEmlExport = true
        val subject = message?.subject ?: ""
        val dateMillis = (message?.sentDate ?: message?.internalDate)?.time
        val localDateTime = if (dateMillis != null) {
            Instant.fromEpochMilliseconds(dateMillis)
                .toLocalDateTime(TimeZone.UTC)
        } else {
            // Fallback to current local time if message has no dates
            Instant.fromEpochMilliseconds(System.currentTimeMillis())
                .toLocalDateTime(TimeZone.currentSystemDefault())
        }
        val suggestedName = fileNameSuggester.suggestFileName(subject, localDateTime, "eml")
        try {
            createDocumentLauncher.launch(
                input = CreateDocumentResultContract.Input(
                    title = suggestedName,
                    mimeType = "message/rfc822",
                ),
            )
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(requireContext(), R.string.error_activity_not_found, Toast.LENGTH_LONG).show()
        }
    }

    private fun onSendAlternate() {
        val message = checkNotNull(message)

        val shareIntent = shareIntentBuilder.createShareIntent(message)
        val shareTitle = getString(R.string.send_alternate_chooser_title)
        val chooserIntent = Intent.createChooser(shareIntent, shareTitle)

        startActivity(chooserIntent)
    }

    fun onToggleRead() {
        val message = checkNotNull(this.message)
        val isMarkAsUnreadAction = message.isSet(Flag.SEEN)

        toggleFlag(Flag.SEEN)

        if (isMarkAsUnreadAction) {
            fragmentListener.performNavigationAfterMarkAsUnread()
        }
    }

    fun onToggleFlagged() {
        toggleFlag(Flag.FLAGGED)
    }

    private fun toggleFlag(flag: Flag) {
        check(!isOutbox)
        val message = checkNotNull(this.message)

        val newState = !message.isSet(flag)
        messagingController.setFlag(account, message.folder.databaseId, listOf(message), flag, newState)

        messageTopView.setHeaders(message, account, true)
        updateLinusMessageHeader()

        invalidateMenu()
    }

    private fun moveMessage(reference: MessageReference?, folderId: Long) {
        messagingController.moveMessage(account, messageReference.folderId, reference, folderId)
    }

    private fun copyMessage(reference: MessageReference?, folderId: Long) {
        messagingController.copyMessage(account, messageReference.folderId, reference, folderId)
    }

    private fun showDialog(dialogId: Int) {
        val fragment = when (dialogId) {
            R.id.dialog_confirm_delete -> {
                val title = getString(R.string.dialog_confirm_delete_title)
                val message = getString(R.string.dialog_confirm_delete_message)
                val confirmText = getString(R.string.dialog_confirm_delete_confirm_button)
                val cancelText = getString(R.string.dialog_confirm_delete_cancel_button)
                ConfirmationDialogFragment.newInstance(
                    dialogId,
                    title,
                    message,
                    confirmText,
                    cancelText,
                )
            }

            R.id.dialog_confirm_spam -> {
                val title = getString(R.string.dialog_confirm_spam_title)
                val message = resources.getQuantityString(R.plurals.dialog_confirm_spam_message, 1)
                val confirmText = getString(R.string.dialog_confirm_spam_confirm_button)
                val cancelText = getString(R.string.dialog_confirm_spam_cancel_button)
                ConfirmationDialogFragment.newInstance(
                    dialogId,
                    title,
                    message,
                    confirmText,
                    cancelText,
                )
            }

            R.id.dialog_attachment_progress -> {
                val currentAttachmentViewInfo = checkNotNull(this.currentAttachmentViewInfo)

                val message = getString(R.string.dialog_attachment_progress_title)
                val size = currentAttachmentViewInfo.size
                AttachmentDownloadDialogFragment.newInstance(size, message)
            }

            else -> {
                throw RuntimeException("Called showDialog(int) with unknown dialog id.")
            }
        }

        fragment.setTargetFragment(this, dialogId)
        fragment.show(parentFragmentManager, getDialogTag(dialogId))
    }

    private fun removeDialog(dialogId: Int) {
        if (!isAdded) return

        val fragmentManager = parentFragmentManager

        // Make sure the "show dialog" transaction has been processed when we call  findFragmentByTag() below.
        // Otherwise the fragment won't be found and the dialog will never be dismissed.
        fragmentManager.executePendingTransactions()

        val fragment = fragmentManager.findFragmentByTag(getDialogTag(dialogId)) as DialogFragment?
        fragment?.dismissAllowingStateLoss()
    }

    private fun getDialogTag(dialogId: Int): String {
        return String.format(Locale.US, "dialog-%d", dialogId)
    }

    override fun doPositiveClick(dialogId: Int) {
        if (dialogId == R.id.dialog_confirm_delete) {
            delete()
        } else if (dialogId == R.id.dialog_confirm_spam) {
            val destinationFolderId = checkNotNull(this.destinationFolderId)

            refileMessage(destinationFolderId)
            this.destinationFolderId = null
        }
    }

    override fun doNegativeClick(dialogId: Int) = Unit

    override fun dialogCancelled(dialogId: Int) = Unit

    private val isOutbox: Boolean
        get() = messageReference.folderId == outboxFolderManager.getOutboxFolderIdSync(account.id)

    private val isMessageRead: Boolean
        get() = message?.isSet(Flag.SEEN) == true

    private val isCopyCapable: Boolean
        get() = !isOutbox && messagingController.isCopyCapable(account)

    private val isMoveCapable: Boolean
        get() = !isOutbox && messagingController.isMoveCapable(account)

    private fun canMessageBeArchived(): Boolean {
        val archiveFolderId = account.archiveFolderId ?: return false
        return messageReference.folderId != archiveFolderId
    }

    private fun canMessageBeMovedToSpam(): Boolean {
        val spamFolderId = account.spamFolderId ?: return false
        return messageReference.folderId != spamFolderId
    }

    private fun canMessageBeUnsubscribed(): Boolean {
        return preferredUnsubscribeUri != null
    }

    private fun onUnsubscribe() {
        val intent = when (val unsubscribeUri = preferredUnsubscribeUri) {
            is MailtoUnsubscribeUri -> {
                Intent(requireContext(), MessageCompose::class.java).apply {
                    action = Intent.ACTION_VIEW
                    data = unsubscribeUri.uri
                    putExtra(MessageCompose.EXTRA_ACCOUNT, messageReference.accountUuid)
                }
            }

            is HttpsUnsubscribeUri -> {
                Intent(Intent.ACTION_VIEW, unsubscribeUri.uri)
            }

            else -> error("Unknown UnsubscribeUri - $unsubscribeUri")
        }

        startActivity(intent)
    }

    fun runOnMainThread(runnable: Runnable) {
        requireActivity().runOnUiThread(runnable)
    }

    override fun showAttachmentLoadingDialog() {
        showDialog(R.id.dialog_attachment_progress)
    }

    override fun hideAttachmentLoadingDialogOnMainThread() {
        runOnMainThread {
            removeDialog(R.id.dialog_attachment_progress)
        }
    }

    override fun refreshAttachmentThumbnail(attachment: AttachmentViewInfo) {
        messageTopView.refreshAttachmentThumbnail(attachment)
    }

    private fun markMessageAsOpened() {
        val message = message ?: return

        if (!wasMessageMarkedAsOpened) {
            messagingController.markMessageAsOpened(account, message)
            wasMessageMarkedAsOpened = true
        }
    }

    private val messageCryptoMvpView: MessageCryptoMvpView = object : MessageCryptoMvpView {
        override fun redisplayMessage() {
            messageLoaderHelper.asyncReloadMessage()
        }

        @Throws(SendIntentException::class)
        override fun startPendingIntentForCryptoPresenter(intentSender: IntentSender, requestCode: Int) {
            val maskedRequestCode = requestCode or REQUEST_MASK_CRYPTO_PRESENTER
            OpenPgpIntentStarter.startIntentSenderForResult(this@MessageViewFragment, intentSender, maskedRequestCode)
        }

        override fun restartMessageCryptoProcessing() {
            messageTopView.setToLoadingState()
            messageLoaderHelper.asyncRestartMessageCryptoProcessing()
        }

        override fun showCryptoConfigDialog() {
            AccountSettingsActivity.startCryptoSettings(requireActivity(), account.uuid)
        }
    }

    interface MessageViewFragmentListener {
        fun onForward(messageReference: MessageReference, decryptionResultForReply: Parcelable?)
        fun onForwardAsAttachment(messageReference: MessageReference, decryptionResultForReply: Parcelable?)
        fun onEditAsNewMessage(messageReference: MessageReference)
        fun onReplyAll(messageReference: MessageReference, decryptionResultForReply: Parcelable?)
        fun onReply(messageReference: MessageReference, decryptionResultForReply: Parcelable?)
        fun setProgress(enable: Boolean)
        fun performNavigationAfterMessageRemoval()
        fun performNavigationAfterMarkAsUnread()
    }

    private val messageLoaderCallbacks: MessageLoaderCallbacks = object : MessageLoaderCallbacks {
        override fun onMessageDataLoadFinished(message: LocalMessage) {
            aiSummaryJob?.cancel()
            aiSummaryJob = null
            aiSummaryState.value = MessageViewAiSummaryState.Idle
            this@MessageViewFragment.message = message
            updateLinusReaderReplyActions(message)

            displayHeaderForLoadingMessage(message)
            updateLinusMessageHeader(message.subject)
            messageTopView.setToLoadingState()
            showProgressThreshold = null

            // Only mark the message as opened when the fragment is resumed, i.e. when this is the active message.
            if (isResumed) {
                markMessageAsOpened()
            }
        }

        private fun updateLinusReaderReplyActions(message: LocalMessage) {
            val replyActions = replayAllStrategy.getReplyActions(account, message)
            linusReaderReplyActions = LinusMessageReaderReplyActionsState(
                isVisible = !isOutbox,
                showReplyAll = ReplyAction.REPLY_ALL in replyActions.additionalActions,
            )
        }

        override fun onMessageDataLoadFailed() {
            Toast.makeText(activity, R.string.status_loading_error, Toast.LENGTH_LONG).show()
            showProgressThreshold = null
        }

        override fun onMessageViewInfoLoadFinished(messageViewInfo: MessageViewInfo) {
            mMessageViewInfo = messageViewInfo
            showMessage(messageViewInfo)
            preferredUnsubscribeUri = messageViewInfo.preferredUnsubscribeUri
            showProgressThreshold = null
        }

        override fun onMessageViewInfoLoadFailed(messageViewInfo: MessageViewInfo) {
            showMessage(messageViewInfo)
            preferredUnsubscribeUri = null
            showProgressThreshold = null
        }

        override fun setLoadingProgress(current: Int, max: Int) {
            val oldShowProgressThreshold = showProgressThreshold

            if (oldShowProgressThreshold == null) {
                showProgressThreshold = SystemClock.elapsedRealtime() + PROGRESS_THRESHOLD_MILLIS
            } else if (oldShowProgressThreshold == 0L || SystemClock.elapsedRealtime() > oldShowProgressThreshold) {
                showProgressThreshold = 0L
                messageTopView.setLoadingProgress(current, max)
            }
        }

        override fun onDownloadErrorMessageNotFound() {
            messageTopView.enableDownloadButton()
            Toast.makeText(requireContext(), R.string.status_invalid_id_error, Toast.LENGTH_LONG).show()
        }

        override fun onDownloadErrorNetworkError() {
            messageTopView.enableDownloadButton()
            Toast.makeText(requireContext(), R.string.status_network_error, Toast.LENGTH_LONG).show()
        }

        override fun startIntentSenderForMessageLoaderHelper(intentSender: IntentSender, requestCode: Int): Boolean {
            if (!isActive) return false

            showProgressThreshold = null
            try {
                val maskedRequestCode = requestCode or REQUEST_MASK_LOADER_HELPER
                OpenPgpIntentStarter.startIntentSenderForResult(
                    this@MessageViewFragment,
                    intentSender,
                    maskedRequestCode,
                )
            } catch (e: SendIntentException) {
                Log.e(e, "Irrecoverable error calling PendingIntent!")
            }

            return true
        }
    }

    override fun onViewAttachment(attachment: AttachmentViewInfo) {
        currentAttachmentViewInfo = attachment

        createAttachmentController(attachment).viewAttachment(lifecycleScope)
    }

    fun onSaveAllAttachments() {
        try {
            openDocumentTreeLauncher.launch(null)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(requireContext(), R.string.error_activity_not_found, Toast.LENGTH_LONG).show()
        }
    }

    override fun onSaveAttachment(attachment: AttachmentViewInfo) {
        currentAttachmentViewInfo = attachment

        try {
            createDocumentLauncher.launch(
                input = CreateDocumentResultContract.Input(
                    title = attachment.displayName ?: getString(MessageReaderR.string.unnamed_attachment_title),
                    mimeType = requireNotNull(attachment.mimeType) {
                        "Invalid attachment type. The mimeType is null. Attachment = $attachment"
                    },
                ),
            )
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(requireContext(), R.string.error_activity_not_found, Toast.LENGTH_LONG).show()
        }
    }

    private fun createAttachmentController(attachment: AttachmentViewInfo): AttachmentController {
        return AttachmentController(
            context = requireContext(),
            controller = attachmentLoadingController,
            attachmentDisplayController = this,
            attachment = attachment,
            logger = logger,
        )
    }

    private fun invalidateMenu() {
        activity?.invalidateMenu()
    }

    companion object {
        const val REQUEST_MASK_LOADER_HELPER = 1 shl 8
        const val REQUEST_MASK_CRYPTO_PRESENTER = 1 shl 9
        const val PROGRESS_THRESHOLD_MILLIS = 500 * 1000

        private const val ARG_REFERENCE = "reference"
        private const val ARG_SHOW_ACCOUNT_INDICATOR = "showAccountIndicator"

        private const val STATE_WAS_MESSAGE_MARKED_AS_OPENED = "wasMessageMarkedAsOpened"
        private const val STATE_IS_ACTIVE = "isActive"

        fun newInstance(reference: MessageReference, showAccountIndicator: Boolean): MessageViewFragment {
            return MessageViewFragment().withArguments(
                ARG_REFERENCE to reference.toIdentityString(),
                ARG_SHOW_ACCOUNT_INDICATOR to showAccountIndicator,
            )
        }
    }
}
