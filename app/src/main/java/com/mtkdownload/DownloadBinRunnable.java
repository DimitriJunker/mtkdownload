package com.mtkdownload;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.format.Time;

import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DownloadBinRunnable implements Runnable {
	volatile boolean running = true;
	
	private static final int SIZEOF_SECTOR = 0x10000;

	private final Handler dHandler;
	
    File bin_file;
	BufferedOutputStream bin_output_stream = null;
    File gpx_file;
    File log_file = null;
    BufferedWriter log_writer = null;
    GPSrxtx gpsdev = null;
	Message msg;
	Bundle b;
    
	// Output date
	private final String file_time_stamp;
	private BufferedWriter bwLog;
	private BufferedOutputStream bosBin;
	private long binLen;

	// Preferences
	private boolean create_log_file = false;
	private boolean createGPX = true;
	private int SIZEOF_CHUNK = 0x0800;
    private int SIZEOF_GPS_MEMORY = 0;
	private int OVERWRITE_MODE = 0;
    private static String GPS_bluetooth_id;

	public DownloadBinRunnable(String file_time_stamp, BufferedWriter bwLog, BufferedOutputStream bosBin,long binLen, Handler downLoadDialogHandler) {
		this.dHandler = downLoadDialogHandler;
		this.file_time_stamp = file_time_stamp;
		this.bwLog = bwLog;
		this.bosBin = bosBin;
		this.binLen = binLen;

		// Get some preferences information
		create_log_file = MTKDownload.getSharedPreferences().getBoolean("createDebugPref", false);
		createGPX = MTKDownload.getSharedPreferences().getBoolean("createGPXPref", true);
		SIZEOF_CHUNK = Integer.parseInt(MTKDownload.getSharedPreferences().getString("chunkSizePref", "4096"));
    	SIZEOF_GPS_MEMORY = Integer.parseInt(MTKDownload.getSharedPreferences().getString("memSizePref", "0"));
    	OVERWRITE_MODE = Integer.parseInt(MTKDownload.getSharedPreferences().getString("overwritePref", "0"));
    	GPS_bluetooth_id = MTKDownload.getSharedPreferences().getString("bluetoothListPref", "-1");
	}

	public int getFlashSize (int model) {
        // 8 Mbit = 1 Mb
        if (model == 0x1388) return( 8 * 1024 * 1024 / 8); // 757/ZI v1
        if (model == 0x5202) return( 8 * 1024 * 1024 / 8); // 757/ZI v2
        // 32 Mbit = 4 Mb
        if (model == 0x0000) return(32 * 1024 * 1024 / 8); // Holux M-1200E
        if (model == 0x0001) return(32 * 1024 * 1024 / 8); // Qstarz BT-Q1000X
        if (model == 0x0004) return(32 * 1024 * 1024 / 8); // 747 A+ GPS Trip Recorder
        if (model == 0x0005) return(32 * 1024 * 1024 / 8); // Qstarz BT-Q1000P
        if (model == 0x0006) return(32 * 1024 * 1024 / 8); // 747 A+ GPS Trip Recorder
        if (model == 0x0008) return(32 * 1024 * 1024 / 8); // Pentagram PathFinder P 3106
        if (model == 0x000F) return(32 * 1024 * 1024 / 8); // 747 A+ GPS Trip Recorder
        if (model == 0x005C) return(32 * 1024 * 1024 / 8); // Holux M-1000C
        if (model == 0x8300) return(32 * 1024 * 1024 / 8); // Qstarz BT-1200
        // 16Mbit -> 2Mb
        // 0x0051    i-Blue 737, Qstarz 810, Polaris iBT-GPS, Holux M1000
        // 0x0002    Qstarz 815
        // 0x001b    i-Blue 747
        // 0x001d    BT-Q1000 / BGL-32
        // 0x0131    EB-85A
        return(16 * 1024 * 1024 / 8);
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

	public void errorWhileDownloading() {
		closeGPS_bin();
    	//cleanup();
    	
    	// Report download failed!
        sendCloseProgress();
        sendMessageToMessageField("Download failed");
    }
	public void errorWhileDownloading_cont() {	//DYJ
		closeGPS_bin();
		//cleanup();

		// Report download failed but try to continue!
		sendRestartGPS();
		sendMessageToMessageField(String.format("try restart and continue: %s", this.file_time_stamp));
	}

    public void closeGPS_only() {
        Log("Closing GPS device");
    	gpsdev.close();

    }
	public void closeGPS_bin() {
		Log("Closing GPS device and bin");
		gpsdev.close();

		// Close the bin file
		try {
			if (bin_output_stream != null) {
				bin_output_stream.flush();
				bin_output_stream.close();
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void cleanup() {
    	// Clean up, delete the bin file and perhaps the log file
    	bin_file.delete();
    	if (log_file != null) {
    		log_file.delete();
    	}
    }
	public void run() {
		String reply = null;
		Pattern p;
		Matcher m;
		
        sendMessageToMessageField("Getting log from GPS");
		sendPercentageComplete(0);
		// Open log file

		if (create_log_file) {
			log_writer= bwLog;
		}
		Log(String.format("Trying to connect to GPS device: %s", GPS_bluetooth_id));
    	gpsdev = new GPSrxtx(MTKDownload.getmBluetoothAdapter(), GPS_bluetooth_id);
    	if (gpsdev.connect()) {
			Log(String.format("Connected to GPS device: %s", GPS_bluetooth_id));

    		// Query recording method when full (OVERWRITE/STOP).
			Log("Sending command: PMTK182,2,6 and waiting for reply: PMTK182,3,6,");
    		try {
				gpsdev.sendCommand("PMTK182,2,6");
			} catch (IOException e2) {
				// Error sending... oops.. close connection and fail
				errorWhileDownloading();
				return;
			}
    		// Wait for reply from the device
        	try {
				reply = gpsdev.waitForReply("PMTK182,3,6,", 10.0,log_writer);
			} catch (IOException e2) {
				// Error reading... oops.. close connection and fail
				errorWhileDownloading();
				return;
			} catch (InterruptedException e2) {
				// TODO Auto-generated catch block
				e2.printStackTrace();
			}
			Log(String.format("Got reply: %s", reply));

        	// log_full_method == 1 means overwrite from the beginning
        	// log_full_method == 2 means stop recording
        	int log_full_method = 0;
        	p = Pattern.compile(".*PMTK182,3,6,([0-9]+).*");
        	m = p.matcher(reply);
        	if (m.find()) {
        		log_full_method = Integer.parseInt(m.group(1));
        	}

        	// Determine how much bytes we need to read from the memory
        	int bytes_to_read = SIZEOF_GPS_MEMORY;
        	if (log_full_method == 1) {
        		// Device is in OVERWRITE mode we don't know where data ends; read the entire memory.
        		if (OVERWRITE_MODE == 0) {
                    sendMessageToMessageField("NOTE! Your device is in 'OVERWRITE when FULL mode', this is not a very efficient mode for download over bluetooth. Aborting! If you really want to download in this mode, please enable it via the preferences");            		
            		sendCloseProgress();
					closeGPS_bin();
                    return;
        		}
        		if (bytes_to_read > 0) {
            		Log(String.format("Device is in OVERWRITE mode, memory size set by user preferences"));
        		}
        		else {
            		Log(String.format("Device is in OVERWRITE mode, trying to determine memory size"));
            		int flashManuProdID = 0;
            		// Query memory information
        			Log("Sending command: PMTK605 and waiting for reply: PMTK705,");
                    try {
						gpsdev.sendCommand("PMTK605");
					} catch (IOException e) {
						// Error sending... oops.. close connection and fail
						errorWhileDownloading();
						return;
					}
                    // Wait for reply from the device
                    try {
						reply = gpsdev.waitForReply("PMTK705,", 10.0,log_writer);
					} catch (IOException e) {
						// Error reading... oops.. close connection and fail
						errorWhileDownloading();
						return;
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
        			Log(String.format("Got reply: %s", reply));
                    p = Pattern.compile(".*PMTK705,[\\.0-9A-Za-z_-]+,([0-9A-Za-z]+).*");
                    m = p.matcher(reply);
                    if (m.find()) {
                        flashManuProdID = Integer.parseInt(m.group(1), 16);
                        Log(String.format("flashManuProdID: %d (0x%08X)", flashManuProdID, flashManuProdID));
                    }
                    bytes_to_read = getFlashSize(flashManuProdID);
        		}
        	}
        	else {
        		Log(String.format("Device is in STOP mode finding next write address"));
        		int next_write_address = 0;
        		// Query the RCD_ADDR (data log Next Write Address).
    			Log("Sending command: PMTK182,2,8 and waiting for reply: PMTK182,3,8,");
        		try {
					gpsdev.sendCommand("PMTK182,2,8");
				} catch (IOException e) {
					// Error sending... oops.. close connection and fail
					errorWhileDownloading();
					return;
				}
        		// Wait for reply from the device
        		try {
					reply = gpsdev.waitForReply("PMTK182,3,8,", 10.0,log_writer);
				} catch (IOException e) {
					// Error reading... oops.. close connection and fail
					errorWhileDownloading();
					return;
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
    			Log(String.format("Got reply: %s", reply));

        		p = Pattern.compile(".*PMTK182,3,8,([0-9A-Za-z]+).*");
        		m = p.matcher(reply);
        		if (m.find()) {
        			next_write_address = Integer.parseInt(m.group(1), 16);  
        			Log(String.format("Next write address: %d (0x%08X)", next_write_address, next_write_address));
        		}
        		int sectors  = (int) Math.floor(next_write_address / SIZEOF_SECTOR);
        		if (next_write_address % SIZEOF_SECTOR != 0) {
        			sectors += 1;
        		}
        		bytes_to_read = sectors * SIZEOF_SECTOR;
        	}
            Log(String.format("Need to read %d (0x%08X) bytes of log data from device...", bytes_to_read, bytes_to_read));

        	// Open an output stream for writing
			//DYJ: if the file already exists errorWhileDownloading_cont was called and the download should be continued
			int offset;
			if(bosBin!=null) {
				bin_output_stream = bosBin;
				offset = (int) binLen;
			}
			else{
				errorWhileDownloading();
				return;
			}
            // To be safe we iterate requesting SIZEOF_CHUNK bytes at time.
            while (running && offset < bytes_to_read) {
                // Request log data (PMTK_LOG_REQ_DATA) from offset to bytes_to_read.
            	String command = String.format("PMTK182,7,%08X,%08X", offset, SIZEOF_CHUNK);
    			Log(String.format("Sending command: %s", command));
            	try {
					gpsdev.sendCommand(command);
				} catch (IOException e1) {
					// Error sending... oops.. close connection and fail
					errorWhileDownloading();
					return;
				}
                // Read from the device
            	// The chunk might be split over more than one message
            	// read until all bytes are received
            	int number_of_empty = 0;
                byte[] tmp_array = new byte[SIZEOF_CHUNK];
                int bytes_received = 0;
                int number_of_message = 1;
                if (SIZEOF_CHUNK > 0x800) {
                    number_of_message = SIZEOF_CHUNK/0x800;
                }
                Log(String.format("SIZEOF_CHUNK=%d, waiting for %d PMTK182,8 messages", SIZEOF_CHUNK, number_of_message));
                for (int j=0; j<number_of_message; j++) {
                    Log(String.format("waiting for part:%d", j));	//tmp
            		try {
						reply = gpsdev.waitForReply("PMTK182,8", 10.0,log_writer);
					} catch (IOException e1) {		//DYJ Start
						// Error reading... oops.. close connection and reconnect
				    	closeGPS_only();
				    	reply ="";
				        sendMessageToMessageField("try to reconnect");
		                Log(String.format("try to reconnect"));
                	    int w=500;
						int reconnects=0;
                	    boolean ok=false;

							while(w<5000 && !ok){
                    		Log(String.format("Trying to reconnect to GPS device: %s", GPS_bluetooth_id));
                    	    try {
    							Thread.sleep(w);
    						} catch (InterruptedException e) {
    							// TODO Auto-generated catch block
    							e.printStackTrace();
    						}

                    	    ok=gpsdev.connect();
							Log(String.format("connect (%d)",++reconnects));
                	    	w+=500;
                	    }
                    	if (!ok) {
    						errorWhileDownloading_cont();
    						return;
                    	}
	                	Log(String.format("Connected to GPS device: %s", GPS_bluetooth_id));
	                	j=number_of_message; //no need to wait for the others after reconnection.
	                	//DYJ end
					} catch (InterruptedException e1) {
						// TODO Auto-generated catch block
						e1.printStackTrace();
					}
            		if (reply == "") {
            			// Asked for message was not found.
	        			Log(String.format("Asked for message was not found"));
            			continue;
            		}
        			Log(String.format("Got reply: %s", reply));
        			byte[] bytes = reply.getBytes();
            		if(bytes[reply.length()-5]=='*'){
            			int csum=0;
            			for (int i = 1; i < (reply.length()-5); i ++) {
	            			csum=csum^bytes[i];
	            		}
            			String string_byte = reply.substring(reply.length()-4, reply.length()-2);
            			int csumT=257;
            			try {
            				csumT = (Integer.parseInt(string_byte, 16) & 0xFF);
            			} catch (NumberFormatException e) {
            				//e.printStackTrace();
            			}
            			if(csum==csumT) {
            				Log(String.format("Checksum ok:%d", csum));
            			}
            			else {
            				Log(String.format("Checksum Error:%d", csum));
            				reply="";
            			}
           			}
            		for (int i = 20; i < (reply.length()-3); i += 2) {
            			String string_byte = reply.substring(i, i+2);
            			if (string_byte.equals("FF")) {
            				number_of_empty++;
            			}
            			try {
            				tmp_array[bytes_received] = (byte) (Integer.parseInt(string_byte, 16) & 0xFF);
            				bytes_received++;
            			} catch (NumberFormatException e) {
            				//e.printStackTrace();
            			}
            		}
                }
                if (bytes_received != SIZEOF_CHUNK) {
                    Log(String.format("ERROR! bytes_received(%d) != SIZEOF_CHUNK", bytes_received));
                    continue;
                }
                else {
                    offset += SIZEOF_CHUNK;
                    try {
						bin_output_stream.write(tmp_array, 0, SIZEOF_CHUNK);
					} catch (IOException e) {
						e.printStackTrace();
					}
                }
                // In OVERWRITE mode when user asked us, when we find and empty sector assume rest of memory is empty
        		if (OVERWRITE_MODE == 1 && number_of_empty == bytes_received) {
        			offset = bytes_to_read;
        			Log(String.format("Found empty SIZEOF_CHUNK, stopping reading any further"));
        		}

            	double percetageComplete = ((offset+SIZEOF_CHUNK) / (double)bytes_to_read) * 100.0;
            	Log(String.format("Saved log data: %6.2f%%", percetageComplete));
            	sendPercentageComplete((int)percetageComplete);
            }
        	
            closeGPS_bin();

            if (!running) {
                sendCloseProgress();
                sendMessageToMessageField("Download aborted");
                cleanup();
        		return;
        	}

        	// Send a status message to the main thread
            sendCloseProgress();
            sendMessageToMessageField("Download complete saved to:" + bin_file);

			// Close the log file
        	if (log_writer != null) {
            	try {
        			log_writer.close();
    			} catch (IOException e) {
    				e.printStackTrace();
    			}
        	}
    	}
    	else {
    		Log(String.format("Could NOT connected to GPS device: %s", GPS_bluetooth_id));
        	// Send a status message to the main thread
    		sendCloseProgress();
    		sendMessageToMessageField("Error, could not connect to GPS device");
    		return;
    	}
    	
    	// Check if we should also create a GPX file
    	if (createGPX) {
    		sendCreateGPX(file_time_stamp);
    	}
    	
		/*
		Log(String.format("Trying to connect to GPS device: %s", GPS_bluetooth_id));
		GPSrxtx gpsdev = new GPSrxtx(MTKDownload.getmBluetoothAdapter(), GPS_bluetooth_id);
		if (gpsdev.connect()) {
			try {
				Log(String.format("Connected to GPS device: %s", GPS_bluetooth_id));

				// Query recording method when full (OVERLAP/STOP).
				Log("Sending command: PMTK182,2,6 and waiting for reply: PMTK182,3,6,");
				gpsdev.sendCommand("PMTK182,2,6");
				// Wait for reply from the device
				reply = gpsdev.waitForReply("PMTK182,3,6,");
				Log(String.format("Got reply: %s", reply));

				// log_full_method == 1 means overwrite from the beginning
				// log_full_method == 2 means stop recording
				int log_full_method = 0;
				p = Pattern.compile(".*PMTK182,3,6,([0-9]+).*");
				m = p.matcher(reply);
				if (m.find()) {
					log_full_method = Integer.parseInt(m.group(1));
				}
				Log(String.format("Recording method on memory full: %d",
						log_full_method));

				// Determine how much bytes we need to read from the memory
				int bytes_to_read = 0;
				if (log_full_method == 1) {
					// Device is in OVERLAP mode we don't know where data ends;
					// read
					// the entire memory.
					int flashManuProdID = 0;
					// Query memory information
					Log("Sending command: PMTK605 and waiting for reply: PMTK705,");
					gpsdev.sendCommand("PMTK605");
					// Wait for reply from the device
					reply = gpsdev.waitForReply("PMTK705,");
					Log(String.format("Got reply: %s", reply));
					p = Pattern
							.compile(".*PMTK705,[\\.0-9A-Za-z_-]+,([0-9A-Za-z]+).*");
					m = p.matcher(reply);
					if (m.find()) {
						flashManuProdID = Integer.parseInt(m.group(1), 16);
						Log(String.format("flashManuProdID: %d (0x%08X)",
								flashManuProdID, flashManuProdID));
					}
					bytes_to_read = getFlashSize(flashManuProdID);
				} else {
					int next_write_address = 0;
					// Query the RCD_ADDR (data log Next Write Address).
					Log("Sending command: PMTK182,2,8 and waiting for reply: PMTK182,3,8,");
					gpsdev.sendCommand("PMTK182,2,8");
					// Wait for reply from the device
					reply = gpsdev.waitForReply("PMTK182,3,8,");
					Log(String.format("Got reply: %s", reply));

					p = Pattern.compile(".*PMTK182,3,8,([0-9A-Za-z]+).*");
					m = p.matcher(reply);
					if (m.find()) {
						next_write_address = Integer.parseInt(m.group(1), 16);
						Log(String.format("Next write address: %d (0x%08X)",
								next_write_address, next_write_address));
					}
					int sectors = (int) Math.floor(next_write_address
							/ SIZEOF_SECTOR);
					if (next_write_address % SIZEOF_SECTOR != 0) {
						sectors += 1;
					}
					bytes_to_read = sectors * SIZEOF_SECTOR;
				}
				Log(String
						.format("Retrieving %d (0x%08X) bytes of log data from device...",
								bytes_to_read, bytes_to_read));

				// Open an output stream for writing
				File bin_file = new File(Environment.getExternalStorageDirectory(), "gpslog" + file_time_stamp + ".bin");
				try {
					output_stream = new BufferedOutputStream(new FileOutputStream(bin_file), SIZEOF_SECTOR);
				} catch (FileNotFoundException e1) {
					e1.printStackTrace();
					return;
				}

				// To be safe we iterate requesting SIZEOF_CHUNK bytes at time.
				for (int offset = 0; offset < bytes_to_read; offset += SIZEOF_CHUNK) {
					// Request log data (PMTK_LOG_REQ_DATA) from offset to
					// bytes_to_read.
					String command = String.format("PMTK182,7,%08X,%08X",
							offset, SIZEOF_CHUNK);
					Log(String.format("Sending command: %s", command));

					gpsdev.sendCommand(command);

					// Read from the device
					// It seems the chunk might be split over more than one
					// message
					// read until all bytes are received
					int number_of_empty = 0;
					byte[] tmp_array = new byte[SIZEOF_CHUNK];
					int bytes_received = 0;
					int number_of_message = 1;
					if (SIZEOF_CHUNK > 0x800) {
						number_of_message = SIZEOF_CHUNK / 0x800;
					}
					Log(String.format("Waiting for %d PMTK182,8 messages",
							number_of_message));
					for (int j = 0; j < number_of_message; j++) {
						reply = gpsdev.waitForReply("PMTK182,8");
						Log(String.format("Got reply: %s", reply));
						for (int i = 20; i < (reply.length() - 3); i += 2) {
							String string_byte = reply.substring(i, i + 2);
							if (string_byte.equals("FF")) {
								number_of_empty++;
							}
							try {
								tmp_array[bytes_received] = (byte) (Integer
										.parseInt(string_byte, 16) & 0xFF);
								bytes_received++;
							} catch (NumberFormatException e) {
								e.printStackTrace();

							}
						}
					}
					if (bytes_received != SIZEOF_CHUNK) {
						Log(String.format(
								"ERROR! bytes_received(%d) != SIZEOF_CHUNK",
								bytes_received));
						offset -= SIZEOF_CHUNK;
						continue;
					} else {
						try {
							output_stream.write(tmp_array, 0, SIZEOF_CHUNK);
						} catch (IOException e) {
							e.printStackTrace();
						}
					}
					if (number_of_empty == bytes_received) {
						offset = bytes_to_read;
						Log(String
								.format("Found empty SIZEOF_CHUNK, stopping reading any further"));
					}

					double percentageComplete = ((offset + SIZEOF_CHUNK) / (double) bytes_to_read) * 100.0;
					Log(String.format("Saved log data: %6.2f%%",
							percentageComplete));
					sendPercentageConverted((int) percentageComplete);
				}
			} catch (BluetoothException be) {
				String exceptionMessage="Bluetooth connection problem: " + be.getMessage();
				sendTOAST(exceptionMessage);
				createGPX = false;
			}

			Log("Closing GPS device");
			gpsdev.close();

			// Close the bin file
			try {
				output_stream.flush();
				output_stream.close();
			} catch (IOException e) {
				e.printStackTrace();

			}
		} else {
			Log("Bluetooth connection problems");
			sendTOAST(R.string.GPSinactive);
			createGPX = false;
		}
		sendPercentageConverted(99);

		// Close the log file
		if (log_writer != null) {
			try {
				log_writer.close();
			} catch (IOException e) {
				e.printStackTrace();

			}
		}*/
	}

	private void sendPercentageComplete(int percentageComplete) {
		Message msg = dHandler.obtainMessage();
		Bundle b = new Bundle();
		b.putInt(MTKDownload.KEY_PROGRESS, percentageComplete);
		msg.setData(b);
		dHandler.sendMessage(msg);
	}

	private void sendMessageToMessageField(String message) {
		Message msg = dHandler.obtainMessage();
		Bundle b = new Bundle();
		b.putString(MTKDownload.MESSAGEFIELD, message);
		msg.setData(b);
		dHandler.sendMessage(msg);
	}
	
	private void sendCreateGPX(String message) {
		Message msg = dHandler.obtainMessage();
		Bundle b = new Bundle();
		b.putString(MTKDownload.CREATEGPX, message);
		msg.setData(b);
		dHandler.sendMessage(msg);
	}

	private void sendCloseProgress() {
		Message msg = dHandler.obtainMessage();
		Bundle b = new Bundle();
		b.putInt(MTKDownload.CLOSE_PROGRESS, 1);
		msg.setData(b);
		dHandler.sendMessage(msg);
	}
	private void sendRestartGPS() {	//DYJ
		Message msg = dHandler.obtainMessage();
		Bundle b = new Bundle();
		b.putString(MTKDownload.RESTART_GPS,this.file_time_stamp );
		msg.setData(b);
		dHandler.sendMessage(msg);
	}

}
