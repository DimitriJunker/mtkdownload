package com.mtkdownload;

import com.mtkdownload.R;
import android.app.AlertDialog;
import android.app.Fragment;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

public class DownloadFragment extends Fragment {
	private View myFragmentView;
	private Button buttondel;
	private TextView msg_field;
	
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Inflate the layout for this fragment
    	myFragmentView = inflater.inflate(R.layout.main_fragment, container, false);
    	
    	buttondel = (Button) myFragmentView.findViewById(R.id.buttondel);
        buttondel.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                // Perform action on click
            	new AlertDialog.Builder(getActivity())
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setTitle(getString(R.string.Delete_log_Q))
                .setMessage(getString(R.string.Are_you_sure))
                .setPositiveButton(getString(R.string.Yes), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                    	MTKDownload act = (MTKDownload)getActivity();
                    	act.delLog();    
                    }

                })
                .setNegativeButton(getString(R.string.No), null)
                .show();
            }
        });

        msg_field = (TextView) myFragmentView.findViewById(R.id.main_textview);
        
        return myFragmentView;        
    }

    public void writeToMessageField(String text) {
    	msg_field.setText(text + '\n' + msg_field.getText());
    }

}
