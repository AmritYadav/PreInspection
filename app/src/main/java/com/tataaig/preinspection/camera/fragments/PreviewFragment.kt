package com.tataaig.preinspection.camera.fragments

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.MediaController
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.tataaig.preinspection.R
import com.tataaig.preinspection.camera.OnPreviewActionClickListener
import com.tataaig.preinspection.camera.PreviewAction
import com.tataaig.preinspection.databinding.FragmentPreviewBinding
import java.io.File

private const val ARG_VIDEO_PATH = "video_path"
private const val ARG_RECORDING_DIR = "recording_dir"

class PreviewFragment : Fragment() {

    private lateinit var binding: FragmentPreviewBinding

    private var vidPath: String? = null
    private var recordingDir: File? = null

    private var onPreviewActionClickListener: OnPreviewActionClickListener? = null

    companion object {
        @JvmStatic
        fun newInstance(recordingDir: File?, filePath: String?) =
            PreviewFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_VIDEO_PATH, filePath)
                    putSerializable(ARG_RECORDING_DIR, recordingDir)
                }
            }
    }

    private val backPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            val dialog = AlertDialog.Builder(activity!!)
                .setTitle(R.string.title_discard_recording)
                .setMessage(R.string.message_delete_recording)
                .setPositiveButton("CONTINUE") {_, _ ->
                    recordingDir?.deleteRecursively()
                    isEnabled = false
                    activity?.finish()
                }.setNegativeButton("CANCEL") {_, _ ->

                }.create()
            dialog.show()
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is OnPreviewActionClickListener)
            onPreviewActionClickListener = context
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            vidPath = it.getString(ARG_VIDEO_PATH)
            recordingDir = it.getSerializable(ARG_RECORDING_DIR) as File?
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        binding = FragmentPreviewBinding.inflate(inflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setMediaForRecordVideo()

        binding.mPlayVideo.setOnClickListener {
            binding.mVideoView.start()
            binding.mPlayVideo.visibility = View.GONE
        }

        binding.save.setOnClickListener {
            onPreviewActionClickListener?.onAction(PreviewAction.DONE)
        }

        binding.retake.setOnClickListener {
            recordingDir?.deleteRecursively()
            onPreviewActionClickListener?.onAction(PreviewAction.RETAKE)
        }
    }

    override fun onResume() {
        super.onResume()
        activity?.onBackPressedDispatcher?.addCallback(backPressedCallback)
    }

    private fun setMediaForRecordVideo() {
        with(binding.mVideoView) {
            setMediaController(MediaController(activity))
            requestFocus()
            setVideoPath(vidPath)
            seekTo(100)
            setOnCompletionListener {
                binding.mPlayVideo.visibility = View.VISIBLE
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        backPressedCallback.isEnabled = false
    }
}

