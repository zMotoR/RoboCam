package ru.proghouse.robocam;

/**
 * Created by Alexey Valuev on 25.01.2016.
 */
public class RgbData {
    public volatile boolean initialized = false;
    //public int[] buf = null;
    public byte[] nv21 = null;
    public byte[] jpeg = null;
    public byte[] yBytes = null;
    public byte[] uBytes = null;
    public byte[] vBytes = null;
    public int uRowStride = 0;
    public int uPixelStride = 0;
    public int vRowStride = 0;
    public int vPixelStride = 0;
    public int width = 0;
    public int height = 0;
    public volatile boolean ready = false;
    public volatile int id = 0;

    public void Restore(){
        initialized = false;
        ready = false;
        id = 0;
    }
}
