package ru.proghouse.robocam.drivers.Custom;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.util.Log;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import ru.proghouse.robocam.DefaultValue;
import ru.proghouse.robocam.HttpServer;
import ru.proghouse.robocam.R;
import ru.proghouse.robocam.StringHelper;
import ru.proghouse.robocam.Utils;
import ru.proghouse.robocam.drivers.RoboCamDriver;
import ru.proghouse.robocam.drivers.RoboCamJoystick;
import ru.proghouse.robocam.drivers.RoboCamKeyGroup;
import ru.proghouse.robocam.drivers.StreamHelper;

/**
 * Created by Alexey Valuev on 13.06.2017.
 */

public class CustomDriver extends RoboCamDriver {

    private static final int MAX_SUPPORTED_PROTOCOL_VERSION = 1;
    private volatile int currentProtocolVersion = 1; //Protocol version for the current connection.

    private static final int CMD_STOP = 255;
    private static final int CMD_START = 0;
    private static final int CMD_CALLSIGN = 1;
    private static final int CMD_CTRL = 2;

    private static final int KEY_PRESSED = 255;
    private static final int KEY_RELEASED = 254;

    static {RoboCamDriver.registerDriver(DefaultValue.Custom, CustomDriver.class);};

    private static final int SOCKET_DISCONNECTED = 0;
    private static final int SOCKET_CONNECTING = 1;
    private static final int SOCKET_ABORTED = 2;
    private static final int SOCKET_CONNECTED = 3;

    private volatile String settingsName = "";
    private volatile String settingsFileName = "";
    private volatile String callsign = "";
    private volatile String response = "";
    private volatile String charsetName = "US-ASCII";
    private List<RoboCamJoystick> joysticks = new ArrayList<RoboCamJoystick>();
    private volatile boolean keyGroupIsActive = false;
    private HashSet<Integer> keyCodes = new HashSet<Integer>();
    private volatile long lastModified = 0;
    private volatile boolean hideJoysticks = true;
    private volatile int socketState = SOCKET_DISCONNECTED;

    private volatile BluetoothSocket socket = null;
    private volatile BluetoothDevice device = null;

    private final Object socketSyncObject = new Object();
    private final Object joystickMonitor = new Object();
    private final Object joystickSyncObject = new Object();
    private boolean pressedKeysChanged = false;

    private Hashtable<String, Integer> joystickValues = new Hashtable<String, Integer>();
    private HashSet<Integer> pressedKeys = new HashSet<Integer>();
    private HashSet<Integer> currentPressedKeys = new HashSet<Integer>();

    static final String[] axisNames = new String[]{"x", "y", "w", "z", "a", "b", "c", "d"};
    private int[] joystickCoordinates = new int[4 * 2];

    public CustomDriver() {
        joysticks.add(new RoboCamJoystick());
        joysticks.add(new RoboCamJoystick());
        joysticks.add(new RoboCamJoystick());
        joysticks.add(new RoboCamJoystick());
        clearJoystickCoordinates();
    }

    private void clearJoystickCoordinates() {
        for (int i = 0; i < joystickCoordinates.length; i++)
            joystickCoordinates[i] = 0;
    }

    private void clearJoysticks() {
        for (int i = 0; i < joysticks.size(); i++)
            joysticks.get(i).setVisible(false);
    }

