package ru.proghouse.robocam;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.AppCompatButton;
import android.util.TypedValue;
import android.view.ContextMenu;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import ru.proghouse.robocam.drivers.EV3.EV3Controller;
import ru.proghouse.robocam.drivers.EV3.EV3Driver;
import ru.proghouse.robocam.drivers.EV3.EV3KeyGroup;
import ru.proghouse.robocam.drivers.RoboCamDriver;

public class EV3SettingsActivity extends AppCompatActivity  implements View.OnClickListener {

    public static final String SETTINGS_FILE_NAME = "SettingsFileName";
    private static final String SETTINGS_XML = "SettingsXml";
    private String settingsFileName;
    private JoystickComponents[] joystickComponents = new JoystickComponents[4];
    private List<KeyGroupComponents> keyGroupComponents = new ArrayList<KeyGroupComponents>();
    private int CHECKBOX_PADDING = 0;
    private EditText editTextBotName = null;
    private EditText editTextBotDesc = null;
    private CheckBox checkBoxShowDebugInfo = null;
    private CheckBox checkBoxHideJoysticks = null;
    private ImageButton buttonOverflow = null;
    private CheckBox checkBoxStartUserProgram = null;
    private EditText editTextUserProgram = null;
    private static final int MI_SEND_SETTINGS = -2;
    private static final int MI_EXPORT_SETTINGS = -3;
    private static final int MI_COPY_SETTINGS = -4;
    private static final int KEY_CODE_REQUEST = 1;
    //private List<String> tempFileName = new ArrayList<String>();

    private static final int REQUEST_WRITE_EXTERNAL_STORAGE_PERMISSION_FOR_EXPORT = 1;
    private static final int REQUEST_WRITE_EXTERNAL_STORAGE_PERMISSION_FOR_COPY = 2;

    private Button buttonDeletePortJoystick1 = null;
    private Button buttonDeletePortJoystick2 = null;
    private Button buttonDeletePortJoystick3 = null;
    private Button buttonDeletePortJoystick4 = null;

    private Button buttonDeleteKeyGroup = null;

    private int[] indexToJoystickTypes = {
            RoboCamDriver.JOYSTICK_TYPE_INDEPENDENT_MOTORS,
            RoboCamDriver.JOYSTICK_TYPE_STEERING,
            RoboCamDriver.JOYSTICK_TYPE_STEERING_PROGRESSIVE,
            RoboCamDriver.JOYSTICK_TYPE_MAILBOX
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ev3_settings);

        buttonOverflow = (ImageButton)findViewById(R.id.buttonOverflow);
        buttonOverflow.setOnClickListener(this);
        registerForContextMenu(buttonOverflow);

        buttonDeleteKeyGroup = (Button)findViewById(R.id.buttonDeleteKeyGroup);
        registerForContextMenu(buttonDeleteKeyGroup);

        if (Build.VERSION.SDK_INT < 17) {
            CheckBox rb = new CheckBox(this);
            CHECKBOX_PADDING = rb.getPaddingLeft();
        }

        editTextBotName = (EditText)findViewById(R.id.editTextBotName);
        editTextBotDesc = (EditText)findViewById(R.id.editTextDesc);
        checkBoxShowDebugInfo = (CheckBox)findViewById(R.id.checkBoxShowDebugInfo);
        checkBoxHideJoysticks = (CheckBox)findViewById(R.id.checkBoxHideJoysticks);
        checkBoxStartUserProgram = (CheckBox)findViewById(R.id.checkBoxStartUserProgram);
        editTextUserProgram = (EditText)findViewById(R.id.editTextUserProgram);

