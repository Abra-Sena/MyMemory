package com.emissa.mymemory

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.MediaStore
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.util.DisplayMetrics
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.emissa.mymemory.models.BoardSize
import com.emissa.mymemory.utils.*
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.analytics.ktx.logEvent
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.remoteconfig.ktx.remoteConfig
import com.google.firebase.storage.ktx.storage
import java.io.ByteArrayOutputStream

class CreateActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "CreateActivity"
        private const val MIN_GAME_NAME_LENGTH = 3
        private const val MAX_GAME_NAME_LENGTH = 14
        private const val PICK_PHOTO_CODE = 10
        private const val READ_EXTERNAL_PHOTO_CODE = 27
        private const val READ_PHOTOS_PERMISSION = android.Manifest.permission.READ_EXTERNAL_STORAGE
    }

    private lateinit var rvImagePicker: RecyclerView
    private lateinit var etGameName: EditText
    private lateinit var btnSave: Button
    private lateinit var pbUploading: ProgressBar
    private lateinit var adapter: ImagePickerAdapter
    private lateinit var boardSize: BoardSize
    private var numImagesRequired = -1
    private val chosenImageUris = mutableListOf<Uri>()
    private val firebaseAnalytics = Firebase.analytics
    private val remoteConfig = Firebase.remoteConfig
    private val storage = Firebase.storage
    private val db = Firebase.firestore


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create)

        rvImagePicker = findViewById(R.id.rvImagePicker)
        etGameName = findViewById(R.id.etGameName)
        btnSave = findViewById(R.id.btnSave)
        pbUploading = findViewById(R.id.pbUploading)

        // show go back to home icon
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        boardSize = intent.getSerializableExtra(EXTRA_BOARD_SIZE) as BoardSize
        numImagesRequired = boardSize.getNumPairs()
        // inform user about how many images to select base on the typ of game they want (ex: 4 images for Easy game)
        supportActionBar?.title = getString(R.string.choose_pics) + " (0 / $numImagesRequired)"

        btnSave.setOnClickListener{
            saveDataToFireBase()
        }

        // set max length for game name
        etGameName.filters = arrayOf(InputFilter.LengthFilter(MAX_GAME_NAME_LENGTH))
        etGameName.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                btnSave.isEnabled = shouldEnableSaveButton()
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) { }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { }

        })

        adapter = ImagePickerAdapter(this, chosenImageUris, boardSize, object: ImagePickerAdapter.ImageClickListener {
            override fun onPlaceholderClicked() {
                // check if user granted permission to the app to access photos/storage on their phone
                if (isPermissionGranted(this@CreateActivity, READ_PHOTOS_PERMISSION)) {
                    // launch the photos from anywhere(gallery, google photos, etc) on user's phone
                    launchIntentForPhotos()
                } else {
                    // request permission from user to access phone's storage
                    requestPermission(this@CreateActivity, READ_PHOTOS_PERMISSION, READ_EXTERNAL_PHOTO_CODE)
                }
            }

        })
        rvImagePicker.adapter = adapter
        rvImagePicker.setHasFixedSize(true)
        rvImagePicker.layoutManager = GridLayoutManager(this, boardSize.getWidth())
    }


    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if (requestCode == READ_EXTERNAL_PHOTO_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                launchIntentForPhotos()
            } else {
                // the user did not grant the app to access their photo/storage
                Toast.makeText(this, getString(R.string.phone_storage_access), Toast.LENGTH_LONG).show()
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    // handle user click on go back to home icon
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != PICK_PHOTO_CODE || resultCode != Activity.RESULT_OK || data == null) {
            Log.w(TAG, "Did not get data back from the launched activity, user likely canceled flow")
            return
        }

        // when user inputted valid data
        val selectedUri = data.data
        val clipData = data.clipData
        if (clipData != null) {
            Log.i(TAG, "ClipData numImages ${clipData.itemCount}: $clipData")
            for (i in 0 until clipData.itemCount) {
                val clipItem = clipData.getItemAt(i)
                if (chosenImageUris.size < numImagesRequired) {
                    chosenImageUris.add(clipItem.uri)
                }
            }
        } else if (selectedUri != null) {
            Log.i(TAG, "Data: $selectedUri")
            chosenImageUris.add(selectedUri)
        }

        adapter.notifyDataSetChanged()
        // update title of the activity to notify user on how many photos the picked so far
        supportActionBar?.title = getString(R.string.choose_pics) + " (${chosenImageUris.size} / $numImagesRequired)"
        // toggle active on save button after all conditions are met
        btnSave.isEnabled = shouldEnableSaveButton()
    }

    private fun saveDataToFireBase() {
        Log.i(TAG, "savedDataToFireBase")

        val customGameName = etGameName.text.toString()
        firebaseAnalytics.logEvent("creation_save_attempt") {
            param("game_name", customGameName)
        }
        //disable save button when user clicks on it
        btnSave.isEnabled = false

        // check that we're not over writing someone else's data
        db.collection("games").document(customGameName).get().addOnSuccessListener { document ->
            if (document != null && document.data != null) {
                AlertDialog.Builder(this)
                        .setTitle(getString(R.string.name_taken))
                        .setMessage(getString(R.string.name_taken_details) + " '$customGameName'. " + getString(R.string.name_taken_details_2))
                        .setPositiveButton("OK", null)
                        .show()
                // enable the save button to let user enter a new game name and save it
                btnSave.isEnabled = true
            } else {
                handleImageUploading(customGameName)
            }
        }.addOnFailureListener { exception ->
            Log.e(TAG, "Encountered error while saving memory game", exception)
            Toast.makeText(this, getString(R.string.saving_game_error), Toast.LENGTH_SHORT).show()
            // enable the save button to let user enter a new game name and save it
            btnSave.isEnabled = true
        }
    }

    private fun handleImageUploading(gameName: String) {
        // set progress bar visible once images start uploading
        pbUploading.visibility = View.VISIBLE

        val uploadedImageUrls = mutableListOf<String>()
        var didEncounterError = false

        for ((index, photoUri) in chosenImageUris.withIndex()) {
            // downgrade photo size and downscale image quality
            val imageByteArray = getImageByteArray(photoUri)
            val filePath = "images/$gameName/${System.currentTimeMillis()}-${index}.jpg"
            val photoReference = storage.reference.child(filePath)
            photoReference.putBytes(imageByteArray)
                    .continueWithTask { photoUploadTask ->
                        Log.i(TAG, "Uploaded bytes: ${photoUploadTask.result?.bytesTransferred}")
                        photoReference.downloadUrl
                    }.addOnCompleteListener { downloadUrlTask ->
                        if (!downloadUrlTask.isSuccessful) {
                            Log.e(TAG, "Exception with Firebase storage", downloadUrlTask.exception)
                            Toast.makeText(this, getString(R.string.upload_failed), Toast.LENGTH_SHORT).show()
                            didEncounterError = true
                            return@addOnCompleteListener
                        }
                        if (didEncounterError) {
                            // set progress bar visibility to gone if an error is encountered
                            pbUploading.visibility = View.GONE
                            return@addOnCompleteListener
                        }

                        // update the progress each time an image is uploaded
                        pbUploading.progress = uploadedImageUrls.size *100 / chosenImageUris.size
                        val downloadUrl =  downloadUrlTask.result.toString()
                        uploadedImageUrls.add(downloadUrl)
                        Log.i(TAG, "Finished uploading $photoUri, num uploaded ${uploadedImageUrls.size}")
                        if (uploadedImageUrls.size == chosenImageUris.size) {
                            handleAllImagesUploaded(gameName, uploadedImageUrls)
                        }
                    }
        }
    }

    private fun handleAllImagesUploaded(gameName: String, imageUrls: MutableList<String>) {
        // upload this info to Firestore
        db.collection("games")
                .document(gameName)
                .set(mapOf("images" to imageUrls))
                .addOnCompleteListener{gameCreationTask ->
                    // set progress bar visibility to gone once all images are uploaded to firebase
                    pbUploading.visibility = View.GONE
                    if (!gameCreationTask.isSuccessful) {
                        Log.e(TAG, "Exception with game creation", gameCreationTask.exception)
                        Toast.makeText(this, getString(R.string.game_creation_failed), Toast.LENGTH_SHORT).show()
                        return@addOnCompleteListener
                    }
                    firebaseAnalytics.logEvent("creation_save_success") {
                        param("game_name", gameName)
                    }
                    Log.i(TAG, "Successfully created game $gameName")
                    AlertDialog.Builder(this)
                            .setTitle(getString(R.string.game_upload_success) + " '$gameName'")
                            .setPositiveButton("OK") {_, _ ->
                                // pass back to main activity the game name once user clicks OK button
                                val resultData = Intent()
                                resultData.putExtra(EXTRA_GAME_NAME, gameName)
                                setResult(Activity.RESULT_OK, resultData)
                                finish()
                            }.show()
                }
    }

    private fun getImageByteArray(photoUri: Uri): ByteArray {
        // handle android version of user's phone
        val originalBitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(contentResolver, photoUri)
            ImageDecoder.decodeBitmap(source)
        } else {
            // android version older than Android Pi
            MediaStore.Images.Media.getBitmap(contentResolver, photoUri)
        }
        Log.i(TAG, "Original width ${originalBitmap.width} and height ${originalBitmap.height}")
        val scaledBitmap = BitmapScaler.scaleToFitHeight(originalBitmap, remoteConfig.getLong("scaled_height").toInt())
        Log.i(TAG, "Scaled width ${scaledBitmap.height} and height ${scaledBitmap.width}")
        val byteOutputStream = ByteArrayOutputStream()
        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, remoteConfig.getLong("compress_quality").toInt(), byteOutputStream)
        return byteOutputStream.toByteArray()
    }

    private fun shouldEnableSaveButton(): Boolean {
        // check if save button should be enabled
        if (chosenImageUris.size != numImagesRequired) {
            return false
        }
        if (etGameName.text.isBlank() || etGameName.text.length < MIN_GAME_NAME_LENGTH) {
            return false
        }
        return true
    }

    private fun launchIntentForPhotos() {
        // this is an implicit intent
        val intent = Intent(Intent.ACTION_PICK)
        // specify the type to only load images (no videos, or other files)
        intent.type = "image/*"
        //allow user to pick multiples photos at once
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        startActivityForResult(Intent.createChooser(intent, getString(R.string.choose_pics)), PICK_PHOTO_CODE)
    }
}