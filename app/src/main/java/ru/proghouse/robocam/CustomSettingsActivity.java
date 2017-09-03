package ru.proghouse.robocam;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.Toast;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import ru.proghouse.robocam.drivers.RoboCamDriver;

public class CustomSettingsActivity extends AppCompatActivity implements View.OnClickListener {

    private static final String SETTINGS_FILE_NAME = "SettingsFileName";
    private static final String SETTINGS_XML = "SettingsXml";

    private static final int MI_SEND_SETTINGS = -2;
    private static final int MI_EXPORT_SETTINGS = -3;
    private static final int MI_COPY_SETTINGS = -4;
    private static final int KEY_CODE_REQUEST = 1;

    private static final int REQUEST_WRITE_EXTERNAL_STORAGE_PERMISSION_FOR_EXPORT = 1;
    private static final int REQUEST_WRITE_EXTERNAL_STORAGE_PERMISSION_FOR_COPY = 2;

    private int CHECKBOX_PADDING = 0;
    private String settingsFileName;

    private ImageButton buttonOverflow = null;

    private EditText editTextBotName = null;
    private EditText editTextBotDesc = null;
    private CheckBox checkBoxShowDebugInfo = null;
    private CheckBox checkBoxHideJoysticks = null;
    private CheckBox checkBoxActive = null;
    private EditText editTextCallsign = null;
    private EditText editTextResponse = null;
    private LinearLayout keygroupLayout = null;
    private KeyCodeControl keysControl = null;
    private HashSet<Integer> keyCodes = new HashSet<Integer>();

