package com.tataaig.preinspection.camera.fragments

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.media.CamcorderProfile
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Size
import android.util.SparseIntArray
import android.view.Surface
import android.view.TextureView
import android.view.View
import androidx.annotation.NonNull
import com.tataaig.preinspection.utils.RECORDING_PREFIX
import com.tataaig.preinspection.custom.AutoFitTextureView
import java.io.File
import java.io.IOException
import java.util.*
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import kotlin.math.abs

abstract class CameraVideoFragment : BaseFragment() {

    private val TAG = "CameraVideoFragment"

    private val INVERSE_ORIENTATIONS = SparseIntArray()
    private val DEFAULT_ORIENTATIONS = SparseIntArray()

    companion object {
        private const val ASPECT_TOLERANCE = 0.1

        private const val FRAME_CAPTURE_DELAY = 5000L

        private const val SENSOR_ORIENTATION_INVERSE_DEGREES = 270
        private const val SENSOR_ORIENTATION_DEFAULT_DEGREES = 90
    }

    init {
        INVERSE_ORIENTATIONS.append(Surface.ROTATION_270, 0)
        INVERSE_ORIENTATIONS.append(Surface.ROTATION_180, 90)
        INVERSE_ORIENTATIONS.append(Surface.ROTATION_90, 180)
        INVERSE_ORIENTATIONS.append(Surface.ROTATION_0, 270)

        DEFAULT_ORIENTATIONS.append(Surface.ROTATION_90, 0)
        DEFAULT_ORIENTATIONS.append(Surface.ROTATION_0, 90)
        DEFAULT_ORIENTATIONS.append(Surface.ROTATION_270, 180)
        DEFAULT_ORIENTATIONS.append(Surface.ROTATION_180, 270)
    }

    private var mCurrentFile: File? = null

    /**
     * An [AutoFitTextureView] for camera preview.
     */
    private lateinit var mTextureView: AutoFitTextureView

    /**
     * A reference to the opened [CameraDevice].
     */
    private var mCameraDevice: CameraDevice? = null

    /**
     * A reference to the current [CameraCaptureSession] for
     * preview.
     */
    private var mPreviewSession: CameraCaptureSession? = null

    /**
     * {@link TextureView.SurfaceTextureListener} handles several lifecycle events on a
     * {@link TextureView}.
     */
    private val mSurfaceTextureListener: TextureView.SurfaceTextureListener =
        object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureSizeChanged(
                surfaceTexture: SurfaceTexture, w: Int, h: Int
            ) {
                configureTransform(w, h)
            }

