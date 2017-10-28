package ru.proghouse.robocam;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Point;
import android.net.Uri;
import android.os.Build;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.Display;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.webkit.WebView;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.io.File;
import java.util.HashSet;
import java.util.Hashtable;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import ru.proghouse.robocam.drivers.RoboCamDriver;

public class LocalControllersActivity extends AppCompatActivity {

    private static LocalControllersActivity localControllersActivity = null;
    private AdView adView = null;
    private WebView banner = null;
    private String bannerUrl = null;
    private double screenMin = 0;
    private int screenMinPixels = 0;
    private int screenWidth = 0;
    private int screenHeight = 0;
    private String bannerType = "";
    private boolean bannerShowOffline = false;
    private volatile boolean loadedAds = false;
    private ImageView imageViewJoystick2 = null;
    private ImageView imageViewJoystick1 = null;
    private ImageView imageViewCurrentJoystick = null;
    private ImageView imageViewEmpty1 = null;
    private ImageView imageViewEmpty2 = null;
    private ImageView imageViewHands = null;
    private TextView textViewDebugMessage = null;
    private TextView textViewText = null;
    private RelativeLayout parentLayout = null;
    private String control = "left-handed";

    private int[] axisValue = new int[] {0, 0, 0, 0, 0, 0, 0, 0};
    private int[] oldAxisValue = new int[] {0, 0, 0, 0, 0, 0, 0, 0};
    private int touchId1 = -1;
    private int touchId2 = -1;
    private boolean showJoysticks = true;
    private boolean hideJoysticks = false;
    private boolean showDebugInfo = true;
    private boolean maximizeJoysticks = false;
    private String axisNames = "xywzabcd";
    private String joystickShapes = "----";
    private String joystickBehaviors = "00000000";
    private int joystickCurrentPanel = 0;
    private double scale = 1;
    private HashSet<Integer> usedKeys = new HashSet<Integer>();
    private HashSet<Integer> keys = new HashSet<Integer>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_local_controllers);
        localControllersActivity = this;
        parentLayout = (RelativeLayout)findViewById(R.id.parentLayout);
        getSupportActionBar().hide();
        fullScreen();

        SharedPreferences settings = getSharedPreferences(ExtraKey.APP_PREFERENCE, Context.MODE_PRIVATE);
        control = settings.getString(ExtraKey.LOCAL_CONTROL, "left-handed");
        joystickCurrentPanel = settings.getInt(ExtraKey.JOYSTICK_CURRENT_PANEL, 0);
        maximizeJoysticks = settings.getBoolean(ExtraKey.MAXIMIZE_JOYSTICKS, DefaultValue.MAXIMIZE_JOYSTICKS);

        if (control.equals("left-handed")) {
            imageViewJoystick1 = (ImageView) findViewById(R.id.imageViewJoystick1);
            imageViewJoystick2 = (ImageView) findViewById(R.id.imageViewJoystick2);
        } else {
            imageViewJoystick1 = (ImageView) findViewById(R.id.imageViewJoystick2);
            imageViewJoystick2 = (ImageView) findViewById(R.id.imageViewJoystick1);
        }
        textViewDebugMessage = (TextView) findViewById(R.id.textViewDebugMessage);
        textViewDebugMessage.setText("");
        textViewText = (TextView)findViewById(R.id.textViewText);
        textViewText.setText("");
        imageViewEmpty1 = (ImageView)findViewById(R.id.imageViewEmpty1);
        imageViewCurrentJoystick = (ImageView)findViewById(R.id.imageViewCurrentJoystick);
        imageViewEmpty2 = (ImageView)findViewById(R.id.imageViewEmpty2);
        imageViewHands = (ImageView)findViewById(R.id.imageViewHands);

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
        screenWidth = width;
        screenHeight = height;
        screenMinPixels = Math.min(width, height);
        scale = (double)screenMinPixels / 1080.0;
        screenMin = Math.min(
                (double)width / (double)displayMetrics.density,
                (double)height / (double)displayMetrics.density);

        imageViewJoystick1.setVisibility(View.INVISIBLE);
        imageViewJoystick2.setVisibility(View.INVISIBLE);
        int joystickSize = (int) (screenMinPixels / 2);
        if (maximizeJoysticks && width > height)
            joystickSize = (int)Math.min((double)height - 120.0 * scale - 30.0 * scale, (double)width / 2.0);
        int buttonSize = (int)(120.0 * scale);
        int offset = (int)(30.0 * scale);
        imageViewJoystick2.getLayoutParams().width = joystickSize;
        imageViewJoystick2.getLayoutParams().height = joystickSize;
        imageViewJoystick1.getLayoutParams().width = joystickSize;
        imageViewJoystick1.getLayoutParams().height = joystickSize;
        imageViewEmpty1.getLayoutParams().width = offset;
        imageViewEmpty1.getLayoutParams().height = offset;
        imageViewCurrentJoystick.getLayoutParams().width = buttonSize;
        imageViewCurrentJoystick.getLayoutParams().height = buttonSize;
        imageViewEmpty2.getLayoutParams().width = offset;
        imageViewEmpty2.getLayoutParams().height = offset;
        imageViewHands.getLayoutParams().width = buttonSize;
        imageViewHands.getLayoutParams().height = buttonSize;
        textViewDebugMessage.setTextSize(TypedValue.COMPLEX_UNIT_PX, Math.round(screenMinPixels / 32));
        textViewText.setTextSize(TypedValue.COMPLEX_UNIT_PX, Math.round(screenMinPixels / 32));

        if (maximizeJoysticks && width > height) {
            RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(buttonSize, buttonSize);
            params.addRule(RelativeLayout.BELOW, R.id.imageViewEmpty2);
            params.addRule(RelativeLayout.LEFT_OF, R.id.imageViewEmpty2);
            imageViewCurrentJoystick.setLayoutParams(params);
            params = new RelativeLayout.LayoutParams(offset, offset);
            params.addRule(RelativeLayout.BELOW, R.id.imageViewEmpty2);
            params.addRule(RelativeLayout.LEFT_OF, R.id.imageViewCurrentJoystick);
            imageViewEmpty1.setLayoutParams(params);
            params = new RelativeLayout.LayoutParams(buttonSize, buttonSize);
            params.addRule(RelativeLayout.BELOW, R.id.imageViewEmpty2);
            params.addRule(RelativeLayout.LEFT_OF, R.id.imageViewEmpty1);
            imageViewHands.setLayoutParams(params);
        }

        _updateJoysticks();

        adView = (AdView) findViewById(R.id.adView);

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

        banner.setVisibility(View.GONE);
        adView.setVisibility(View.GONE);

        loadAds();
    }

    private void fullScreen() {
        //getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
        //        WindowManager.LayoutParams.FLAG_FULLSCREEN);
        /*if (Build.VERSION.SDK_INT >= 19) {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        } else {
            if (Build.VERSION.SDK_INT > 10) {
                findViewById(android.R.id.content).setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
                getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE);
            }
        }*/
        // If the Android version is lower than Jellybean, use this call to hide
        // the status bar.
        if (Build.VERSION.SDK_INT <= 17) {
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                    WindowManager.LayoutParams.FLAG_FULLSCREEN);
        } else {
            View decorView = getWindow().getDecorView();
            // Hide the status bar.
            int uiOptions = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_IMMERSIVE
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
            decorView.setSystemUiVisibility(uiOptions);
        }
        //if (Build.VERSION.SDK_INT > 10 && Build.VERSION.SDK_INT < 19)
        //    findViewById(android.R.id.content).setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
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
            if (MainActivity.findCondition(version, MainActivity.getLocales(this), condition, country, language)) {
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

    private boolean isTouching(ImageView joystick, int px, int py) {
        return joystick.getLeft() <= px
                && joystick.getRight() >= px
                && joystick.getTop() <= py
                && joystick.getBottom() >= py;
    }

    private boolean isJoystickBehavior0(int axisIndex) {
        try {
            return joystickBehaviors.substring(axisIndex, axisIndex + 1).equals("0");
        } catch(Throwable e) {
            e.printStackTrace();
            return true;
        }
    }

    private String getJoystickShape(int joystickIndex) {
        return joystickShapes.substring(joystickIndex, joystickIndex + 1);
    }

    private void sendJoystickValues() {
        boolean changed = false;
        for (int i = 0; i < 8; i++)
            if (axisValue[i] != oldAxisValue[i]) {
                changed = true;
                break;
            }
        if (changed) {
            String message = "";
            Hashtable<String, Integer> parameters = new Hashtable<String, Integer>();
            for (int i = 0; i < 8; i++)
                if (axisValue[i] != oldAxisValue[i]) {
                    parameters.put(axisNames.substring(i, i + 1), axisValue[i]);
                    message += axisNames.substring(i, i + 1) + "=" + Integer.toString(axisValue[i]) + ";";
                }
            textViewDebugMessage.setText(message.length() > 0 ? message.substring(0, message.length() - 1) : message);
            RoboCamBroker.setJoystickValues(parameters);
            for (int i = 0; i < 8; i++)
                oldAxisValue[i] = axisValue[i];
        }
    }

    public static void updateJoysticks() {
        if (localControllersActivity != null)
            localControllersActivity.parentLayout.post(new Runnable() {
                @Override
                public void run() {
                    if (localControllersActivity != null) {
                        localControllersActivity._updateJoysticks();
                    }
                }
            });
    }

    private void updateJoystickShape(ImageView imageView, int joystickIndex) {
        String shape = joystickShapes.substring(joystickIndex, joystickIndex + 1);
        if (shape.equals("v"))
            imageView.setImageResource(R.drawable.joystick1);
        else if (shape.equals("c"))
            imageView.setImageResource(R.drawable.joystick2);
        else if (shape.equals("q"))
            imageView.setImageResource(R.drawable.joystick3);
        else if (shape.equals("a"))
            imageView.setImageResource(R.drawable.joystick4);
        else if (shape.equals("h"))
            imageView.setImageResource(R.drawable.joystick7);
        else if (shape.equals("t"))
            imageView.setImageResource(R.drawable.joystick5);
        else if (shape.equals("l"))
            imageView.setImageResource(R.drawable.joystick6);
        if (shape.equals("-"))
            imageView.setVisibility(View.INVISIBLE);
        else
            imageView.setVisibility(View.VISIBLE);
    }

    private void _updateJoysticks() {
        try {
            joystickShapes = RoboCamDriver.getCurrentDriver().isConnected()
                    ? RoboCamDriver.getCurrentDriver().getJoystickShapes() : "----";
            joystickBehaviors = RoboCamDriver.getCurrentDriver().isConnected()
                    ? RoboCamDriver.getCurrentDriver().getJoystickBehaviors() : "00000000";
            showDebugInfo = RoboCamDriver.getCurrentDriver().isConnected()
                    ? RoboCamDriver.getCurrentDriver().isShowDebugInfo() : true;
            textViewDebugMessage.setVisibility(showDebugInfo ? View.VISIBLE : View.INVISIBLE);
            usedKeys.clear();
            if (RoboCamDriver.getCurrentDriver().isConnected()) {
                String s = RoboCamDriver.getCurrentDriver().getUsedKeys();
                for (int i = 0; i < s.length() / 3; i++)
                    usedKeys.add(new Integer(s.substring(i * 3, i * 3 + 3)));
            }
            hideJoysticks = RoboCamDriver.getCurrentDriver().isConnected()
                    ? RoboCamDriver.getCurrentDriver().isHideJoysticks() : false;
            if (showJoysticks) {
                if (control.equals("left-handed")) {
                    imageViewJoystick1 = (ImageView)findViewById(R.id.imageViewJoystick1);
                    imageViewJoystick2 = (ImageView)findViewById(R.id.imageViewJoystick2);
                    imageViewHands.setImageResource(R.drawable.hands_r);
                } else {
                    imageViewJoystick1 = (ImageView)findViewById(R.id.imageViewJoystick2);
                    imageViewJoystick2 = (ImageView)findViewById(R.id.imageViewJoystick1);
                    imageViewHands.setImageResource(R.drawable.hands_l);
                }
                if (joystickCurrentPanel == 0 && joystickShapes.substring(0, 2).equals("--"))
                    joystickCurrentPanel = 1;
                if (joystickCurrentPanel == 1 && joystickShapes.substring(2, 4).equals("--"))
                    joystickCurrentPanel = 0;
                if (joystickCurrentPanel == 0) {
                    updateJoystickShape(imageViewJoystick1, 0);
                    updateJoystickShape(imageViewJoystick2, 1);
                } else {
                    updateJoystickShape(imageViewJoystick1, 2);
                    updateJoystickShape(imageViewJoystick2, 3);
                }
                if (joystickShapes.substring(0, 2).equals("--") || joystickShapes.substring(2, 4).equals("--"))
                    imageViewCurrentJoystick.setVisibility(View.INVISIBLE);
                else
                    imageViewCurrentJoystick.setVisibility(View.VISIBLE);
                if (joystickCurrentPanel == 0)
                    imageViewCurrentJoystick.setImageResource(R.drawable.joystick_1_2);
                else
                    imageViewCurrentJoystick.setImageResource(R.drawable.joystick_3_4);
                if (joystickShapes.equals("----"))
                    imageViewHands.setVisibility(View.INVISIBLE);
                else
                    imageViewHands.setVisibility(View.VISIBLE);
            } else {
                imageViewJoystick1.setVisibility(View.INVISIBLE);
                imageViewJoystick2.setVisibility(View.INVISIBLE);
                imageViewCurrentJoystick.setVisibility(View.INVISIBLE);
                imageViewHands.setVisibility(View.INVISIBLE);
            }
            if (!RoboCamDriver.getCurrentDriver().isConnected())
                textViewText.setText(R.string.robot_is_not_connected);
            else
                textViewText.setText("");

        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    private double getJoystickX(ImageView imageView) {
        return (imageView.getLeft() + imageView.getRight()) / 2;
    }

    private double getJoystickY(ImageView imageView) {
        return (imageView.getTop() + imageView.getBottom()) / 2;
    }

    private double getJoystickSize(ImageView imageView) {
        //image is 540px
        //joystick on the image is 450px
        return imageView.getWidth() * 450 / 540;
    }

    private void calcJoystickAxis(String shape, double[] coord) {
        if (shape.equals("v")) {
            coord[0] = 0;
            coord[1] = coord[1] < -100 ? -100 : (coord[1] > 100 ? 100 : coord[1]);
        } else if (shape.equals("h")) {
            coord[0] = coord[0] < -100 ? -100 : (coord[0] > 100 ? 100 : coord[0]);
            coord[1] = 0;
        } else if (shape.equals("c") && Math.sqrt(coord[0] * coord[0] + coord[1] * coord[1]) > 100) {
            double a = (Math.atan2(coord[0], coord[1]) - Math.PI / 2.0) * -1;
            coord[0] = Math.cos(a) * 100;
            coord[1] = Math.sin(a) * 100;
        } else if (shape.equals("q")) {
            coord[0] = coord[0] < -100 ? -100 : (coord[0] > 100 ? 100 : coord[0]);
            coord[1] = coord[1] < -100 ? -100 : (coord[1] > 100 ? 100 : coord[1]);
        } else if (shape.equals("t")) {
            coord[0] = coord[1] > -40 & coord[1] < 40 ? coord[0] >= -100 & coord[0] < -40 ? -100 : coord[0] <= 100 & coord[0] > 40 ? 100 : 0 : 0;
            coord[1] = 0;
        } else if (shape.equals("l")) {
            coord[0] = 0;
            coord[1] = coord[0] > -40 & coord[0] < 40 ? coord[1] >= -100 & coord[1] < -40 ? -100 : coord[1] <= 100 & coord[1] > 40 ? 100 : 0 : 0;
        } else if (shape.equals("a")) {
            coord[0] = coord[1] > -40 & coord[1] < 40 ? coord[0] >= -100 & coord[0] < -40 ? -100 : coord[0] <= 100 & coord[0] > 40 ? 100 : 0 : 0;
            coord[1] = coord[0] > -40 & coord[0] < 40 ? coord[1] >= -100 & coord[1] < -40 ? -100 : coord[1] <= 100 & coord[1] > 40 ? 100 : 0 : 0;
        }
        coord[0] = Math.round(coord[0]);
        coord[1] = Math.round(coord[1]);
    }

    private void releaseTouchId(int joystickNumber) {
        if (joystickNumber == 1) {
            touchId1 = -1;
            if (joystickCurrentPanel == 0) {
                if (isJoystickBehavior0(0))
                    axisValue[0] = 0; //x
                if (isJoystickBehavior0(1))
                    axisValue[1] = 0; //y
            } else {
                if (isJoystickBehavior0(4))
                    axisValue[4] = 0; //a
                if (isJoystickBehavior0(5))
                    axisValue[5] = 0; //b
            }
        } else {
            touchId2 = -1;
            if (joystickCurrentPanel == 0) {
                if (isJoystickBehavior0(2))
                    axisValue[2] = 0; //w
                if (isJoystickBehavior0(3))
                    axisValue[3] = 0; //z
            } else {
                if (isJoystickBehavior0(6))
                    axisValue[6] = 0; //c
                if (isJoystickBehavior0(7))
                    axisValue[7] = 0; //d
            }
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        try {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                case MotionEvent.ACTION_POINTER_DOWN:
                    int px = (int) event.getX(event.getActionIndex());
                    int py = (int) event.getY(event.getActionIndex());
                    if (isTouching(imageViewJoystick1, px, py)
                            && touchId1 < 0
                            && showJoysticks)
                        touchId1 = event.getPointerId(event.getActionIndex());
                    else if (isTouching(imageViewJoystick2, px, py)
                            && touchId2 < 0
                            && showJoysticks)
                        touchId2 = event.getPointerId(event.getActionIndex());
                    if (!showJoysticks) {
                        showJoysticks = true;
                        updateJoysticks();
                    }
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_POINTER_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (touchId1 == event.getPointerId(event.getActionIndex()))
                        releaseTouchId(1);
                    else if (touchId2 == event.getPointerId(event.getActionIndex()))
                        releaseTouchId(2);
                    break;
            }
            double[] coord = new double[]{0, 0};
            for (int i = 0; i < event.getPointerCount(); i++) {
                if (touchId1 >= 0 && event.getPointerId(i) == touchId1) {
                    coord[0] = ((getJoystickX(imageViewJoystick1) - event.getX(i)) * 100 / getJoystickSize(imageViewJoystick1) * 2) * -1;
                    coord[1] = (getJoystickY(imageViewJoystick1) - event.getY(i)) * 100 / getJoystickSize(imageViewJoystick1) * 2;
                    if (joystickCurrentPanel == 0 && !getJoystickShape(0).equals("-")) {
                        calcJoystickAxis(getJoystickShape(0), coord);
                        axisValue[0] = (int) coord[0]; //x
                        axisValue[1] = (int) coord[1]; //y
                    }
                    if (joystickCurrentPanel == 1 && !getJoystickShape(2).equals("-")) {
                        calcJoystickAxis(getJoystickShape(2), coord);
                        axisValue[4] = (int) coord[0]; //a
                        axisValue[5] = (int) coord[1]; //b
                    }
                }
                if (touchId2 >= 0 && event.getPointerId(i) == touchId2) {
                    coord[0] = ((getJoystickX(imageViewJoystick2) - event.getX(i)) * 100 / getJoystickSize(imageViewJoystick2) * 2) * -1;
                    coord[1] = (getJoystickY(imageViewJoystick2) - event.getY(i)) * 100 / getJoystickSize(imageViewJoystick2) * 2;
                    if (joystickCurrentPanel == 0 && !getJoystickShape(1).equals("-")) {
                        calcJoystickAxis(getJoystickShape(1), coord);
                        axisValue[2] = (int) coord[0]; //w
                        axisValue[3] = (int) coord[1]; //z
                    }
                    if (joystickCurrentPanel == 1 && !getJoystickShape(3).equals("-")) {
                        calcJoystickAxis(getJoystickShape(3), coord);
                        axisValue[6] = (int) coord[0]; //c
                        axisValue[7] = (int) coord[1]; //d
                    }
                }
            }
            sendJoystickValues();
        /*textViewDebugMessage.setText(Integer.toString(axisValue[0]) + "x" + Integer.toString(axisValue[1])
                + " " + Integer.toString(axisValue[2]) + 'x' + Integer.toString(axisValue[3])
                + " " + Float.toString(event.getX()) + 'x' + Float.toString(event.getY()));*/
        } catch (Throwable e) {
            e.printStackTrace();
        }
        return false;
    }

    @Override
    protected void onPause() {
        localControllersActivity = null;
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        localControllersActivity = this;
        updateJoysticks();
        fullScreen();
    }

    public void onHandsClick(View v) {
        if (touchId1 < 0 && touchId2 < 0 && imageViewHands.getVisibility() == View.VISIBLE) {
            if (control.equals("left-handed"))
                control = "right-handed";
            else
                control = "left-handed";
            _updateJoysticks();
            SharedPreferences settings = getSharedPreferences(ExtraKey.APP_PREFERENCE, Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = settings.edit();
            editor.putString(ExtraKey.LOCAL_CONTROL, control);
            if (Build.VERSION.SDK_INT >= 9)
                editor.apply();
            else
                editor.commit();
        }
    }

    public void onCurrentJoystickClick(View v) {
        if (touchId1 < 0 && touchId2 < 0 && imageViewCurrentJoystick.getVisibility() == View.VISIBLE) {
            if (joystickCurrentPanel == 0)
                joystickCurrentPanel = 1;
            else
                joystickCurrentPanel = 0;
            _updateJoysticks();
            SharedPreferences settings = getSharedPreferences(ExtraKey.APP_PREFERENCE, Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = settings.edit();
            editor.putInt(ExtraKey.JOYSTICK_CURRENT_PANEL, joystickCurrentPanel);
            if (Build.VERSION.SDK_INT >= 9)
                editor.apply();
            else
                editor.commit();
        }
    }

    /*private void showKeyCodes(int keyCode, int ascii) {
        String s0 = "";
        for (Integer k : usedKeys) {
            if (!s0.isEmpty())
                s0 += ", ";
            s0 += k.toString();
        }
        String s = "";
        for (Integer k : keys) {
            if (!s.isEmpty())
                s += ", ";
            s += k.toString();
        }
        textViewDebugMessage.setText(s0 + "---" + s + " keyCode = " + Integer.toString(keyCode)
                + " ascii = " + Integer.toString(ascii));
    }*/

    private void sendPressedKeys() {
        if (hideJoysticks && showJoysticks && keys.size() > 0) {
            showJoysticks = false;
            _updateJoysticks();
        }
        String s = "";
        for (Integer k : keys) {
            if (!s.isEmpty())
                s += ", ";
            s += k.toString();
        }
        textViewDebugMessage.setText(s);
        RoboCamBroker.setPressedKeys((HashSet<Integer>)keys.clone());
    }

    private int AndroidKeyCodeToASCII(int keyCode) {
        int ascii = 0;
        if (keyCode >= KeyEvent.KEYCODE_A && keyCode <= KeyEvent.KEYCODE_Z)
            ascii = keyCode - KeyEvent.KEYCODE_A + 'A';
        else if (keyCode >= KeyEvent.KEYCODE_0 && keyCode <= KeyEvent.KEYCODE_9)
            ascii = keyCode - KeyEvent.KEYCODE_0 + '0';
        else if (keyCode == KeyEvent.KEYCODE_DEL) //Backspace
            ascii = 8;
        else if (keyCode == KeyEvent.KEYCODE_SHIFT_LEFT || keyCode == KeyEvent.KEYCODE_SHIFT_RIGHT)
            ascii = 16;
        else if (keyCode == KeyEvent.KEYCODE_CTRL_LEFT || keyCode == KeyEvent.KEYCODE_CTRL_RIGHT)
            ascii = 17;
        else if (keyCode == KeyEvent.KEYCODE_ALT_LEFT || keyCode == KeyEvent.KEYCODE_ALT_RIGHT)
            ascii = 18;
        else if (keyCode == KeyEvent.KEYCODE_NUM_LOCK)
            ascii = 144;
        else if (keyCode == KeyEvent.KEYCODE_SCROLL_LOCK)
            ascii = 145;
        else if (keyCode >= KeyEvent.KEYCODE_NUMPAD_0 && keyCode <= KeyEvent.KEYCODE_NUMPAD_9)
            ascii = keyCode - KeyEvent.KEYCODE_NUMPAD_0 + 96;
        else if (keyCode == KeyEvent.KEYCODE_BREAK)
            ascii = 19;
        else if (keyCode == KeyEvent.KEYCODE_GRAVE)
            ascii = 192;
        else if (keyCode == KeyEvent.KEYCODE_MINUS)
            ascii = 189;
        else if (keyCode == KeyEvent.KEYCODE_LEFT_BRACKET)
            ascii = 219;
        else if (keyCode == KeyEvent.KEYCODE_RIGHT_BRACKET)
            ascii = 221;
        else if (keyCode == KeyEvent.KEYCODE_BACKSLASH)
            ascii = 220;
        else if (keyCode == KeyEvent.KEYCODE_SEMICOLON)
            ascii = 186;
        else if (keyCode == KeyEvent.KEYCODE_APOSTROPHE)
            ascii = 222;
        else if (keyCode == KeyEvent.KEYCODE_COMMA)
            ascii = 188;
        else if (keyCode == KeyEvent.KEYCODE_PERIOD)
            ascii = 190;
        else if (keyCode == KeyEvent.KEYCODE_SLASH)
            ascii = 191;
        else if (keyCode == KeyEvent.KEYCODE_F2)
            ascii = 113;
        else if (keyCode == KeyEvent.KEYCODE_F4)
            ascii = 115;
        else if (keyCode == KeyEvent.KEYCODE_F7)
            ascii = 118;
        else if (keyCode == KeyEvent.KEYCODE_F8)
            ascii = 119;
        else if (keyCode == KeyEvent.KEYCODE_F9)
            ascii = 120;
        else if (keyCode == KeyEvent.KEYCODE_F10)
            ascii = 121;
        else if (keyCode == KeyEvent.KEYCODE_TAB)
            ascii = 9;
        else if (keyCode == KeyEvent.KEYCODE_CLEAR)
            ascii = 12;
        else if (keyCode == KeyEvent.KEYCODE_ENTER)
            ascii = 13;
        else if (keyCode == KeyEvent.KEYCODE_CAPS_LOCK)
            ascii = 20;
        else if (keyCode == KeyEvent.KEYCODE_ESCAPE)
            ascii = 27;
        else if (keyCode == KeyEvent.KEYCODE_SPACE)
            ascii = 32;
        else if (keyCode == KeyEvent.KEYCODE_PAGE_UP)
            ascii = 33;
        else if (keyCode == KeyEvent.KEYCODE_PAGE_DOWN)
            ascii = 34;
        else if (keyCode == KeyEvent.KEYCODE_MOVE_END)
            ascii = 35;
        else if (keyCode == KeyEvent.KEYCODE_MOVE_HOME)
            ascii = 36;
        else if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT)
            ascii = 37;
        else if (keyCode == KeyEvent.KEYCODE_DPAD_UP)
            ascii = 38;
        else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT)
            ascii = 39;
        else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN)
            ascii = 40;
        else if (keyCode == KeyEvent.KEYCODE_INSERT)
            ascii = 45;
        else if (keyCode == KeyEvent.KEYCODE_FORWARD_DEL) //Del
            ascii = 46;
        else if (keyCode == KeyEvent.KEYCODE_NUMPAD_MULTIPLY)
            ascii = 106;
        else if (keyCode == KeyEvent.KEYCODE_NUMPAD_ADD)
            ascii = 107;
        else if (keyCode == KeyEvent.KEYCODE_NUMPAD_SUBTRACT)
            ascii = 109;
        else if (keyCode == KeyEvent.KEYCODE_NUMPAD_DOT)
            ascii = 110;
        return ascii;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        int ascii = AndroidKeyCodeToASCII(keyCode);
        //Log.d("RoboCam", "+ " + Integer.toString(ascii));
        if (usedKeys.contains(ascii)) {
            if (!keys.contains(ascii)) {
                keys.add(ascii);
                sendPressedKeys();

            }
        }
        //showKeyCodes(keyCode, ascii);
        //return super.onKeyDown(keyCode, event);
        if (ascii > 0)
            return true;
        else
            return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        //Sometimes onKeyUp occurs immediately after onKeyDown.
        //We will ignore that events.
        if (event.getDownTime() != event.getEventTime()) {
            int ascii = AndroidKeyCodeToASCII(keyCode);
            //Log.d("RoboCam", "- " + Integer.toString(ascii));
            if (keys.contains(ascii)) {
                keys.remove(ascii);
                sendPressedKeys();
            }
            //Log.d("RoboCam", "- " + Integer.toString(ascii) + " " + Long.toString(event.getDownTime()) + " " + Long.toString(event.getEventTime()));
            //showKeyCodes(keyCode, ascii);
            //return super.onKeyDown(keyCode, event);
            if (ascii > 0)
                return true;
            else
                return super.onKeyUp(keyCode, event);
        } else
            return super.onKeyUp(keyCode, event);
    }

}
