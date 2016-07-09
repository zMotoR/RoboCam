package ru.proghouse.robocam;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.Camera;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.support.v7.app.AppCompatActivity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

import ru.proghouse.robocam.drivers.RoboCamDriver;

public class ServerSettingsActivity extends AppCompatActivity {
    private Spinner spinnerCamera = null;
    private int cameraId = 0;
    private Spinner spinnerPreviewSize = null;
    private int previewSize = -1;
    private CustomAdapter adapterPreviewSize = null;
    private TextView textViewJpegQuality2 = null;
    private SeekBar seekBarJpegQuality = null;
    private int jpegQuality = 60;
    private SharedPreferences.Editor editor = null;
    private String driverName;
    private EditText editTextDriverName = null;
    private String driverPassword;
    private EditText editTextDriverPassword = null;
    private String spectatorName;
    private EditText editTextSpectatorName = null;
    private String spectatorPassword;
    private EditText editTextSpectatorPassword = null;
    private CheckBox checkBoxAllowSpectators = null;
    private boolean allowSpectators = true;
    private TextView textViewSpectatorName = null;
    private TextView textViewSpectatorPassword = null;
    //private ScrollView scrollView = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_server_settings);

        //CAMERA ID
        List<String> cameras = getCameras();

        spinnerCamera = (Spinner)findViewById(R.id.spinnerCamera);
        ArrayAdapter<?> adapterCamera = new CustomAdapter(this, spinnerCamera,
                R.layout.spinner_item, cameras, R.string.camera);
        adapterCamera.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerCamera.setAdapter(adapterCamera);
        spinnerCamera.setPromptId(R.string.camera);

        SharedPreferences settings = getSharedPreferences(ExtraKey.APP_PREFERENCE, Context.MODE_PRIVATE);
        cameraId = settings.getInt(ExtraKey.CAMERA_ID, 0);
        if (cameraId < 0 || cameraId >= cameras.size())
            cameraId = 0;
        spinnerCamera.setSelection(cameraId, true);

