package ru.proghouse.robocam;

//TODO: Create embedded localized ads and ability to show my own online localized ads instead google ads.
/*Ad formats for tablet PCs
        728 x 90
        300 x 250
        468 x 60*/
//TODO: Image to show QR-Code.
//TODO: Smiles.
//TODO: Button to focus and to take a picture.
//TODO: Export and import settings.

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Movie;
import android.graphics.Point;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Environment;
import android.os.LocaleList;
import android.os.PowerManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
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

import java.io.DataInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.NetworkInterface;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import ru.proghouse.robocam.drivers.EV3.EV3Driver;
import ru.proghouse.robocam.drivers.RoboCamDriver;
import ru.proghouse.robocam.util.IabHelper;
import ru.proghouse.robocam.util.IabResult;
import ru.proghouse.robocam.util.Inventory;

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

    private CameraManager cameraManager = CameraManager.getCameraManager();
    private SurfaceView surfaceView = null;
    private RelativeLayout parentLayout = null;
    private MainActivity thisActivity = null;
    private boolean isScreenOn = true;
    private TextView serverMessage = null;
    private ImageView serverMessageConnector = null;
    private TextView robotMessage = null;
    private ImageView robotMessageConnector = null;
    private Animation animationStartServer = null;
    private Animation animationConnectRobot = null;
    private ImageButton btnServer = null;
    private ImageButton btnRobot = null;
    private BroadcastReceiver bluetoothReceiver = null;
    private BroadcastReceiver wifiReceiver = null;
    private ListView robotListView = null;
    private static volatile int robotState = ROBOT_STATE_DISCONNECTED;
    private static volatile String robotStateError = null;
    private volatile RobotThreadRunnable robotThreadRunnable = null;
    private volatile ControlsUpdater controlsUpdater = null;
    private TextView testMessage = null;
    private volatile String testMessageText = null;
    //private IabHelper mHelper;
    //private IabHelper.QueryInventoryFinishedListener mGotInventoryListener;
    //private int purchaseState = PURCHASE_STATE_UNKNOWN;
    //public static final String SKU_PREMIUM = "premium";
    private static volatile boolean loadingAds = false;

    private WebView banner_320x50 = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            setContentView(R.layout.activity_main);
            getSupportActionBar().hide();
            thisActivity = this;

            banner_320x50 = (WebView)findViewById(R.id.banner_320x50);

            DownloadAd();

            MobileAds.initialize(getApplicationContext(), "ca-app-pub-7800876624909705~9648407673");
            AdView mAdView = (AdView) findViewById(R.id.adView);
            AdRequest adRequest = new AdRequest.Builder().build();
            mAdView.setAdListener(new AdListener(){
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
            });
            mAdView.loadAd(adRequest);

            surfaceView = (SurfaceView) findViewById(R.id.surfaceView);
            surfaceView.getHolder().addCallback(this);
            surfaceView.getHolder().setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
            parentLayout = (RelativeLayout) findViewById(R.id.parentLayout);
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
            robotListView = (ListView) findViewById(R.id.robotListView);
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
        }catch(Throwable e){
            e.printStackTrace();
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

    private void DownloadAd() throws IOException {
        try {
            //File cacheDir = getCacheDir();
            //File adsDir = new File(cacheDir, DefaultValue.ADS_DIRECTORY);
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

            List<Locale> locales = new ArrayList<Locale>();
            if (Build.VERSION.SDK_INT >= 24) {
                LocaleList localeList = getResources().getConfiguration().getLocales();
                for (int i = 0; i < localeList.size(); i++)
                    locales.add(localeList.get(i));
            }
            else
                locales.add(getResources().getConfiguration().locale);

            new Thread(new AdsLoader(getString(R.string.local_web_path),
                    locales)).start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    class AdsLoader implements  Runnable {
        String locale = "def";
        List<Locale> locales;

        AdsLoader(String locale, List<Locale> locales) {
            this.locale = locale;
            this.locales = locales;
        }

        @Override
        public void run() {
            try {
                if (loadingAds)
                    return;
                loadingAds = true;
                File cacheDir = getCacheDir();
                File adsDir = new File(cacheDir, DefaultValue.ADS_DIRECTORY);
                File adsLocalizedDir = new File(adsDir, locale);
                adsLocalizedDir.mkdirs();
                File newVersionFile = new File(adsLocalizedDir, "nv.xml");
                DownloadFile("http://www.proghouse.ru/images/t/robocam/v.xml", newVersionFile);
                DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
                DocumentBuilder db = dbf.newDocumentBuilder();
                Document newVersion = db.parse(newVersionFile);
                File versionFile = new File(adsLocalizedDir, "v.xml");
                boolean haveToDownload = false;
                if (!versionFile.exists())
                    haveToDownload = true;
                else {
                    Document version = db.parse(versionFile);
                    haveToDownload = !newVersion.getDocumentElement().getAttribute("number").equals(
                            version.getDocumentElement().getAttribute("number"));
                }
                if (haveToDownload) {
                    if (versionFile.exists())
                        versionFile.delete();
                    newVersionFile.renameTo(versionFile);
                }
            } catch (Exception e) {
                String s = e.getMessage();
                e.printStackTrace();
            }
            loadingAds = false;
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
        if (robotThreadRunnable != null)
            robotThreadRunnable.mainActivity = null;
        if (controlsUpdater != null)
            controlsUpdater.mainActivity = null;
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
        if (robotState == ROBOT_STATE_DISCONNECTED
                || robotState == ROBOT_STATE_DEVICES_NOT_FOUND
                || robotState == ROBOT_STATE_CONNECTION_ERROR) {
            RoboCamDriver driver = RoboCamDriver.getCurrentDriver();
            if (driver.needBluetooth()) {
                if (BluetoothAdapter.getDefaultAdapter() == null) {
                    showServerMessageError(getString(R.string.bluetooth_is_not_supported));
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
        }
        else if (robotState == ROBOT_STATE_SELECTING){
            robotState = ROBOT_STATE_DISCONNECTED;
            updateControls();
        }
        else if (robotState == ROBOT_STATE_CONNECTING
                || robotState == ROBOT_STATE_CONNECTED){
            robotState = ROBOT_STATE_DISCONNECTED;
            RoboCamDriver.getCurrentDriver().disconnect();
            updateControls();
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
        HttpServer.updateServerSettings(this);
        RoboCamBroker.addRoboCamBrokerListener(this);
        RoboCamDriver.getCurrentDriver().setDriverListener(this);
        updateControls();
        isScreenOn = true;
        super.onResume();
        RoboCamDriver.updateCurrentDriver(this, true /*purchaseState == PURCHASE_STATE_PREMIUM*/);
        postUpdateControls();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        try {
            updateCamera();
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

    /*@Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }*/

    private void updateCamera() {
        //android:configChanges="orientation|screenSize"
        /*parentLayout.post(new Runnable() {
            @Override
            public void run() {*/
                try {
                    boolean orientationIsUpdated = false;
                    boolean parametersIsUpdated = false;
                    cameraManager.stopPreview();
                    if (isScreenOn)
                        orientationIsUpdated = cameraManager.updateOrientation(thisActivity);
                    parametersIsUpdated = cameraManager.updateParameters();
                    ViewGroup.LayoutParams layoutParams = surfaceView.getLayoutParams();
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
                    surfaceView.setLayoutParams(layoutParams);
                    cameraManager.startPreview();
                    if (orientationIsUpdated || parametersIsUpdated)
                        HttpServer.broadcastMessage("<msg><name>updatePictureSize</name>"
                                + "<prw>" + cameraManager.getActualPreviewWidth() + "</prw>"
                                + "<prh>" + cameraManager.getActualPreviewHeight() + "</prh>"
                                + "</msg>");
                }
                catch(Exception e)
                {
                    e.printStackTrace();
                }
            /*}
        });*/
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
        WifiManager wifiManager = (WifiManager) getSystemService(WIFI_SERVICE);
        try {
            Method method = wifiManager.getClass().getDeclaredMethod("getWifiApState");
            method.setAccessible(true);
            int actualState = (Integer) method.invoke(wifiManager, (Object[]) null);
            if (actualState == AP_STATE_ENABLED) {
                int ipAddress = wifiManager.getDhcpInfo().ipAddress;
                ipAddressString = getStringIpAddress(ipAddress);
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
        parentLayout.post(new Runnable() {
            @Override
            public void run() {
                thisActivity.updateControls();
            }
        });
    }

    public void updateControls(){
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
                if (serverAddress == null){
                    WifiManager wifiManager = (WifiManager) getSystemService(WIFI_SERVICE);
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
        RoboCamDriver driver = RoboCamDriver.getCurrentDriver();
        //ERROR WHEN DISCONNECTING FROM ROBOT!!!
        if (driver.isDisconnected() && robotState == ROBOT_STATE_CONNECTED)
            robotState = ROBOT_STATE_DISCONNECTED;
        switch (robotState){
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
                    ArrayAdapter<BluetoothDevice> adapter = new ArrayAdapter<BluetoothDevice>(this,
                            android.R.layout.simple_list_item_1, devices) {
                        @Override
                        public View getView(int position, View convertView, ViewGroup parent){
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
                }
                else{
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
}
