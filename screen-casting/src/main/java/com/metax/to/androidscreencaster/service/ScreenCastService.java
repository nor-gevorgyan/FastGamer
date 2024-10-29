package com.metax.to.androidscreencaster.service;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaCodec;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Surface;
import androidx.annotation.NonNull;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.mlkit.common.sdkinternal.MlKitContext;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import com.metax.to.androidscreencaster.consts.ActivityServiceMessage;
import com.metax.to.androidscreencaster.consts.ExtraIntent;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;
import androidx.palette.graphics.Palette;

import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.TermCriteria;

public final class ScreenCastService extends Service {

    public static final String GOOD = "com.fastGamer.Good";
    public static final String BAD = "com.fastGamer.BAD";
    private static int FPS = 5;
    private static int configWidth;
    private static int streamQuality;
    private static int configHeight;
    private static int screenDpi;
    private static String bitrate;
    private final String TAG = "MTX.Main.Livestream.ScreenCastService";
    private MediaProjectionManager mediaProjectionManager;
    private Handler handler;
    private Messenger crossProcessMessenger;
//    private LocalHandler backgroundTask;
    private MediaProjection mediaProjection;
    private MediaProjection mediaProjection2;
    private Surface inputSurface;
    private VirtualDisplay virtualDisplay;
    private MediaCodec.BufferInfo videoBufferInfo;
    private String videoSrc;
    private final String defSrcValue = "ScreenCast";
    private final MediaProjection mediaCallback = null;
    private ImageReader imageReader;
    private final int calc = 0 ;
    private long now;
    private long lastImageTime = 0L;
    private final int imageReaderMaxImages = 2;
    private Boolean liveStreamOnPause = false;
    private TextRecognizer textRecognizer;
    private int boxTop = 0;
    private int boxBottom = 0;
    private int boxLeft = 0;
    private int boxRight = 0;
    private int positionFoundIndex = 0;
    private boolean onScreenDetect = false;
    private long lastDetectTime;
    public static Handler mainHandler;
    public static int MESSAGE_COORDINATES = 888;
    public static int MESSAGE_HEADING = 889;
    private Messenger messengerWithMain;
    private long textDetectInterval = 2000;
    private boolean test = false ;


    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "onBind");
        handler = new Handler(new Handler.Callback() {
            @Override
            public boolean handleMessage(Message msg) {
                Log.i(TAG, "Handler got message. what:" + msg.what);
                switch(msg.what) {
                    case ActivityServiceMessage.CONNECTED:
                    case ActivityServiceMessage.DISCONNECTED:
                        break;
                    case ActivityServiceMessage.PAUSE:
                        Log.d(TAG, "Received Pause message in ScreenCast service");
                        pauseLiveStream();
                        break;
                    case ActivityServiceMessage.RESTART:
                        Log.d(TAG, "Received restart message in ScreenCast service");
                        restartLiveStream();
                        break;
                    case ActivityServiceMessage.STOP:
                        Log.i(TAG, "Received stop Message in ScreenCastService");
                        stopScreenCapture();
                        stopSelf();
                        break;
                }
                return false;
            }
        });
        crossProcessMessenger = new Messenger(handler);
        return crossProcessMessenger.getBinder();
    }

    MediaProjection.Callback callback = new MediaProjection.Callback() {
        @Override
        public void onStop() {
            // Handle MediaProjection being stopped
            Log.d(TAG, "MediaProjection onStop");
            stopScreenCapture();
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate");
        Log.d(TAG, "onCreate");
        mediaProjectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);

    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");
        if (textRecognizer != null ) {
            textRecognizer.close();
        }
//        stopBackgroundThread();
//        stopStreamConsumer();
        stopScreenCapture();
    }

//    private void startStreamConsumer(int width, int height, int rate , String bitrate,  String src) {
//        int dstPort = getResources().getInteger(R.integer.tcpport);
//        if (src.equals(defSrcValue)) {
//            streamConsumer = new StreamConsumer(this, dstPort, getMetaxUrl(this), rate, src, width, height, bitrate);
//            streamConsumer.resume();
//        } else {
//            Log.d(TAG, "onstart Stream COnsumer " + src );
//            streamConsumer = new StreamConsumer(this, dstPort, getMetaxUrl(this), rate, src, width, height, bitrate);
//            streamConsumer.startStreamWithSrc();
//        }
//
//    }