    public static void createTestSettings(Context context) throws Exception {
        File robotDir = Utils.getRobotDir(context);
        File testSettingsFile = new File(robotDir, "test_custom.xml");
        //if (!testSettingsFile.exists()) {
            FileOutputStream fileOutputStream = new FileOutputStream(testSettingsFile);
            try {
                fileOutputStream.write(new String(
                        "<Custom Name=\"Arduino\""
                                + " Description=\"Arduino researcher\""
                                + " Callsign=\"RoboCam\""
                                + " Response=\"Researcher\""
                                + ">"
                                + "<Joystick Index=\"0\" Visible=\"1\" Shape=\"c\" />"
                                + "<Joystick Index=\"1\" Visible=\"1\" Shape=\"v\" Behavior1=\"1\" />"
                                + "<KeyGroup Active=\"1\">"
                                + "<Key>87</Key>"
                                + "<Key>38</Key>"
                                + "<Key>65</Key>"
                                + "<Key>37</Key>"
                                + "<Key>83</Key>"
                                + "<Key>40</Key>"
                                + "<Key>68</Key>"
                                + "<Key>39</Key>"
                                + "<Key>89</Key>"
                                + "<Key>72</Key>"
                                + "</KeyGroup>"
                                + "</Custom>"
                ).getBytes("UTF-8"));
            } finally {
                fileOutputStream.close();
            }
        //}
    }

    @Override
    public String getRobotName() {
        if (device != null)
            return device.getName();
        return null;
    }

    @Override
    public String getJoystickBehaviors() {
        String result = "";
        for (RoboCamJoystick joystick : joysticks)
            result += joystick.getBehaviors();
        return result;
    }

    @Override
    public String getJoystickShapes() {
        String result = "";
        for (RoboCamJoystick joystick : joysticks)
            result += joystick.getShape();
        return result;
    }

    @Override
    public String getUsedKeys() {
        HashSet<Integer> keys = new HashSet<Integer>();
        if (keyGroupIsActive)
            synchronized (keyCodes) {
                keys.addAll(keyCodes);
            }
        String usedKeys = "";
        if (keys.size() > 0) {
            char[] zeroChar = new char[]{'0'};
            for (Integer key : keys) {
                String keyCode = key.toString();
                if (keyCode.length() < 3)
                    keyCode = new String(zeroChar, 0, 3 - keyCode.length()) + keyCode;
                usedKeys += keyCode;
            }
        }
        return usedKeys;
    }

    @Override
    public boolean isHideJoysticks() {
        return hideJoysticks;
    }

    @Override
    public void setJoystickValues(Hashtable<String, Integer> newJoystickValues) {
        synchronized (joystickSyncObject) {
            for (Enumeration<String> enumerator = newJoystickValues.keys(); enumerator.hasMoreElements(); ) {
                String key = enumerator.nextElement();
                joystickValues.put(key, newJoystickValues.get(key));
            }
        }
        synchronized (joystickMonitor) {
            joystickMonitor.notifyAll();
        }
    }

    @Override
    public void setPressedKeys(HashSet<Integer> pressedKeys) {
        synchronized (joystickSyncObject) {
            this.pressedKeys = pressedKeys;
            pressedKeysChanged = true;
        }
        synchronized (joystickMonitor) {
            joystickMonitor.notifyAll();
        }
    }

