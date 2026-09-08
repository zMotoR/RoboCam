package ru.proghouse.robocam;

/*Ad formats for tablet PCs
        728 x 90
        300 x 250
        468 x 60*/
//TODO: Image to show QR-Code.
//TODO: Smiles.
//TODO: Button to focus and to take a picture.
//TODO: Export and import settings.
//TODO: Throw away overflow image from resource.

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.SurfaceTexture;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.LocaleList;
import android.os.PowerManager;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Display;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.webkit.WebView;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.MobileAds;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.NodeList;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import ru.proghouse.robocam.drivers.Custom.CustomDriver;
import ru.proghouse.robocam.drivers.EV3.EV3Driver;
import ru.proghouse.robocam.drivers.RoboCamDriver;

public class MainActivity extends AppCompatActivity implements SurfaceHolder.Callback,
        RoboCamBroker.RoboCamBrokerListener, RoboCamDriver.DriverListener {
    private static final int REQUEST_ENABLE_BT = 1;

    private static final int ROBOT_STATE_DISCONNECTED = 0;
    private static final int ROBOT_STATE_REQUEST_ENABLE_BLUETOOTH = 1;
    private static final int ROBOT_STATE_SELECTING = 2;
    private static final int ROBOT_STATE_DEVICES_NOT_FOUND = 3;
    private static final int ROBOT_STATE_CONNECTING = 4;
    private static final int ROBOT_STATE_CONNECTED = 5;
    private static final int ROBOT_STATE_CONNECTION_ERROR = 6;

    public static final int PURCHASE_STATE_UNKNOWN = 0;
    public static final int PURCHASE_STATE_PREMIUM = 1;
    public static final int PURCHASE_STATE_ERROR = 2;

    private static int AP_STATE_DISABLING = 10;
    private static int AP_STATE_DISABLED = 11;
    private static int AP_STATE_ENABLING = 12;
    private static int AP_STATE_ENABLED = 13;
    private static int AP_STATE_FAILED = 14;

    public static final int REQUEST_CAMERA_PERMISSION = 1;

    private CameraManager cameraManager = CameraManager.getCameraManager();
    private SurfaceView surfaceView = null;
    private View textureView = null;
    private TextureView.SurfaceTextureListener surfaceTextureListener = null;
    private RelativeLayout parentLayout = null;
    private MainActivity thisActivity = null;
    private volatile static MainActivity mainActivity = null;
    private static boolean isScreenOn = true;
    private TextView serverMessage = null;
    private ImageView serverMessageConnector = null;
    private TextView robotMessage = null;
    private ImageView robotMessageConnector = null;
    private Animation animationStartServer = null;
    private Animation animationConnectRobot = null;
    private ImageButton btnServer = null;
    private ImageButton btnRobot = null;
    private ImageButton btnLocalControls = null;
    private BroadcastReceiver bluetoothReceiver = null;
    private BroadcastReceiver wifiReceiver = null;
    private ListView robotListView = null;
    private static volatile int robotState = ROBOT_STATE_DISCONNECTED;
    private static volatile String robotStateError = null;
    private volatile RobotThreadRunnable robotThreadRunnable = null;
    private volatile ControlsUpdater controlsUpdater = null;
    private TextView testMessage = null;
    private volatile String testMessageText = null;
    private ImageView imageViewBackground = null;
    //private IabHelper mHelper;
    //private IabHelper.QueryInventoryFinishedListener mGotInventoryListener;
    //private int purchaseState = PURCHASE_STATE_UNKNOWN;
    //public static final String SKU_PREMIUM = "premium";
    private static volatile boolean loadingAds = false;
    private static final String AD_DOWNLOAD_ROOT_PATH = "http://www.proghouse.ru/robocam/";

    private WebView banner = null;
    private String bannerUrl = null;
    private String bannerType = "";
    private boolean bannerShowOffline = false;
    private AdView adView = null;
    private volatile boolean loadedAds = false;
    private Thread backgroundThread = null;
    private volatile boolean backgroundThreadTerminated = false;
    private volatile int lastCheckedOrientation = Surface.ROTATION_0;

    private volatile int surfaceTextureWidth = 0;
    private volatile int surfaceTextureHeight = 0;

    private boolean useLocalControls = false;

    public static double screenMin = 0;

    private SharedPreferences.Editor editor = null;

    private String lastBluetoothDevice = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            setContentView(R.layout.activity_main);
            getSupportActionBar().hide();
            mainActivity = this;
            thisActivity = this;

            /*double[] coord = new double[] {-200, 100};
            double a = (Math.atan2(coord[0], coord[1]) - Math.PI / 2.0) * -1;// * (180 / Math.PI);// + 180;
            double a_gr = a * (180 / Math.PI);
            coord[0] = Math.cos(a) * 100;
            coord[1] = Math.sin(a) * 100;*/

            /*int test = 64250;
            byte[] bytes = new byte[2];
            StreamHelper.setUShortToByteArray(bytes, 0, test);
            int test2 = StreamHelper.getUShortFromByteArray(bytes, 0);*/

            SharedPreferences settings = getSharedPreferences(ExtraKey.APP_PREFERENCE, Context.MODE_PRIVATE);
            if (savedInstanceState != null)
                lastBluetoothDevice = savedInstanceState.getString(ExtraKey.LAST_BLUETOOTH_DEVICE);
            else
                lastBluetoothDevice = settings.getString(ExtraKey.LAST_BLUETOOTH_DEVICE, null);
            useLocalControls = settings.getBoolean(ExtraKey.USE_LOCAL_CONTROLS, DefaultValue.USE_LOCAL_CONTROLS);

            Display display = getWindowManager().getDefaultDisplay();
            DisplayMetrics displayMetrics = new DisplayMetrics();
            display.getMetrics(displayMetrics);
            // since SDK_INT = 1;
            int width = displayMetrics.widthPixels;
            int height = displayMetrics.heightPixels;
            // includes window decorations (statusbar bar/menu bar)
            if (Build.VERSION.SDK_INT >= 14 && Build.VERSION.SDK_INT < 17)
                try {
                    width = (Integer) Display.class.getMethod("getRawWidth").invoke(display);
                    height = (Integer) Display.class.getMethod("getRawHeight").invoke(display);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            // includes window decorations (statusbar bar/menu bar)
            if (Build.VERSION.SDK_INT >= 17)
                try {
                    Point realSize = new Point();
                    Display.class.getMethod("getRealSize", Point.class).invoke(display, realSize);
                    width = realSize.x;
                    height = realSize.y;
                } catch (Exception e) {
                    e.printStackTrace();
                }
            screenMin = Math.min(
                    (double)width / (double)displayMetrics.density,
                    (double)height / (double)displayMetrics.density);

            banner = (WebView)findViewById(R.id.banner);
            banner.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View view, MotionEvent motionEvent) {
                    switch (motionEvent.getAction()) {
                        case MotionEvent.ACTION_UP:
                            if (bannerUrl != null && !bannerUrl.isEmpty()) {
                                Uri address = Uri.parse(bannerUrl);
                                Intent intent = new Intent(Intent.ACTION_VIEW, address);
                                startActivity(intent);
                            }
                            break;
                    }
                    return false;
                }
            });

            MobileAds.initialize(getApplicationContext(), "ca-app-pub-7800876624909705~9648407673"); //"ca-app-pub-7800876624909705/2125140872"
            adView = (AdView) findViewById(R.id.adView);
            //AdRequest adRequest = new AdRequest.Builder().build();
            /*mAdView.setAdListener(new AdListener(){
                @Override
                public void onAdFailedToLoad(int var1) {
                    Toast.makeText(thisActivity, "Ad error", Toast.LENGTH_LONG).show();
                    banner_320x50.loadUrl("file:///android_asset/banner_320x50.gif");
                    banner_320x50.setVisibility(View.VISIBLE);
                }
                @Override
                public void onAdLoaded() {
                    Toast.makeText(thisActivity, "Ad loaded", Toast.LENGTH_LONG).show();
                    banner_320x50.setVisibility(View.GONE);
                }
                @Override
                public void onAdOpened() {
                    Toast.makeText(thisActivity, "Ad opened", Toast.LENGTH_LONG).show();
                    banner_320x50.setVisibility(View.GONE);
                }
            });*/
            //adView.loadAd(adRequest);

            parentLayout = (RelativeLayout) findViewById(R.id.parentLayout);

            surfaceView = (SurfaceView) findViewById(R.id.surfaceView);
            if (Build.VERSION.SDK_INT >= CameraManager.CAMERA2_SDK) {
                textureView = new TextureView(this);
                RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT);
                params.addRule(RelativeLayout.CENTER_HORIZONTAL);
                params.addRule(RelativeLayout.CENTER_VERTICAL);
                parentLayout.addView(textureView, 0, params);
                surfaceView.setVisibility(View.GONE);
                createSurfaceTextureListener();
                textureView.setVisibility(useLocalControls ? View.GONE : View.VISIBLE);
            } else {
                surfaceView.getHolder().addCallback(this);
                surfaceView.getHolder().setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
                setSurfaceSize();
                surfaceView.setVisibility(useLocalControls ? View.GONE : View.VISIBLE);
                //if (isScreenOn && cameraManager.getPreviewSize() != null)
                //    updateCamera();
            }
            lastCheckedOrientation = getWindowManager().getDefaultDisplay().getRotation();
            backgroundThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    while (!thisActivity.backgroundThreadTerminated) {
                        try {
                            Thread.sleep(1000);
                            if (lastCheckedOrientation != getWindowManager().getDefaultDisplay().getRotation()) {
                                postUpdateCamera();
                                lastCheckedOrientation = getWindowManager().getDefaultDisplay().getRotation();
                            }
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }
            });
            serverMessage = (TextView) findViewById(R.id.serverMessage);
            serverMessageConnector = (ImageView) findViewById(R.id.serverMessageConnector);
            robotMessage = (TextView) findViewById(R.id.robotMessage);
            robotMessageConnector = (ImageView) findViewById(R.id.robotMessageConnector);
            testMessage = (TextView) findViewById(R.id.testMessage);
            animationStartServer = new AlphaAnimation(1, 0);
            animationStartServer.setDuration(200);
            animationStartServer.setInterpolator(new LinearInterpolator());
            animationStartServer.setRepeatCount(Animation.INFINITE);
            animationStartServer.setRepeatMode(Animation.REVERSE);
            animationConnectRobot = new AlphaAnimation(1, 0);
            animationConnectRobot.setDuration(200);
            animationConnectRobot.setInterpolator(new LinearInterpolator());
            animationConnectRobot.setRepeatCount(Animation.INFINITE);
            animationConnectRobot.setRepeatMode(Animation.REVERSE);
            btnServer = (ImageButton) findViewById(R.id.btnServer);
            btnRobot = (ImageButton) findViewById(R.id.btnRobot);
            btnLocalControls = (ImageButton) findViewById(R.id.btnLocalControls);
            imageViewBackground = (ImageView) findViewById(R.id.imageViewBackground);
            imageViewBackground.setVisibility(useLocalControls ? View.VISIBLE : View.GONE);
            robotListView = (ListView) findViewById(R.id.robotListView);
            if (useLocalControls) {
                btnServer.setVisibility(View.GONE);
                btnLocalControls.setVisibility(View.VISIBLE);
            }
            createBroadcastReceiver();
            hideServerMessage();
            hideRobotMessage();
            setRobotClickListener();
            controlsUpdater = new ControlsUpdater(this);
            try {
                EV3Driver.checkDefaultSettingsFile(this);
            } catch (Exception e) {
                Toast.makeText(this, R.string.error_while_creating_default_ev3_settings_file, Toast.LENGTH_LONG).show();
                e.printStackTrace();
            }
            //CustomDriver.createTestSettings(this);

            /*String base64EncodedPublicKey = "";
            mHelper = new IabHelper(this, base64EncodedPublicKey);
            mGotInventoryListener = new IabHelper.QueryInventoryFinishedListener() {
                public void onQueryInventoryFinished(IabResult result,
                                                     Inventory inventory) {
                    if (result.isFailure()) {
                        // handle error here
                        purchaseState = PURCHASE_STATE_ERROR;
                    }
                    else {
                        // does the user have the premium upgrade?
                        if (inventory.hasPurchase(SKU_PREMIUM)) {
                            // update UI accordingly
                            purchaseState = PURCHASE_STATE_PREMIUM;
                            RoboCamDriver.updateCurrentDriver(thisActivity, purchaseState == PURCHASE_STATE_PREMIUM);
                        }
                    }
                }
            };
            mHelper.startSetup(new IabHelper.OnIabSetupFinishedListener() {
                public void onIabSetupFinished(IabResult result) {
                    if (!result.isSuccess()) {
                        // Oh noes, there was a problem.
                        //Toast.makeText(thisActivity, "Problem setting up In-app Billing: " + result, Toast.LENGTH_LONG).show();
                    }
                    else {
                        // Hooray, IAB is fully set up!
                        try {
                            mHelper.queryInventoryAsync(mGotInventoryListener);
                        } catch (IabHelper.IabAsyncInProgressException e) {
                            e.printStackTrace();
                        }
                    }
                }
            });*/
            //loadAds();
            banner.setVisibility(View.GONE);
            adView.setVisibility(View.GONE);
            DownloadAd();
            backgroundThread.start();
        }catch(Throwable e){
            e.printStackTrace();
        }
    }

    @Override
    protected void onSaveInstanceState (Bundle outState) {
        outState.putString(ExtraKey.LAST_BLUETOOTH_DEVICE, lastBluetoothDevice);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onRestoreInstanceState (Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        lastBluetoothDevice = savedInstanceState.getString(ExtraKey.LAST_BLUETOOTH_DEVICE);
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

    private void createSurfaceTextureListener() {
        if (Build.VERSION.SDK_INT >= CameraManager.CAMERA2_SDK) {
            surfaceTextureListener = new TextureView.SurfaceTextureListener() {

                @Override
                public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int width, int height) {
                    if (cameraManager.isInitialized())
                        cameraManager.changeSurfaceTexture(thisActivity, surfaceTexture);
                    else
                        cameraManager.initCamera(thisActivity, surfaceTexture);
                    //cameraManager.configureTransform(thisActivity, textureView, width, height);
                    surfaceTextureWidth = width;
                    surfaceTextureHeight = height;
                    parentLayout.post(new Runnable() {
                        @Override
                        public void run() {
                            cameraManager.configureTransform(thisActivity, textureView,
                                    surfaceTextureWidth, surfaceTextureHeight);
                            updateCamera();
                        }
                    });
                }

                @Override
                public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int width, int height) {
                    cameraManager.configureTransform(thisActivity, textureView, width, height);
                    updateCamera();
                }

                @Override
                public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
                    cameraManager.releaseCamera(surfaceTexture);
                    return true;
                }

                @Override
                public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {

                }
            };
            ((TextureView)textureView).setSurfaceTextureListener(surfaceTextureListener);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length != 1 || grantResults[0] != PackageManager.PERMISSION_GRANTED)
                Utils.showError(this, R.string.request_camera_permission, false);
            else
            if (!cameraManager.isInitialized()) {
                if (cameraManager.getAfterGrandPermission())
                    Utils.showError(this, R.string.cannot_init_camera, false);
                else {
                    cameraManager.initCamera(this, true);
                    if (Build.VERSION.SDK_INT >= CameraManager.CAMERA2_SDK) {
                        cameraManager.configureTransform(thisActivity, textureView,
                                textureView.getWidth(), textureView.getHeight());
                    }
                }
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    private void DownloadFile(String src, File dst) throws IOException {
        URL url = new URL(src);
        InputStream inputStream = url.openStream();
        DataInputStream dataInputStream = new DataInputStream(inputStream);
        byte[] buffer = new byte[1024];
        int length;
        FileOutputStream fileOutputStream = new FileOutputStream(dst);
        while ((length = dataInputStream.read(buffer)) > 0)
            fileOutputStream.write(buffer, 0, length);
    }

    private void DownloadAd() {
        try {
            ////File cacheDir = getCacheDir();
            ////File adsDir = new File(cacheDir, DefaultValue.ADS_DIRECTORY);
            //File adsDir = Utils.getAdsDir(thisActivity);
            //File adsLocalizedDir = new File(adsDir, getString(R.string.local_web_path));
            /*Display display = getWindowManager().getDefaultDisplay();
            DisplayMetrics displayMetrics = new DisplayMetrics();
            getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
            double screenWidth = (double)displayMetrics.widthPixels / (double)displayMetrics.densityDpi;
            double screenHeight = (double)displayMetrics.heightPixels / (double)displayMetrics.densityDpi;
            double screenInches = Math.sqrt(Math.pow(screenWidth, 2) + Math.pow(screenHeight, 2));
            Point size = new Point();
            if (screenInches < 7) {//Less then 7 inches
                size.x = 320;
                size.y = 50;
            }
            else {
                size.x = 728;
                size.y = 90;
            }*/
            //adsLocalizedDir.mkdirs();

            //File newVersion = new File(adsLocalizedDir, "nv.xml");
            //DownloadFile("http://www.proghouse.ru/images/t/robocam/v.xml", newVersion);
            //DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            //DocumentBuilder db = dbf.newDocumentBuilder();
            //db.parse


            /*URL url = new URL("http://www.proghouse.ru/images/t/robocam/"
                    + getString(R.string.local_web_path) + "/v.txt");
            InputStream inputStream = url.openStream();
            DataInputStream dataInputStream = new DataInputStream(inputStream);
            byte[] buffer = new byte[1024];
            int length;
            FileOutputStream fileOutputStream = new FileOutputStream(
                    new File(Environment.getExternalStorageDirectory() + "/" + "data/test.kml"));
            while ((length = dis.read(buffer))>0) {
                fos.write(buffer, 0, length);
            }*/

            new Thread(new AdsLoader(getLocales(this))).start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static List<Locale> getLocales(Context context) {
        List<Locale> locales = new ArrayList<Locale>();
        if (Build.VERSION.SDK_INT >= 24) {
            LocaleList localeList = context.getResources().getConfiguration().getLocales();
            for (int i = 0; i < localeList.size(); i++)
                locales.add(localeList.get(i));
        }
        else
            locales.add(context.getResources().getConfiguration().locale);
        return locales;
    }

    class AdsLoader implements  Runnable {
        List<Locale> locales;

        AdsLoader(List<Locale> locales) {
            this.locales = locales;
        }

        @Override
        public void run() {
            if (!loadingAds) {
                try {
                    loadingAds = true;
                    //File cacheDir = getCacheDir();
                    //File adsDir = new File(cacheDir, DefaultValue.ADS_DIRECTORY);
                    //adsDir.mkdirs();
                    File adsDir = Utils.getAdsDir(thisActivity);
                    File newVersionFile = new File(adsDir, "nv.xml");
                    DownloadFile(AD_DOWNLOAD_ROOT_PATH + "v.xml", newVersionFile);
                    //DownloadFile(AD_DOWNLOAD_ROOT_PATH + "v_temp3.xml", newVersionFile);
                    DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
                    DocumentBuilder db = dbf.newDocumentBuilder();
                    Document newVersion = db.parse(newVersionFile);
                    //v.xml comes over plain HTTP and its attribute values are joined
                    //into local file paths, so a parent reference must abort the update.
                    if (containsParentSegment(newVersion.getDocumentElement()))
                        throw new Exception("unsafe ads version file");
                    File versionFile = new File(adsDir, "v.xml");
                    boolean haveToDownload = false;
                    Document version = null;
                    Element[] condition = {null};
                    String[] country = {null};
                    String[] language = {null};
                    File newVersionDir = null;
                    if (!versionFile.exists())
                        haveToDownload = true;
                    else {
                        version = db.parse(versionFile);
                        haveToDownload = !newVersion.getDocumentElement().getAttribute("number").equals(
                                version.getDocumentElement().getAttribute("number"));
                    }
                    if (!haveToDownload) {
                        if (findCondition(version, locales, condition, country, language)) {
                            File localizedVersionFile = new File(adsDir, "v" + country[0] + "-" + language[0] + ".xml");
                            haveToDownload = !localizedVersionFile.exists();
                        }
                    }
                    if (haveToDownload) {
                        newVersionDir = new File(adsDir,
                                newVersion.getDocumentElement().getAttribute("number"));
                        newVersionDir.mkdirs();
                        if (findCondition(newVersion, locales, condition, country, language)) {
                            //deleteDir(newVersionDir);
                            String conditionPath = condition[0].getAttribute("path");
                            NodeList nodeItems = condition[0].getChildNodes();
                            for (int i = 0; i < nodeItems.getLength(); i++) {
                                if (nodeItems.item(i).getNodeName().equals("file")) {
                                    String path = ((Element) nodeItems.item(i)).getAttribute("path");
                                    if (path != null && !path.isEmpty()) {
                                        File dst = new File(newVersionDir, path);
                                        File parent = dst.getParentFile();
                                        parent.mkdirs();
                                        DownloadFile(AD_DOWNLOAD_ROOT_PATH
                                                + (conditionPath != null ? conditionPath : "")
                                                + path, dst);
                                    }
                                }
                            }
                        }
                        //Deletes old files only when server is off!
                        if (HttpServer.getServerState() == HttpServer.SERVER_IS_OFF) {
                            for (File file : adsDir.listFiles())
                                if (file.isFile() && !file.getName().equals(newVersionFile.getName()))
                                    file.delete();
                                else if (file.isDirectory() && !file.getName().equals(newVersionDir.getName()))
                                    Utils.deleteDir(file);
                        }
                        File localizedVersionFile = new File(adsDir, "v" + country[0] + "-" + language[0] + ".xml");
                        localizedVersionFile.delete();
                        versionFile.delete();
                        Utils.copy(newVersionFile, localizedVersionFile);
                        newVersionFile.renameTo(versionFile);
                    }
                } catch (Throwable e) {
                    String s = e.getMessage();
                    e.printStackTrace();
                }
                loadingAds = false;
            }
            try {
                if (mainActivity != null) {
                    for (int i = 0; i < 7; i++) {
                        if (mainActivity.loadedAds)
                            break;
                        mainActivity.parentLayout.post(new Runnable() {
                            @Override
                            public void run() {
                                mainActivity.loadAds();
                            }
                        });
                        Thread.sleep(1000);
                    }
                }
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private static boolean containsParentSegment(Element element) {
        NamedNodeMap attributes = element.getAttributes();
        for (int i = 0; attributes != null && i < attributes.getLength(); i++) {
            String value = attributes.item(i).getNodeValue();
            if (value != null && value.contains(".."))
                return true;
        }
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element
                    && containsParentSegment((Element) children.item(i)))
                return true;
        }
        return false;
    }

    public static boolean findCondition(Document version, List<Locale> locales,
                                 Element[] conditionOut, String[] countryOut,
                                 String[] languageOut) {
        conditionOut[0] = null;
        languageOut[0] = "";
        countryOut[0] = "";
        NodeList conditions = version.getDocumentElement().getChildNodes();
        for (int i = 0; i < conditions.getLength(); i++) {
            if (conditions.item(i).getNodeName().equals("condition")) {
                String languageValue = ((Element)conditions.item(i)).getAttribute("language");
                String countryValue = ((Element)conditions.item(i)).getAttribute("country");
                if (languageValue == null)
                    languageValue = "";
                if (countryValue == null)
                    countryValue = "";
                String[] languages = languageValue.split(",");
                String[] countries = countryValue.split(",");
                for (Locale locale : locales) {
                    if (locale.getCountry() != null && !locale.getCountry().isEmpty())
                        for (String country : countries) {
                            if (country.equals(locale.getCountry())) {
                                conditionOut[0] = (Element)conditions.item(i);
                                countryOut[0] = country;
                                break;
                            }
                        }
                    if (conditionOut[0] != null)
                        break;
                    if (locale.getLanguage() != null && !locale.getLanguage().isEmpty())
                        for (String language : languages) {
                            if (language.equals(locale.getLanguage())) {
                                conditionOut[0] = (Element)conditions.item(i);
                                languageOut[0] = language;
                                break;
                            }
                        }
                    if (conditionOut[0] != null)
                        break;
                }
            }
            if (conditionOut[0] != null)
                break;
        }
        return conditionOut[0] != null;
    }

    private void loadAds() {
        if (loadedAds)
            return;
        loadedAds = true;
        boolean showAdView = true;
        try {
            //File cacheDir = getCacheDir();
            //File adsDir = new File(cacheDir, DefaultValue.ADS_DIRECTORY);
            File adsDir = Utils.getAdsDir(this);
            File versionFile = new File(adsDir, "v.xml");
            if (!versionFile.exists())
                return;
            Element[] condition = {null};
            String[] country = {null};
            String[] language = {null};
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document version = db.parse(versionFile);
            if (findCondition(version, getLocales(this), condition, country, language)) {
                File versionDir = new File(adsDir,
                        version.getDocumentElement().getAttribute("number"));
                /*DisplayMetrics displayMetrics = new DisplayMetrics();
                getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
                double screenMin = Math.min(
                        (double)displayMetrics.widthPixels / (double)displayMetrics.density,
                        (double)displayMetrics.heightPixels / (double)displayMetrics.density);*/
                //double screenWidth = (double)displayMetrics.widthPixels / (double)displayMetrics.densityDpi;
                //double screenHeight = (double)displayMetrics.heightPixels / (double)displayMetrics.densityDpi;
                //double screenInches = Math.sqrt(Math.pow(screenWidth, 2) + Math.pow(screenHeight, 2));
                String path = null, href = null;
                float width = 0, height = 0;
                for (int i = 0; i < condition[0].getChildNodes().getLength(); i++) {
                    if (condition[0].getChildNodes().item(i).getNodeName().equals("device")) {
                        Element device = (Element)condition[0].getChildNodes().item(i);
                        if (    (device.getAttribute("target") == null
                                    || device.getAttribute("target").isEmpty()
                                    || device.getAttribute("target").equals("server"))
                            &&  (device.getAttribute("screenMin") == null
                                    || device.getAttribute("screenMin").isEmpty()
                                    || screenMin >= Double.parseDouble(device.getAttribute("screenMin")))
                            &&
                                (device.getAttribute("sdkMin") == null
                                    || device.getAttribute("sdkMin").isEmpty()
                                    || Build.VERSION.SDK_INT >= Integer.parseInt(device.getAttribute("sdkMin")))) {
                            path = device.getAttribute("path");
                            bannerUrl = device.getAttribute("url");
                            width = Float.parseFloat(device.getAttribute("width"));
                            height = Float.parseFloat(device.getAttribute("height"));
                            width = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, width, getResources().getDisplayMetrics());
                            height = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, height, getResources().getDisplayMetrics());
                            bannerType = device.getAttribute("type");
                            break;
                        }
                    }
                }
                if (path != null && width > 0 && height > 0
                        && (bannerType.equals("replace") || (bannerType.equals("offline") && bannerShowOffline))) {
                    File index = new File(versionDir, path);
                    if (index.exists()) {
                        String url = index.toURI().toString();
                        banner.getSettings().setJavaScriptEnabled(true);
                        banner.loadUrl(url);
                        RelativeLayout.LayoutParams lpView = new RelativeLayout.LayoutParams(
                                Math.round(width), Math.round(height));
                        lpView.addRule(RelativeLayout.CENTER_HORIZONTAL);
                        banner.setLayoutParams(lpView);
                        showAdView = false;
                        banner.setVisibility(View.VISIBLE);
                        adView.setVisibility(View.GONE);
                    }
                }
            }
        }
        catch(Exception e) {
            e.printStackTrace();
        }
        if (showAdView) {
            try {
                banner.setVisibility(View.GONE);
                adView.setVisibility(View.VISIBLE);
                AdRequest adRequest = new AdRequest.Builder().build();
                adView.setAdListener(new AdListener() {
                    @Override
                    public void onAdFailedToLoad(int var1) {
                        try {
                            if (bannerType.equals("offline")) {
                                bannerShowOffline = true;
                                loadedAds = false;
                                loadAds();
                            }
                            //Toast.makeText(thisActivity, "Ad error", Toast.LENGTH_LONG).show();
                        } catch(Throwable e) {
                            e.printStackTrace();
                        }
                    }

                    @Override
                    public void onAdLoaded() {
                        try {
                            banner.setVisibility(View.GONE);
                            adView.setVisibility(View.VISIBLE);
                            //Toast.makeText(thisActivity, "Ad loaded", Toast.LENGTH_LONG).show();
                        } catch(Throwable e) {
                            e.printStackTrace();
                        }
                    }

                    @Override
                    public void onAdOpened() {
                        try {
                            banner.setVisibility(View.GONE);
                            adView.setVisibility(View.VISIBLE);
                            //Toast.makeText(thisActivity, "Ad opened", Toast.LENGTH_LONG).show();
                        } catch(Throwable e) {
                            e.printStackTrace();
                        }
                    }
                });
                adView.loadAd(adRequest);
            } catch(Throwable e) {
                e.printStackTrace();
            }
        }
    }

    //See http://androidosbeginning.blogspot.ru/2010/09/gif-animation-in-android.html
    /*private class MYGIFView extends View{
        Movie movie,movie1;
        InputStream is=null,is1=null;
        long moviestart;

        public MYGIFView(Context context) {
            super(context);
            is=context.getResources().openRawResource(R.drawable.banner_320x50);
            movie=Movie.decodeStream(is);
        }

        @Override
        protected void onDraw(Canvas canvas) {

            canvas.drawColor(Color.WHITE);
            super.onDraw(canvas);
            long now=android.os.SystemClock.uptimeMillis();
            System.out.println("now="+now);
            if (moviestart == 0) { // first time
                moviestart = now;
            }
            System.out.println("\tmoviestart="+moviestart);
            int relTime = (int)((now - moviestart) % movie.duration()) ;
            System.out.println("time="+relTime+"\treltime="+movie.duration());
            movie.setTime(relTime);
            movie.draw(canvas,this.getWidth()/2-20,this.getHeight()/2-40);
            this.invalidate();
        }
    }*/

    @Override
    public void onDestroy() {
        super.onDestroy();
        backgroundThreadTerminated = true;
        /*try {
            if (mHelper != null)
                mHelper.dispose();
        } catch (IabHelper.IabAsyncInProgressException e) {
            e.printStackTrace();
        }
        mHelper = null;*/
    }

    @Override
    protected void onStop (){
        //if (robotThreadRunnable != null)
        //    robotThreadRunnable.mainActivity = null;
        //if (controlsUpdater != null)
        //    controlsUpdater.mainActivity = null;
        if (robotState == ROBOT_STATE_REQUEST_ENABLE_BLUETOOTH)
            robotState = ROBOT_STATE_DISCONNECTED;
        super.onStop();
    }

    private void createBroadcastReceiver(){
        bluetoothReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                /*int bluetoothState = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE,
                        BluetoothAdapter.ERROR);
                if (bluetoothState == BluetoothAdapter.STATE_ON)*/
                    checkBluetooth();
            }
        };
        registerReceiver(bluetoothReceiver, new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));
        wifiReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                /*int wifiState = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE,
                        WifiManager.WIFI_STATE_UNKNOWN);
                if (wifiState == WifiManager.WIFI_STATE_ENABLED)*/
                    new Thread(controlsUpdater).start();
                //else
                //    updateControls();
            }
        };
        registerReceiver(wifiReceiver, new IntentFilter(WifiManager.WIFI_STATE_CHANGED_ACTION));
    }

    /*@Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        //getMenuInflater().inflate(R.menu.menu_main, menu);
        //return true;
        return false;
    }*/

    /*@Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            //Intent intent = new Intent(this, GlobalSettingsActivity.class);
            Intent intent = new Intent(this, SettingsActivity.class);
            startActivity(intent);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }*/

    public void onStartButtonClick(View v)
    {
        try {
            //try {
            if (HttpServer.getServerState() == HttpServer.SERVER_IS_OFF) {
                HttpServer.start(this);
                btnServer.startAnimation(animationStartServer);
            }
            else if (HttpServer.getServerState() == HttpServer.SERVER_IS_WORKING){
                HttpServer.updateServerSettings(this, true);
                HttpServer.stop(this);
            }
            /*}
            finally {
                if (getResources().getConfiguration().orientation != Configuration.ORIENTATION_LANDSCAPE)
                    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                else
                    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            }*/
        }
        catch(Exception e){
            e.printStackTrace();
        }
    }

    public void onLocalControlsButtonClick(View v) {
        Intent intent = new Intent(this, LocalControllersActivity.class);
        startActivity(intent);
    }

    public void onSettingsButtonClick(View v){
        Intent intent = new Intent(this, GlobalSettingsActivity.class);
        //Intent intent = new Intent(this, SettingsActivity.class);
        startActivity(intent);
    }

    /*public void onBluetoothDeviceListClick(View v){
        RoboCamDriver driver = RoboCamDriver.getCurrentDriver();
        //driver.connect(BluetoothAdapter.getDefaultAdapter().getBondedDevices());
    }*/

    public void onConnectToRobot(View v) {
        try {
            if (robotState == ROBOT_STATE_DISCONNECTED
                    || robotState == ROBOT_STATE_DEVICES_NOT_FOUND
                    || robotState == ROBOT_STATE_CONNECTION_ERROR) {
                RoboCamDriver driver = RoboCamDriver.getCurrentDriver();
                if (driver.needBluetooth()) {
                    if (BluetoothAdapter.getDefaultAdapter() == null) {
                        showRobotMessageError(getString(R.string.bluetooth_is_not_supported));
                        return;
                    }
                    if (!BluetoothAdapter.getDefaultAdapter().isEnabled()) {
                        robotStateError = null;
                        robotState = ROBOT_STATE_REQUEST_ENABLE_BLUETOOTH;
                        updateControls();
                        Intent enableBluetooth = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                        startActivityForResult(enableBluetooth, REQUEST_ENABLE_BT);
                        robotThreadRunnable = new RobotThreadRunnable(this);
                        new Thread(robotThreadRunnable).start();
                    } else {
                        robotStateError = null;
                        robotState = ROBOT_STATE_SELECTING;
                        updateControls();
                    }
                }
            } else if (robotState == ROBOT_STATE_SELECTING) {
                robotState = ROBOT_STATE_DISCONNECTED;
                updateControls();
            } else if (robotState == ROBOT_STATE_CONNECTING
                    || robotState == ROBOT_STATE_CONNECTED) {
                robotState = ROBOT_STATE_DISCONNECTED;
                RoboCamDriver.getCurrentDriver().disconnect();
                updateControls();
            }
        } catch(Throwable e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onConnected() {
        if (robotState == ROBOT_STATE_CONNECTING){
            robotState = ROBOT_STATE_CONNECTED;
            postUpdateControls();
        }
    }

    @Override
    public void onConnectionError(String error) {
        if (robotState == ROBOT_STATE_CONNECTING){
            robotState = ROBOT_STATE_CONNECTION_ERROR;
            robotStateError = error;
            postUpdateControls();
        }
    }

    @Override
    public void onConnectionError(int errorResId) {
        if (robotState == ROBOT_STATE_CONNECTING){
            robotState = ROBOT_STATE_CONNECTION_ERROR;
            robotStateError = getString(errorResId);
            postUpdateControls();
        }
    }

    @Override
    public void onConnectionError(int errorResId, Object... formatArgs) {
        if (robotState == ROBOT_STATE_CONNECTING){
            robotState = ROBOT_STATE_CONNECTION_ERROR;
            robotStateError = getString(errorResId, formatArgs);
            postUpdateControls();
        }
    }

    @Override
    public void onDisconnected() {
        robotState = ROBOT_STATE_DISCONNECTED;
        robotStateError = null;
        postUpdateControls();
    }

    @Override
    public void onDisconnected(int errorResId) {
        robotState = ROBOT_STATE_CONNECTION_ERROR;
        robotStateError = getString(errorResId);
        postUpdateControls();
    }

    @Override
    public void onDisconnected(int errorResId, Object... formatArgs) {
        robotState = ROBOT_STATE_CONNECTION_ERROR;
        robotStateError = getString(errorResId, formatArgs);
        postUpdateControls();
    }

    class RobotThreadRunnable implements Runnable {
        public volatile MainActivity mainActivity = null;

        public RobotThreadRunnable(MainActivity mainActivity) {
            this.mainActivity = mainActivity;
        }

        public void run() {
            try {
                Thread.sleep(10000);
                if (mainActivity != null) {
                    if (robotState == ROBOT_STATE_REQUEST_ENABLE_BLUETOOTH) {
                        robotState = ROBOT_STATE_DISCONNECTED;
                        mainActivity.postUpdateControls();
                    }
                }
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }
    }

    class ControlsUpdater implements Runnable {
        public volatile MainActivity mainActivity = null;

        public ControlsUpdater(MainActivity mainActivity) {
            this.mainActivity = mainActivity;
        }

        @Override
        public void run() {
            try {
                Thread.sleep(1000);
                if (mainActivity != null) {
                    mainActivity.postUpdateControls();
                }
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }
    }

    protected void onActivityResult(int requestCode, int resultCode,
                                    Intent data) {
        /*if (requestCode == REQUEST_ENABLE_BT && robotState == ROBOT_STATE_REQUEST_ENABLE_BLUETOOTH) {
            if (resultCode == RESULT_CANCELED) {
                robotState = ROBOT_STATE_DISCONNECTED;
                updateControls();
            }
        }*/
    }

    private void checkBluetooth() {
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (robotState == ROBOT_STATE_REQUEST_ENABLE_BLUETOOTH) {
            if (bluetoothAdapter.getState() == BluetoothAdapter.STATE_OFF) {
                robotState = ROBOT_STATE_DISCONNECTED;
                updateControls();
            }
            else if (bluetoothAdapter.getState() == BluetoothAdapter.STATE_ON) {
                robotState = ROBOT_STATE_SELECTING;
                updateControls();
            }
        }
        else if (robotState == ROBOT_STATE_CONNECTED
                || robotState == ROBOT_STATE_CONNECTING){
            if (bluetoothAdapter.getState() == BluetoothAdapter.STATE_OFF) {
                RoboCamDriver.getCurrentDriver().disconnect();
                robotState = ROBOT_STATE_DISCONNECTED;
                updateControls();
            }
        }
    }

    private void setRobotClickListener() {
        robotListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View view,
                                    int position, long id) {
                BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
                if (robotState == ROBOT_STATE_SELECTING){
                    RoboCamDriver driver = RoboCamDriver.getCurrentDriver();
                    robotState = ROBOT_STATE_CONNECTING;
                    lastBluetoothDevice = ((BluetoothDevice)robotListView.getAdapter().getItem(position)).getName();
                    getPreferenceEditor().putString(ExtraKey.LAST_BLUETOOTH_DEVICE, lastBluetoothDevice);
                    apply();
                    driver.connect((BluetoothDevice)robotListView.getAdapter().getItem(position));
                    updateControls();
                }
            }
        });
    }

    private void showServerMessage(int resId){
        serverMessage.setText(resId);
        serverMessage.setTextColor(Color.BLACK);
        showServerMessage();
    }

    private void showServerMessage(String message){
        serverMessage.setText(message);
        serverMessage.setTextColor(Color.BLACK);
        showServerMessage();
    }

    private void showServerMessageError(String message){
        serverMessage.setText(message);
        serverMessage.setTextColor(Color.RED);
        showServerMessage();
    }

    private void showServerMessage() {
        serverMessage.setVisibility(View.VISIBLE);
        serverMessageConnector.setVisibility(View.VISIBLE);
    }

    private void hideServerMessage(){
        serverMessage.setVisibility(View.INVISIBLE);
        serverMessageConnector.setVisibility(View.INVISIBLE);
    }

    private void showRobotMessage(int resId){
        String s = getString(resId);
        robotMessage.setText(resId);
        robotMessage.setTextColor(Color.BLACK);
        showRobotMessage();
    }

    private void showRobotMessage(String message){
        robotMessage.setText(message);
        robotMessage.setTextColor(Color.BLACK);
        showRobotMessage();
    }

    private void showRobotMessageError(String message){
        robotMessage.setText(message);
        robotMessage.setTextColor(Color.RED);
        showRobotMessage();
    }

    private void showRobotMessageError(int resId){
        robotMessage.setText(resId);
        robotMessage.setTextColor(Color.RED);
        showRobotMessage();
    }

    private void showRobotMessage() {
        robotMessage.setVisibility(View.VISIBLE);
        robotMessageConnector.setVisibility(View.VISIBLE);
        robotListView.setVisibility(View.INVISIBLE);
    }

    private void hideRobotMessage(){
        robotMessage.setVisibility(View.INVISIBLE);
        robotMessageConnector.setVisibility(View.INVISIBLE);
        robotListView.setVisibility(View.INVISIBLE);
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        try {
            if (cameraManager.isInitialized())
                cameraManager.changeHolder(this, holder);
            else
                cameraManager.initCamera(this, holder);
        }
        catch(Exception e){
            e.printStackTrace();
        }
    }

    @Override
    protected void onPause(){
        RoboCamBroker.removeRoboCamBrokerListener(this);
        RoboCamDriver.getCurrentDriver().setDriverListener(null);
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        if (Build.VERSION.SDK_INT >= 20)
            isScreenOn = powerManager.isInteractive();
        else
            isScreenOn = powerManager.isScreenOn();
        super.onPause();
    }

    @Override
    protected void onResume (){
        SharedPreferences settings = getSharedPreferences(ExtraKey.APP_PREFERENCE, Context.MODE_PRIVATE);
        useLocalControls = settings.getBoolean(ExtraKey.USE_LOCAL_CONTROLS, DefaultValue.USE_LOCAL_CONTROLS);
        btnServer.setVisibility(useLocalControls ? View.GONE : View.VISIBLE);
        btnLocalControls.setVisibility(useLocalControls ? View.VISIBLE : View.GONE);
        imageViewBackground.setVisibility(useLocalControls ? View.VISIBLE : View.GONE);
        HttpServer.updateServerSettings(this, false);
        if (useLocalControls && HttpServer.getServerState() != HttpServer.SERVER_IS_OFF)
            HttpServer.stop(this);
        if (Build.VERSION.SDK_INT >= CameraManager.CAMERA2_SDK) {
            textureView.setVisibility(useLocalControls ? View.GONE : View.VISIBLE);
        } else {
            surfaceView.setVisibility(useLocalControls ? View.GONE : View.VISIBLE);
        }
        RoboCamBroker.addRoboCamBrokerListener(this);
        RoboCamDriver.getCurrentDriver().setDriverListener(this);
        updateControls();
        isScreenOn = true;
        super.onResume();
        RoboCamDriver.updateCurrentDriver(this, true /*purchaseState == PURCHASE_STATE_PREMIUM*/);
        postUpdateControls();
        postUpdateCamera();
        //DownloadAd();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        try {
            postUpdateCamera();
        }
        catch(Exception e){
            e.printStackTrace();
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        try {
            cameraManager.releaseCamera(holder);
        }
        catch(Exception e){
            e.printStackTrace();
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        postUpdateCamera();
    }

    private void postUpdateCamera() {
        parentLayout.post(new Runnable() {
            @Override
            public void run() {
                updateCamera();
            }
        });
    }

    private void setSurfaceSize() {
        PreviewSize previewSize = cameraManager.getPreviewSize();
        if (previewSize != null) {
            ViewGroup.LayoutParams layoutParams;
            if (Build.VERSION.SDK_INT >= CameraManager.CAMERA2_SDK)
                layoutParams = textureView.getLayoutParams();
            else
                layoutParams = surfaceView.getLayoutParams();
            float previewWidth, previewHeight;
            if (getResources().getConfiguration().orientation != Configuration.ORIENTATION_LANDSCAPE) {
                previewWidth = previewSize.height;
                previewHeight = previewSize.width;
            } else {
                previewWidth = previewSize.width;
                previewHeight = previewSize.height;
            }
            float ratioX = (float) parentLayout.getWidth() / previewWidth;
            float ratioY = (float) parentLayout.getHeight() / previewHeight;
            if (ratioX < ratioY) {
                layoutParams.width = parentLayout.getWidth();
                layoutParams.height = (int) ((float) previewHeight * ratioX);
            } else {
                layoutParams.width = (int) ((float) previewWidth * ratioY);
                layoutParams.height = parentLayout.getHeight();
            }
            if (Build.VERSION.SDK_INT >= CameraManager.CAMERA2_SDK)
                textureView.setLayoutParams(layoutParams);
            else
                surfaceView.setLayoutParams(layoutParams);
        }
    }

    private void updateCamera() {
        //android:configChanges="orientation|screenSize"
        /*parentLayout.post(new Runnable() {
            @Override
            public void run() {*/
                try {
                    //if (cameraManager.checkIfParametersIsChanged() ||
                    //        (isScreenOn && cameraManager.checkIfDisplayRotationIsChanged(thisActivity))) {
                    boolean orientationIsUpdated = false;
                    boolean parametersIsUpdated = false;
                    if (Build.VERSION.SDK_INT < CameraManager.CAMERA2_SDK)
                        cameraManager.stopPreview();
                    if (isScreenOn)
                        orientationIsUpdated = cameraManager.updateOrientation(thisActivity);
                    parametersIsUpdated = cameraManager.updateParameters();
                    setSurfaceSize();
                    if (Build.VERSION.SDK_INT < CameraManager.CAMERA2_SDK)
                        cameraManager.startPreview();
                    /*ViewGroup.LayoutParams layoutParams;
                    if (Build.VERSION.SDK_INT >= CameraManager.CAMERA2_SDK)
                        layoutParams = textureView.getLayoutParams();
                    else
                        layoutParams = surfaceView.getLayoutParams();
                    float previewWidth, previewHeight;
                    if (getResources().getConfiguration().orientation != Configuration.ORIENTATION_LANDSCAPE) {
                        previewWidth = cameraManager.getPreviewSize().height;
                        previewHeight = cameraManager.getPreviewSize().width;
                    } else {
                        previewWidth = cameraManager.getPreviewSize().width;
                        previewHeight = cameraManager.getPreviewSize().height;
                    }
                    float ratioX = (float) parentLayout.getWidth() / previewWidth;
                    float ratioY = (float) parentLayout.getHeight() / previewHeight;
                    if (ratioX < ratioY) {
                        layoutParams.width = parentLayout.getWidth();
                        layoutParams.height = (int) ((float) previewHeight * ratioX);
                    } else {
                        layoutParams.width = (int) ((float) previewWidth * ratioY);
                        layoutParams.height = parentLayout.getHeight();
                    }
                    if (Build.VERSION.SDK_INT >= CameraManager.CAMERA2_SDK)
                        textureView.setLayoutParams(layoutParams);
                    else {
                        surfaceView.setLayoutParams(layoutParams);
                        cameraManager.startPreview();
                    }*/
                    if (orientationIsUpdated || parametersIsUpdated)
                        HttpServer.broadcastMessage("<msg><name>updatePictureSize</name>"
                                + "<prw>" + cameraManager.getActualPreviewWidth() + "</prw>"
                                + "<prh>" + cameraManager.getActualPreviewHeight() + "</prh>"
                                + "</msg>");
                    //}
                }
                catch(Exception e)
                {
                    e.printStackTrace();
                }
            //}
        //});
    }

    private String getStringIpAddress(int ipAddress) {
        String ipAddressString = null;
        if (ByteOrder.nativeOrder().equals(ByteOrder.LITTLE_ENDIAN))
            ipAddress = Integer.reverseBytes(ipAddress);
        byte[] ipByteArray = BigInteger.valueOf(ipAddress).toByteArray();
        try {
            ipAddressString = InetAddress.getByAddress(ipByteArray).getHostAddress();
        } catch (UnknownHostException e) {
            e.printStackTrace();
            ipAddressString = null;
        }
        return ipAddressString;
    }

    private String getServerAddress() {
        String ipAddressString = null;
        WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
        try {
            Method method = wifiManager.getClass().getDeclaredMethod("getWifiApState");
            method.setAccessible(true);
            int actualState = (Integer) method.invoke(wifiManager, (Object[]) null);
            if (actualState < 10)
                actualState += 10;
            if (actualState == AP_STATE_ENABLED) {
                //Android 2.3 patch
                for (Enumeration<NetworkInterface> enumerator = NetworkInterface.getNetworkInterfaces(); enumerator.hasMoreElements(); ) {
                    NetworkInterface intf = enumerator.nextElement();
                    for (Enumeration<InetAddress> enumInetAddress = intf.getInetAddresses(); enumInetAddress.hasMoreElements(); ) {
                        InetAddress address = enumInetAddress.nextElement();
                        if (!address.isLoopbackAddress() && (address.getAddress().length == 4)) {
                            String ip = address.getHostAddress();
                            if ("192.168.43.1".equals(ip)) {
                                ipAddressString = ip;
                                break;
                            }
                        }
                    }
                    if (ipAddressString != null)
                        break;
                }
                //Android 6 patch
                if (ipAddressString == null)
                    for (Enumeration<NetworkInterface> enumerator = NetworkInterface.getNetworkInterfaces(); enumerator.hasMoreElements(); ) {
                        NetworkInterface intf = enumerator.nextElement();
                        if (intf.getName().contains("ap"))
                            for (Enumeration<InetAddress> enumInetAddress = intf.getInetAddresses(); enumInetAddress.hasMoreElements(); ) {
                                InetAddress address = enumInetAddress.nextElement();
                                if (!address.isLoopbackAddress() && (address.getAddress().length == 4)) {
                                    ipAddressString = address.getHostAddress();
                                    break;
                                }
                            }
                        if (ipAddressString != null)
                            break;
                    }
                if (ipAddressString == null) {
                    int ipAddress = wifiManager.getDhcpInfo().ipAddress;
                    ipAddressString = getStringIpAddress(ipAddress);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            ipAddressString = null;
        }
        if (ipAddressString == null) {
            WifiInfo wifiInfo = wifiManager.getConnectionInfo();
            int ipAddress = wifiInfo.getIpAddress();
            ipAddressString = getStringIpAddress(ipAddress);
        }
        if (ipAddressString == null) {
            try {
                for (Enumeration<NetworkInterface> enumerator = NetworkInterface.getNetworkInterfaces(); enumerator.hasMoreElements(); ) {
                    NetworkInterface intf = enumerator.nextElement();
                    if (intf.getName().contains("wlan"))
                        for (Enumeration<InetAddress> enumInetAddress = intf.getInetAddresses(); enumInetAddress.hasMoreElements(); ) {
                            InetAddress address = enumInetAddress.nextElement();
                            if (!address.isLoopbackAddress() && (address.getAddress().length == 4)) {
                                ipAddressString = address.getHostAddress();
                                break;
                            }
                        }
                    if (ipAddressString != null)
                        break;
                }
            } catch (Exception e) {
                e.printStackTrace();
                ipAddressString = null;
            }
        }
        if (ipAddressString != null) {
            ipAddressString = "http://" + ipAddressString;
            if (HttpServer.getHttpPort() != 80)
                ipAddressString += ":" + Integer.toString(HttpServer.getHttpPort());
        }
        return ipAddressString;
    }

    @Override
    public void onServerStateChange() {
        postUpdateControls();
    }

    @Override
    public void onTestMessage(String msg) {
        /*testMessageText = msg;
        parentLayout.post(new Runnable(){
            @Override
            public void run() {
                if (thisActivity.testMessage.getVisibility() != View.VISIBLE)
                    thisActivity.testMessage.setVisibility(View.VISIBLE);
                thisActivity.testMessage.setText(testMessageText);
            }
        });*/
    }

    /*class TestMessageRunnable implements Runnable{
        public String msg;

        @Override
        public void run() {
            thisActivity.testMessage.setText(msg);
        }
    }*/

    /*
    //http://ru.stackoverflow.com/questions/451288/android-%D0%9E%D1%82%D0%BF%D1%80%D0%B0%D0%B2%D0%BA%D0%B0-%D0%BD%D0%B0%D0%B7%D0%B2%D0%B0%D0%BD%D0%B8%D0%B9-%D0%BD%D0%B0%D0%B9%D0%B4%D0%B5%D0%BD%D0%BD%D1%8B%D1%85-bluetooth-%D1%83%D1%81%D1%82%D1%80%D0%BE%D0%B9%D1%81%D1%82%D0%B2-%D0%BD%D0%B0-%D1%81%D0%B5%D1%80%D0%B2%D0%B5%D1%80
    public void discoverDevices(View view) {

        discoveredDevices.clear();
        listAdapter.notifyDataSetChanged();

        if (discoverDevicesReceiver == null) {
            discoverDevicesReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    String action = intent.getAction();

                    if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                        BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

                        if (!discoveredDevices.contains(device)) {
                            discoveredDevices.add(device);
                            listAdapter.notifyDataSetChanged();
                        }
                    }
                }
            };
        }

        if (discoveryFinishedReceiver == null) {
            discoveryFinishedReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    getListView().setEnabled(true);
                    if (progressDialog != null)
                        progressDialog.dismiss();

                    unregisterReceiver(discoveryFinishedReceiver);
                }
            };
        }

        registerReceiver(discoverDevicesReceiver, new IntentFilter(BluetoothDevice.ACTION_FOUND));
        registerReceiver(discoveryFinishedReceiver, new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED));

        getListView().setEnabled(false);

        progressDialog = ProgressDialog.show(this, "Searching for devices", "Please wait...");

        bluetoothAdapter.startDiscovery();
    }*/

    private void postUpdateControls(){
        try {
            parentLayout.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        thisActivity.updateControls();
                    } catch (Throwable e) {
                        e.printStackTrace();
                    }
                }
            });
        } catch(Throwable e) {
            e.printStackTrace();
        }
    }

    public void updateControls(){
        try {
            if (useLocalControls) {
                showServerMessage(R.string.go_to_the_controllers);
            }
            else {
                int serverState = HttpServer.getServerState();
                switch (serverState) {
                    case HttpServer.SERVER_IS_OFF:
                        showServerMessage(R.string.server_is_off);
                        btnServer.clearAnimation();
                        btnServer.setBackgroundResource(R.drawable.start_server_bg);
                        break;
                    case HttpServer.SERVER_IS_INITIALIZING:
                        showServerMessage(R.string.server_is_initializing);
                        break;
                    case HttpServer.SERVER_IS_WORKING:
                        String serverAddress = getServerAddress();
                        if (serverAddress == null) {
                            WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
                            if (wifiManager.getWifiState() == WifiManager.WIFI_STATE_ENABLED)
                                new Thread(controlsUpdater).start();//waiting for an ip-address
                        }
                        showServerMessage(getString(R.string.server_is_working)
                                + (serverAddress == null ? "" : (":\r\n" + serverAddress)));
                        btnServer.clearAnimation();
                        btnServer.setBackgroundResource(R.drawable.server_started_bg);
                        break;
                    case HttpServer.SERVER_IS_STOPPING:
                        showServerMessage(R.string.server_is_stopping);
                        break;
                }
            }
            RoboCamDriver driver = RoboCamDriver.getCurrentDriver();
            //ERROR WHEN DISCONNECTING FROM ROBOT!!!
            if (driver.isDisconnected() && robotState == ROBOT_STATE_CONNECTED)
                robotState = ROBOT_STATE_DISCONNECTED;
            switch (robotState) {
                case ROBOT_STATE_DISCONNECTED:
                    btnRobot.clearAnimation();
                    btnRobot.setBackgroundResource(R.drawable.connect_robot);
                    showRobotMessage(getString(driver.getStringResId(RoboCamDriver.ROBOT_IS_DISCONNECTED))
                            + ("".equals(driver.getSettingsName()) ? "" : ":\r\n" + driver.getSettingsName())
                    );
                    break;
                case ROBOT_STATE_REQUEST_ENABLE_BLUETOOTH:
                    btnRobot.startAnimation(animationConnectRobot);
                    showRobotMessage(driver.getStringResId(RoboCamDriver.ROBOT_IS_CONNECTING));
                    break;
                case ROBOT_STATE_SELECTING:
                    btnRobot.startAnimation(animationConnectRobot);
                    hideRobotMessage();
                    List<BluetoothDevice> devices = new ArrayList<BluetoothDevice>(
                            BluetoothAdapter.getDefaultAdapter().getBondedDevices());
                    if (devices.size() > 0) {
                        Collections.sort(devices, new Comparator<BluetoothDevice>() {
                            @Override
                            public int compare(BluetoothDevice lhs, BluetoothDevice rhs) {
                                if (lhs.getName().equals(lastBluetoothDevice)
                                        && rhs.getName().equals(lastBluetoothDevice))
                                    return 0;
                                else if (lhs.getName().equals(lastBluetoothDevice))
                                    return -1;
                                else if (rhs.getName().equals(lastBluetoothDevice))
                                    return 1;
                                else
                                    return lhs.getName().compareTo(rhs.getName());
                            }
                        });
                        ArrayAdapter<BluetoothDevice> adapter = new ArrayAdapter<BluetoothDevice>(this,
                                android.R.layout.simple_list_item_1, devices) {
                            @Override
                            public View getView(int position, View convertView, ViewGroup parent) {
                                View view = super.getView(position, convertView, parent);
                                final BluetoothDevice device = getItem(position);
                                ((TextView) view.findViewById(android.R.id.text1)).setText(
                                        device.getName());
                                return view;
                            }
                        };
                        robotListView.setAdapter(adapter);
                        robotMessageConnector.setVisibility(View.VISIBLE);
                        robotListView.setVisibility(View.VISIBLE);
                    } else {
                        robotState = ROBOT_STATE_DEVICES_NOT_FOUND;
                        updateControls();
                    }
                    break;
                case ROBOT_STATE_DEVICES_NOT_FOUND:
                    btnRobot.clearAnimation();
                    showRobotMessageError(driver.getStringResId(RoboCamDriver.ROBOT_IS_NOT_FOUND_VIA_BLUETOOTH));
                    break;
                case ROBOT_STATE_CONNECTING:
                    btnRobot.startAnimation(animationConnectRobot);
                    showRobotMessage(
                            String.format(getString(driver.getStringResId(RoboCamDriver.ROBOT_IS_CONNECTING_TO)),
                                    driver.getRobotName())
                                    + ("".equals(driver.getSettingsName()) ? "" : ":\r\n" + driver.getSettingsName())
                    );
                    break;
                case ROBOT_STATE_CONNECTED:
                    btnRobot.clearAnimation();
                    btnRobot.setBackgroundResource(R.drawable.robot_connected_bg);
                    showRobotMessage(
                            String.format(getString(driver.getStringResId(RoboCamDriver.ROBOT_IS_CONNECTED_TO)),
                                    driver.getRobotName())
                                    + ("".equals(driver.getSettingsName()) ? "" : ":\r\n" + driver.getSettingsName())
                    );
                    break;
                case ROBOT_STATE_CONNECTION_ERROR:
                    btnRobot.clearAnimation();
                    btnRobot.setBackgroundResource(R.drawable.connect_robot);
                    showRobotMessageError(robotStateError);
                    break;
            }
        }
        catch (Throwable e) {
            e.printStackTrace();
        }
    }
}
