package ru.proghouse.robocam;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.content.DialogInterface;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;

/**
 * Created by Alexey Valuev on 10.08.2016.
 */
@TargetApi(Build.VERSION_CODES.HONEYCOMB)
public class ConfirmationDialog extends DialogFragment {
    public static final String MESSAGE_ID = "message_id";
    public static final String PERMISSIONS = "permissions";
    public static final String REQUEST_CODE = "request_code";
    public static final String FINISH_ACTIVITY = "finish_activity";

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        if (Build.VERSION.SDK_INT >= 17) {
            final Fragment parent = getParentFragment();
            Bundle bundle = getArguments();
            return new AlertDialog.Builder(getActivity())
                    .setMessage(bundle.getInt(MESSAGE_ID) /*R.string.request_camera_permission*/)
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            try {
                                Bundle bundle = getArguments();
                                ActivityCompat.requestPermissions(getActivity(),
                                        bundle.getStringArray(PERMISSIONS) /*new String[]{Manifest.permission.CAMERA}*/,
                                        bundle.getInt(REQUEST_CODE) /*MainActivity.REQUEST_CAMERA_PERMISSION*/);
                            } catch (Throwable e) {
                                e.printStackTrace();
                            }
                        }
                    })
                    .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            try {
                                Bundle bundle = getArguments();
                                if (bundle.getBoolean(FINISH_ACTIVITY)) {
                                    Activity activity = null;
                                    if (parent != null)
                                        activity = parent.getActivity();
                                    if (activity == null)
                                        activity = getActivity();
                                    if (activity != null) {
                                        activity.finish();
                                    }
                                }
                            } catch (Throwable e) {
                                e.printStackTrace();
                            }
                        }
                    })
                    .create();
        }
        return null;
    }
}
