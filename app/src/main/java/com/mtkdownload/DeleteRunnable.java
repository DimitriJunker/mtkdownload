package com.mtkdownload;

import java.io.IOException;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

/** Nested class that performs the restart */
public class DeleteRunnable implements Runnable 
{
	public static final String TAG = "DeleteRunnable";
	
	Handler mHandler;
	private static String GPS_bluetooth_id;

	DeleteRunnable(Handler h) {
		mHandler = h;
		GPS_bluetooth_id = MTKDownload.getSharedPreferences().getString("bluetoothListPref", "-1");
	}

	public void run() {	
		Log.v(TAG, "+++ DeleteRunnable.run() +++");
		sendMessageField("Deleting log from GPS");
		
		GPSrxtx gpsdev = new GPSrxtx(MTKDownload.getmBluetoothAdapter(), GPS_bluetooth_id);
		if (gpsdev.connect()) {
			// Send the command to clear the log
        	try {
				gpsdev.sendCommand("PMTK182,6,1");
			} catch (IOException e) {
				sendMessageField("Failed");
                gpsdev.close();
                return;
			}
    		// Wait for reply from the device
        	try {
				gpsdev.waitForReply("PMTK001,182,6,3", 60.0,null);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
        	gpsdev.close();

        	sendMessageField("Delete succeeded!");
    	}
    	else {
    		sendMessageField("Error, could not connect to GPS device");
    	}
		sendCloseProgress();
		Log.d(MTKDownload.TAG, "++++ Done: DeleteRunnable.run()");
	}

	private void sendMessageField(String message) {
		Message msg = mHandler.obtainMessage();
		Bundle b = new Bundle();
		b.putString(MTKDownload.MESSAGEFIELD, message);
		msg.setData(b);
		mHandler.sendMessage(msg);
	}
	
	private void sendCloseProgress() {
		Message msg = mHandler.obtainMessage();
		Bundle b = new Bundle();
		b.putInt(MTKDownload.CLOSE_PROGRESS, 1);
		msg.setData(b);
		mHandler.sendMessage(msg);
	}
}
