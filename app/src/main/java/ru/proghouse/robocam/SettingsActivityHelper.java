package ru.proghouse.robocam;

import android.app.Activity;
import android.content.Context;
import android.graphics.Point;
import android.graphics.drawable.BitmapDrawable;
import android.support.v7.widget.AppCompatButton;
import android.util.TypedValue;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.RelativeLayout;
import android.widget.Spinner;
import android.widget.TextView;
//import android.support.v7.appcompat.R;

import java.util.Dictionary;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import ru.proghouse.robocam.drivers.EV3.EV3KeyGroup;

/**
 * Created by Alexey Valuev on 23.04.2016.
 */
public class SettingsActivityHelper {
    public static void initSpinner(Spinner spinner, Activity activity, List<String> options,
                             int titleResId, int dropDownStyle) {
        ArrayAdapter<?> adapter = new CustomAdapter(activity, spinner,
                R.layout.spinner_item, options, titleResId);
        adapter.setDropDownViewResource(dropDownStyle);
        spinner.setAdapter(adapter);
        //spinner.setPromptId(titleResId);
        //http://stackoverflow.com/questions/13560432/android-change-spinner-dropdown-view
        /*spinner.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {

                p = new Point();
                p.x = location[0]+(v.getHeight());
                p.y = location[1]+v.getHeight();

                if (p != null)
                    showPopup(statusActivity.this, p);

                System.out.println("show popup");
            }
        });*/
        //spinner.setPopupBackgroundResource(R.layout.spinner_item);
        //spinner.setBackground(null);
    }

    public static void initSpinner(Spinner spinner, Activity activity, List<String> options,
                             int titleResId) {
        initSpinner(spinner, activity, options, titleResId,
                R.layout.spinner_dropdown_item/*android.R.layout.simple_spinner_dropdown_item*/);
    }

    // The method that displays the popup.
    /*private void showPopup(final Activity context, Point p) {
        int popupWidth = 300;
        int popupHeight = 500;

        // Inflate the popup_layout.xml
        LinearLayout viewGroup = (LinearLayout) context.findViewById(R.id.popup);
        LayoutInflater layoutInflater = (LayoutInflater) context
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View layout = layoutInflater.inflate(R.layout.popup_layout, viewGroup);

        // Creating the PopupWindow
        popup = new PopupWindow(context);
        popup.setContentView(layout);
        popup.setWidth(popupWidth);
        popup.setHeight(popupHeight);
        popup.setFocusable(true);

        // Some offset to align the popup a bit to the right, and a bit down, relative to button's position.
        int OFFSET_X = 00;
        int OFFSET_Y = 00;

        // Clear the default translucent background
        popup.setBackgroundDrawable(new BitmapDrawable());

        // Displaying the popup at the specified location, + offsets.
        popup.showAtLocation(layout, Gravity.NO_GRAVITY, p.x + OFFSET_X, p.y + OFFSET_Y);
        ((TextView)layout.findViewById(R.id.textView2)).setClickable(true);
        ((TextView)layout.findViewById(R.id.textView3)).setClickable(true);
        ((TextView)layout.findViewById(R.id.textView4)).setClickable(true);
        ((TextView)layout.findViewById(R.id.textView5)).setClickable(true);
        ((TextView)layout.findViewById(R.id.textView6)).setClickable(true);
        ((TextView)layout.findViewById(R.id.textView7)).setClickable(true);
        ((TextView)layout.findViewById(R.id.textView8)).setClickable(true);
        ((TextView)layout.findViewById(R.id.textView9)).setClickable(true);

    }*/

