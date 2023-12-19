package com.mtkdownload

import android.os.Bundle
import android.os.Handler
import android.util.Log
import java.io.IOException

/** Nested class that performs the restart  */
class DeleteRunnable internal constructor(val mtkDownload: MTKDownload,var mHandler: Handler) : Runnable {
    init {
        val sharedPreferences = mtkDownload.sharedPreferences
        GPS_bluetooth_id = sharedPreferences.getString("bluetoothListPref", "-1").toString()
    }

    override fun run() {
        Log.v(TAG, "+++ DeleteRunnable.run() +++")
        sendMessageField("Deleting log from GPS")
        val gpsdev = GPSrxtx(mtkDownload.getmBluetoothAdapter(), GPS_bluetooth_id)
        if (gpsdev.connect()) {
            // Send the command to clear the log
            try {
                gpsdev.sendCommand("PMTK182,6,1")
            } catch (e: IOException) {
                sendMessageField("Failed")
                gpsdev.close()
                return
            }
            // Wait for reply from the device
            try {
                gpsdev.waitForReply("PMTK001,182,6,3", 60.0, null)
            } catch (e: IOException) {
                // TODO Auto-generated catch block
                e.printStackTrace()
            } catch (e: InterruptedException) {
                // TODO Auto-generated catch block
                e.printStackTrace()
            }
            gpsdev.close()
            sendMessageField("Delete succeeded!")
        } else {
            sendMessageField("Error, could not connect to GPS device")
        }
        sendCloseProgress()
        Log.d(mtkDownload.logTAG, "++++ Done: DeleteRunnable.run()")
    }

    private fun sendMessageField(message: String) {
        val msg = mHandler.obtainMessage()
        val b = Bundle()
        b.putString(mtkDownload.MESSAGEFIELD, message)
        msg.data = b
        mHandler.sendMessage(msg)
    }

    private fun sendCloseProgress() {
        val msg = mHandler.obtainMessage()
        val b = Bundle()
        b.putInt(mtkDownload.CLOSE_PROGRESS, 1)
        msg.data = b
        mHandler.sendMessage(msg)
    }

    companion object {
        const val TAG = "DeleteRunnable"
        private var GPS_bluetooth_id: String=""
    }
}