package ru.proghouse.robocam;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.os.Build;
import android.os.Bundle;

/**
 * Created by Alexey Valuev on 10.08.2016.
 */
@TargetApi(Build.VERSION_CODES.HONEYCOMB)
public class ErrorDialog extends DialogFragment {

    private static final String ARG_MESSAGE = "message";
    private static final String ARG_FINISH_ACTIVITY = "finish_activity";

    public static ErrorDialog newInstance(String message, boolean finishActivity) {
        ErrorDialog dialog = new ErrorDialog();
        Bundle args = new Bundle();
        args.putString(ARG_MESSAGE, message);
        args.putBoolean(ARG_FINISH_ACTIVITY, finishActivity);
        dialog.setArguments(args);
        return dialog;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        final Activity activity = getActivity();
        final boolean finishActivity = getArguments().getBoolean(ARG_FINISH_ACTIVITY);
        return new AlertDialog.Builder(activity)
                .setMessage(getArguments().getString(ARG_MESSAGE))
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        if (finishActivity)
                            activity.finish();
                    }
                })
                .create();
    }

}
