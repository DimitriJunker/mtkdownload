package com.mtkdownload;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Environment;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceScreen;
import android.util.Log;
import android.widget.Toast;

import java.io.File;
import java.util.Set;


public class PrefsFragment extends PreferenceFragment {
	private final String LOGTAG = "PrefsFragment";
	private String PathName = "";

	private final int REQUEST_CODE_PICK_DIR = 1;

	@Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Load the preferences from an XML resource
        addPreferencesFromResource(R.xml.preferences);

        // Populate the listPreference with all the bluetooth devices
        ListPreference customPref = (ListPreference) findPreference("bluetoothListPref");
		BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
        // If there are paired devices
        if (pairedDevices.size() > 0) {
        	// Loop through paired devices
        	CharSequence[] entries = new CharSequence[pairedDevices.size()];
        	CharSequence[] entrieValues = new CharSequence[pairedDevices.size()];
        	int i = 0;
        	for (BluetoothDevice device : pairedDevices) {
        		// Add the name and address to an array adapter to show in a
        		// ListView
        		entries[i] = device.getName();
        		entrieValues[i] = device.getAddress();
        		i++;
        	}
        	customPref.setEntries(entries);
        	customPref.setEntryValues(entrieValues);
        }
        else {
        	customPref.setEnabled(false);
        }

		PathName = String.valueOf(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)) + File.separator + "mtkDL"+ File.separator;
        //  PathName = MTKDownload.getSharedPreferences().getString("Path", Environment.getExternalStorageDirectory().toString() );
        Preference pathPref = findPreference("path");
        pathPref.setSummary(PathName);
	}
	
	public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
		Log.d(LOGTAG, "Clicked on preference tree: "+preference.getKey());
		
		if (preference.getKey().compareTo("path") == 0) {
		     // Start the file chooser here

			Toast.makeText(getActivity(), R.string.NoBrowser , Toast.LENGTH_LONG).show();
			/*
			final SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getActivity());
			Log.d(LOGTAG, "Start browsing button pressed");
			Intent fileExploreIntent = new Intent(FileBrowserActivity.INTENT_ACTION_SELECT_DIR, null, getActivity(), FileBrowserActivity.class);
			fileExploreIntent.putExtra( FileBrowserActivity.startDirectoryParameter, sharedPreferences.getString("Path", Environment.getExternalStorageDirectory().toString() ) );
			startActivityForResult(fileExploreIntent, REQUEST_CODE_PICK_DIR);
			return true;
			 */
		}
		return false;
	}
	
	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		Log.d(LOGTAG, "onActivityResult("+requestCode+","+resultCode+",...)");
		
		if (requestCode == REQUEST_CODE_PICK_DIR) {
			if (resultCode == Activity.RESULT_OK) {
				String newDir = data.getStringExtra(FileBrowserActivity.returnDirectoryParameter);
				Toast.makeText(getActivity(), R.string.New_Export_Path + newDir, Toast.LENGTH_LONG).show();
				SharedPreferences sharedPreferences = MTKDownload.getSharedPreferences();
				SharedPreferences.Editor editor = sharedPreferences.edit();
				editor.putString("Path", newDir);
		        editor.commit();
		        Preference pathPref = findPreference("path");
		        pathPref.setSummary(newDir);
			} else {
				Toast.makeText(getActivity(), R.string.No_Changes_Made, Toast.LENGTH_LONG).show();
			}
		}

		super.onActivityResult(requestCode, resultCode, data);
	}
}
