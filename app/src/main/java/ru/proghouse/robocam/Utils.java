package ru.proghouse.robocam;

import android.app.Activity;
import android.os.Build;
import android.widget.Toast;

/**
 * Created by Alexey Valuev on 21.08.2016.
 */
public class Utils {
    public static void showError(Activity activity, int messageId, boolean finishActivity) {
        if (Build.VERSION.SDK_INT >= 11)
            ErrorDialog.newInstance(activity.getString(messageId), finishActivity)
                    .show(activity.getFragmentManager(), DefaultValue.FRAGMENT_DIALOG);
        else {
            Toast.makeText(activity, activity.getString(R.string.request_permission), Toast.LENGTH_SHORT).show();
            activity.finish();
        }
    }
}
