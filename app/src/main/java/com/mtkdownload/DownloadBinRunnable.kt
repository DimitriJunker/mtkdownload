package com.mtkdownload

import android.content.ContentValues
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import java.io.BufferedOutputStream
import java.io.BufferedWriter
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.regex.Matcher
import java.util.regex.Pattern

class DownloadBinRunnable(val mtkDownload: MTKDownload,private val dHandler: Handler) : Runnable {
    @Volatile
    var running = true

    private val logTAG = "DownLog"
    private val SIZEOF_SECTOR = 0x10000

    var bin_output_stream: BufferedOutputStream? = null
    var binPath = ""
    var gtPath = ""
    var logWriter: BufferedWriter? = null
    lateinit var gpsdev: GPSrxtx

    // Preferences
    private var create_log_file = false
    private var createGPX = true
    private var SIZEOF_CHUNK = 0x0800
    private var SIZEOF_GPS_MEMORY = 0
    private var OVERWRITE_MODE = 0
    private var GPS_bluetooth_id = ""

    init {

        // Get some preferences information
        create_log_file = mtkDownload.sharedPreferences.getBoolean("createDebugPref", false)
        createGPX = mtkDownload.sharedPreferences.getBoolean("createGPXPref", true)
        SIZEOF_CHUNK =
            mtkDownload.sharedPreferences.getString("chunkSizePref", "4096").toString().toInt()
        SIZEOF_GPS_MEMORY =
            mtkDownload.sharedPreferences.getString("memSizePref", "0").toString().toInt()
        OVERWRITE_MODE =
            mtkDownload.sharedPreferences.getString("overwritePref", "0").toString().toInt()
        GPS_bluetooth_id =
            mtkDownload.sharedPreferences.getString("bluetoothListPref", "-1").toString()
    }

    fun getFlashSize(model: Int): Int {
        // 8 Mbit = 1 Mb
        if (model == 0x1388) return 8 * 1024 * 1024 / 8 // 757/ZI v1
        if (model == 0x5202) return 8 * 1024 * 1024 / 8 // 757/ZI v2
        // 32 Mbit = 4 Mb
        if (model == 0x0000) return 32 * 1024 * 1024 / 8 // Holux M-1200E
        if (model == 0x0001) return 32 * 1024 * 1024 / 8 // Qstarz BT-Q1000X
        if (model == 0x0004) return 32 * 1024 * 1024 / 8 // 747 A+ GPS Trip Recorder
        if (model == 0x0005) return 32 * 1024 * 1024 / 8 // Qstarz BT-Q1000P
        if (model == 0x0006) return 32 * 1024 * 1024 / 8 // 747 A+ GPS Trip Recorder
        if (model == 0x0008) return 32 * 1024 * 1024 / 8 // Pentagram PathFinder P 3106
        if (model == 0x000F) return 32 * 1024 * 1024 / 8 // 747 A+ GPS Trip Recorder
        if (model == 0x005C) return 32 * 1024 * 1024 / 8 // Holux M-1000C
        return if (model == 0x8300) 32 * 1024 * 1024 / 8 else 16 * 1024 * 1024 / 8 // Qstarz BT-1200
        // 16Mbit -> 2Mb
        // 0x0051    i-Blue 737, Qstarz 810, Polaris iBT-GPS, Holux M1000
        // 0x0002    Qstarz 815
        // 0x001b    i-Blue 747
        // 0x001d    BT-Q1000 / BGL-32
        // 0x0131    EB-85A
    }

