package com.mtkdownload

import android.app.Activity
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import androidx.preference.PreferenceScreen
import java.io.File

class PreferenceActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings_activity)
        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.settings, SettingsFragment())
                .commit()
        }
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }
    override fun finish() {
        val data=Intent()
        setResult(RESULT_OK,data)
        super.finish()

    }

}



class SettingsFragment : PreferenceFragmentCompat() {
    private val LOGTAG = "PrefsFragment"
    private var PathName = ""
    private var prefBT:ListPreference?=null
    private var prefChunk:ListPreference?=null
    private var prefMem:ListPreference?=null
    private var prefOverw:ListPreference?=null
    private var prefPath:EditTextPreference?=null


    private val REQUEST_CODE_PICK_DIR = 1
    /*declare Callback Function for Filebrowser*/
    val startForResult = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()) {
            result: ActivityResult ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            data?.let {
                if (it.hasExtra("returnData")) {
                    val newDir = it.extras?.getString("returnData")
                    Toast.makeText(
                        activity,
                        R.string.New_Export_Path.toString() + newDir,
                        Toast.LENGTH_LONG
                    ).show()

                    val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
                    val editor = sharedPreferences?.edit()
                    editor?.putString("Path", newDir)
                    editor?.commit()
                    val pathPref = findPreference<Preference>("path")
                    pathPref!!.summary = newDir
                } else {
                    Toast.makeText(activity, R.string.No_Changes_Made, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {

        setPreferencesFromResource(R.xml.root_preferences, rootKey)

        // Populate the listPreference with all the bluetooth devices
        val customPref = findPreference<Preference>("bluetoothListPref") as ListPreference?
        try{
            val manager =
                this.context?.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            val mBluetoothAdapter = manager.adapter
            val pairedDevices= mBluetoothAdapter.bondedDevices
            // If there are paired devices
            if (pairedDevices.size > 0) {
                // Loop through paired devices
                val entries = arrayOfNulls<CharSequence>(pairedDevices.size)
                val entrieValues = arrayOfNulls<CharSequence>(pairedDevices.size)
                var i = 0
                for (device in pairedDevices) {
                    // Add the name and address to an array adapter to show in a
                    // ListView
                    entries[i] = device.name
                    entrieValues[i] = device.address
                    i++
                }
                customPref!!.entries = entries
                customPref.entryValues = entrieValues
            } else {
                Toast.makeText(
                    activity,
                    R.string.noPairedBT,
                    Toast.LENGTH_LONG
                ).show()
                customPref!!.isEnabled = false
            }

        }catch (e: SecurityException){
            e.printStackTrace()
            return
        }
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())

        prefBT=findPreference("bluetoothListPref")
        prefBT?.summaryProvider=ListPreference.SimpleSummaryProvider.getInstance()
        prefChunk=findPreference("chunkSizePref")
        prefChunk?.summaryProvider=ListPreference.SimpleSummaryProvider.getInstance()
        prefMem=findPreference("memSizePref")
        prefMem?.summaryProvider=ListPreference.SimpleSummaryProvider.getInstance()
        prefOverw=findPreference("overwritePref")
        prefOverw?.summaryProvider=ListPreference.SimpleSummaryProvider.getInstance()
        prefPath=findPreference("path")
        prefPath?.summaryProvider=Preference.SummaryProvider <EditTextPreference> { preference ->
            val actPath=Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                .toString() + File.separator + sharedPreferences.getString("path","mtkDL") + File.separator
            actPath.toString()
        }

    }

    interface OnFragmentInteractionListener {
        fun onFragmentInteraction(uri: Uri?)
    }
}