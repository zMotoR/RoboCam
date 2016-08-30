package ru.proghouse.robocam;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.os.Build;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Size;
import android.view.View;
import android.widget.AdapterView;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import ru.proghouse.robocam.drivers.RoboCamDriver;

public class ServerSettingsActivity extends AppCompatActivity {
    private Spinner spinnerCamera = null;
    android.hardware.camera2.CameraManager cameraManager = null;
    private int cameraId = 0;
    private String camera2Id = null;
    private Spinner spinnerPreviewSize = null;
    private int previewSize = -1;
    private int previewSizeSaved = -1;
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
    private List<String> cameraIds = new ArrayList<String>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_server_settings);

        //CAMERA ID
        List<String> cameras = getCameras();
        spinnerCamera = (Spinner)findViewById(R.id.spinnerCamera);
        SpinnerHelper.initSpinner(spinnerCamera, this, cameras, R.string.camera);
        SharedPreferences settings = getSharedPreferences(ExtraKey.APP_PREFERENCE, Context.MODE_PRIVATE);
        if (Build.VERSION.SDK_INT >= 21) {
            camera2Id = settings.getString(ExtraKey.CAMERA2_ID, null);
            if (camera2Id == null || !cameraIds.contains(camera2Id))
                camera2Id = cameraIds.get(0);
            spinnerCamera.setSelection(cameraIds.indexOf(camera2Id), true);
        }
        else {
            cameraId = settings.getInt(ExtraKey.CAMERA_ID, 0);
            if (cameraId < 0 || cameraId >= cameras.size())
                cameraId = 0;
            spinnerCamera.setSelection(cameraId, true);
        }
        spinnerCamera.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parent,
                                       View itemSelected, int selectedItemPosition, long selectedId) {
                setPreviewSizeAdapter(false);
            }
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        //PREVIEW SIZE
        if (savedInstanceState != null)
            previewSize = savedInstanceState.getInt(ExtraKey.PREVIEW_SIZE, -1);
        if (savedInstanceState == null || previewSize < 0)
            previewSize = settings.getInt(ExtraKey.PREVIEW_SIZE, -1);
        previewSizeSaved = settings.getInt(ExtraKey.PREVIEW_SIZE, -1);
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

    @Override
    protected void onSaveInstanceState (Bundle outState) {
        outState.putInt(ExtraKey.PREVIEW_SIZE, previewSize);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onRestoreInstanceState (Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        previewSize = savedInstanceState.getInt(ExtraKey.PREVIEW_SIZE, -1);
    }

    private void setPreviewSizeAdapter(boolean first) {
        List<PreviewSize> sizes = new ArrayList<PreviewSize>();
        List<String> previewSizes = getPreviewSizes(sizes);
        SpinnerHelper.initSpinner(spinnerPreviewSize, this, previewSizes, R.string.previewSize);
        if (first) {
            if (previewSize < 0) {
                PreviewSize firstSize = sizes.get(0);
                PreviewSize lastSize = sizes.get(sizes.size() - 1);
                int third = Math.round((float) previewSizes.size() / (float) 3.0);
                if (firstSize.width > lastSize.width || firstSize.height > lastSize.height)
                    previewSize = previewSizes.size() - third;
                else
                    previewSize = third - 1;
            }
        }
        if (previewSize < 0)
            previewSize = 0;
        if (previewSize >= previewSizes.size())
            previewSize = previewSizes.size() - 1;
        spinnerPreviewSize.setSelection(previewSize, true);
        spinnerPreviewSize.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parent,
                                       View itemSelected, int selectedItemPosition, long selectedId) {
                previewSize = selectedItemPosition;
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
        cameraIds.clear();
        if (Build.VERSION.SDK_INT >= 21) {
            try {
                cameraManager = (android.hardware.camera2.CameraManager) getSystemService(Context.CAMERA_SERVICE);
                for (String cameraId : cameraManager.getCameraIdList()) {
                    cameraIds.add(cameraId);
                    CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraId);
                    switch (characteristics.get(CameraCharacteristics.LENS_FACING)) {
                        case CameraCharacteristics.LENS_FACING_FRONT:
                            cameras.add(getString(R.string.camera_facing_front));
                            break;
                        case CameraCharacteristics.LENS_FACING_BACK:
                            cameras.add(getString(R.string.camera_facing_back));
                            break;
                        default: //CameraCharacteristics.LENS_FACING_EXTERNAL and other
                            cameras.add(getString(R.string.camera_facing_external));
                    }
                }
            } catch (Exception e) {
                Toast.makeText(this, e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
            }
        }
        else if (Build.VERSION.SDK_INT >= 9) {
            int cameraCount = Camera.getNumberOfCameras();
            for (int i = 0; i < cameraCount; i++) {
                Camera.CameraInfo info = new Camera.CameraInfo();
                Camera.getCameraInfo(i, info);
                if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT)
                    cameras.add(getString(R.string.camera_facing_front));
                else if (info.facing == Camera.CameraInfo.CAMERA_FACING_BACK)
                    cameras.add(getString(R.string.camera_facing_back));
                else
                    cameras.add(getString(R.string.camera_facing_external));
            }
        }
        else
            //Android 2.2 supports only the first back-facing camera on the device.
            cameras.add(getString(R.string.camera_facing_back));
        return cameras;
    }

    public List<String> getPreviewSizes(List<PreviewSize> sizes) {
        List<String> previewSizes = new ArrayList<String>();
        if (Build.VERSION.SDK_INT >= 21) {
            try {
                CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(camera2Id);
                StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                HashSet<Float> ratio = new HashSet<Float>();
                for (Size size : map.getOutputSizes(SurfaceTexture.class))
                    ratio.add((float)size.getHeight() / (float)size.getWidth());
                for (Size size : map.getOutputSizes(ImageFormat.YUV_420_888))
                    if (ratio.contains((float)size.getHeight() / (float)size.getWidth()))
                        sizes.add(new PreviewSize(size));
                Collections.sort(sizes, new CompareSizesByArea());
                for (PreviewSize size : sizes)
                    previewSizes.add("" + size.width + "x" + size.height);
            } catch (Exception e) {
                Toast.makeText(this, e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
            }
        } else {
            Camera camera;
            if (Build.VERSION.SDK_INT >= 9)
                camera = Camera.open(cameraId);
            else
                camera = Camera.open();
            try {
                Camera.Parameters parameters = camera.getParameters();
                for (Camera.Size previewSize : parameters.getSupportedPreviewSizes()) {
                    if (sizes != null)
                        sizes.add(new PreviewSize(previewSize));
                    previewSizes.add("" + previewSize.width + "x" + previewSize.height);
                }
            } finally {
                camera.release();
            }
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
        if (Build.VERSION.SDK_INT >= 21) {
            if (!cameraIds.get(spinnerCamera.getSelectedItemPosition()).equals(camera2Id)){
                getPreferenceEditor().putString(ExtraKey.CAMERA2_ID, cameraIds.get(spinnerCamera.getSelectedItemPosition()));
                changed = true;
            }
        } else {
            if (cameraId != spinnerCamera.getSelectedItemPosition()){
                getPreferenceEditor().putInt(ExtraKey.CAMERA_ID, spinnerCamera.getSelectedItemPosition());
                changed = true;
            }
        }
        if (previewSizeSaved != spinnerPreviewSize.getSelectedItemPosition()) {
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
