package com.mtkdownload

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothSocket
import java.io.BufferedWriter
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class GPSrxtx(adapter: BluetoothAdapter?, gpsdev: String) {
    private var `in`: InputStream? = null
    private var out: OutputStream? = null
    private var mBluetoothAdapter: BluetoothAdapter? = null
    private val dev_id: String
    private var sock: BluetoothSocket? = null
    private val buffer = StringBuilder()
    var log_writer: BufferedWriter? = null //DYJ

    init {
        mBluetoothAdapter = adapter
        dev_id = gpsdev
    }

    fun connect(): Boolean {
        android.util.Log.d(TAG, "++++ connect()")
        val zee = mBluetoothAdapter!!.getRemoteDevice(dev_id)
        var m: Method = try {
            zee.javaClass.getMethod(
                "createRfcommSocket", *arrayOf<Class<*>?>(
                    Int::class.javaPrimitiveType
                )
            )
        } catch (e1: SecurityException) {
            e1.printStackTrace()
            return false
        } catch (e1: NoSuchMethodException) {
            e1.printStackTrace()
            return false
        }
        sock = try {
            m.invoke(zee, Integer.valueOf(1)) as BluetoothSocket
        } catch (e1: IllegalArgumentException) {
            e1.printStackTrace()
            return false
        } catch (e1: IllegalAccessException) {
            e1.printStackTrace()
            return false
        } catch (e1: InvocationTargetException) {
            e1.printStackTrace()
            return false
        }
        try {
            sock!!.connect()
        } catch (e1: IOException) {
            e1.printStackTrace()
            return false
        }
        android.util.Log.d(TAG, "++++ Connected")
        `in` = try {
            sock!!.inputStream
        } catch (e1: IOException) {
            e1.printStackTrace()
            return false
        }
        out = try {
            sock!!.outputStream
        } catch (e1: IOException) {
            e1.printStackTrace()
            return false
        }
        return true
    }

    @Throws(IOException::class)
    fun sendCommand(command: String) {
        var i = command.length
        var checksum: Byte = 0
        while (--i >= 0) {
            checksum = (checksum.toInt() xor command[i].code.toByte().toInt()).toByte()
        }
        val rec = StringBuilder(256)
        rec.setLength(0)
        rec.append('$')
        rec.append(command)
        rec.append('*')
        rec.append(Integer.toHexString(checksum.toInt()))
        rec.append("\r\n")
        android.util.Log.d(TAG, "++++ Writing: $rec")
        Log(String.format("Writing: %s", rec))

        // Actually send it
        out!!.write(rec.toString().toByteArray())
    }

    @Throws(IOException::class, InterruptedException::class)
    fun readBytes(timeout: Double): ByteArray {
        var time = 0.0
        var bytes_available: Int
        while (`in`!!.available().also { bytes_available = it } == 0 && time < timeout) {
            // throws interrupted exception
            Thread.sleep(250)
            time += 0.25
        }
        val buf = ByteArray(bytes_available)
        `in`!!.read(buf)
        android.util.Log.d(TAG, "++++ Read $bytes_available bytes from GPS")
        return buf
    }

    fun Log(text: String?) {
        if (log_writer != null) {
            // Create a unique file for writing the log files to
            val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            val time = sdf.format(Date())
            try {
                log_writer?.append(time)
                log_writer?.append(text)
                log_writer?.append('\n')
                log_writer?.flush()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    @Throws(IOException::class, InterruptedException::class)
    fun waitForReply(reply: String, timeout: Double, log_writer_1: BufferedWriter?): String {
        var buf: ByteArray
        // Read from the device until we get the reply we are looking for
        log_writer = log_writer_1
        android.util.Log.d(TAG, "++++ Reading from device, waiting for: $reply, timeout: $timeout")
        var i = 0
        while (i < 10) { //DYJ was 100
            Log(String.format("waitForReply i %d", i))
            buf = readBytes(timeout)
            Log(String.format("waitForReply read: %d", buf.size))
            if (buf.size == 0) {
                android.util.Log.d(TAG, "++++ No bytes read from device!")
                throw IOException()
            }
            for (j in buf.indices) {
                val b = (buf[j].toInt() and 0xff).toChar()
                // Check if this is the start of a new message
                if (buffer.length > 0 && b == '$') {
                    // Yep new message started, parse old message (if any)
                    i++
                    val message = buffer.toString()
                    android.util.Log.d(TAG, "++++ Received a message($i): $message")
                    Log(String.format("Received a message: %s", message))
                    if (message[0] == '$') {
                        if (message.indexOf(reply, 0) > 0) {
                            android.util.Log.d(TAG, "++++ Breaking because we received:$reply")
                            buffer.setLength(0)
                            for (k in j until buf.size) {
                                val c = (buf[k].toInt() and 0xff).toChar()
                                buffer.append(c)
                            }
                            return message
                        }
                    }
                    buffer.setLength(0)
                }
                buffer.append(b)
            }
        }
        // We did not receive the message we where waiting for after 100 messages! Return empty string.
        android.util.Log.d(TAG, "++++ We did not receive $reply for after 100 messages!")
        return ""
    }

    fun close() {
        android.util.Log.d(TAG, "++++ close()")
        try {
            sock!!.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    companion object {
        private const val TAG = "MTKDownload-GPSrxtx"
    }
}