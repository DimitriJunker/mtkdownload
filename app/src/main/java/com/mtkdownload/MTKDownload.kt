package com.mtkdownload

import android.Manifest
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.provider.MediaStore
import android.util.Log
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import com.mtkdownload.databinding.ActivityMtkdownloadBinding
import com.mtkdownload.databinding.ContentMtkdownloadBinding
import java.io.BufferedOutputStream
import java.io.BufferedWriter
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.FileWriter
import java.io.IOException
import java.io.OutputStreamWriter
import java.io.Writer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Objects


class MTKDownload : AppCompatActivity() {

    val logTAG = "MTKDownload"
    // Local Bluetooth adapter
    private var mBluetoothAdapter: BluetoothAdapter? = null
    lateinit var sharedPreferences: SharedPreferences
    private lateinit var mainTextView:TextView
    private lateinit var bindingA: ActivityMtkdownloadBinding
    private lateinit var bindingC: ContentMtkdownloadBinding
    private var dialog: AlertDialog? = null
    var alertProgressBar: ProgressBar? = null
    private val SIZEOF_SECTOR = 0x10000

    // Output date
    var fileTimeStamp = ""
    //GetThread get_thread;
    var downloadBin: DownloadBinRunnable? = null
    var parseBinFile:ParseBinFile?=null

    // Keys
    val KEY_TOAST = "toast"
    val MESSAGEFIELD = "msgField"
    val SETTINGS_MESSAGEFIELD = "settingsMsgField"
    val KEY_PROGRESS = "progressCompleted"
    val CLOSE_PROGRESS = "closeProgressDialog"
    val CREATEGPX = "parseBinFile"
    val RESTART_GPS = "restartGPS" //DYJ
    private val WE_REQUEST_CODE = 102

    /** Called when the activity is first created. */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        bindingA = ActivityMtkdownloadBinding.inflate(layoutInflater)
        bindingC = ContentMtkdownloadBinding.inflate(layoutInflater)
        setContentView(bindingA.root)
        //       setContentView(bindingC.root)
        //       sharedPreferences = getPreferences(MODE_PRIVATE)
        sharedPreferences=PreferenceManager.getDefaultSharedPreferences(this)
        // Clear all preferences. FOR TESTING!
        //SharedPreferences.Editor editor = sharedPreferences.edit();
        //editor.clear();
        //editor.commit();

        setSupportActionBar(bindingA.toolbar)
        Log.v(logTAG, "+++ ON CREATE +++")


