package com.tataaig.preinspection.main

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.ThumbnailUtils
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.provider.Settings
import android.util.Size
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.SimpleItemAnimator
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import com.tataaig.preinspection.R
import com.tataaig.preinspection.camera.CameraActivity
import com.tataaig.preinspection.databinding.ActivityMainBinding
import com.tataaig.preinspection.main.adapter.InspectionAdapter
import com.tataaig.preinspection.main.model.Inspection
import com.tataaig.preinspection.utils.DividerItemDecoration
import com.tataaig.preinspection.utils.VIDEO_DIRECTORY
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private lateinit var inspectionAdapter: InspectionAdapter

    private val permissionCamera = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecycler()

        binding.record.setOnClickListener {
            if (hasPermission(permissionCamera)) {
                startActivity(Intent(this, CameraActivity::class.java))
            } else {
                requestPermission()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        prepareInspectionList()
    }

    private fun setupRecycler() {
        inspectionAdapter = InspectionAdapter()

        val dividerDecorator =
            DividerItemDecoration(ContextCompat.getDrawable(this, R.drawable.recycler_divider))
        binding.rvInspections.apply {
            (itemAnimator as SimpleItemAnimator).supportsChangeAnimations = false
            addItemDecoration(dividerDecorator)
            adapter = inspectionAdapter
        }
    }

    private fun prepareInspectionList() {
        val inspections = mutableListOf<Inspection>()
        val inspectionsDir = File(
            filesDir,
            VIDEO_DIRECTORY
        )
        if (inspectionsDir.exists()) {
            inspectionsDir.listFiles()?.forEach { file ->
                val vidFile =
                    file.listFiles { _, name -> name.endsWith(".mp4") }
                val allFiles = file.listFiles()?.size ?: 0
                val vidFiles = vidFile?.size ?: 0
                val shots = allFiles - vidFiles
                vidFile?.forEach { videoFile ->
                    val thumbnail = getThumbnail(videoFile)
                    val inspection = Inspection(videoFile.nameWithoutExtension, thumbnail, shots)
                    inspections.add(inspection)
                }
            }
        }
        inspectionAdapter.inspections = inspections.sortedByDescending { it.title }
    }

    private fun getThumbnail(file: File): Bitmap {
        val mSize = Size(96, 96)
        val ca = CancellationSignal()
        return ThumbnailUtils.createVideoThumbnail(file, mSize, ca)
    }

    /**
     * Requesting permissions storage, audio and camera at once
     */
    private fun requestPermission() {
        Dexter.withActivity(this)
            .withPermissions(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
            .withListener(object : MultiplePermissionsListener {
                override fun onPermissionsChecked(report: MultiplePermissionsReport) {
                    // check if all permissions are granted or not
                    if (report.areAllPermissionsGranted()) {
                        startActivity(Intent(this@MainActivity, CameraActivity::class.java))
                    }
                    // check for permanent denial of any permission show alert dialog
                    if (report.isAnyPermissionPermanentlyDenied) {
                        showSettingsDialog()
                    }
                }

                override fun onPermissionRationaleShouldBeShown(
                    permissions: List<PermissionRequest>,
                    token: PermissionToken
                ) {
                    token.continuePermissionRequest()
                }
            }).withErrorListener {
                Toast.makeText(this, "Error occurred! ", Toast.LENGTH_SHORT).show()
            }
            .onSameThread()
            .check()
    }

    /**
     * Showing Alert Dialog with Settings option in case of deny any permission
     */
    private fun showSettingsDialog() {
        val builder: AlertDialog.Builder = AlertDialog.Builder(this)
        builder.setTitle(getString(R.string.message_need_permission))
        builder.setMessage(getString(R.string.message_permission))
        builder.setPositiveButton(getString(R.string.title_go_to_setting)) { dialog, _ ->
            dialog.cancel()
            openSettings()
        }
        builder.show()
    }

    // navigating settings app
    private fun openSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        val uri = Uri.fromParts("package", packageName, null)
        intent.data = uri
        startActivityForResult(intent, 101)
    }

    private fun hasPermission(permissions: Array<String>) = permissions.all { permission ->
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }
}