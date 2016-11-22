package ru.proghouse.robocam;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.AttributeSet;
import android.view.ContextThemeWrapper;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import ru.proghouse.robocam.drivers.EV3.EV3KeyGroup;

/**
 * Created by Alexey Valuev on 06.11.2016.
 */
public class KeyCodeControl extends LinearLayout {

    private TextView textViewTitle;
    private TextView textViewDescription;
    HashSet<Integer> keys;
    String keyGroupName;

    @TargetApi(11)
    public KeyCodeControl(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public KeyCodeControl(Context context) {
        super(context);
    }

    public void updateKeyString(Activity activity) {
        textViewDescription.setText(EV3KeyGroup.getKeyString(activity, keys));
    }

    public String getTitle() {
        return textViewTitle.getText().toString();
    }

    public static KeyCodeControl createKeyCodeControl(Activity activity, LinearLayout layout,
                                                      HashSet<Integer> keys, int titleId,
                                                      String keyGroupName) {
        KeyCodeControl controlLayout;
        if (Build.VERSION.SDK_INT >= 11) {
            ContextThemeWrapper newContext = new ContextThemeWrapper(activity, R.style.KeyCodeControl);
            controlLayout = new KeyCodeControl(newContext, null, R.style.KeyCodeControl);
        }
        else
            controlLayout = new KeyCodeControl(activity);
        controlLayout.keys = keys;
        controlLayout.keyGroupName = keyGroupName;
        controlLayout.setPadding(0, 0, 0, 0);

        controlLayout.setOrientation(LinearLayout.HORIZONTAL);
        controlLayout.setWeightSum(1);
        layout.addView(controlLayout);

        LinearLayout textLayout = new LinearLayout(activity);
        textLayout.setOrientation(LinearLayout.VERTICAL);
        textLayout.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        controlLayout.addView(textLayout);

        controlLayout.textViewTitle = SettingsActivityHelper.createTextViewTitle(
                activity, textLayout, titleId);
        controlLayout.textViewDescription = SettingsActivityHelper.createTextViewDescBP(
                activity, textLayout, EV3KeyGroup.getKeyString(activity, controlLayout.keys));

        RelativeLayout imageLayout = new RelativeLayout(activity);
        imageLayout.setLayoutParams(new RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
        controlLayout.addView(imageLayout);

        RelativeLayout.LayoutParams imageLayoutParams = new RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        imageLayoutParams.addRule(RelativeLayout.CENTER_VERTICAL);

        ImageView overflowView = new ImageView(activity);
        overflowView.setBackgroundResource(R.drawable.overflow);
        overflowView.setLayoutParams(imageLayoutParams);
        imageLayout.addView(overflowView);

        return controlLayout;
    }

    public void showKeySelector(Activity activity, int requestCode) {
        Intent intent = new Intent(activity, SelectKeyActivity.class);
        //id
        intent.putExtra(Intent.EXTRA_REMOTE_INTENT_TOKEN, keyGroupName);
        //title
        intent.putExtra(Intent.EXTRA_TEXT, getTitle());
        //key codes
        intent.putExtra(Intent.EXTRA_STREAM, EV3KeyGroup.toArray(keys));
        activity.startActivityForResult(intent, requestCode);
    }

    public void setKeys(Activity activity, int[] keys) {
        if (keys == null)
            this.keys = null;
        else {
            if (this.keys == null)
                this.keys = new HashSet<Integer>();
            EV3KeyGroup.fromArray(keys, this.keys);
        }
        updateKeyString(activity);
    }
}

