package com.example.emojify;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import javax.annotation.ParametersAreNonnullByDefault;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_IMAGE_CAPTURE = 1;
    private static final int REQUEST_STORAGE_PERMISSION = 1;

    private static final String FILE_PROVIDER_AUTHORITY = "com.example.fileprovider";

    private ImageView mImageView;

    private Button mEmojifyButton;
    private FloatingActionButton mShareFab;
    private FloatingActionButton mSaveFab;
    private FloatingActionButton mClearFab;

    private TextView mTitleTextView;

    private String mTempPhotoPath;

    private Bitmap mResultsBitmap;

    private int mNumOfFaces;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Bind the views
        mImageView = findViewById(R.id.image_view);
        mEmojifyButton = findViewById(R.id.emojify_button);
        mShareFab = findViewById(R.id.share_button);
        mSaveFab = findViewById(R.id.save_button);
        mClearFab = findViewById(R.id.clear_button);
        mTitleTextView = findViewById(R.id.title_text_view);
    }

    /**
     * OnClick method for "Emojify Me!" Button. Launches the camera app.
     *
     * @param view The emojify me button.
     */
    public void emojifyMe(View view) {

        // Check for the external storage permission, runtime permission model
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {

            // If you do not have permission, request it
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    REQUEST_STORAGE_PERMISSION);
        } else {
            // Launch the camera if the permission exists
            launchCamera();
        }
    }

    // The callback triggered by user's response after you use requestPermissions()
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        // Called when you request permission to read and write to external storage
        switch (requestCode) {
            case REQUEST_STORAGE_PERMISSION: {
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // If you get permission, launch the camera
                    launchCamera();
                } else {
                    // If you do not get permission, show a Toast
                    Toast.makeText(this, R.string.permission_denied, Toast.LENGTH_SHORT).show();
                }
                break;

            }
        }
    }

    /**
     * Creates a temporary image file and captures a picture to store in it.
     */
    private void launchCamera() {

        // Create the capture image intent
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);

        // Ensure that there's a camera activity to handle the intent
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            // Create the temporary File where the photo should go
            File photoFile = null;
            try {
                //
                photoFile = BitmapUtils.createTempImageFile(this);
            } catch (IOException ex) {
                // Error occurred while creating the File
                ex.printStackTrace();
            }
            // Continue only if the File was successfully created
            if (photoFile != null) {

                // Get the path of the temporary file
                mTempPhotoPath = photoFile.getAbsolutePath();

                // Get the content URI for the image file
                Uri photoURI = FileProvider.getUriForFile(this,
                        FILE_PROVIDER_AUTHORITY,
                        photoFile);

                // Add the URI so the camera can store the image
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);

                // Launch the camera activity
                startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
            }
        }
    }

    // The callback of startActivityForResult(), triggered when user returned from camera app
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // If the image capture activity was called and was successful
        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == RESULT_OK) {

            // Process the image and set it to the ImageView
            processAndSetImage();

        } else {

            // Otherwise, delete the temporary image file
            BitmapUtils.deleteImageFile(mTempPhotoPath);
        }
    }

    /**
     * Method for processing the captured image and setting it to the TextView.
     */
    private void processAndSetImage() {

        // Toggle Visibility of the views
        mEmojifyButton.setVisibility(View.GONE);
        mTitleTextView.setVisibility(View.GONE);
        mSaveFab.show();
        mShareFab.show();
        mClearFab.show();

        // Resample the saved image to fit the ImageView
        mResultsBitmap = BitmapUtils.resamplePic(this, mTempPhotoPath);

        // Set the new bitmap to the ImageView
        mImageView.setImageBitmap(mResultsBitmap);

        //
        Toast.makeText(this, "Neural network is processing the image...", Toast.LENGTH_LONG).show();
        final ProgressBar spinner = findViewById(R.id.progressBar);
        spinner.setVisibility(View.VISIBLE);

        Callable<MyData> callable = new Emojifier(this, mResultsBitmap);
        ListeningExecutorService service = MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(10));
        ListenableFuture<MyData> future = service.submit(callable); // Another thread starts

        // callback triggerd when the other thread finished
        Futures.addCallback(
                future,
                new FutureCallback<MyData>() {
                    public void onSuccess(MyData myData) {
                        mNumOfFaces = myData.getI();
                        mResultsBitmap = myData.getBitmap();
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (mNumOfFaces == 0) {
                                    Toast.makeText(MainActivity.this, "0 face detected.", Toast.LENGTH_LONG).show();
                                    spinner.setVisibility(View.GONE);
                                } else {
                                    // Set the new bitmap to the ImageView
                                    mImageView.setImageBitmap(mResultsBitmap);
                                    Toast.makeText(MainActivity.this, "Emojified!", Toast.LENGTH_LONG).show();
                                    spinner.setVisibility(View.GONE);
                                }
                            }
                        });

                    }
                    @ParametersAreNonnullByDefault
                    public void onFailure( Throwable thrown) {
                        Log.i("XZ", "onFailure");
                    }
                },
                service);
    }


    /**
     * OnClick method for the save button.
     *
     * @param view The save button.
     */
    public void saveMe(View view) {
        // Delete the temporary image file
        BitmapUtils.deleteImageFile( mTempPhotoPath);

        // Save the image
        BitmapUtils.saveImage(this, mResultsBitmap);
    }

    /**
     * OnClick method for the share button, saves and shares the new bitmap.
     *
     * @param view The share button.
     */
    public void shareMe(View view) {
        // Delete the temporary image file
        BitmapUtils.deleteImageFile( mTempPhotoPath);

        // Save the image
        String path = BitmapUtils.saveImage(this, mResultsBitmap);

        // Share the image
        BitmapUtils.shareImage(this, path);
    }

    /**
     * OnClick for the clear button, resets the app to original state.
     *
     * @param view The clear button.
     */
    public void clearImage(View view) {
        // Clear the image and toggle the view visibility
        mImageView.setImageResource(0);
        mEmojifyButton.setVisibility(View.VISIBLE);
        mTitleTextView.setVisibility(View.VISIBLE);
        mShareFab.hide();
        mSaveFab.hide();
        mClearFab.hide();

        // Delete the temporary image file
        BitmapUtils.deleteImageFile( mTempPhotoPath);
    }
}
