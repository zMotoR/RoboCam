package ru.proghouse.robocam;

import android.content.Context;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;

import ru.proghouse.robocam.drivers.RoboCamDriver;

/**
 * Created by Alexey Valuev on 03.02.2016.
 */
public class RoboCamBroker {
    public static boolean DEBUG_HTTP = false;//true;
    private static List<RoboCamBrokerListener> listeners = new ArrayList<RoboCamBrokerListener>();

    public static void setJoystickValues(Hashtable<String, Integer> joystickValues) {
        RoboCamDriver.getCurrentDriver().setJoystickValues(joystickValues);
    }

    public static void setPressedKeys(HashSet<Integer> pressedKeys) {
        RoboCamDriver.getCurrentDriver().setPressedKeys(pressedKeys);
    }

    public interface RoboCamBrokerListener{
        public void onServerStateChange();
        public void onTestMessage(String msg);
    }

    public static void addRoboCamBrokerListener(RoboCamBrokerListener listener) {
        synchronized (HttpServer.sync) {
            if (listeners.indexOf(listener) < 0)
                listeners.add(listener);
        }
    }

    public static void removeRoboCamBrokerListener(RoboCamBrokerListener listener) {
        synchronized (HttpServer.sync) {
            listeners.remove(listener);
        }
    }

    public static void doServerStateChange(){
        synchronized (HttpServer.sync) {
            for (int i = 0; i < listeners.size(); i++)
                listeners.get(i).onServerStateChange();
        }
    }

    public static void doTestMessage(String msg){
        synchronized (HttpServer.sync) {
            for (int i = 0; i < listeners.size(); i++)
                listeners.get(i).onTestMessage(msg);
        }
    }

    private static volatile Date date = new Date();

    public static void setLastSettingsModified(Date date) {
        RoboCamBroker.date = date;
    }

    public static Date getLastSettingsModified() {
        return RoboCamBroker.date;
    }

}
