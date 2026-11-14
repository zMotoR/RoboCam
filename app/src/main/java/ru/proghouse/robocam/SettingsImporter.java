package ru.proghouse.robocam;

import android.app.Activity;
import android.net.Uri;
import android.widget.Toast;

import org.w3c.dom.Document;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import ru.proghouse.robocam.drivers.RoboCamDriver;

/**
 * Imports robot settings either from a file the app can read itself
 * or from a content uri returned by the system file picker.
 */
public class SettingsImporter {

    public static boolean importSettings(Activity activity, File file, boolean finishOnError) {
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document xml = db.parse(file);
            xml.getDocumentElement().normalize();
            //Testing of the file.
            String driverName = xml.getDocumentElement().getNodeName();
            RoboCamDriver driver = RoboCamDriver.createDriver(activity, driverName);
            driver.loadSettingsFromXml(activity, file, xml);
            //Searching for names of existing settings.
            String name = Utils.createNewSettingsName(activity, driver.getSettingsName(), driver.getName());
            //Changing the settings name.
            xml.getDocumentElement().setAttribute("Name", name);
            //Getting the file name.
            SimpleDateFormat format = new SimpleDateFormat("yyyyMMddHHmmsszzz", Locale.ENGLISH);
            String newFileName = driverName + "_" + format.format(new Date()) + ".xml";
            //Saving the file.
            Utils.saveXml(new File(Utils.getRobotDir(activity), newFileName), xml);
            RoboCamBroker.setLastSettingsModified(new Date());
            Toast.makeText(activity, activity.getString(R.string.settings_ware_imported_successfully),
                    Toast.LENGTH_LONG).show();
            return true;
        } catch (Throwable e) {
            Utils.showError(activity, activity.getString(R.string.error_while_opening_settings_file,
                    file.getName(), e.getMessage()), finishOnError);
            return false;
        }
    }

    public static boolean importSettings(Activity activity, Uri uri, boolean finishOnError) {
        String fileName = null;
        File tempFile = null;
        boolean imported = false;
        try {
            fileName = getFileName(activity, uri);
            File cacheDir = new File(activity.getCacheDir(), DefaultValue.IMPORT_DIRECTORY);
            cacheDir.mkdirs();
            tempFile = new File(cacheDir, fileName);
            InputStream is = activity.getContentResolver().openInputStream(uri);
            if (is == null)
                throw new IOException(uri.toString());
            try {
                OutputStream os = new FileOutputStream(tempFile);
                try {
                    byte[] buffer = new byte[4096];
                    int length;
                    while ((length = is.read(buffer)) > 0)
                        os.write(buffer, 0, length);
                } finally {
                    os.close();
                }
            } finally {
                is.close();
            }
            imported = importSettings(activity, tempFile, finishOnError);
        } catch (Throwable e) {
            Utils.showError(activity, activity.getString(R.string.error_while_opening_settings_file,
                    fileName != null ? fileName : uri.toString(), e.getMessage()), finishOnError);
        } finally {
            if (tempFile != null)
                tempFile.delete();
        }
        return imported;
    }

    private static String getFileName(Activity activity, Uri uri) {
        String name = new File(Utils.getDisplayName(activity, uri)).getName().trim();
        if (name.equals(""))
            name = DefaultValue.IMPORTED_FILE_NAME;
        return name;
    }
}
