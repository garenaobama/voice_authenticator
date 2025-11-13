package com.fangtian.voice_authen.screens

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.fangtian.voice_authen.R
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.SpeakerModel
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import org.vosk.android.StorageService
import java.io.File
import java.io.IOException

class EnrollmentActivity : Activity(), RecognitionListener {

    private lateinit var instructionText: TextView
    private lateinit var recordButton: Button
    private lateinit var resultText: TextView

    private var model: Model? = null
    private var spkModel: SpeakerModel? = null
    private var speechService: SpeechService? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.enrollment)

        instructionText = findViewById(R.id.instruction_text)
        recordButton = findViewById(R.id.record_button)
        resultText = findViewById(R.id.result_text)

        recordButton.setOnClickListener {
            record()
        }
        recordButton.isEnabled = false

        val permissionCheck = ContextCompat.checkSelfPermission(
            applicationContext,
            Manifest.permission.RECORD_AUDIO
        )
        if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                PERMISSIONS_REQUEST_RECORD_AUDIO
            )
        } else {
            initModel()
        }
    }

    private fun initModel() {
        StorageService.unpack(this, "model-en-us", "model",
            { model: Model ->
                this.model = model
                initSpeakerModel()
            },
            { exception: IOException ->
                setErrorState("Failed to unpack the model: " + exception.message)
            })
    }

    private fun initSpeakerModel() {
        StorageService.unpack(this, "vosk-model-spk-0.4", "spk-model",
            { model: Model ->
                spkModel = SpeakerModel(model.path)
                recordButton.isEnabled = true
            },
            { exception: IOException ->
                setErrorState("Failed to unpack the speaker model: " + exception.message)
            })
    }

    private fun record() {
        if (speechService != null) {
            speechService?.stop()
            speechService = null
            recordButton.text = "Start Recording"
        } else {
            recordButton.text = "Stop Recording"
            try {
                val rec = Recognizer(model, 16000.0f, spkModel)
                speechService = SpeechService(rec, 16000.0f)
                speechService?.startListening(this)
                android.os.Handler().postDelayed({
                    record()
                }, 10000)
            } catch (e: IOException) {
                setErrorState(e.message ?: "")
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSIONS_REQUEST_RECORD_AUDIO) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                initModel()
            } else {
                finish()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        speechService?.stop()
        spkModel?.close()
    }

    override fun onResult(hypothesis: String) {
        // We are not interested in the result, just the speaker vector
    }

    override fun onFinalResult(hypothesis: String) {
        val json = JSONObject(hypothesis)
        if (json.has("spk")) {
            val spk = json.getJSONArray("spk").toString()
            try {
                val file = File(this.filesDir, "spk.model")
                file.writeText(spk)
                resultText.text = "Speaker model saved successfully"
            } catch (e: IOException) {
                setErrorState("Failed to save speaker model: " + e.message)
            }
        }
    }

    override fun onPartialResult(hypothesis: String) {
        // We are not interested in the result, just the speaker vector
    }

    override fun onError(e: Exception) {
        setErrorState(e.message ?: "")
    }

    override fun onTimeout() {
        // Nothing to do
    }

    private fun setErrorState(message: String) {
        resultText.text = message
        recordButton.isEnabled = false
    }

    companion object {
        private const val PERMISSIONS_REQUEST_RECORD_AUDIO = 1
    }
}