            override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) {
            }

            override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture): Boolean {
                return true
            }

            override fun onSurfaceTextureAvailable(surfaceTexture: SurfaceTexture, w: Int, h: Int) {
                openCamera(w, h)
            }

        }

    /**
     * The [Size] of camera preview.
     */
    private var mPreviewSize: Size? = null

    /**
     * MediaRecorder
     */
    private lateinit var mMediaRecorder: MediaRecorder

    /**
     * Whether the app is recording video now
     */
    var mIsRecordingVideo = false

    /**
     * An additional thread for running tasks that shouldn't block the UI.
     */
    private var mBackgroundThread: HandlerThread? = null

    /**
     * A [Handler] for running tasks in the background.
     */
    private var mBackgroundHandler: Handler? = null

    /**
     * A [Semaphore] to prevent the app from exiting before closing the camera.
     */
    private val mCameraOpenCloseLock = Semaphore(1)

    /**
     * {@link CameraDevice.StateCallback} is called when {@link CameraDevice} changes its status.
     */
    private val mStateCallback: CameraDevice.StateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(cameraDevice: CameraDevice) {
            mCameraDevice = cameraDevice
            startPreview()
            mCameraOpenCloseLock.release()
            configureTransform(mTextureView.width, mTextureView.height)
        }

        override fun onDisconnected(cameraDevice: CameraDevice) {
            mCameraOpenCloseLock.release()
            cameraDevice.close()
            mCameraDevice = null
        }

        override fun onError(cameraDevice: CameraDevice, p1: Int) {
            mCameraOpenCloseLock.release()
            cameraDevice.close()
            mCameraDevice = null
            activity?.finish()
        }

    }

    private var mSensorOrientation: Int? = null
    private var mPreviewBuilder: CaptureRequest.Builder? = null

    abstract fun getTextureResource(): Int

    abstract fun getRecordingDirectory(): File?

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        mTextureView = view.findViewById(getTextureResource())
    }

    override fun onResume() {
        super.onResume()
        startBackgroundThread()
        if (mTextureView.isAvailable) {
            openCamera(mTextureView.width, mTextureView.height)
        } else {
            mTextureView.surfaceTextureListener = mSurfaceTextureListener
        }
    }

    override fun onPause() {
        closeCamera()
        stopBackgroundThread()
        super.onPause()
    }

    protected open fun getCurrentFile(): File?  =  mCurrentFile

    /**
     * Starts a background thread and its [Handler].
     */
    private fun startBackgroundThread() {
        mBackgroundThread = HandlerThread("CameraBackground")
        mBackgroundThread?.start()
        mBackgroundHandler = Handler(mBackgroundThread!!.looper)
    }

    /**
     * Stops the background thread and its [Handler].
     */
    private fun stopBackgroundThread() {
        mBackgroundThread?.quitSafely()
        try {
            mBackgroundThread?.join()
            mBackgroundThread = null
            mBackgroundHandler = null
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
    }

    /**
     * Tries to open a {@link CameraDevice}. The result is listened by `mStateCallback`.
     */
    @SuppressLint("MissingPermission")
    private fun openCamera(width: Int, height: Int) {
        val context = activity ?: return

        val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

        try {

            if (!mCameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw RuntimeException("Time out waiting to lock camera opening.")
            }
            // default front camera will activate
            // default front camera will activate
            val cameraId = manager.cameraIdList[0]

            val characteristics = manager.getCameraCharacteristics(cameraId)

            val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            mSensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION)
            if (map == null) {
                throw java.lang.RuntimeException("Cannot get available preview/video sizes")
            }
            val optimalSizes = map.getOutputSizes(SurfaceTexture::class.java)
            mPreviewSize = chooseOptimalSize(optimalSizes, width, height)

            val orientation = resources.configuration.orientation
            if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                mTextureView.setAspectRatio(mPreviewSize!!.width, mPreviewSize!!.height)
            } else {
                mTextureView.setAspectRatio(mPreviewSize!!.height, mPreviewSize!!.width)
            }
            configureTransform(width, height)
            mMediaRecorder = MediaRecorder()
            manager.openCamera(cameraId, mStateCallback, null)
        } catch (e: CameraAccessException) {
            Log.e(TAG, "openCamera: Cannot access the camera.");
        } catch (e: NullPointerException) {
            Log.e(TAG, "Camera2API is not supported on the device.");
        } catch (e: InterruptedException) {
            throw RuntimeException("Interrupted while trying to lock camera opening.");
        }
    }

    /**
     * Create directory and return file
     * returning video file
     */
    private fun getOutputMediaFile(): File? {
        val recordingDir = getRecordingDirectory()
        val timeStamp = recordingDir?.name?.replace(RECORDING_PREFIX, "")
        val mediaFile: File
        mediaFile = File(recordingDir?.path + File.separator + "VID_" + timeStamp + ".mp4")
        return mediaFile
    }

    /**
     * close camera and release object
     */
    private fun closeCamera() {
        try {
            mCameraOpenCloseLock.acquire()
            closePreviewSession()
            if (mCameraDevice != null) {
                mCameraDevice?.close()
                mCameraDevice = null
            }
            mMediaRecorder.release()
        } catch (e: InterruptedException) {
            throw java.lang.RuntimeException("Interrupted while trying to lock camera closing.")
        } finally {
            mCameraOpenCloseLock.release()
        }
    }

    /**
     * Start the camera preview.
     */
    private fun startPreview() {
        if (mCameraDevice ==null || !mTextureView.isAvailable || mPreviewSize == null) {
            return
        }
        try {
            closePreviewSession()
            val texture = mTextureView.surfaceTexture
            texture?.setDefaultBufferSize(mPreviewSize!!.width, mPreviewSize!!.height)
            mPreviewBuilder = mCameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            val previewSurface = Surface(texture)
            mPreviewBuilder?.addTarget(previewSurface)
            mCameraDevice?.createCaptureSession(
                listOf(previewSurface),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(@NonNull session: CameraCaptureSession) {
                        mPreviewSession = session
                        updatePreview()
                    }

                    override fun onConfigureFailed(@NonNull session: CameraCaptureSession) {
                        Log.e("CameraVideoFragment", "onConfigureFailed: Failed ")
                    }
                }, mBackgroundHandler
            )
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    /**
     * Update the camera preview. [.startPreview] needs to be called in advance.
     */
    private fun updatePreview() {
        mCameraDevice ?: return
        val previewBuilder = mPreviewBuilder ?: return
        try {
            setUpCaptureRequestBuilder(previewBuilder)
            val thread = HandlerThread("CameraPreview")
            thread.start()
            mPreviewSession?.setRepeatingRequest(
                previewBuilder.build(),
                null,
                mBackgroundHandler
            )
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    private fun setUpCaptureRequestBuilder(builder: CaptureRequest.Builder) {
        builder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
    }

    /**
     * Given `choices` of `Size`s supported by a camera, chooses the smallest one whose
     * width and height are at least as large as the respective requested values, and whose aspect
     * ratio matches with the specified value.
     *
     * @param choices The list of sizes that the camera supports for the intended output class
     * @param width   The minimum desired width
     * @param height  The minimum desired height
     * @return The optimal `Size`, or an arbitrary one if none were big enough
     */
    private fun chooseOptimalSize(choices: Array<Size>, width: Int, height: Int): Size? {
        // The vertical screen is h/w and the horizontal screen is w/h
        val targetRatio = height.toDouble() / width
        var optimalSize: Size? = null
        var minDiff = Double.MAX_VALUE
        for (size in choices) {
            val ratio = size.width.toDouble() / size.height
            if (abs(ratio - targetRatio) > ASPECT_TOLERANCE) continue
            if (abs(size.height - height) < minDiff) {
                optimalSize = size
                minDiff = abs(size.height - height).toDouble()
            }
        }
        if (optimalSize == null) {
            minDiff = Double.MAX_VALUE
            for (size in choices) {
                if (abs(size.height - height) < minDiff) {
                    optimalSize = size
                    minDiff = abs(size.height - height).toDouble()
                }
            }
        }
        return optimalSize
    }

    /**
     * Configures the necessary [Matrix] transformation to `mTextureView`.
     * This method should not to be called until the camera preview size is determined in
     * openCamera, or until the size of `mTextureView` is fixed.
     *
     * @param viewWidth  The width of `mTextureView`
     * @param viewHeight The height of `mTextureView`
     */
    private fun configureTransform(viewWidth: Int, viewHeight: Int) {
        activity ?: return
        mPreviewSize ?: return

        // preview size selected by the camera
        val cameraHeight = mPreviewSize!!.width
        val cameraWidth = mPreviewSize!!.height

        // Calculate the zoom factor required to size the camera => the size of the View
        val ratioPreview = cameraWidth.toFloat() / cameraHeight
        val ratioView = viewWidth.toFloat() / viewHeight

        val scaleX: Float
        val scaleY: Float
        if (ratioView < ratioPreview) {
            scaleX = ratioPreview / ratioView
            scaleY = 1f
        } else {
            scaleX = 1f
            scaleY = ratioView / ratioPreview
        }

        // Calculate the offset of the View
        val scaledWidth = viewWidth * scaleX
        val scaledHeight = viewHeight * scaleY
        val dx = (viewWidth - scaledWidth) / 2
        val dy = (viewHeight - scaledHeight) / 2

        val matrix = Matrix()
        matrix.postScale(scaleX, scaleY)
        matrix.postTranslate(dx, dy)

        mTextureView.setTransform(matrix)
    }

    @Throws(IOException::class)
    private fun setUpMediaRecorder() {
        val activity = activity ?: return
        mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC)
        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE)
        mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        /**
         * create video output file
         */
        mCurrentFile = getOutputMediaFile()
        /**
         * set output file in media recorder
         */
        mMediaRecorder.setOutputFile(mCurrentFile!!.absolutePath)
        val profile = CamcorderProfile.get(CamcorderProfile.QUALITY_480P)
        mMediaRecorder.setVideoFrameRate(profile.videoFrameRate)
        mMediaRecorder.setVideoSize(profile.videoFrameWidth, profile.videoFrameHeight)
        mMediaRecorder.setVideoEncodingBitRate(profile.videoBitRate)
        mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
        mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        mMediaRecorder.setAudioEncodingBitRate(profile.audioBitRate)
        mMediaRecorder.setAudioSamplingRate(profile.audioSampleRate)
        val rotation = activity.windowManager.defaultDisplay.rotation
        when (mSensorOrientation) {
            SENSOR_ORIENTATION_DEFAULT_DEGREES -> mMediaRecorder.setOrientationHint(
                DEFAULT_ORIENTATIONS.get(rotation)
            )
            SENSOR_ORIENTATION_INVERSE_DEGREES -> mMediaRecorder.setOrientationHint(
                INVERSE_ORIENTATIONS.get(rotation)
            )
        }
        mMediaRecorder.prepare()
    }

    fun startRecordingVideo() {
        val cameraDevice = mCameraDevice ?: return
        val previewSize = mPreviewSize ?: return
        if (!mTextureView.isAvailable) return

        try {
            closePreviewSession()
            setUpMediaRecorder()
            val texture = mTextureView.surfaceTexture
            texture?.setDefaultBufferSize(previewSize.width, previewSize.height)
            mPreviewBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD)
            val surfaces: MutableList<Surface> = ArrayList()

            /**
             * Surface for the camera preview set up
             */
            val previewSurface = Surface(texture)
            surfaces.add(previewSurface)
            mPreviewBuilder!!.addTarget(previewSurface)

            //MediaRecorder setup for surface
            val recorderSurface = mMediaRecorder.surface
            surfaces.add(recorderSurface)
            mPreviewBuilder!!.addTarget(recorderSurface)

            // Start a capture session
            cameraDevice.createCaptureSession(
                surfaces, object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(@NonNull cameraCaptureSession: CameraCaptureSession) {
                        mPreviewSession = cameraCaptureSession
                        updatePreview()
                        activity?.runOnUiThread {
                            mIsRecordingVideo = true
                            // Start recording
                            mMediaRecorder.start()
                        }
                    }

                    override fun onConfigureFailed(@NonNull cameraCaptureSession: CameraCaptureSession) {
                        Log.e("CameraVideoFragment", "onConfigureFailed: Failed")
                    }
                },
                mBackgroundHandler
            )
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun closePreviewSession() {
        if (mPreviewSession != null) {
            mPreviewSession?.close()
            mPreviewSession = null
        }
    }

    fun stopRecordingVideo() {
        mIsRecordingVideo = false
        try {
            mPreviewSession?.stopRepeating()
            mPreviewSession?.abortCaptures()
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }

        // Stop recording
        mMediaRecorder.stop()
        mMediaRecorder.reset()
    }
}