        settingsFileName = this.getIntent().getStringExtra(SETTINGS_FILE_NAME);
        if (settingsFileName == null || settingsFileName.equals("")) {
            String savedSettingsFileName = null;
            if (savedInstanceState != null)
                savedSettingsFileName = savedInstanceState.getString(SETTINGS_FILE_NAME);
            if (savedSettingsFileName == null || savedSettingsFileName.equals("")) {
                SimpleDateFormat format = new SimpleDateFormat("yyyyMMddHHmmsszzz", Locale.ENGLISH);
                settingsFileName = "EV3_" + format.format(new Date()) + ".xml";
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

        buttonDeletePortJoystick1 = (Button)findViewById(R.id.buttonDeletePortJoystick1);
        buttonDeletePortJoystick1.setOnClickListener(this);
        registerForContextMenu(buttonDeletePortJoystick1);

        buttonDeletePortJoystick2 = (Button)findViewById(R.id.buttonDeletePortJoystick2);
        buttonDeletePortJoystick2.setOnClickListener(this);
        registerForContextMenu(buttonDeletePortJoystick2);

        buttonDeletePortJoystick3 = (Button)findViewById(R.id.buttonDeletePortJoystick3);
        buttonDeletePortJoystick3.setOnClickListener(this);
        registerForContextMenu(buttonDeletePortJoystick3);

        buttonDeletePortJoystick4 = (Button)findViewById(R.id.buttonDeletePortJoystick4);
        buttonDeletePortJoystick4.setOnClickListener(this);
        registerForContextMenu(buttonDeletePortJoystick4);
    }

    private File getSettingsFile() {
        File robotDir = Utils.getRobotDir(this);
        return new File(robotDir, settingsFileName);
    }

    private void loadOutputPorts(Element parentNode, List<OutputPortComponents> outputPorts,
                                 Spinner typeSpinner, LinearLayout portLayout, int ownerIndex,
                                 boolean forJoystick) {
        NodeList outputPortNodes = parentNode.getElementsByTagName("OutputPort");
        for (int j = 0; j < outputPortNodes.getLength(); j++) {
            Element outputPortNode = (Element) outputPortNodes.item(j);
            int group = StringHelper.intFromString(outputPortNode.getAttribute("Group"), -1);
            int layer = StringHelper.intFromString(outputPortNode.getAttribute("Layer"), -1);
            String portName = StringHelper.stringFromString(outputPortNode.getAttribute("Number"), "");
            int number = -1;
            if (portName.equals("A"))
                number = EV3Driver.OUTPUT_PORT_A;
            else if (portName.equals("B"))
                number = EV3Driver.OUTPUT_PORT_B;
            else if (portName.equals("C"))
                number = EV3Driver.OUTPUT_PORT_C;
            else if (portName.equals("D"))
                number = EV3Driver.OUTPUT_PORT_D;
            if (group >= 0 && group <= 1 && layer >= 0 && layer < 4 && number >= 0) {
                try {
                    int joystickType = StringHelper.intFromString(
                            outputPortNode.getAttribute("JoystickType"),
                            EV3Driver.JOYSTICK_TYPE_POWER);
                    int power = StringHelper.intFromString(
                            outputPortNode.getAttribute("Power"), 0);
                    boolean invert = StringHelper.booleanFromString(
                            outputPortNode.getAttribute("Invert"), false);
                    float coefficient = StringHelper.floatFromString(
                            outputPortNode.getAttribute("Coefficient"), 1);
                    int brake = StringHelper.intFromString(
                            outputPortNode.getAttribute("Brake"), EV3Driver.BRAKE);
                    outputPorts.add(new OutputPortComponents(this,
                            portLayout, ownerIndex,
                            group, layer, portName, joystickType, power, invert, coefficient,
                            brake, indexToJoystickTypes[typeSpinner.getSelectedItemPosition()],
                            forJoystick));
                }
                catch(Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void loadSettingsFromXml(Document xml) throws Exception {
        xml.getDocumentElement().normalize();
        String newDriverName = xml.getDocumentElement().getNodeName();
        if (!newDriverName.equals("EV3"))
            throw new Exception(getString(R.string.unknown_driver_name, newDriverName));
        editTextBotName.setText(xml.getDocumentElement().getAttribute("Name"));
        editTextBotDesc.setText(xml.getDocumentElement().getAttribute("Description"));
        checkBoxShowDebugInfo.setChecked(StringHelper.booleanFromString(
                xml.getDocumentElement().getAttribute("ShowDebugInfo"), true));
        checkBoxHideJoysticks.setChecked(StringHelper.booleanFromString(
                xml.getDocumentElement().getAttribute("HideJoysticks"), true));
        checkBoxStartUserProgram.setChecked(StringHelper.booleanFromString(
                xml.getDocumentElement().getAttribute("StartUserProgram"), false));
        editTextUserProgram.setText(xml.getDocumentElement().getAttribute("UserProgram"));
        NodeList joystickNodes = xml.getElementsByTagName("Joystick");
        for (int i = 0; i < this.joystickComponents.length; i++) {
            for (int j = 0; j < this.joystickComponents[i].outputPorts.size(); j++)
                this.joystickComponents[i].outputPorts.get(j).removeViews();
            this.joystickComponents[i].outputPorts.clear();
        }
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
                joystickComponents.typeSpinner.setSelection(indexToJoystickTypes[StringHelper.intFromString(
                        joystickNode.getAttribute("Type"),
                        RoboCamDriver.JOYSTICK_TYPE_INDEPENDENT_MOTORS)]);
                joystickComponents.behavior1Spinner.setSelection(StringHelper.intFromString(
                        joystickNode.getAttribute("Behavior0"),
                        RoboCamDriver.JOYSTICK_BEHAVIOR_RETURN_TO_ZERO));
                joystickComponents.behavior2Spinner.setSelection(StringHelper.intFromString(
                        joystickNode.getAttribute("Behavior1"),
                        RoboCamDriver.JOYSTICK_BEHAVIOR_RETURN_TO_ZERO));
                loadOutputPorts(joystickNode,
                        joystickComponents.outputPorts,
                        joystickComponents.typeSpinner,
                        joystickComponents.joystickPortsLinearLayout,
                        joystickIndex,
                        true);
            }
        }
        NodeList keyGroupNodes = xml.getElementsByTagName("KeyGroup");
        for (int i = 0; i < keyGroupNodes.getLength(); i++) {
            Element keyGroupNode = (Element) keyGroupNodes.item(i);
            this.keyGroupComponents.add(new KeyGroupComponents(
                    this, (LinearLayout)findViewById(R.id.linearLayoutKeyGroups),
                    this.keyGroupComponents.size(),
                    StringHelper.booleanFromString(keyGroupNode.getAttribute("Active"), false),
                    StringHelper.intFromString(keyGroupNode.getAttribute("Type"),
                            RoboCamDriver.JOYSTICK_TYPE_INDEPENDENT_MOTORS),
                    StringHelper.intFromString(keyGroupNode.getAttribute("IncX"), 0),
                    StringHelper.intFromString(keyGroupNode.getAttribute("IncY"), 0),
                    StringHelper.intFromString(keyGroupNode.getAttribute("DecX"), 0),
                    StringHelper.intFromString(keyGroupNode.getAttribute("DecY"), 0),
                    StringHelper.intFromString(keyGroupNode.getAttribute("StepXPause"), 100),
                    StringHelper.intFromString(keyGroupNode.getAttribute("StepYPause"), 100),
                    loadKeys(keyGroupNode, "UpKey"),
                    loadKeys(keyGroupNode, "LeftKey"),
                    loadKeys(keyGroupNode, "DownKey"),
                    loadKeys(keyGroupNode, "RightKey"),
                    loadKeys(keyGroupNode, "Key"),
                    StringHelper.stringFromString(keyGroupNode.getAttribute("Mailbox"), ""),
                    StringHelper.intFromString(keyGroupNode.getAttribute("Behavior0"),
                            RoboCamDriver.JOYSTICK_BEHAVIOR_RETURN_TO_ZERO),
                    StringHelper.intFromString(keyGroupNode.getAttribute("Behavior1"),
                            RoboCamDriver.JOYSTICK_BEHAVIOR_RETURN_TO_ZERO)));
            KeyGroupComponents keyGroupComponents = this.keyGroupComponents.get(this.keyGroupComponents.size() - 1);
            loadOutputPorts(keyGroupNode,
                    keyGroupComponents.outputPorts,
                    keyGroupComponents.spinnerType,
                    keyGroupComponents.portsLayout,
                    this.keyGroupComponents.size() - 1,
                    false);
        }
    }

    private HashSet<Integer> loadKeys(Element parentNode, String nodeName) {
        HashSet<Integer> keyCodes = new HashSet<Integer>();
        NodeList keyCodeNodes = parentNode.getElementsByTagName(nodeName);
        for (int j = 0; j < keyCodeNodes.getLength(); j++) {
            Element keyCodeNode = (Element) keyCodeNodes.item(j);
            String strValue = keyCodeNode.getTextContent();
            Integer value = Integer.valueOf(strValue);
            if (value > 0 && value <= 255)
                keyCodes.add(value);
        }
        return keyCodes;
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

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        int joystickIndex = -1;
        if (v.getId() == R.id.buttonDeletePortJoystick1)
            joystickIndex = 0;
        else if (v.getId() == R.id.buttonDeletePortJoystick2)
            joystickIndex = 1;
        else if (v.getId() == R.id.buttonDeletePortJoystick3)
            joystickIndex = 2;
        else if (v.getId() == R.id.buttonDeletePortJoystick4)
            joystickIndex = 3;
        if (joystickIndex >= 0)
            for (int i = 0; i < joystickComponents[joystickIndex].outputPorts.size(); i++)
                menu.add(Menu.NONE, (joystickIndex + 1) * 1000000 + i + 1, i + 1,
                        joystickComponents[joystickIndex].outputPorts.get(i).textViewTitle.getText());
        else if (v.getTag() != null && ButtonTag.class.isAssignableFrom(v.getTag().getClass())) {
            ButtonTag buttonTag = (ButtonTag)v.getTag();
            for (int i = 0; i < buttonTag.keyGroupComponents.outputPorts.size(); i++)
                menu.add(Menu.NONE, (buttonTag.keyGroupComponents.index + 1) * 10000000 + i + 1, i + 1,
                        buttonTag.keyGroupComponents.outputPorts.get(i).textViewTitle.getText());
        }
        else if (v.getId() == R.id.buttonDeleteKeyGroup) {
            for (int i = 0; i < keyGroupComponents.size(); i++)
                menu.add(Menu.NONE, (i + 11) * -1, i + 1,
                        keyGroupComponents.get(i).textViewTitle.getText());
        }
        else {
            menu.add(Menu.NONE, MI_SEND_SETTINGS, Menu.NONE, R.string.action_send_robot_settings);
            menu.add(Menu.NONE, MI_EXPORT_SETTINGS, Menu.NONE, R.string.action_export_robot_settings_to_file);
            menu.add(Menu.NONE, MI_COPY_SETTINGS, Menu.NONE, R.string.action_copy_robot_settings);
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        if (item.getItemId() > 1000000 && (int)(item.getItemId() / 1000000) <= 4) {
            int joystickIndex = (int) (item.getItemId() / 1000000) - 1;
            int portIndex = item.getItemId() - (joystickIndex + 1) * 1000000 - 1;
            joystickComponents[joystickIndex].outputPorts.get(portIndex).removeViews();
            joystickComponents[joystickIndex].outputPorts.remove(portIndex);
            Toast.makeText(this, R.string.port_was_deleted, Toast.LENGTH_LONG).show();
            return true;
        }
        else if (item.getItemId() > 10000000) {
            int keyGroupIndex = (int) (item.getItemId() / 10000000) - 1;
            int portIndex = item.getItemId() - (keyGroupIndex + 1) * 10000000 - 1;
            keyGroupComponents.get(keyGroupIndex).outputPorts.get(portIndex).removeViews();
            keyGroupComponents.get(keyGroupIndex).outputPorts.remove(portIndex);
            Toast.makeText(this, R.string.port_was_deleted, Toast.LENGTH_LONG).show();
            return true;
        }
        else if (item.getItemId() < -10) {
            int keyGroupIndex = (item.getItemId() * -1) - 11;
            keyGroupComponents.get(keyGroupIndex).removeViews();
            keyGroupComponents.remove(keyGroupIndex);
            for (int i = 0; i < keyGroupComponents.size(); i++)
                keyGroupComponents.get(i).updateIndex(i);
            Toast.makeText(this, R.string.keygroup_was_deleted, Toast.LENGTH_LONG).show();
            return true;
        }
        else if (item.getItemId() == MI_SEND_SETTINGS)
            sendSettings();
        else if (item.getItemId() == MI_EXPORT_SETTINGS)
            exportSettings();
        else if (item.getItemId() == MI_COPY_SETTINGS)
            copySettings();
        return super.onContextItemSelected(item);
    }

    private void copySettings() {
        try {
            //Creating xml.
            Document xml = createCurrentSettingsXml();
            //Searching for names of existing settings.
            String name = Utils.createNewSettingsName(this, editTextBotName.getText().toString(), "EV3");
            //Changing the settings name.
            xml.getDocumentElement().setAttribute("Name", name);
            //Getting the file name.
            SimpleDateFormat format = new SimpleDateFormat("yyyyMMddHHmmsszzz", Locale.ENGLISH);
            String newFileName = "EV3_" + format.format(new Date()) + ".xml";
            //Saving the file.
            Utils.saveXml(new File(Utils.getRobotDir(this), newFileName), xml);
            RoboCamBroker.setLastSettingsModified(new Date());
            Toast.makeText(this, getString(R.string.settings_ware_copied_successfully),
                    Toast.LENGTH_LONG).show();
            //Utils.showError(this, getString(R.string.settings_ware_copied_successfully), false);
        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.error_while_copying_settings_xml,
                    e.getLocalizedMessage()), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode,
                                    Intent data) {
        if (resultCode == RESULT_OK) {
            switch (requestCode) {
                case KEY_CODE_REQUEST:
                    String keyGroupTitle = data.getStringExtra(Intent.EXTRA_REMOTE_INTENT_TOKEN);
                    String keyCodeControlTitle = data.getStringExtra(Intent.EXTRA_TEXT);
                    for (KeyGroupComponents components : keyGroupComponents) {
                        if (components.getKeyCodeTitle().equals(keyGroupTitle)) {
                            KeyCodeControl control = components.findKeyCodeControl(keyCodeControlTitle);
                            if (control != null)
                                control.setKeys(this, data.getIntArrayExtra(Intent.EXTRA_STREAM));
                            break;
                        }
                    }
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
                //Toast.makeText(this, getString(R.string.settings_ware_exported_successfully, fileName),
                //        Toast.LENGTH_LONG).show();
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
                //tempFileName.add(fileName);
                Intent intent = new Intent();
                intent.setAction(Intent.ACTION_SEND);
                intent.setType("text/xml");
                intent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(new File(fileName)));
                //startActivityForResult(intent, REQUEST_CODE_SEND);
                startActivity(intent);
            } catch (Exception e) {
                Toast.makeText(this, getString(R.string.error_while_exporting_settings_xml,
                        e.getLocalizedMessage()), Toast.LENGTH_LONG).show();
            }
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

    @Override
    public void onClick(View v) {
        int joystickIndex = -1;
        if (v.getId() == R.id.buttonDeletePortJoystick1)
            joystickIndex = 0;
        else if (v.getId() == R.id.buttonDeletePortJoystick2)
            joystickIndex = 1;
        else if (v.getId() == R.id.buttonDeletePortJoystick3)
            joystickIndex = 2;
        else if (v.getId() == R.id.buttonDeletePortJoystick4)
            joystickIndex = 3;
        if (joystickIndex >= 0 && joystickComponents[joystickIndex].outputPorts.size() > 0)
            v.showContextMenu();
        else if (v.getId() == R.id.buttonOverflow)
            v.showContextMenu();
        else if (KeyCodeControl.class.isAssignableFrom(v.getClass())) {
            ((KeyCodeControl)v).showKeySelector(this, KEY_CODE_REQUEST);
        }
        else if (v.getTag() != null && ButtonTag.class.isAssignableFrom(v.getTag().getClass())) {
            try {
                ButtonTag buttonTag = (ButtonTag)v.getTag();
                if (buttonTag.addButton) {
                    buttonTag.keyGroupComponents.outputPorts.add(new OutputPortComponents(this,
                            buttonTag.keyGroupComponents.portsLayout, buttonTag.keyGroupComponents.index, 0, 0, "A",
                            EV3Driver.JOYSTICK_TYPE_POWER, 0, false, 1, EV3Driver.BRAKE,
                            indexToJoystickTypes[buttonTag.keyGroupComponents.spinnerType.getSelectedItemPosition()],
                            false));
                    Toast.makeText(this, getString(R.string.port_has_been_added),
                            Toast.LENGTH_LONG).show();
                } else {
                    v.showContextMenu();
                }
            } catch(Exception e) {
                Toast.makeText(this, getString(R.string.error_while_creating_settings_xml,
                        e.getLocalizedMessage()), Toast.LENGTH_LONG).show();
            }
        }
    }

    class JoystickComponents
    {
        int index;
        public CheckBox visibilityCheckBox;
        public ImageView imageView1;
        public Spinner shapeSpinner;
        public ImageView imageView2;
        public Spinner typeSpinner;
        public ImageView behavior1imageView;
        public Spinner behavior1Spinner;
        public ImageView behavior2imageView;
        public Spinner behavior2Spinner;
        public TextView joystickPortsTextView;
        public LinearLayout joystickPortsLinearLayout;
        public LinearLayout joystickPortButtonsLinearLayout;
        private JoystickComponents thisJoystickComponents = null;
        public List<OutputPortComponents> outputPorts = new ArrayList<OutputPortComponents>();

        private void setJoystickVisibility() {
            int visibility = visibilityCheckBox.isChecked() ? View.VISIBLE : View.GONE;
            thisJoystickComponents.imageView1.setVisibility(visibility);
            thisJoystickComponents.imageView2.setVisibility(visibility);
            thisJoystickComponents.shapeSpinner.setVisibility(visibility);
            thisJoystickComponents.typeSpinner.setVisibility(visibility);
            thisJoystickComponents.joystickPortsTextView.setVisibility(visibility);
            thisJoystickComponents.joystickPortsLinearLayout.setVisibility(visibility);
            thisJoystickComponents.joystickPortButtonsLinearLayout.setVisibility(visibility);
            setBehaviorVisibility();
        }

        private void setPortsVisibility() {
            int portVisibility = indexToJoystickTypes[typeSpinner.getSelectedItemPosition()] == RoboCamDriver.JOYSTICK_TYPE_MAILBOX ? View.GONE : View.VISIBLE;
            joystickPortsTextView.setVisibility(portVisibility);
            joystickPortsLinearLayout.setVisibility(portVisibility);
            joystickPortButtonsLinearLayout.setVisibility(portVisibility);
            for (OutputPortComponents portComponents : outputPorts) {
                portComponents.setTypeVisibility(
                        indexToJoystickTypes[typeSpinner.getSelectedItemPosition()] != RoboCamDriver.JOYSTICK_TYPE_STEERING
                        && indexToJoystickTypes[typeSpinner.getSelectedItemPosition()] != RoboCamDriver.JOYSTICK_TYPE_STEERING_PROGRESSIVE);
                portComponents.updateGroupSpinner(indexToJoystickTypes[typeSpinner.getSelectedItemPosition()]);
            }
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
                          int spinnerJoystickShape, int spinnerJoystickType,
                          int imageViewJoystickBehavior1, int imageViewJoystickBehavior2,
                          int spinnerJoystickBehavior1, int spinnerJoystickBehavior2,
                          int textViewJoystickPorts, int linearLayoutJoystickPorts,
                          int imageView1, int imageView2, int linearLayoutJoystickPortButtons){
            this.index = index;
            visibilityCheckBox = (CheckBox) activity.findViewById(checkBoxJoystickVisibility);
            this.imageView1 = (ImageView) activity.findViewById(imageView1);
            this.imageView2 = (ImageView) activity.findViewById(imageView2);
            shapeSpinner = (Spinner) activity.findViewById(spinnerJoystickShape);
            SettingsActivityHelper.initSpinner(shapeSpinner, activity, Arrays.asList(new String[]{
                    activity.getString(R.string.joystick_shape_vertical),
                    activity.getString(R.string.joystick_shape_horizontal),
                    activity.getString(R.string.joystick_shape_circular),
                    activity.getString(R.string.joystick_shape_quadratic),
                    activity.getString(R.string.joystick_shape_arrows),
                    activity.getString(R.string.joystick_shape_vertical_arrows),
                    activity.getString(R.string.joystick_shape_horizontal_arrows)}), R.string.joystick_shape);
            typeSpinner = (Spinner) activity.findViewById(spinnerJoystickType);
            SettingsActivityHelper.initSpinner(typeSpinner, activity, Arrays.asList(new String[]{
                    activity.getString(R.string.joystick_type_independent_motors),
                    activity.getString(R.string.joystick_type_steering),
                    activity.getString(R.string.joystick_type_steering_progressive),
                    activity.getString(R.string.joystick_type_mailbox) + " ("
                            //xywzabcd
                            + (index == 0 ? "x, y"
                            : index == 1 ? "w, z"
                            : index == 2 ? "a, b"
                            : "c, d")
                            + ")"
            }), R.string.joystick_type);
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
            joystickPortsTextView = (TextView) activity.findViewById(textViewJoystickPorts);
            joystickPortsLinearLayout = (LinearLayout) activity.findViewById(linearLayoutJoystickPorts);
            joystickPortButtonsLinearLayout = (LinearLayout) activity.findViewById(linearLayoutJoystickPortButtons);

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

            typeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {

                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    setPortsVisibility();
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {
                }
            });

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
            int position = typeSpinner.getSelectedItemPosition();
            CustomAdapter adapter = (CustomAdapter)typeSpinner.getAdapter();
            adapter.getObjects().set(3, adapter.getActivity().getString(R.string.joystick_type_mailbox)
                    + " (" + axis + ")");
            typeSpinner.setAdapter(adapter);
            typeSpinner.setSelection(position);
        }

        public JoystickComponents(Activity activity, int index) {
            thisJoystickComponents = this;
            switch(index) {
                case 0:
                    init(activity, index, R.id.checkBoxJoystickVisibility1,
                            R.id.spinnerJoystickShape1, R.id.spinnerJoystickType1,
                            R.id.imageViewJoystickBehavior1_1, R.id.imageViewJoystickBehavior1_2,
                            R.id.spinnerJoystickBehavior1_1, R.id.spinnerJoystickBehavior1_2,
                            R.id.textViewJoystick1Ports, R.id.linearLayoutJoystick1Ports,
                            R.id.imageView1_1, R.id.imageView1_2, R.id.linearLayoutJoystick1PortButtons);
                    break;
                case 1:
                    init(activity, index, R.id.checkBoxJoystickVisibility2,
                            R.id.spinnerJoystickShape2, R.id.spinnerJoystickType2,
                            R.id.imageViewJoystickBehavior2_1, R.id.imageViewJoystickBehavior2_2,
                            R.id.spinnerJoystickBehavior2_1, R.id.spinnerJoystickBehavior2_2,
                            R.id.textViewJoystick2Ports, R.id.linearLayoutJoystick2Ports,
                            R.id.imageView2_1, R.id.imageView2_2, R.id.linearLayoutJoystick2PortButtons);
                    break;
                case 2:
                    init(activity, index, R.id.checkBoxJoystickVisibility3,
                            R.id.spinnerJoystickShape3, R.id.spinnerJoystickType3,
                            R.id.imageViewJoystickBehavior3_1, R.id.imageViewJoystickBehavior3_2,
                            R.id.spinnerJoystickBehavior3_1, R.id.spinnerJoystickBehavior3_2,
                            R.id.textViewJoystick3Ports, R.id.linearLayoutJoystick3Ports,
                            R.id.imageView3_1, R.id.imageView3_2, R.id.linearLayoutJoystick3PortButtons);
                    break;
                case 3:
                    init(activity, index, R.id.checkBoxJoystickVisibility4,
                            R.id.spinnerJoystickShape4, R.id.spinnerJoystickType4,
                            R.id.imageViewJoystickBehavior4_1, R.id.imageViewJoystickBehavior4_2,
                            R.id.spinnerJoystickBehavior4_1, R.id.spinnerJoystickBehavior4_2,
                            R.id.textViewJoystick4Ports, R.id.linearLayoutJoystick4Ports,
                            R.id.imageView4_1, R.id.imageView4_2, R.id.linearLayoutJoystick4PortButtons);
                    break;
            }
        }
    }

    class OutputPortComponents {
        int ownerIndex;
        boolean forJoystick;
        LinearLayout activityLayout;
        LinearLayout portLayout;
        TextView textViewTitle;
        Spinner spinnerGroup;
        Spinner spinnerLayer;
        Spinner spinnerNumber;
        Spinner spinnerJoystickType;
        CheckBox checkBoxInvert;
        EditText editTextCoefficient;
        TextView textViewPower;
        EditText editTextPower;
        CheckBox checkBoxBrake;
        ImageView separatorPower;
        ImageView separatorJoystickType;
        LinearLayout layoutJoystickType;

        public OutputPortComponents(Activity activity, LinearLayout layout, int ownerIndex,
                                    int group, int layer, String portName, int portJoystickType, int power,
                                    boolean invert, float coefficient, int brake, int joystickType,
                                    boolean forJoystick) {
            this.forJoystick = forJoystick;
            this.ownerIndex = ownerIndex;
            activityLayout = layout;

            //OutputPort layout
            portLayout = new LinearLayout(activity);
            portLayout.setOrientation(LinearLayout.VERTICAL);
            layout.addView(portLayout);
            //Title
            ContextThemeWrapper newContext  = new ContextThemeWrapper(activity,
                    R.style.SettingsSectionTitle);
            textViewTitle = new TextView(newContext);
            if (forJoystick)
                textViewTitle.setText(getString(R.string.joystick_n_port_n, ownerIndex + 1, portName));
            else
                textViewTitle.setText(getString(R.string.keygroup_n_port_n, ownerIndex + 1, portName));
            portLayout.addView(textViewTitle);
            //Joystick group
            spinnerGroup = SettingsActivityHelper.createSpinner(activity, portLayout, Arrays.asList(new String[]{
                    activity.getString(joystickType == RoboCamDriver.JOYSTICK_TYPE_INDEPENDENT_MOTORS
                            ? R.string.joystick_group_horizontal : R.string.joystick_group_left),
                    activity.getString(joystickType == RoboCamDriver.JOYSTICK_TYPE_INDEPENDENT_MOTORS
                            ? R.string.joystick_group_vertical : R.string.joystick_group_right)}),
                    group, joystickType == RoboCamDriver.JOYSTICK_TYPE_INDEPENDENT_MOTORS
                            ? R.string.joystick_group_axis : R.string.joystick_group_motor, null);
            //Separator
            SettingsActivityHelper.createSeparator(activity, portLayout);
            //EV3 level
            spinnerLayer = SettingsActivityHelper.createSpinner(activity, portLayout,
                    Arrays.asList(new String[]{
                    "1", "2", "3", "4"}), layer, R.string.joystick_layer, null);
            //Separator
            SettingsActivityHelper.createSeparator(activity, portLayout);
            //Port number
            spinnerNumber = SettingsActivityHelper.createSpinner(activity, portLayout,
                    Arrays.asList(new String[]{
                            "A", "B", "C", "D"}),
                    portName.equals("A") ? 0 : portName.equals("B") ? 1 : portName.equals("C") ? 2 : 3,
                    R.string.joystick_port_number, null);
            spinnerNumber.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {

                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    updateTitle(-1);
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {

                }
            });
            //Separator
            SettingsActivityHelper.createSeparator(activity, portLayout);

            //Joystick type (changeable value)
            LinearLayout[] spinnerLayout = new LinearLayout[1];
            spinnerJoystickType = SettingsActivityHelper.createSpinner(activity, portLayout, Arrays.asList(new String[]{
                    activity.getString(R.string.joystick_type_power),
                    activity.getString(R.string.joystick_type_angle)}),
                    portJoystickType, R.string.joystick_type_cv, spinnerLayout);
            layoutJoystickType = spinnerLayout[0];
            //Separator
            separatorJoystickType = SettingsActivityHelper.createSeparator(activity, portLayout);
            setTypeVisibility(joystickType != RoboCamDriver.JOYSTICK_TYPE_STEERING
                && joystickType != RoboCamDriver.JOYSTICK_TYPE_STEERING_PROGRESSIVE);
            //Power
            textViewPower = SettingsActivityHelper.createTextViewTitle(activity, portLayout, R.string.joystick_power);
            editTextPower = SettingsActivityHelper.createEditTextInteger(activity, portLayout, power, 0, 0);
            //Separator
            separatorPower = SettingsActivityHelper.createSeparator(activity, portLayout);
            //Invert
            checkBoxInvert = SettingsActivityHelper.createCheckBox(activity,
                    portLayout, invert, R.string.joystick_invert, CHECKBOX_PADDING);
            //Separator
            SettingsActivityHelper.createSeparator(activity, portLayout);
            //Brake
            checkBoxBrake = SettingsActivityHelper.createCheckBox(activity,
                    portLayout, brake == EV3Driver.BRAKE, R.string.joystick_brake,
                    CHECKBOX_PADDING);
            //Separator
            SettingsActivityHelper.createSeparator(activity, portLayout);
            //Coefficient
            editTextCoefficient = SettingsActivityHelper.createEditTextFloat(activity, portLayout, coefficient,
                    R.string.joystick_coefficient, 0);

            spinnerJoystickType.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                public void onItemSelected(AdapterView<?> parent, View itemSelected,
                                           int selectedItemPosition, long selectedId) {
                    updateEditTextPower();
                }

                public void onNothingSelected(AdapterView<?> parent) {
                }
            });
            updateEditTextPower();
        }

        private void setTypeVisibility(boolean showType) {
            int visibility = showType ? View.VISIBLE : View.GONE;
            //spinnerJoystickType.setVisibility(visibility);
            layoutJoystickType.setVisibility(visibility);
            separatorJoystickType.setVisibility(visibility);
        }

        private void updateTitle(int newOwnerIndex) {
            if (newOwnerIndex >= 0)
                ownerIndex = newOwnerIndex;
            if (forJoystick)
                textViewTitle.setText(getString(R.string.joystick_n_port_n, ownerIndex + 1,
                        new String[]{"A", "B", "C", "D"}[spinnerNumber.getSelectedItemPosition()]));
            else
                textViewTitle.setText(getString(R.string.keygroup_n_port_n, ownerIndex + 1,
                        new String[]{"A", "B", "C", "D"}[spinnerNumber.getSelectedItemPosition()]));
        }

        private void updateEditTextPower() {
            int visibility = spinnerJoystickType.getSelectedItemPosition() == 1 ? View.VISIBLE : View.GONE;
            editTextPower.setVisibility(visibility);
            textViewPower.setVisibility(visibility);
            separatorPower.setVisibility(visibility);
        }

        public void removeViews() {
            activityLayout.removeView(portLayout);
        }

        public void updateGroupSpinner(int joystickType) {
            int position = spinnerGroup.getSelectedItemPosition();
            CustomAdapter adapter = (CustomAdapter)spinnerGroup.getAdapter();
            adapter.setTitleResId(joystickType == RoboCamDriver.JOYSTICK_TYPE_INDEPENDENT_MOTORS
                    ? (forJoystick ? R.string.joystick_group_axis : R.string.keyboard_group_axis) : R.string.joystick_group_motor);
            adapter.getObjects().set(0, adapter.getActivity().getString(
                    joystickType == RoboCamDriver.JOYSTICK_TYPE_INDEPENDENT_MOTORS
                            ? (forJoystick ? R.string.joystick_group_horizontal : R.string.keygroup_horizontal) : R.string.joystick_group_left));
            adapter.getObjects().set(1, adapter.getActivity().getString(
                    joystickType == RoboCamDriver.JOYSTICK_TYPE_INDEPENDENT_MOTORS
                            ? (forJoystick ? R.string.joystick_group_vertical : R.string.keygroup_vertical) : R.string.joystick_group_right));
            spinnerGroup.setAdapter(adapter);
            spinnerGroup.setSelection(position);
        }
    }

    private Document createCurrentSettingsXml() throws ParserConfigurationException {
        //Creating the XML
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        DocumentBuilder db = dbf.newDocumentBuilder();
        Document xml = db.newDocument();
        xml.appendChild(xml.createElement("EV3"));
        Element ev3Element = xml.getDocumentElement();
        ev3Element.setAttribute("Name", editTextBotName.getText().toString());
        ev3Element.setAttribute("Description", editTextBotDesc.getText().toString());
        if (!checkBoxShowDebugInfo.isChecked())
            ev3Element.setAttribute("ShowDebugInfo", "0");
        if (!checkBoxHideJoysticks.isChecked())
            ev3Element.setAttribute("HideJoysticks", "0");
        if (checkBoxStartUserProgram.isChecked())
            ev3Element.setAttribute("StartUserProgram", "1");
        if (editTextUserProgram.getText().toString() != null
                && (!editTextUserProgram.getText().toString().equals("")))
            ev3Element.setAttribute("UserProgram", editTextUserProgram.getText().toString());
        int index = 0;
        for (JoystickComponents joystickComponent : joystickComponents) {
            Element joystickElement = xml.createElement("Joystick");
            ev3Element.appendChild(joystickElement);
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
            if (indexToJoystickTypes[joystickComponent.typeSpinner.getSelectedItemPosition()]
                    != RoboCamDriver.JOYSTICK_TYPE_INDEPENDENT_MOTORS
                    || joystickComponent.visibilityCheckBox.isChecked())
                joystickElement.setAttribute("Type",
                        Integer.toString(indexToJoystickTypes[joystickComponent.typeSpinner.getSelectedItemPosition()]));
            if (joystickComponent.behavior1Spinner.getSelectedItemPosition() != RoboCamDriver.JOYSTICK_BEHAVIOR_RETURN_TO_ZERO)
                joystickElement.setAttribute("Behavior0",
                        Integer.toString(joystickComponent.behavior1Spinner.getSelectedItemPosition()));
            if (joystickComponent.behavior2Spinner.getSelectedItemPosition() != RoboCamDriver.JOYSTICK_BEHAVIOR_RETURN_TO_ZERO)
                joystickElement.setAttribute("Behavior1",
                        Integer.toString(joystickComponent.behavior2Spinner.getSelectedItemPosition()));
            addOutputPorts(xml, joystickElement, joystickComponent.outputPorts);
            index++;
        }
        for (KeyGroupComponents keyGroupComponents : this.keyGroupComponents) {
            Element keyGroupElement = xml.createElement("KeyGroup");
            ev3Element.appendChild(keyGroupElement);
            if (keyGroupComponents.spinnerType.getSelectedItemPosition()
                    != RoboCamDriver.JOYSTICK_TYPE_INDEPENDENT_MOTORS)
                keyGroupElement.setAttribute("Type",
                        Integer.toString(keyGroupComponents.spinnerType.getSelectedItemPosition()));
            if (keyGroupComponents.checkBoxActive.isChecked())
                keyGroupElement.setAttribute("Active", "1");
            if (!"".equals(keyGroupComponents.editTextMailbox.getText().toString()))
                keyGroupElement.setAttribute("Mailbox",
                        keyGroupComponents.editTextMailbox.getText().toString());
            if (keyGroupComponents.spinnerBehavior1.getSelectedItemPosition()
                    != RoboCamDriver.JOYSTICK_BEHAVIOR_RETURN_TO_ZERO)
                keyGroupElement.setAttribute("Behavior0",
                        Integer.toString(keyGroupComponents.spinnerBehavior1.getSelectedItemPosition()));
            if (keyGroupComponents.spinnerBehavior2.getSelectedItemPosition()
                    != RoboCamDriver.JOYSTICK_BEHAVIOR_RETURN_TO_ZERO)
                keyGroupElement.setAttribute("Behavior1",
                        Integer.toString(keyGroupComponents.spinnerBehavior2.getSelectedItemPosition()));
            if ((!"0".equals(keyGroupComponents.editTextIncX.getText().toString()))
                    && (!"".equals(keyGroupComponents.editTextIncX.getText().toString())))
                keyGroupElement.setAttribute("IncX",
                        keyGroupComponents.editTextIncX.getText().toString());
            if ((!"0".equals(keyGroupComponents.editTextIncY.getText().toString()))
                    && (!"".equals(keyGroupComponents.editTextIncY.getText().toString())))
                keyGroupElement.setAttribute("IncY",
                        keyGroupComponents.editTextIncY.getText().toString());
            if ((!"0".equals(keyGroupComponents.editTextDecX.getText().toString()))
                    && (!"".equals(keyGroupComponents.editTextDecX.getText().toString())))
                keyGroupElement.setAttribute("DecX",
                        keyGroupComponents.editTextDecX.getText().toString());
            if ((!"0".equals(keyGroupComponents.editTextDecY.getText().toString()))
                    && (!"".equals(keyGroupComponents.editTextDecY.getText().toString())))
                keyGroupElement.setAttribute("DecY",
                        keyGroupComponents.editTextDecY.getText().toString());
            if ((!"100".equals(keyGroupComponents.editTextStepXPause.getText().toString()))
                    && (!"".equals(keyGroupComponents.editTextStepXPause.getText().toString())))
                keyGroupElement.setAttribute("StepXPause",
                        keyGroupComponents.editTextStepXPause.getText().toString());
            if ((!"100".equals(keyGroupComponents.editTextStepYPause.getText().toString()))
                    && (!"".equals(keyGroupComponents.editTextStepYPause.getText().toString())))
                keyGroupElement.setAttribute("StepYPause",
                        keyGroupComponents.editTextStepYPause.getText().toString());
            addKeys(xml, keyGroupElement, keyGroupComponents.upKeysControl, "UpKey");
            addKeys(xml, keyGroupElement, keyGroupComponents.leftKeysControl, "LeftKey");
            addKeys(xml, keyGroupElement, keyGroupComponents.downKeysControl, "DownKey");
            addKeys(xml, keyGroupElement, keyGroupComponents.rightKeysControl, "RightKey");
            addKeys(xml, keyGroupElement, keyGroupComponents.keysControl, "Key");
            addOutputPorts(xml, keyGroupElement, keyGroupComponents.outputPorts);
        }
        return xml;
    }

    private void addOutputPorts(Document xml, Element parentElement, List<OutputPortComponents> outputPorts) {
        for (OutputPortComponents outputPortComponent : outputPorts) {
            Element portElement = xml.createElement("OutputPort");
            parentElement.appendChild(portElement);
            portElement.setAttribute("Group",
                    Integer.toString(outputPortComponent.spinnerGroup.getSelectedItemPosition()));
            portElement.setAttribute("Layer",
                    Integer.toString(outputPortComponent.spinnerLayer.getSelectedItemPosition()));
            portElement.setAttribute("Number",
                    new String[]{"A", "B", "C", "D"}[outputPortComponent.spinnerNumber.getSelectedItemPosition()]);
            if (outputPortComponent.spinnerJoystickType.getSelectedItemPosition() != EV3Driver.JOYSTICK_TYPE_POWER)
                portElement.setAttribute("JoystickType",
                        Integer.toString(outputPortComponent.spinnerJoystickType.getSelectedItemPosition()));
            if (!outputPortComponent.editTextPower.getText().toString().equals("0"))
                portElement.setAttribute("Power", outputPortComponent.editTextPower.getText().toString());
            if (outputPortComponent.checkBoxInvert.isChecked())
                portElement.setAttribute("Invert", "1");
            if (!outputPortComponent.editTextCoefficient.getText().toString().equals("1.0"))
                portElement.setAttribute("Coefficient", outputPortComponent.editTextCoefficient.getText().toString());
            if (!outputPortComponent.checkBoxBrake.isChecked())
                portElement.setAttribute("Brake", "0");
        }
    }

    private void addKeys(Document xml, Element keyGroupElement, KeyCodeControl keysControl, String nodeName) {
        if (keysControl.keys != null)
            for (Integer keyCode : keysControl.keys) {
                Element keyCodeElement = xml.createElement(nodeName);
                keyGroupElement.appendChild(keyCodeElement);
                keyCodeElement.setTextContent(keyCode.toString());
            }
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

    /*@Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_ev3_settings, menu);
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

    public void onAddPortButtonClick(View v){
        int joystickIndex = -1;
        if (v.getId() == R.id.buttonAddPortJoystick1)
            joystickIndex = 0;
        else if (v.getId() == R.id.buttonAddPortJoystick2)
            joystickIndex = 1;
        else if (v.getId() == R.id.buttonAddPortJoystick3)
            joystickIndex = 2;
        else if (v.getId() == R.id.buttonAddPortJoystick4)
            joystickIndex = 3;
        if (joystickIndex >= 0) {
            try {
                joystickComponents[joystickIndex].outputPorts.add(new OutputPortComponents(this,
                        joystickComponents[joystickIndex].joystickPortsLinearLayout, joystickIndex,
                        0, 0, "A", EV3Driver.JOYSTICK_TYPE_POWER, 0, false, 1, EV3Driver.BRAKE,
                        indexToJoystickTypes[joystickComponents[joystickIndex].typeSpinner.getSelectedItemPosition()],
                        true));
                Toast.makeText(this, getString(R.string.port_has_been_added),
                        Toast.LENGTH_LONG).show();
            } catch(Exception e) {
                Toast.makeText(this, getString(R.string.error_while_creating_settings_xml,
                        e.getLocalizedMessage()), Toast.LENGTH_LONG).show();
            }
        }
    }

    class ButtonTag {
        KeyGroupComponents keyGroupComponents = null;
        boolean addButton = false;
        ButtonTag(KeyGroupComponents keyGroupComponents, boolean addButton) {
            this.keyGroupComponents = keyGroupComponents;
            this.addButton = addButton;
        }
    }

    class KeyGroupComponents {
        int index;
        private Activity activity = null;
        LinearLayout keyGroupLayout, portPanelLayout, portsLayout, activityLayout;
        TextView textViewTitle, textViewPortsTitle;
        CheckBox checkBoxActive;
        LinearLayout contentLayout;
        Spinner spinnerType, spinnerBehavior1, spinnerBehavior2;
        LinearLayout nonMailboxLayout;
        LinearLayout mailboxLayout;
        EditText editTextIncX, editTextIncY, editTextDecX, editTextDecY;
        EditText editTextStepXPause, editTextStepYPause;
        EditText editTextMailbox;
        KeyCodeControl upKeysControl, leftKeysControl, downKeysControl, rightKeysControl, keysControl;
        Button addButton, deleteButton;

        public List<OutputPortComponents> outputPorts = new ArrayList<OutputPortComponents>();

        public String getKeyCodeTitle() {
            return textViewTitle.getText().toString();
        }

        KeyGroupComponents(final EV3SettingsActivity activity, LinearLayout layout, int index, boolean active,
                           int type, int incX, int incY, int decX, int decY,
                           int stepXPause, int stepYPause, HashSet<Integer> upKeyCodes,
                           HashSet<Integer> leftKeyCodes, HashSet<Integer> downKeyCodes,
                           HashSet<Integer> rightKeyCodes, HashSet<Integer> keyCodes,
                           String mailbox, int behavior1, int behavior2) {
            this.activity = activity;
            this.index = index;
            this.activityLayout = layout;
            //KeyGroup layout
            keyGroupLayout = new LinearLayout(activity);
            keyGroupLayout.setOrientation(LinearLayout.VERTICAL);
            layout.addView(keyGroupLayout);
            //Title
            ContextThemeWrapper newContext = new ContextThemeWrapper(activity,
                    R.style.SettingsSectionTitle);
            textViewTitle = new TextView(newContext);
            textViewTitle.setText(getString(R.string.keygroup_n, Integer.toString(index + 1)));
            keyGroupLayout.addView(textViewTitle);
            //Active
            checkBoxActive = SettingsActivityHelper.createCheckBox(activity,
                    keyGroupLayout, active, R.string.keygroup_active, CHECKBOX_PADDING);
            //Settings content
            contentLayout = new LinearLayout(activity);
            if (!active)
                contentLayout.setVisibility(View.GONE);
            contentLayout.setOrientation(LinearLayout.VERTICAL);
            keyGroupLayout.addView(contentLayout);
            //Separator
            SettingsActivityHelper.createSeparator(activity, contentLayout);
            //Type
            spinnerType = SettingsActivityHelper.createSpinner(activity, contentLayout,
                    Arrays.asList(new String[]{
                            activity.getString(R.string.joystick_type_independent_motors),
                            activity.getString(R.string.keygroup_type_steering),
                            activity.getString(R.string.joystick_type_mailbox),
                    }),
                    type > 2 ? 2 : type, R.string.joystick_type, null);
            //Non mailbox layout
            nonMailboxLayout = new LinearLayout(activity);
            nonMailboxLayout.setVisibility(type != RoboCamDriver.JOYSTICK_TYPE_MAILBOX
                    ? View.VISIBLE : View.GONE);
            nonMailboxLayout.setOrientation(LinearLayout.VERTICAL);
            contentLayout.addView(nonMailboxLayout);
            //Separator
            SettingsActivityHelper.createSeparator(activity, nonMailboxLayout);
            //Behavior1
            spinnerBehavior1 = SettingsActivityHelper.createSpinner(activity, nonMailboxLayout,
                    Arrays.asList(new String[]{
                            activity.getString(R.string.joystick_behavior_return_to_zero),
                            activity.getString(R.string.joystick_behavior_hold_position)
                    }),
                    behavior1, R.string.keygroup_behavior1, null);
            //Separator
            SettingsActivityHelper.createSeparator(activity, nonMailboxLayout);
            //Behavior2
            spinnerBehavior2 = SettingsActivityHelper.createSpinner(activity, nonMailboxLayout,
                    Arrays.asList(new String[]{
                            activity.getString(R.string.joystick_behavior_return_to_zero),
                            activity.getString(R.string.joystick_behavior_hold_position)
                    }),
                    behavior2, R.string.keygroup_behavior2, null);
            //Separator
            SettingsActivityHelper.createSeparator(activity, nonMailboxLayout);
            //Steps
            editTextIncX = SettingsActivityHelper.createEditTextInteger(activity, nonMailboxLayout,
                    incX, R.string.inc_x, R.string.inc_x_desc);
            SettingsActivityHelper.createSeparator(activity, nonMailboxLayout);
            editTextIncY = SettingsActivityHelper.createEditTextInteger(activity, nonMailboxLayout,
                    incY, R.string.inc_y, R.string.inc_y_desc);
            SettingsActivityHelper.createSeparator(activity, nonMailboxLayout);
            editTextDecX = SettingsActivityHelper.createEditTextInteger(activity, nonMailboxLayout,
                    decX, R.string.dec_x, R.string.dec_x_desc);
            SettingsActivityHelper.createSeparator(activity, nonMailboxLayout);
            editTextDecY = SettingsActivityHelper.createEditTextInteger(activity, nonMailboxLayout,
                    decY, R.string.dec_y, R.string.dec_y_desc);
            SettingsActivityHelper.createSeparator(activity, nonMailboxLayout);
            editTextStepXPause = SettingsActivityHelper.createEditTextInteger(activity, nonMailboxLayout,
                    stepXPause, R.string.step_x_pause, 0);
            SettingsActivityHelper.createSeparator(activity, nonMailboxLayout);
            editTextStepYPause = SettingsActivityHelper.createEditTextInteger(activity, nonMailboxLayout,
                    stepYPause, R.string.step_y_pause, 0);
            //Separator
            SettingsActivityHelper.createSeparator(activity, nonMailboxLayout);
            //Keys
            upKeysControl = KeyCodeControl.createKeyCodeControl(activity, nonMailboxLayout,
                    upKeyCodes, R.string.up_keys, getKeyCodeTitle());
            upKeysControl.setOnClickListener(activity);
            SettingsActivityHelper.createSeparator(activity, nonMailboxLayout);
            leftKeysControl = KeyCodeControl.createKeyCodeControl(activity, nonMailboxLayout,
                    leftKeyCodes, R.string.left_keys, getKeyCodeTitle());
            leftKeysControl.setOnClickListener(activity);
            SettingsActivityHelper.createSeparator(activity, nonMailboxLayout);
            downKeysControl = KeyCodeControl.createKeyCodeControl(activity, nonMailboxLayout,
                    downKeyCodes, R.string.down_keys, getKeyCodeTitle());
            downKeysControl.setOnClickListener(activity);
            SettingsActivityHelper.createSeparator(activity, nonMailboxLayout);
            rightKeysControl = KeyCodeControl.createKeyCodeControl(activity, nonMailboxLayout,
                    rightKeyCodes, R.string.right_keys, getKeyCodeTitle());
            rightKeysControl.setOnClickListener(activity);
            //Ports title
            newContext = new ContextThemeWrapper(activity, R.style.SettingsSectionTitle);
            textViewPortsTitle = new TextView(newContext);
            textViewPortsTitle.setText(getString(R.string.keygroup_ports, index + 1));
            nonMailboxLayout.addView(textViewPortsTitle);

            //Ports buttons
            portPanelLayout = new LinearLayout(activity);
            portPanelLayout.setOrientation(LinearLayout.HORIZONTAL);
            portPanelLayout.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            portPanelLayout.setBaselineAligned(false);
            portPanelLayout.setGravity(Gravity.RIGHT);
            nonMailboxLayout.addView(portPanelLayout);

            deleteButton = SettingsActivityHelper.createButton(activity, portPanelLayout, R.string.delete);
            deleteButton.setTag(new ButtonTag(this, false));
            deleteButton.setOnClickListener(activity);
            registerForContextMenu(deleteButton);
            SettingsActivityHelper.createVerticalSeparator(activity, portPanelLayout);
            addButton = SettingsActivityHelper.createButton(activity, portPanelLayout, R.string.add);
            addButton.setTag(new ButtonTag(this, true));
            addButton.setOnClickListener(activity);

            portsLayout = new LinearLayout(activity);
            portsLayout.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            portsLayout.setOrientation(LinearLayout.VERTICAL);
            nonMailboxLayout.addView(portsLayout);

            //Mailbox layout
            mailboxLayout = new LinearLayout(activity);
            mailboxLayout.setVisibility(type == RoboCamDriver.JOYSTICK_TYPE_MAILBOX
                    ? View.VISIBLE : View.GONE);
            mailboxLayout.setOrientation(LinearLayout.VERTICAL);
            contentLayout.addView(mailboxLayout);
            //Separator
            SettingsActivityHelper.createSeparator(activity, mailboxLayout);
            //Mailbox name
            editTextMailbox = SettingsActivityHelper.createEditText(activity, mailboxLayout,
                    mailbox, R.string.mailbox, 0);
            //Separator
            SettingsActivityHelper.createSeparator(activity, mailboxLayout);
            //Keys
            keysControl = KeyCodeControl.createKeyCodeControl(activity, mailboxLayout,
                    keyCodes, R.string.keys, getKeyCodeTitle());
            keysControl.setOnClickListener(activity);

            checkBoxActive.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    contentLayout.setVisibility(isChecked ? View.VISIBLE : View.GONE);
                }
            });

            spinnerType.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {

                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    nonMailboxLayout.setVisibility(position != RoboCamDriver.JOYSTICK_TYPE_MAILBOX
                            ? View.VISIBLE : View.GONE);
                    mailboxLayout.setVisibility(position == RoboCamDriver.JOYSTICK_TYPE_MAILBOX
                            ? View.VISIBLE : View.GONE);
                    for (OutputPortComponents portComponents : outputPorts)
                        portComponents.updateGroupSpinner(spinnerType.getSelectedItemPosition());
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {
                }
            });
        }

        public KeyCodeControl findKeyCodeControl(String keyCodeControlTitle) {
            if (upKeysControl.getTitle().equals(keyCodeControlTitle))
                return upKeysControl;
            else if (leftKeysControl.getTitle().equals(keyCodeControlTitle))
                return leftKeysControl;
            else if (downKeysControl.getTitle().equals(keyCodeControlTitle))
                return downKeysControl;
            else if (rightKeysControl.getTitle().equals(keyCodeControlTitle))
                return rightKeysControl;
            else if (keysControl.getTitle().equals(keyCodeControlTitle))
                return keysControl;
            return null;
        }

        public void removeViews() {
            activityLayout.removeView(keyGroupLayout);
        }

        public void updateIndex(int index) {
            this.index = index;
            textViewTitle.setText(getString(R.string.keygroup_n, Integer.toString(index + 1)));
            textViewPortsTitle.setText(getString(R.string.keygroup_ports, index + 1));
            for (int i = 0; i < outputPorts.size(); i++)
                outputPorts.get(i).updateTitle(index);
        }
    }

    public void onAddKeyGroupButtonClick(View v){
        try {
            keyGroupComponents.add(new KeyGroupComponents(this,
                    (LinearLayout)findViewById(R.id.linearLayoutKeyGroups),
                    keyGroupComponents.size(), true, RoboCamDriver.JOYSTICK_TYPE_INDEPENDENT_MOTORS,
                    0, 0, 0, 0, 100, 100, null, null, null, null, null, "",
                    RoboCamDriver.JOYSTICK_BEHAVIOR_RETURN_TO_ZERO,
                    RoboCamDriver.JOYSTICK_BEHAVIOR_RETURN_TO_ZERO));
            Toast.makeText(this, getString(R.string.keygroup_has_been_added),
                    Toast.LENGTH_LONG).show();
        } catch(Exception e) {
            Toast.makeText(this, getString(R.string.error_while_creating_settings_xml,
                    e.getLocalizedMessage()), Toast.LENGTH_LONG).show();
        }
    }

    public void onDeleteKeyGroupClick(View v){
        v.showContextMenu();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

}
