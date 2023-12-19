package com.mtkdownload

import android.os.Handler
import android.util.Log
import java.io.IOException

/** Nested class that performs the restart  */
class RestartRunnable internal constructor(mtkDownload: MTKDownload, h: Handler, mode: Int) : GeneralRunnable(mtkDownload,h) {
    private var restartName=""
    private var restartCommand=""
    private var restartResponse=""
    private var valid=false
    init {
        when (mode) {
            1 -> {
                restartName = "Hot start"
                restartCommand = "PMTK101"
                restartResponse = "PMTK010,001"
                valid=true
            }

            2 -> {
                restartName = "Warm start"
                restartCommand = "PMTK102"
                restartResponse = "PMTK010,001"
                valid=true
            }

            3 -> {
                restartName = "Cold start"
                restartCommand = "PMTK103"
                restartResponse = "PMTK010,001"
                valid=true
            }

            else -> {
                sendTOAST("Unrecognized restart mode")
                valid=false
            }
        }
    }

    override fun run() {
        if (valid) {
            Log.v(mtkDownload.logTAG, "+++ ON RestartRunnable.run($restartName) +++")
            var GPS_bluetooth_id = mtkDownload.sharedPreferences.getString("bluetoothListPref", "-1").toString()

            val gpsdev = GPSrxtx(mtkDownload.getmBluetoothAdapter(), GPS_bluetooth_id)
            if (gpsdev.connect()) {
                // Send the command to perform restart
                try {
                    gpsdev.sendCommand(restartCommand)
                } catch (e: IOException) {
                    sendMessageField("Failed")
                    gpsdev.close()
                    return
                }
                // Wait for reply from the device
                try {
                    gpsdev.waitForReply(restartResponse, 60.0, null)
                } catch (e: IOException) {
                    // TODO Auto-generated catch block
                    e.printStackTrace()
                } catch (e: InterruptedException) {
                    // TODO Auto-generated catch block
                    e.printStackTrace()
                }
                gpsdev.close()
                sendMessageField("$restartName succeed")
            } else {
                sendMessageField("Error, could not connect to GPS device")
            }
            sendCloseProgress()
            Log.d(mtkDownload.logTAG, "++++ Done: $restartName")
        }
        else{
            Log.v(mtkDownload.logTAG, "+++ ON RestartRunnable.run( not valid) +++")

        }
    }
}