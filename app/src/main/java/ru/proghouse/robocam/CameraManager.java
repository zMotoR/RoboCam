package ru.proghouse.robocam;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.os.Build;
import android.view.Surface;
import android.view.SurfaceHolder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
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
    private int storedPreviewSize = 0;
    private Camera.Size previewSize = null;
    private RgbData rgbWriter = new RgbData();
    private RgbData rgbReader = new RgbData();
    private int rgbIndex = 0;
    private boolean previewing = false;
    private volatile int clientCount = 0;
    private int jpegQuality = 60;
    private OutputStream outputStream = null;
    private volatile int displayOrientation = 0;
    private volatile SurfaceHolder holder = null;
    private volatile boolean portrait_n_facing = false;

    CameraManager(){
    }

    public static CameraManager getCameraManager() {
        return cameraManager;
    }

    public boolean isInitialized() {
        synchronized(HttpServer.sync) {
            return camera != null;
        }
    }

    public Camera.Size getPreviewSize(){
        synchronized(HttpServer.sync) {
            return previewSize;
        }
    }

    public int getActualPreviewWidth(){
        synchronized(HttpServer.sync) {
            return cameraManager.isPreviewing()
                    ? (cameraManager.getDisplayOrientation() % 180 == 0
                    ? cameraManager.getPreviewSize().width
                    : cameraManager.getPreviewSize().height)
                    : -1;
        }
    }

    public int getActualPreviewHeight(){
        synchronized(HttpServer.sync) {
            return cameraManager.isPreviewing()
                    ? (cameraManager.getDisplayOrientation() % 180 == 0
                    ? cameraManager.getPreviewSize().height
                    : cameraManager.getPreviewSize().width)
                    : -1;
        }
    }

    public void addClient() {
        clientCount++;
    }

    public void removeClient() {
        clientCount--;
    }

    public void changeHolder(Context context, SurfaceHolder holder) {
        synchronized(HttpServer.sync) {
            releaseCamera(this.holder);
            initCamera(context, holder);
        }
    }

    public void initCamera(Context context, SurfaceHolder holder){
        synchronized(HttpServer.sync) {
            SharedPreferences settings = context.getSharedPreferences(ExtraKey.APP_PREFERENCE, Context.MODE_PRIVATE);
            if (Build.VERSION.SDK_INT >= 9) {
                cameraId = settings.getInt(ExtraKey.CAMERA_ID, 0);
                camera = Camera.open(cameraId);
                jpegQuality = settings.getInt(ExtraKey.JPEG_QUALITY, 60);
            }
            else if (cameraId == 0)
                camera = Camera.open();
            else {
                error = server.getString(R.string.error_only_back_facing_camera_supported);
                return;
            }
            storedPreviewSize = settings.getInt(ExtraKey.PREVIEW_SIZE, 0);
            try {
                camera.setPreviewDisplay(holder);
            } catch (IOException e) {
                error = e.getMessage();
                e.printStackTrace();
                camera.release();
                camera = null;
            }
            startPreview();
            this.holder = holder;
        }
    }

    public void releaseCamera(SurfaceHolder holder){
        synchronized(HttpServer.sync) {
            if (camera != null && this.holder != null && holder == this.holder) {
                stopPreview();
                camera.release();
                camera = null;
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

    public boolean updateOrientation(Activity activity){
        synchronized(HttpServer.sync) {
            portrait_n_facing = false;
            int oldDisplayOrientation = displayOrientation;
            if (camera != null && !previewing) {
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
                int result;
                if (Build.VERSION.SDK_INT >= 9) {
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
                camera.setDisplayOrientation(result);
                displayOrientation = result;
            }
            return oldDisplayOrientation != displayOrientation;
        }
    }

    public boolean updateParameters(){
        boolean result = false;
        synchronized(HttpServer.sync) {
            if (camera != null && !previewing) {
                rgbReader.Restore();
                rgbWriter.Restore();
                Camera.Parameters parameters = camera.getParameters();
                //List<Integer> formats = parameters.getSupportedPreviewFormats();
                //if (formats.contains(ImageFormat.YV12))
                //    parameters.setPreviewFormat(ImageFormat.YV12);
                List<Camera.Size> previewSizes = parameters.getSupportedPreviewSizes();
                if (storedPreviewSize < 0)
                    storedPreviewSize = 0;
                if (storedPreviewSize >= previewSizes.size())
                    storedPreviewSize = previewSizes.size() - 1;
                int oldWidth = previewSize == null ? 0 : previewSize.width;
                int oldHeight = previewSize == null ? 0 : previewSize.height;
                previewSize = previewSizes.get(storedPreviewSize);
                parameters.setPreviewSize(previewSize.width, previewSize.height);
                parameters.setJpegQuality(100);
                camera.setParameters(parameters);
                result = oldWidth != previewSize.width || oldHeight != previewSize.height;
            }
        }
        return result; //true if updated
    }

    private void tryToChangeRgb(){
        if (rgbReader.ready && rgbWriter.ready){
            RgbData tmp = rgbReader;
            rgbReader = rgbWriter;
            rgbWriter = tmp;
            rgbReader.ready = false;
            rgbWriter.ready = false;
        }
    }

    public boolean writeJpg(OutputStream outputStream, String boundary) throws IOException {
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
            /*decodeYUV420(rgbReader.buf, rgbReader.yuv420, rgbReader.width, rgbReader.height);
            Bitmap bitmap = Bitmap.createBitmap(rgbReader.buf, rgbReader.width, rgbReader.height,
                    Bitmap.Config.ARGB_8888);*/
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
    }

    @Override
    public void onPreviewFrame(byte[] data, Camera camera) {
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

    public void setJpegQuality(int jpegQuality){
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
    }

}
