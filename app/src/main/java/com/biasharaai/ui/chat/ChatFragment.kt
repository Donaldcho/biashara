package com.biasharaai.ui.chat

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.speech.RecognizerIntent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.biasharaai.R
import com.biasharaai.ai.AudioCaptureHelper
import com.biasharaai.ai.VoiceInputPreferences
import com.biasharaai.ai.VoiceInputProcessor
import com.biasharaai.data.local.db.AppSettingsDao
import com.biasharaai.locale.LanguagePreferences
import com.biasharaai.voice.BiasharaTtsEngine
import com.biasharaai.voice.CommandHandler
import com.biasharaai.voice.TranscriptionEngine
import com.biasharaai.voice.TranscriptionResult
import com.biasharaai.voice.VoiceIntent
import com.biasharaai.voice.VoiceRouter
import com.biasharaai.voice.VoiceScreenContext
import com.biasharaai.voice.navigateFromVoiceTarget
import coil.load
import com.biasharaai.databinding.FragmentChatBinding
import com.biasharaai.ui.base.BaseFragment
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class ChatFragment : BaseFragment() {

    @Inject
    lateinit var voiceInputProcessor: VoiceInputProcessor

    @Inject
    lateinit var voiceInputPreferences: VoiceInputPreferences

    @Inject
    lateinit var voiceRouter: VoiceRouter

    @Inject
    lateinit var commandHandler: CommandHandler

    @Inject
    lateinit var appSettingsDao: AppSettingsDao

    @Inject
    lateinit var biasharaTtsEngine: BiasharaTtsEngine

    private var _binding: FragmentChatBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ChatViewModel by activityViewModels()
    private lateinit var chatAdapter: ChatAdapter

    private var pendingImagePath: String? = null
    private var wikipediaAugmentNext: Boolean = false
    private var pendingRemoteSkillPrefix: String? = null

    private val pickVisualMedia = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        val b = _binding ?: return@registerForActivityResult
        val path = copyUriToChatCache(uri)
        if (path == null) {
            Snackbar.make(b.root, R.string.chat_error_generic, Snackbar.LENGTH_SHORT).show()
            return@registerForActivityResult
        }
        pendingImagePath = path
        showAttachmentPreview(path)
        Snackbar.make(b.root, R.string.chat_image_ready, Snackbar.LENGTH_SHORT).show()
    }

    private val speechLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
        val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
        val transcript = matches?.firstOrNull()?.trim().orEmpty()
        if (transcript.isNotEmpty()) {
            handleChatVoiceFromText(transcript, TranscriptionEngine.SPEECH_RECOGNIZER)
        }
    }

    private val audioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            launchSpeechRecognizer()
            return@registerForActivityResult
        }
        val root = _binding?.root ?: return@registerForActivityResult
        Snackbar.make(
            root,
            R.string.chat_mic_permission_denied,
            Snackbar.LENGTH_SHORT,
        ).show()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentChatBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupToolbar()
        setupRecyclerView()
        setupInput()
        observeMessages()
        observeSuggestionChips()
        observeThinking()
        observeGenerating()
        observeVoiceInputPreference()
        observeAssistantAutoRead()
    }

    private fun observeVoiceInputPreference() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                voiceInputPreferences.voiceInputEnabled.collect { enabled ->
                    binding.btnMic.visibility = if (enabled) View.VISIBLE else View.GONE
                }
            }
        }
    }

    private fun setupToolbar() {
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_new_chat -> {
                    viewModel.startNewChat()
                    clearSendFlags()
                    Snackbar.make(binding.root, R.string.chat_new_chat_started, Snackbar.LENGTH_SHORT).show()
                    true
                }
                R.id.action_chat_history -> {
                    findNavController().navigate(R.id.action_chatFragment_to_chatHistoryFragment)
                    true
                }
                R.id.action_wikipedia -> {
                    showWikipediaDialog()
                    true
                }
                R.id.action_maps -> {
                    showMapsDialog()
                    true
                }
                R.id.action_load_skills -> {
                    showSkillsUrlDialog()
                    true
                }
                else -> false
            }
        }
    }

    private fun setupRecyclerView() {
        chatAdapter = ChatAdapter(
            onAssistantFeedback = { messageId, vote ->
                viewModel.submitAssistantFeedback(messageId, vote)
            },
        )
        binding.recyclerChat.apply {
            layoutManager = LinearLayoutManager(requireContext()).apply { stackFromEnd = true }
            adapter = chatAdapter
        }
    }

    private fun rebuildSuggestionChips() {
        val group = binding.chipGroupSuggestions
        group.removeAllViews()
        val seenLower = mutableSetOf<String>()
        fun addChip(fullText: String, onClick: () -> Unit) {
            val t = fullText.trim()
            if (t.isEmpty()) return
            val key = t.lowercase(Locale.getDefault())
            if (!seenLower.add(key)) return
            val display = if (t.length > 48) t.take(47) + "…" else t
            val chip = Chip(requireContext()).apply {
                text = display
                contentDescription = t
                isCheckable = false
                setOnClickListener { onClick() }
            }
            group.addView(chip)
        }
        viewModel.recentQueries.value.take(6).forEach { addChip(it) { sendImmediate(it) } }
        viewModel.suggestedPrompts().forEach { addChip(it) { sendImmediate(it) } }
        viewModel.remoteSkillChips.value.forEach { rs ->
            val label = rs.label.trim()
            if (label.isEmpty()) return@forEach
            val display = if (label.length > 48) label.take(47) + "…" else label
            val key = ("skill:" + rs.id + label).lowercase(Locale.getDefault())
            if (!seenLower.add(key)) return@forEach
            val chip = Chip(requireContext()).apply {
                text = display
                contentDescription = label
                isCheckable = false
                setOnClickListener {
                    pendingRemoteSkillPrefix = rs.promptPrefix
                    Snackbar.make(binding.root, R.string.chat_skill_hint_added, Snackbar.LENGTH_SHORT).show()
                }
            }
            group.addView(chip)
        }
    }

    private fun observeSuggestionChips() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(
                    viewModel.messages,
                    viewModel.recentQueries,
                    viewModel.remoteSkillChips,
                ) { msgs, _, _ -> msgs }
                    .collect { messages ->
                        if (messages.isEmpty()) rebuildSuggestionChips()
                    }
            }
        }
    }

    private fun setupInput() {
        binding.btnSend.setOnClickListener {
            if (viewModel.isGenerating.value) viewModel.stopGeneration() else sendMessage()
        }

        binding.editMessage.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                if (!viewModel.isGenerating.value) sendMessage()
                true
            } else false
        }

        binding.btnMic.setOnClickListener { onMicTapped() }
        binding.btnAttach.setOnClickListener {
            if (viewModel.isGenerating.value) return@setOnClickListener
            pickVisualMedia.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
            )
        }
        binding.btnRemoveAttachment.setOnClickListener {
            pendingImagePath = null
            hideAttachmentPreview()
        }
    }

    private fun showAttachmentPreview(absolutePath: String) {
        val b = _binding ?: return
        b.layoutAttachmentPreview.visibility = View.VISIBLE
        b.dividerChatAttachment.visibility = View.VISIBLE
        b.imageAttachmentPreview.load(File(absolutePath)) {
            crossfade(true)
        }
    }

    private fun hideAttachmentPreview() {
        val b = _binding ?: return
        b.layoutAttachmentPreview.visibility = View.GONE
        b.dividerChatAttachment.visibility = View.GONE
        b.imageAttachmentPreview.setImageDrawable(null)
    }

    private fun onMicTapped() {
        if (!voiceInputPreferences.isVoiceInputEnabled()) return
        if (viewModel.isGenerating.value) return
        val granted = ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) launchSpeechRecognizer() else audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    private fun launchSpeechRecognizer() {
        viewLifecycleOwner.lifecycleScope.launch {
            if (voiceInputProcessor.shouldUseOnDeviceAi() &&
                AudioCaptureHelper.hasRecordPermission(requireContext())
            ) {
                val text = runCatching {
                    voiceInputProcessor.transcribeWithAi(
                        Locale.getDefault(),
                        AudioCaptureHelper.DEFAULT_DURATION_MS,
                    )
                }.getOrNull()?.trim().orEmpty()
                if (text.isNotEmpty()) {
                    val result = TranscriptionResult(
                        text = text,
                        language = resolveVoiceLanguageTag(),
                        confidence = 1f,
                        isPartial = false,
                        engine = TranscriptionEngine.WHISPER,
                    )
                    dispatchChatVoiceIntent(voiceRouter.classify(result, VoiceScreenContext.CHAT))
                    return@launch
                }
            }
            launchSystemSpeechRecognizer()
        }
    }

    private fun launchSystemSpeechRecognizer() {
        val root = _binding?.root ?: return
        try {
            val intent = voiceInputProcessor.createSpeechRecognizerIntent(
                locale = Locale.getDefault(),
                prompt = getString(R.string.chat_mic_prompt),
            )
            speechLauncher.launch(intent)
        } catch (_: Exception) {
            Snackbar.make(root, R.string.chat_mic_unavailable, Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun handleChatVoiceFromText(raw: String, engine: TranscriptionEngine) {
        viewLifecycleOwner.lifecycleScope.launch {
            val text = raw.trim()
            if (text.isEmpty()) return@launch
            val result = TranscriptionResult(
                text = text,
                language = resolveVoiceLanguageTag(),
                confidence = 1f,
                isPartial = false,
                engine = engine,
            )
            dispatchChatVoiceIntent(voiceRouter.classify(result, VoiceScreenContext.CHAT))
        }
    }

    private suspend fun dispatchChatVoiceIntent(intent: VoiceIntent) {
        when (intent) {
            is VoiceIntent.Query -> sendImmediate(intent.text)
            is VoiceIntent.Unclassified -> sendImmediate(intent.rawText)
            is VoiceIntent.DataEntry -> {
                val edit = _binding?.editMessage ?: return
                val t = intent.text
                edit.append(
                    if (edit.text?.isNotBlank() == true) " $t" else t,
                )
            }
            is VoiceIntent.Command -> {
                val target = commandHandler.resolveNavigationTarget(intent)
                val root = _binding?.root ?: return
                findNavController().navigateFromVoiceTarget(
                    target = target,
                    onUnknownHint = { hint ->
                        Snackbar.make(
                            root,
                            getString(R.string.chat_voice_unknown_command, hint),
                            Snackbar.LENGTH_LONG,
                        ).show()
                    },
                    onAlreadyAtChat = {
                        Snackbar.make(root, R.string.chat_voice_already_chat, Snackbar.LENGTH_SHORT).show()
                    },
                )
            }
        }
    }

    private fun resolveVoiceLanguageTag(): String {
        LanguagePreferences.getPersistedLocaleTag(requireContext())?.let { tag ->
            val lang = tag.substringBefore('-', missingDelimiterValue = tag).lowercase(Locale.US)
            if (lang.isNotBlank()) return lang
        }
        return Locale.getDefault().language
    }

    private fun preferredTtsLanguageCode(): String? {
        val ctx = requireContext()
        LanguagePreferences.getPersistedLocaleTag(ctx)?.let { tag ->
            val lang = tag.substringBefore('-', missingDelimiterValue = tag).lowercase(Locale.US)
            if (lang.isNotBlank()) return lang
        }
        return ctx.resources.configuration.locales[0]?.language?.lowercase(Locale.US)
    }

    private fun observeAssistantAutoRead() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                var wasGenerating = false
                var lastSpokenAssistantId: Long? = null
                combine(viewModel.messages, viewModel.isGenerating) { m, g -> Pair(m, g) }
                    .collect { (msgs, gen) ->
                        if (msgs.isEmpty()) lastSpokenAssistantId = null
                        val finished = wasGenerating && !gen
                        wasGenerating = gen
                        if (!finished) return@collect
                        val settings = withContext(Dispatchers.IO) {
                            appSettingsDao.getSettingsSync()
                        } ?: return@collect
                        if (!settings.ttsEnabled || !settings.ttsAutoReadQueryAnswers) return@collect
                        val last = msgs.lastOrNull() ?: return@collect
                        if (last.isUser || last.text.isBlank() || last.stableId <= 0L) return@collect
                        if (last.stableId == lastSpokenAssistantId) return@collect
                        lastSpokenAssistantId = last.stableId
                        biasharaTtsEngine.speak(last.text, preferredTtsLanguageCode())
                    }
            }
        }
    }

    private fun sendImmediate(text: String) {
        binding.editMessage.setText(text)
        sendMessage()
    }

    private fun sendMessage() {
        val text = binding.editMessage.text?.toString() ?: ""
        if (text.isBlank() && pendingImagePath == null) return
        if (viewModel.isGenerating.value) return
        viewModel.sendMessage(
            text,
            imageAbsolutePath = pendingImagePath,
            wikipediaAugment = wikipediaAugmentNext,
            remoteSkillPromptPrefix = pendingRemoteSkillPrefix,
        )
        clearSendFlags()
        binding.editMessage.text?.clear()
        hideKeyboard()
    }

    private fun clearSendFlags() {
        pendingImagePath = null
        hideAttachmentPreview()
        wikipediaAugmentNext = false
        pendingRemoteSkillPrefix = null
    }

    private fun copyUriToChatCache(uri: Uri): String? {
        return try {
            val dir = File(requireContext().cacheDir, "chat_images").apply { mkdirs() }
            val out = File(dir, "${System.currentTimeMillis()}.jpg")
            val stream = requireContext().contentResolver.openInputStream(uri) ?: return null
            stream.use { input ->
                out.outputStream().use { output -> input.copyTo(output) }
            }
            out.absolutePath
        } catch (_: Exception) {
            null
        }
    }

    private fun showWikipediaDialog() {
        val density = resources.displayMetrics.density
        val pad = (density * 20).toInt()
        val layout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, (density * 8).toInt(), pad, 0)
        }
        val msg = TextView(requireContext()).apply {
            text = getString(R.string.chat_wikipedia_dialog_message)
            setPadding(0, 0, 0, pad / 2)
        }
        val input = EditText(requireContext()).apply {
            hint = getString(R.string.chat_wikipedia_hint)
        }
        val check = CheckBox(requireContext()).apply {
            text = getString(R.string.chat_wikipedia_augment)
            isChecked = true
        }
        layout.addView(msg)
        layout.addView(input)
        layout.addView(check)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.chat_wikipedia_title)
            .setView(layout)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val q = input.text?.toString()?.trim().orEmpty()
                if (q.isEmpty()) return@setPositiveButton
                binding.editMessage.setText(q)
                wikipediaAugmentNext = check.isChecked
            }
            .show()
    }

    private fun showMapsDialog() {
        val input = EditText(requireContext()).apply {
            hint = getString(R.string.chat_maps_hint)
        }
        val pad = (resources.displayMetrics.density * 24).toInt()
        input.setPadding(pad, pad / 2, pad, pad / 2)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.chat_maps_title)
            .setView(input)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val q = input.text?.toString()?.trim().orEmpty()
                if (q.isEmpty()) return@setPositiveButton
                val uri = Uri.parse("geo:0,0?q=${Uri.encode(q)}")
                val intent = Intent(Intent.ACTION_VIEW, uri)
                runCatching { startActivity(Intent.createChooser(intent, null)) }
            }
            .show()
    }

    private fun showSkillsUrlDialog() {
        val input = EditText(requireContext()).apply {
            hint = getString(R.string.chat_skills_url_hint)
            setText(viewModel.getSavedSkillManifestUrl())
        }
        val pad = (resources.displayMetrics.density * 24).toInt()
        input.setPadding(pad, pad / 2, pad, pad / 2)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.chat_skills_url_title)
            .setView(input)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val url = input.text?.toString()?.trim().orEmpty()
                if (url.isEmpty()) return@setPositiveButton
                viewModel.refreshSkillManifestFromUrl(url) { ok ->
                    val root = _binding?.root ?: return@refreshSkillManifestFromUrl
                    Snackbar.make(
                        root,
                        if (ok) R.string.chat_skills_loaded else R.string.chat_skills_load_failed,
                        Snackbar.LENGTH_SHORT,
                    ).show()
                }
            }
            .show()
    }

    private fun hideKeyboard() {
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.editMessage.windowToken, 0)
    }

    private fun observeMessages() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.messages.collect { messages ->
                    val ttsOn = withContext(Dispatchers.IO) {
                        appSettingsDao.getSettingsSync()?.ttsEnabled
                    } == true
                    chatAdapter.assistantTtsEnabled = ttsOn
                    chatAdapter.submitList(messages) {
                        if (messages.isNotEmpty()) {
                            binding.recyclerChat.smoothScrollToPosition(messages.size - 1)
                        }
                    }
                    binding.layoutEmptyState.visibility =
                        if (messages.isEmpty()) View.VISIBLE else View.GONE
                }
            }
        }
    }

    private fun observeThinking() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.isThinking.collect { thinking ->
                    binding.textTyping.visibility = if (thinking) View.VISIBLE else View.GONE
                }
            }
        }
    }

    private fun observeGenerating() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.isGenerating.collect { generating ->
                    val ctx = requireContext()
                    binding.btnSend.isEnabled = true
                    if (generating) {
                        binding.btnSend.icon = AppCompatResources.getDrawable(ctx, R.drawable.ic_close)
                        binding.btnSend.contentDescription = getString(R.string.chat_stop)
                    } else {
                        binding.btnSend.icon = AppCompatResources.getDrawable(
                            ctx,
                            android.R.drawable.ic_menu_send,
                        )
                        binding.btnSend.contentDescription = getString(R.string.chat_send)
                    }
                    binding.btnMic.isEnabled = !generating
                    binding.btnAttach.isEnabled = !generating
                    binding.btnRemoveAttachment.isEnabled = !generating
                }
            }
        }
    }
}