        //Check Blootooth Permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requestMultiplePermissions.launch(
                arrayOf(
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT
                )
            )
        } else {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            requestBluetooth.launch(enableBtIntent)
        }
        Log.i(
            logTAG,
            "+++ GPS bluetooth device: " + sharedPreferences.getString(
                "bluetoothListPref",
                "-1"
            )
        )
        val bluetoothManager: BluetoothManager = getSystemService(BluetoothManager::class.java)
        mBluetoothAdapter = bluetoothManager.getAdapter()
        if (mBluetoothAdapter == null) {
            // Device does not support Bluetooth
            Toast.makeText(this, R.string.Bluetooth_na, Toast.LENGTH_LONG).show()
            finish()
            return
        }
        mainTextView=findViewById(R.id.mainTextView)
        val sdkVers = Build.VERSION.SDK_INT
        if (sdkVers > 22 && sdkVers < 29) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
            ) else {
                if (shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                    Toast.makeText(
                        this, R.string.Write_Ext_Perm,
                        Toast.LENGTH_SHORT
                    ).show()
                }
                requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 110)
            }
        }
    }

    protected fun makeRequest(permissionType: String, reqCode: Int) {
        ActivityCompat.requestPermissions(this, arrayOf(permissionType), reqCode)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String?>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == WE_REQUEST_CODE) {
            if (grantResults.size == 0 || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                bindingC.loadButton.isEnabled=false
                Toast.makeText(
                    this,
                    R.string.Write_Ext_Perm,
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }


    fun set_MemFull_STOP(v: View?) {
        Log.v(logTAG, "+++ set_MemFull_STOP() +++")
        if (isGPSSelected()) {
            dialog = setProgressDialog(this, getString(R.string.chgGps), false, 0)
            dialog!!.show()

            // Start a thread to do the deleting
            val runnable =
                ChangeGPSSettingsRunable(this, ThreadHandler, "PMTK182,1,6,2", "PMTK001,182,1,")
            val thread = Thread(runnable)
            thread.start()
            Log.d(logTAG, "++++ Done: set_MemFull_STOP()")
        }
    }

    fun set_MemFull_OVERWRITE(v: View?) {
        Log.v(logTAG, "+++ set_MemFull_OVERWRITE() +++")
        if (!isGPSSelected()) return
        dialog = setProgressDialog(this, getString(R.string.chgGps), false, 0)
        dialog!!.show()

        // Start a thread to do the deleting
        val runnable =
            ChangeGPSSettingsRunable(this, ThreadHandler, "PMTK182,1,6,1", "PMTK001,182,1,")
        val thread = Thread(runnable)
        thread.start()
        Log.d(logTAG, "++++ Done: set_MemFull_OVERWRITE()")
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        return when (item.itemId) {
            R.id.action_settings -> settings()
            R.id.action_GPSsettings -> gpsSettings()
            R.id.action_Help -> help()
            R.id.action_CR -> about()
            R.id.action_End -> ende()
            else -> super.onOptionsItemSelected(item)
        }
    }
    fun getBufWr(
        file: String,
        mime_type: String,
        append: Boolean,
        sz: Int
    ): Pair<BufferedWriter?,String> {
        var bw: BufferedWriter? = null
        var path=""
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = applicationContext.contentResolver
                val fileUri =getFileUri(resolver,file,mime_type)
                if (fileUri != null) {
                    path= fileUri.path.toString()
                    val mode: String
                    mode = if (append) "wa" else "w"
                    val fos = resolver.openOutputStream(Objects.requireNonNull(fileUri), mode)
                    val writer: Writer = OutputStreamWriter(fos, "US-ASCII")
                    bw = if (sz == 0) BufferedWriter(writer) else BufferedWriter(writer, sz)
                }
            } else {
                val dirN =getDlDir()
                val new_file = File(dirN, file)
                path=new_file.path
                bw = if (sz == 0) BufferedWriter(FileWriter(new_file, append)) else BufferedWriter(
                    FileWriter(new_file, append), sz
                )
            }
        } catch (e: IOException) {
            Toast.makeText(
                this,
                R.string.File_not_saved.toString() + e.toString(),
                Toast.LENGTH_SHORT
            ).show()
        }
        return Pair(bw,path)
    }
    public fun makeBinBOS(
        file: String,
        mime_type: String,
        append: Boolean
    ): Pair<BufferedOutputStream?,String> {
        var bOs: BufferedOutputStream? = null
        var path=""
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = applicationContext.contentResolver
                val fileUri =getFileUri(resolver,file,mime_type)
                if (fileUri != null) {
                    path= fileUri.path.toString()
                    val mode: String
                    mode = if (append) "wa" else "w"
                    val oS = resolver.openOutputStream(fileUri, mode)
                    bOs = BufferedOutputStream(oS)
                }
            } else {
                val dirN =getDlDir()
                val new_file = File(dirN, file)
                try {
                    val fos= FileOutputStream(new_file, append)
                    bOs = BufferedOutputStream(fos, SIZEOF_SECTOR)
                    //                  bOs = BufferedOutputStream(FileOutputStream(new_file, append), SIZEOF_SECTOR)
                } catch (e1: FileNotFoundException) {
                    e1.printStackTrace()
                }
                path=new_file.path
            }
        } catch (e: IOException) {
            Toast.makeText(
                this,
                R.string.File_not_saved.toString() + e.toString(),
                Toast.LENGTH_SHORT
            ).show()
        }
        return Pair(bOs,path)
    }



    private var requestBluetooth =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                //granted
            } else {
                // Device does not support Bluetooth
                Toast.makeText(this, R.string.Bluetooth_na, Toast.LENGTH_LONG).show()
                finish()
            }
        }

    private val requestMultiplePermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            permissions.entries.forEach {
                Log.d("Permission Test", "${it.key} = ${it.value}")
            }
        }

    private fun createSubdirectory(): Boolean {
        var ok=true
        val subDirectory = File(getDlDir())
        if (!subDirectory.exists()) {
            ok = subDirectory.mkdirs()
        }
        return ok
    }
    fun getFLen(fileN: String): Long {
        var length = 0L
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val subDir=sharedPreferences.getString("path","mtkDL");
            val contentUri = MediaStore.Files.getContentUri("external")
            val projection = arrayOf(MediaStore.Files.FileColumns.SIZE)
            val selection = MediaStore.Files.FileColumns.DISPLAY_NAME + "=? AND " +
                    MediaStore.Files.FileColumns.RELATIVE_PATH + " LIKE ?"
            val selectionArgs = arrayOf(
                fileN,
                "%/$subDir/%"
            )
            val cursor = applicationContext.contentResolver.query(
                contentUri,
                projection,
                selection,
                selectionArgs,
                null
            )
            if (cursor != null && cursor.moveToFirst()) {
                val sizeColumnIndex = cursor.getColumnIndex(MediaStore.Downloads.SIZE)
                length = cursor.getLong(sizeColumnIndex)
                cursor.close()
            }
        } else {
            val dirN =getDlDir()
            val DirF = File(dirN)
            val file = File(DirF, fileN)
            if (file.exists()) length = file.length()
        }
        return length
    }
    fun delLog(v: View?) {
        Log.v(logTAG, "+++ delLog() +++")

        // Perform action on click
        AlertDialog.Builder(this)
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setTitle(getString(R.string.Delete_log_Q))
            .setMessage(getString(R.string.Are_you_sure))
            .setPositiveButton(
                getString(R.string.Yes)
            ) { _, _ ->
                delLog2()
            }
            .setNegativeButton(getString(R.string.No), null)
            .show()
    }

    fun delLog2() {
        Log.v(logTAG, "+++ delLog() +++")
        if (!isGPSSelected()) return
        dialog = setProgressDialog(this, getString(R.string.Del_log), false, 0)
        dialog!!.show()

        // Start a thread to do the deleting
        val deleteRunnable = DeleteRunnable(this, ThreadHandler)
        val restartThread = Thread(deleteRunnable)
        restartThread.start()
        Log.d(logTAG, "++++ Done: delLog()")
    }
    fun killGetThread() {
        writeToMessageField(getString(R.string.cancelDL))
        downloadBin?.running = false
    }
    fun killGPXThread() {
        writeToMessageField(getString(R.string.cancelCV))
        parseBinFile?.running = false
    }

    fun performRestart(v: View?) {
        Log.v(logTAG, "+++ performRestart() +++")
        if (!isGPSSelected()||v==null) return
        //v.getId() == R.id.button1
        val mode:Int

        when(v.id){
            R.id.buttonhotstart-> mode=1
            R.id.buttonwarmstart->mode=2
            R.id.buttoncoldstart->mode=3
            else ->return
        }


        dialog = setProgressDialog(this, getString(R.string.restart), false, 0)
        dialog!!.show()

        // Start a thread to do the deleting
        val restartRunnable = RestartRunnable(this, ThreadHandler, mode)
        val restartThread = Thread(restartRunnable)
        restartThread.start()
        // Start a thread to do the restarting
        Log.d(logTAG, "++++ Done: performRestart()")
    }

    // Define a Handler
    val ThreadHandler: Handler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {

            if (msg.getData().containsKey(MESSAGEFIELD)) {
                writeToMessageField(msg.getData().getString(MESSAGEFIELD).toString())
            }
            if (msg.getData().containsKey(SETTINGS_MESSAGEFIELD)) {
                val fragment: GpsSettingsActivity =
                    supportFragmentManager.findFragmentByTag("gps_settings") as GpsSettingsActivity
                fragment.writeToMessageField(msg.getData().getString(SETTINGS_MESSAGEFIELD).toString())
            }

            if (msg.getData().containsKey(CLOSE_PROGRESS)) {
                if (msg.getData().getInt(CLOSE_PROGRESS) == 1) {
                    dialog!!.dismiss()
                }
            }
            if (msg.getData().containsKey(RESTART_GPS)) {    //DYJ
                dialog!!.dismiss()
                fileTimeStamp = msg.getData().getString(RESTART_GPS).toString()
                val builder = AlertDialog.Builder(this@MTKDownload)
                builder.setTitle(getString(R.string.Restart1))
                builder.setMessage(getString(R.string.Restart2))
                builder.setPositiveButton(
                    getString(R.string.weiter)
                ) { _, _ -> getLog2() }
                builder.setNegativeButton(getString(R.string.abort), null)


                builder.setIcon(android.R.drawable.ic_dialog_alert)
                builder.show()
            }
            if (msg.getData().containsKey(KEY_PROGRESS)) {
                dialog!!.show()
                alertProgressBar?.setProgress(msg.getData().getInt(KEY_PROGRESS))
            }
            if (msg.getData().containsKey(CREATEGPX)) {
                try {
                    createGPX(msg.getData().getString(CREATEGPX).toString())
                } catch (e: IOException) {
                    throw RuntimeException(e)
                }
            }
            if (msg.getData().containsKey(KEY_TOAST)) {
                val message: String = msg.getData().getString(KEY_TOAST).toString()
                Toast.makeText(this@MTKDownload, message, Toast.LENGTH_LONG).show()
            }
        }
    }
    fun close( item: MenuItem) {
        finish()
    }

    fun getmBluetoothAdapter(): BluetoothAdapter? {
        return mBluetoothAdapter
    }
    /**
     * Check if Bluetooth GPS device selected
     */
    private fun isGPSSelected(): Boolean {
        // Preferences
        // Bluetooth device string


        val GPS_bluetooth_id = sharedPreferences.getString("bluetoothListPref", "-1")
        if ("-1" == GPS_bluetooth_id || GPS_bluetooth_id!!.length == 0) {
            // No GPS device selected in the preferences
            val builder = AlertDialog.Builder(this@MTKDownload)
            builder.setMessage("Please select a GPS device in the preferences first!")
            builder.setPositiveButton(
                "OK"
            ) { _, _ ->
                val preferenceActivity = Intent(baseContext, PreferenceActivity::class.java)
                startActivity(preferenceActivity)
            }
            builder.show()
            return false
        }
        return true
    }

    @Throws(IOException::class)

    private fun createGPX(fts: String) {
        fileTimeStamp=fts

        dialog = setProgressDialog(this, getString(R.string.Convert), false, 100)

        dialog!!.setButton(
            DialogInterface.BUTTON_NEGATIVE, "Cancel"
        ) { dialog, _ ->
            dialog.dismiss() //dismiss dialog
            killGPXThread()
        }
        dialog!!.show()

        // Start a new thread for it!
        Log.d(logTAG, "++++ ParseBinFile()")
        parseBinFile  = ParseBinFile(this, ThreadHandler)

        val gpxThread  = Thread(parseBinFile)
        gpxThread .start()
        Log.d(logTAG, "++++ Done: getLog()")
    }
    /*
        override fun onSaveInstanceState(outState: Bundle) {
            super.onSaveInstanceState(outState)
            outState.putInt("tab", actionBar!!.selectedNavigationIndex)
        }

    */
    fun settings(): Boolean {
        val intent = Intent(this, PreferenceActivity::class.java)
        startActivity(intent)
        return true
    }

    fun gpsSettings(): Boolean {
        val intent = Intent(this, GpsSettingsActivity::class.java)
        startActivity(intent)
        return true

    }

    fun help(): Boolean {
        val intent = Intent(this, HelpActivity::class.java)
        startActivity(intent)
        return true
    }
    fun about(): Boolean {
        val intent = Intent(this, AboutActivity::class.java)
        startActivity(intent)
        return true
    }

    fun ende(): Boolean {
        finish()
        return true
    }

    fun getLog(v: View?) {
        Log.v(logTAG, "+++ getLog() +++")
        // Create a unique file for writing the log files to
        val sdf = SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.getDefault())
        fileTimeStamp = sdf.format(Date())

        getLog2()
    }

    fun getLog2() {
        Log.v(logTAG, "+++ getLog() +++")

        // Get some preferences information
        if (!isGPSSelected()) return

        // Create a unique file for writing the log files to
        //   	Time now = new Time();
        //  	now.setToNow();
        // 	fileTimeStamp = now.format("%Y-%m-%d_%H%M%S");
        dialog = setProgressDialog(this, getString(R.string.Downloading), false, 100)

        dialog!!.setButton(
            DialogInterface.BUTTON_NEGATIVE, "Cancel"
        ) { dialog, _ ->
            dialog.dismiss() //dismiss dialog
            killGetThread()
        }
        dialog!!.show()

// Start a thread to get the log
      //  var sharedPreferences=PreferenceManager.getDefaultSharedPreferences(this)

        if (createSubdirectory()) {
            downloadBin = DownloadBinRunnable(this, ThreadHandler)
            val downloadThread = Thread(downloadBin)
            downloadThread.start()
            Log.d(logTAG, "++++ Done: getLog()")
        }
    }



    fun setProgressDialog(
        context: Context,
        message: String,
        cancelable: Boolean,
        max: Int
    ): AlertDialog {
        val llPadding = 30
        val ll = LinearLayout(context)
        ll.orientation = if(max==0)  LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
        ll.setPadding(llPadding, llPadding, llPadding, llPadding)
        ll.gravity = Gravity.CENTER

        var llParam = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        llParam.gravity = Gravity.CENTER
        ll.layoutParams = llParam
        val progressBar = if(max==0) ProgressBar(context) else ProgressBar(context,null,android.R.attr.progressBarStyleHorizontal)
        progressBar.isIndeterminate = false
        progressBar.setPadding(0, 0, llPadding, 0)
        if(max!=0){         //richtiger Balken
            val layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            // Setze die Breite der ProgressBar auf MATCH_PARENT
            layoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT
            progressBar.layoutParams = layoutParams
            progressBar.max = max
        }else{      //rotierender Kreis
            progressBar.layoutParams = llParam

        }
        alertProgressBar = progressBar

        llParam = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        llParam.gravity = Gravity.CENTER
        val tvText = TextView(context)
        tvText.text = message
        tvText.setTextColor(Color.parseColor("#000000"))
        tvText.textSize = 20.toFloat()
        tvText.layoutParams = llParam

        ll.addView(tvText)
        ll.addView(progressBar)

        val builder = AlertDialog.Builder(context)
        builder.setCancelable(cancelable)
        builder.setView(ll)

        val dialog = builder.create()
        val window = dialog.window
        if (window != null) {
            val layoutParams = WindowManager.LayoutParams()
            layoutParams.copyFrom(dialog.window?.attributes)
            layoutParams.width = LinearLayout.LayoutParams.WRAP_CONTENT
            layoutParams.height = LinearLayout.LayoutParams.WRAP_CONTENT
            layoutParams.gravity=Gravity.TOP
            layoutParams.y=100
            dialog.window?.attributes = layoutParams
        }
        return dialog
    }

    fun writeToMessageField(newText: String) {
        val text=
            """
        $newText
        ${mainTextView.text}
        """.trimIndent()

        mainTextView.text=text
    }
    public fun getDlDir():String{
        val subDir=sharedPreferences.getString("path","mtkDL");
        val dirN =
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                .toString() + File.separator + subDir
        return dirN
    }
    private fun getFileUri(resolver:ContentResolver,file: String,mime_type: String):Uri?{
        val subDir=sharedPreferences.getString("path","mtkDL");
        val contentValues = ContentValues()
        contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, file)
        contentValues.put(MediaStore.MediaColumns.MIME_TYPE, mime_type)
        contentValues.put(
            MediaStore.MediaColumns.RELATIVE_PATH,
            Environment.DIRECTORY_DOWNLOADS + File.separator + subDir
        )
        val fileUri =
            resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
        return fileUri

    }
}
