package ru.proghouse.robocam;

import android.app.ActionBar;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Point;
import android.net.Uri;
import android.os.Build;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.ContextMenu;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Adapter;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import ru.proghouse.robocam.drivers.EV3.EV3Driver;
import ru.proghouse.robocam.drivers.RoboCamDriver;

public class EV3SettingsActivity extends AppCompatActivity  implements View.OnClickListener {

    public static final String SETTINGS_FILE_NAME = "SettingsFileName";
    public static final String SETTINGS_XML = "SettingsXml";
    private String settingsFileName;
    private JoystickComponents[] joystickComponents = new JoystickComponents[4];
    private int CHECKBOX_PADDING = 0;
    private EditText editTextBotName = null;
    private EditText editTextBotDesc = null;

    private Button buttonDeletePortJoystick1 = null;
    private Button buttonDeletePortJoystick2 = null;
    private Button buttonDeletePortJoystick3 = null;
    private Button buttonDeletePortJoystick4 = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ev3_settings);

        if (Build.VERSION.SDK_INT < 17) {
            CheckBox rb = new CheckBox(this);
            CHECKBOX_PADDING = rb.getPaddingLeft();
        }

        editTextBotName = (EditText)findViewById(R.id.editTextBotName);
        editTextBotDesc = (EditText)findViewById(R.id.editTextDesc);

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
                        e.getMessage()), Toast.LENGTH_LONG).show();
            }
        } else {
            File settingsFile = getSettingsFile();
            if (settingsFile.exists()) {
                try {
                    loadSettings(settingsFile);
                } catch (Exception e) {
                    Toast.makeText(this, getString(R.string.error_while_opening_settings_file,
                            settingsFileName, e.getMessage()), Toast.LENGTH_LONG).show();
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
        File cacheDir = getCacheDir();
        File ev3Dir = new File(cacheDir, DefaultValue.ROBOT_SETTINGS_DIRECTORY);
        ev3Dir.mkdirs();
        return new File(ev3Dir, settingsFileName);
    }

    private void loadSettingsFromXml(Document xml) throws Exception {
        xml.getDocumentElement().normalize();
        String newDriverName = xml.getDocumentElement().getNodeName();
        if (!newDriverName.equals("EV3"))
            throw new Exception(getString(R.string.unknown_driver_name, newDriverName));
        editTextBotName.setText(xml.getDocumentElement().getAttribute("Name"));
        editTextBotDesc.setText(xml.getDocumentElement().getAttribute("Description"));
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
                joystickComponents.typeSpinner.setSelection(StringHelper.intFromString(
                        joystickNode.getAttribute("Type"),
                        RoboCamDriver.JOYSTICK_TYPE_INDEPENDENT_MOTORS));
                joystickComponents.behavior1Spinner.setSelection(StringHelper.intFromString(
                        joystickNode.getAttribute("Behavior0"),
                        RoboCamDriver.JOYSTICK_BEHAVIOR_RETURN_TO_ZERO));
                joystickComponents.behavior2Spinner.setSelection(StringHelper.intFromString(
                        joystickNode.getAttribute("Behavior1"),
                        RoboCamDriver.JOYSTICK_BEHAVIOR_RETURN_TO_ZERO));
                NodeList outputPortNodes = joystickNode.getElementsByTagName("OutputPort");
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
                            joystickComponents.outputPorts.add(new OutputPortComponents(this,
                                    joystickComponents.joystickPortsLinearLayout, joystickIndex,
                                    group, layer, portName, joystickType, power, invert, coefficient,
                                    brake, joystickComponents.typeSpinner.getSelectedItemPosition()));

                        }
                        catch(Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        }
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
        return super.onContextItemSelected(item);
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
            int portVisibility = typeSpinner.getSelectedItemPosition() == RoboCamDriver.JOYSTICK_TYPE_MAILBOX ? View.GONE : View.VISIBLE;
            joystickPortsTextView.setVisibility(portVisibility);
            joystickPortsLinearLayout.setVisibility(portVisibility);
            joystickPortButtonsLinearLayout.setVisibility(portVisibility);
            for (OutputPortComponents portComponents : outputPorts) {
                portComponents.setTypeVisibility(typeSpinner.getSelectedItemPosition() != RoboCamDriver.JOYSTICK_TYPE_STEERING);
                portComponents.updateGroupSpinner(typeSpinner.getSelectedItemPosition());
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
            SpinnerHelper.initSpinner(shapeSpinner, activity, Arrays.asList(new String[]{
                    activity.getString(R.string.joystick_shape_vertical),
                    activity.getString(R.string.joystick_shape_horizontal),
                    activity.getString(R.string.joystick_shape_circular),
                    activity.getString(R.string.joystick_shape_quadratic),
                    activity.getString(R.string.joystick_shape_arrows),
                    activity.getString(R.string.joystick_shape_vertical_arrows),
                    activity.getString(R.string.joystick_shape_horizontal_arrows)}), R.string.joystick_shape);
            typeSpinner = (Spinner) activity.findViewById(spinnerJoystickType);
            SpinnerHelper.initSpinner(typeSpinner, activity, Arrays.asList(new String[]{
                    activity.getString(R.string.joystick_type_independent_motors),
                    activity.getString(R.string.joystick_type_steering),
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
            SpinnerHelper.initSpinner(behavior1Spinner, activity, Arrays.asList(new String[]{
                    activity.getString(R.string.joystick_behavior_return_to_zero),
                    activity.getString(R.string.joystick_behavior_hold_position)}), R.string.joystick_behavior1);
            behavior2Spinner = (Spinner) activity.findViewById(spinnerJoystickBehavior2);
            SpinnerHelper.initSpinner(behavior2Spinner, activity, Arrays.asList(new String[]{
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
            adapter.getObjects().set(2, adapter.getActivity().getString(R.string.joystick_type_mailbox)
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
        int joystickIndex;
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

        public OutputPortComponents(Activity activity, LinearLayout layout, int joystickIndex,
                                    int group, int layer, String portName, int portJoystickType, int power,
                                    boolean invert, float coefficient, int brake, int joystickType) {

            this.joystickIndex = joystickIndex;
            activityLayout = layout;

            //OutputPort layout
            portLayout = new LinearLayout(activity);
            portLayout.setOrientation(LinearLayout.VERTICAL);
            layout.addView(portLayout);
            //Title
            ContextThemeWrapper newContext  = new ContextThemeWrapper(activity,
                    R.style.SettingsSectionTitle);
            textViewTitle = new TextView(newContext);
            textViewTitle.setText(getString(R.string.joystick_n_port_n, joystickIndex + 1, portName));
            portLayout.addView(textViewTitle);
            //Joystick group
            spinnerGroup = createSpinner(activity, portLayout, Arrays.asList(new String[]{
                    activity.getString(joystickType == RoboCamDriver.JOYSTICK_TYPE_INDEPENDENT_MOTORS
                            ? R.string.joystick_group_horizontal : R.string.joystick_group_left),
                    activity.getString(joystickType == RoboCamDriver.JOYSTICK_TYPE_INDEPENDENT_MOTORS
                            ? R.string.joystick_group_vertical : R.string.joystick_group_right)}),
                    group, joystickType == RoboCamDriver.JOYSTICK_TYPE_INDEPENDENT_MOTORS
                            ? R.string.joystick_group_axis : R.string.joystick_group_motor, null);
            //Separator
            createSeparator(activity, portLayout);
            //EV3 level
            spinnerLayer = createSpinner(activity, portLayout, Arrays.asList(new String[]{
                    "1", "2", "3", "4"}), layer, R.string.joystick_layer, null);
            //Separator
            createSeparator(activity, portLayout);
            //Port number
            spinnerNumber = createSpinner(activity, portLayout, Arrays.asList(new String[]{
                            "A", "B", "C", "D"}),
                    portName.equals("A") ? 0 : portName.equals("B") ? 1 : portName.equals("C") ? 2 : 3,
                    R.string.joystick_port_number, null);
            spinnerNumber.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {

                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    updateTitle();
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {

                }
            });
            //Separator
            createSeparator(activity, portLayout);

            //Joystick type (changeable value)
            LinearLayout[] spinnerLayout = new LinearLayout[1];
            spinnerJoystickType = createSpinner(activity, portLayout, Arrays.asList(new String[]{
                    activity.getString(R.string.joystick_type_power),
                    activity.getString(R.string.joystick_type_angle)}),
                    portJoystickType, R.string.joystick_type_cv, spinnerLayout);
            layoutJoystickType = spinnerLayout[0];
            //Separator
            separatorJoystickType = createSeparator(activity, portLayout);
            setTypeVisibility(joystickType != RoboCamDriver.JOYSTICK_TYPE_STEERING);
            //Power
            textViewPower = createTextViewTitle(activity, portLayout, R.string.joystick_power);
            editTextPower = createEditTextInteger(activity, portLayout, power,
                    R.string.joystick_power, false);
            //Separator
            separatorPower = createSeparator(activity, portLayout);
            //Invert
            checkBoxInvert = createCheckBox(activity, portLayout, invert, R.string.joystick_invert);
            //Separator
            createSeparator(activity, portLayout);
            //Brake
            checkBoxBrake = createCheckBox(activity, portLayout, brake == EV3Driver.BRAKE,
                    R.string.joystick_brake);
            //Separator
            createSeparator(activity, portLayout);
            //Coefficient
            editTextCoefficient = createEditTextFloat(activity, portLayout, coefficient,
                    R.string.joystick_coefficient);

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

        private void updateTitle() {
            textViewTitle.setText(getString(R.string.joystick_n_port_n, joystickIndex + 1,
                    new String[]{"A", "B", "C", "D"}[spinnerNumber.getSelectedItemPosition()]));
        }

        private void updateEditTextPower() {
            int visibility = spinnerJoystickType.getSelectedItemPosition() == 1 ? View.VISIBLE : View.GONE;
            editTextPower.setVisibility(visibility);
            textViewPower.setVisibility(visibility);
            separatorPower.setVisibility(visibility);
        }

        private ImageView createSeparator(Activity activity, LinearLayout layout) {
            ImageView separator = new ImageView(activity);
            separator.setImageResource(R.drawable.spacer_small);
            layout.addView(separator);
            return separator;
        }

        private TextView createTextViewTitle(Activity activity, LinearLayout layout, int titleId) {
            ContextThemeWrapper newContext  = new ContextThemeWrapper(activity,
                    R.style.SettingsSectionEditTextTitle);
            TextView textView = new TextView(newContext, null,
                    R.style.SettingsSectionEditTextTitle);
            textView.setText(getString(titleId));
            layout.addView(textView);
            return textView;
        }

        private EditText createEditTextFloat(Activity activity, LinearLayout layout, float value,
                                             int titleId) {
            createTextViewTitle(activity, layout, titleId);
            ContextThemeWrapper newContext = new ContextThemeWrapper(activity, R.style.SettingsEditTextFloat);
            EditText editText = new EditText(newContext, null, R.style.SettingsEditTextFloat);
            editText.setText(Float.toString(value));
            layout.addView(editText);
            return editText;
        }

        private EditText createEditTextInteger(Activity activity, LinearLayout layout, int value,
                                             int titleId, boolean createTextView) {
            if (createTextView)
                createTextViewTitle(activity, layout, titleId);
            ContextThemeWrapper newContext = new ContextThemeWrapper(activity, R.style.SettingsEditTextInteger);
            EditText editText = new EditText(newContext, null, R.style.SettingsEditTextInteger);
            editText.setText(Integer.toString(value));
            layout.addView(editText);
            return editText;
        }

        private CheckBox createCheckBox(Activity activity, LinearLayout layout, boolean value,
                                        int titleId) {
            ContextThemeWrapper newContext = new ContextThemeWrapper(activity, R.style.SettingsCheckBox);
            CheckBox checkBox = new CheckBox(newContext, null, R.style.SettingsCheckBox);
            checkBox.setHeight(Math.round(TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP, 56, getResources().getDisplayMetrics())));
            checkBox.setChecked(value);
            checkBox.setText(titleId);
            //http://stackoverflow.com/questions/4037795/android-spacing-between-checkbox-and-text
            checkBox.setPadding(CHECKBOX_PADDING, checkBox.getPaddingTop(),
                    checkBox.getPaddingRight(), checkBox.getPaddingBottom());
            layout.addView(checkBox);
            return checkBox;
        }

        private Spinner createSpinner(Activity activity, LinearLayout layout, List<String> objects,
                                      int position, int titleId, LinearLayout[] _spinnerLayout) {
            LinearLayout spinnerLayout = new LinearLayout(activity);
            if (_spinnerLayout != null && _spinnerLayout.length > 0)
                _spinnerLayout[0] = spinnerLayout;
            spinnerLayout.setOrientation(LinearLayout.HORIZONTAL);
            spinnerLayout.setWeightSum(1);
            layout.addView(spinnerLayout);

            ContextThemeWrapper newContext  = new ContextThemeWrapper(activity, R.style.SettingsSpinner);
            Spinner spinner = new Spinner(newContext, null, R.style.SettingsSpinner);
            SpinnerHelper.initSpinner(spinner, activity, objects, titleId);
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

        public void removeViews() {
            activityLayout.removeView(portLayout);
        }

        public void updateGroupSpinner(int joystickType) {
            int position = spinnerGroup.getSelectedItemPosition();
            CustomAdapter adapter = (CustomAdapter)spinnerGroup.getAdapter();
            adapter.setTitleResId(joystickType == RoboCamDriver.JOYSTICK_TYPE_INDEPENDENT_MOTORS
                    ? R.string.joystick_group_axis : R.string.joystick_group_motor);
            adapter.getObjects().set(0, adapter.getActivity().getString(
                    joystickType == RoboCamDriver.JOYSTICK_TYPE_INDEPENDENT_MOTORS
                            ? R.string.joystick_group_horizontal : R.string.joystick_group_left));
            adapter.getObjects().set(1, adapter.getActivity().getString(
                    joystickType == RoboCamDriver.JOYSTICK_TYPE_INDEPENDENT_MOTORS
                            ? R.string.joystick_group_vertical : R.string.joystick_group_right));
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
            if (joystickComponent.typeSpinner.getSelectedItemPosition() != RoboCamDriver.JOYSTICK_TYPE_INDEPENDENT_MOTORS
                    || joystickComponent.visibilityCheckBox.isChecked())
                joystickElement.setAttribute("Type",
                        Integer.toString(joystickComponent.typeSpinner.getSelectedItemPosition()));
            if (joystickComponent.behavior1Spinner.getSelectedItemPosition() != RoboCamDriver.JOYSTICK_BEHAVIOR_RETURN_TO_ZERO)
                joystickElement.setAttribute("Behavior0",
                        Integer.toString(joystickComponent.behavior1Spinner.getSelectedItemPosition()));
            if (joystickComponent.behavior2Spinner.getSelectedItemPosition() != RoboCamDriver.JOYSTICK_BEHAVIOR_RETURN_TO_ZERO)
                joystickElement.setAttribute("Behavior1",
                        Integer.toString(joystickComponent.behavior2Spinner.getSelectedItemPosition()));
            for (OutputPortComponents outputPortComponent : joystickComponent.outputPorts) {
                Element portElement = xml.createElement("OutputPort");
                joystickElement.appendChild(portElement);
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
                if (!outputPortComponent.editTextCoefficient.getText().toString().equals("1"))
                    portElement.setAttribute("Coefficient", outputPortComponent.editTextCoefficient.getText().toString());
                if (outputPortComponent.checkBoxBrake.isChecked())
                    portElement.setAttribute("Brake", "1");
            }
            index++;
        }
        return xml;
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
            Toast.makeText(this, getString(R.string.error_while_creating_settings_xml),
                    Toast.LENGTH_LONG).show();
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

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_ev3_settings, menu);
        return true;
    }

    @Override
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
            Toast.makeText(this, getString(R.string.error_while_creating_settings_xml),
                    Toast.LENGTH_LONG).show();
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
                        joystickComponents[joystickIndex].typeSpinner.getSelectedItemPosition()));
                Toast.makeText(this, getString(R.string.port_has_been_added),
                        Toast.LENGTH_LONG).show();
            } catch(Exception e) {
                Toast.makeText(this, getString(R.string.error_while_creating_settings_xml),
                        Toast.LENGTH_LONG).show();
            }
        }
    }
}
