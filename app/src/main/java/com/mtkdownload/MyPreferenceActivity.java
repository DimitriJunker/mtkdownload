package com.mtkdownload;

import android.os.Bundle;
import android.preference.PreferenceActivity;

public class MyPreferenceActivity extends PreferenceActivity {

	@Override
	protected void onCreate(final Bundle savedInstanceState) {
	    super.onCreate(savedInstanceState);
	    getFragmentManager().beginTransaction().replace(android.R.id.content, new PrefsFragment()).commit();
	}
}

