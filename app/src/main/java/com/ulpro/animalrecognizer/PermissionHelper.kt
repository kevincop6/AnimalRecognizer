package com.ulpro.animalrecognizer

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.ActivityResultLauncher
import androidx.core.content.ContextCompat
import android.widget.Toast

class PermissionHelper(private val context: Context) {

    // Función que solicita los permisos necesarios
    fun requestPermissions(permissionLauncher: ActivityResultLauncher<Array<String>>) {
        val permissions = mutableListOf(
            Manifest.permission.INTERNET, // Permiso para acceder a internet
            Manifest.permission.POST_NOTIFICATIONS // Permiso para mostrar notificaciones
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Para Android 14 (API 34) y versiones posteriores
            permissions.add(Manifest.permission.READ_MEDIA_IMAGES) // Permiso para leer imágenes
            permissions.add(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) // Permiso para leer imágenes seleccionadas
        } else {
            // Para versiones anteriores (hasta API 33)
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE) // Permiso para leer almacenamiento externo
        }

        permissionLauncher.launch(permissions.toTypedArray())
    }

    // Función para verificar si todos los permisos necesarios han sido otorgados
    fun arePermissionsGranted(): Boolean {
        val internetPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.INTERNET
        ) == PackageManager.PERMISSION_GRANTED

        val postNotificationsPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED

        val readExternalStoragePermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.READ_MEDIA_IMAGES
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }

        val readVisualUserSelectedPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
        ) == PackageManager.PERMISSION_GRANTED

        return internetPermission &&
                postNotificationsPermission &&
                readExternalStoragePermission &&
                readVisualUserSelectedPermission
    }

    // Función para mostrar un mensaje cuando falta algún permiso
    fun showPermissionError() {
        Toast.makeText(
            context,
            "Some permissions are missing. Please grant the necessary permissions.",
            Toast.LENGTH_LONG
        ).show()
    }
}