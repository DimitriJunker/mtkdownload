package com.mtkdownload

import android.os.Handler
import android.util.Log
import java.io.IOException



class ChangeGPSSettingsRunable internal constructor(mtkDownload: MTKDownload,
                                                    h: Handler,
                                                    private val command: String,
                                                    private val responce: String
) : GeneralRunnable(mtkDownload,h) {
    override fun run() {
        Log.v(mtkDownload.logTAG, "+++ ON ChangeGPSSettingsRunable.run($command, $responce) +++")
        val gpsBluetoothId = mtkDownload.sharedPreferences.getString("bluetoothListPref", "-1").toString()
        val gpsdev = GPSrxtx(mtkDownload.getmBluetoothAdapter(), gpsBluetoothId)
        if (gpsdev.connect()) {
            // Send the command to perform restart
            try {
                gpsdev.sendCommand(command)
            } catch (e: IOException) {
                sendMessageField("Failed")
                gpsdev.close()
                return
            }
            // Wait for reply from the device
            try {
                gpsdev.waitForReply(responce, 60.0, null)
            } catch (e: IOException) {
                // TODO Auto-generated catch block
                e.printStackTrace()
            } catch (e: InterruptedException) {
                // TODO Auto-generated catch block
                e.printStackTrace()
            }
            gpsdev.close()
            sendSettings_MessageField("Changing GPS setting succeed")
        } else {
            sendSettings_MessageField("Error, could not connect to GPS device")
        }
        sendCloseProgress()
        Log.d(mtkDownload.logTAG, "++++ Done: ChangeGPSSettingsRunable()")
    }
}