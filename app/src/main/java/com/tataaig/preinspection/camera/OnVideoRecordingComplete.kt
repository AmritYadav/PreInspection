package com.tataaig.preinspection.camera

import java.io.File

interface OnVideoRecordingComplete {
    fun onComplete(recordingDir: File?, videoPath: String?)
}