    private JoystickComponents[] joystickComponents = new JoystickComponents[4];

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_custom_settings);

        buttonOverflow = (ImageButton)findViewById(R.id.buttonOverflow);
        buttonOverflow.setOnClickListener(this);
        registerForContextMenu(buttonOverflow);

        if (Build.VERSION.SDK_INT < 17) {
            CheckBox rb = new CheckBox(this);
            CHECKBOX_PADDING = rb.getPaddingLeft();
        }

        editTextBotName = (EditText)findViewById(R.id.editTextBotName);
        editTextBotDesc = (EditText)findViewById(R.id.editTextDesc);
        checkBoxShowDebugInfo = (CheckBox)findViewById(R.id.checkBoxShowDebugInfo);
        checkBoxHideJoysticks = (CheckBox)findViewById(R.id.checkBoxHideJoysticks);
        editTextCallsign = (EditText)findViewById(R.id.editTextCallsign);
        editTextResponse = (EditText)findViewById(R.id.editTextResponse);

        //Keys
        checkBoxActive = (CheckBox)findViewById(R.id.checkBoxActive);
        keygroupLayout = (LinearLayout)findViewById(R.id.keygroupLayout);
        keysControl = KeyCodeControl.createKeyCodeControl(this, keygroupLayout,
                keyCodes, R.string.keys, getString(R.string.keys));
        keysControl.setOnClickListener(this);

        checkBoxActive.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                keygroupLayout.setVisibility(isChecked ? View.VISIBLE : View.GONE);
            }
        });

        settingsFileName = this.getIntent().getStringExtra(SETTINGS_FILE_NAME);
        if (settingsFileName == null || settingsFileName.equals("")) {
            String savedSettingsFileName = null;
            if (savedInstanceState != null)
                savedSettingsFileName = savedInstanceState.getString(SETTINGS_FILE_NAME);
            if (savedSettingsFileName == null || savedSettingsFileName.equals("")) {
                SimpleDateFormat format = new SimpleDateFormat("yyyyMMddHHmmsszzz", Locale.ENGLISH);
                settingsFileName = "Custom_" + format.format(new Date()) + ".xml";
            } else
                settingsFileName = savedSettingsFileName;
        }

        for (int i = 0; i < 4; i++)
            joystickComponents[i] = new JoystickComponents(this, i);

        String savedSettings = null;
        if (savedInstanceState != null)
            savedSettings = savedInstanceState.getString(SETTINGS_XML);
        if (savedSettings != null && !savedSettings.equals("")) {
            try {
                loadSettings(savedSettings);
            } catch (Exception e) {
                Toast.makeText(this, getString(R.string.error_while_opening_settings,
                        e.getLocalizedMessage()), Toast.LENGTH_LONG).show();
            }
        } else {
            File settingsFile = getSettingsFile();
            if (settingsFile.exists()) {
                try {
                    loadSettings(settingsFile);
                } catch (Exception e) {
                    Toast.makeText(this, getString(R.string.error_while_opening_settings_file,
                            settingsFileName, e.getLocalizedMessage()), Toast.LENGTH_LONG).show();
                }
            }
        }
    }

    @Override
    protected void onSaveInstanceState (Bundle outState) {
        outState.putString(SETTINGS_FILE_NAME, settingsFileName);
        outState.putString(SETTINGS_XML, getCurrentSettingsXml());
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onRestoreInstanceState (Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        settingsFileName = savedInstanceState.getString(SETTINGS_FILE_NAME);
    }

    private String getCurrentSettingsXml() {
        try {
            Document xml = createCurrentSettingsXml();
            //Saving the XML
            DOMSource source = new DOMSource(xml);
            StringWriter writer = new StringWriter();
            StreamResult result = new StreamResult(writer);
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();
            transformer.transform(source, result);
            return writer.toString();
        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.error_while_creating_settings_xml,
                    e.getLocalizedMessage()), Toast.LENGTH_LONG).show();
        }
        return null;
    }

    private Document createCurrentSettingsXml() throws ParserConfigurationException {
        //Creating the XML
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        DocumentBuilder db = dbf.newDocumentBuilder();
        Document xml = db.newDocument();
        xml.appendChild(xml.createElement("Custom"));
        Element rootElement = xml.getDocumentElement();
        rootElement.setAttribute("Name", editTextBotName.getText().toString());
        rootElement.setAttribute("Description", editTextBotDesc.getText().toString());
        rootElement.setAttribute("Callsign", editTextCallsign.getText().toString());
        rootElement.setAttribute("Response", editTextResponse.getText().toString());
        if (!checkBoxShowDebugInfo.isChecked())
            rootElement.setAttribute("ShowDebugInfo", "0");
        if (!checkBoxHideJoysticks.isChecked())
            rootElement.setAttribute("HideJoysticks", "0");
        int index = 0;
        for (JoystickComponents joystickComponent : joystickComponents) {
            Element joystickElement = xml.createElement("Joystick");
            rootElement.appendChild(joystickElement);
            joystickElement.setAttribute("Index", Integer.toString(index));
            if (joystickComponent.visibilityCheckBox.isChecked())
                joystickElement.setAttribute("Visible", "1");
            switch (joystickComponent.shapeSpinner.getSelectedItemPosition()) {
                case 0:
                    joystickElement.setAttribute("Shape", RoboCamDriver.JOYSTICK_SHAPE_VERTICAL);
                    break;
                case 1:
                    joystickElement.setAttribute("Shape", RoboCamDriver.JOYSTICK_SHAPE_HORIZONTAL);
                    break;
                case 2: //Default value
                    if (joystickComponent.visibilityCheckBox.isChecked())
                        joystickElement.setAttribute("Shape", RoboCamDriver.JOYSTICK_SHAPE_CIRCULAR);
                    break;
                case 3:
                    joystickElement.setAttribute("Shape", RoboCamDriver.JOYSTICK_SHAPE_QUADRATIC);
                    break;
                case 4:
                    joystickElement.setAttribute("Shape", RoboCamDriver.JOYSTICK_SHAPE_ARROWS);
                    break;
                case 5:
                    joystickElement.setAttribute("Shape", RoboCamDriver.JOYSTICK_SHAPE_VERTICAL_ARROWS);
                    break;
                case 6:
                    joystickElement.setAttribute("Shape", RoboCamDriver.JOYSTICK_SHAPE_HORIZONTAL_ARROWS);
                    break;
            }
            if (joystickComponent.behavior1Spinner.getSelectedItemPosition() != RoboCamDriver.JOYSTICK_BEHAVIOR_RETURN_TO_ZERO)
                joystickElement.setAttribute("Behavior0",
                        Integer.toString(joystickComponent.behavior1Spinner.getSelectedItemPosition()));
            if (joystickComponent.behavior2Spinner.getSelectedItemPosition() != RoboCamDriver.JOYSTICK_BEHAVIOR_RETURN_TO_ZERO)
                joystickElement.setAttribute("Behavior1",
                        Integer.toString(joystickComponent.behavior2Spinner.getSelectedItemPosition()));
            index++;
        }
        Element keyGroupElement = xml.createElement("KeyGroup");
        rootElement.appendChild(keyGroupElement);
        if (checkBoxActive.isChecked())
            keyGroupElement.setAttribute("Active", "1");
        if (keysControl.keys != null)
            for (Integer keyCode : keysControl.keys) {
                Element keyCodeElement = xml.createElement("Key");
                keyGroupElement.appendChild(keyCodeElement);
                keyCodeElement.setTextContent(keyCode.toString());
            }
        return xml;
    }

    private File getSettingsFile() {
        File robotDir = Utils.getRobotDir(this);
        return new File(robotDir, settingsFileName);
    }

    private void loadSettings(File settingsFile) throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        DocumentBuilder db = dbf.newDocumentBuilder();
        Document xml = db.parse(settingsFile);
        loadSettingsFromXml(xml);
    }

    private void loadSettings(String settingsXml) throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        DocumentBuilder db = dbf.newDocumentBuilder();
        Document xml = db.parse(new InputSource( new StringReader( settingsXml ) ));
        loadSettingsFromXml(xml);
    }

    private void loadSettingsFromXml(Document xml) throws Exception {
        xml.getDocumentElement().normalize();
        String newDriverName = xml.getDocumentElement().getNodeName();
        if (!newDriverName.equals("Custom"))
            throw new Exception(getString(R.string.unknown_driver_name, newDriverName));
        editTextBotName.setText(xml.getDocumentElement().getAttribute("Name"));
        editTextBotDesc.setText(xml.getDocumentElement().getAttribute("Description"));
        editTextCallsign.setText(xml.getDocumentElement().getAttribute("Callsign"));
        editTextResponse.setText(xml.getDocumentElement().getAttribute("Response"));
        checkBoxShowDebugInfo.setChecked(StringHelper.booleanFromString(
                xml.getDocumentElement().getAttribute("ShowDebugInfo"), true));
        checkBoxHideJoysticks.setChecked(StringHelper.booleanFromString(
                xml.getDocumentElement().getAttribute("HideJoysticks"), true));
        NodeList joystickNodes = xml.getElementsByTagName("Joystick");
        for (int i = 0; i < joystickNodes.getLength(); i++) {
            Element joystickNode = (Element) joystickNodes.item(i);
            int joystickIndex = StringHelper.intFromString(joystickNode.getAttribute("Index"), -1);
            if (joystickIndex >= 0 && joystickIndex < 4) {
                JoystickComponents joystickComponents = this.joystickComponents[joystickIndex];
                joystickComponents.visibilityCheckBox.setChecked(StringHelper.booleanFromString(joystickNode.getAttribute("Visible"), false));
                String shape = StringHelper.stringFromString(joystickNode.getAttribute("Shape"),
                        RoboCamDriver.JOYSTICK_SHAPE_CIRCULAR);
                if (shape.equals(RoboCamDriver.JOYSTICK_SHAPE_VERTICAL))
                    joystickComponents.shapeSpinner.setSelection(0);
                else if (shape.equals(RoboCamDriver.JOYSTICK_SHAPE_HORIZONTAL))
                    joystickComponents.shapeSpinner.setSelection(1);
                else if (shape.equals(RoboCamDriver.JOYSTICK_SHAPE_CIRCULAR))
                    joystickComponents.shapeSpinner.setSelection(2);
                else if (shape.equals(RoboCamDriver.JOYSTICK_SHAPE_QUADRATIC))
                    joystickComponents.shapeSpinner.setSelection(3);
                else if (shape.equals(RoboCamDriver.JOYSTICK_SHAPE_ARROWS))
                    joystickComponents.shapeSpinner.setSelection(4);
                else if (shape.equals(RoboCamDriver.JOYSTICK_SHAPE_VERTICAL_ARROWS))
                    joystickComponents.shapeSpinner.setSelection(5);
                else if (shape.equals(RoboCamDriver.JOYSTICK_SHAPE_HORIZONTAL_ARROWS))
                    joystickComponents.shapeSpinner.setSelection(6);
                joystickComponents.behavior1Spinner.setSelection(StringHelper.intFromString(
                        joystickNode.getAttribute("Behavior0"),
                        RoboCamDriver.JOYSTICK_BEHAVIOR_RETURN_TO_ZERO));
                joystickComponents.behavior2Spinner.setSelection(StringHelper.intFromString(
                        joystickNode.getAttribute("Behavior1"),
                        RoboCamDriver.JOYSTICK_BEHAVIOR_RETURN_TO_ZERO));
            }
        }
        keyCodes.clear();
        keysControl.clear(this);
        NodeList keyGroupNodes = xml.getElementsByTagName("KeyGroup");
        for (int i = 0; i < keyGroupNodes.getLength(); i++) {
            Element keyGroupNode = (Element) keyGroupNodes.item(i);
            boolean active = StringHelper.booleanFromString(keyGroupNode.getAttribute("Active"), false);
            checkBoxActive.setChecked(active);
            NodeList keyCodeNodes = keyGroupNode.getElementsByTagName("Key");
            for (int j = 0; j < keyCodeNodes.getLength(); j++) {
                Element keyCodeNode = (Element) keyCodeNodes.item(j);
                String strValue = keyCodeNode.getTextContent();
                Integer value = Integer.valueOf(strValue);
                if (value > 0 && value <= 255)
                    keyCodes.add(value);
            }
        }
        keysControl.setKeys(this, keyCodes);
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.buttonOverflow)
            v.showContextMenu();
        else if (KeyCodeControl.class.isAssignableFrom(v.getClass())) {
            ((KeyCodeControl)v).showKeySelector(this, KEY_CODE_REQUEST);
        }
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        menu.add(Menu.NONE, MI_SEND_SETTINGS, Menu.NONE, R.string.action_send_robot_settings);
        menu.add(Menu.NONE, MI_EXPORT_SETTINGS, Menu.NONE, R.string.action_export_robot_settings_to_file);
        menu.add(Menu.NONE, MI_COPY_SETTINGS, Menu.NONE, R.string.action_copy_robot_settings);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        if (item.getItemId() == MI_SEND_SETTINGS)
            sendSettings();
        else if (item.getItemId() == MI_EXPORT_SETTINGS)
            exportSettings();
        else if (item.getItemId() == MI_COPY_SETTINGS)
            copySettings();
        return super.onContextItemSelected(item);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode,
                                    Intent data) {
        if (resultCode == RESULT_OK) {
            switch (requestCode) {
                case KEY_CODE_REQUEST:
                    keysControl.setKeys(this, data.getIntArrayExtra(Intent.EXTRA_STREAM));
                    break;
            }
        }
    }

    class JoystickComponents
    {
        int index;
        public CheckBox visibilityCheckBox;
        public ImageView imageView1;
        public Spinner shapeSpinner;
        public ImageView behavior1imageView;
        public Spinner behavior1Spinner;
        public ImageView behavior2imageView;
        public Spinner behavior2Spinner;
        private JoystickComponents thisJoystickComponents = null;

        private void setJoystickVisibility() {
            int visibility = visibilityCheckBox.isChecked() ? View.VISIBLE : View.GONE;
            thisJoystickComponents.imageView1.setVisibility(visibility);
            thisJoystickComponents.shapeSpinner.setVisibility(visibility);
            setBehaviorVisibility();
        }

        private void setBehaviorVisibility() {
            int verticalVisibility = View.VISIBLE;
            int horizontalVisibility = View.VISIBLE;
            switch (shapeSpinner.getSelectedItemPosition()) {
                case 0: //Vertical
                    horizontalVisibility = View.GONE;
                    break;
                case 1: //Horizontal
                    verticalVisibility = View.GONE;
                    break;
                case 4: //Arrows
                case 5: //Vertical arrows
                case 6: //Horizontal arrows
                    //case 7: //Invisible
                    horizontalVisibility = View.GONE;
                    verticalVisibility = View.GONE;
                    break;
            }
            if (shapeSpinner.getVisibility() != View.VISIBLE) {
                horizontalVisibility = View.GONE;
                verticalVisibility = View.GONE;
            }
            behavior1imageView.setVisibility(horizontalVisibility);
            behavior1Spinner.setVisibility(horizontalVisibility);
            behavior2imageView.setVisibility(verticalVisibility);
            behavior2Spinner.setVisibility(verticalVisibility);
        }

        private void init(Activity activity, int index, int checkBoxJoystickVisibility,
                          int spinnerJoystickShape,
                          int imageViewJoystickBehavior1, int imageViewJoystickBehavior2,
                          int spinnerJoystickBehavior1, int spinnerJoystickBehavior2,
                          int imageView1){
            this.index = index;
            visibilityCheckBox = (CheckBox) activity.findViewById(checkBoxJoystickVisibility);
            this.imageView1 = (ImageView) activity.findViewById(imageView1);
            shapeSpinner = (Spinner) activity.findViewById(spinnerJoystickShape);
            SettingsActivityHelper.initSpinner(shapeSpinner, activity, Arrays.asList(new String[]{
                    activity.getString(R.string.joystick_shape_vertical),
                    activity.getString(R.string.joystick_shape_horizontal),
                    activity.getString(R.string.joystick_shape_circular),
                    activity.getString(R.string.joystick_shape_quadratic),
                    activity.getString(R.string.joystick_shape_arrows),
                    activity.getString(R.string.joystick_shape_vertical_arrows),
                    activity.getString(R.string.joystick_shape_horizontal_arrows)}), R.string.joystick_shape);
            behavior1imageView = (ImageView) activity.findViewById(imageViewJoystickBehavior1);
            behavior2imageView = (ImageView) activity.findViewById(imageViewJoystickBehavior2);
            behavior1Spinner = (Spinner) activity.findViewById(spinnerJoystickBehavior1);
            SettingsActivityHelper.initSpinner(behavior1Spinner, activity, Arrays.asList(new String[]{
                    activity.getString(R.string.joystick_behavior_return_to_zero),
                    activity.getString(R.string.joystick_behavior_hold_position)}), R.string.joystick_behavior1);
            behavior2Spinner = (Spinner) activity.findViewById(spinnerJoystickBehavior2);
            SettingsActivityHelper.initSpinner(behavior2Spinner, activity, Arrays.asList(new String[]{
                    activity.getString(R.string.joystick_behavior_return_to_zero),
                    activity.getString(R.string.joystick_behavior_hold_position)}), R.string.joystick_behavior2);

            visibilityCheckBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    setJoystickVisibility();
                }
            });

            shapeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                public void onItemSelected(AdapterView<?> parent, View itemSelected,
                                           int selectedItemPosition, long selectedId) {
                    setBehaviorVisibility();
                    setMailboxType();
                }

                public void onNothingSelected(AdapterView<?> parent) {
                }
            });
            shapeSpinner.setSelection(2); //Default value (see JOYSTICK_SHAPE_CIRCULAR)

            setJoystickVisibility();
        }

        private void setMailboxType() {
            String axis = "";
            switch (shapeSpinner.getSelectedItemPosition()) {
                case 0: //RoboCamDriver.JOYSTICK_SHAPE_VERTICAL
                case 5: //RoboCamDriver.JOYSTICK_SHAPE_VERTICAL_ARROWS
                    axis = index == 0 ? "y"
                            : index == 1 ? "z"
                            : index == 2 ? "b"
                            : "d";
                    break;
                case 1: //RoboCamDriver.JOYSTICK_SHAPE_HORIZONTAL
                case 6: //RoboCamDriver.JOYSTICK_SHAPE_HORIZONTAL_ARROWS
                    axis = index == 0 ? "x"
                            : index == 1 ? "w"
                            : index == 2 ? "a"
                            : "c";
                    break;
                case 2: //RoboCamDriver.JOYSTICK_SHAPE_CIRCULAR -- Default value
                case 3: //RoboCamDriver.JOYSTICK_SHAPE_QUADRATIC
                case 4: //RoboCamDriver.JOYSTICK_SHAPE_ARROWS
                    axis = index == 0 ? "x, y"
                            : index == 1 ? "w, z"
                            : index == 2 ? "a, b"
                            : "c, d";
                    break;
            }
        }

        public JoystickComponents(Activity activity, int index) {
            thisJoystickComponents = this;
            switch(index) {
                case 0:
                    init(activity, index, R.id.checkBoxJoystickVisibility1,
                            R.id.spinnerJoystickShape1,
                            R.id.imageViewJoystickBehavior1_1, R.id.imageViewJoystickBehavior1_2,
                            R.id.spinnerJoystickBehavior1_1, R.id.spinnerJoystickBehavior1_2,
                            R.id.imageView1_1);
                    break;
                case 1:
                    init(activity, index, R.id.checkBoxJoystickVisibility2,
                            R.id.spinnerJoystickShape2,
                            R.id.imageViewJoystickBehavior2_1, R.id.imageViewJoystickBehavior2_2,
                            R.id.spinnerJoystickBehavior2_1, R.id.spinnerJoystickBehavior2_2,
                            R.id.imageView2_1);
                    break;
                case 2:
                    init(activity, index, R.id.checkBoxJoystickVisibility3,
                            R.id.spinnerJoystickShape3,
                            R.id.imageViewJoystickBehavior3_1, R.id.imageViewJoystickBehavior3_2,
                            R.id.spinnerJoystickBehavior3_1, R.id.spinnerJoystickBehavior3_2,
                            R.id.imageView3_1);
                    break;
                case 3:
                    init(activity, index, R.id.checkBoxJoystickVisibility4,
                            R.id.spinnerJoystickShape4,
                            R.id.imageViewJoystickBehavior4_1, R.id.imageViewJoystickBehavior4_2,
                            R.id.spinnerJoystickBehavior4_1, R.id.spinnerJoystickBehavior4_2,
                            R.id.imageView4_1);
                    break;
            }
        }
    }

    private void exportSettings() {
        if (Utils.requestExternalStoragePermission(this,
                REQUEST_WRITE_EXTERNAL_STORAGE_PERMISSION_FOR_EXPORT)) {
            try {
                String fileName = saveSettingsForExport(false, false);
                Utils.showError(this, getString(R.string.settings_ware_exported_successfully, fileName), false);
            } catch (Exception e) {
                Toast.makeText(this, getString(R.string.error_while_exporting_settings_xml,
                        e.getLocalizedMessage()), Toast.LENGTH_LONG).show();
            }
        }
    }

    private void sendSettings() {
        if (Utils.requestExternalStoragePermission(this,
                REQUEST_WRITE_EXTERNAL_STORAGE_PERMISSION_FOR_COPY)) {
            try {
                String fileName = saveSettingsForExport(true, true);
                Intent intent = new Intent();
                intent.setAction(Intent.ACTION_SEND);
                intent.setType("text/xml");
                intent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(new File(fileName)));
                startActivity(intent);
            } catch (Exception e) {
                Toast.makeText(this, getString(R.string.error_while_exporting_settings_xml,
                        e.getLocalizedMessage()), Toast.LENGTH_LONG).show();
            }
        }
    }

    private void copySettings() {
        try {
            //Creating xml.
            Document xml = createCurrentSettingsXml();
            //Searching for names of existing settings.
            String name = Utils.createNewSettingsName(this, editTextBotName.getText().toString(), "Custom");
            //Changing the settings name.
            xml.getDocumentElement().setAttribute("Name", name);
            //Getting the file name.
            SimpleDateFormat format = new SimpleDateFormat("yyyyMMddHHmmsszzz", Locale.ENGLISH);
            String newFileName = "Custom_" + format.format(new Date()) + ".xml";
            //Saving the file.
            Utils.saveXml(new File(Utils.getRobotDir(this), newFileName), xml);
            RoboCamBroker.setLastSettingsModified(new Date());
            Toast.makeText(this, getString(R.string.settings_ware_copied_successfully),
                    Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.error_while_copying_settings_xml,
                    e.getLocalizedMessage()), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode == REQUEST_WRITE_EXTERNAL_STORAGE_PERMISSION_FOR_EXPORT
                || requestCode == REQUEST_WRITE_EXTERNAL_STORAGE_PERMISSION_FOR_COPY) {
            if (grantResults.length != 1 || grantResults[0] != PackageManager.PERMISSION_GRANTED)
                Utils.showError(this, R.string.request_write_external_storage_permission, false);
            else {
                //Trying one more time.
                if (requestCode == REQUEST_WRITE_EXTERNAL_STORAGE_PERMISSION_FOR_EXPORT)
                    exportSettings();
                else if (requestCode == REQUEST_WRITE_EXTERNAL_STORAGE_PERMISSION_FOR_COPY)
                    copySettings();
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    private String saveSettingsForExport(boolean tempFolder, boolean rewrite) throws ParserConfigurationException, FileNotFoundException, TransformerException {
        //Creating the file name.
        File path = tempFolder ? new File(Environment.getExternalStorageDirectory(), ".robocam") : Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        path.mkdirs();
        String fileName = "RoboCam_";
        String settingsName = editTextBotName.getText().toString();
        if (settingsName != null && (!settingsName.isEmpty()))
            fileName += settingsName.replace("\\", "_").replace("/", "_").replace(":", "_").replace(" ", "_") + "_";
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH);
        fileName += format.format(new Date());
        if ((!rewrite) && new File(path.getPath() + "/" + fileName + ".xml").exists()) {
            int n = 2;
            while (new File(path.getPath() + "/" + fileName + "_" + Integer.toString(n) + ".xml").exists())
                n++;
            fileName += "_" + Integer.toString(n);
        }
        fileName = path.getPath() + "/" + fileName + ".xml";
        //Saving a copy.
        Document xml = createCurrentSettingsXml();
        Utils.saveXml(fileName, xml);
        return fileName;
    }

    public void onCancelButtonClick(View v){
        finish();
    }

    public void onSaveButtonClick(View v){
        try {
            Document xml = createCurrentSettingsXml();
            DOMSource source = new DOMSource(xml);
            FileOutputStream stream = new FileOutputStream(getSettingsFile());
            StreamResult result = new StreamResult(stream);
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();
            transformer.transform(source, result);
            RoboCamBroker.setLastSettingsModified(new Date());
            Toast.makeText(this, R.string.settings_were_saved, Toast.LENGTH_SHORT).show();
            finish();
        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.error_while_creating_settings_xml,
                    e.getLocalizedMessage()), Toast.LENGTH_LONG).show();
        }
    }

}