    @Override
    public void loadSettingsFromXml(Context context, File file, Document xml) throws Exception {
        if (!xml.getDocumentElement().getNodeName().equals(DefaultValue.Custom))
            throw new Exception(context.getString(R.string.unknown_driver_name,
                    xml.getDocumentElement().getNodeName()));
        settingsFileName = file.getName();
        lastModified = file.lastModified();
        settingsName = xml.getDocumentElement().getAttribute("Name");
        callsign = xml.getDocumentElement().getAttribute("Callsign");
        response = xml.getDocumentElement().getAttribute("Response");
        int charset = StringHelper.intFromString(xml.getDocumentElement().getAttribute("Charset"), 0);
        if (charset == 1)
            charsetName = "UTF-8";
        else
            charsetName = "US-ASCII";
        hideJoysticks = StringHelper.booleanFromString(xml.getDocumentElement().getAttribute("HideJoysticks"), true);
        setShowDebugInfo(StringHelper.booleanFromString(xml.getDocumentElement().getAttribute("ShowDebugInfo"), true));
        clearJoysticks();
        NodeList joystickNodes = xml.getElementsByTagName("Joystick");
        for (int i = 0; i < joystickNodes.getLength(); i++) {
            Element joystickNode = (Element)joystickNodes.item(i);
            int joystickIndex = StringHelper.intFromString(joystickNode.getAttribute("Index"), -1);
            if (joystickIndex >= 0 && joystickIndex < joysticks.size()) {
                RoboCamJoystick joystick = joysticks.get(joystickIndex);
                joystick.setVisible(StringHelper.booleanFromString(joystickNode.getAttribute("Visible"), false));
                joystick.setShape(StringHelper.stringFromString(joystickNode.getAttribute("Shape"),
                        RoboCamDriver.JOYSTICK_SHAPE_CIRCULAR));
                joystick.setBehavior(0, StringHelper.intFromString(joystickNode.getAttribute("Behavior0"),
                        RoboCamDriver.JOYSTICK_BEHAVIOR_RETURN_TO_ZERO));
                joystick.setBehavior(1, StringHelper.intFromString(joystickNode.getAttribute("Behavior1"),
                        RoboCamDriver.JOYSTICK_BEHAVIOR_RETURN_TO_ZERO));
            }
        }
        synchronized (keyCodes) {
            keyGroupIsActive = false;
            keyCodes.clear();
            NodeList keyGroupNodes = xml.getElementsByTagName("KeyGroup");
            for (int i = 0; i < keyGroupNodes.getLength(); i++) {
                Element keyGroupNode = (Element) keyGroupNodes.item(i);
                keyGroupIsActive = StringHelper.booleanFromString(keyGroupNode.getAttribute("Active"), false);
                RoboCamKeyGroup.loadKeysFromXml(keyGroupNode, keyCodes, "Key");
            }
        }
    }

    @Override
    public String getName() {
        return DefaultValue.Custom;
    }

    @Override
    public String getSettingsFileName() {
        return settingsFileName;
    }

    @Override
    public String getSettingsName() {
        return settingsName;
    }

    @Override
    public boolean isDisconnected() {
        return socketState == SOCKET_DISCONNECTED;
    }

    @Override
    public boolean isConnected() {
        return socketState == SOCKET_CONNECTED;
    }

    @Override
    public long getLastModified() {
        return lastModified;
    }

    @Override
    public boolean needBluetooth() {
        return true;
    }

    @Override
    public void connect(BluetoothDevice device) {
        if (socketState == SOCKET_DISCONNECTED || socketState == SOCKET_ABORTED) {
            this.device = device;
            socketState = SOCKET_CONNECTING;
            new Thread(new CustomRunnable(this)).start();
        }
    }

    @Override
    public void disconnect() {
        disconnect(0);
    }

    public void disconnect(int resId) {
        socketState = SOCKET_ABORTED;
        HttpServer.updateJoysticks();
        if (socket != null)
            close();
        if (resId == 0)
            doOnDisconnected();
        else
            doOnDisconnected(resId);
    }

    @Override
    public void stop() {
        if (socket != null)
            try {
                if (socketState == SOCKET_CONNECTED)
                    sendCommand(CMD_STOP);
            } catch (Throwable e) {
                e.printStackTrace();
            }
    }

