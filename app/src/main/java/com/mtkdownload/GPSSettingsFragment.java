package com.mtkdownload;

import android.app.Fragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

public class GPSSettingsFragment extends Fragment  {
    
    @SuppressWarnings("unused")
	private static final String TAG = null;
	private TextView msg_field;
	private View myFragmentView;

	/** Called when the activity is first created. */
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }
    
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) 
    {
        // Inflate the layout for this fragment
    	myFragmentView = inflater.inflate(R.layout.gps_settings, container, false);

        msg_field = (TextView) myFragmentView.findViewById(R.id.settings_textview);

        return myFragmentView;        
    }
    
    public void onStart() {
       super.onStart();
    }
    
    public void onStop() {
       super.onStop();
    }    
    
    public void writeToMessageField(String text) {
    	msg_field.setText(text + '\n' + msg_field.getText());
    }
}

