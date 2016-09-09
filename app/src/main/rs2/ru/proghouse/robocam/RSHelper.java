package ru.proghouse.robocam;

import android.support.v8.renderscript.*;
import android.graphics.Bitmap;
import android.app.Activity;
import android.os.Build;

/**
 * Created by Alexey Valuev on 07.09.2016.
 */
public class RSHelper implements CameraManager.ImageHelper {
    private RenderScript renderScript = null;
    private Allocation inputAllocation = null;
    private Allocation outputAllocation = null;

    public void setActivity(Activity activity) {
        renderScript = RenderScript.create(activity);
    }

    public Bitmap convertYUV_420_888ToRGB3(
            byte[] yBytes, byte[] uBytes, byte[] vBytes,
            int uvRowStride, int uvPixelStride, int width, int height,
            int imageRotation) {
        if (Build.VERSION.SDK_INT >= CameraManager.CAMERA2_SDK) {
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
        }
        return null;

    }
}
