package com.gil.routines.ui

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

data class BtDevice(val name: String, val address: String)

/** המכשירים המזווגים במכשיר — מהם בוחרים את מערכת הרכב */
object BtDevices {
    fun paired(ctx: Context): List<BtDevice> {
        val granted = ContextCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) return emptyList()

        return runCatching {
            ctx.getSystemService(BluetoothManager::class.java)
                ?.adapter?.bondedDevices
                ?.map { BtDevice(it.name ?: it.address, it.address) }
                ?.sortedBy { it.name }
                .orEmpty()
        }.getOrDefault(emptyList())
    }
}
