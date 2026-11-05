package com.sony.scalar.media;

/**
 * Compile-time stub (see CameraEx). One picture's Exif and DCF attributes and its 160x120
 * thumbnail, from AvindexStore.Images.Media.getImageInfo. Recycle after use.
 */
public abstract class AvindexContentInfo {
    public static final String TAG_APERTURE = "FNumber";
    public static final String TAG_DATETIME = "DateTime";
    public static final String TAG_DCF_TBL_DIR_NAME = "DCF_TBLDirName";
    public static final String TAG_DCF_TBL_FILE_NAME = "DCF_TBLFileName";
    public static final String TAG_EXPOSURE_BIAS_VALUE = "ExposureBiasValue";
    public static final String TAG_EXPOSURE_TIME = "ExposureTime";
    public static final String TAG_FOCAL_LENGTH = "FocalLength";
    public static final String TAG_IMAGE_LENGTH = "ImageLength";
    public static final String TAG_IMAGE_WIDTH = "ImageWidth";
    public static final String TAG_ISO = "ISOSpeedRatings";
    public static final String TAG_ORIENTATION = "Orientation";

    public AvindexContentInfo() { throw new RuntimeException("Stub!"); }

    public String getAttribute(String tag) { throw new RuntimeException("Stub!"); }
    public int getAttributeInt(String tag, int defaultValue) { throw new RuntimeException("Stub!"); }
    public double getAttributeDouble(String tag, double defaultValue) { throw new RuntimeException("Stub!"); }
    public abstract byte[] getThumbnail();
    public void recycle() { throw new RuntimeException("Stub!"); }
}