    public static CheckBox createCheckBox(Activity activity, LinearLayout layout, boolean value,
                                    int titleId, int checkboxPadding) {
        ContextThemeWrapper newContext = new ContextThemeWrapper(activity, R.style.SettingsCheckBox);
        CheckBox checkBox = new CheckBox(newContext, null, R.style.SettingsCheckBox);
        checkBox.setHeight(Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 56, activity.getResources().getDisplayMetrics())));
        checkBox.setChecked(value);
        checkBox.setText(titleId);
        //http://stackoverflow.com/questions/4037795/android-spacing-between-checkbox-and-text
        checkBox.setPadding(checkboxPadding, checkBox.getPaddingTop(),
                checkBox.getPaddingRight(), checkBox.getPaddingBottom());
        layout.addView(checkBox);
        return checkBox;
    }

    public static Spinner createSpinner(Activity activity, LinearLayout layout, List<String> objects,
                                  int position, int titleId, LinearLayout[] _spinnerLayout) {
        LinearLayout spinnerLayout = new LinearLayout(activity);
        if (_spinnerLayout != null && _spinnerLayout.length > 0)
            _spinnerLayout[0] = spinnerLayout;
        spinnerLayout.setOrientation(LinearLayout.HORIZONTAL);
        spinnerLayout.setWeightSum(1);
        layout.addView(spinnerLayout);

        ContextThemeWrapper newContext  = new ContextThemeWrapper(activity, R.style.SettingsSpinner);
        Spinner spinner = new Spinner(newContext, null, R.style.SettingsSpinner);
        SettingsActivityHelper.initSpinner(spinner, activity, objects, titleId);
        spinner.setSelection(position);
        spinner.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        spinnerLayout.addView(spinner);

        ImageView triangleView = new ImageView(activity);
        triangleView.setBackgroundResource(R.drawable.abc_spinner);
        triangleView.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
        spinnerLayout.addView(triangleView);
        return spinner;
    }

    public static ImageView createSeparator(Activity activity, LinearLayout layout) {
        ImageView separator = new ImageView(activity);
        separator.setImageResource(R.drawable.spacer_small);
        layout.addView(separator);
        return separator;
    }

    public static ImageView createVerticalSeparator(Activity activity, LinearLayout layout) {
        ContextThemeWrapper newContext  = new ContextThemeWrapper(activity,
                R.style.VerticalSeparator);
        ImageView separator = new ImageView(newContext, null, R.style.VerticalSeparator);
        //separator.setImageResource(R.drawable.spacer_small);
        separator.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        layout.addView(separator);
        return separator;
    }

    public static TextView createTextViewTitle(Activity activity, LinearLayout layout, int titleId) {
        ContextThemeWrapper newContext  = new ContextThemeWrapper(activity,
                R.style.SettingsSectionEditTextTitle);
        TextView textView = new TextView(newContext, null,
                R.style.SettingsSectionEditTextTitle);
        textView.setText(activity.getString(titleId));
        layout.addView(textView);
        return textView;
    }

    public static TextView createTextViewDesc(Activity activity, LinearLayout layout, int descId) {
        ContextThemeWrapper newContext  = new ContextThemeWrapper(activity,
                R.style.SettingsSectionEditTextDesc);
        TextView textView = new TextView(newContext, null,
                R.style.SettingsSectionEditTextDesc);
        textView.setText(activity.getString(descId));
        layout.addView(textView);
        return textView;
    }

    public static TextView createTextViewDescBP(Activity activity, LinearLayout layout, String desc) {
        ContextThemeWrapper newContext  = new ContextThemeWrapper(activity,
                R.style.SettingsSectionEditTextDescBP);
        TextView textView = new TextView(newContext, null,
                R.style.SettingsSectionEditTextDescBP);
        textView.setText(desc);
        layout.addView(textView);
        return textView;
    }

    public static EditText createEditTextFloat(Activity activity, LinearLayout layout, float value,
                                         int titleId, int descId) {
        if (titleId != 0)
            createTextViewTitle(activity, layout, titleId);
        if (descId != 0)
            createTextViewDesc(activity, layout, descId);
        ContextThemeWrapper newContext = new ContextThemeWrapper(activity, R.style.SettingsEditTextFloat);
        EditText editText = new EditText(newContext, null, R.style.SettingsEditTextFloat);
        editText.setText(Float.toString(value));
        layout.addView(editText);
        return editText;
    }

    public static EditText createEditTextInteger(Activity activity, LinearLayout layout, int value,
                                           int titleId, int descId) {
        if (titleId != 0)
            createTextViewTitle(activity, layout, titleId);
        if (descId != 0)
            createTextViewDesc(activity, layout, descId);
        ContextThemeWrapper newContext = new ContextThemeWrapper(activity, R.style.SettingsEditTextInteger);
        EditText editText = new EditText(newContext, null, R.style.SettingsEditTextInteger);
        editText.setText(Integer.toString(value));
        layout.addView(editText);
        return editText;
    }

    public static EditText createEditText(Activity activity, LinearLayout layout, String value,
                                                 int titleId, int descId) {
        if (titleId != 0)
            createTextViewTitle(activity, layout, titleId);
        if (descId != 0)
            createTextViewDesc(activity, layout, descId);
        ContextThemeWrapper newContext = new ContextThemeWrapper(activity, R.style.SettingsEditText);
        EditText editText = new EditText(newContext, null, R.style.SettingsEditText);
        editText.setText(value);
        layout.addView(editText);
        return editText;
    }

    public static Button createButton(Activity activity, LinearLayout layout, int textId) {
        //ContextThemeWrapper newContext = new ContextThemeWrapper(activity, R.style.SettingsActionButton);
        ContextThemeWrapper newContext = new ContextThemeWrapper(activity,
                R.style.SettingsActionButton);
        //Button button = new Button(newContext, null, R.style.SettingsActionButton);
        Button button = new AppCompatButton(newContext, null,
                R.style.SettingsActionButton);
        //Button button = new Button(newContext, null,
        //        android.support.v7.widget.AppCompatButton android.support.v7.appcompat.R.style.Widget_AppCompat_ActionButton);
        button.setText(activity.getString(textId));
        button.setFocusable(false);
        layout.addView(button);
        return button;
    }

}
