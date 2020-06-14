package com.mtkdownload;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.Handler;
import android.os.Message;
import android.text.format.Time;
import android.util.Log;
import android.widget.Toast;

public class GPSrxtx {
	private static final String TAG = "MTKDownload-GPSrxtx";
	
	public InputStream in = null;
	public OutputStream out = null;
	
	private BluetoothAdapter mBluetoothAdapter = null;
	private String dev_id;
	private	BluetoothSocket sock = null;
	private StringBuilder buffer = new StringBuilder();
	BufferedWriter log_writer = null; //DYJ
	public GPSrxtx(BluetoothAdapter adapter, String gpsdev) {
		mBluetoothAdapter = adapter;
		dev_id = gpsdev;
	}
	public boolean connect() {
		Log.d(TAG, "++++ connect()");
		
    	BluetoothDevice zee = mBluetoothAdapter.getRemoteDevice(dev_id);
    	Method m = null;
		try {
			m = zee.getClass().getMethod("createRfcommSocket", new Class[] { int.class });
		} catch (SecurityException e1) {
			e1.printStackTrace();
			return false;
		} catch (NoSuchMethodException e1) {
			e1.printStackTrace();
			return false;
		}
    	try {
			sock = (BluetoothSocket)m.invoke(zee, Integer.valueOf(1));
		} catch (IllegalArgumentException e1) {
			e1.printStackTrace();
			return false;
		} catch (IllegalAccessException e1) {
			e1.printStackTrace();
			return false;
		} catch (InvocationTargetException e1) {
			e1.printStackTrace();
			return false;
		}
    	try {
			sock.connect();
		} catch (IOException e1) {
			e1.printStackTrace();
			return false;
		}
    	Log.d(TAG, "++++ Connected");
    	try {
			in = sock.getInputStream();
		} catch (IOException e1) {
			e1.printStackTrace();
			return false;
		}
    	try {
			out = sock.getOutputStream();
		} catch (IOException e1) {
			e1.printStackTrace();
			return false;
		}

		return true;
	}

	public void sendCommand(String command) throws IOException {
		int i = command.length();
		byte checksum = 0;
		while (--i >= 0) {
			checksum ^= (byte) command.charAt(i);
		}
		StringBuilder rec = new StringBuilder(256);
		rec.setLength(0);
		rec.append('$');
		rec.append(command);
		rec.append('*');
		rec.append(Integer.toHexString(checksum));
		rec.append("\r\n");
		Log.d(TAG, "++++ Writing: " + rec.toString() );

		// Actually send it
		out.write(rec.toString().getBytes());
	}

	public byte[] readBytes(double timeout) throws IOException, InterruptedException
	{
    	double time = 0.0;
    	int bytes_available = 0;

    	while((bytes_available = in.available()) == 0 && time < timeout) {
    	    // throws interrupted exception
    	    Thread.sleep(250);
    	    time += 0.25;
    	}
   		byte[] buf = new byte[bytes_available];
   		in.read(buf);
   		
   		Log.d(TAG, "++++ Read "+bytes_available+" bytes from GPS");
		return buf;
	}
	public void Log(String text) {
		if (log_writer != null) {
			// Create a unique file for writing the log files to
			Time now = new Time();
			now.setToNow();
			String time = now.format("%H:%M:%S ");
			try {
				log_writer.append(time);
				log_writer.append(text);
				log_writer.append('\n');
				log_writer.flush();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	public String waitForReply(String reply, double timeout,BufferedWriter log_writer_1) throws IOException, InterruptedException 
	{
		byte[] buf;
		// Read from the device until we get the reply we are looking for
		log_writer=log_writer_1;
    	Log.d(TAG, "++++ Reading from device, waiting for: " +reply+", timeout: "+ timeout);

    	int i = 0;
		while (i < 10) { //DYJ was 100
			Log(String.format("waitForReply i %d", i));
			buf = readBytes(timeout);
			Log(String.format("waitForReply read: %d", buf.length));
			if (buf.length == 0) {
				Log.d(TAG, "++++ No bytes read from device!");
				throw new IOException();
			}
    		for (int j = 0; j < buf.length; j++) {
    			char b = (char)(buf[j] & 0xff);
    			// Check if this is the start of a new message
    			if (buffer.length() > 0 && b == '$') {
    				// Yep new message started, parse old message (if any)
    				i++;
    				String message = buffer.toString();
    				Log.d(TAG, "++++ Received a message("+i+"): "+ message);
    				Log(String.format("Received a message: %s",message));
    				if (message.charAt(0) == '$') {
    					if (message.indexOf(reply, 0) > 0) {
    						Log.d(TAG, "++++ Breaking because we received:" + reply);
    						buffer.setLength(0);
    						for (int k = j; k < buf.length; k++) {
    							char c = (char)(buf[k] & 0xff);
    							buffer.append(c);
    						}
    						return message;
    					}
    				}
    				buffer.setLength(0);
    			}
    			buffer.append(b);
    		}
		}
		// We did not receive the message we where waiting for after 100 messages! Return empty string.
		Log.d(TAG, "++++ We did not receive " + reply + " for after 100 messages!");
    	return "";
	}
	
	public void close() {
		Log.d(TAG, "++++ close()");
		try {
			sock.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
