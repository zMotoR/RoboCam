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
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.v4.app.ActivityCompat;
import android.util.DisplayMetrics;
import android.util.Size;
import android.view.Display;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.TextureView;
import android.view.View;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;

/**
 * Created by Alexey Valuev on 23.01.2016.
 */
public class CameraManager implements Camera.PreviewCallback {
    //Using Camera2 since this SDK
    public final static int CAMERA2_SDK = 23;
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
    //private OutputStream outputStream = null;
    private volatile int displayOrientation = -1;
    private volatile int displayRotation = -1;
    private volatile SurfaceHolder holder = null;
    private volatile boolean portrait_n_facing = false;
    private volatile boolean landscape_n_facing = false;

    //Camera2
    int oldWidth = -1;
    int oldHeight = -1;
    private volatile boolean afterGrandPermission = false;
    private android.hardware.camera2.CameraManager manager = null;
    private String camera2Id = "";
    private int sensorOrientation = 0;
    private int facing = 0;
    private PreviewSize[] surfacePreviewSizes = null;
    private List<PreviewSize> previewSizes = new ArrayList<PreviewSize>();
    private CameraDevice.StateCallback stateCallback = null;
    private CameraDevice cameraDevice = null;
    private CameraCaptureSession previewSession = null;
    //private CameraCaptureSession streamSession = null;
    private CaptureRequest.Builder previewRequestBuilder = null;
    //private CaptureRequest.Builder streamRequestBuilder = null;
    private CaptureRequest previewRequest = null;
    //private CaptureRequest streamRequest = null;
    private CameraCaptureSession.CaptureCallback previewCallback = null;
    //private CameraCaptureSession.CaptureCallback streamCallback = null;
    private Handler previewBackgroundHandler = null;
    //private Handler streamBackgroundHandler = null;
    private HandlerThread previewBackgroundThread = null;
    //private HandlerThread streamBackgroundThread = null;
    private SurfaceTexture texture = null;
    //private DisplayMetrics displayMetrics = null;
    private ImageReader imageReader = null;
    private ImageReader.OnImageAvailableListener imageAvailableListener = null;
    //private RenderScript renderScript = null;
    //private Allocation inputAllocation = null;
    //private Allocation outputAllocation = null;
    private volatile boolean useRenderScript = true;
    private ImageHelper rsHelper = null;

    CameraManager(){
    }

    public static CameraManager getCameraManager() {
        return cameraManager;
    }

    public boolean isInitialized() {
        synchronized(HttpServer.sync) {
            if (Build.VERSION.SDK_INT >= CAMERA2_SDK)
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
            if (Build.VERSION.SDK_INT >= CAMERA2_SDK)
                return displayRotation == Surface.ROTATION_0
                        || displayRotation == Surface.ROTATION_180
                        ? getPreviewHeight() : getPreviewWidth();
            else
                return isPreviewing() && getDisplayOrientation() != -1
                        ? (getDisplayOrientation() % 180 == 0
                        ? getPreviewWidth()
                        : getPreviewHeight())
                        : -1;
        }
    }

    public int getActualPreviewHeight() {
        synchronized (HttpServer.sync) {
            if (Build.VERSION.SDK_INT >= CAMERA2_SDK)
                return displayRotation == Surface.ROTATION_0
                        || displayRotation == Surface.ROTATION_180
                        ? getPreviewWidth() : getPreviewHeight();
            else
                return isPreviewing() && getDisplayOrientation() != -1
                        ? (getDisplayOrientation() % 180 == 0
                        ? getPreviewHeight()
                        : getPreviewWidth())
                        : -1;
        }
    }

    public void addClient() {
        if (clientCount == 0) {
            readerCount = 0;
            for (RgbData data : rgb)
                data.Restore();
        }
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
        if ((!isInitialized()) && texture != null)
            initCamera(activity, texture);
    }

    private void createCameraCallback() {
        if (Build.VERSION.SDK_INT >= CAMERA2_SDK && stateCallback == null) {
            stateCallback = new CameraDevice.StateCallback() {

                @Override
                public void onOpened(CameraDevice _cameraDevice) {
                    cameraDevice = _cameraDevice;
                    createCameraPreviewSession();
                    //createCameraStreamSession();
                }

                @Override
                public void onDisconnected(CameraDevice _cameraDevice) {
                    if (Build.VERSION.SDK_INT >= CAMERA2_SDK) {
                        _cameraDevice.close();
                        cameraDevice = null;
                    }
                }

                @Override
                public void onError(CameraDevice _cameraDevice, int i) {
                    if (Build.VERSION.SDK_INT >= CAMERA2_SDK) {
                        _cameraDevice.close();
                        cameraDevice = null;
                    }
                }
            };
        }

    }

    private PreviewSize chooseOptimalSize() {
        List<PreviewSize> bigEnough = new ArrayList<>();
        List<PreviewSize> notBigEnough = new ArrayList<>();
        int w = previewSize.width;
        int h = previewSize.height;
        for (PreviewSize option : surfacePreviewSizes) {
            if (option.height == option.width * h / w) {
                if (option.width >= w && option.height >= h)
                    bigEnough.add(option);
                else
                    notBigEnough.add(option);
            }
        }
        if (bigEnough.size() > 0)
            return Collections.min(bigEnough, new CompareSizesByArea());
        else if (notBigEnough.size() > 0)
            return Collections.max(notBigEnough, new CompareSizesByArea());
        else
            return previewSize;
    }

