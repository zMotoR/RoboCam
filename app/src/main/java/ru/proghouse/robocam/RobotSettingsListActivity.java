package ru.proghouse.robocam;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Path;
import android.os.Build;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Xml;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.w3c.dom.Document;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import ru.proghouse.robocam.drivers.EV3.EV3Driver;
import ru.proghouse.robocam.drivers.RoboCamDriver;
import ru.proghouse.robocam.util.IabHelper;
import ru.proghouse.robocam.util.IabResult;
import ru.proghouse.robocam.util.Inventory;

public class RobotSettingsListActivity extends AppCompatActivity implements View.OnClickListener {
    private static final int MI_ADD_EV3_SETTINGS = -1;
    private Button buttonAdd = null;
    private Button buttonDelete = null;
    private File cacheDir, ev3Dir, ev3DefaultSettingsFile;
    private ListView settingsListView = null;
    private Spinner spinnerCurrentRobotSettings = null;
    private List<SettingsTitle> settingsList = null;
    private SharedPreferences.Editor editor = null;
    private Context thisContext = null;
    private String currentRobotSettings = null;
    private Date lastSettingsModified = null;
    private IabHelper mHelper;
    private IabHelper.QueryInventoryFinishedListener mGotInventoryListener;
    private boolean isPremium = false;
    private LinearLayout robot_settings_list_main_layout = null;
    private TextView textViewSubsWarning = null;
    private LinearLayout linearLayoutSubsWarning = null;
    private static final String WARNING_HIDED = "WarningHided";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_robot_settings_list);

        thisContext = this;

        robot_settings_list_main_layout = (LinearLayout)findViewById(R.id.robot_settings_list_main_layout);
        robot_settings_list_main_layout.setEnabled(false);
        textViewSubsWarning = (TextView)findViewById(R.id.textViewSubsWarning);
        linearLayoutSubsWarning = (LinearLayout)findViewById(R.id.linearLayoutSubsWarning);

        if (savedInstanceState != null && savedInstanceState.getBoolean(WARNING_HIDED))
            linearLayoutSubsWarning.setVisibility(View.GONE);

        buttonAdd = (Button)findViewById(R.id.buttonAdd);
        buttonAdd.setOnClickListener(this);
        registerForContextMenu(buttonAdd);

        buttonDelete = (Button)findViewById(R.id.buttonDelete);
        buttonDelete.setOnClickListener(this);
        registerForContextMenu(buttonDelete);

        cacheDir = getCacheDir();
        ev3Dir = new File(cacheDir, DefaultValue.ROBOT_SETTINGS_DIRECTORY);
        ev3Dir.mkdirs();

        settingsListView = (ListView)findViewById(R.id.settingsListView);

        fillSettinsList();

        lastSettingsModified = new Date();
        RoboCamBroker.setLastSettingsModified(lastSettingsModified);

        settingsListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View view,
                                    int position, long id) {
                SettingsTitle selectedSettings = (SettingsTitle) settingsListView.getAdapter().getItem(position);
                Intent intent = new Intent(thisContext, EV3SettingsActivity.class);
                intent.putExtra(EV3SettingsActivity.SETTINGS_FILE_NAME, selectedSettings.file.getName());
                startActivity(intent);
            }
        });

        spinnerCurrentRobotSettings = (Spinner)findViewById(R.id.spinnerCurrentRobotSettings);

        fillCurSettings();

        //-----------------
        /*List<String> currentSettings = new ArrayList<String>();
        for (SettingsTitle settingsTitle : settingsList)
            currentSettings.add(settingsTitle.title);
        ArrayAdapter<?> adapterCurrentRobotSettings = new CustomAdapter(this,
                spinnerCurrentRobotSettings, R.layout.spinner_item,
                currentSettings, R.string.current_robot_settings);
        adapterCurrentRobotSettings.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerCurrentRobotSettings.setAdapter(adapterCurrentRobotSettings);
        spinnerCurrentRobotSettings.setPromptId(R.string.current_robot_settings);

        SharedPreferences settings = getSharedPreferences(ExtraKey.APP_PREFERENCE, Context.MODE_PRIVATE);
        currentRobotSettings = settings.getString(ExtraKey.CURRENT_ROBOT_SETTINGS, "");
        int index = -1;
        for (int i = 0; i < settingsList.size(); i++) {
            if (settingsList.get(i).file.getName().equals(currentRobotSettings)) {
                index = i;
                break;
            }
        }
        if (index > 0)
            spinnerCurrentRobotSettings.setSelection(index);*/

        //-------------------

        spinnerCurrentRobotSettings.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parent,
                                       View itemSelected, int selectedItemPosition, long selectedId) {
                String fileName = settingsList.get(selectedItemPosition).file.getName();
                if (!currentRobotSettings.equals(fileName)) {
                    getPreferenceEditor().putString(ExtraKey.CURRENT_ROBOT_SETTINGS, fileName);
                    apply();
                    RoboCamDriver.updateCurrentDriver(thisContext, isPremium);
                    Toast.makeText(thisContext, R.string.current_robot_settings_have_changed, Toast.LENGTH_LONG).show();
                    currentRobotSettings = fileName;
                }
            }

            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        String base64EncodedPublicKey = "";
        mHelper = new IabHelper(this, base64EncodedPublicKey);
        mGotInventoryListener = new IabHelper.QueryInventoryFinishedListener() {
            public void onQueryInventoryFinished(IabResult result,
                                                 Inventory inventory) {
                if (result.isFailure()) {
                    // handle error here
                }
                else {
                    // does the user have the premium upgrade?
                    isPremium = inventory.hasPurchase(MainActivity.SKU_PREMIUM);
                    if (isPremium)
                        linearLayoutSubsWarning.setVisibility(View.GONE);
                }
                // update UI accordingly
                robot_settings_list_main_layout.setEnabled(true);
            }
        };
        mHelper.startSetup(new IabHelper.OnIabSetupFinishedListener() {
            public void onIabSetupFinished(IabResult result) {
                if (!result.isSuccess()) {
                    // Oh noes, there was a problem.
                    Toast.makeText(thisContext, "Problem setting up In-app Billing: " + result, Toast.LENGTH_LONG).show();
                } else {
                    // Hooray, IAB is fully set up!
                    try {
                        mHelper.queryInventoryAsync(mGotInventoryListener);
                    } catch (IabHelper.IabAsyncInProgressException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        try {
            if (mHelper != null)
                mHelper.dispose();
        } catch (IabHelper.IabAsyncInProgressException e) {
            e.printStackTrace();
        }
        mHelper = null;
    }

    @Override
    protected void onSaveInstanceState (Bundle outState) {
        outState.putBoolean(WARNING_HIDED, linearLayoutSubsWarning.getVisibility() == View.GONE);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onRestoreInstanceState (Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        if (savedInstanceState.getBoolean(WARNING_HIDED))
            linearLayoutSubsWarning.setVisibility(View.GONE);
    }

    private void fillSettinsList() {
        settingsList = new ArrayList<SettingsTitle>();
        File[] files = ev3Dir.listFiles();
        for (File file : files) {
            if (file.getName().trim().endsWith(".xml")) {
                try {
                    DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
                    DocumentBuilder db = dbf.newDocumentBuilder();
                    Document xml = db.parse(file);
                    xml.getDocumentElement().normalize();
                    if (!xml.getDocumentElement().getNodeName().equals("EV3"))
                        throw new Exception(getString(R.string.unknown_driver_name,
                                xml.getDocumentElement().getNodeName()));
                    settingsList.add(new SettingsTitle(xml.getDocumentElement().getNodeName(),
                            xml.getDocumentElement().getAttribute("Name"),
                            xml.getDocumentElement().getAttribute("Description"),
                            file));
                } catch (Throwable e) {
                    Toast.makeText(this, getString(R.string.error_while_opening_settings_file,
                            file.getName(), e.getMessage()), Toast.LENGTH_LONG).show();
                }
            }
        }
        Collections.sort(settingsList, new Comparator<SettingsTitle>() {
            @Override
            public int compare(SettingsTitle lhs, SettingsTitle rhs) {
                long result = rhs.file.lastModified() - lhs.file.lastModified();
                return result == 0 ? 0 : result < 0 ? -1 : 1;
            }
        });
        ArrayAdapter<SettingsTitle> adapter = new ArrayAdapter<SettingsTitle>(this,
                R.layout.spinner_item, settingsList){
            @Override
            public View getView(int position, View convertView, ViewGroup parent){
                if (convertView == null) {
                    LayoutInflater inflater = getLayoutInflater();
                    convertView = inflater.inflate(R.layout.spinner_item, parent, false);
                }
                final SettingsTitle settingsTitle = getItem(position);
                ((TextView) convertView.findViewById(R.id.textView1)).setText(
                        settingsTitle.title);
                ((TextView) convertView.findViewById(R.id.textView2)).setText(
                        settingsTitle.driverName + ": " + settingsTitle.desc);
                return convertView;
            }
        };
        settingsListView.setAdapter(adapter);
    }

    private void fillCurSettings() {
        List<String> currentSettings = new ArrayList<String>();
        for (SettingsTitle settingsTitle : settingsList)
            currentSettings.add(settingsTitle.title);
        ArrayAdapter<?> adapterCurrentRobotSettings = new CustomAdapter(this,
                spinnerCurrentRobotSettings, R.layout.spinner_item,
                currentSettings, R.string.current_robot_settings);
        adapterCurrentRobotSettings.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerCurrentRobotSettings.setAdapter(adapterCurrentRobotSettings);
        spinnerCurrentRobotSettings.setPromptId(R.string.current_robot_settings);

        SharedPreferences settings = getSharedPreferences(ExtraKey.APP_PREFERENCE, Context.MODE_PRIVATE);
        currentRobotSettings = settings.getString(ExtraKey.CURRENT_ROBOT_SETTINGS, "");
        int index = -1;
        for (int i = 0; i < settingsList.size(); i++) {
            if (settingsList.get(i).file.getName().equals(currentRobotSettings)) {
                index = i;
                break;
            }
        }
        if (index > 0)
            spinnerCurrentRobotSettings.setSelection(index);

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

    class SettingsTitle {
        public String driverName;
        public String title;
        public String desc;
        public File file;

        public SettingsTitle(String driverName, String title, String desc, File file) {
            this.driverName = driverName;
            this.title = title;
            this.desc = desc;
            this.file = file;
        }
    }

    /*@Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_robot_settings_list, menu);
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

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        if (v.getId() == R.id.buttonAdd)
            menu.add(Menu.NONE, MI_ADD_EV3_SETTINGS, Menu.NONE, R.string.action_add_ev3_settings);
        else if (v.getId() == R.id.buttonDelete) {
            for (int i = 0; i < settingsListView.getAdapter().getCount(); i++) {
                SettingsTitle selectedSettings = (SettingsTitle) settingsListView.getAdapter().getItem(i);
                menu.add(Menu.NONE, i + 1, i + 1, selectedSettings.title);
            }
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case MI_ADD_EV3_SETTINGS:
                Intent intent = new Intent(this, EV3SettingsActivity.class);
                startActivity(intent);
                break;
            default:
                if (item.getItemId() > 0 && item.getItemId() <= settingsListView.getAdapter().getCount()) {
                    SettingsTitle selectedSettings =
                            (SettingsTitle) settingsListView.getAdapter().getItem(item.getItemId() - 1);
                    String fileName = selectedSettings.file.getName();
                    if (currentRobotSettings != null && fileName != null
                            && fileName.length() >= currentRobotSettings.length()
                            && fileName.substring(0, currentRobotSettings.length()).equals(currentRobotSettings))
                        Toast.makeText(this, R.string.cannot_delete_current_settings_file, Toast.LENGTH_LONG).show();
                    else if (!selectedSettings.file.delete())
                        Toast.makeText(this, R.string.cannot_delete_settings_file, Toast.LENGTH_LONG).show();
                    else {
                        fillSettinsList();
                        fillCurSettings();
                        Toast.makeText(this, R.string.settings_were_deleted, Toast.LENGTH_LONG).show();
                    }
                }
                else
                    return super.onContextItemSelected(item);
        }
        return true;
    }

    @Override
    public void onClick(View v) {
        final int id = v.getId();
        switch(id){
            case R.id.buttonAdd: {
                v.showContextMenu();
                /*PopupMenu popup = new PopupMenu(getApplicationContext(), v);
                popup.getMenuInflater().inflate(R.menu.menu_robot_settings_list, popup.getMenu());
                popup.show();*/
            }
            case R.id.buttonDelete: {
                v.showContextMenu();
            }
        }
    }

    public void onCancelButtonClick(View v){
        finish();
    }

    public void onHideSubsButtonClick(View v){
        linearLayoutSubsWarning.setVisibility(View.GONE);
    }

    /*public void onAddButtonClick(View v){
        Intent intent = new Intent(thisContext, EV3SettingsActivity.class);
        intent.putExtra(EV3SettingsActivity.SETTINGS_FILE_NAME, "");
        startActivity(intent);
    }

    public void onDeleteButtonClick(View v){
        Intent intent = new Intent(thisContext, EV3SettingsActivity.class);
        intent.putExtra(EV3SettingsActivity.SETTINGS_FILE_NAME, "");
        startActivity(intent);
    }*/

    @Override
    protected void onResume (){
        super.onResume();
        if (!lastSettingsModified.equals(RoboCamBroker.getLastSettingsModified())) {
            lastSettingsModified = RoboCamBroker.getLastSettingsModified();
            fillSettinsList();
            fillCurSettings();
            RoboCamDriver.updateCurrentDriver(thisContext, isPremium);
        }
    }

}
