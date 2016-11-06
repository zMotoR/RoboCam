package ru.proghouse.robocam;

import android.app.Activity;
import android.content.Context;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import java.util.HashSet;

import ru.proghouse.robocam.drivers.EV3.EV3KeyGroup;

/**
 * Created by Alexey Valuev on 06.11.2016.
 */
public class KeyCodeControl extends LinearLayout {

    private TextView textViewDescription;
    HashSet<Integer> keys;

    public KeyCodeControl(Context context) {
        super(context);
    }

    private String getKeyString(Activity activity) {
        String strKeys = "";
        if (keys != null)
            for (KeyDescription desc : EV3KeyGroup.getKeyDescriptions())
                if (keys.contains(desc.getCode()))
                    if (strKeys.isEmpty())
                        strKeys += desc.getDesc();
                    else
                        strKeys += ", " + desc.getDesc();
        if (strKeys.isEmpty())
            strKeys = activity.getString(R.string.nothing_selected);
        return strKeys;
    }

    public void updateKeyString(Activity activity) {
        textViewDescription.setText(getKeyString(activity));
    }

    public static KeyCodeControl createKeyCodeControl(Activity activity, LinearLayout layout,
                                                      HashSet<Integer> keys, int titleId) {
        KeyCodeControl controlLayout = new KeyCodeControl(activity);
        controlLayout.keys = keys;

        controlLayout.setOrientation(LinearLayout.HORIZONTAL);
        controlLayout.setWeightSum(1);
        layout.addView(controlLayout);

        LinearLayout textLayout = new LinearLayout(activity);
        textLayout.setOrientation(LinearLayout.VERTICAL);
        textLayout.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        controlLayout.addView(textLayout);

        SettingsActivityHelper.createTextViewTitle(activity, textLayout, titleId);
        controlLayout.textViewDescription = SettingsActivityHelper.createTextViewDescBP(
                activity, textLayout, controlLayout.getKeyString(activity));

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
}

