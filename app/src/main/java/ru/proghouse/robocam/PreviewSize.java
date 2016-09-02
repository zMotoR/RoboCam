package ru.proghouse.robocam;

import android.hardware.Camera;
import android.os.Build;
import android.util.Size;

/**
 * Created by Alexey Valuev on 06.08.2016.
 */
public final class PreviewSize {
    public int width = 0, height = 0;

    public PreviewSize(int width, int height) {
        this.width = width;
        this.height = height;
    }

    public PreviewSize(Object o) {
        if (Build.VERSION.SDK_INT < CameraManager.CAMERA2_SDK
                && Camera.Size.class.isAssignableFrom(o.getClass())) {
            width = ((Camera.Size) o).width;
            height = ((Camera.Size) o).height;
        } else if (Build.VERSION.SDK_INT >= CameraManager.CAMERA2_SDK
                && Size.class.isAssignableFrom(o.getClass())) {
            width = ((Size)o).getWidth();
            height = ((Size)o).getHeight();
        }
    }
}
