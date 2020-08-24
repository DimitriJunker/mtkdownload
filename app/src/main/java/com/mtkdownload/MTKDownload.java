package com.mtkdownload;

import com.mtkdownload.R;

import android.Manifest;
import android.app.ActionBar;
import android.app.ActionBar.Tab;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.text.format.Time;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

public class MTKDownload extends Activity
{
	public static final String TAG = "MTKDownload";
	
	private static final int REQUEST_ENABLE_BT = 2;
	// Local Bluetooth adapter
    private static BluetoothAdapter mBluetoothAdapter = null;
    private static SharedPreferences sharedPreferences;

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
        	Toast.makeText(this, "Bluetooth is not available", Toast.LENGTH_LONG).show();
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

		if (!isGPSSelected())
			return;
		
    	dialog = ProgressDialog.show(this, "Changing GPS settings", "Please wait...", true, false);
    	
    	// Start a thread to do the deleting
    	ChangeGPSSettingsRunable runnable = new ChangeGPSSettingsRunable(ThreadHandler, "PMTK182,1,6,2", "PMTK001,182,1,");
		Thread thread = new Thread(runnable);
		thread.start();

    	Log.d(TAG, "++++ Done: set_MemFull_STOP()");
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
    
    public void delLog() {
    	Log.v(TAG, "+++ delLog() +++");

		if (!isGPSSelected())
			return;

    	dialog = ProgressDialog.show(this, "@string/Del_log", "@string/wait", true, false);
    	
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
		//check permissions
		int  sdkVers= Build.VERSION.SDK_INT;
		boolean permOK=false;
		if(sdkVers>22)
		{

			if(checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)== PackageManager.PERMISSION_GRANTED)
				permOK=true;
			else
			{
				if (shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
					Toast.makeText(this, "Write External Starage Permision is needed to copy Logfile.",
							Toast.LENGTH_SHORT).show();
				}
				requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 110);
			}

		}
		else
			permOK=true;
		if(permOK){
			downloadBin = new DownloadBinRunnable(file_time_stamp, ThreadHandler);
			Thread downloadThread = new Thread(downloadBin);
			downloadThread.start();

			Log.d(TAG, "++++ Done: getLog()");

			}

	    }

	@Override
	public void onRequestPermissionsResult(int requestCode,String[] permissions,
										   int[] grantResults){
		if(requestCode==110){
			if(grantResults[0]==PackageManager.PERMISSION_GRANTED){
				downloadBin = new DownloadBinRunnable(file_time_stamp, ThreadHandler);
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

    	dialog = ProgressDialog.show(this, "@string/restart", "@string/wait", true , false);

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
				builder.setTitle("@string/Restart1");
				builder.setMessage("@string/Restart2");
				builder.setPositiveButton("@string/continue", new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						getLog2();
					}
				});
				builder.setNegativeButton("@string/abort", null);
				builder.setIcon(android.R.drawable.ic_dialog_alert);
				builder.show();

			}
			if (msg.getData().containsKey(KEY_PROGRESS)) {
    			dialog.show();
        		dialog.setProgress(msg.getData().getInt(KEY_PROGRESS));
			}
    		if (msg.getData().containsKey(CREATEGPX)) {
    			createGPX(msg.getData().getString(CREATEGPX));
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

	private void createGPX(String file_time_stamp) {		
		dialog = new ProgressDialog(MTKDownload.this);
		dialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
		dialog.setMessage("@string/Convert");
		dialog.setCancelable(false);
		dialog.setMax(100);
		dialog.show();
		
		// Start a new thread for it!
		ParseBinFile parseBinFile = new ParseBinFile(file_time_stamp, ThreadHandler);
		Thread gpxThread = new Thread(parseBinFile);
		gpxThread.start();
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
