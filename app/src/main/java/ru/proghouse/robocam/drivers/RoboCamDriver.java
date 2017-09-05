package ru.proghouse.robocam.drivers;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.widget.Toast;

import org.w3c.dom.Document;

import java.io.File;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import ru.proghouse.robocam.DefaultValue;
import ru.proghouse.robocam.ExtraKey;
import ru.proghouse.robocam.HttpServer;
import ru.proghouse.robocam.MainActivity;
import ru.proghouse.robocam.R;
import ru.proghouse.robocam.Utils;
import ru.proghouse.robocam.drivers.Custom.CustomDriver;
import ru.proghouse.robocam.drivers.EV3.EV3Driver;

/**
 * Created by Alexey Valuev on 02.02.2016.
 */
public abstract class RoboCamDriver {
    public static final int ROBOT_IS_DISCONNECTED = 0;
    public static final int ROBOT_IS_CONNECTING = 1;
    public static final int ROBOT_IS_NOT_FOUND_VIA_BLUETOOTH = 2;
    public static final int ROBOT_IS_CONNECTED_TO = 3;
    public static final int ROBOT_IS_CONNECTING_TO = 4;

    public static final int JOYSTICK_BEHAVIOR_RETURN_TO_ZERO = 0;
    public static final int JOYSTICK_BEHAVIOR_HOLD_POSITION = 1;

    public static final String JOYSTICK_SHAPE_VERTICAL = "v";
    public static final String JOYSTICK_SHAPE_HORIZONTAL = "h";
    public static final String JOYSTICK_SHAPE_CIRCULAR = "c";
    public static final String JOYSTICK_SHAPE_QUADRATIC = "q";
    public static final String JOYSTICK_SHAPE_ARROWS = "a";
    public static final String JOYSTICK_SHAPE_INVISIBLE = "-";
    public static final String JOYSTICK_SHAPE_VERTICAL_ARROWS = "l";
    public static final String JOYSTICK_SHAPE_HORIZONTAL_ARROWS = "t";

    public static final int JOYSTICK_TYPE_INDEPENDENT_MOTORS = 0;
    public static final int JOYSTICK_TYPE_STEERING = 1;
    public static final int JOYSTICK_TYPE_MAILBOX = 2;
    public static final int JOYSTICK_TYPE_STEERING_PROGRESSIVE = 3;

    private static List<String> driverNames = new ArrayList<String>();
    private static List<Class<? extends RoboCamDriver>> driverClasses
            = new ArrayList<Class<? extends RoboCamDriver>>();
    private volatile static RoboCamDriver currentDriver = new EV3Driver();
    private DriverListener listener = null;

    static {
        RoboCamDriver.registerDriver(DefaultValue.EV3, EV3Driver.class);
        RoboCamDriver.registerDriver(DefaultValue.Custom, CustomDriver.class);
    };

    public abstract String getRobotName();

    /**
     * Describes possible behaviors of 8 joystick axes when the user ends touch the joystick: xywzabcd.
     * For example "00010000" means that z holds position when the user ends touch the joystick number 2,
     * others return to 0 when the user ends touch the joystick.
     * @return
     */
    public abstract String getJoystickBehaviors();

    /**
     * Describes shapes of 4 joysticks: 1234. l - vertical line, c - circular, q - quadratic, a - arrows.
     * For example "qc--" means that the joystick number 1
     * is quadratic, the joystick number 2 is circular, and joysticks 3, 4 are not used.
     * @return
     */
    public abstract String getJoystickShapes();

    /**
     * Which keys are used?
     * @return
     */
    public abstract String getUsedKeys();

    /**
     * Do I have to hide joysticks while using a keyboard?
     * @return
     */
    public abstract boolean isHideJoysticks();

    public abstract void setJoystickValues(Hashtable<String, Integer> joystickValues);

    public abstract void setPressedKeys(HashSet<Integer> pressedKeys);

    public static void updateCurrentDriver(Context context, boolean update) {
        SharedPreferences settings = context.getSharedPreferences(ExtraKey.APP_PREFERENCE, Context.MODE_PRIVATE);
        String currentDriverSettings = settings.getString(ExtraKey.CURRENT_ROBOT_SETTINGS, "");
        File robotDir = Utils.getRobotDir(context);
        try {
            if (currentDriverSettings.equals("")) {
                currentDriverSettings = DefaultValue.EV3_SETTINGS_FILE;
                EV3Driver.checkDefaultSettingsFile(context);
            }
            File settingsFile = new File(robotDir, currentDriverSettings);
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document xml = db.parse(settingsFile);
            xml.getDocumentElement().normalize();
            String newDriverName = xml.getDocumentElement().getNodeName();
            if (((!getCurrentDriver().getName().equals(newDriverName))
                    || (!getCurrentDriver().getSettingsFileName().equals(settingsFile.getName()))
                    || (getCurrentDriver().getLastModified() != settingsFile.lastModified())) && update) {
                RoboCamDriver newDriver = createDriver(context, newDriverName);
                newDriver.loadSettingsFromXml(context, settingsFile, xml);
                setCurrentDriver(newDriver);
            }
        } catch(Throwable e) {
            Toast.makeText(context, context.getString(R.string.error_while_opening_settings_file,
                    currentDriverSettings, e.getMessage()), Toast.LENGTH_LONG).show();
        }
    }

