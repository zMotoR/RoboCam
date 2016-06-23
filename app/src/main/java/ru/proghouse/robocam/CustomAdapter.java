package ru.proghouse.robocam;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;

import java.util.List;

/**
 * Created by Alexey Valuev on 27.03.2016.
 */
public class CustomAdapter extends ArrayAdapter {
    public Activity getActivity() {
        return activity;
    }

    private Activity activity;
    private Spinner spinner;
    private int resource;
    private int dd_resource;

    public List<String> getObjects() {
        return objects;
    }

    private List<String> objects;

    public void setTitleResId(int titleResId) {
        this.titleResId = titleResId;
    }

    private int titleResId;

    public CustomAdapter(Activity activity, Spinner spinner, int resource, List<String> objects,
                         int titleResId) {
        super(activity, resource, objects);
        this.activity = activity;
        this.spinner = spinner;
        this.resource = resource;
        this.objects = objects;
        this.titleResId = titleResId;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            LayoutInflater inflater = activity.getLayoutInflater();
            convertView = inflater.inflate(resource, parent, false);
        }

            /*if (convertView == null)
                convertView = View.inflate(context, resource, null);*/
        TextView textView = (TextView)convertView.findViewById(android.R.id.text1);
        if (textView != null)
            textView.setText(objects.get(position));
        else {
            TextView textView1 = (TextView)convertView.findViewById(R.id.textView1);
            TextView textView2 = (TextView)convertView.findViewById(R.id.textView2);
            if (textView2 == null)
                textView1.setText(objects.get(position));
            else {
                textView1.setText(titleResId);
                textView2.setText(objects.get(position));
            }
        }
        return convertView;
    }

    @Override
    public void setDropDownViewResource(int dd_resource) {
        this.dd_resource = dd_resource;
        super.setDropDownViewResource(dd_resource);
    }

    @Override
    public View getDropDownView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            LayoutInflater inflater = activity.getLayoutInflater();
            convertView = inflater.inflate(dd_resource, parent, false);
        }

        TextView textView = (TextView)convertView.findViewById(android.R.id.text1);
        if (textView != null)
            textView.setText(objects.get(position));
        else {
            TextView textView1 = (TextView)convertView.findViewById(R.id.textView1);
            TextView textView2 = (TextView)convertView.findViewById(R.id.textView2);
            if (textView2 == null) {
                textView1.setText(objects.get(position));
                if (spinner != null && spinner.getSelectedItemPosition() == position)
                    textView1.setBackgroundColor(Color.LTGRAY);
                else
                    textView1.setBackgroundColor(Color.WHITE);
            }
            else {
                textView1.setText(titleResId);
                textView2.setText(objects.get(position));
            }
        }
        return convertView;
    }
}
