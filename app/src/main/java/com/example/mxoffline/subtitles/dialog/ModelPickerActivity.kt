/**
 * Role: System file picker trampoline activity for speech recognition models.
 * Responsibility: Launches the SAF document picker and imports the selected model binary into app storage.
 * Details: Runs with a translucent theme to provide a seamless modal picker overlay experience.
 */
package com.example.mxoffline.subtitles.dialog

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

class ModelPickerActivity : ComponentActivity() {

    private val pickerLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            Toast.makeText(this, "Importing AI model...", Toast.LENGTH_SHORT).show()
            SubtitleModelPromptHelper.importModelFromUri(this, uri) { file ->
                if (file != null) {
                    Toast.makeText(this, "Model ready: ${file.name}", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Failed to import model or invalid file format", Toast.LENGTH_LONG).show()
                }
                finish()
            }
        } else {
            SubtitleModelPromptHelper.clearPendingCallback()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pickerLauncher.launch(arrayOf("*/*"))
    }
}
