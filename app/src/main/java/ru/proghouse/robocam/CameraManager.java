package ru.proghouse.robocam;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.v4.app.ActivityCompat;
import android.util.Size;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.TextureView;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.List;

/**
 * Created by Alexey Valuev on 23.01.2016.
 */
public class CameraManager implements Camera.PreviewCallback {
    private static CameraManager cameraManager = new CameraManager();
    private String error = null;
    private HttpServer server = null;
    private Camera camera = null;
    private int cameraId = 0;
    private int storedPreviewSize = -1;
    private PreviewSize previewSize = null;
    //private RgbData rgbWriter = new RgbData();
    //private RgbData rgbReader = new RgbData();
    //private int rgbIndex = 0;
    private boolean previewing = false;
    private volatile int clientCount = 0;
    private int jpegQuality = 60;
    private OutputStream outputStream = null;
    private volatile int displayOrientation = -1;
    private volatile int displayRotation = -1;
    private volatile SurfaceHolder holder = null;
    private volatile boolean portrait_n_facing = false;

    //Camera2
    private volatile boolean afterGrandPermission = false;
    private android.hardware.camera2.CameraManager manager = null;
    private String camera2Id = "";
    private int sensorOrientation = 0;
    private int facing = 0;
    private PreviewSize[] previewSizes = null;
    private CameraDevice.StateCallback stateCallback = null;
    private CameraDevice cameraDevice = null;
    private CameraCaptureSession captureSession = null;
    private CaptureRequest.Builder previewRequestBuilder = null;
    private CaptureRequest previewRequest = null;
    private CameraCaptureSession.CaptureCallback captureCallback = null;
    private Handler backgroundHandler = null;
    private HandlerThread backgroundThread = null;
    private SurfaceTexture texture = null;

    CameraManager(){
    }

    public static CameraManager getCameraManager() {
        return cameraManager;
    }

    public boolean isInitialized() {
        synchronized(HttpServer.sync) {
            if (Build.VERSION.SDK_INT >= 21)
                return cameraDevice != null;
            else
                return camera != null;
        }
    }

    public PreviewSize getPreviewSize(){
        synchronized(HttpServer.sync) {
            return previewSize;
        }
    }

    public int getPreviewWidth() {
        synchronized(HttpServer.sync) {
            return previewSize.width;
        }
    }

    public int getPreviewHeight() {
        synchronized(HttpServer.sync) {
            return previewSize.height;
        }
    }

    public int getActualPreviewWidth() {
        synchronized (HttpServer.sync) {
            return isPreviewing() && getDisplayOrientation() != -1
                    ? (getDisplayOrientation() % 180 == 0
                    ? getPreviewWidth()
                    : getPreviewHeight())
                    : -1;
        }
    }

    public int getActualPreviewHeight() {
        synchronized (HttpServer.sync) {
            return isPreviewing() && getDisplayOrientation() != -1
                    ? (getDisplayOrientation() % 180 == 0
                    ? getPreviewHeight()
                    : getPreviewWidth())
                    : -1;
        }
    }

    public void addClient() {
        clientCount++;
    }

    public void removeClient() {
        clientCount--;
    }

    public void changeHolder(Activity activity, SurfaceHolder holder) {
        synchronized(HttpServer.sync) {
            releaseCamera(this.holder);
            initCamera(activity, holder);
        }
    }

    public void changeSurfaceTexture(Activity activity, SurfaceTexture texture) {
        synchronized(HttpServer.sync) {
            releaseCamera(this.texture);
            initCamera(activity, texture);
        }
    }

    public boolean getAfterGrandPermission() {
        return afterGrandPermission;
    }

    public void initCamera(Activity activity, boolean afterGrandPermission) {
        this.afterGrandPermission = afterGrandPermission;
        if ((!isInitialized()) && holder != null)
            initCamera(activity, holder);
    }

    private void createCameraCallback() {
        if (Build.VERSION.SDK_INT >= 21 && stateCallback == null) {
            stateCallback = new CameraDevice.StateCallback() {

                @Override
                public void onOpened(CameraDevice _cameraDevice) {
                    cameraDevice = _cameraDevice;
                    createCameraPreviewSession();
                }

                @Override
                public void onDisconnected(CameraDevice _cameraDevice) {
                    if (Build.VERSION.SDK_INT >= 21) {
                        _cameraDevice.close();
                        cameraDevice = null;
                    }
                }

                @Override
                public void onError(CameraDevice _cameraDevice, int i) {
                    if (Build.VERSION.SDK_INT >= 21) {
                        _cameraDevice.close();
                        cameraDevice = null;
                    }
                }
            };
        }

    }

