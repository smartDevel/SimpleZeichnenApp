package me.ways4.simplezeichnenapp

import android.Manifest
import android.app.Dialog
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.widget.*
import android.widget.SeekBar.OnSeekBarChangeListener
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.get
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream


class MainActivity : AppCompatActivity() {
    private var drawingView: DrawingView? = null
    private var mImageButtonCurrentPaint: ImageButton? =
        null // A variable for current color is picked from color pallet.

    var customProgressDialog: Dialog? = null

    //20220926_V2_1_Added_AboutMenu Dialog-Screen für App-Info
    var customAboutDialog: Dialog? = null


    private var mVersionCode = BuildConfig.VERSION_CODE
    private var mVersionName = BuildConfig.VERSION_NAME
    private var mAppName: String = "Simple Zeichnen-App"

    // 20220923_SY_Changed_uri_MediaStore
    // static variables
    companion object {
        private const val AUTHORITY = "${BuildConfig.APPLICATION_ID}.fileprovider"
    }

    //Todo 2: create an activity result launcher to open an intent
    val openGalleryLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            //Todo 3: get the returned result from the lambda and check the resultcode and the data returned
            if (result.resultCode == RESULT_OK && result.data != null) {
                //process the data
                //Todo 4 if the data is not null reference the imageView from the layout
                val imageBackground: ImageView = findViewById(R.id.iv_background)
                //Todo 5: set the imageuri received
                imageBackground.setImageURI(result.data?.data)
            }
        }

    /** create an ActivityResultLauncher with MultiplePermissions since we are requesting
     * both read and write
     */
    val requestPermission: ActivityResultLauncher<Array<String>> =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            permissions.entries.forEach {
                val perMissionName = it.key
                val isGranted = it.value
                //if permission is granted show a toast and perform operation
                if (isGranted) {
                    Toast.makeText(
                        this@MainActivity,
                        "Permission granted now you can read the storage files.",
                        Toast.LENGTH_LONG
                    ).show()
                    //perform operation
                    //Todo 1: create an intent to pick image from external storage
                    val pickIntent =
                        Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                    //Todo 6: using the intent launcher created above launch the pick intent
                    openGalleryLauncher.launch(pickIntent)
                } else {
                    //Displaying another toast if permission is not granted and this time focus on
                    //    Read external storage
                    if (perMissionName == Manifest.permission.READ_EXTERNAL_STORAGE)
                        Toast.makeText(
                            this@MainActivity,
                            "Oops you just denied the permission.",
                            Toast.LENGTH_LONG
                        ).show()
                }
            }

        }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        drawingView = findViewById(R.id.drawing_view)
        val ibBrush: ImageButton = findViewById(R.id.ib_brush)
        val ibStroke: ImageButton = findViewById(R.id.ib_stroke)
        val ibOpacity: ImageButton = findViewById(R.id.ib_opacity)

        drawingView?.setSizeForBrush(20.toFloat())
        val linearLayoutPaintColors = findViewById<LinearLayout>(R.id.ll_paint_colors)
        mImageButtonCurrentPaint = linearLayoutPaintColors[1] as ImageButton
        mImageButtonCurrentPaint?.setImageDrawable(
            ContextCompat.getDrawable(
                this,
                R.drawable.pallet_pressed
            )
        )
        ibBrush.setOnClickListener {
            showBrushSizeChooserDialog()
        }
        ibStroke.setOnClickListener {
            showStrokeStyleChooserDialog()
        }

        ibOpacity.setOnClickListener {
            showOpacityChooserDialog()
        }

        val ibGallery: ImageButton = findViewById(R.id.ib_gallery)
        ibGallery.setOnClickListener {
            requestStoragePermission()
        }
        val ibUndo: ImageButton = findViewById(R.id.ib_undo)
        ibUndo.setOnClickListener {
            // This is for undo recent stroke.
            drawingView?.onClickUndo()
        }
        //reference the save button from the layout
        val ibSave: ImageButton = findViewById(R.id.ib_save)
        //set onclick listener
        ibSave.setOnClickListener {

            //20220925_V2_Added_AboutMenu Refactor Save
            img_save()
        }
    }

    private fun showOpacityChooserDialog() {

        val opacDialog = Dialog(this)
        opacDialog.setContentView(R.layout.opacity_chooser)
        opacDialog.setTitle("Opacity :")
        val seekTxt: TextView = opacDialog.findViewById(R.id.opq_txt)
        val seekOpq: SeekBar = opacDialog.findViewById(R.id.opacity_seek)
        seekOpq.max = 255
        var currOpac: Int = 0
        currOpac = drawingView?.getPaintAlpha()!!
        seekTxt.text = currOpac.toString() + " von 255"
        seekOpq.progress = currOpac
        seekOpq.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                seekTxt.text = Integer.toString(progress) + " von 255"
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })
        val opqBtn = opacDialog.findViewById(R.id.opq_ok) as Button

        opqBtn.setOnClickListener(View.OnClickListener {
            drawingView?.setPaintAlpha(seekOpq.progress)
            opacDialog.dismiss()
        })
        opacDialog.show()

    }


    /**
     * Method is used to launch the dialog to select different brush sizes.
     */
    private fun showBrushSizeChooserDialog() {
        val brushDialog = Dialog(this)
        brushDialog.setContentView(R.layout.dialog_brush_size)
        brushDialog.setTitle("Strichstärke :")

//20220924_AddedProgressbarBrushSize
        val brushBarTxt: TextView = brushDialog.findViewById(R.id.tvBrushSizeBar_txt)
        val seekBrush: SeekBar = brushDialog.findViewById(R.id.seek_brush)
        seekBrush.max = 255
        seekBrush.min = 0
        val currBrushSize = drawingView?.getBrushSize()?.let { Math.round(it) }
        //var currBrushProg = currBrushSize!! / 138 * 50
        //Log.e("CurrentBrushSize", "Current Brush-Size " + currBrushSize + " :");
        val txtBrushBar: String = currBrushSize.toString() + " von 50"
        brushBarTxt.text = txtBrushBar

        if (currBrushSize != null) {
            seekBrush.progress = currBrushSize.toInt()
        }
        seekBrush.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                brushBarTxt.text =
                    Math.round(progress.toFloat() / 255 * 50).toInt().toString() + " von 50"
                val newBrushSize: Float = Math.round(progress.toFloat() / 255 * 50).toFloat()
                drawingView?.setSizeForBrush(newBrushSize)
                //Log.e("NewBrushSize", "New Brush-Size " + newBrushSize + " :")


            }
            //20220924


            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        }
        )
        val smallBtn: ImageButton = brushDialog.findViewById(R.id.ib_small_brush)
        smallBtn.setOnClickListener(View.OnClickListener {
            drawingView?.setSizeForBrush(10.toFloat())
            brushDialog.dismiss()
        })
        val mediumBtn: ImageButton = brushDialog.findViewById(R.id.ib_medium_brush)
        mediumBtn.setOnClickListener(View.OnClickListener {
            drawingView?.setSizeForBrush(20.toFloat())
            brushDialog.dismiss()
        })

        val largeBtn: ImageButton = brushDialog.findViewById(R.id.ib_large_brush)
        largeBtn.setOnClickListener(View.OnClickListener {
            drawingView?.setSizeForBrush(30.toFloat())
            brushDialog.dismiss()
        })
        brushDialog.show()
    }

    private fun showStrokeStyleChooserDialog() {
        val strokestyleDialog = Dialog(this)
        strokestyleDialog.setContentView(R.layout.dialog_stroke_style)
        strokestyleDialog.setTitle("Stroke-Style :")
        val strokeBtn: ImageButton = strokestyleDialog.findViewById(R.id.ib_strokebrush)
        strokeBtn.setOnClickListener(View.OnClickListener {
            drawingView?.setStrokeStyle(1)
            strokestyleDialog.dismiss()
        })
        val fillstrokeBtn: ImageButton = strokestyleDialog.findViewById(R.id.ib_fillstroke_brush)
        fillstrokeBtn.setOnClickListener(View.OnClickListener {
            drawingView?.setStrokeStyle(2)
            strokestyleDialog.dismiss()
        })
        val fillBtn: ImageButton = strokestyleDialog.findViewById(R.id.ib_fill_brush)
        fillBtn.setOnClickListener(View.OnClickListener {
            drawingView?.setStrokeStyle(3)
            strokestyleDialog.dismiss()
        })
        strokestyleDialog.show()
    }

    /**
     * Method is called when color is clicked from pallet_normal.
     *
     * @param view ImageButton on which click took place.
     */
    fun paintClicked(view: View) {
        if (view !== mImageButtonCurrentPaint) {
            // Update the color
            val imageButton = view as ImageButton
            // Here the tag is used for swaping the current color with previous color.
            // The tag stores the selected view
            val colorTag = imageButton.tag.toString()
            // The color is set as per the selected tag here.
            drawingView?.setColor(colorTag)
            // Swap the backgrounds for last active and currently active image button.
            imageButton.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.pallet_pressed))
            mImageButtonCurrentPaint?.setImageDrawable(
                ContextCompat.getDrawable(
                    this,
                    R.drawable.pallet_normal
                )
            )

            //Current view is updated with selected view in the form of ImageButton.
            mImageButtonCurrentPaint = view
        }
    }

    /**
     * We are calling this method to check the permission status
     */
    private fun isReadStorageAllowed(): Boolean {
        //Getting the permission status
        // Here the checkSelfPermission is
        /**
         * Determine whether <em>you</em> have been granted a particular permission.
         *
         * @param permission The name of the permission being checked.
         *
         */
        val result = ContextCompat.checkSelfPermission(
            this, Manifest.permission.READ_EXTERNAL_STORAGE
        )

        /**
         *
         * @return {@link android.content.pm.PackageManager#PERMISSION_GRANTED} if you have the
         * permission, or {@link android.content.pm.PackageManager#PERMISSION_DENIED} if not.
         *
         */
        //If permission is granted returning true and If permission is not granted returning false
        return result == PackageManager.PERMISSION_GRANTED
    }

    //create a method to requestStorage permission
    private fun requestStoragePermission() {
        // Check if the permission was denied and show rationale
        if (
            ActivityCompat.shouldShowRequestPermissionRationale(
                this,
                Manifest.permission.READ_EXTERNAL_STORAGE
            )
        ) {
            //call the rationale dialog to tell the user why they need to allow permission request
            showRationaleDialog(
                "Einfache Zeichnen App", "Zeichnen-App " +
                        "benötigt Speicherzugriff"
            )
        } else {
            // You can directly ask for the permission.
            //if it has not been denied then request for permission
            //  The registered ActivityResultCallback gets the result of this request.
            requestPermission.launch(
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
            )
        }

    }

    /**  create rationale dialog
     * Shows rationale dialog for displaying why the app needs permission
     * Only shown if the user has denied the permission request previously
     */
    private fun showRationaleDialog(
        title: String,
        message: String,
    ) {
        val builder: AlertDialog.Builder = AlertDialog.Builder(this)
        builder.setTitle(title)
            .setMessage(message)
            .setPositiveButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
        builder.create().show()
    }

    /**
     * Create bitmap from view and returns it
     */
    private fun getBitmapFromView(view: View): Bitmap {

        //Define a bitmap with the same size as the view.
        // CreateBitmap : Returns a mutable bitmap with the specified width and height
        val returnedBitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        //Bind a canvas to it
        val canvas = Canvas(returnedBitmap)
        //Get the view's background
        val bgDrawable = view.background
        if (bgDrawable != null) {
            //has background drawable, then draw it on the canvas
            bgDrawable.draw(canvas)
        } else {
            //does not have background drawable, then draw white background on the canvas
            canvas.drawColor(Color.WHITE)
        }
        // draw the view on the canvas
        view.draw(canvas)
        //return the bitmap
        return returnedBitmap
    }

    private suspend fun saveBitmapFile(mBitmap: Bitmap?): String {
        var result = ""
        withContext(Dispatchers.IO) {
            if (mBitmap != null) {

                try {
                    val bytes = ByteArrayOutputStream() // Creates a new byte array output stream.
                    // The buffer capacity is initially 32 bytes, though its size increases if necessary.

                    mBitmap.compress(Bitmap.CompressFormat.PNG, 90, bytes)
                    /**
                     * Write a compressed version of the bitmap to the specified outputstream.
                     * If this returns true, the bitmap can be reconstructed by passing a
                     * corresponding inputstream to BitmapFactory.decodeStream(). Note: not
                     * all Formats support all bitmap configs directly, so it is possible that
                     * the returned bitmap from BitmapFactory could be in a different bitdepth,
                     * and/or may have lost per-pixel alpha (e.g. JPEG only supports opaque
                     * pixels).
                     *
                     * @param format   The format of the compressed image
                     * @param quality  Hint to the compressor, 0-100. 0 meaning compress for
                     *                 small size, 100 meaning compress for max quality. Some
                     *                 formats, like PNG which is lossless, will ignore the
                     *                 quality setting
                     * @param stream   The outputstream to write the compressed data.
                     * @return true if successfully compressed to the specified stream.
                     */

                    /*val f = File(
                       "Download"
                               + File.separator + "SySimpleZeichenApp_" + System.currentTimeMillis() / 1000 + ".jpg"
                   )*/
                    val f = File(
                        externalCacheDir?.absoluteFile.toString()
                                + File.separator + "SySimpleZeichnenApp_" + System.currentTimeMillis() / 1000 + ".jpg"
                    )
                    // Here the Environment : Provides access to environment variables.
                    // getExternalStorageDirectory : returns the primary shared/external storage directory.
                    // absoluteFile : Returns the absolute form of this abstract pathname.
                    // File.separator : The system-dependent default name-separator character. This string contains a single character.

                    val fo =
                        FileOutputStream(f) // Creates a file output stream to write to the file represented by the specified object.
                    fo.write(bytes.toByteArray()) // Writes bytes from the specified byte array to this file output stream.
                    fo.close() // Closes this file output stream and releases any system resources associated with this stream. This file output stream may no longer be used for writing bytes.
                    result = f.absolutePath // The file absolute path is return as a result.
                    //We switch from io to ui thread to show a toast
                    runOnUiThread {
                        cancelProgressDialog()
                        if (!result.isEmpty()) {
                            Toast.makeText(
                                this@MainActivity,
                                "File saved successfully :$result",
                                Toast.LENGTH_LONG
                            ).show()
                            //20220923_SY_Changed_Removed_funMediaScannerConnections
                            // old fun commented
                            //shareImage(result)
                            //new fun:
                            shareImage2(
                                FileProvider.getUriForFile(
                                    baseContext,
                                    "me.ways4.simplezeichnenapp.fileprovider",
                                    f
                                )
                            )
                        } else {
                            Toast.makeText(
                                this@MainActivity,
                                "Something went wrong while saving the file.",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                } catch (e: Exception) {
                    result = ""
                    e.printStackTrace()
                }
            }
        }
        return result
    }

    //20220923_SY_Changed_Removed_funMediaScannerConnections
    // Old funShareImage no more needed
    private fun shareImage(result: String) {

        // START

        /*MediaScannerConnection provides a way for applications to pass a
        newly created or downloaded media file to the media scanner service.
        The media scanner service will read metadata from the file and add
        the file to the media content provider.
        The MediaScannerConnectionClient provides an interface for the
        media scanner service to return the Uri for a newly scanned file
        to the client of the MediaScannerConnection class.*/

        /*scanFile is used to scan the file when the connection is established with MediaScanner.*/


// offer to share content
        MediaScannerConnection.scanFile(
            this@MainActivity,
            arrayOf(result),
            null
        ) { path, _ ->

            // Use the FileProvider to get a content URI
            val requestFile = File(path)
            //var file = File(path);
            var pathuri: Uri = Uri.fromFile(requestFile)
            val fileUri: Uri? = try {
                FileProvider.getUriForFile(
                    this@MainActivity,
                    AUTHORITY,
                    requestFile
                )
            } catch (e: IllegalArgumentException) {
                Log.e(
                    "File Selector",
                    "The selected file can't be shared: $requestFile"
                )
                null
            }

            /*Log.e("ExternalStorage", "Scanned " + path + ":");
            Log.e("ExternalStorage", "-> fileUri=" + fileUri);
            Log.e("ExternalStorage", "-> uri=" + pathuri);
            Log.e("Authority-Value", ": " + AUTHORITY.toString())*/


            val shareIntent = Intent()
            shareIntent.action = Intent.ACTION_SEND
            var file = File(pathuri.toString())
            shareIntent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            shareIntent.flags = Intent.FLAG_GRANT_WRITE_URI_PERMISSION

            val contentURI = getContentUri(this@MainActivity, file.absolutePath)
            // Log.e("contentURI", "-> contentUri=" + contentURI);
            shareIntent.type = "image/*"
            shareIntent.putExtra(Intent.EXTRA_STREAM, contentURI)
            startActivity(
                Intent.createChooser(
                    shareIntent, "Share"
                )
            )


        }


        // END
    }

    //20220923_SY_Changed_Removed_funMediaScannerConnections
    // New fun for shareImage
    private fun shareImage2(uri: Uri) {
        val intent = Intent().apply {
            this.action = Intent.ACTION_SEND
            this.putExtra(Intent.EXTRA_STREAM, uri)
            this.type = "image/*"
        }
        startActivity(Intent.createChooser(intent, "Share image via "))
    }


    /**
     * Method is used to show the Custom Progress Dialog.
     */
    private fun showProgressDialog() {
        customProgressDialog = Dialog(this@MainActivity)

        /*Set the screen content from a layout resource.
        The resource will be inflated, adding all top-level views to the screen.*/
        customProgressDialog?.setContentView(R.layout.dialog_custom_progress)

        //Start the dialog and display it on screen.
        customProgressDialog?.show()
    }

    //20220926_V2_1_Added_AboutMenu Screen-Dialog
    private fun showDialogAbout() {
        customAboutDialog = Dialog(this@MainActivity)
        customAboutDialog?.setContentView((R.layout.dialog_about))

        val btnOkAbout = customAboutDialog?.findViewById<Button>(R.id.btnOk_about)
        val tvVersionInfo =
            customAboutDialog?.findViewById<TextView>(R.id.tvAppVersioninfoTxt_About)
        val tvAppName = customAboutDialog?.findViewById<TextView>(R.id.tvAppNameTxt_About)


        customAboutDialog?.setTitle("App-Info")
        getVersionInfo()
        tvVersionInfo?.text = "${mVersionName} $mVersionCode"
        tvAppName?.text = mAppName

        btnOkAbout?.setOnClickListener(View.OnClickListener
        {
            customAboutDialog?.dismiss()
        })

        customAboutDialog?.show()
    }

    /**
     * This function is used to dismiss the progress dialog if it is visible to user.
     */
    private fun cancelProgressDialog() {
        if (customProgressDialog != null) {
            customProgressDialog?.dismiss()
            customProgressDialog = null
        }
    }

    // 20220923_SY_Changed_uri_MediaStore
    // Funktion getContentURI wandelt File-Path in contentURI um
    private fun getContentUri(context: Context, absPath: String): Uri? {
        val cursor = context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            arrayOf<String>(MediaStore.Images.Media._ID),
            MediaStore.Images.Media.DATA + "=? ",
            arrayOf<String>(absPath), null
        )
        if (cursor != null && cursor.moveToFirst()) {

            //// 20220923_SY_Changed_uri_MediaStore
            //wegen fehlermeldung 'must be >= 0' geändert von getColumnIndex zu getColumnIndexOrThrow
            // siehe: https://stackoverflow.com/questions/69053061/android-studio-value-must-be-%E2%89%A5-0
            val id = cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID))
            //Orig:
            // val id = cursor.getInt(cursor.getColumnIndex(MediaStore.MediaColumns._ID))

            return Uri.withAppendedPath(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                Integer.toString(id)
            )
        } else if (!absPath.isEmpty()) {
            val values = ContentValues()
            values.put(MediaStore.Images.Media.DATA, absPath)
            return context.contentResolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values
            )
        } else {
            return null
        }
    }

    //20220925_V2_Added_AboutMenu
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        var inflater: MenuInflater = menuInflater
        inflater.inflate(R.menu.about, menu)
        return true
    }

    //20220925_V2
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.m_about -> {
                //20220926_V2_1_Added_AboutMenu_Screen
                showDialogAbout()
                return true
            }
            R.id.m_share -> {
                img_save()


                return true
            }
            R.id.m_exit -> {
                finish()
                return true
            }
            else -> return super.onOptionsItemSelected(item)

        }

    }

    //20220925_V2_Added_AboutMenu Refactor Save
    private fun img_save() {
        //check if permission is allowed
        if (isReadStorageAllowed()) {
            showProgressDialog()
            //launch a coroutine block
            lifecycleScope.launch {
                //reference the frame layout
                val flDrawingView: FrameLayout = findViewById(R.id.fl_drawing_view_container)
                //Save the image to the device
                saveBitmapFile(getBitmapFromView(flDrawingView))
            }
        }
    }


    //20220926_V2_1_Added_AboutMenu_Screen_Dialog
    private fun getVersionInfo() {
        val context =
            applicationContext // or activity.getApplicationContext()

        val packageManager = context.packageManager
        val packageName = context.packageName


        try {
            mVersionName = packageManager.getPackageInfo(packageName, 0).versionName
            mAppName = applicationInfo.loadLabel(getPackageManager()).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
        }

    }
}