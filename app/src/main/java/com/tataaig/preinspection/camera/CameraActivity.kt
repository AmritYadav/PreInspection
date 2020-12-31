package com.tataaig.preinspection.camera

import android.os.Bundle
import android.view.Window
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.tataaig.preinspection.R
import com.tataaig.preinspection.camera.fragments.CameraFragment
import com.tataaig.preinspection.camera.fragments.PreviewFragment
import com.tataaig.preinspection.databinding.ActivityCameraBinding
import java.io.File

class CameraActivity : AppCompatActivity(), OnVideoRecordingComplete, OnPreviewActionClickListener {

    private lateinit var binding: ActivityCameraBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCameraBinding.inflate(layoutInflater)
        setContentView(binding.root)

        openCustomCamera()
    }

    override fun onComplete(recordingDir: File?, videoPath: String?) {
        val previewFrag = PreviewFragment.newInstance(recordingDir, videoPath)
        supportFragmentManager.beginTransaction()
            .replace(binding.container.id, previewFrag)
            .commit()
    }

    override fun onAction(action: PreviewAction) {
        when(action) {
            PreviewAction.DONE -> finish()
            PreviewAction.RETAKE -> openCustomCamera()
        }
    }

    private fun openCustomCamera() {
        supportFragmentManager.beginTransaction()
            .replace(binding.container.id, CameraFragment())
            .commit()
    }
}