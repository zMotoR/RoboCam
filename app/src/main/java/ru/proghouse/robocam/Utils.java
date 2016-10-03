package ru.proghouse.robocam;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Point;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.WindowManager;
import android.widget.Toast;

import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashSet;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

/**
 * Created by Alexey Valuev on 21.08.2016.
 */
public class Utils {
    public static void showError(Activity activity, String message, boolean finishActivity) {
        if (Build.VERSION.SDK_INT >= 11)
            ErrorDialog.newInstance(message, finishActivity)
                    .show(activity.getFragmentManager(), DefaultValue.FRAGMENT_DIALOG);
        else {
            Toast.makeText(activity, message, Toast.LENGTH_SHORT).show();
            activity.finish();
        }
    }

    public static void showError(Activity activity, int messageId, boolean finishActivity) {
        if (Build.VERSION.SDK_INT >= 11)
            ErrorDialog.newInstance(activity.getString(messageId), finishActivity)
                    .show(activity.getFragmentManager(), DefaultValue.FRAGMENT_DIALOG);
        else {
            Toast.makeText(activity, activity.getString(messageId), Toast.LENGTH_SHORT).show();
            activity.finish();
        }
    }

    private static Point getScreenSize(Context context) {
        Point screenSize = new Point();
        WindowManager windowManager = (WindowManager)context.getSystemService(Context.WINDOW_SERVICE);
        Display display = windowManager.getDefaultDisplay();
        DisplayMetrics metrics = new DisplayMetrics();
        display.getMetrics(metrics);
        // since SDK_INT = 1;
        screenSize.x = metrics.widthPixels;
        screenSize.y = metrics.heightPixels;
        // includes window decorations (statusbar bar/menu bar)
        if (Build.VERSION.SDK_INT >= 14 && Build.VERSION.SDK_INT < 17)
            try {
                screenSize.x = (Integer) Display.class.getMethod("getRawWidth").invoke(display);
                screenSize.y = (Integer) Display.class.getMethod("getRawHeight").invoke(display);
            } catch (Exception e) {
                e.printStackTrace();
            }
        // includes window decorations (statusbar bar/menu bar)
        if (Build.VERSION.SDK_INT >= 17)
            try {
                Point realSize = new Point();
                Display.class.getMethod("getRealSize", Point.class).invoke(display, realSize);
                return realSize;
            } catch (Exception e) {
                e.printStackTrace();
            }
        return screenSize;
    }

    public static void copy(File source, File dest) throws IOException {
        FileInputStream is = new FileInputStream(source);
        try {
            FileOutputStream os = new FileOutputStream(dest);
            try {
                byte[] buffer = new byte[4096];
                int length;
                while ((length = is.read(buffer)) > 0) {
                    os.write(buffer, 0, length);
                }
            } finally {
                os.close();
            }
        } finally {
            is.close();
        }
    }

    public static void deleteDir(File dir) {
        if (dir.isDirectory())
            for (File subDir : dir.listFiles())
                deleteDir(subDir);
        dir.delete();
    }

    public static File getRobotDir(Context context) {
        File oldRobotDir = new File(context.getCacheDir(), DefaultValue.ROBOT_SETTINGS_DIRECTORY);
        File robotDir = new File(context.getFilesDir(), DefaultValue.ROBOT_SETTINGS_DIRECTORY);
        if (oldRobotDir.exists() && oldRobotDir.isDirectory() && !robotDir.exists()) {
            //Copying old settings from the old path.
            robotDir.mkdirs();
            File[] oldFiles = oldRobotDir.listFiles();
            for (File oldFile : oldFiles) {
                if (oldFile.getName().trim().endsWith(".xml")) {
                    File file = new File(robotDir, oldFile.getName());
                    try {
                        Utils.copy(oldFile, file);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
        else
            robotDir.mkdirs();
        return robotDir;
    }

    public static File getAdsDir(Context context) {
        File adsDir = new File(context.getFilesDir(), DefaultValue.ADS_DIRECTORY);
        adsDir.mkdirs();
        return adsDir;
    }

    public static String createNewSettingsName(Context context, String currentSettingsName,
                                               String driverName) throws ParserConfigurationException, IOException, SAXException {
        HashSet<String> names = new HashSet<String>();
        File cacheDir = context.getCacheDir();
        File robotDir = getRobotDir(context);
        File[] files = robotDir.listFiles();
        for (File file : files) {
            if (file.getName().trim().endsWith(".xml")) {
                DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
                DocumentBuilder db = dbf.newDocumentBuilder();
                Document xml = db.parse(file);
                xml.getDocumentElement().normalize();
                if (xml.getDocumentElement().getNodeName().equals(driverName))
                    names.add(driverName + ":" + xml.getDocumentElement().getAttribute("Name"));
            }
        }
        String name = currentSettingsName;
        if (name == null)
            name = "";
        int n = 0;
        int lastWordIndex = name.trim().lastIndexOf(" ");
        if (lastWordIndex > 0) {
            try {
                n = Integer.parseInt(name.trim().substring(lastWordIndex + 1));
                name = name.trim().substring(0, lastWordIndex).trim();
            } catch(NumberFormatException e) {
            }
        }
        if (names.contains(driverName + ":" + name + (n > 0 ? " " + Integer.toString(n) : ""))) {
            n = n == 0 ? 2 : n + 1;
            while (names.contains(driverName + ":" + name + " " + Integer.toString(n)))
                n++;
            name += " " + Integer.toString(n);
        }
        else
            name = name + (n > 0 ? " " + Integer.toString(n) : "");
        return name;
    }

    public static void saveXml(String fileName, Document xml) throws FileNotFoundException, TransformerException {
        saveXml(new File(fileName), xml);
    }

    public static void saveXml(File file, Document xml) throws FileNotFoundException, TransformerException {
        DOMSource source = new DOMSource(xml);
        FileOutputStream stream = new FileOutputStream(file);
        StreamResult result = new StreamResult(stream);
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();
        transformer.transform(source, result);
    }

    public static boolean requestExternalStoragePermission(Activity activity, int requestCode) {
        boolean go = false;
        if (Build.VERSION.SDK_INT >= 23) {
            if (ActivityCompat.checkSelfPermission(activity,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                if (ActivityCompat.shouldShowRequestPermissionRationale(activity,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                    Bundle bundle = new Bundle();
                    bundle.putInt(ConfirmationDialog.MESSAGE_ID,
                            R.string.request_write_external_storage_permission);
                    bundle.putStringArray(ConfirmationDialog.PERMISSIONS,
                            new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE});
                    bundle.putInt(ConfirmationDialog.REQUEST_CODE, requestCode);
                    bundle.putBoolean(ConfirmationDialog.FINISH_ACTIVITY, false);
                    ConfirmationDialog dialog = new ConfirmationDialog();
                    dialog.setArguments(bundle);
                    dialog.show(activity.getFragmentManager(), DefaultValue.FRAGMENT_DIALOG);
                } else {
                    ActivityCompat.requestPermissions(activity,
                            new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                            requestCode);
                }
            }
            else
                go = true;
        }
        else
            go = true;
        return go;
    }
}
