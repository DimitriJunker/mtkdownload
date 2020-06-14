package com.mtkdownload;

import java.io.IOException;

import android.os.Handler;
import android.util.Log;

public class ChangeGPSSettingsRunable extends GeneralRunnable {

	private String command;
	private String responce;
	
	ChangeGPSSettingsRunable(Handler h, String command, String responce) {
		super(h);
		this.command = command;
		this.responce = responce;
	}

	public void run() 
	{	
		Log.v(MTKDownload.TAG, "+++ ON ChangeGPSSettingsRunable.run("+ command +", "+ responce +") +++");
			
		GPSrxtx gpsdev = new GPSrxtx(MTKDownload.getmBluetoothAdapter(), GPS_bluetooth_id);
		if (gpsdev.connect()) {
    		// Send the command to perform restart
        	try {
				gpsdev.sendCommand(command);
			} catch (IOException e) {
				sendMessageField("Failed");
				gpsdev.close();
                return;
			}
    		// Wait for reply from the device
        	try {
				gpsdev.waitForReply(responce, 60.0,null);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
        	gpsdev.close();

        	sendSettings_MessageField("Changing GPS setting succeed");
    	}
    	else {
    		sendSettings_MessageField("Error, could not connect to GPS device");
    	}
		sendCloseProgress();
		Log.d(MTKDownload.TAG, "++++ Done: ChangeGPSSettingsRunable()");
	}
}
