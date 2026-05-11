package com.biasharaai.ui.chat

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognizerIntent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.biasharaai.R
import com.biasharaai.ai.VoiceInputProcessor
import com.biasharaai.databinding.FragmentChatBinding
import com.biasharaai.ui.base.BaseFragment
import com.google.android.material.chip.Chip
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class ChatFragment : BaseFragment() {

    @Inject
    lateinit var voiceInputProcessor: VoiceInputProcessor

    private var _binding: FragmentChatBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ChatViewModel by viewModels()
    private lateinit var chatAdapter: ChatAdapter

    private val speechLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            matches?.firstOrNull()?.let { transcript ->
                binding.editMessage.append(
                    if (binding.editMessage.text?.isNotBlank() == true) " $transcript" else transcript,
                )
            }
        }
    }

    private val audioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) launchSpeechRecognizer()
        else Snackbar.make(
            binding.root,
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
        setupSuggestionChips()
        setupInput()
        observeMessages()
        observeThinking()
        observeGenerating()
    }

    private fun setupToolbar() {
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_new_chat -> {
                    viewModel.startNewChat()
                    Snackbar.make(binding.root, R.string.chat_new_chat_started, Snackbar.LENGTH_SHORT).show()
                    true
                }
                else -> false
            }
        }
    }

    private fun setupRecyclerView() {
        chatAdapter = ChatAdapter()
        binding.recyclerChat.apply {
            layoutManager = LinearLayoutManager(requireContext()).apply { stackFromEnd = true }
            adapter = chatAdapter
        }
    }

    private fun setupSuggestionChips() {
        val group = binding.chipGroupSuggestions
        group.removeAllViews()
        viewModel.suggestedPrompts().forEach { prompt ->
            val chip = Chip(requireContext()).apply {
                text = prompt
                isCheckable = false
                setOnClickListener {
                    binding.editMessage.setText(prompt)
                    sendMessage()
                }
            }
            group.addView(chip)
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
    }

    private fun onMicTapped() {
        if (viewModel.isGenerating.value) return
        val granted = ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) launchSpeechRecognizer() else audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    private fun launchSpeechRecognizer() {
        try {
            val intent = voiceInputProcessor.createSpeechRecognizerIntent(
                locale = Locale.getDefault(),
                prompt = getString(R.string.chat_mic_prompt),
            )
            speechLauncher.launch(intent)
        } catch (_: Exception) {
            Snackbar.make(binding.root, R.string.chat_mic_unavailable, Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun sendMessage() {
        val text = binding.editMessage.text?.toString() ?: return
        if (text.isBlank()) return
        viewModel.sendMessage(text)
        binding.editMessage.text?.clear()
        hideKeyboard()
    }

    private fun hideKeyboard() {
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.editMessage.windowToken, 0)
    }

    private fun observeMessages() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.messages.collect { messages ->
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
                }
            }
        }
    }
}