//    private void stopStreamConsumer() {
//        if (null != streamConsumer) {
//            Log.i(TAG, "Stop Stream Consumer");
//            try {
//                streamConsumer.pause();
//                streamConsumer = null;
//            } catch (Exception e) {
//                Log.e(TAG, Objects.requireNonNull(e.getMessage()));
//            }
//        }
//    }

//    private void stopBackgroundThread() {
//        if (backgroundTask != null) {
//            backgroundTask.pause();
//            backgroundTask = null;
//        }
//    }
//
    private void sendDisconnectedNotification() {
        Log.w(TAG, "Will be sending CLOSED message from ScreenCastService");
        Intent intent = new Intent();
//        intent.setAction(LS_CLOSED);
        this.sendBroadcast(intent);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i("NORGEVORGYAN", "any Log");
        MlKitContext.initializeIfNeeded(this);
        send_notification_for_service();
//        if (backgroundTask == null) {
//            Log.i(TAG, "Creating local background task");
//            backgroundTask = new LocalHandler("screen-caster");
//        }
//
//        backgroundTask.resume();
//        backgroundTask.getHandler().post(() -> {
//            if (intent == null) {
//                Log.w(TAG, "Intent is null on backgroundTAsk");
//            }
//            startCommand(intent, flags, startId);
//        });
        startCommand(intent, flags, startId);
        Log.i(TAG, "Scheduled Start command");
        return super.onStartCommand(intent, flags, startId);
    }

    public int startCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            Log.w(TAG, "Intent is null");
            return START_NOT_STICKY;
        }
        textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);

        configWidth = intent.getIntExtra(ExtraIntent.SCREEN_WIDTH.toString(), 640);
        configHeight = intent.getIntExtra(ExtraIntent.SCREEN_HEIGHT.toString(), 480);
        screenDpi = intent.getIntExtra(ExtraIntent.SCREEN_DPI.toString(), 96);
        bitrate = intent.getStringExtra(ExtraIntent.VIDEO_BITRATE.toString());
        FPS = intent.getIntExtra(ExtraIntent.VIDEO_FPS.toString(),15);
        streamQuality = intent.getIntExtra((ExtraIntent.STREAM_QUALITY.toString()),0);
        videoSrc = intent.getStringExtra(ExtraIntent.VIDEO_SRC.toString());
        messengerWithMain = intent.getParcelableExtra(ExtraIntent.MESSENGER.toString());

            // if ScreenCast
            final int resultCode = intent.getIntExtra(ExtraIntent.RESULT_CODE.toString(), -1);
            final Intent resultData = intent.getParcelableExtra(ExtraIntent.RESULT_DATA.toString());
            if (resultCode == 0 || resultData == null) { return  START_NOT_STICKY; }
            Log.i(TAG, "ScreenCast permission request resultCode: " + resultCode);
            startScreenCapture(resultCode, resultData, configWidth, configHeight, screenDpi, bitrate, streamQuality, FPS);
        Log.i(TAG,"started screenCapture with default source value ->" + defSrcValue);

        return START_STICKY;
    }

    // TODO: Need to investigate this function. and cleanup
    private void send_notification_for_service() {
        String CHANNELID = "MetaxScreenCast";
        NotificationChannel channel = null;
        channel = new NotificationChannel(
                CHANNELID,
                CHANNELID,
                NotificationManager.IMPORTANCE_LOW
        );
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
        Notification notification =
                null;
        notification = new Notification.Builder(this, CHANNELID)
                .setContentTitle("ScreenCast")
                .setContentText("StartedScreenCast")
                .setTicker("TickerScreenCast")
                .build();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                    999, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        } else {
            startForeground(999, notification);
        }
    }

    private void sendMessageToMainThread(String msg) {
        if (messengerWithMain == null) {
            Log.e(TAG, "messenger is null ");
            return;
        }
        Message message;
            message = Message.obtain(null, 777);
            Bundle bundle = new Bundle();
            bundle.putString("checked", msg);
            message.setData(bundle);
        try {
            messengerWithMain.send(message);
        } catch (Exception e) {
            Log.e(TAG, Objects.requireNonNull(e.getMessage()));
        }
    }

    private void initializeImageReader(
            int resultCode, Intent resultData, int width, int height, int dpi) {
        onScreenDetect = true;
        Log.i(TAG,"ImageReader will be initialized");
        this.mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, resultData);
        this.mediaProjection.registerCallback(callback, null);
        // From virtualDisplay got only frame with pixel format RGBA_8888
        // TODO: Need to investigate imageReaderMaxImages value impact on the stream
        if (onScreenDetect) {
            // FIXME: Need to implement without magic numbers
            width = 1280;
            height = 720;
        }
        imageReader = ImageReader.newInstance(
                width, height, PixelFormat.RGBA_8888, imageReaderMaxImages);
        VirtualDisplay display = mediaProjection.createVirtualDisplay("screenCapture",
                width, height, dpi,
                0, imageReader.getSurface(), null, handler);
        imageReader.setOnImageAvailableListener(new ImageAvailableListener(), null);
        Log.i(TAG, "start ImageReader");
    }

    private class ImageAvailableListener implements ImageReader.OnImageAvailableListener {
        @Override
        public void onImageAvailable(ImageReader reader) {
            now = System.currentTimeMillis();
            Image image;
            image = imageReader.acquireLatestImage();
            if (image != null & (now - lastImageTime >= 1000/ FPS)) {
                try {
                    //sendMessageToMainThread("latitude", "longitude");
                    int width = image.getWidth();
                    int height = image.getHeight();
                    ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                    image.close();
                    Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                    buffer.rewind(); // Rewind the buffer to the beginning
                    bitmap.copyPixelsFromBuffer(buffer);
                    // full size bitmap
                    if (onScreenDetect) {
                        if (now - lastDetectTime >= textDetectInterval) {
                            lastDetectTime = System.currentTimeMillis();
                            Runnable longRunningTask = () -> {
                                captureTextFromFrame(bitmap);
                            };
                            CompletableFuture.runAsync(longRunningTask);
                        }
//                        if (null != streamConsumer && !liveStreamOnPause) {
//                            Bitmap resizedBitmap = Bitmap.createScaledBitmap(bitmap, configWidth, configHeight, true);
//                            ByteBuffer resizedBuffer = ByteBuffer.allocate(resizedBitmap.getByteCount());
//                            resizedBitmap.copyPixelsToBuffer(resizedBuffer);
//                            byte[] rgbaBytes = resizedBuffer.array();
//                            resizedBitmap.recycle();
//                            streamConsumer.consumeBytes(rgbaBytes, rgbaBytes.length);
//                        }
                        Bitmap croppedBitmap =  cropBitmapWithBoundingBox(bitmap, 615,327,656, 345);
                        saveImage(getApplicationContext(), croppedBitmap, "FastGamerCropped.jpg");
                        analyzeExtendedColors(bitmap);
//                        bitmap.recycle();
//                        sendMessageToMainThread("GOOD");
                        return;
                    }
//                    if (null != streamConsumer && !liveStreamOnPause) {
//                        ByteBuffer resizedBuffer = ByteBuffer.allocate(bitmap.getByteCount());
//                        bitmap.copyPixelsToBuffer(resizedBuffer);
//                        byte[] rgbaBytes = resizedBuffer.array();
//                        // send byte[] to streamConsumer
//                        streamConsumer.consumeBytes(rgbaBytes, rgbaBytes.length);
//                    }
                } catch (OutOfMemoryError e) {
                    Log.e(TAG, Objects.requireNonNull(e.getMessage()));
                } finally {
                    lastImageTime = now;
                    image.close();
                }
            }
            if (image != null) {
                image.close();
            }
        }
    }

    private void saveImage(Context context, Bitmap bitmap, String filename) {
        if (test) return ;
        test = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Use MediaStore for Android 10 and above
            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME, filename);
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
            values.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/"); // Custom folder in Pictures

            // Insert into MediaStore and get the URI
            OutputStream outputStream;
            try {
                outputStream = context.getContentResolver().openOutputStream(
                        context.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                );
            } catch (FileNotFoundException e) {
                throw new RuntimeException(e);
            }
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream);
        }
    }

    public static void analyzeExtendedColors(Bitmap bitmap) {
        Palette.from(bitmap).generate(new Palette.PaletteAsyncListener() {
            @Override
            public void onGenerated(Palette palette) {
                if (palette == null) return;

                // Get image area to calculate color percentage
                double totalPixels = bitmap.getWidth() * bitmap.getHeight();

                // Dark Vibrant Swatch
                Palette.Swatch darkVibrantSwatch = palette.getDarkVibrantSwatch();
                if (darkVibrantSwatch != null) {
                    int color = darkVibrantSwatch.getRgb();
                    int population = darkVibrantSwatch.getPopulation();
                    double percentage = (population / totalPixels) * 100;
                    Log.i("ColorAnalyzer", "Dark Vibrant Color: #" + Integer.toHexString(color) + ", Percentage: " + percentage + "%");
                }

//                // Light Vibrant Swatch
//                Palette.Swatch lightVibrantSwatch = palette.getLightVibrantSwatch();
//                if (lightVibrantSwatch != null) {
//                    int color = lightVibrantSwatch.getRgb();
//                    int population = lightVibrantSwatch.getPopulation();
//                    double percentage = (population / totalPixels) * 100;
//                    Log.i("ColorAnalyzer", "Light Vibrant Color: #" + Integer.toHexString(color) + ", Percentage: " + percentage + "%");
//                }

                // Dark Muted Swatch
                Palette.Swatch darkMutedSwatch = palette.getDarkMutedSwatch();
                if (darkMutedSwatch != null) {
                    int color = darkMutedSwatch.getRgb();
                    int population = darkMutedSwatch.getPopulation();
                    double percentage = (population / totalPixels) * 100;
                    Log.i("ColorAnalyzer", "Dark Muted Color: #" + Integer.toHexString(color) + ", Percentage: " + percentage + "%");
                }

            }
        });
    }


    // This function get bitmap and detect text from frame,
    // for detected text called text handler functionality to get valid coordinates
    private  void captureTextFromFrame(Bitmap bitmap) {
        Log.i(TAG, "On detect text from frame");
        InputImage cImage;
        InputImage hImage;
        Bitmap mutableBitmap;
        // if the text position is found and actual,
        // so will be crop getting bitmap with detected text position
        if (positionFoundIndex > 0) {
            Log.i(TAG, "Will be Cropping frame");
            Bitmap cropBitmap = cropBitmapWithBoundingBox(bitmap, boxLeft, boxTop, boxRight, boxBottom);
            if (cropBitmap == null) {
                positionFoundIndex -= 1;
                return;
            }
            mutableBitmap = enlargeBitmap(cropBitmap, 2);
        } else {
            mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true);
        }
        // FIXME : This gen cropped bitmap to detect heading data, it is hardcoded implementation,
        //  and will be work only when bitmap frame size is 1920x1080 and will be using dji pilot 2 app
        //  Need to change this implementation
        //Bitmap headingBitmap = cropBitmapWithBoundingBox(bitmap, 900, 650, 1020, 820);
        // This block code get prepared bitmap frame (cImage) and launch AI detecting functionality
