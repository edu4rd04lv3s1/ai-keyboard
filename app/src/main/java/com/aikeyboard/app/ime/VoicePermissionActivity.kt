package com.aikeyboard.app.ime

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * Activity transparente e sem UI cujo único papel é pedir a permissão de
 * microfone (RECORD_AUDIO). Um IME não tem Activity em primeiro plano, então
 * não consegue solicitar permissões perigosas sozinho — esta "trampolim"
 * resolve isso: pede a permissão e finaliza imediatamente. Se concedida,
 * sinaliza para o teclado iniciar o ditado assim que o foco voltar ao campo.
 */
class VoicePermissionActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (hasMicPermission(this)) {
            VoiceInputController.autoStartPending = true
            finish()
            return
        }
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.RECORD_AUDIO),
            REQUEST_CODE
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        VoiceInputController.autoStartPending =
            grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
        finish()
    }

    companion object {
        private const val REQUEST_CODE = 7001

        fun hasMicPermission(context: Context): Boolean =
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
    }
}