    /*private void createCameraStreamSession() {
        try {
            if (Build.VERSION.SDK_INT >= CAMERA2_SDK && cameraDevice != null
                    && streamSession == null && !previewing) {
                Surface surface = imageReader.getSurface();
                streamRequestBuilder
                        = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
                streamRequestBuilder.addTarget(surface);
                cameraDevice.createCaptureSession(
                        Arrays.asList(surface),
                        new CameraCaptureSession.StateCallback() {

                            @Override
                            public void onConfigured(CameraCaptureSession cameraCaptureSession) {
                                try {
                                    if (Build.VERSION.SDK_INT >= CAMERA2_SDK) {
                                        streamSession = cameraCaptureSession;
                                        streamRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                                                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                                        streamRequest = streamRequestBuilder.build();
                                        streamSession.setRepeatingRequest(streamRequest,
                                                streamCallback, streamBackgroundHandler);
                                    }
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }

                            @Override
                            public void onConfigureFailed(CameraCaptureSession cameraCaptureSession) {
                                error = "Stream configure failed.";
                            }
                        }, null);
            }
        } catch(Exception e) {
            e.printStackTrace();
        }
    }*/

    private void createCameraPreviewSession() {
        try {
            if (Build.VERSION.SDK_INT >= CAMERA2_SDK && cameraDevice != null
                    && previewSession == null && !previewing) {
                PreviewSize optimalSize = chooseOptimalSize();
                texture.setDefaultBufferSize(optimalSize.width, optimalSize.height);
                Surface surface = new Surface(texture);
                Surface streamSurface = imageReader.getSurface();
                previewRequestBuilder
                        = (CaptureRequest.Builder)CameraDevice.class
                        .getMethod("createCaptureRequest", int.class)
                        .invoke(cameraDevice, CameraDevice.TEMPLATE_PREVIEW);
                //previewRequestBuilder
                //        = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                previewRequestBuilder.addTarget(surface);
                //previewRequestBuilder
                //        = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
                previewRequestBuilder.addTarget(streamSurface);
                cameraDevice.createCaptureSession(
                        Arrays.asList(streamSurface, surface),
                        new CameraCaptureSession.StateCallback() {

                            @Override
                            public void onConfigured(CameraCaptureSession cameraCaptureSession) {
                                try {
                                    if (Build.VERSION.SDK_INT >= CAMERA2_SDK) {
                                        previewSession = cameraCaptureSession;
                                        previewRequestBuilder.set(CaptureRequest.CONTROL_CAPTURE_INTENT,
                                                CaptureRequest.CONTROL_CAPTURE_INTENT_PREVIEW);
                                        //previewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                                        //        CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                                        //Scene mode
                                        previewRequestBuilder.set(CaptureRequest.CONTROL_MODE,
                                                CaptureRequest.CONTROL_MODE_AUTO);
                                        //A special color effect to apply
                                        previewRequestBuilder.set(CaptureRequest.CONTROL_EFFECT_MODE,
                                                CaptureRequest.CONTROL_EFFECT_MODE_OFF);
                                        //White balance
                                        previewRequestBuilder.set(CaptureRequest.CONTROL_AWB_MODE,
                                                CaptureRequest.CONTROL_AWB_MODE_AUTO);
                                        //Auto-exposure
                                        previewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE,
                                                CaptureRequest.CONTROL_AE_MODE_ON);
                                        //previewRequestBuilder.set(CaptureRequest.FLASH_MODE,
                                        //        CaptureRequest.FLASH_MODE_TORCH);
                                        //previewRequestBuilder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION,
                                        //        6);
                                        //Auto-focus
                                        previewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                                                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO);

                                        previewRequest = previewRequestBuilder.build();
                                        previewSession.setRepeatingRequest(previewRequest,
                                                previewCallback, previewBackgroundHandler);

                                        //streamRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                                        //        CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                                        //streamRequest = streamRequestBuilder.build();
                                        //previewSession.setRepeatingBurst(
                                        //        Arrays.asList(previewRequest, streamRequest),
                                        //        previewCallback, previewBackgroundHandler);
                                    }
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }

                            @Override
                            public void onConfigureFailed(CameraCaptureSession cameraCaptureSession) {
                                error = "Preview configure failed.";
                            }
                        }, null);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void createPreviewCallback() {
        if (Build.VERSION.SDK_INT >= CAMERA2_SDK && previewCallback == null) {
            previewCallback = new CameraCaptureSession.CaptureCallback() {

            };
        }
    }

    /*private void createStreamCallback() {
        if (Build.VERSION.SDK_INT >= CAMERA2_SDK && streamCallback == null) {
            streamCallback = new CameraCaptureSession.CaptureCallback() {

            };
        }
    }*/

    /*private void startStreamBackgroundThread() {
        if (streamBackgroundThread == null) {
            streamBackgroundThread = new HandlerThread("StreamBackground");
            streamBackgroundThread.start();
            streamBackgroundHandler = new Handler(streamBackgroundThread.getLooper());
        }
    }*/

    private void startPreviewBackgroundThread() {
        if (previewBackgroundThread == null) {
            previewBackgroundThread = new HandlerThread("PreviewBackground");
            previewBackgroundThread.start();
            previewBackgroundHandler = new Handler(previewBackgroundThread.getLooper());
        }
    }

    private void stopPreviewBackgroundThread() {
        if (previewBackgroundThread != null) {
            if (Build.VERSION.SDK_INT >= 18)
                previewBackgroundThread.quitSafely();
            else
                previewBackgroundThread.quit();
            try {
                previewBackgroundThread.join();
                previewBackgroundThread = null;
                previewBackgroundHandler = null;
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    /*private void stopStreamBackgroundThread() {
        if (streamBackgroundThread != null) {
            if (Build.VERSION.SDK_INT >= 18)
                streamBackgroundThread.quitSafely();
            else
                streamBackgroundThread.quit();
            try {
                streamBackgroundThread.join();
                streamBackgroundThread = null;
                streamBackgroundHandler = null;
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }*/

    public interface ImageHelper {
        void setActivity(Activity activity);
        Bitmap convertYUV_420_888ToRGB3(
                byte[] yBytes, byte[] uBytes, byte[] vBytes,
                int uvRowStride, int uvPixelStride, int width, int height,
                int imageRotation);
    }

    public void initCamera(Activity activity, SurfaceTexture texture) {
        synchronized(HttpServer.sync) {
            if (Build.VERSION.SDK_INT >= CAMERA2_SDK) {
                try {
                    rsHelper = (ImageHelper)Class.forName("ru.proghouse.robocam.RSHelper").newInstance();
                    rsHelper.setActivity(activity);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                this.texture = texture;
                //Display display = activity.getWindowManager().getDefaultDisplay();
                //displayMetrics = new DisplayMetrics();
                //display.getMetrics(displayMetrics);
                SharedPreferences settings = activity.getSharedPreferences(ExtraKey.APP_PREFERENCE, Context.MODE_PRIVATE);
                storedPreviewSize = settings.getInt(ExtraKey.PREVIEW_SIZE, -1);
                useRenderScript = settings.getBoolean(ExtraKey.USER_RENDER_SCRIPT, DefaultValue.USER_RENDER_SCRIPT);
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
                        StreamConfigurationMap map = characteristics.get(
                                CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                        Size[] outputSizes = map.getOutputSizes(SurfaceTexture.class);
                        surfacePreviewSizes = new PreviewSize[outputSizes.length];
                        HashSet<Float> ratio = new HashSet<Float>();
                        for (int i = 0; i < outputSizes.length; i++) {
                            surfacePreviewSizes[i] = new PreviewSize(outputSizes[i]);
                            ratio.add((float) outputSizes[i].getHeight() / (float) outputSizes[i].getWidth());
                        }
                        outputSizes = map.getOutputSizes(ImageFormat.YUV_420_888);
                        previewSizes.clear();
                        for (int i = 0; i < outputSizes.length; i++)
                            if (ratio.contains((float) outputSizes[i].getHeight() / (float) outputSizes[i].getWidth()))
                                previewSizes.add(new PreviewSize(outputSizes[i]));
                        Collections.sort(previewSizes, new CompareSizesByArea());
                        previewSize = calculateNewPreviewSize();
                        createCameraCallback();
                        createPreviewCallback();
                        //createStreamCallback();
                        startPreviewBackgroundThread();
                        //startStreamBackgroundThread();
                        createImageAvailableListener();
                        imageReader = ImageReader.newInstance(previewSize.width, previewSize.height,
                                ImageFormat.YUV_420_888, /*maxImages*/2);
                        imageReader.setOnImageAvailableListener(
                                imageAvailableListener, previewBackgroundHandler);
                        manager.openCamera(camera2Id, stateCallback, previewBackgroundHandler);
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
            if (Build.VERSION.SDK_INT < CAMERA2_SDK) {
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
                    this.previewSizes.clear();
                    for (int i = 0; i < previewSizes.size(); i++)
                        this.previewSizes.add(new PreviewSize(previewSizes.get(i)));
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
            if (Build.VERSION.SDK_INT >= CAMERA2_SDK) {
                if (cameraDevice != null && this.texture != null && texture == this.texture) {
                    afterGrandPermission = false;
                    if (previewSession != null) {
                        previewSession.close();
                        previewSession = null;
                    }
                    /*if (streamSession != null) {
                        streamSession.close();
                        streamSession = null;
                    }*/
                    if (cameraDevice != null) {
                        try {
                            CameraDevice.class.getMethod("close").invoke(cameraDevice);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        //cameraDevice.close();
                        cameraDevice = null;
                    }
                    if (imageReader != null) {
                        imageReader.close();
                        imageReader = null;
                    }
                    stopPreviewBackgroundThread();
                    //stopStreamBackgroundThread();
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

    /*public boolean checkIfDisplayRotationIsChanged(Activity activity) {
        int curRotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        return displayRotation != curRotation;
    }*/

    private int calculateOrientation(Activity activity) {
        synchronized(HttpServer.sync) {
            portrait_n_facing = false;
            landscape_n_facing = false;
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
                if (Build.VERSION.SDK_INT >= CAMERA2_SDK) {
                    if (facing == CameraCharacteristics.LENS_FACING_FRONT
                            || facing == CameraCharacteristics.LENS_FACING_BACK) {
                        result = (360 - degrees) % 360;
                        landscape_n_facing =
                                (rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270)
                                && facing == CameraCharacteristics.LENS_FACING_FRONT;
                    }
                    /*else if (facing == CameraCharacteristics.LENS_FACING_BACK) {
                        result = degrees;
                    }*/
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
                displayRotation = rotation;
                return result;
            }
        }
        return displayOrientation;
    }

    public boolean updateOrientation(Activity activity){
        synchronized(HttpServer.sync) {
            portrait_n_facing = false;
            int oldDisplayOrientation = displayOrientation;
            displayOrientation = calculateOrientation(activity);
            if (camera != null)
                camera.setDisplayOrientation(displayOrientation);
            /*if ((camera != null && !previewing) || cameraDevice != null) {
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
                if (Build.VERSION.SDK_INT >= CAMERA2_SDK) {
                    if (facing == CameraCharacteristics.LENS_FACING_FRONT
                            || facing == CameraCharacteristics.LENS_FACING_BACK) {
                        result = (360 - degrees) % 360;
                    }
                    //else if (facing == CameraCharacteristics.LENS_FACING_BACK) {
                    //    result = degrees;
                    //}
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
                //displayRotation = rotation;
            })*/
            return oldDisplayOrientation != displayOrientation;
        }
    }

    private PreviewSize calculateNewPreviewSize() {
        if (previewSizes == null || previewSizes.size() == 0)
            return null;
        if (storedPreviewSize < 0) {
            PreviewSize first = previewSizes.get(0);
            PreviewSize last = previewSizes.get(previewSizes.size() - 1);
            int third = Math.round((float) previewSizes.size() / (float) 3.0);
            if (first.width > last.width || first.height > last.height)
                storedPreviewSize = previewSizes.size() - third;
            else
                storedPreviewSize = third - 1;
        }
        if (storedPreviewSize < 0)
            storedPreviewSize = 0;
        if (storedPreviewSize >= previewSizes.size())
            storedPreviewSize = previewSizes.size() - 1;
        return previewSizes.get(storedPreviewSize);
    }

    /*public boolean checkIfParametersIsChanged() {
        int oldWidth = previewSize == null ? 0 : previewSize.width;
        int oldHeight = previewSize == null ? 0 : previewSize.height;
        PreviewSize newPreviewSize = calculateNewPreviewSize();
        int newWidth = newPreviewSize == null ? 0 : newPreviewSize.width;
        int newHeight = newPreviewSize == null ? 0 : newPreviewSize.height;
        return oldWidth != newWidth || oldHeight != newHeight;
    }*/

    public void configureTransform(Activity activity, View _textureView,
                                   int viewWidth, int viewHeight) {
        if (Build.VERSION.SDK_INT >= CAMERA2_SDK) {
            TextureView textureView = (TextureView)_textureView;
            int orientation = calculateOrientation(activity);
            Matrix matrix = new Matrix();
            RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
            float centerX = viewRect.centerX();
            float centerY = viewRect.centerY();

            if (orientation == 90 || orientation == 270) {
                RectF bufferRect = new RectF(0, 0, previewSize.height, previewSize.width);
                bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
                matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
                float scale = Math.max(
                        (float) viewHeight / previewSize.height,
                        (float) viewWidth / previewSize.width);
                matrix.postScale(scale, scale, centerX, centerY);
                matrix.postRotate(orientation, centerX, centerY);
            }
            else
                matrix.postRotate(orientation, centerX, centerY);

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
            if (Build.VERSION.SDK_INT >= CAMERA2_SDK) {
                if (cameraDevice == null)
                    return result;
            } else {
                if (camera == null || previewing)
                    return result;
            }
            //int oldWidth = previewSize == null ? 0 : previewSize.width;
            //int oldHeight = previewSize == null ? 0 : previewSize.height;
            previewSize = calculateNewPreviewSize();
            //int newWidth = previewSize == null ? 0 : previewSize.width;
            //int newHeight = previewSize == null ? 0 : previewSize.height;
            for (RgbData data : rgb)
                data.Restore();
            //rgbReader.Restore();
            //rgbWriter.Restore();
            if (Build.VERSION.SDK_INT < CAMERA2_SDK) {
                Camera.Parameters parameters = camera.getParameters();
                parameters.setPreviewSize(previewSize.width, previewSize.height);
                parameters.setJpegQuality(100);
                camera.setParameters(parameters);
            }
            result = oldWidth != previewSize.width || oldHeight != previewSize.height;
            oldWidth = previewSize == null ? -1 : previewSize.width;
            oldHeight = previewSize == null ? -1 : previewSize.height;
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

    private void createImageAvailableListener() {
        if (Build.VERSION.SDK_INT >= CAMERA2_SDK) {
            if (imageAvailableListener == null)
                imageAvailableListener = new ImageReader.OnImageAvailableListener() {
                    @Override
                    public void onImageAvailable(ImageReader imageReader) {
                        if (Build.VERSION.SDK_INT >= CAMERA2_SDK) {
                            try {
                                Image image = imageReader.acquireLatestImage();
                                if (image != null) {
                                    //int width = image.getWidth();
                                    //int height = image.getHeight();
                                    if (clientCount > 0)
                                        storeYUV_420_888(image);
                                    image.close();
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    }
                };
        }
    }

    private void storeYUV_420_888(Object _image) {
        if (Build.VERSION.SDK_INT >= CAMERA2_SDK) {
            if (_image == null)
                return;
            Image image = (Image)_image;
            id++;
            if (id == Integer.MAX_VALUE)
                id = Integer.MIN_VALUE;
            if (id == 0)
                id++;
            ByteBuffer yBuf = image.getPlanes()[0].getBuffer();
            ByteBuffer uBuf = image.getPlanes()[1].getBuffer();
            ByteBuffer vBuf = image.getPlanes()[2].getBuffer();
            int size = yBuf.remaining();
            if (rgb[writeIndex].yBytes == null || rgb[writeIndex].yBytes.length != size)
                rgb[writeIndex].yBytes = new byte[size];
            yBuf.rewind();
            yBuf.get(rgb[writeIndex].yBytes);
            size = uBuf.remaining();
            if (rgb[writeIndex].uBytes == null || rgb[writeIndex].uBytes.length != size)
                rgb[writeIndex].uBytes = new byte[size];
            uBuf.rewind();
            uBuf.get(rgb[writeIndex].uBytes);
            size = vBuf.remaining();
            if (rgb[writeIndex].vBytes == null || rgb[writeIndex].vBytes.length != size)
                rgb[writeIndex].vBytes = new byte[vBuf.remaining()];
            vBuf.rewind();
            vBuf.get(rgb[writeIndex].vBytes);
            rgb[writeIndex].uRowStride = image.getPlanes()[1].getRowStride();
            rgb[writeIndex].uPixelStride = image.getPlanes()[1].getPixelStride();
            rgb[writeIndex].vRowStride = image.getPlanes()[2].getRowStride();
            rgb[writeIndex].vPixelStride = image.getPlanes()[2].getPixelStride();
            rgb[writeIndex].width = image.getWidth();
            rgb[writeIndex].height = image.getHeight();
            rgb[writeIndex].initialized = true;
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

    /*private Bitmap YUV_420_888_toRGB(Image image, int width, int height){
        if (Build.VERSION.SDK_INT >= CAMERA2_SDK) {
            // Get the three image planes
            Image.Plane[] planes = image.getPlanes();
            ByteBuffer buffer = planes[0].getBuffer();
            byte[] y = new byte[buffer.remaining()];
            buffer.get(y);

            buffer = planes[1].getBuffer();
            byte[] u = new byte[buffer.remaining()];
            buffer.get(u);

            buffer = planes[2].getBuffer();
            byte[] v = new byte[buffer.remaining()];
            buffer.get(v);

            // get the relevant RowStrides and PixelStrides
            // (we know from documentation that PixelStride is 1 for y)
            int yRowStride = planes[0].getRowStride();
            int uvRowStride = planes[1].getRowStride();  // we know from   documentation that RowStride is the same for u and v.
            int uvPixelStride = planes[1].getPixelStride();  // we know from   documentation that PixelStride is the same for u and v.


            // rs creation just for demo. Create rs just once in onCreate and use it again.
            RenderScript rs = RenderScript.create(this);
            //RenderScript rs = MainActivity.rs;
            ScriptC_yuv420888 mYuv420 = new ScriptC_yuv420888(rs);

            // Y,U,V are defined as global allocations, the out-Allocation is the Bitmap.
            // Note also that uAlloc and vAlloc are 1-dimensional while yAlloc is 2-dimensional.
            Type.Builder typeUcharY = new Type.Builder(rs, Element.U8(rs));
            typeUcharY.setX(yRowStride).setY(height);
            Allocation yAlloc = Allocation.createTyped(rs, typeUcharY.create());
            yAlloc.copyFrom(y);
            mYuv420.set_ypsIn(yAlloc);

            Type.Builder typeUcharUV = new Type.Builder(rs, Element.U8(rs));
            // note that the size of the u's and v's are as follows:
            //      (  (width/2)*PixelStride + padding  ) * (height/2)
            // =    (RowStride                          ) * (height/2)
            // but I noted that on the S7 it is 1 less...
            typeUcharUV.setX(u.length);
            Allocation uAlloc = Allocation.createTyped(rs, typeUcharUV.create());
            uAlloc.copyFrom(u);
            mYuv420.set_uIn(uAlloc);

            Allocation vAlloc = Allocation.createTyped(rs, typeUcharUV.create());
            vAlloc.copyFrom(v);
            mYuv420.set_vIn(vAlloc);

            // handover parameters
            mYuv420.set_picWidth(width);
            mYuv420.set_uvRowStride(uvRowStride);
            mYuv420.set_uvPixelStride(uvPixelStride);

            Bitmap outBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            Allocation outAlloc = Allocation.createFromBitmap(rs, outBitmap, Allocation.MipmapControl.MIPMAP_NONE, Allocation.USAGE_SCRIPT);

            Script.LaunchOptions lo = new Script.LaunchOptions();
            lo.setX(0, width);  // by this we ignore the y’s padding zone, i.e. the right side of x between width and yRowStride
            lo.setY(0, height);

            mYuv420.forEach_doConvert(outAlloc, lo);
            outAlloc.copyTo(outBitmap);
        }
        return outBitmap;
    }*/

    private int id = Integer.MIN_VALUE;
    private RgbData[] rgb = new RgbData[] {new RgbData(), new RgbData(), new RgbData()};
    private int writeIndex = 1, oldWriteIndex = 0, readIndex = 0;
    private Object lock = new Object();
    private Object readObject = new Object();
    private int readerCount = 0;

    @Override
    public void onPreviewFrame(byte[] data, Camera camera) {
        if (clientCount <= 0 || !previewing)
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
        if (rgb[writeIndex].nv21 == null || rgb[writeIndex].nv21.length != data.length)
            rgb[writeIndex].nv21 = data.clone();
        else
            System.arraycopy(data, 0, rgb[writeIndex].nv21, 0, data.length);
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

    /*private byte[] convertYUV420ToN21(Object imgYUV420) {
        byte[] rez = new byte[0];
        if (Build.VERSION.SDK_INT >= CAMERA2_SDK) {
            ByteBuffer buffer0 = ((Image)imgYUV420).getPlanes()[0].getBuffer();
            ByteBuffer buffer2 = ((Image)imgYUV420).getPlanes()[2].getBuffer();
            int buffer0_size = buffer0.remaining();
            int buffer2_size = buffer2.remaining();
            rez = new byte[buffer0_size + buffer2_size];
            buffer0.get(rez, 0, buffer0_size);
            buffer2.get(rez, buffer0_size, buffer2_size);
        }
        return rez;
    }*/

    /*private void storeImage(Object _image) {
        if (Build.VERSION.SDK_INT >= CAMERA2_SDK) {
            if (_image == null)
                return;
            Image image = (Image)_image;
            id++;
            if (id == Integer.MAX_VALUE)
                id = Integer.MIN_VALUE;
            if (id == 0)
                id++;
            if (!rgb[writeIndex].initialized) {
                rgb[writeIndex].width = image.getWidth();
                rgb[writeIndex].height = image.getHeight();
                rgb[writeIndex].initialized = true;
            }
            ByteBuffer buffer0 = image.getPlanes()[0].getBuffer();
            ByteBuffer buffer2 = image.getPlanes()[2].getBuffer();
            int buffer0_size = buffer0.remaining();
            int buffer2_size = buffer2.remaining();
            if (rgb[writeIndex].nv21 == null || rgb[writeIndex].nv21.length != buffer0_size + buffer2_size)
                rgb[writeIndex].nv21 = new byte[buffer0_size + buffer2_size];
            buffer0.get(rgb[writeIndex].nv21, 0, buffer0_size);
            buffer2.get(rgb[writeIndex].nv21, buffer0_size, buffer2_size);
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
    }*/

    /*private void processJpeg(Object image) {
        if (Build.VERSION.SDK_INT >= CAMERA2_SDK) {
            try {
                if (image == null || ((Image) image).getFormat() != ImageFormat.JPEG)
                    return;
                id++;
                if (id == Integer.MAX_VALUE)
                    id = Integer.MIN_VALUE;
                if (id == 0)
                    id++;
                if (!rgb[writeIndex].initialized) {
                    rgb[writeIndex].width = ((Image) image).getWidth();
                    rgb[writeIndex].height = ((Image) image).getHeight();
                    rgb[writeIndex].initialized = true;
                }
                ByteBuffer buffer = ((Image) image).getPlanes()[0].getBuffer();
                rgb[writeIndex].jpeg = new byte[buffer.remaining()];
                buffer.get(rgb[writeIndex].jpeg);
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
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }*/

    //long mstime1 = 0;
    //long mscount1 = 0;
    //long mstime2 = 0;
    //long mscount2 = 0;

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
            byte[] imageBytes = null;
            synchronized (readObject) {
                if (rgb[readIndex].id != excludedId) {
                    if (rgb[readIndex].imageBytesId != rgb[readIndex].id) {
                        int imageRotation = 0;
                        if (displayOrientation != 0 || Build.VERSION.SDK_INT >= CAMERA2_SDK)
                            if (portrait_n_facing)
                                imageRotation = displayOrientation + 180;
                            else if (landscape_n_facing)
                                imageRotation = (displayOrientation + sensorOrientation + 180) % 360;
                            else
                                imageRotation = (displayOrientation + sensorOrientation) % 360;
                        boolean rotate = true;
                        Bitmap bitmap = null;
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        if (rgb[readIndex].nv21 != null) {
                            YuvImage yuvImage = new YuvImage(rgb[readIndex].nv21, ImageFormat.NV21,
                                    rgb[readIndex].width, rgb[readIndex].height, null);
                            yuvImage.compressToJpeg(new Rect(0, 0, rgb[readIndex].width, rgb[readIndex].height),
                                    imageRotation == 0 ? jpegQuality : 100, baos);
                            imageBytes = baos.toByteArray();
                        } else if (rgb[readIndex].yBytes != null
                                && rgb[readIndex].uBytes != null
                                && rgb[readIndex].vBytes != null) {
                            try {
                                //long curtime1 = System.currentTimeMillis();
                                if (useRenderScript && rsHelper != null) {
                                    //Converting via RenderScript - 33ms
                                    bitmap = rsHelper.convertYUV_420_888ToRGB3(
                                            rgb[readIndex].yBytes, rgb[readIndex].uBytes, rgb[readIndex].vBytes,
                                            rgb[readIndex].uRowStride, rgb[readIndex].uPixelStride,
                                            rgb[readIndex].width, rgb[readIndex].height, imageRotation);
                                } else {
                                    //Java converting - 100ms
                                    int[] bmp = new int[rgb[readIndex].width * rgb[readIndex].height];
                                    convertYUV_420_888ToRGB2(bmp, rgb[readIndex].yBytes, rgb[readIndex].uBytes, rgb[readIndex].vBytes,
                                            rgb[readIndex].uRowStride, rgb[readIndex].uPixelStride,
                                            rgb[readIndex].width, rgb[readIndex].height, imageRotation);
                                    if (imageRotation == 270 || imageRotation == 90)
                                        bitmap = Bitmap.createBitmap(bmp, rgb[readIndex].height, rgb[readIndex].width,
                                                Bitmap.Config.ARGB_8888);
                                    else
                                        bitmap = Bitmap.createBitmap(bmp, rgb[readIndex].width, rgb[readIndex].height,
                                                Bitmap.Config.ARGB_8888);
                                }
                                rotate = false;
                                //if (mscount1 == 0)
                                //    mstime1 = System.currentTimeMillis() - curtime1;
                                //else
                                //    mstime1 = (System.currentTimeMillis() - curtime1 + mstime1) / 2;
                                //mscount1++;
                                //bitmap.compress(Bitmap.CompressFormat.JPEG, imageRotation == 0 ? jpegQuality : 100, baos);
                                //imageBytes = baos.toByteArray();
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        } else if (rgb[readIndex].jpeg != null)
                            imageBytes = rgb[readIndex].jpeg;
                        if (rotate && (imageRotation != 0 || rgb[readIndex].jpeg != null)) {
                            //41ms
                            //long curtime2 = System.currentTimeMillis();
                            if (imageBytes != null)
                                bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
                            Matrix matrix = new Matrix();
                            matrix.postRotate(imageRotation);
                            bitmap = Bitmap.createBitmap(bitmap, 0, 0,
                                    rgb[readIndex].width, rgb[readIndex].height, matrix, true);
                            //if (mscount2 == 0)
                            //    mstime2 = System.currentTimeMillis() - curtime2;
                            //else
                            //    mstime2 = (System.currentTimeMillis() - curtime2 + mstime2) / 2;
                            //mscount2++;
                        }
                        if (bitmap != null) {
                            baos.reset();
                            bitmap.compress(Bitmap.CompressFormat.JPEG, jpegQuality, baos);
                            imageBytes = baos.toByteArray();
                        }
                        rgb[readIndex].imageBytesId = rgb[readIndex].id;
                        rgb[readIndex].imageBytes = imageBytes;
                    } else {
                        imageBytes = rgb[readIndex].imageBytes;
                    }
                }
            }
            if (imageBytes != null) {
                outputStream.write(("Content-type: image/jpeg\r\n"
                        + "Content-Length: " + imageBytes.length + "\r\n\r\n").getBytes());
                outputStream.write(imageBytes);
                outputStream.write(("\r\n").getBytes());
                outputStream.flush();
            }

            /*if (imageBytes != null || bitmap != null) {
                if (imageRotation != 0 || rgb[readIndex].jpeg != null) {
                    if (imageBytes != null)
                        bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
                    Matrix matrix = new Matrix();
                    matrix.postRotate(imageRotation);
                    bitmap = Bitmap.createBitmap(bitmap, 0, 0,
                            rgb[readIndex].width, rgb[readIndex].height, matrix, true);
                    baos.reset();
                    bitmap.compress(Bitmap.CompressFormat.JPEG, jpegQuality, baos);
                    imageBytes = baos.toByteArray();
                }
                if (imageBytes == null) {
                    baos.reset();
                    bitmap.compress(Bitmap.CompressFormat.JPEG, imageRotation == 0 ? jpegQuality : 100, baos);
                    imageBytes = baos.toByteArray();
                }
                outputStream.write(("Content-type: image/jpeg\r\n"
                        + "Content-Length: " + imageBytes.length + "\r\n\r\n").getBytes());
                outputStream.write(imageBytes);
                outputStream.write(("\r\n").getBytes());
                outputStream.flush();
            }*/
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

    //Do not work!
    /*private void convertYUV_420_888ToRGB(int[] rgb, byte[] yBytes, byte[] uBytes, byte[] vBytes,
                                         int uRowStride, int vRowStride, int width, int height)
    {
        final int BYTES_PER_RGB_PIX = 3;
        int yp, up, vp; // YUV positions.
        byte[] yuvPixel = { 0, 0, 0 };
        byte[] rowBytesRGB = new byte[width * BYTES_PER_RGB_PIX];
        int lSumR = 0, lSumG = 0, lSumB = 0;

        // For Android 5.0.1(API 21) has an issue which is blank(zero) U, V arrays except the first some bytes(eg. 656 bytes for 176x144).
        // This issue caused the converted image turned to Green scaled overall of the image.
        // This issue is fixed in Android 5.1.1(API 22).

        yp = 0;
        for (int i = 0; i < height; i++)
        {
            up = (i >> 1) * uRowStride;
            vp = (i >> 1) * vRowStride;
            for (int j = 0; j < width; j++)
            {
                yuvPixel[0] = yBytes[yp++];
                if ((j & 1) == 0)
                {
                    yuvPixel[1] = uBytes[up++];
                    yuvPixel[2] = vBytes[vp++];
                }

                // For the test to get 3 RGB colors average.
                yuvToRgb_Test(yuvPixel, j * BYTES_PER_RGB_PIX, rowBytesRGB);
                lSumR += (rowBytesRGB[j * BYTES_PER_RGB_PIX] & 0xff);
                lSumG += (rowBytesRGB[j * BYTES_PER_RGB_PIX + 1] & 0xff);
                lSumB += (rowBytesRGB[j * BYTES_PER_RGB_PIX + 2] & 0xff);

                rgb[yp - 1] = 0xff000000 | ((lSumR << 6) & 0xff0000) | ((lSumG >> 2) & 0xff00) | ((lSumB >> 10) & 0xff);
            }
        }
    }

    private void yuvToRgb_Test(byte[] yuvData, int rgbOffset, byte[] rgbOut)
    {
        final int COLOR_MAX = 255;

        float y = yuvData[0] & 0xff; // Y channel
        float cb = yuvData[1] & 0xff; // U channel
        float cr = yuvData[2] & 0xff; // V channel

        // Convert YUV fixed pixel to RGB (from JFIF's "Conversion to and from RGB" section).
        float r = y + 1.402f * (cr - 128);
        float g = y - 0.34414f * (cb - 128) - 0.71414f * (cr - 128);
        float b = y + 1.772f * (cb - 128);

        // Clamp to [0, 255].
        r = Math.max(0, Math.min(COLOR_MAX, r));
        g = Math.max(0, Math.min(COLOR_MAX, g));
        b = Math.max(0, Math.min(COLOR_MAX, b));

        // 'byte' is signed, it takes the last 8bits of the integer.
        rgbOut[rgbOffset] = (byte) ((int) r);
        rgbOut[rgbOffset + 1] = (byte) ((int) g);
        rgbOut[rgbOffset + 2] = (byte) ((int) b);
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

    private void convertYUV_420_888ToRGB2(
            int[] rgb, byte[] yBytes, byte[] uBytes, byte[] vBytes,
            int uvRowStride, int uvPixelStride,
            int width, int height, int imageRotation) {
        int yuvWidth = width;
        if (imageRotation == 270 || imageRotation == 90) {
            width = height;
            height = yuvWidth;
        }
        int i, y, u, v, r, g, b, uvIndex, _x, _y;
        for (int j = 0, yp = 0; j < height; j++) {
            for (i = 0; i < width; i++, yp++) {
                if (imageRotation == 180) {
                    _x = width - i - 1;
                    _y = height - j - 1;
                } else if (imageRotation == 90) {
                    _x = j;
                    _y = width - i - 1;
                } else if (imageRotation == 270) {
                    _x = height - j - 1;
                    _y = i;
                } else {
                    _x = i;
                    _y = j;
                }
                y = yBytes[_y * yuvWidth + _x];
                if (y < 0) y += 256;
                uvIndex = uvRowStride * (_y / 2) + uvPixelStride * (_x / 2);
                u = uBytes[uvIndex];
                if (u < 0) u += 256;
                v = vBytes[uvIndex];
                if (v < 0) v += 256;

                r = (int)((float)y + 1.402f * ((float)v - 128));
                g = (int)((float)y - 0.34414f * ((float)u - 128) - 0.71414f * ((float)v - 128));
                b = (int)((float)y + 1.772f * ((float)u - 128));

                r = Math.max(0, Math.min(255, r));
                g = Math.max(0, Math.min(255, g));
                b = Math.max(0, Math.min(255, b));

                rgb[yp] = 0xff000000 | ((r << 16) & 0xff0000) | ((g << 8) & 0xff00) | (b & 0xff);
            }
        }
    }

    private Bitmap convertYUV_420_888ToRGB3(
            byte[] yBytes, byte[] uBytes, byte[] vBytes,
            int uvRowStride, int uvPixelStride, int width, int height,
            int imageRotation) {
        /*if (Build.VERSION.SDK_INT >= CAMERA2_SDK) {
            ScriptC_yuv420888 mYuv420 = new ScriptC_yuv420888(renderScript);

            // Y,U,V are defined as global allocations, the out-Allocation is the Bitmap.
            // Note also that uAlloc and vAlloc are 1-dimensional while yAlloc is 2-dimensional.
            Type.Builder typeUcharY = new Type.Builder(renderScript, Element.U8(renderScript));
            typeUcharY.setX(width).setY(height);
            Allocation yAlloc = Allocation.createTyped(renderScript, typeUcharY.create());
            yAlloc.copyFrom(yBytes);
            mYuv420.set_ypsIn(yAlloc);

            Type.Builder typeUcharUV = new Type.Builder(renderScript, Element.U8(renderScript));
            // note that the size of the u's and v's are as follows:
            //      (  (width/2)*PixelStride + padding  ) * (height/2)
            // =    (RowStride                          ) * (height/2)
            // but I noted that on the S7 it is 1 less...
            typeUcharUV.setX(uBytes.length);
            Allocation uAlloc = Allocation.createTyped(renderScript, typeUcharUV.create());
            uAlloc.copyFrom(uBytes);
            mYuv420.set_uIn(uAlloc);

            Allocation vAlloc = Allocation.createTyped(renderScript, typeUcharUV.create());
            vAlloc.copyFrom(vBytes);
            mYuv420.set_vIn(vAlloc);

            // handover parameters
            //mYuv420.set_picWidth(width);
            mYuv420.set_uvRowStride(uvRowStride);
            mYuv420.set_uvPixelStride(uvPixelStride);
            if (imageRotation < 0)
                imageRotation += 3600;
            mYuv420.set_imageRotation(imageRotation % 360);
            Script.LaunchOptions lo = new Script.LaunchOptions();
            Bitmap outBitmap = null;
            if (imageRotation == 270 || imageRotation == 90) {
                outBitmap = Bitmap.createBitmap(height, width, Bitmap.Config.ARGB_8888);
                lo.setX(0, height);  // by this we ignore the y’s padding zone, i.e. the right side of x between width and yRowStride
                lo.setY(0, width);
                mYuv420.set_width(height);
                mYuv420.set_height(width);
            }
            else {
                outBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                lo.setX(0, width);  // by this we ignore the y’s padding zone, i.e. the right side of x between width and yRowStride
                lo.setY(0, height);
                mYuv420.set_width(width);
                mYuv420.set_height(height);
            }
            Allocation outAlloc = Allocation.createFromBitmap(renderScript,
                    outBitmap, Allocation.MipmapControl.MIPMAP_NONE,
                    Allocation.USAGE_SCRIPT);

            mYuv420.forEach_doConvert(outAlloc, lo);
            outAlloc.copyTo(outBitmap);

            return outBitmap;
        }*/
        return null;

    }

}
