package com.mtkdownload

import android.os.Bundle
import android.os.Handler

/** Nested class that performs the restart  */
open class GeneralRunnable internal constructor(val mtkDownload: MTKDownload,var mHandler: Handler) : Runnable {
    init {
        GPS_bluetooth_id = mtkDownload.sharedPreferences.getString("bluetoothListPref", "-1").toString()
    }

    protected fun sendTOAST(message: String?) {
        val msg = mHandler.obtainMessage()
        val b = Bundle()
        b.putString(mtkDownload.KEY_TOAST, message)
        msg.data = b
        mHandler.sendMessage(msg)
    }

    protected fun sendMessageField(message: String?) {
        val msg = mHandler.obtainMessage()
        val b = Bundle()
        b.putString(mtkDownload.MESSAGEFIELD, message)
        msg.data = b
        mHandler.sendMessage(msg)
    }

    protected fun sendSettings_MessageField(message: String?) {
        val msg = mHandler.obtainMessage()
        val b = Bundle()
        b.putString(mtkDownload.SETTINGS_MESSAGEFIELD, message)
        msg.data = b
        mHandler.sendMessage(msg)
    }

    protected fun sendCloseProgress() {
        val msg = mHandler.obtainMessage()
        val b = Bundle()
        b.putInt(mtkDownload.CLOSE_PROGRESS, 1)
        msg.data = b
        mHandler.sendMessage(msg)
    }

    override fun run() {}

    companion object {
        protected var GPS_bluetooth_id: String=""
    }
}