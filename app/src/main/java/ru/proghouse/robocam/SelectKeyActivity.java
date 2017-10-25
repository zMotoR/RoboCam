package ru.proghouse.robocam;

//TODO: Enter key codes directly from the keyboard.

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

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

    private int AndroidKeyCodeToASCII(int keyCode) {
        int ascii = 0;
        if (keyCode >= KeyEvent.KEYCODE_A && keyCode <= KeyEvent.KEYCODE_Z)
            ascii = keyCode - KeyEvent.KEYCODE_A + 'A';
        else if (keyCode >= KeyEvent.KEYCODE_0 && keyCode <= KeyEvent.KEYCODE_9)
            ascii = keyCode - KeyEvent.KEYCODE_0 + '0';
        else if (keyCode == KeyEvent.KEYCODE_FORWARD_DEL) //Del
            ascii = 46;
        else if (keyCode == KeyEvent.KEYCODE_DEL) //Backspace
            ascii = 8;
        else if (keyCode == KeyEvent.KEYCODE_INSERT)
            ascii = 45;
        else if (keyCode == KeyEvent.KEYCODE_SHIFT_LEFT || keyCode == KeyEvent.KEYCODE_SHIFT_RIGHT)
            ascii = 16;
        else if (keyCode == KeyEvent.KEYCODE_CTRL_LEFT || keyCode == KeyEvent.KEYCODE_CTRL_RIGHT)
            ascii = 17;
        else if (keyCode == KeyEvent.KEYCODE_ALT_LEFT || keyCode == KeyEvent.KEYCODE_ALT_RIGHT)
            ascii = 18;
        else if (keyCode == KeyEvent.KEYCODE_NUM_LOCK)
            ascii = 144;
        else if (keyCode == KeyEvent.KEYCODE_SCROLL_LOCK)
            ascii = 145;
        else if (keyCode >= KeyEvent.KEYCODE_NUMPAD_0 && keyCode <= KeyEvent.KEYCODE_NUMPAD_9)
            ascii = keyCode - KeyEvent.KEYCODE_NUMPAD_0 + 96;
        else if (keyCode == KeyEvent.KEYCODE_BREAK)
            ascii = 19;
        else if (keyCode == KeyEvent.KEYCODE_GRAVE)
            ascii = 192;
        else if (keyCode == KeyEvent.KEYCODE_MINUS)
            ascii = 189;
        else if (keyCode == KeyEvent.KEYCODE_LEFT_BRACKET)
            ascii = 219;
        else if (keyCode == KeyEvent.KEYCODE_RIGHT_BRACKET)
            ascii = 221;
        else if (keyCode == KeyEvent.KEYCODE_BACKSLASH)
            ascii = 220;
        else if (keyCode == KeyEvent.KEYCODE_SEMICOLON)
            ascii = 186;
        else if (keyCode == KeyEvent.KEYCODE_APOSTROPHE)
            ascii = 222;
        else if (keyCode == KeyEvent.KEYCODE_COMMA)
            ascii = 188;
        else if (keyCode == KeyEvent.KEYCODE_PERIOD)
            ascii = 190;
        else if (keyCode == KeyEvent.KEYCODE_SLASH)
            ascii = 191;
        else if (keyCode == KeyEvent.KEYCODE_F2)
            ascii = 113;
        else if (keyCode == KeyEvent.KEYCODE_F4)
            ascii = 115;
        else if (keyCode == KeyEvent.KEYCODE_F7)
            ascii = 118;
        else if (keyCode == KeyEvent.KEYCODE_F8)
            ascii = 119;
        else if (keyCode == KeyEvent.KEYCODE_F9)
            ascii = 120;
        else if (keyCode == KeyEvent.KEYCODE_F10)
            ascii = 121;
        //else if (keyCode == KeyEvent.KEYCODE_MOVE_END)
        //    ascii = 35;
        //else if (keyCode == KeyEvent.KEYCODE_MOVE_HOME)
        //    ascii = 36;
        //else if (keyCode == KeyEvent.KEYCODE_PAGE_DOWN)
        //    ascii = 34;
        //else if (keyCode == KeyEvent.KEYCODE_PAGE_UP)
        //    ascii = 33;
        //else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN)
        //    ascii = 40;
        /*if (ascii == 0) {
            if (event.isAltPressed())
                ascii = 18;
            else if (Build.VERSION.SDK_INT >= 11 && event.isCtrlPressed())
                ascii = 17;
        }*/
        return ascii;
    }

    @Override
    public boolean onKeyDown (int keyCode, KeyEvent event) {
        int ascii = AndroidKeyCodeToASCII(keyCode);
        //KeyCharacterMap keyCharacterMap = KeyCharacterMap.load(event.getDeviceId());
        //keyCharacterMap.getDisplayLabel(keyCode);
        //Toast.makeText(this, Integer.toString(keyCode), Toast.LENGTH_LONG).show();
        /*Toast.makeText(this, "ASCII = " + Integer.toString(ascii) + " keyCode = " + Integer.toString(event.getKeyCode())
                + " unicode = " + Integer.toString(event.getUnicodeChar()),
                Toast.LENGTH_SHORT).show();
                */
        if (EV3KeyGroup.isKeyCodeValid(ascii)) {
            if (keys.contains(ascii))
                keys.remove(ascii);
            else
                keys.add(ascii);
            ArrayAdapter<KeyDescription> adapter = (ArrayAdapter<KeyDescription>)keyListView.getAdapter();
            adapter.notifyDataSetChanged();
            updateTitle();
            return true;
        } else
            return super.onKeyDown(keyCode, event);
    }
}