    private void close() {
        if (socket != null) {
            try {
                if (socketState == SOCKET_CONNECTED)
                    sendCommand(CMD_STOP);
            } catch (Throwable e) {
                e.printStackTrace();
            }
            try {
                socket.close();
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }
        socket = null;
        socketState = SOCKET_DISCONNECTED;
    }

    private void sendCommand(int cmd) throws IOException {
        ByteArrayOutputStream s = new ByteArrayOutputStream();
        StreamHelper.writeUByte(s, cmd);
        sendMessageAndReadReply(s, 1000);
    }

    private void sendMessage(ByteArrayOutputStream s) throws IOException {
        OutputStream outputStream = socket.getOutputStream();
        byte[] bytes = new byte[2 + s.size()];
        StreamHelper.setUShortToByteArray(bytes, 0, s.size());
        System.arraycopy(s.toByteArray(), 0, bytes, 2, s.size());
        synchronized (socketSyncObject) {
            outputStream.write(bytes);
        }
    }

    private byte[] sendMessageAndReadReply(ByteArrayOutputStream s, int timeOut) throws IOException {
        OutputStream outputStream = socket.getOutputStream();
        byte[] outputBytes = new byte[2 + s.size()];
        StreamHelper.setUShortToByteArray(outputBytes, 0, s.size());
        System.arraycopy(s.toByteArray(), 0, outputBytes, 2, s.size());
        byte[] replyBytes = null;
        synchronized (socketSyncObject) {
            outputStream.write(outputBytes);
            InputStream inputStream = socket.getInputStream();
            int available = 0;
            int counter = 0;
            while (counter * 20 < timeOut) {
                available = inputStream.available();
                if (available > 0)
                    break;
                try {
                    Thread.sleep(20);
                    counter++;
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            if (available > 0) {
                counter = 0;
                int replySize = StreamHelper.readUShort(inputStream);
                if (replySize > 0) {
                    int readBytes = 0;
                    replyBytes = new byte[replySize];
                    while (true) {
                        readBytes = inputStream.read(replyBytes, readBytes, replySize);
                        if (readBytes == replySize)
                            break;
                        else if (readBytes <= 0) {
                            if (counter * 20 < timeOut) {
                                try {
                                    Thread.sleep(20);
                                    counter++;
                                    continue;
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                }
                            }
                            else {
                                replyBytes = null;
                                break;
                            }
                        }
                        else
                            replySize -= readBytes;
                    }
                }
            }
        }
        return replyBytes;
    }

    private class CustomRunnable implements Runnable {
        CustomDriver driver = null;
        private int errorId = 0;
        private Object arg1 = null;

        public CustomRunnable(CustomDriver driver) {
            this.driver = driver;
        }

        @Override
        public void run() {
            int counter = 0;
            try {
                while(true) {
                    driver.socket = driver.device.createRfcommSocketToServiceRecord(
                            UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"));
                    boolean connected = false;
                    if (socketState == SOCKET_ABORTED)
                        break;
                    try {
                        driver.socket.connect();
                        connected = true;
                    } catch (IOException e) {
                        driver.socket.close();
                        if (counter >= 7/* || !e.getMessage().equals("Connection refused")*/)
                            throw e;
                    }
                    if (connected) {
                        errorId = 0;
                        checkIfOurRobotConnected();
                        if (errorId == 0 && callsign != null && (!callsign.equals(""))
                                && response != null && (!response.equals("")))
                            checkCallsign();
                        if (counter >= 7
                                || errorId == R.string.robot_error_unknown_device
                                || errorId == R.string.robot_error_internal_error
                                || errorId == R.string.robot_error_wrong_reply
                                || errorId == R.string.robot_error_usupported_protocol
                                || errorId == R.string.robot_error_usupported_charset
                                || errorId == R.string.robot_error_wrong_response_on_callsign
                                || errorId == 0)
                            break;
                    }
                    Thread.sleep(3000);
                    counter++;
                }
                if (socketState != SOCKET_ABORTED) {
                    if (errorId == 0) {
                        synchronized (keyCodes) {
                            currentPressedKeys.clear();
                        }
                        clearJoystickCoordinates();
                        startReadingControllerValues();
                        driver.doOnConnected();
                        socketState = SOCKET_CONNECTED;
                        HttpServer.updateJoysticks();
                    } else {
                        socketState = SOCKET_DISCONNECTED;
                        if (arg1 == null)
                            driver.doOnConnectionError(errorId);
                        else
                            driver.doOnConnectionError(errorId, arg1);
                        driver.close();
                    }
                }
            } catch (Exception e) {
                    //java.io.IOException: read failed, socket might closed or timeout, read ret: -1
                    if (socketState != SOCKET_ABORTED) {
                        e.printStackTrace();
                        driver.doOnConnectionError(R.string.robot_connection_error);
                        //driver.doOnConnectionError(e.getLocalizedMessage());
                        driver.close();
                    }
                }
        }

        private void checkIfOurRobotConnected() throws IOException {
            ByteArrayOutputStream s = new ByteArrayOutputStream();
            StreamHelper.writeUByte(s, CMD_START);
            Random r = new Random();
            int testByte = r.nextInt(254);
            StreamHelper.writeUByte(s, testByte); //Test byte.
            StreamHelper.writeUByte(s, MAX_SUPPORTED_PROTOCOL_VERSION); //Max supported protocol version.
            byte[] replyBytes = sendMessageAndReadReply(s, 1000);
            if (replyBytes == null || replyBytes.length != 4) {
                errorId = R.string.robot_error_unknown_device;
                return;
            }
            int replyCode = StreamHelper.getUByteFromByteArray(replyBytes, 0);
            if (replyCode != 0 && replyCode != 1) {
                errorId = R.string.robot_error_wrong_reply;
                return;
            }
            if (replyCode == 1) {
                errorId = R.string.robot_error_internal_error;
                return;
            }
            int replyByte = StreamHelper.getUByteFromByteArray(replyBytes, 1);
            if (replyByte != testByte + 1) {
                errorId = R.string.robot_error_wrong_reply;
                return;
            }
            int version = StreamHelper.getUByteFromByteArray(replyBytes, 2);
            if (version < 1 || version > MAX_SUPPORTED_PROTOCOL_VERSION) {
                errorId = R.string.robot_error_usupported_protocol;
                return;
            }
            int charset = StreamHelper.getUByteFromByteArray(replyBytes, 3);
            if (charset != 0 && charset != 1) {
                errorId = R.string.robot_error_usupported_charset;
                return;
            }
            currentProtocolVersion = version;
            charsetName = charset == 1 ? "UTF-8" : "US-ASCII";
        }

        private void checkCallsign() throws IOException {
            if (callsign != null && (!callsign.equals(""))
                    && response != null && (!response.equals(""))) {
                ByteArrayOutputStream s = new ByteArrayOutputStream();
                StreamHelper.writeUByte(s, CMD_CALLSIGN);
                StreamHelper.writeString(s, driver.callsign == null ? "" : driver.callsign, charsetName);
                byte[] replyBytes = sendMessageAndReadReply(s, 1000);
                if (replyBytes == null || replyBytes.length < 2) {
                    errorId = R.string.robot_error_wrong_reply;
                    return;
                }
                int replyCode = StreamHelper.getUByteFromByteArray(replyBytes, 0);
                if (replyCode != 0 && replyCode != 1) {
                    errorId = R.string.robot_error_wrong_reply;
                    return;
                }
                if (replyCode == 1) {
                    errorId = R.string.robot_error_internal_error;
                    return;
                }
                String robotResponse = StreamHelper.getStringFromByteArray(replyBytes, 1, replyBytes.length - 1, charsetName);
                if (robotResponse == null)
                    robotResponse = "";
                if (!robotResponse.equals(response)) {
                    errorId = R.string.robot_error_wrong_response_on_callsign;
                    return;
                }
            }
        }
    }

    private void startReadingControllerValues() {
        new Thread(new CustomDriver.ControllerValuesReader()).start();
    }

    private class ControllerValuesReader implements Runnable {

        @Override
        public void run() {
            while (true) {
                try {
                    synchronized (joystickMonitor) {
                        if (socketState == SOCKET_ABORTED || socket == null)
                            break;
                        joystickMonitor.wait(100);
                        if (socketState == SOCKET_ABORTED || socket == null)
                            break;
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                setControllerValues();
            }
        }

        public void setControllerValues() {
            while (true) {
                if (socketState == SOCKET_ABORTED || socket == null)
                    break;
                Hashtable<String, Integer> newJoystickValues = null;
                HashSet<Integer> newPressedKeys = null;
                synchronized (joystickSyncObject) {
                    if (joystickValues.size() > 0) {
                        newJoystickValues = (Hashtable<String, Integer>) joystickValues.clone();
                        joystickValues.clear();
                    }
                    if (pressedKeysChanged) {
                        newPressedKeys = (HashSet<Integer>)pressedKeys.clone();
                        pressedKeysChanged = false;
                    }
                }
                setControllerValues(newJoystickValues, newPressedKeys);
            }
        }

        public void setControllerValues(Hashtable<String, Integer> newJoystickValues,
                                           HashSet<Integer> newPressedKeys) {
            if (socketState == SOCKET_ABORTED)
                return;
            if (socket != null) {
                try {
                    ByteArrayOutputStream s = null;
                    if (newJoystickValues != null)
                        for (int i = 0; i < 4; i++)
                            if (newJoystickValues.containsKey(axisNames[i * 2])
                                    || newJoystickValues.containsKey(axisNames[i * 2 + 1])) {
                                int newX = newJoystickValues.containsKey(axisNames[i * 2])
                                        ? newJoystickValues.get(axisNames[i * 2]) : joystickCoordinates[i * 2];
                                int newY = newJoystickValues.containsKey(axisNames[i * 2 + 1])
                                        ? newJoystickValues.get(axisNames[i * 2 + 1]) : joystickCoordinates[i * 2 + 1];
                                if (newX != joystickCoordinates[i * 2]) {
                                    if (s == null) {
                                        s = new ByteArrayOutputStream();
                                        StreamHelper.writeUByte(s, CMD_CTRL);
                                    }
                                    StreamHelper.writeUByte(s, i * 2);
                                    StreamHelper.writeByte(s, (byte) newX);
                                    joystickCoordinates[i * 2] = newX;
                                }
                                if (newY != joystickCoordinates[i * 2 + 1]) {
                                    if (s == null) {
                                        s = new ByteArrayOutputStream();
                                        StreamHelper.writeUByte(s, CMD_CTRL);
                                    }
                                    StreamHelper.writeUByte(s, i * 2 + 1);
                                    StreamHelper.writeByte(s, (byte) newY);
                                    joystickCoordinates[i * 2 + 1] = newY;
                                }
                            }
                    if (newPressedKeys != null) {
                        HashSet<Integer> keys0 = new HashSet<Integer>();
                        HashSet<Integer> keys1 = new HashSet<Integer>();
                        synchronized (keyCodes) {
                            for (Integer key : newPressedKeys) {
                                if (keyCodes.contains(key)) {
                                    if (!currentPressedKeys.contains(key))
                                        keys1.add(key);
                                }
                            }
                            for (Integer key : currentPressedKeys) {
                                if (!newPressedKeys.contains(key))
                                    keys0.add(key);
                            }
                            currentPressedKeys.clear();
                            currentPressedKeys.addAll(newPressedKeys);
                        }
                        if (keys0.size() > 0 || keys1.size() > 0) {
                            if (s == null) {
                                s = new ByteArrayOutputStream();
                                StreamHelper.writeUByte(s, CMD_CTRL);
                            }
                            for (Integer key : keys1) {
                                StreamHelper.writeUByte(s, KEY_PRESSED);
                                StreamHelper.writeUByte(s, key);
                            }
                            for (Integer key : keys0) {
                                StreamHelper.writeUByte(s, KEY_RELEASED);
                                StreamHelper.writeUByte(s, key);
                            }
                        }
                    }
                    if (s != null) {
                        byte[] replyBytes = sendMessageAndReadReply(s, 1000);
                        if (replyBytes != null) {
                            //Log.d("RoboCam", "Mesage is sent");
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }


}
