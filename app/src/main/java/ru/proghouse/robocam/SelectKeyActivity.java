package ru.proghouse.robocam;

import android.app.Activity;
import android.content.Intent;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import java.io.File;
import java.util.HashSet;

import ru.proghouse.robocam.drivers.EV3.EV3KeyGroup;

public class SelectKeyActivity extends AppCompatActivity {

    HashSet<Integer> keys = new HashSet<Integer>();
    ListView keyListView;
    TextView titleTextView;
    Activity thisActivity;
    String token;

    public static final String SETTINGS_KEYS = "SettingsKeys";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_select_key);
        thisActivity = this;
        token = getIntent().getStringExtra(Intent.EXTRA_REMOTE_INTENT_TOKEN);
        setTitle(getIntent().getStringExtra(Intent.EXTRA_TEXT));
        if (savedInstanceState != null)
            EV3KeyGroup.fromArray(savedInstanceState.getIntArray(SETTINGS_KEYS), keys);
        else
            EV3KeyGroup.fromArray(getIntent().getIntArrayExtra(Intent.EXTRA_STREAM), keys);
        titleTextView = (TextView) findViewById(R.id.titleTextView);
        ArrayAdapter<KeyDescription> adapter = new ArrayAdapter<KeyDescription>(this,
                R.layout.checked_listview_item, EV3KeyGroup.getKeyDescriptions()) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                if (convertView == null) {
                    LayoutInflater inflater = getLayoutInflater();
                    convertView = inflater.inflate(R.layout.checked_listview_item, parent, false);
                }
                final KeyDescription keyDesc = getItem(position);
                CheckBox checkBox = (CheckBox) convertView.findViewById(R.id.checkBox1);
                checkBox.setText(keyDesc.getDesc());
                checkBox.setTag(new Integer(keyDesc.getCode()));
                checkBox.setChecked(keys.contains(keyDesc.getCode()));
                checkBox.setOnClickListener(new View.OnClickListener() {

                    @Override
                    public void onClick(View view) {
                        CheckBox checkBox = (CheckBox) view;
                        Integer code = (Integer) checkBox.getTag();
                        if (checkBox.isChecked())
                            keys.add(code);
                        else
                            keys.remove(code);
                        updateTitle();
                    }
                });
                return convertView;
            }
        };

        keyListView = (ListView)findViewById(R.id.keyListView);
        keyListView.setAdapter(adapter);
        updateTitle();
    }

    public void updateTitle() {
        if (keys.size() == 0)
            titleTextView.setText(R.string.nothing_selected);
        else
            titleTextView.setText(getString(R.string.selected,
                    EV3KeyGroup.getKeyString(thisActivity, keys)));
    }

    public void onCancelButtonClick(View v){
        finish();
    }

    public void onDoneButtonClick(View v){
        Intent intent = new Intent(this, SelectKeyActivity.class);
        intent.putExtra(Intent.EXTRA_REMOTE_INTENT_TOKEN, token);
        intent.putExtra(Intent.EXTRA_TEXT, getTitle().toString());
        intent.putExtra(Intent.EXTRA_STREAM, EV3KeyGroup.toArray(keys));
        setResult(RESULT_OK, intent);
        finish();
    }

    @Override
    protected void onSaveInstanceState (Bundle outState) {
        outState.putIntArray(SETTINGS_KEYS, EV3KeyGroup.toArray(keys));
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onRestoreInstanceState (Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        EV3KeyGroup.fromArray(savedInstanceState.getIntArray(SETTINGS_KEYS), keys);
    }

}
