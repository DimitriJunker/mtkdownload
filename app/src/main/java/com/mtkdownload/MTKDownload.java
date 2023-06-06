package com.mtkdownload;

import android.Manifest;
import android.app.ActionBar;
import android.app.ActionBar.Tab;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.text.format.Time;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.Objects;


public class MTKDownload extends Activity
{
	public static final String TAG = "MTKDownload";
	
	private static final int REQUEST_ENABLE_BT = 2;
	// Local Bluetooth adapter
    private static BluetoothAdapter mBluetoothAdapter = null;
    private static SharedPreferences sharedPreferences;

	private BufferedWriter log_writer;
	BufferedOutputStream bosBin;
	long lenBin;
	private static final int SIZEOF_SECTOR = 0x10000;
	// Bluetooth device string
    private ProgressDialog dialog;
    
    // Output date
    private String file_time_stamp;
    
	//GetThread get_thread;
    DownloadBinRunnable downloadBin;
	
	// Keys
	public static final String KEY_TOAST = "toast";
	public static final String MESSAGEFIELD = "msgField";
	public static final String SETTINGS_MESSAGEFIELD = "settingsMsgField";
	public static final String KEY_PROGRESS = "progressCompleted";
	public static final String CLOSE_PROGRESS = "closeProgressDialog";
	public static final String CREATEGPX = "parseBinFile";
	public static final String RESTART_GPS = "restartGPS";	//DYJ

	@Override
    public void onResume() {
    	super.onResume();
    }
    
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

		Log.v(TAG, "+++ ON CREATE +++");

    	sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
		// Clear all preferences. FOR TESTING!
        //SharedPreferences.Editor editor = sharedPreferences.edit();
        //editor.clear();
        //editor.commit();

