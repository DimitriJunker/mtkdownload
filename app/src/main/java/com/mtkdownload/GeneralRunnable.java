package com.mtkdownload;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;

/** Nested class that performs the restart */
public class GeneralRunnable implements Runnable 
{
	Handler mHandler;
	protected static String GPS_bluetooth_id;

	GeneralRunnable(Handler h) 
	{
		mHandler = h;
		GPS_bluetooth_id = MTKDownload.getSharedPreferences().getString("bluetoothListPref", "-1");
	}

	protected void sendTOAST(String message) {
		Message msg = mHandler.obtainMessage();
		Bundle b = new Bundle();
		b.putString(MTKDownload.KEY_TOAST, message);
		msg.setData(b);
		mHandler.sendMessage(msg);
	}

	protected void sendMessageField(String message) {
		Message msg = mHandler.obtainMessage();
		Bundle b = new Bundle();
		b.putString(MTKDownload.MESSAGEFIELD, message);
		msg.setData(b);
		mHandler.sendMessage(msg);
	}
	
	protected void sendSettings_MessageField(String message) {
		Message msg = mHandler.obtainMessage();
		Bundle b = new Bundle();
		b.putString(MTKDownload.SETTINGS_MESSAGEFIELD, message);
		msg.setData(b);
		mHandler.sendMessage(msg);
	}

	protected void sendCloseProgress() {
		Message msg = mHandler.obtainMessage();
		Bundle b = new Bundle();
		b.putInt(MTKDownload.CLOSE_PROGRESS, 1);
		msg.setData(b);
		mHandler.sendMessage(msg);
	}

	@Override
	public void run() {
		
	}

}
