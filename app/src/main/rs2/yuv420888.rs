#pragma version(1)
#pragma rs java_package_name(ru.proghouse.robocam)
#pragma rs_fp_relaxed

int32_t width;
int32_t height;

uint uvPixelStride, uvRowStride, imageRotation;
rs_allocation ypsIn,uIn,vIn;

// The LaunchOptions ensure that the Kernel does not enter the padding  zone of Y, so yRowStride can be ignored WITHIN the Kernel.
uchar4 __attribute__((kernel)) doConvert(uint32_t x, uint32_t y) {

    if (imageRotation == 180) {
        x = width - x - 1;
        y = height - y - 1;
    } else if (imageRotation == 90) {
        uint32_t newY = x;
        x = y;
        y = width - newY - 1;
    } else if (imageRotation == 270) {
        uint32_t newY = x;
        x = height - y - 1;
        y = newY;
    }

    // index for accessing the uIn's and vIn's
    uint uvIndex=  uvPixelStride * (x/2) + uvRowStride*(y/2);

    // get the y,u,v values
    uchar yps= rsGetElementAt_uchar(ypsIn, x, y);
    uchar u= rsGetElementAt_uchar(uIn, uvIndex);
    uchar v= rsGetElementAt_uchar(vIn, uvIndex);

    // calc argb
    int4 argb;
    argb.r = yps + v * 1436 / 1024 - 179;
    argb.g =  yps -u * 46549 / 131072 + 44 -v * 93604 / 131072 + 91;
    argb.b = yps +u * 1814 / 1024 - 227;
    argb.a = 255;

    if (argb.r < 0) argb.r = 0;
    if (argb.r > 255) argb.r = 255;
    if (argb.g < 0) argb.g = 0;
    if (argb.g > 255) argb.g = 255;
    if (argb.b < 0) argb.b = 0;
    if (argb.b > 255) argb.b = 255;

    uchar4 out = convert_uchar4(argb);
    //uchar4 out = convert_uchar4(clamp(argb, 0, 255));

    return out;
}