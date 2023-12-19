package com.mtkdownload

import android.os.Bundle
import android.os.Environment
import android.os.Handler
import java.io.BufferedInputStream
import java.io.BufferedWriter
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

//DYJ
class ParseBinFile(
    private val mtkDownload: MTKDownload,
    private val convertHandler: Handler
) : Runnable {
    private var logPath=""
    private var gpxPath=""
    var running = true
    private val logTAG = "ParseBin"
    private var logIsHoluxM241 = false
    private var gpxTrkNumber = 0
    private val buffer = ByteArray(SIZEOF_SECTOR)
    private val emptyseparator = ByteArray(0x10)
    private var logWriter: BufferedWriter? = null
    private var gpxInTrk = false
    private val writeOneTrk: Boolean =
        mtkDownload.sharedPreferences.getBoolean("createOneTrkPref", true)

    //    private val log_file: File? = null
    private var oldToastmessage = ""
    private var oldPercentage = 0
    private val createLogFile: Boolean =
        mtkDownload.sharedPreferences.getBoolean("createDebugPref", false)
    private val formatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'")

    init {
        for (i in 0..0xf) {
            emptyseparator[i] = 0xFF.toByte()
        }
        formatter.timeZone = TimeZone.getTimeZone("UTC")
    }

    @Throws(IOException::class)
    fun doConvert() {
        // Open Log file

        if (createLogFile) {
            val(lw,path) = mtkDownload.getBufWr( "gpslog" + mtkDownload.fileTimeStamp + "_gpx.txt", "text/plain",
                true,
                SIZEOF_SECTOR)
            logWriter=lw
            logPath=path
        } else {
            logPath=""
            logWriter = null
        }
        // Open an input stream for reading from the binary Log
        val pathName =mtkDownload.getDlDir()
        val binFile = File(pathName, "gpslog${mtkDownload.fileTimeStamp}.bin")
        val reader: BufferedInputStream = try {
            val freader = FileInputStream(binFile)
            Log(String.format("Reading bin file: %s", binFile.toString()))
            BufferedInputStream(freader, SIZEOF_SECTOR)
        } catch (e1: FileNotFoundException) {
            e1.printStackTrace()
            return
        }

        // Open an output for writing the gpx file
        Log("Creating GPX file: ${mtkDownload.fileTimeStamp}.gpx")
        sendMessageToMessageField("Creating GPX file: ${mtkDownload.fileTimeStamp}.gpx")
        val(gpxWriter,path) = mtkDownload.getBufWr(
            "gpslog${mtkDownload.fileTimeStamp}.gpx", "application/gpx+xml", false, SIZEOF_SECTOR
        )
        if (gpxWriter == null) {
            android.util.Log.d(logTAG, "++++ can't write to file for getLog()")
            reader.close()
            return
        }
        gpxPath=path
        writeHeader(gpxWriter)
        var bytesInSector = 0
        var sectorCount = 0
        var logCountFullyWrittenSector = -1
        var totalNrOfSectors =
            java.lang.Double.valueOf((binFile.length() / SIZEOF_SECTOR).toDouble()).toInt()
        if (binFile.length() % SIZEOF_SECTOR != 0L) {
            totalNrOfSectors++
        }
        Log("totalNrOfSectors: $totalNrOfSectors")
        var formattedDate = ""
        var recordCountTotal = 0
        while (running && reader.read(buffer, 0, SIZEOF_SECTOR).also { bytesInSector = it } > 0) {
            sectorCount++
            val buf = ByteBuffer.wrap(buffer)
            buf.order(ByteOrder.LITTLE_ENDIAN)
            var nrOfRecordsInSector = buf.getShort(0)
            // -1 is used if a sector is not fully written
            if (nrOfRecordsInSector.toInt() == -1) {
                nrOfRecordsInSector = 5000
            } else {
                logCountFullyWrittenSector = nrOfRecordsInSector.toInt()
            }
            var logFormat = buf.getInt(2)

            // Skip the header (which is 0x200 bytes long)
            buf.position(0x200)
            Log(String.format("Read %d bytes from bin file", bytesInSector))
            Log(String.format("Reading sector"))
            Log(String.format("Log format %x", logFormat))
            Log(String.format("Nr of sector records: %d", nrOfRecordsInSector))
            var recordCountSector = 0
            while (recordCountSector < nrOfRecordsInSector) {
                val tmp = ByteArray(0x10)
                // Test for record separators
                val seperatorLength = 0x10
                Log(String.format("Reading offset: %x", buf.position()))
                buf[tmp]
                if (tmp[0] == 0xAA.toByte() && tmp[1] == 0xAA.toByte() && tmp[2] == 0xAA.toByte() && tmp[3] == 0xAA.toByte() && tmp[4] == 0xAA.toByte() && tmp[5] == 0xAA.toByte() && tmp[6] == 0xAA.toByte() && tmp[15] == 0xBB.toByte() && tmp[14] == 0xBB.toByte() && tmp[13] == 0xBB.toByte() && tmp[12] == 0xBB.toByte()) {
                    // So we found a record separator..
                    Log("Found a record separator")

                    // if open, close the current trk section
                    try {
                        if (!writeOneTrk && gpxInTrk) {
                            writeTrackEnd(gpxWriter)
                            gpxInTrk = false
                        }
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }
                    // It is possible that the logFormat have changed, parse
                    // out the new Log conditions
                    buf.position(buf.position() - 9)
                    val seperatorType = buf.get()
                    if (seperatorType.toInt() == 0x02) {
                        logFormat = buf.int
                        buf.position(buf.position() + 4)
                        Log(String.format("Log format has changed to %x", logFormat))
                    } else {
                        buf.position(buf.position() + 8)
                    }
                    continue
                } else if (tmp[0] == 0x48.toByte() && tmp[1] == 0x4F.toByte() && tmp[2] == 0x4C.toByte() && tmp[3] == 0x55.toByte() && tmp[4] == 0x58.toByte() && tmp[5] == 0x47.toByte() && tmp[6] == 0x52.toByte() && tmp[15] == 0x52.toByte() && tmp[14] == 0x45.toByte() && tmp[13] == 0x47.toByte() && tmp[12] == 0x47.toByte()) {
                    logIsHoluxM241 = true
                    Log("Found a HOLUX M241 separator!")
                    val tmp4 = ByteArray(4)
                    buf[tmp4]
                    if (tmp4[0] == 0x20.toByte() && tmp4[1] == 0x20.toByte() && tmp4[2] == 0x20.toByte() && tmp4[3] == 0x20.toByte()) {
                        Log("Found a HOLUX M241 1.3 firmware!")
                    } else {
                        buf.position(buf.position() - 4)
                    }
                    continue
                } else if (tmp.contentEquals(emptyseparator)) {
                    Log("Empty space, assume end of sector")
                    break
                } else {
                    buf.position(buf.position() - seperatorLength)
                }

                // So this is not a separator but it is an actual record, read it!
                recordCountSector++
                var bytesRead = 0
                var utcTime: Long = 0
                var valid: Short = 0
                var lat = 0.0
                var lon = 0.0
                var height = 0f
                var speed = 0f
                Log(
                    String.format(
                        "Read record: %d of %d position %x",
                        recordCountSector,
                        nrOfRecordsInSector,
                        buf.position()
                    )
                )
                if (logFormat and logFormat_UTC == logFormat_UTC) {
                    bytesRead += 4
                    utcTime = buf.int.toLong()
                    Log(String.format("UTC time %d", utcTime))
                }
                if (logFormat and logFormat_VALID == logFormat_VALID) {
                    bytesRead += 2
                    valid = buf.short
                    Log(String.format("Valid %d", valid))
                }
                if (logFormat and logFormat_LATITUDE == logFormat_LATITUDE) {
                    if (logIsHoluxM241) {
                        bytesRead += 4
                        lat = buf.float.toDouble()
                    } else {
                        bytesRead += 8
                        lat = buf.double
                    }
                    Log(String.format("Latitude %f", lat))
                }
                if (logFormat and logFormat_LONGITUDE == logFormat_LONGITUDE) {
                    if (logIsHoluxM241) {
                        bytesRead += 4
                        lon = buf.float.toDouble()
                    } else {
                        bytesRead += 8
                        lon = buf.double
                    }
                    Log(String.format("Longitude %f", lon))
                }
                if (logFormat and logFormat_HEIGHT == logFormat_HEIGHT) {
                    if (logIsHoluxM241) {
                        bytesRead += 3
                        val tmp4 = ByteArray(4)
                        buf[tmp4, 1, 3]
                        val b = ByteBuffer.wrap(tmp4)
                        b.order(ByteOrder.LITTLE_ENDIAN)
                        height = b.float
                    } else {
                        bytesRead += 4
                        height = buf.float
                    }
                    Log(String.format("Height %f m", height))
                }
                if (logFormat and logFormat_SPEED == logFormat_SPEED) {
                    bytesRead += 4
                    speed = buf.float / 3.6f
                    Log(String.format("Speed %f m/s", speed))
                }
                if (logFormat and logFormat_HEADING == logFormat_HEADING) {
                    bytesRead += 4
                    val heading = buf.float
                    Log(String.format("Heading %f", heading))
                }
                if (logFormat and logFormat_DSTA == logFormat_DSTA) {
                    bytesRead += 2
                    val dsta = buf.short
                    Log(String.format("DSTA %d", dsta))
                }
                if (logFormat and logFormat_DAGE == logFormat_DAGE) {
                    bytesRead += 4
                    val dage = buf.int
                    Log(String.format("DAGE %d", dage))
                }
                if (logFormat and logFormat_PDOP == logFormat_PDOP) {
                    bytesRead += 2
                    val pdop = buf.short
                    Log(String.format("PDOP %d", pdop))
                }
                if (logFormat and logFormat_HDOP == logFormat_HDOP) {
                    bytesRead += 2
                    val hdop = buf.short
                    Log(String.format("HDOP %d", hdop))
                }
                if (logFormat and logFormat_VDOP == logFormat_VDOP) {
                    bytesRead += 2
                    val vdop = buf.short
                    Log(String.format("VDOP %d", vdop))
                }
                if (logFormat and logFormat_NSAT == logFormat_NSAT) {
                    bytesRead += 2
                    val nsat = buf.get()
                    val nsatInUse = buf.get()
                    Log(String.format("NSAT %d %d", nsat.toInt(), nsatInUse.toInt()))
                }
                if (logFormat and logFormat_SID == logFormat_SID) {
                    // Large section to parse
                    var satdataCount = 0
                    while (true) {
                        bytesRead += 1
                        val satdataSid = buf.get()
                        bytesRead += 1
                        val satdataInuse = buf.get()
                        bytesRead += 2
                        val satdataInview = buf.short
                        Log(String.format("SID %d", satdataSid.toInt()))
                        Log(String.format("SID in use %d", satdataInuse.toInt()))
                        Log(String.format("SID in view %d", satdataInview.toInt()))
                        if (satdataInview > 0) {
                            if (logFormat and logFormat_ELEVATION == logFormat_ELEVATION) {
                                bytesRead += 2
                                val satElevation = buf.short
                                Log(String.format("Satellite ELEVATION %d", satElevation.toInt()))
                            }
                            if (logFormat and logFormat_AZIMUTH == logFormat_AZIMUTH) {
                                bytesRead += 2
                                val satAzimuth = buf.short
                                Log(String.format("Satellite AZIMUTH %d", satAzimuth.toInt()))
                            }
                            if (logFormat and logFormat_SNR == logFormat_SNR) {
                                bytesRead += 2
                                val satSnr = buf.short
                                Log(String.format("Satellite SNR %d", satSnr.toInt()))
                            }
                            satdataCount++
                        }
                        if (satdataCount >= satdataInview) {
                            break
                        }
                    }
                }
                if (logFormat and logFormat_RCR == logFormat_RCR) {
                    bytesRead += 2
                    val rcr = buf.short
                    Log(String.format("RCR %d", rcr))
                }
                if (logFormat and logFormat_MILLISECOND == logFormat_MILLISECOND) {
                    bytesRead += 2
                    val millisecond = buf.short
                    Log(String.format("Millisecond %d", millisecond))
                }
                if (logFormat and logFormat_DISTANCE == logFormat_DISTANCE) {
                    bytesRead += 8
                    val distance = buf.double
                    Log(String.format("Distance %f", distance))
                }
                buf.position(buf.position() - bytesRead)
                val tmp2 = ByteArray(bytesRead)
                buf[tmp2, 0, bytesRead]
                val checksum = packetChecksum(tmp2, bytesRead)
                if (!logIsHoluxM241) {
                    // Read the "*"
                    buf.get()
                }
                // And the final character is the checksum count
                val readChecksum = buf.get()
                Log(
                    String.format(
                        "bytesRead %d Checksum %x read checksum %x",
                        bytesRead,
                        checksum,
                        readChecksum
                    )
                )
                try {
                    if (valid.toInt() != VALID_NOFIX && checksum == readChecksum) {
                        if (!gpxInTrk) {
                            writeTrackBegin(gpxWriter, utcTime)
                            gpxInTrk = true
                        }
                        formattedDate = writeTrackPoint(
                            gpxWriter,
                            lat,
                            lon,
                            height.toDouble(),
                            utcTime,
                            speed.toDouble()
                        )
                    }
                } catch (e: IOException) {
                    e.printStackTrace()
                }
                val percentageCompleteSector =
                    java.lang.Double.valueOf(recordCountSector.toDouble()) / java.lang.Double.valueOf(
                        nrOfRecordsInSector.toDouble()
                    ) * 100.0
                if (sectorCount > totalNrOfSectors) { //apparently this might happen..
                    totalNrOfSectors = sectorCount
                }
                var percentageCompleteTotal = 0.0
                if (totalNrOfSectors > 1) {
                    val totalNrOfRecords =
                        java.lang.Double.valueOf(totalNrOfSectors.toDouble()) * logCountFullyWrittenSector
                    percentageCompleteTotal =
                        100.0 * java.lang.Double.valueOf((recordCountTotal + recordCountSector).toDouble()) / totalNrOfRecords
                } else if (totalNrOfSectors == 1) {
                    percentageCompleteTotal = 200*percentageCompleteSector/(100+percentageCompleteSector)   //hopefully better estimate
                    /*
                    val someTuning = 0.5
                    percentageCompleteTotal = percentageCompleteSector / someTuning
                    */
                }
                if (percentageCompleteTotal > 98.0) {
                    percentageCompleteTotal = 99.0
                }
                android.util.Log.v(
                    mtkDownload.logTAG, "+++ ON Parse "
                            + recordCountSector + " of " + nrOfRecordsInSector + ". PercSect "
                            + percentageCompleteSector.toInt() + ". Sector " + sectorCount + " of " + totalNrOfSectors + ". PercTot "
                            + percentageCompleteTotal.toInt() + " Track:" + gpxTrkNumber
                )
                if (formattedDate.length > 10 && oldPercentage != percentageCompleteTotal.toInt()) {
                    sendPercentageConverted(percentageCompleteTotal.toInt())
                    sendTOAST(
                        String.format(
                            "Sector %d of %d | Track %d | %s",
                            sectorCount,
                            totalNrOfSectors,
                            gpxTrkNumber,
                            formattedDate.substring(0, 10)
                        )
                    )
                    oldPercentage = percentageCompleteSector.toInt()
                }
            } // while (recordCountSector < logCount)
            recordCountTotal += nrOfRecordsInSector
            if (bytesInSector < SIZEOF_SECTOR) {
                // Reached the end of the file or something is wrong
                Log(String.format("End of file!"))
                break
            }
            try { // do a flush after each sector is read
                gpxWriter.flush()
                logWriter?.flush()
            } catch (e: IOException) {
                sendTOAST("exception while flushing")
            }
        } // while reader.read

        // Close GPX file
        try {
            if (gpxInTrk) {
                writeTrackEnd(gpxWriter)
                gpxInTrk = false
            }
            writeFooter(gpxWriter)
            gpxWriter.flush()
            gpxWriter.close()
            reader.close()
        } catch (e: IOException) {
            e.printStackTrace()
            return
        }

        // Close the Log file
        if (logWriter != null) {
            try {
                logWriter?.flush()
                logWriter?.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
        sendPercentageConverted(100)
        if (running) {
            sendMessageToMessageField("Finished converting to GPX")
            android.util.Log.v(TAG, "+++ GPS converting finished")
        }
        else{
            sendMessageToMessageField("converting to GPX aborted")
            android.util.Log.v(TAG, "+++ GPS converting aborted")
            cleanup()
        }

        sendCloseProgress()
        return
    }
    private fun cleanup() {
        // Clean up, delete the bin file and perhaps the Log file
        if(gpxPath.isNotEmpty()) {
            val f1 = File(gpxPath)
            f1.delete()
        }
        if(logPath.isNotEmpty()) {
            val f2 = File(
                logPath
            )
            f2.delete()
        }
    }

    @Throws(IOException::class)
    private fun writeHeader(writer: BufferedWriter?) {
        writer!!.write(
            """<?xml version="1.0" encoding="UTF-8" standalone="no" ?>
<gpx
    version="1.1"
    creator="MTKDownload - http://www.dimitri-junker.de"
    xmlns="http://www.topografix.com/GPX/1/1"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="http://www.topografix.com/GPX/1/1 http://www.topografix.com/GPX/1/1/gpx.xsd">
"""
        )
    }

    @Throws(IOException::class)
    private fun writeFooter(writer: BufferedWriter?) {
        writer!!.write("</gpx>\n")
    }

    @Throws(IOException::class)
    private fun writeTrackBegin(writer: BufferedWriter?, time: Long) {
        val date = Date(time * 1000)
        writer!!.write(
            """<trk>
 <name>${formatter.format(add1024w(date))} </name>
 <number>$gpxTrkNumber</number>
<trkseg>
"""
        )
        gpxTrkNumber++
    }

    @Throws(IOException::class)
    private fun writeTrackPoint(
        writer: BufferedWriter?,
        lat: Double,
        lon: Double,
        height: Double,
        time: Long,
        speed: Double
    ): String {
        val date = Date(time * 1000)
        val formattedDate = formatter.format(add1024w(date)) //DYJ
        Log(String.format("formattedDate %s", formattedDate))
        writer!!.write(
            String.format(
                Locale.US,
                "<trkpt lat=\"%.9f\" lon=\"%.9f\">\n  <ele>%.6f</ele>\n  <time>%s</time>\n  <speed>%.6f</speed>\n</trkpt>\n",
                lat, lon, height, formattedDate, speed
            )
        )
        return formattedDate
    }

    private fun add1024w(oldDate: Date): Date //DYJ
    {
        val today = Calendar.getInstance()
        val newCal = Calendar.getInstance()
        newCal.time = oldDate
        newCal.add(Calendar.DATE, 7168) // add 7168 days =1024 weeks
        return if (newCal.after(today)) oldDate else newCal.time
    }

    @Throws(IOException::class)
    private fun writeTrackEnd(writer: BufferedWriter?) {
        writer!!.write("</trkseg>\n</trk>\n")
    }

    private fun Log(text: String) {
        if (logWriter != null) {
            try {
                logWriter?.append(text)
                logWriter?.append('\n')
                logWriter?.flush()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    private fun packetChecksum(array: ByteArray, length: Int): Byte {
        var check: Byte = 0
        var i = 0
        while (i < length) {
            check = (check.toInt() xor array[i].toInt()).toByte()
            i++
        }
        return check
    }

    private fun sendPercentageConverted(percentageComplete: Int) {
        val msg = convertHandler.obtainMessage()
        val b = Bundle()
        b.putInt(mtkDownload.KEY_PROGRESS, percentageComplete)
        msg.data = b
        convertHandler.sendMessage(msg)
    }

    private fun sendTOAST(message: String) {
        if (message == oldToastmessage) {
            return
        }
        oldToastmessage = message
        val msg = convertHandler.obtainMessage()
        val b = Bundle()
        b.putString(mtkDownload.KEY_TOAST, message)
        msg.data = b
        convertHandler.sendMessage(msg)
    }

    private fun sendMessageToMessageField(message: String) {
        val msg = convertHandler.obtainMessage()
        val b = Bundle()
        b.putString(mtkDownload.MESSAGEFIELD, message)
        msg.data = b
        convertHandler.sendMessage(msg)
    }

    private fun sendCloseProgress() {
        val msg = convertHandler.obtainMessage()
        val b = Bundle()
        b.putInt(mtkDownload.CLOSE_PROGRESS, 1)
        msg.data = b
        convertHandler.sendMessage(msg)
    }

    override fun run() {
        Log(String.format("Parse 1"))
        try {
            doConvert()
        } catch (e: IOException) {
            e.printStackTrace()
            sendMessageToMessageField("Couldn't convert GPS Log to gpx")
        }
    }

    companion object {
        const val TAG = "ParseBinFile"
        private const val SIZEOF_SECTOR = 0x10000

        // Log format is stored as a bitmask field.
        private const val logFormat_UTC = 0x00000001
        private const val logFormat_VALID = 0x00000002
        private const val logFormat_LATITUDE = 0x00000004
        private const val logFormat_LONGITUDE = 0x00000008
        private const val logFormat_HEIGHT = 0x00000010
        private const val logFormat_SPEED = 0x00000020
        private const val logFormat_HEADING = 0x00000040
        private const val logFormat_DSTA = 0x00000080
        private const val logFormat_DAGE = 0x00000100
        private const val logFormat_PDOP = 0x00000200
        private const val logFormat_HDOP = 0x00000400
        private const val logFormat_VDOP = 0x00000800
        private const val logFormat_NSAT = 0x00001000
        private const val logFormat_SID = 0x00002000
        private const val logFormat_ELEVATION = 0x00004000
        private const val logFormat_AZIMUTH = 0x00008000
        private const val logFormat_SNR = 0x00010000
        private const val logFormat_RCR = 0x00020000
        private const val logFormat_MILLISECOND = 0x00040000
        private const val logFormat_DISTANCE = 0x00080000
        private const val VALID_NOFIX = 0x0001
    }
}