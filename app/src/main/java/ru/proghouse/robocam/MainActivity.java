package ru.proghouse.robocam;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.PowerManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.math.BigInteger;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

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

    public static final String SKU_PREMIUM = "Premium";

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
    private IabHelper mHelper;
    private IabHelper.QueryInventoryFinishedListener mGotInventoryListener;
    private int purchaseState = PURCHASE_STATE_UNKNOWN;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            setContentView(R.layout.activity_main);
            getSupportActionBar().hide();
            surfaceView = (SurfaceView) findViewById(R.id.surfaceView);
            surfaceView.getHolder().addCallback(this);
            surfaceView.getHolder().setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
            parentLayout = (RelativeLayout) findViewById(R.id.parentLayout);
            thisActivity = this;
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

            String base64EncodedPublicKey = "";
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
            });
        }catch(Throwable e){
            e.printStackTrace();
        }
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
        RoboCamDriver.updateCurrentDriver(this, purchaseState == PURCHASE_STATE_PREMIUM);
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

    private String getServerAddress(){
        WifiManager wifiManager = (WifiManager) getSystemService(WIFI_SERVICE);
        WifiInfo wifiInfo = wifiManager.getConnectionInfo();
        int ipAddress = wifiInfo.getIpAddress();
        if (ByteOrder.nativeOrder().equals(ByteOrder.LITTLE_ENDIAN))
            ipAddress = Integer.reverseBytes(ipAddress);
        byte[] ipByteArray = BigInteger.valueOf(ipAddress).toByteArray();
        String ipAddressString;
        try {
            ipAddressString = InetAddress.getByAddress(ipByteArray).getHostAddress();
        } catch (UnknownHostException e) {
            e.printStackTrace();
            ipAddressString = null;
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
        testMessageText = msg;
        parentLayout.post(new Runnable(){
            @Override
            public void run() {
                if (thisActivity.testMessage.getVisibility() != View.VISIBLE)
                    thisActivity.testMessage.setVisibility(View.VISIBLE);
                thisActivity.testMessage.setText(testMessageText);
            }
        });
    }

    class TestMessageRunnable implements Runnable{
        public String msg;

        @Override
        public void run() {
            thisActivity.testMessage.setText(msg);
        }
    }

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