    public abstract void loadSettingsFromXml(Context context, File file, Document xml) throws Exception;

    public static RoboCamDriver createDriverAndLoadSettingsFromXml(Context context, File file) throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        DocumentBuilder db = dbf.newDocumentBuilder();
        Document xml = db.parse(file);
        xml.getDocumentElement().normalize();
        String driverName = xml.getDocumentElement().getNodeName();
        RoboCamDriver driver = createDriver(context, driverName);
        driver.loadSettingsFromXml(context, file, xml);
        return driver;
    }

    public abstract String getName();

    public abstract String getSettingsFileName();

    public abstract String getSettingsName();

    public abstract boolean isDisconnected();

    public abstract boolean isConnected();

    public abstract long getLastModified();

    public interface DriverListener {
        void onConnected();
        void onConnectionError(String error);
        void onConnectionError(int errorResId);
        void onConnectionError(int errorResId, Object... formatArgs);
        void onDisconnected();
        void onDisconnected(int errorResId);
        void onDisconnected(int errorResId, Object... formatArgs);
    }

    protected static void registerDriver(String driverName, Class<? extends RoboCamDriver> driverClass){
        if (driverNames.contains(driverName))
            return;
        driverNames.add(driverName);
        driverClasses.add(driverClass);
    }

    public static List<String> getDriverNames(){
        return driverNames;
    }

    public static RoboCamDriver createDriver(Context context, String driverName) throws Exception {
        int index = driverNames.indexOf(driverName);
        if (index < 0)
            throw new Exception(String.format(context.getString(R.string.unknown_driver_name), driverName));
        return (RoboCamDriver)driverClasses.get(index).getConstructor().newInstance();
    }

    public static boolean checkBluetooth(Activity activity) throws Exception {
        if (BluetoothAdapter.getDefaultAdapter() == null)
            throw new Exception(activity.getString(R.string.bluetooth_is_not_supported));
        if (!BluetoothAdapter.getDefaultAdapter().isEnabled()){
            Intent turnOn = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            activity.startActivityForResult(turnOn, 0);
        }
        return false;
    }

    public static RoboCamDriver getCurrentDriver() {
        return currentDriver;
    }

    public static void setCurrentDriver(RoboCamDriver driver) {
        if (currentDriver != driver) {
            if (currentDriver != null && (!currentDriver.isDisconnected()))
                currentDriver.disconnect();
            driver.listener = currentDriver.listener;
            currentDriver.listener = null;
            currentDriver = driver;
        }
    }

    public static void setCurrentDriver(Activity activity, String driverName) throws Exception {
        currentDriver = createDriver(activity, driverName);
    }

    public abstract boolean needBluetooth();

    public int getStringResId(int robotIsDisconnected){
        switch (robotIsDisconnected){
            case RoboCamDriver.ROBOT_IS_DISCONNECTED:
                return R.string.robot_is_disconnected;
            case RoboCamDriver.ROBOT_IS_CONNECTING:
                return R.string.robot_is_connecting;
            case RoboCamDriver.ROBOT_IS_NOT_FOUND_VIA_BLUETOOTH:
                return R.string.robot_bonded_devices_not_found;
            case RoboCamDriver.ROBOT_IS_CONNECTING_TO:
                return R.string.robot_is_connecting_to;
            case RoboCamDriver.ROBOT_IS_CONNECTED_TO:
                return R.string.robot_is_connected_to;
        }
        return 0;
    };

    public abstract void connect(BluetoothDevice device);

    public abstract void disconnect();

    /**
     * Stops all motors and rotates them back to start positions if necessary.
     */
    public abstract void stop();

    public void setDriverListener(DriverListener listener){
        synchronized (HttpServer.sync) {
            this.listener = listener;
        }
    }

    protected void doOnConnected(){
        synchronized (HttpServer.sync) {
            if (listener != null)
                listener.onConnected();
        }
    }

    protected void doOnConnectionError(String error){
        synchronized (HttpServer.sync) {
            if (listener != null)
                listener.onConnectionError(error);
        }
    }

    protected void doOnConnectionError(int errorResId){
        synchronized (HttpServer.sync) {
            if (listener != null)
                listener.onConnectionError(errorResId);
        }
    }

    protected void doOnConnectionError(int errorResId, Object... formatArgs){
        synchronized (HttpServer.sync) {
            if (listener != null)
                listener.onConnectionError(errorResId, formatArgs);
        }
    }

    protected void doOnDisconnected() {
        synchronized (HttpServer.sync) {
            if (listener != null)
                listener.onDisconnected();
        }
    }

    protected void doOnDisconnected(int errorResId) {
        synchronized (HttpServer.sync) {
            if (listener != null)
                listener.onDisconnected(errorResId);
        }
    }

    protected void doOnDisconnected(int errorResId, Object... formatArgs) {
        synchronized (HttpServer.sync) {
            if (listener != null)
                listener.onDisconnected(errorResId, formatArgs);
        }
    }

    private boolean showDebugInfo = true;

    public boolean isShowDebugInfo() {
        return showDebugInfo;
    }

    public void setShowDebugInfo(boolean showDebugInfo) {
        this.showDebugInfo = showDebugInfo;
    }
}
