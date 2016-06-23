package ru.proghouse.robocam;

import android.app.Activity;
import android.content.Context;
import android.graphics.Point;
import android.graphics.drawable.BitmapDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.Spinner;

import java.util.List;

/**
 * Created by Alexey Valuev on 23.04.2016.
 */
public class SpinnerHelper {
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
}
