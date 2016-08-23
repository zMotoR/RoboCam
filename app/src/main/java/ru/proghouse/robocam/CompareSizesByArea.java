package ru.proghouse.robocam;

import java.util.Comparator;

/**
 * Created by Alexey Valuev on 23.08.2016.
 */
class CompareSizesByArea implements Comparator<PreviewSize> {

    @Override
    public int compare(PreviewSize lhs, PreviewSize rhs) {
        return Long.signum((long) lhs.width * lhs.height
                - (long) rhs.width * rhs.height);
    }

}