//        cImage = InputImage.fromBitmap(mutableBitmap, 0);
//        detect(cImage, false);
        // FIXME: I commented this block code because there are a bug related with this code,
        //  This code cropped getting captured image and detected heading value using AI,
        //  The bug related with bitmap processing.
//        if (headingBitmap != null) {
//            Bitmap headingEnlargeBitmap = enlargeBitmap(headingBitmap, 2);
//            headingBitmap.recycle();
//            hImage = InputImage.fromBitmap(headingEnlargeBitmap, 0);
//            // You can uncomment saveBitmap function if you need save and display cropped frame
//            //saveBitmap(getApplicationContext(), headingEnlargeBitmap, "cropImage.png");
//            detect(hImage, true);
//        }
    };

//    void detect(InputImage image, Boolean onCheckHeading) {
//        Task<Text> result = textRecognizer.process(image)
//                .addOnSuccessListener(new OnSuccessListener<Text>() {
//                    @Override
//                    public void onSuccess(Text visionText) {
//                        // If available text box to process
//                        if (!visionText.getText().isEmpty()) {
//                            processDetectedText(visionText, onCheckHeading);
//                        } else {
//                            Log.w(TAG, "empty text box");
//                            if (!onCheckHeading) {
//                                positionFoundIndex -= 1;
//                            }
//                        }
//                    }
//                })
//                .addOnFailureListener(
//                        new OnFailureListener() {
//                            @Override
//                            public void onFailure(@NonNull Exception e) {
//                                // Task failed with an exception
//                                positionFoundIndex -= 1;
//                                //mutableBitmap.recycle();
//                                // ...
//                            }
//                        });
//    }



    // This function get text box and split it separate lines,
    // and for every line called checkDetectedString() function to find valid coordinates
    // when found valid coordinates the function update positionFoundIndex to 8,
    // and update also variables for cropping next bitmap frames
