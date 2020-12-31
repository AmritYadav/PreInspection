package com.tataaig.preinspection.camera.fragments

import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.LayoutInflater
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.google.gson.JsonParser
import com.tataaig.preinspection.R
import com.tataaig.preinspection.utils.RECORDING_PREFIX
import com.tataaig.preinspection.utils.VIDEO_DIRECTORY
import com.tataaig.preinspection.camera.OnVideoRecordingComplete
import com.tataaig.preinspection.databinding.FragmentCameraBinding
import com.tataaig.preinspection.service.VddService
import okhttp3.*
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class CameraFragment : CameraVideoFragment() {

    private lateinit var binding : FragmentCameraBinding

    private var mOutputFilePath: String? = null
    private var recordingDir: File? = null

    private lateinit var vddService: VddService

    private var mVideoRecordCompleteListener: OnVideoRecordingComplete? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if(context is OnVideoRecordingComplete) {
            mVideoRecordCompleteListener = context
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        binding = FragmentCameraBinding.inflate(inflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        makeInspectionsDirectory()

        val retrofit = getRetrofit()
        vddService = retrofit.create(VddService::class.java)

        binding.mRecordVideo.setOnClickListener { handleMediaRecordAction() }

        binding.takeSnap.setOnClickListener { takeSnapshot(view.context, binding.mTextureView) }
    }

    private fun handleMediaRecordAction() {
        // If media is not recoding then start recording else stop recording
        binding.takeSnap.isEnabled = mIsRecordingVideo
        if (mIsRecordingVideo) {
            try {
                binding.chronometer.stop()
                stopRecordingVideo()
                mVideoRecordCompleteListener?.onComplete(recordingDir, mOutputFilePath)
                recordingDir = null
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else {
            binding.takeSnap.isEnabled = true
            startRecordingVideo()
            Handler(Looper.getMainLooper()).postDelayed({
                sendFrame()
            }, 3000)
            binding.chronometer.base = SystemClock.elapsedRealtime()
            binding.chronometer.start()
            binding.mRecordVideo.setImageResource(R.drawable.ic_stop)
            //  Receive out put file here
            mOutputFilePath = getCurrentFile()?.absolutePath
        }
    }

    override fun getTextureResource(): Int  = binding.mTextureView.id

    override fun getRecordingDirectory(): File?  {
        recordingDir = createNewRecordingDir()
        return recordingDir
    }

    override fun setUp(view: View?) {}

    private fun takeSnapshot(context: Context, textureView: TextureView) {
        val recordingDir = recordingDir ?: return
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val mPath = recordingDir.absolutePath + File.separator + "SHOT_$timeStamp.png"
        Toast.makeText(context, "Capturing Screenshot: $mPath", Toast.LENGTH_SHORT).show()
        val bm = textureView.bitmap
        if (bm == null) Log.e("TAG", "bitmap is null")
        val imageFile = File(mPath)
        try {
            FileOutputStream(imageFile).use { fOut ->
                bm?.compress(Bitmap.CompressFormat.JPEG, 90, fOut)
                fOut.flush()
            }
        } catch (e: FileNotFoundException) {
            Log.e("TAG", "FileNotFoundException")
            e.printStackTrace()
        } catch (e: IOException) {
            Log.e("TAG", "IOException")
            e.printStackTrace()
        }
    }

    private fun makeInspectionsDirectory() {
        val inspectionsDir = File(activity?.filesDir,
            VIDEO_DIRECTORY
        )
        if (!inspectionsDir.exists()) inspectionsDir.mkdirs()
    }

    private fun createNewRecordingDir() : File? {
        val inspectionsDir = File(activity?.filesDir,
            VIDEO_DIRECTORY
        )

        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
        val recordingDirPath = "$RECORDING_PREFIX$timeStamp"

        val recordingDir = File(inspectionsDir.absolutePath, recordingDirPath)
        if (!recordingDir.exists()) recordingDir.mkdirs()

        return if(!recordingDir.exists()) null else recordingDir
    }

    private fun getRetrofit() : Retrofit {
        val client = OkHttpClient.Builder()
        client.connectTimeout(60, TimeUnit.SECONDS)
        client.readTimeout(60, TimeUnit.SECONDS)
        client.writeTimeout(60, TimeUnit.SECONDS)

        return Retrofit.Builder()
            .baseUrl("http://EC2Co-EcsEl-8PQNDLDKBCTM-741217460.ap-south-1.elb.amazonaws.com:5000/")
            .client(client.build())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    private fun uploadFrame(bitmap: Bitmap?) {
        val stream = ByteArrayOutputStream()
        bitmap?.compress(Bitmap.CompressFormat.JPEG, 85, stream)
        val image = stream.toByteArray()
        val reqFile: RequestBody =
            RequestBody.create(MediaType.parse("application/octet"), image)

        val body = MultipartBody.Part.createFormData("image", "gframe", reqFile)
        vddService.uploadImage(body).enqueue(object : Callback<ResponseBody> {

            override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                binding.indicator.text = t.localizedMessage
                sendFrame()
            }

            override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {
                if (response.isSuccessful) {
                    try {
                        val responseBody = response.body()?.string()
                        val message = JsonParser().parse(responseBody).asJsonObject.get("message").asString
                        binding.indicator.text = message
                        sendFrame()
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }
                }
            }
        })
    }

    private fun sendFrame() {
        if(mIsRecordingVideo) uploadFrame(binding.mTextureView.bitmap)
    }
}