package com.videotools.videomerger

import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.SystemClock
import android.provider.Settings
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegSession
import com.arthenica.ffmpegkit.ReturnCode
import com.videotools.videomerger.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Collections

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: VideoListAdapter

    private val videoItems = mutableListOf<VideoItem>()
    private var isMerging = false
    private var activeSession: FFmpegSession? = null
    private var timerJob: Job? = null
    private var currentProgressPercent = 0

    private val pickVideosLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != RESULT_OK) return@registerForActivityResult
        val data = result.data ?: return@registerForActivityResult

        val uris = mutableListOf<Uri>()
        val clipData = data.clipData
        if (clipData != null) {
            for (i in 0 until clipData.itemCount) {
                clipData.getItemAt(i).uri?.let { uris.add(it) }
            }
        } else {
            data.data?.let { uris.add(it) }
        }

        if (uris.isNotEmpty()) {
            addVideosFromUris(uris)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        applySystemBarInsets()
        setupRecyclerView()
        setupActions()
        refreshUiState()
    }

    override fun onDestroy() {
        super.onDestroy()
        timerJob?.cancel()
        activeSession?.let { FFmpegKit.cancel(it.sessionId) }
    }

    private fun applySystemBarInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.rootLayout) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
    }

    private fun setupRecyclerView() {
        adapter = VideoListAdapter(
            onMoveUp = { position ->
                if (position > 0) {
                    Collections.swap(videoItems, position, position - 1)
                    refreshUiState()
                }
            },
            onMoveDown = { position ->
                if (position < videoItems.lastIndex) {
                    Collections.swap(videoItems, position, position + 1)
                    refreshUiState()
                }
            },
            onRemove = { position ->
                val removed = videoItems.removeAt(position)
                appendLog(getString(R.string.msg_file_removed, removed.name))
                refreshUiState()
            }
        )
        binding.rvVideos.layoutManager = LinearLayoutManager(this)
        binding.rvVideos.adapter = adapter
    }

    private fun setupActions() {
        binding.btnAddFiles.setOnClickListener {
            if (!ensureStoragePermission()) return@setOnClickListener
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "video/*"
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            }
            pickVideosLauncher.launch(intent)
        }

        binding.btnMerge.setOnClickListener {
            if (isMerging) {
                cancelMerge()
            } else {
                startMerge()
            }
        }

        binding.btnClearAll.setOnClickListener {
            if (isMerging) return@setOnClickListener
            videoItems.clear()
            binding.edtOutput.setText("")
            binding.progressMerge.setProgressCompat(0, true)
            binding.tvStatus.text = getString(R.string.status_ready)
            appendLog(getString(R.string.msg_all_cleared))
            refreshUiState()
        }
    }

    private fun ensureStoragePermission(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            appendLog(getString(R.string.msg_permission_needed))
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
            return false
        }
        return true
    }

    private fun addVideosFromUris(uris: List<Uri>) {
        binding.tvStatus.text = getString(R.string.status_analyzing)
        binding.btnAddFiles.isEnabled = false

        lifecycleScope.launch {
            for (uri in uris) {
                val path = withContext(Dispatchers.IO) { VideoUtils.resolvePath(this@MainActivity, uri) }
                if (path.isNullOrBlank() || !File(path).exists()) {
                    appendLog(getString(R.string.msg_unsupported_uri))
                    continue
                }
                val item = withContext(Dispatchers.IO) { VideoUtils.probeVideo(path) }
                videoItems.add(item)
                appendLog(getString(R.string.msg_file_added, item.name, item.formattedMetadata()))

                if (binding.edtOutput.text.isNullOrBlank()) {
                    binding.edtOutput.setText(VideoUtils.suggestOutputPath(item.path))
                }
            }
            binding.btnAddFiles.isEnabled = !isMerging
            binding.tvStatus.text = getString(R.string.status_ready)
            refreshUiState()
        }
    }

    private fun startMerge() {
        if (videoItems.isEmpty()) {
            appendLog(getString(R.string.msg_no_files))
            return
        }
        val outputPath = binding.edtOutput.text?.toString()?.trim().orEmpty()
        if (outputPath.isBlank()) {
            appendLog(getString(R.string.msg_invalid_output))
            return
        }

        val totalDurationMs = videoItems.sumOf { it.durationMs }
        val totalFrames = videoItems.sumOf { it.frames }
        val listFile = File(cacheDir, "mergelist.txt")

        val listContent = buildString {
            videoItems.forEach { item ->
                append("file '")
                append(VideoUtils.escapeForConcat(item.path))
                append("'\n")
            }
        }
        listFile.writeText(listContent, Charsets.UTF_8)

        val escapedListPath = listFile.absolutePath.replace("\"", "\\\"")
        val escapedOutputPath = outputPath.replace("\"", "\\\"")
        val command = "-y -f concat -safe 0 -i \"$escapedListPath\" -c copy \"$escapedOutputPath\""

        isMerging = true
        currentProgressPercent = 0
        updateMergingControls(true)
        binding.progressMerge.setProgressCompat(0, false)
        appendLog(getString(R.string.msg_merge_started, videoItems.size, File(outputPath).name))

        val startTime = SystemClock.elapsedRealtime()
        timerJob?.cancel()
        timerJob = lifecycleScope.launch {
            while (isActive && isMerging) {
                val elapsedSec = (SystemClock.elapsedRealtime() - startTime) / 1000.0
                binding.tvStatus.text = getString(R.string.status_merging, currentProgressPercent, elapsedSec)
                delay(100L)
            }
        }

        activeSession = FFmpegKit.executeAsync(
            command,
            { session ->
                val elapsedSec = (SystemClock.elapsedRealtime() - startTime) / 1000.0
                listFile.delete()
                runOnUiThread {
                    isMerging = false
                    activeSession = null
                    timerJob?.cancel()
                    updateMergingControls(false)

                    val rc = session.returnCode
                    when {
                        ReturnCode.isSuccess(rc) -> {
                            currentProgressPercent = 100
                            binding.progressMerge.setProgressCompat(100, true)
                            binding.tvStatus.text = getString(R.string.status_done, elapsedSec)
                            appendLog(getString(R.string.msg_merge_success, elapsedSec, outputPath))
                        }

                        ReturnCode.isCancel(rc) -> {
                            binding.tvStatus.text = getString(R.string.status_cancelled)
                            appendLog(getString(R.string.msg_merge_cancelled))
                        }

                        else -> {
                            binding.tvStatus.text = getString(R.string.status_failed)
                            appendLog(getString(R.string.msg_merge_failed, rc?.value?.toString() ?: "unknown"))
                            session.failStackTrace?.takeIf { it.isNotBlank() }?.let { appendLog(it) }
                        }
                    }
                }
            },
            { /* FFmpeg verbose logs suppressed to keep UI log clean */ },
            { statistics ->
                val percent = when {
                    totalDurationMs > 0L && statistics.time > 0.0 -> {
                        ((statistics.time / totalDurationMs.toDouble()) * 100.0).toInt()
                    }
                    totalFrames > 0L && statistics.videoFrameNumber > 0 -> {
                        ((statistics.videoFrameNumber.toDouble() / totalFrames.toDouble()) * 100.0).toInt()
                    }
                    else -> currentProgressPercent
                }.coerceIn(0, 99)

                currentProgressPercent = percent
                runOnUiThread {
                    binding.progressMerge.setProgressCompat(percent, true)
                }
            }
        )
    }

    private fun cancelMerge() {
        activeSession?.let { FFmpegKit.cancel(it.sessionId) }
    }

    private fun updateMergingControls(merging: Boolean) {
        adapter.controlsEnabled = !merging
        binding.btnAddFiles.isEnabled = !merging
        binding.btnClearAll.isEnabled = !merging && videoItems.isNotEmpty()
        binding.edtOutput.isEnabled = !merging

        if (merging) {
            binding.btnMerge.text = getString(R.string.btn_cancel)
            binding.btnMerge.setIconResource(R.drawable.ic_close)
            binding.btnMerge.backgroundTintList = ColorStateList.valueOf(
                ContextCompat.getColor(this, R.color.status_danger)
            )
        } else {
            binding.btnMerge.text = getString(R.string.btn_merge)
            binding.btnMerge.setIconResource(R.drawable.ic_merge)
            binding.btnMerge.backgroundTintList = ColorStateList.valueOf(
                ContextCompat.getColor(this, R.color.brand_primary)
            )
        }
    }

    private fun refreshUiState() {
        adapter.submitList(videoItems)
        val hasItems = videoItems.isNotEmpty()

        binding.emptyState.visibility = if (hasItems) View.GONE else View.VISIBLE
        binding.rvVideos.visibility = if (hasItems) View.VISIBLE else View.GONE
        binding.btnClearAll.isEnabled = hasItems && !isMerging
        binding.btnClearAll.alpha = if (hasItems && !isMerging) 1f else 0.4f

        binding.tvSubtitle.text = if (hasItems) {
            val totalDuration = VideoUtils.formatDuration(videoItems.sumOf { it.durationMs })
            getString(R.string.subtitle_count, videoItems.size, totalDuration)
        } else {
            getString(R.string.subtitle_count_empty)
        }
    }

    private fun appendLog(message: String) {
        if (binding.tvLog.text.isNotEmpty()) {
            binding.tvLog.append("\n")
        }
        binding.tvLog.append("• $message")
        binding.scrollLog.post {
            binding.scrollLog.fullScroll(View.FOCUS_DOWN)
        }
    }
}