//    void processDetectedText(Text detectedText, boolean onCheckHeading){
//        boolean coordinatesIsChecked = false;
//        for (Text.TextBlock block : detectedText.getTextBlocks()) {
//            for (Text.Line line: block.getLines()) {
//                if (onCheckHeading) {
//                    String[] validHeading = checkDetectedString(line.getText(), true);
//                    if (validHeading != null ) {
//                        Log.i(TAG, "Detected valid heading ::::::");
//                        sendMessageToMainThread(MESSAGE_HEADING, validHeading);
//                    }
//                    continue;
//                }
//                Rect lineBoundingBox = line.getBoundingBox();
//                String[] validCoordinates = checkDetectedString(line.getText(), false);
//                if (validCoordinates != null) {
//                    Log.i(TAG, "Detected valid coordinates ::::::");
//                sendMessageToMainThread(MESSAGE_COORDINATES, validCoordinates);
//                    if (positionFoundIndex > 0) {
//                        positionFoundIndex = 8;
//                        return;
//                    }
//                    if (lineBoundingBox == null ) {
//                        return;
//                    }
//                    // FIXME: Maybe need to get the value 10  from configs
//                    // +/- 10 to set padding for cropping frame
//                    boxLeft = lineBoundingBox.left - 10;
//                    boxTop = lineBoundingBox.top - 10;
//                    boxRight = lineBoundingBox.right + 10;
//                    boxBottom = lineBoundingBox.bottom + 10;
//                    positionFoundIndex = 8;
//                    coordinatesIsChecked = true;
//                    break;
//                }
//            }
//            if (coordinatesIsChecked && !onCheckHeading) {
//                break;
//            }
//        }
//        if (!coordinatesIsChecked && !onCheckHeading) {
//            positionFoundIndex -= 1;
//        }
//    }

    public static Bitmap enlargeBitmap(Bitmap originalBitmap, int enlargementFactor) {
        int originalWidth = originalBitmap.getWidth();
        int originalHeight = originalBitmap.getHeight();
        int newWidth = originalWidth * enlargementFactor;
        int newHeight = originalHeight * enlargementFactor;
        Bitmap enlargedBitmap = Bitmap.createBitmap(newWidth, newHeight, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(enlargedBitmap);
        Rect destinationRect = new Rect(0, 0, newWidth, newHeight);
        canvas.drawBitmap(originalBitmap, null, destinationRect, null);

        return enlargedBitmap;
    }

    public static boolean saveBitmap(Context context, Bitmap bitmap, String fileName) {
        try {
            // Create a file in the app's internal storage directory
            File file = new File(context.getFilesDir(), fileName);

            // Create a FileOutputStream to write the bitmap to the file
            FileOutputStream fos = new FileOutputStream(file);

            // Compress and save the bitmap to the file
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);

            // Close the FileOutputStream
            fos.close();

            return true; // Return true if saving is successful
        } catch (IOException e) {
            e.printStackTrace();
            return false; // Return false if saving fails
        }
    }

    public static Bitmap quantizeColors(Bitmap bitmap, int clusters) {
        Mat srcMat = new Mat();
        Utils.bitmapToMat(bitmap, srcMat);

        // Convert image to 3D points for K-means
        Mat samples = srcMat.reshape(1, srcMat.cols() * srcMat.rows());
        samples.convertTo(samples, CvType.CV_32F);

        // Apply K-means clustering
        Mat labels = new Mat();
        Mat centers = new Mat();
        Core.kmeans(samples, clusters, labels,
                new TermCriteria(TermCriteria.EPS + TermCriteria.MAX_ITER, 10, 1.0),
                1, Core.KMEANS_RANDOM_CENTERS, centers);

        // Map the centers to the original pixels
        centers.convertTo(centers, CvType.CV_8UC1);
        Mat quantized = new Mat(srcMat.size(), srcMat.type());
        int index = 0;
        for (int y = 0; y < srcMat.rows(); y++) {
            for (int x = 0; x < srcMat.cols(); x++) {
                int label = (int) labels.get(index++, 0)[0];
                double[] centerColor = centers.get(label, 0);
                quantized.put(y, x, centerColor);
            }
        }

        // Convert quantized Mat back to Bitmap
        Bitmap outputBitmap = Bitmap.createBitmap(quantized.cols(), quantized.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(quantized, outputBitmap);

        // Release resources
        srcMat.release();
        samples.release();
        labels.release();
        centers.release();
        quantized.release();

        return outputBitmap;
    }

    // This function get bitmap and crop parameters return cropped bitmap
    public static Bitmap cropBitmapWithBoundingBox(Bitmap bitmap, int left, int top, int right, int bottom) {
        if (bitmap == null || bitmap.isRecycled()) {
            // Log an error or handle the case where the bitmap is not ready
            Log.e("MTX.Main.Livestream.ScreenCastService", "CropBitmap incorrect bitmap ");
            return null;
        }
        int width = right - left;
        int height = bottom - top;
        Rect cropRect = new Rect(left, top, right, bottom);
        if (width > 0 && height > 0 ) {
            return Bitmap.createBitmap(bitmap, cropRect.left, cropRect.top, width, height);
        }
        return null;
    }

    private void startScreenCapture(int resultCode, Intent resultData, int width, int height, int dpi, String bitrate, int streamQuality, int FPS) {
        try {
            // Init streamConsumer. The streamConsumer inited on hear to get new stream
            //  configuration every start.
//            startStreamConsumer(width, height, FPS, bitrate, videoSrc);
            initializeImageReader(resultCode, resultData, width, height, dpi);
        } catch (Exception e){
            Log.e(TAG, Objects.requireNonNull(e.getMessage()));
        }
    }

    private void pauseLiveStream(){
        Log.d(TAG, "LiveStreamService on Pause");
//        if (streamConsumer != null) {
//            liveStreamOnPause = true;
//            streamConsumer.pause();
//        }
    }

    private void restartLiveStream(){
//        Log.d(TAG, "LiveStreamService restart");
//        liveStreamOnPause = false;
//        if (videoSrc.equals(defSrcValue)) {
//            streamConsumer.resume();
//        } else {
//            streamConsumer.startStreamWithSrc();
//        }

    }

//    void startEncoder(StreamEncoder.EncoderConfig config) {
//        try {
//            startStreamConsumer();
//            streamEncoder = new StreamEncoder(config);
//            if (streamConsumer != null) {
//                streamEncoder.setConsumer(streamConsumer);
//            }
//            streamEncoder.resume();
//        } catch (Exception e){
//            Log.e(TAG, Objects.requireNonNull(e.getMessage()));
//        }
//    }
    private void stopScreenCapture() {
        Log.i(TAG, "stop Screen Capture");
        releaseEncoders();
//        stopStreamConsumer();
        sendDisconnectedNotification();
        if (virtualDisplay == null) {
            return;
        }
        virtualDisplay.release();
        virtualDisplay = null;
        if (mediaProjection != null) {
            mediaProjection.stop();
            mediaProjection.unregisterCallback(callback);
            mediaProjection = null;
        }
    }

    private void releaseEncoders() {

//        if (streamEncoder != null) {
//            streamEncoder.pause();
//            streamEncoder = null;
//        }

        if (inputSurface != null) {
            inputSurface.release();
            inputSurface = null;
        }
        if (mediaProjection != null) {
            mediaProjection.stop();
            mediaProjection = null;
        }

        videoBufferInfo = null;
    }

}