    private void createCameraPreviewSession() {
        try {
            if (Build.VERSION.SDK_INT >= 21 && cameraDevice != null
                    && captureSession == null && !previewing) {
                texture.setDefaultBufferSize(previewSize.width, previewSize.height);
                Surface surface = new Surface(texture);
                previewRequestBuilder
                        = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                previewRequestBuilder.addTarget(surface);
                cameraDevice.createCaptureSession(
                        Arrays.asList(surface),
                        new CameraCaptureSession.StateCallback() {

                            @Override
                            public void onConfigured(CameraCaptureSession cameraCaptureSession) {
                                try {
                                    if (Build.VERSION.SDK_INT >= 21) {
                                        captureSession = cameraCaptureSession;
                                        previewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                                                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                                        previewRequest = previewRequestBuilder.build();
                                        captureSession.setRepeatingRequest(previewRequest,
                                                captureCallback, backgroundHandler);
                                    }
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }

                            @Override
                            public void onConfigureFailed(CameraCaptureSession cameraCaptureSession) {

                            }
                        }, null);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void createCaptureCallback() {
        if (Build.VERSION.SDK_INT >= 21 && captureCallback == null) {
            captureCallback = new CameraCaptureSession.CaptureCallback() {

            };
        }
    }

    private void startBackgroundThread() {
        if (backgroundThread == null) {
            backgroundThread = new HandlerThread("CameraBackground");
            backgroundThread.start();
            backgroundHandler = new Handler(backgroundThread.getLooper());
        }
    }

    private void stopBackgroundThread() {
        if (backgroundThread != null) {
            if (Build.VERSION.SDK_INT >= 18)
                backgroundThread.quitSafely();
            else
                backgroundThread.quit();
            try {
                backgroundThread.join();
                backgroundThread = null;
                backgroundHandler = null;
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public void initCamera(Activity activity, SurfaceTexture texture) {
        synchronized(HttpServer.sync) {
            if (Build.VERSION.SDK_INT >= 21) {
                this.texture = texture;
                SharedPreferences settings = activity.getSharedPreferences(ExtraKey.APP_PREFERENCE, Context.MODE_PRIVATE);
                storedPreviewSize = settings.getInt(ExtraKey.PREVIEW_SIZE, -1);
                jpegQuality = settings.getInt(ExtraKey.JPEG_QUALITY, 60);
                if (ActivityCompat.checkSelfPermission(activity, Manifest.permission.CAMERA)
                        != PackageManager.PERMISSION_GRANTED) {
                    if (ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.CAMERA)) {
                        new ConfirmationDialog().show(activity.getFragmentManager(), DefaultValue.FRAGMENT_DIALOG);
                    } else {
                        ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.CAMERA},
                                MainActivity.REQUEST_CAMERA_PERMISSION);
                    }
                } else {
                    manager = (android.hardware.camera2.CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
                    try {
                        camera2Id = settings.getString(ExtraKey.CAMERA2_ID, null);
                        if (camera2Id == null)
                            camera2Id = manager.getCameraIdList()[0];
                        CameraCharacteristics characteristics
                                = manager.getCameraCharacteristics(camera2Id);
                        sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
                        facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                        StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                        Size[] outputSizes = map.getOutputSizes(ImageFormat.JPEG);
                        previewSizes = new PreviewSize[outputSizes.length];
                        for (int i = 0; i < outputSizes.length; i++)
                            previewSizes[i] = new PreviewSize(outputSizes[i]);
                        previewSize = calculateNewPreviewSize();
                        createCameraCallback();
                        createCaptureCallback();
                        startBackgroundThread();
                        manager.openCamera(camera2Id, stateCallback, backgroundHandler);
                    } catch (Exception e) {
                        error = e.getMessage();
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    public void initCamera(Activity activity, SurfaceHolder holder){
        synchronized(HttpServer.sync) {
            if (Build.VERSION.SDK_INT < 21) {
                this.holder = holder;
                SharedPreferences settings = activity.getSharedPreferences(ExtraKey.APP_PREFERENCE, Context.MODE_PRIVATE);
                storedPreviewSize = settings.getInt(ExtraKey.PREVIEW_SIZE, -1);
                jpegQuality = settings.getInt(ExtraKey.JPEG_QUALITY, 60);
                if (Build.VERSION.SDK_INT >= 9) {
                    cameraId = settings.getInt(ExtraKey.CAMERA_ID, 0);
                    camera = Camera.open(cameraId);
                    jpegQuality = settings.getInt(ExtraKey.JPEG_QUALITY, 60);
                } else if (cameraId == 0)
                    camera = Camera.open();
                else {
                    error = server.getString(R.string.error_only_back_facing_camera_supported);
                    return;
                }
                if (camera != null) {
                    Camera.Parameters parameters = camera.getParameters();
                    List<Camera.Size> previewSizes = parameters.getSupportedPreviewSizes();
                    this.previewSizes = new PreviewSize[previewSizes.size()];
                    for (int i = 0; i < previewSizes.size(); i++)
                        this.previewSizes[i] = new PreviewSize(previewSizes.get(i));
                    try {
                        camera.setPreviewDisplay(holder);
                    } catch (IOException e) {
                        error = e.getMessage();
                        e.printStackTrace();
                        camera.release();
                        camera = null;
                    }
                    startPreview();
                }
            }
        }
    }

    public void releaseCamera(SurfaceTexture texture){
        synchronized(HttpServer.sync) {
            if (Build.VERSION.SDK_INT >= 21) {
                if (cameraDevice != null && this.texture != null && texture == this.texture) {
                    afterGrandPermission = false;
                    if (captureSession != null) {
                        captureSession.close();
                        captureSession = null;
                    }
                    if (cameraDevice != null) {
                        cameraDevice.close();
                        cameraDevice = null;
                    }
                    stopBackgroundThread();
                    this.texture = null;
                }
            }
        }
    }

    public void releaseCamera(SurfaceHolder holder){
        synchronized(HttpServer.sync) {
            if (camera != null && this.holder != null && holder == this.holder) {
                stopPreview();
                camera.release();
                camera = null;
                this.holder = null;
            }
        }
    }

    public boolean isPreviewing(){
        return previewing;
    }

    public int getDisplayOrientation(){
        return displayOrientation;
    }

    public void startPreview(){
        synchronized(HttpServer.sync) {
            if (camera != null) {
                camera.setPreviewCallback(this);
                camera.startPreview();
                previewing = true;
            }
        }
    }

    public void stopPreview(){
        synchronized(HttpServer.sync) {
            if (camera != null) {
                previewing = false;
                camera.stopPreview();
                camera.setPreviewCallback(null);
            }
        }
    }

    public boolean checkIfDisplayRotationIsChanged(Activity activity) {
        int curRotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        return displayRotation != curRotation;
    }

    public boolean updateOrientation(Activity activity){
        synchronized(HttpServer.sync) {
            portrait_n_facing = false;
            int oldDisplayOrientation = displayOrientation;
            if ((camera != null && !previewing) || cameraDevice != null) {
                int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
                int degrees = 0;
                switch (rotation) {
                    case Surface.ROTATION_0:
                        degrees = 0;
                        break;
                    case Surface.ROTATION_90:
                        degrees = 90;
                        break;
                    case Surface.ROTATION_180:
                        degrees = 180;
                        break;
                    case Surface.ROTATION_270:
                        degrees = 270;
                        break;
                }
                int result = 0;
                if (Build.VERSION.SDK_INT >= 21) {
                    if (facing == CameraCharacteristics.LENS_FACING_FRONT) {
                        result = (360 - degrees) % 360;
                    }
                    else if (facing == CameraCharacteristics.LENS_FACING_BACK) {
                        result = degrees;
                    }
                } else if (Build.VERSION.SDK_INT >= 9) {
                    android.hardware.Camera.CameraInfo info =
                            new android.hardware.Camera.CameraInfo();
                    android.hardware.Camera.getCameraInfo(cameraId, info);
                    if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                        result = (info.orientation + degrees) % 360;
                        result = (360 - result) % 360;  // compensate the mirror
                        portrait_n_facing = rotation == Surface.ROTATION_0;
                    } else {  // back-facing
                        result = (info.orientation - degrees + 360) % 360;
                    }
                } else if (activity.getResources().getConfiguration().orientation != Configuration.ORIENTATION_LANDSCAPE)
                    result = 90;
                else
                    result = degrees - 90;
                if (camera != null)
                    camera.setDisplayOrientation(result);
                displayOrientation = result;
                displayRotation = rotation;
            }
            return oldDisplayOrientation != displayOrientation;
        }
    }

    private PreviewSize calculateNewPreviewSize() {
        if (previewSizes == null || previewSizes.length == 0)
            return null;
        if (storedPreviewSize < 0) {
            PreviewSize first = previewSizes[0];
            PreviewSize last = previewSizes[previewSizes.length - 1];
            int third = Math.round((float) previewSizes.length / (float) 3.0);
            if (first.width > last.width || first.height > last.height)
                storedPreviewSize = previewSizes.length - third;
            else
                storedPreviewSize = third - 1;
        }
        if (storedPreviewSize < 0)
            storedPreviewSize = 0;
        if (storedPreviewSize >= previewSizes.length)
            storedPreviewSize = previewSizes.length - 1;
        return previewSizes[storedPreviewSize];
    }

    public boolean checkIfParametersIsChanged() {
        int oldWidth = previewSize == null ? 0 : previewSize.width;
        int oldHeight = previewSize == null ? 0 : previewSize.height;
        PreviewSize newPreviewSize = calculateNewPreviewSize();
        int newWidth = newPreviewSize == null ? 0 : newPreviewSize.width;
        int newHeight = newPreviewSize == null ? 0 : newPreviewSize.height;
        return oldWidth != newWidth || oldHeight != newHeight;
    }

    public void configureTransform(Activity activity, TextureView textureView,
                                   int viewWidth, int viewHeight) {
        if (Build.VERSION.SDK_INT >= 21) {
            updateOrientation(activity);
            Matrix matrix = new Matrix();
            RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
            float centerX = viewRect.centerX();
            float centerY = viewRect.centerY();

            if (displayOrientation == 90 || displayOrientation == 270) {
                RectF bufferRect = new RectF(0, 0, previewSize.height, previewSize.width);
                bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
                matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
                float scale = Math.max(
                        (float) viewHeight / previewSize.height,
                        (float) viewWidth / previewSize.width);
                matrix.postScale(scale, scale, centerX, centerY);
                matrix.postRotate(displayOrientation, centerX, centerY);
            }
            else
                matrix.postRotate(displayOrientation, centerX, centerY);

            /*
            if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
                bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
                matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.CENTER);
                float scale = Math.max(
                        (float) viewHeight / previewSize.height,
                        (float) viewWidth / previewSize.width);
                matrix.postScale(scale, scale, centerX, centerY);
                matrix.postRotate(90 * (rotation - 2), centerX, centerY);
            } else if (Surface.ROTATION_180 == rotation) {
                matrix.postRotate(180, centerX, centerY);
            }*/
            textureView.setTransform(matrix);
        }
    }

    public boolean updateParameters(){
        boolean result = false;
        synchronized(HttpServer.sync) {
            if (Build.VERSION.SDK_INT >= 21) {
                if (cameraDevice == null)
                    return result;
            } else {
                if (camera == null || previewing)
                    return result;
            }
            int oldWidth = previewSize == null ? 0 : previewSize.width;
            int oldHeight = previewSize == null ? 0 : previewSize.height;
            previewSize = calculateNewPreviewSize();
            //int newWidth = previewSize == null ? 0 : previewSize.width;
            //int newHeight = previewSize == null ? 0 : previewSize.height;
            for (RgbData data : rgb)
                data.Restore();
            //rgbReader.Restore();
            //rgbWriter.Restore();
            if (Build.VERSION.SDK_INT < 21) {
                Camera.Parameters parameters = camera.getParameters();
                parameters.setPreviewSize(previewSize.width, previewSize.height);
                parameters.setJpegQuality(100);
                camera.setParameters(parameters);
            }
            result = oldWidth != previewSize.width || oldHeight != previewSize.height;
            /*if (camera != null && !previewing) {
                rgbReader.Restore();
                rgbWriter.Restore();
                Camera.Parameters parameters = camera.getParameters();
                //List<Integer> formats = parameters.getSupportedPreviewFormats();
                //if (formats.contains(ImageFormat.YV12))
                //    parameters.setPreviewFormat(ImageFormat.YV12);
                List<Camera.Size> previewSizes = parameters.getSupportedPreviewSizes();
                if (storedPreviewSize < 0) {
                    //We have to get not so large image.
                    Camera.Size first = previewSizes.get(0);
                    Camera.Size last = previewSizes.get(previewSizes.size() - 1);
                    int third = Math.round((float)previewSizes.size() / (float)3.0);
                    if (first.width > last.width || first.height > last.height)
                        storedPreviewSize = previewSizes.size() - third;
                    else
                        storedPreviewSize = third - 1;
                }
                if (storedPreviewSize < 0)
                    storedPreviewSize = 0;
                if (storedPreviewSize >= previewSizes.size())
                    storedPreviewSize = previewSizes.size() - 1;
                previewSize = new PreviewSize(previewSizes.get(storedPreviewSize));
                parameters.setPreviewSize(previewSize.width, previewSize.height);
                parameters.setJpegQuality(100);
                camera.setParameters(parameters);
                result = oldWidth != previewSize.width || oldHeight != previewSize.height;
            }*/
        }
        return result; //true if updated
    }

    /*private void tryToChangeRgb(){
        if (rgbReader.ready && rgbWriter.ready){
            RgbData tmp = rgbReader;
            rgbReader = rgbWriter;
            rgbWriter = tmp;
            rgbReader.ready = false;
            rgbWriter.ready = false;
        }
    }*/

    /*public boolean writeJpg0(OutputStream outputStream, String boundary) throws IOException {
        boolean canGo;
        synchronized(HttpServer.sync) {
            if (!rgbReader.initialized) {
                rgbReader.ready = true;
                tryToChangeRgb();
                return false;
            }
            canGo = (clientCount > 0) && rgbReader.initialized && !rgbReader.ready;
        }
        if (canGo){
            YuvImage yuvImage = new YuvImage(rgbReader.yuv420, ImageFormat.NV21, rgbReader.width, rgbReader.height, null);
            synchronized(HttpServer.sync) {
                rgbReader.ready = true;
                tryToChangeRgb();
            }
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            yuvImage.compressToJpeg(new Rect(0, 0, rgbReader.width, rgbReader.height),
                    displayOrientation == 0 ? jpegQuality : 100, baos);
            byte[] imageBytes = baos.toByteArray();
            if (displayOrientation != 0) {
                Bitmap bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
                Matrix matrix = new Matrix();
                if (portrait_n_facing)
                    matrix.postRotate(displayOrientation + 180);
                else
                    matrix.postRotate(displayOrientation);
                bitmap = Bitmap.createBitmap(bitmap, 0, 0, rgbReader.width, rgbReader.height, matrix, true);
                baos.reset();
                bitmap.compress(Bitmap.CompressFormat.JPEG, jpegQuality, baos);
                imageBytes = baos.toByteArray();
            }
            outputStream.write(("Content-type: image/jpeg\r\n"
                    + "Content-Length: " + imageBytes.length + "\r\n\r\n").getBytes());
            outputStream.write(imageBytes);
            outputStream.write(("\r\n").getBytes());
            outputStream.flush();
            return true;
        }
        return false;
    }*/

    //@Override
    /*public void onPreviewFrame0(byte[] data, Camera camera) {
        boolean canGo;
        synchronized(HttpServer.sync) {
            canGo = (clientCount > 0) && data != null && !rgbWriter.ready;
        }
        if (canGo){
            if (!rgbWriter.initialized){
                rgbWriter.width = camera.getParameters().getPreviewSize().width;
                rgbWriter.height = camera.getParameters().getPreviewSize().height;
                //rgbWriter.buf = new int[rgbWriter.width * rgbWriter.height];
                rgbWriter.initialized = true;
            }
            try {
                if (rgbWriter.yuv420 == null || rgbWriter.yuv420.length != data.length)
                    rgbWriter.yuv420 = data.clone();
                else
                    System.arraycopy(data, 0, rgbWriter.yuv420, 0, data.length);
                synchronized(HttpServer.sync) {
                    if (rgbReader.ready) {//While the reader lags behind, we keep writing at all times.
                        rgbWriter.ready = true;
                        tryToChangeRgb();
                    }
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }*/

    private int id = Integer.MIN_VALUE;
    private RgbData[] rgb = new RgbData[] {new RgbData(), new RgbData(), new RgbData()};
    private int writeIndex = 1, oldWriteIndex = 0, readIndex = 0;
    private Object lock = new Object();
    private int readerCount = 0;

    @Override
    public void onPreviewFrame(byte[] data, Camera camera) {
        if (!previewing)
            return;
        id++;
        if (id == Integer.MAX_VALUE)
            id = Integer.MIN_VALUE;
        if (id == 0)
            id++;
        if (!rgb[writeIndex].initialized){
            rgb[writeIndex].width = camera.getParameters().getPreviewSize().width;
            rgb[writeIndex].height = camera.getParameters().getPreviewSize().height;
            rgb[writeIndex].initialized = true;
        }
        if (rgb[writeIndex].yuv420 == null || rgb[writeIndex].yuv420.length != data.length)
            rgb[writeIndex].yuv420 = data.clone();
        else
            System.arraycopy(data, 0, rgb[writeIndex].yuv420, 0, data.length);
        rgb[writeIndex].id = id;
        synchronized (lock) {
            oldWriteIndex = writeIndex;
            writeIndex++;
            if (writeIndex >= rgb.length)
                writeIndex = 0;
            if (writeIndex == readIndex) {
                writeIndex++;
                if (writeIndex >= rgb.length)
                    writeIndex = 0;
            }
        }
    }

    public int writeJpg(OutputStream outputStream, String boundary, int excludedId) throws IOException {
        int readId = 0;
        synchronized (lock) {
            if (rgb[readIndex].id == 0 && rgb[oldWriteIndex].id == 0)
                return 0;
            if (rgb[readIndex].id == 0)
                readIndex = oldWriteIndex;
            if (readerCount > 0 && rgb[readIndex].id == excludedId)
                return rgb[readIndex].id;
            if (readerCount == 0)
                readIndex = oldWriteIndex;
            readerCount++;
        }
        try {
            YuvImage yuvImage = new YuvImage(rgb[readIndex].yuv420, ImageFormat.NV21,
                    rgb[readIndex].width, rgb[readIndex].height, null);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            yuvImage.compressToJpeg(new Rect(0, 0, rgb[readIndex].width, rgb[readIndex].height),
                    displayOrientation == 0 ? jpegQuality : 100, baos);
            byte[] imageBytes = baos.toByteArray();
            if (displayOrientation != 0) {
                Bitmap bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
                Matrix matrix = new Matrix();
                if (portrait_n_facing)
                    matrix.postRotate(displayOrientation + 180);
                else
                    matrix.postRotate(displayOrientation);
                bitmap = Bitmap.createBitmap(bitmap, 0, 0,
                        rgb[readIndex].width, rgb[readIndex].height, matrix, true);
                baos.reset();
                bitmap.compress(Bitmap.CompressFormat.JPEG, jpegQuality, baos);
                imageBytes = baos.toByteArray();
            }
            outputStream.write(("Content-type: image/jpeg\r\n"
                    + "Content-Length: " + imageBytes.length + "\r\n\r\n").getBytes());
            outputStream.write(imageBytes);
            outputStream.write(("\r\n").getBytes());
            outputStream.flush();
        } finally {
            synchronized (lock) {
                readId = rgb[readIndex].id;
                readerCount--;
            }
        }
        return readId;
    }

    /*private void decodeYUV420(int[] rgb, byte[] yuv420, int width, int height) {
        final int frameSize = width * height;
        int uvp, u, v, i, y, r, g, b, y1192;
        for (int j = 0, yp = 0; j < height; j++) {
            uvp = frameSize + (j >> 1) * width;
            u = 0;
            v = 0;
            for (i = 0; i < width; i++, yp++) {
                y = (0xff & ((int) yuv420[yp])) - 16;
                if (y < 0) y = 0;
                if ((i & 1) == 0) {
                    v = (0xff & yuv420[uvp++]) - 128;
                    u = (0xff & yuv420[uvp++]) - 128;
                }

                y1192 = 1192 * y;
                r = (y1192 + 1634 * v);
                g = (y1192 - 833 * v - 400 * u);
                b = (y1192 + 2066 * u);

                if (r < 0) r = 0;
                else if (r > 262143) r = 262143;
                if (g < 0) g = 0;
                else if (g > 262143) g = 262143;
                if (b < 0) b = 0;
                else if (b > 262143) b = 262143;

                rgb[yp] = 0xff000000 | ((r << 6) & 0xff0000) | ((g >> 2) & 0xff00) | ((b >> 10) & 0xff);
            }
        }
    }*/

    /*public void setCameraId(int cameraId) {
        synchronized(HttpServer.sync) {
            if (camera != null && !previewing)
                this.cameraId = cameraId;
        }
    }

    public int getCameraId() {
        synchronized(HttpServer.sync) {
            return cameraId;
        }
    }*/

    /*public void setJpegQuality(int jpegQuality){
        synchronized(HttpServer.sync) {
            this.jpegQuality = jpegQuality;
        }
    }

    public int getJpegQuality() {
        synchronized(HttpServer.sync) {
            return jpegQuality;
        }
    }

    public void setOutputStream(OutputStream outputStream) {
        synchronized(HttpServer.sync) {
            this.outputStream = outputStream;
        }
    }*/

}