    fun Log(text: String?) {
        if (logWriter != null) {
            // Create a unique file for writing the log files to
            val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            val time = sdf.format(Date())
            try {
                logWriter?.append(time)
                logWriter?.append(text)
                logWriter?.append('\n')
                logWriter?.flush()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    fun errorWhileDownloading() {
        closeGPS_bin()
        //cleanup();

        // Report download failed!
        sendCloseProgress()
        sendMessageToMessageField("Download failed")
    }

    fun errorWhileDownloading_cont() {    //DYJ
        closeGPS_bin()
        //cleanup();

        // Report download failed but try to continue!
        sendRestartGPS()
        sendMessageToMessageField(
            java.lang.String.format(
                "try restart and continue: %s",
                mtkDownload.fileTimeStamp
            )
        )
    }

    fun closeGPS_only() {
        Log.v(logTAG, "Closing GPS device")
        gpsdev.close()
    }

    fun closeGPS_bin() {
        Log.v(logTAG, "Closing GPS device and bin")
        gpsdev.close()

        // Close the bin file
        try {
            if (bin_output_stream != null) {
                bin_output_stream!!.flush()
                bin_output_stream!!.close()
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    fun cleanup() {
        // Clean up, delete the bin file and perhaps the log file
        val f1 = File(binPath)
        f1.delete()
        val f2 = File(
            gtPath
        )
        f2.delete()
    }

    override fun run() {
        var reply = ""
        var p: Pattern
        var m: Matcher

        sendMessageToMessageField("Getting log from GPS")   // hier endet es
        sendPercentageComplete(0)
        // Open log file
        if (create_log_file) {
            val (lw, path) = mtkDownload.getBufWr(
                "gpslog${mtkDownload.fileTimeStamp}.txt",
                "text/plain",
                false,
                0
            )
            logWriter = lw
            gtPath = path
        } else {
            logWriter = null
            gtPath = ""
        }
        Log.v(logTAG, String.format("Trying to connect to GPS device: %s", GPS_bluetooth_id))
        gpsdev = GPSrxtx(mtkDownload.getmBluetoothAdapter(), GPS_bluetooth_id)
        if (gpsdev.connect()) {
            Log.v(logTAG, String.format("Connected to GPS device: %s", GPS_bluetooth_id))

            // Query recording method when full (OVERWRITE/STOP).
            Log.v(logTAG, "Sending command: PMTK182,2,6 and waiting for reply: PMTK182,3,6,")
            try {
                gpsdev.sendCommand("PMTK182,2,6")
            } catch (e2: IOException) {
                // Error sending... oops.. close connection and fail
                errorWhileDownloading()
                return
            }
            // Wait for reply from the device
            try {
                reply = gpsdev.waitForReply("PMTK182,3,6,", 10.0, logWriter)
            } catch (e2: IOException) {
                // Error reading... oops.. close connection and fail
                errorWhileDownloading()
                return
            } catch (e2: InterruptedException) {
                // TODO Auto-generated catch block
                e2.printStackTrace()
            }
            Log.v(logTAG, String.format("Got reply: %s", reply))

            // log_full_method == 1 means overwrite from the beginning
            // log_full_method == 2 means stop recording
            var log_full_method = 0
            p = Pattern.compile(".*PMTK182,3,6,([0-9]+).*")
            m = p.matcher(reply)
            if (m.find()) {
                log_full_method = m?.group(1).toString().toInt()
            }

            // Determine how much bytes we need to read from the memory
            var bytes_to_read = SIZEOF_GPS_MEMORY
            if (log_full_method == 1) {
                // Device is in OVERWRITE mode we don't know where data ends; read the entire memory.
                if (OVERWRITE_MODE == 0) {
                    sendMessageToMessageField("NOTE! Your device is in 'OVERWRITE when FULL mode', this is not a very efficient mode for download over bluetooth. Aborting! If you really want to download in this mode, please enable it via the preferences")
                    sendCloseProgress()
                    closeGPS_bin()
                    return
                }
                if (bytes_to_read > 0) {
                    Log.v(
                        logTAG,
                        String.format("Device is in OVERWRITE mode, memory size set by user preferences")
                    )
                } else {
                    Log.v(
                        logTAG,
                        String.format("Device is in OVERWRITE mode, trying to determine memory size")
                    )
                    var flashManuProdID = 0
                    // Query memory information
                    Log.v(logTAG, "Sending command: PMTK605 and waiting for reply: PMTK705,")
                    try {
                        gpsdev.sendCommand("PMTK605")
                    } catch (e: IOException) {
                        // Error sending... oops.. close connection and fail
                        errorWhileDownloading()
                        return
                    }
                    // Wait for reply from the device
                    try {
                        reply = gpsdev.waitForReply("PMTK705,", 10.0, logWriter)
                    } catch (e: IOException) {
                        // Error reading... oops.. close connection and fail
                        errorWhileDownloading()
                        return
                    } catch (e: InterruptedException) {
                        // TODO Auto-generated catch block
                        e.printStackTrace()
                    }
                    Log.v(logTAG, String.format("Got reply: %s", reply))
                    p = Pattern.compile(".*PMTK705,[\\.0-9A-Za-z_-]+,([0-9A-Za-z]+).*")
                    m = p.matcher(reply)
                    if (m.find()) {
                        flashManuProdID = m?.group(1).toString().toInt(16)
                        Log.v(
                            logTAG,
                            String.format(
                                "flashManuProdID: %d (0x%08X)",
                                flashManuProdID,
                                flashManuProdID
                            )
                        )
                    }
                    bytes_to_read = getFlashSize(flashManuProdID)
                }
            } else {
                Log.v(logTAG, String.format("Device is in STOP mode finding next write address"))
                var next_write_address = 0
                // Query the RCD_ADDR (data log Next Write Address).
                Log.v(logTAG, "Sending command: PMTK182,2,8 and waiting for reply: PMTK182,3,8,")
                try {
                    gpsdev.sendCommand("PMTK182,2,8")
                } catch (e: IOException) {
                    // Error sending... oops.. close connection and fail
                    errorWhileDownloading()
                    return
                }
                // Wait for reply from the device
                try {
                    reply = gpsdev.waitForReply("PMTK182,3,8,", 10.0, logWriter)
                } catch (e: IOException) {
                    // Error reading... oops.. close connection and fail
                    errorWhileDownloading()
                    return
                } catch (e: InterruptedException) {
                    // TODO Auto-generated catch block
                    e.printStackTrace()
                }
                Log.v(logTAG, String.format("Got reply: %s", reply))
                p = Pattern.compile(".*PMTK182,3,8,([0-9A-Za-z]+).*")
                m = p.matcher(reply)
                if (m.find()) {
                    next_write_address = m?.group(1).toString().toInt(16)
                    Log.v(
                        logTAG,
                        String.format(
                            "Next write address: %d (0x%08X)",
                            next_write_address,
                            next_write_address
                        )
                    )
                }
                var sectors = Math.floor((next_write_address / SIZEOF_SECTOR).toDouble()).toInt()
                if (next_write_address % SIZEOF_SECTOR != 0) {
                    sectors += 1
                }
                bytes_to_read = sectors * SIZEOF_SECTOR
            }
            Log.v(
                logTAG,
                String.format(
                    "Need to read %d (0x%08X) bytes of log data from device...",
                    bytes_to_read,
                    bytes_to_read
                )
            )

            // Open an output stream for writing
            //DYJ: if the file already exists errorWhileDownloading_cont was called and the download should be continued
            var offset = mtkDownload.getFLen("gpslog${mtkDownload.fileTimeStamp}.bin").toInt()
            val (bb, path) =
                mtkDownload.makeBinBOS(
                    "gpslog${mtkDownload.fileTimeStamp}.bin",
                    "application/x-binary",
                    offset > 0
                )
            if (bb != null) {
                bin_output_stream = bb
                binPath = path
            } else {
                Log.d(logTAG, "++++ can't write to file for getLog()")
                errorWhileDownloading()
                return
            }
            // To be safe we iterate requesting SIZEOF_CHUNK bytes at time.
            while (running && offset < bytes_to_read) {
                // Request log data (PMTK_LOG_REQ_DATA) from offset to bytes_to_read.
                val command = String.format("PMTK182,7,%08X,%08X", offset, SIZEOF_CHUNK)
                Log.v(logTAG, String.format("Sending command: %s", command))
                try {
                    gpsdev.sendCommand(command)
                } catch (e1: IOException) {
                    // Error sending... oops.. close connection and fail
                    errorWhileDownloading()
                    return
                }
                // Read from the device
                // The chunk might be split over more than one message
                // read until all bytes are received
                var number_of_empty = 0
                val tmp_array = ByteArray(SIZEOF_CHUNK)
                var bytes_received = 0
                var number_of_message = 1
                if (SIZEOF_CHUNK > 0x800) {
                    number_of_message = SIZEOF_CHUNK / 0x800
                }
                Log.v(
                    logTAG,
                    String.format(
                        "SIZEOF_CHUNK=%d, waiting for %d PMTK182,8 messages",
                        SIZEOF_CHUNK,
                        number_of_message
                    )
                )
                var j = 0
                while (j < number_of_message) {
                    Log.v(logTAG, String.format("waiting for part:%d", j)) //tmp
                    try {
                        reply = gpsdev.waitForReply("PMTK182,8", 10.0, logWriter)
                    } catch (e1: IOException) {        //DYJ Start
                        // Error reading... oops.. close connection and reconnect
                        closeGPS_only()
                        reply = ""
                        sendMessageToMessageField("try to reconnect")
                        Log.v(logTAG, String.format("try to reconnect"))
                        var w = 500
                        var reconnects = 0
                        var ok = false
                        while (w < 5000 && !ok) {
                            Log.v(
                                logTAG,
                                String.format(
                                    "Trying to reconnect to GPS device: %s",
                                    GPS_bluetooth_id
                                )
                            )
                            try {
                                Thread.sleep(w.toLong())
                            } catch (e: InterruptedException) {
                                // TODO Auto-generated catch block
                                e.printStackTrace()
                            }
                            ok = gpsdev.connect()
                            Log.v(logTAG, String.format("connect (%d)", ++reconnects))
                            w += 500
                        }
                        if (!ok) {
                            errorWhileDownloading_cont()
                            return
                        }
                        Log.v(
                            logTAG,
                            String.format("Connected to GPS device: %s", GPS_bluetooth_id)
                        )
                        j = number_of_message //no need to wait for the others after reconnection.
                        //DYJ end
                    } catch (e1: InterruptedException) {
                        // TODO Auto-generated catch block
                        e1.printStackTrace()
                    }
                    if (reply.isEmpty()) {
                        // Asked for message was not found.
                        Log.v(logTAG, String.format("Asked for message was not found"))
                        j++
                        continue
                    }
                    Log.v(logTAG, String.format("Got reply: %s", reply))
                    val bytes = reply.toByteArray()
                    if (bytes[reply.length - 5] == '*'.code.toByte()) {
                        var csum = 0
                        for (i in 1 until reply.length - 5) {
                            csum = csum xor bytes[i].toInt()
                        }
                        val string_byte = reply.substring(reply.length - 4, reply.length - 2)
                        var csumT = 257
                        try {
                            csumT = string_byte.toInt(16) and 0xFF
                        } catch (e: NumberFormatException) {
                            //e.printStackTrace();
                        }
                        if (csum == csumT) {
                            Log.v(logTAG, String.format("Checksum ok:%d", csum))
                        } else {
                            Log.v(logTAG, String.format("Checksum Error:%d", csum))
                            reply = ""
                        }
                    }
                    var i = 20
                    while (i < reply.length - 3) {
                        val string_byte = reply.substring(i, i + 2)
                        if (string_byte == "FF") {
                            number_of_empty++
                        }
                        try {
                            tmp_array[bytes_received] = (string_byte.toInt(16) and 0xFF).toByte()
                            bytes_received++
                        } catch (e: NumberFormatException) {
                            //e.printStackTrace();
                        }
                        i += 2
                    }
                    j++
                }
                if (bytes_received != SIZEOF_CHUNK) {
                    Log.v(
                        logTAG,
                        String.format("ERROR! bytes_received(%d) != SIZEOF_CHUNK", bytes_received)
                    )
                    continue
                } else {
                    offset += SIZEOF_CHUNK
                    try {
                        bin_output_stream!!.write(tmp_array, 0, SIZEOF_CHUNK)
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }
                }
                // In OVERWRITE mode when user asked us, when we find and empty sector assume rest of memory is empty
                if (OVERWRITE_MODE == 1 && number_of_empty == bytes_received) {
                    offset = bytes_to_read
                    Log.v(
                        logTAG,
                        String.format("Found empty SIZEOF_CHUNK, stopping reading any further")
                    )
                }
                val percetageComplete = (offset + SIZEOF_CHUNK) / bytes_to_read.toDouble() * 100.0
                Log.v(logTAG, String.format("Saved log data: %6.2f%%", percetageComplete))
                sendPercentageComplete(percetageComplete.toInt())
            }
            closeGPS_bin()
            if (!running) {
                sendCloseProgress()
                sendMessageToMessageField("Download aborted")
                cleanup()
                return
            }

            // Send a status message to the main thread
            sendCloseProgress()
            sendMessageToMessageField("Download complete saved to:$binPath")

            // Close the log file
            if (logWriter != null) {
                try {
                    logWriter?.close()
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
        } else {
            Log.v(logTAG, String.format("Could NOT connected to GPS device: %s", GPS_bluetooth_id))
            // Send a status message to the main thread
            sendCloseProgress()
            sendMessageToMessageField("Error, could not connect to GPS device")
            return
        }

        // Check if we should also create a GPX file
        if (createGPX) {
            sendCreateGPX(mtkDownload.fileTimeStamp)
        }
    }

    private fun sendPercentageComplete(percentageComplete: Int) {
        val msg = dHandler.obtainMessage()
        val b = Bundle()
        b.putInt(mtkDownload.KEY_PROGRESS, percentageComplete)
        msg.data = b
        dHandler.sendMessage(msg)
    }

    private fun sendMessageToMessageField(message: String) {
        val msg = dHandler.obtainMessage()
        val b = Bundle()
        b.putString(mtkDownload.MESSAGEFIELD, message)
        msg.data = b
        dHandler.sendMessage(msg)
    }

    private fun sendCreateGPX(message: String) {
        val msg = dHandler.obtainMessage()
        val b = Bundle()
        b.putString(mtkDownload.CREATEGPX, message)
        msg.data = b
        dHandler.sendMessage(msg)
    }

    private fun sendCloseProgress() {
        val msg = dHandler.obtainMessage()
        val b = Bundle()
        b.putInt(mtkDownload.CLOSE_PROGRESS, 1)
        msg.data = b
        dHandler.sendMessage(msg)
    }

    private fun sendRestartGPS() {    //DYJ
        val msg = dHandler.obtainMessage()
        val b = Bundle()
        b.putString(mtkDownload.RESTART_GPS, mtkDownload.fileTimeStamp)
        msg.data = b
        dHandler.sendMessage(msg)
    }
}
