/*
       Licensed to the Apache Software Foundation (ASF) under one
       or more contributor license agreements.  See the NOTICE file
       distributed with this work for additional information
       regarding copyright ownership.  The ASF licenses this file
       to you under the Apache License, Version 2.0 (the
       "License"); you may not use this file except in compliance
       with the License.  You may obtain a copy of the License at

         http://www.apache.org/licenses/LICENSE-2.0

       Unless required by applicable law or agreed to in writing,
       software distributed under the License is distributed on an
       "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
       KIND, either express or implied.  See the License for the
       specific language governing permissions and limitations
       under the License.
 */
package org.apache.cordova.camera;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.Calendar;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;

/**
 * This class launches the camera view, allows the user to take a picture, closes the camera view,
 * and returns the captured image.  When the camera view is closed, the screen displayed before
 * the camera view was shown is redisplayed.
 */

/**
 * NOTE: THIS IS A HIGHLY MODIFIED VERSION OF THE ORIGINAL PLUGIN WITH LESS (BUT WORKING) FUNCTIONALITY
 * FOR OUR NEEDS. THIS NEEDS TO BE MOVED INTO OUR OWN PLUGIN AFTER RELEASE
 */
public class CameraLauncher extends CordovaPlugin {

  private int mQuality;                   // Compression quality hint (0-100: 0=low quality & high compression, 100=compress of max quality)
  private int targetWidth;                // desired width of the image
  private int targetHeight;               // desired height of the image
  private boolean allowEdit;              // Should we allow the user to crop the image.

  public CallbackContext callbackContext;

  private final static int FILECHOOSER_RESULTCODE = 1;
  private final static int IMAGECROP_RESULTCODE = 2;

  private Uri _fileUri;

  /**
   * Executes the request and returns PluginResult.
   *
   * @param action            The action to execute.
   * @param args              JSONArry of arguments for the plugin.
   * @param callbackContext   The callback id used when calling back into JavaScript.
   * @return                  A PluginResult object with a status and message.
   */
  public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
    this.callbackContext = callbackContext;

    if (action.equals("takePicture")) {
      this.targetHeight = 0;
      this.targetWidth = 0;
      this.mQuality = 80;

      this.targetWidth = args.getInt(3);
      this.targetHeight = args.getInt(4);
      this.allowEdit = args.getBoolean(7);

      // If the user specifies a 0 or smaller width/height
      // make it -1 so later comparisons succeed
      if (this.targetWidth < 1) {
        this.targetWidth = -1;
      }
      if (this.targetHeight < 1) {
        this.targetHeight = -1;
      }

      Intent galleryIntent = new Intent( Intent.ACTION_PICK, whichContentStore());
      galleryIntent.setType("image/*");

      Intent cameraIntent = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);

      // Try to fix issues with certain Android versions where the intent didn't return data
          ContentValues values = new ContentValues();
          values.put(MediaStore.Images.Media.TITLE, "IMG_" + Calendar.getInstance().getTimeInMillis() + ".jpg");
          Context context = this.cordova.getActivity().getApplicationContext();
          _fileUri = context.getContentResolver().insert(whichContentStore(), values); // store content values
          cameraIntent.putExtra( MediaStore.EXTRA_OUTPUT, _fileUri);

      Intent chooser = new Intent(Intent.ACTION_CHOOSER);
      chooser.putExtra(Intent.EXTRA_INTENT, galleryIntent);
      chooser.putExtra(Intent.EXTRA_TITLE, "Select Source");