        spinnerCamera.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parent,
                                       View itemSelected, int selectedItemPosition, long selectedId) {
                /*cameraId = selectedItemPosition;
                List<String> previewSizes = getPreviewSizes();
                getPreferenceEditor().putInt(ExtraKey.CAMERA_ID, cameraId);
                if (previewSize >= previewSizes.size()) {
                    previewSize = previewSizes.size() - 1;
                    editor.putInt(ExtraKey.PREVIEW_SIZE, previewSize);
                }
                apply();*/
                setPreviewSizeAdapter(false);
            }

            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        //PREVIEW SIZE
        previewSize = settings.getInt(ExtraKey.PREVIEW_SIZE, -1);
        spinnerPreviewSize = (Spinner)findViewById(R.id.spinnerPreviewSize);
        setPreviewSizeAdapter(true);

        //JPEG QUALITY
        textViewJpegQuality2 = (TextView)findViewById(R.id.textViewJpegQuality2);
        seekBarJpegQuality = (SeekBar)findViewById(R.id.seekBarJpegQuality);
        jpegQuality = settings.getInt(ExtraKey.JPEG_QUALITY, 60);
        seekBarJpegQuality.incrementProgressBy(5);
        seekBarJpegQuality.setProgress(jpegQuality);
        textViewJpegQuality2.setText("" + jpegQuality + "%");
        seekBarJpegQuality.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                progress = progress / 5;
                progress = progress * 5;
                textViewJpegQuality2.setText("" + progress + "%");
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });

        //ADMIN NAME
        driverName = settings.getString(ExtraKey.DRIVER_NAME, DefaultValue.DRIVER_NAME);
        editTextDriverName = (EditText)findViewById(R.id.editTextDriverName);
        editTextDriverName.setText(driverName);
        /*editTextDriverName.setOnKeyListener(
                new View.OnKeyListener() {
                    public boolean onKey(View v, int keyCode, KeyEvent event) {
                        if (event.getAction() == KeyEvent.ACTION_DOWN
                                && driverName != editTextDriverName.getText().toString()) {
                            driverName = editTextDriverName.getText().toString();
                            getPreferenceEditor().putString(ExtraKey.ADMIN_NAME, driverName);
                            apply();
                        }
                        return false;
                    }
                }
        );*/

        //ADMIN PASSWORD
        driverPassword = settings.getString(ExtraKey.DRIVER_PASSWORD, DefaultValue.DRIVER_PASSWORD);
        editTextDriverPassword = (EditText)findViewById(R.id.editTextDriverPassword);
        editTextDriverPassword.setText(driverPassword);
        /*editTextDriverPassword.setOnKeyListener(
                new View.OnKeyListener() {
                    public boolean onKey(View v, int keyCode, KeyEvent event) {
                        if (event.getAction() == KeyEvent.ACTION_DOWN
                                && driverPassword != editTextDriverPassword.getText().toString()) {
                            driverPassword = editTextDriverPassword.getText().toString();
                            getPreferenceEditor().putString(ExtraKey.ADMIN_PASSWORD, driverPassword);
                            apply();
                        }
                        return false;
                    }
                }
        );*/

        //SPECTATOR NAME
        spectatorName = settings.getString(ExtraKey.SPECTATOR_NAME, DefaultValue.SPECTATOR_NAME);
        textViewSpectatorName = (TextView)findViewById(R.id.textViewSpectatorName);
        editTextSpectatorName = (EditText)findViewById(R.id.editTextSpectatorName);
        editTextSpectatorName.setText(spectatorName);
        /*editTextSpectatorName.setOnKeyListener(
                new View.OnKeyListener() {
                    public boolean onKey(View v, int keyCode, KeyEvent event) {
                        if (event.getAction() == KeyEvent.ACTION_DOWN
                                && spectatorName != editTextSpectatorName.getText().toString()) {
                            spectatorName = editTextSpectatorName.getText().toString();
                            getPreferenceEditor().putString(ExtraKey.SPECTATOR_NAME, spectatorName);
                            apply();
                        }
                        return false;
                    }
                }
        );*/

        //SPECTATOR PASSWORD
        spectatorPassword = settings.getString(ExtraKey.SPECTATOR_PASSWORD, DefaultValue.SPECTATOR_PASSWORD );
        textViewSpectatorPassword = (TextView)findViewById(R.id.textViewSpectatorPassword);
        editTextSpectatorPassword = (EditText)findViewById(R.id.editTextSpectatorPassword);
        editTextSpectatorPassword.setText(spectatorPassword);
        /*editTextSpectatorPassword.setOnKeyListener(
                new View.OnKeyListener() {
                    public boolean onKey(View v, int keyCode, KeyEvent event) {
                        if (event.getAction() == KeyEvent.ACTION_DOWN
                                && spectatorPassword != editTextSpectatorPassword.getText().toString()) {
                            spectatorPassword = editTextSpectatorPassword.getText().toString();
                            getPreferenceEditor().putString(ExtraKey.SPECTATOR_PASSWORD, spectatorPassword);
                            apply();
                        }
                        return false;
                    }
                }
        );*/

        //ALLOW SPECTATORS
        allowSpectators = settings.getBoolean(ExtraKey.ALLOW_SPECTATORS, DefaultValue.ALLOW_SPECTATORS);
        checkBoxAllowSpectators = (CheckBox)findViewById(R.id.checkBoxAllowSpectators);
        checkBoxAllowSpectators.setChecked(allowSpectators);
        checkBoxAllowSpectators.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                editTextSpectatorName.setEnabled(isChecked);
                editTextSpectatorPassword.setEnabled(isChecked);
                textViewSpectatorPassword.setEnabled(isChecked);
                textViewSpectatorName.setEnabled(isChecked);
            }
        });
        editTextSpectatorName.setEnabled(allowSpectators);
        editTextSpectatorPassword.setEnabled(allowSpectators);
        textViewSpectatorPassword.setEnabled(allowSpectators);
        textViewSpectatorName.setEnabled(allowSpectators);

        /*InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(editTextDriverName.getWindowToken(),
                InputMethodManager.HIDE_NOT_ALWAYS);*/

        /*scrollView = (ScrollView)findViewById(R.id.scrollView);
        scrollView.post(new Runnable() {
            @Override
            public void run() {
                scrollView.scrollTo(0, 0);
            }
        });*/

    }

    private void setPreviewSizeAdapter(boolean first) {
        List<Camera.Size> sizes = new ArrayList<Camera.Size>();
        List<String> previewSizes = getPreviewSizes(sizes);

        adapterPreviewSize = new CustomAdapter(this, spinnerPreviewSize, R.layout.spinner_item,
                previewSizes, R.string.previewSize);
        adapterPreviewSize.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerPreviewSize.setAdapter(adapterPreviewSize);
        spinnerPreviewSize.setPromptId(R.string.previewSize);

        if (first) {
            if (previewSize < 0) {
                Camera.Size firstSize = sizes.get(0);
                Camera.Size lastSize = sizes.get(sizes.size() - 1);
                int third = Math.round((float) previewSizes.size() / (float) 3.0);
                if (firstSize.width > lastSize.width || firstSize.height > lastSize.height)
                    previewSize = previewSizes.size() - third;
                else
                    previewSize = third - 1;
            }
            if (previewSize < 0)
                previewSize = 0;
            if (previewSize >= previewSizes.size())
                previewSize = previewSizes.size() - 1;
            spinnerPreviewSize.setSelection(previewSize, true);
        }
        else {
            int curPreviewSize = spinnerPreviewSize.getSelectedItemPosition();
            if (curPreviewSize < 0 || curPreviewSize >= previewSizes.size())
                curPreviewSize = 0;
            spinnerPreviewSize.setSelection(curPreviewSize, true);
        }

        spinnerPreviewSize.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parent,
                                       View itemSelected, int selectedItemPosition, long selectedId) {
                /*previewSize = selectedItemPosition;
                getPreferenceEditor().putInt(ExtraKey.PREVIEW_SIZE, previewSize);
                apply();*/
            }

            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    private SharedPreferences.Editor getPreferenceEditor() {
        if (editor == null) {
            SharedPreferences settings = getSharedPreferences(ExtraKey.APP_PREFERENCE, Context.MODE_PRIVATE);
            editor = settings.edit();
        }
        return editor;
    }

    private void apply() {
        if (editor != null)
            if (Build.VERSION.SDK_INT >= 9)
                editor.apply();
            else
                editor.commit();
    }

    /*public class CustomAdapter extends ArrayAdapter {
        private Context context;
        private int resource;
        private List<String> objects;
        private int titleResId;

        public CustomAdapter(Context context, int resource, List<String> objects, int titleResId) {
            super(context, resource, objects);
            this.context = context;
            this.resource = resource;
            this.objects = objects;
            this.titleResId = titleResId;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                LayoutInflater inflater = getLayoutInflater();
                convertView = inflater.inflate(resource, parent, false);
            }

            //if (convertView == null)
            //    convertView = View.inflate(context, resource, null);
            TextView textView = (TextView)convertView.findViewById(android.R.id.text1);
            if (textView != null)
                textView.setText(objects.get(position));
            else {
                TextView textView1 = (TextView)convertView.findViewById(R.id.textView1);
                TextView textView2 = (TextView)convertView.findViewById(R.id.textView2);
                textView1.setText(titleResId);
                textView2.setText(objects.get(position));
            }
            return convertView;
        }
    }*/

    /*@Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_global_settings, menu);
        return true;
    }*/

    /*@Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }*/

    public List<String> getCameras() {
        List<String> cameras = new ArrayList<String>();
        if (Build.VERSION.SDK_INT >= 9) {
            int cameraCount = Camera.getNumberOfCameras();
            for (int i = 0; i < cameraCount; i++) {
                Camera.CameraInfo info = new Camera.CameraInfo();
                Camera.getCameraInfo(i, info);
                if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT)
                    cameras.add(getString(R.string.camera_facing_front));
                else if (info.facing == Camera.CameraInfo.CAMERA_FACING_BACK)
                    cameras.add(getString(R.string.camera_facing_back));
            }
        }
        else
            //Android 2.2 supports only the first back-facing camera on the device.
            cameras.add(getString(R.string.camera_facing_back));
        return cameras;
    }

    public List<String> getPreviewSizes(List<Camera.Size> sizes) {
        List<String> previewSizes = new ArrayList<String>();
        Camera camera;
        if (Build.VERSION.SDK_INT >= 9)
            camera = Camera.open(cameraId);
        else
            camera = Camera.open();
        try {
            Camera.Parameters parameters = camera.getParameters();
            for (Camera.Size previewSize : parameters.getSupportedPreviewSizes()) {
                if (sizes != null)
                    sizes.add(previewSize);
                previewSizes.add("" + previewSize.width + "x" + previewSize.height);
            }
        }
        finally {
            camera.release();
        }
        return previewSizes;
    }

    public void onCancelButtonClick(View v){
        finish();
    }

    public void onSaveButtonClick(View v){
        boolean changed = false;
        if (driverName != editTextDriverName.getText().toString()) {
            getPreferenceEditor().putString(ExtraKey.DRIVER_NAME, editTextDriverName.getText().toString());
            changed = true;
        }
        if (driverPassword != editTextDriverPassword.getText().toString()) {
            getPreferenceEditor().putString(ExtraKey.DRIVER_PASSWORD, editTextDriverPassword.getText().toString());
            changed = true;
        }
        if (spectatorName != editTextSpectatorName.getText().toString()) {
            getPreferenceEditor().putString(ExtraKey.SPECTATOR_NAME, editTextSpectatorName.getText().toString());
            changed = true;
        }
        if (spectatorPassword != editTextSpectatorPassword.getText().toString()) {
            getPreferenceEditor().putString(ExtraKey.SPECTATOR_PASSWORD, editTextSpectatorPassword.getText().toString());
            changed = true;
        }
        if (cameraId != spinnerCamera.getSelectedItemPosition()){
            getPreferenceEditor().putInt(ExtraKey.CAMERA_ID, spinnerCamera.getSelectedItemPosition());
            changed = true;
        }
        if (previewSize != spinnerPreviewSize.getSelectedItemPosition()) {
            getPreferenceEditor().putInt(ExtraKey.PREVIEW_SIZE, spinnerPreviewSize.getSelectedItemPosition());
            changed = true;
        }
        int progress = seekBarJpegQuality.getProgress() / 5;
        progress = progress * 5;
        if (progress == 0)
            progress = 5;
        if (progress != jpegQuality) {
            getPreferenceEditor().putInt(ExtraKey.JPEG_QUALITY, progress);
            changed = true;
        }
        if (allowSpectators != checkBoxAllowSpectators.isChecked()) {
            getPreferenceEditor().putBoolean(ExtraKey.ALLOW_SPECTATORS, checkBoxAllowSpectators.isChecked());
            changed = true;
        }
        if (changed)
            apply();
        Toast.makeText(this, R.string.settings_were_saved, Toast.LENGTH_SHORT).show();
        finish();
    }
}