        // Check if device has Bluetooth
    	mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter == null) {
            // Device does not support Bluetooth
        	Toast.makeText(this, R.string.Bluetooth_na, Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        // Check if Bluetooth is enabled
        if (!mBluetoothAdapter.isEnabled()) {
        	// No, ask user to start it
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }
        Log.i(TAG, "+++ GPS bluetooth device: "+sharedPreferences.getString("bluetoothListPref","-1"));

		//Check Blootooth Permission

		//check permissions
		int  sdkVers= Build.VERSION.SDK_INT;
		boolean permOK=false;
		if(sdkVers>32)
		{

			if(checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)== PackageManager.PERMISSION_GRANTED)
				permOK=true;
			else
			{
				if (shouldShowRequestPermissionRationale(Manifest.permission.BLUETOOTH_CONNECT)) {
					Toast.makeText(this, R.string.perm_con,
							Toast.LENGTH_SHORT).show();
				}
				requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT}, 110);
			}

		}
		else
			permOK=true;
		if(!permOK){
			finish();
		}




		final ActionBar bar = getActionBar();
        bar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);
        //bar.setDisplayOptions(1, ActionBar.DISPLAY_SHOW_TITLE);
        
        bar.addTab(bar.newTab()
                .setText(R.string.Download)
                .setTabListener(new TabListener<DownloadFragment>(this, "download", DownloadFragment.class)));
        
        bar.addTab(bar.newTab()
            .setText(R.string.Help)
            .setTabListener(new TabListener<HelpFragment>(this, "help", HelpFragment.class)));
        
        bar.addTab(bar.newTab()
                .setText(R.string.GPS_Settings)
                .setTabListener(new TabListener<GPSSettingsFragment>(this, "gps_settings", GPSSettingsFragment.class)));
    
        //createGPX("2014-04-25_093511");


	}
    
    // **** Restarting Button code ****
    public void hotStart(View v) {
    	performRestart(1);    
    }
    
    public void warmStart(View v) {
    	performRestart(2);    
    }

    public void coldStart(View v) {
    	performRestart(3);    
    }
    
    public void set_MemFull_STOP(View v) {
		Log.v(TAG, "+++ set_MemFull_STOP() +++");

		if (isGPSSelected()) {
			dialog = ProgressDialog.show(this, "Changing GPS settings", "Please wait...", true, false);

			// Start a thread to do the deleting
			ChangeGPSSettingsRunable runnable = new ChangeGPSSettingsRunable(ThreadHandler, "PMTK182,1,6,2", "PMTK001,182,1,");
			Thread thread = new Thread(runnable);
			thread.start();

			Log.d(TAG, "++++ Done: set_MemFull_STOP()");
		}
    }
    
    public void set_MemFull_OVERWRITE(View v) {
    	Log.v(TAG, "+++ set_MemFull_OVERWRITE() +++");
    	
    	if (!isGPSSelected())
			return;
		
    	dialog = ProgressDialog.show(this, "Changing GPS settings", "Please wait...", true, false);
    	
    	// Start a thread to do the deleting
    	ChangeGPSSettingsRunable runnable = new ChangeGPSSettingsRunable(ThreadHandler, "PMTK182,1,6,1", "PMTK001,182,1,");
		Thread thread = new Thread(runnable);
		thread.start();

    	Log.d(TAG, "++++ Done: set_MemFull_OVERWRITE()");
    }
    
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == REQUEST_ENABLE_BT) {// When the request to enable Bluetooth returns
			if (resultCode == Activity.RESULT_OK) {
				// Bluetooth is now enabled
			} else {
				// User did not enable Bluetooth or an error occured
				finish();
			}
		}
    }

    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_menu, menu);
        return true;
    }
    
    public boolean onOptionsItemSelected(MenuItem item) {
		if (item.getItemId() == R.id.preferences) {
			startActivity(new Intent(getBaseContext(), MyPreferenceActivity.class));
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	private   BufferedWriter getBufWr(String subDir, String file, String mime_type){
		return getBufWr(subDir,file,mime_type,false,0);

	}
	private   BufferedWriter getBufWr(String subDir, String file, String mime_type,boolean append,int sz){

		OutputStream fos;
		BufferedWriter bw=null;

		try{

			if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q){

				ContentResolver resolver =  getApplicationContext().getContentResolver();
				ContentValues contentValues =  new ContentValues();
				contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, file);
				contentValues.put(MediaStore.MediaColumns.MIME_TYPE, mime_type);
				contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + File.separator + subDir);
				Uri fileUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues);

				if(fileUri!=null){
					String mode;
					if (append)
						mode="wa";
					else
						mode="w";

					fos = resolver.openOutputStream(Objects.requireNonNull(fileUri),mode);
					Writer writer = new OutputStreamWriter(fos, "US-ASCII");
					if(sz==0)
						bw = new BufferedWriter(writer);
					else
						bw = new BufferedWriter(writer,sz);

				}
			}
			else{
				String dirN =String.valueOf(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)) + File.separator + subDir;
				File new_file = new File(dirN, file);
				if(sz==0)
					bw = new BufferedWriter(new FileWriter(new_file,append));
				else
					bw = new BufferedWriter(new FileWriter(new_file,append),sz);
			}
		}catch(IOException e){
			Toast.makeText(this, R.string.File_not_saved + e.toString(), Toast.LENGTH_SHORT).show();
		}

		return bw;

	}

	private boolean createSubdirectory(String subDirectoryName) {
		boolean ok=false;
		int  sdkVers= Build.VERSION.SDK_INT;
		if(sdkVers>22 && sdkVers<29)
		{
			if(checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)== PackageManager.PERMISSION_GRANTED)
				ok=true;
			else
			{
				if (shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
					Toast.makeText(this, R.string.Write_Ext_Perm,
							Toast.LENGTH_SHORT).show();
				}
				requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 110);
			}
		}
		else
			ok=true;
		if(ok) {
			File downloadsDirectory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
			File subDirectory = new File(downloadsDirectory, subDirectoryName);
			if (!subDirectory.exists()) {
				ok = subDirectory.mkdirs();
			}
		}
		return ok;
	}

	private BufferedOutputStream getBufOS(String subDir, String file, String mime_type,boolean append){

		BufferedOutputStream bOs=null;

		try{

			if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q){

				ContentResolver resolver =  getApplicationContext().getContentResolver();
				ContentValues contentValues =  new ContentValues();
				contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, file);
				contentValues.put(MediaStore.MediaColumns.MIME_TYPE, mime_type);
				contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + File.separator + subDir);
				Uri fileUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues);
				if(fileUri!=null){
					String mode;
					if (append)
						mode="wa";
					else
						mode="w";
					OutputStream oS=resolver.openOutputStream(fileUri,mode);
					bOs=new BufferedOutputStream(oS);
				}
			}
			else{
				String dirN =String.valueOf(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)) + File.separator + subDir;
				File new_file = new File(dirN, file);
				try {
					bOs = new BufferedOutputStream(new FileOutputStream(new_file, append), SIZEOF_SECTOR);

				} catch (FileNotFoundException e1) {
					e1.printStackTrace();
				}
			}

		}catch(IOException e){

			Toast.makeText(this, R.string.File_not_saved + e.toString(), Toast.LENGTH_SHORT).show();
		}


		return bOs;

	}
	private long getFLen(String subDir, String fileN){

		long length=0l;
		if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q){
			Uri contentUri = MediaStore.Files.getContentUri("external");
			String[] projection = {MediaStore.Files.FileColumns.SIZE};
			String selection = MediaStore.Files.FileColumns.DISPLAY_NAME + "=? AND " +
					MediaStore.Files.FileColumns.RELATIVE_PATH + " LIKE ?";
			String[] selectionArgs = {fileN, "%/"+subDir+"/%"};
			Cursor cursor = getApplicationContext().getContentResolver().query(contentUri, projection, selection, selectionArgs, null);
			if (cursor != null &&  cursor.moveToFirst()) {
				int sizeColumnIndex = cursor.getColumnIndex(MediaStore.Downloads.SIZE);
				length = cursor.getLong(sizeColumnIndex);
				cursor.close();
			}
		}
		else {
			String dirN = String.valueOf(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)) + File.separator + subDir;
			File DirF = new File(dirN);
			File file = new File(DirF, fileN);
			if (file.exists())
				length = file.length();
		}


		return length;


}
	public void delLog() {
    	Log.v(TAG, "+++ delLog() +++");

		if (!isGPSSelected())
			return;

		dialog = ProgressDialog.show(this, getString(R.string.Del_log), getString(R.string.wait), true, false);

    	// Start a thread to do the deleting
		DeleteRunnable deleteRunnable = new DeleteRunnable(ThreadHandler);
		Thread restartThread = new Thread(deleteRunnable);
		restartThread.start();

    	Log.d(TAG, "++++ Done: delLog()");
    }
	public void getLog(View v) {
		Log.v(TAG, "+++ getLog() +++");
		// Create a unique file for writing the log files to
		Time now = new Time();
		now.setToNow();
		file_time_stamp = now.format("%Y-%m-%d_%H%M%S");
		getLog2();
	}
    public void getLog2() {
    	Log.v(TAG, "+++ getLog() +++");

		// Get some preferences information
		if (!isGPSSelected())
			return;
		
		// Create a unique file for writing the log files to
 //   	Time now = new Time();
  //  	now.setToNow();
   // 	file_time_stamp = now.format("%Y-%m-%d_%H%M%S");

    	dialog = new ProgressDialog(this);
    	dialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
    	dialog.setMessage(getString(R.string.Downloading));
    	dialog.setCancelable(false);
    	dialog.setOnCancelListener(new OnCancelListener() {
            public void onCancel(DialogInterface dialog) {
            	killGetThread();
            }
        });
    	dialog.setMax(100);
    	dialog.show();

// Start a thread to get the log
		if(createSubdirectory("mtkDL")) {
			if (getSharedPreferences().getBoolean("createDebugPref", false)) {
				log_writer = getBufWr("mtkDL", "gpslog" + file_time_stamp + ".txt", "text/plain");
			} else
				log_writer = null;
			lenBin = getFLen("mtkDL", "gpslog" + file_time_stamp + ".bin");
			bosBin = getBufOS("mtkDL", "gpslog" + file_time_stamp + ".bin", "application/x-binary", lenBin > 0);
			if (bosBin != null) {
				downloadBin = new DownloadBinRunnable(file_time_stamp, log_writer, bosBin, lenBin, ThreadHandler);
				Thread downloadThread = new Thread(downloadBin);
				downloadThread.start();

				Log.d(TAG, "++++ Done: getLog()");
			} else
				Log.d(TAG, "++++ can't write to file for getLog()");
		}
	}

	@Override
	public void onRequestPermissionsResult(int requestCode,String[] permissions,
										   int[] grantResults){
		if(requestCode==110){
			if(grantResults[0]==PackageManager.PERMISSION_GRANTED){
				downloadBin = new DownloadBinRunnable(file_time_stamp,log_writer,bosBin,lenBin, ThreadHandler);
				Thread downloadThread = new Thread(downloadBin);
				downloadThread.start();

				Log.d(TAG, "++++ Done: getLog()");

			}
		}
		else {
			super.onRequestPermissionsResult(requestCode, permissions, grantResults);
		}
	}


	public void getPref(View v) {
    	Log.v(TAG, "+++ getPref() +++");

		// Get some preferences information
    	startActivity(new Intent(getBaseContext(), MyPreferenceActivity.class));
		
		
    	Log.d(TAG, "++++ Done: getPref()");
    }

    
    
    public void killGetThread() {
    	//writeToMainTextArea("Trying to cancel the thread");
    	downloadBin.running = false;
    }

    private void performRestart(int mode) {
    	Log.v(TAG, "+++ performRestart() +++");

		if (!isGPSSelected())
			return;

    	dialog = ProgressDialog.show(this, getString(R.string.restart), getString(R.string.wait), true , false);

    RestartRunnable restartRunnable = new RestartRunnable(ThreadHandler, mode);
		Thread restartThread = new Thread(restartRunnable);
		restartThread.start();
		// Start a thread to do the restarting

		Log.d(TAG, "++++ Done: performRestart()");
    }
  
    // Define a Handler 
    final Handler ThreadHandler = new Handler() {
    	public void handleMessage(Message msg) {
    		if (msg.getData().containsKey(MESSAGEFIELD)) {
    			DownloadFragment downFragment = (DownloadFragment)getFragmentManager().findFragmentByTag("download");
    			downFragment.writeToMessageField(msg.getData().getString(MESSAGEFIELD));
    		}
    		if (msg.getData().containsKey(SETTINGS_MESSAGEFIELD)) {
    			GPSSettingsFragment fragment = (GPSSettingsFragment)getFragmentManager().findFragmentByTag("gps_settings");
    			fragment.writeToMessageField(msg.getData().getString(SETTINGS_MESSAGEFIELD));
    		}
    		
    		if (msg.getData().containsKey(CLOSE_PROGRESS)) {
        		if (msg.getData().getInt(CLOSE_PROGRESS) == 1){
        			dialog.dismiss();
        		}
    		}
			if (msg.getData().containsKey(RESTART_GPS)) {	//DYJ
				dialog.dismiss();
				file_time_stamp = msg.getData().getString(RESTART_GPS);

				AlertDialog.Builder builder = new AlertDialog.Builder(MTKDownload.this);
				builder.setTitle(getString(R.string.Restart1));
				builder.setMessage(getString(R.string.Restart2));
				builder.setPositiveButton(getString(R.string.weiter), new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						getLog2();
					}
				});
				builder.setNegativeButton(getString(R.string.abort), null);
				builder.setIcon(android.R.drawable.ic_dialog_alert);
				builder.show();

			}
			if (msg.getData().containsKey(KEY_PROGRESS)) {
    			dialog.show();
        		dialog.setProgress(msg.getData().getInt(KEY_PROGRESS));
			}
    		if (msg.getData().containsKey(CREATEGPX)) {
				try {
					createGPX(msg.getData().getString(CREATEGPX));
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			}
			if (msg.getData().containsKey(KEY_TOAST)) {
				String message = msg.getData().getString(KEY_TOAST);
				Toast.makeText(MTKDownload.this, message, Toast.LENGTH_LONG).show();
			}
		}
    };
    
    public void close(View v) {
    	finish();    	
    }

	public static SharedPreferences getSharedPreferences() {
		return sharedPreferences;
	}
	
	public static BluetoothAdapter getmBluetoothAdapter() {
		return mBluetoothAdapter;
	}
		
	/**
	 * Check if Bluetooth GPS device selected
	 */
	private boolean isGPSSelected() {
		// Preferences
		// Bluetooth device string
		String GPS_bluetooth_id = sharedPreferences.getString("bluetoothListPref", "-1");
		if ("-1".equals(GPS_bluetooth_id) || GPS_bluetooth_id.length() == 0) {
			// No GPS device selected in the preferences
			AlertDialog.Builder builder = new AlertDialog.Builder(MTKDownload.this);
			builder.setMessage("Please select a GPS device in the preferences first!");
			builder.setPositiveButton("OK",
					new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int id) {
							Intent preferenceActivity = new Intent(getBaseContext(), MyPreferenceActivity.class);
							startActivity(preferenceActivity);
						}
					});
			builder.show();
			return false;
		}
		return true;
	}

	private void createGPX(String file_time_stamp) throws IOException {
		dialog = new ProgressDialog(MTKDownload.this);
		dialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
		dialog.setMessage(getString(R.string.Convert));
		dialog.setCancelable(false);
		dialog.setMax(100);
		dialog.show();
		
		// Start a new thread for it!

		BufferedWriter loggpx_writer,gpx_writer;
		if(getSharedPreferences().getBoolean("createDebugPref", false))		{
			loggpx_writer=getBufWr("mtkDL","gpslog" + file_time_stamp + "_gpx.txt","text/plain",true,SIZEOF_SECTOR);
		}
		else
			loggpx_writer=null;
		gpx_writer=getBufWr("mtkDL","gpslog" + file_time_stamp + ".gpx","application/gpx+xml",false,SIZEOF_SECTOR);
		if(gpx_writer!=null) {
			ParseBinFile parseBinFile = new ParseBinFile(file_time_stamp, loggpx_writer, gpx_writer, ThreadHandler);
			Thread gpxThread = new Thread(parseBinFile);
			gpxThread.start();
		}else
			gpx_writer.close();

	}
	
	@Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("tab", getActionBar().getSelectedNavigationIndex());
    }
	
	public static class TabListener<T extends Fragment> implements ActionBar.TabListener {
        private final Activity mActivity;
        private final String mTag;
        private final Class<T> mClass;
        private final Bundle mArgs;
        private Fragment mFragment;

        public TabListener(Activity activity, String tag, Class<T> clz) {
            this(activity, tag, clz, null);
        }

        public TabListener(Activity activity, String tag, Class<T> clz, Bundle args) {
            mActivity = activity;
            mTag = tag;
            mClass = clz;
            mArgs = args;

            // Check to see if we already have a fragment for this tab, probably
            // from a previously saved state.  If so, deactivate it, because our
            // initial state is that a tab isn't shown.
            mFragment = mActivity.getFragmentManager().findFragmentByTag(mTag);
            if (mFragment != null && !mFragment.isDetached()) {
                FragmentTransaction ft = mActivity.getFragmentManager().beginTransaction();
                ft.detach(mFragment);
                ft.commit();
            }
        }

        public void onTabSelected(Tab tab, FragmentTransaction ft) {
            if (mFragment == null) {
                mFragment = Fragment.instantiate(mActivity, mClass.getName(), mArgs);
                ft.add(android.R.id.content, mFragment, mTag);
            } else {
                ft.attach(mFragment);
            }
        }

        public void onTabUnselected(Tab tab, FragmentTransaction ft) {
            if (mFragment != null) {
                ft.detach(mFragment);
            }
        }

        public void onTabReselected(Tab tab, FragmentTransaction ft) {
            //Toast.makeText(mActivity, "Reselected!", Toast.LENGTH_SHORT).show();
        }
    }
}