      Intent[] intentArray = {cameraIntent};
      chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, intentArray);

      if (this.cordova != null) {
        this.cordova.startActivityForResult((CordovaPlugin) this, chooser, FILECHOOSER_RESULTCODE);
      }

      PluginResult r = new PluginResult(PluginResult.Status.NO_RESULT);
      r.setKeepCallback(true);
      callbackContext.sendPluginResult(r);

      return true;
    }
    return false;
  }

  public String getPath(Uri uri) {
    String[] projection = { MediaStore.Images.Media.DATA };
    @SuppressWarnings("deprecation")
    Cursor cursor = this.cordova.getActivity().managedQuery(uri, projection, null, null, null);
    int column_index = cursor
        .getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
    cursor.moveToFirst();
    return cursor.getString(column_index);
  }

  public static Uri getImageContentUri(Context context, File imageFile) {
    String filePath = imageFile.getAbsolutePath();
    Cursor cursor = context.getContentResolver().query(
        whichContentStore(),
        new String[] { MediaStore.Images.Media._ID },
        MediaStore.Images.Media.DATA + "=? ",
            new String[] { filePath }, null);
    if (cursor != null && cursor.moveToFirst()) {
      int id = cursor.getInt(cursor.getColumnIndex(MediaStore.MediaColumns._ID));
      Uri baseUri = Uri.parse("content://media/external/images/media");
      return Uri.withAppendedPath(baseUri, "" + id);
    } else {
      if (imageFile.exists()) {
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DATA, filePath);
        values.put(MediaStore.Images.Media.CONTENT_TYPE, "image/jpeg");
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
        return context.getContentResolver().insert(
            whichContentStore(), values);
      } else {
        return null;
      }
    }
  }

  public void cropCapturedImage(Uri picUri){
    Intent cropIntent = new Intent("com.android.camera.action.CROP");
    cropIntent.setDataAndType(picUri, "image/*");
    cropIntent.putExtra("crop", true);
    cropIntent.putExtra("scale", true);
    // indicate output X and Y
    if (targetWidth > 0) {
      cropIntent.putExtra("outputX", targetWidth);
    }
    if (targetHeight > 0) {
      cropIntent.putExtra("outputY", targetHeight);
    }
    if (targetHeight > 0 && targetWidth > 0 && targetWidth == targetHeight) {
      cropIntent.putExtra("aspectX", 1);
      cropIntent.putExtra("aspectY", 1);
    }
    cropIntent.putExtra("return-data", true);

    if (this.cordova != null) {
      this.cordova.startActivityForResult((CordovaPlugin) this, cropIntent, IMAGECROP_RESULTCODE);
    }
  }

  /**
   * Called when the camera view exits.
   *
   * @param requestCode       The request code originally supplied to startActivityForResult(),
   *                          allowing you to identify who this result came from.
   * @param resultCode        The integer result code returned by the child activity through its setResult().
   * @param intent            An Intent, which can return result data to the caller (various data can be attached to Intent "extras").
   */
  public void onActivityResult(int requestCode, int resultCode, Intent intent) {

    if (requestCode == FILECHOOSER_RESULTCODE) {
      if (resultCode != Activity.RESULT_OK) {
        return;
      }

      String fPath;
      try {
        Uri fileUri = intent.getData();
        if ( fileUri != null ) {
          fPath = getPath(fileUri);
        }
        else {
          fPath = getPath(_fileUri);
        }
      }
      catch ( Exception e ) {
        fPath = getPath(_fileUri);
      }

      // we actually don't allow HTTP links in gallery like Google+ or Facebook images
      File file = null;
      if ( fPath != null && fPath.length() > 0 ) {
        file = new File(fPath);
      }

      if ( file == null || !file.exists()) {
        this.failPicture( "Please select a local image file.");
        return;
      }

      if ( this.allowEdit ) {
        try {
          cropCapturedImage(Uri.fromFile(file));
        }
        catch(ActivityNotFoundException aNFE){
          Bitmap bitmap = BitmapFactory.decodeFile( fPath );
          this.processPicture(bitmap);
        }
      }
      else {
        Bitmap bitmap = BitmapFactory.decodeFile( fPath );
        this.processPicture(bitmap);
      }
    }
    else if( requestCode == IMAGECROP_RESULTCODE ) {
      if (intent != null && intent.getExtras() != null) {
        Bitmap bitmap = intent.getExtras().getParcelable("data");
        this.processPicture(bitmap);
      }
    }
  }

  /**
   * Determine if we are storing the images in internal or external storage
   * @return Uri
   */
  private static Uri whichContentStore() {
    if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
      return android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
    } else {
      return android.provider.MediaStore.Images.Media.INTERNAL_CONTENT_URI;
    }
  }

  /**
   * Compress bitmap using jpeg, convert to Base64 encoded string, and return to JavaScript.
   *
   * @param bitmap
   */
  public void processPicture(Bitmap bitmap) {
    ByteArrayOutputStream jpeg_data = new ByteArrayOutputStream();
    try {
      if (bitmap.compress(CompressFormat.JPEG, mQuality, jpeg_data)) {
        byte[] code = jpeg_data.toByteArray();
        byte[] output = Base64.encode(code, Base64.NO_WRAP);
        String js_out = new String(output);
        this.callbackContext.success(js_out);
        js_out = null;
        output = null;
        code = null;
      }
    } catch (Exception e) {
      this.failPicture("Error compressing image.");
    }
    jpeg_data = null;
  }

  /**
   * Send error message to JavaScript.
   *
   * @param err
   */
  public void failPicture(String err) {
    this.callbackContext.error(err);
  }
}
