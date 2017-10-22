package ru.proghouse.robocam;

import android.app.ActionBar;
import android.content.Intent;
import android.graphics.Point;
import android.net.Uri;
import android.os.Build;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Display;
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
import java.util.Hashtable;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import ru.proghouse.robocam.drivers.RoboCamDriver;

public class LocalControllersActivity extends AppCompatActivity {

    private static LocalControllersActivity localControllersActivity = null;
    private AdView adView = null;
    private WebView banner = null;
    private String bannerUrl = null;
    public static double screenMin = 0;
    public static int screenMinPixels = 0;
    private String bannerType = "";
    private boolean bannerShowOffline = false;
    private volatile boolean loadedAds = false;
    private ImageView imageViewLeftJoystick = null;
    private ImageView imageViewRightJoystick = null;
    private TextView textViewDebugMessage = null;
    private RelativeLayout parentLayout = null;

    private int[] axisValue = new int[] {0, 0, 0, 0, 0, 0, 0, 0};
    private int[] oldAxisValue = new int[] {0, 0, 0, 0, 0, 0, 0, 0};
    private int touchId1 = -1;
    private int touchId2 = -1;
    private boolean showJoysticks = true;
    private String axisNames = "xywzabcd";
    String joystickShapes = "----";
    private String joystickBehaviors = "00000000";
    private int joystickCurrentPanel = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_local_controllers);
        localControllersActivity = this;
        parentLayout = (RelativeLayout)findViewById(R.id.parentLayout);
        getSupportActionBar().hide();
        fullScreen();

        imageViewLeftJoystick = (ImageView)findViewById(R.id.imageViewLeftJoystick);
        imageViewRightJoystick = (ImageView)findViewById(R.id.imageViewRightJoystick);
        textViewDebugMessage = (TextView) findViewById(R.id.textViewDebugMessage);

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
        screenMinPixels = Math.min(width, height);
        screenMin = Math.min(
                (double)width / (double)displayMetrics.density,
                (double)height / (double)displayMetrics.density);

        imageViewLeftJoystick.getLayoutParams().width = (int)(screenMinPixels / 2);
        imageViewLeftJoystick.getLayoutParams().height = (int)(screenMinPixels / 2);
        imageViewRightJoystick.getLayoutParams().width = (int)(screenMinPixels / 2);
        imageViewRightJoystick.getLayoutParams().height = (int)(screenMinPixels / 2);
        textViewDebugMessage.setTextSize(TypedValue.COMPLEX_UNIT_PX, Math.round(screenMinPixels / 32));

        updateControls();

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
        // If the Android version is lower than Jellybean, use this call to hide
        // the status bar.
        if (Build.VERSION.SDK_INT < 16) {
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
                    if (localControllersActivity != null)
                        localControllersActivity._updateJoysticks();
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
            imageView.setVisibility(View.GONE);
        else
            imageView.setVisibility(View.VISIBLE);
    }

    private void _updateJoysticks() {
        try {
            joystickShapes = RoboCamDriver.getCurrentDriver().isConnected()
                    ? RoboCamDriver.getCurrentDriver().getJoystickShapes() : "----";
            if (showJoysticks) {
                if (joystickCurrentPanel == 0) {
                    updateJoystickShape(imageViewLeftJoystick, 0);
                    updateJoystickShape(imageViewRightJoystick, 1);
                } else {
                    updateJoystickShape(imageViewLeftJoystick, 2);
                    updateJoystickShape(imageViewRightJoystick, 3);
                }
            } else {
                imageViewLeftJoystick.setVisibility(View.GONE);
                imageViewRightJoystick.setVisibility(View.GONE);
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    private void updateControls() {

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
                    if (isTouching(imageViewLeftJoystick, px, py)
                            && touchId1 < 0
                            && showJoysticks)
                        touchId1 = event.getPointerId(event.getActionIndex());
                    else if (isTouching(imageViewRightJoystick, px, py)
                            && touchId2 < 0
                            && showJoysticks)
                        touchId2 = event.getPointerId(event.getActionIndex());
                    if (!showJoysticks) {
                        showJoysticks = true;
                        updateJoysticks();
                        updateControls();
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
                    coord[0] = ((getJoystickX(imageViewLeftJoystick) - event.getX(i)) * 100 / getJoystickSize(imageViewLeftJoystick) * 2) * -1;
                    coord[1] = (getJoystickY(imageViewLeftJoystick) - event.getY(i)) * 100 / getJoystickSize(imageViewLeftJoystick) * 2;
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
                    coord[0] = ((getJoystickX(imageViewRightJoystick) - event.getX(i)) * 100 / getJoystickSize(imageViewRightJoystick) * 2) * -1;
                    coord[1] = (getJoystickY(imageViewRightJoystick) - event.getY(i)) * 100 / getJoystickSize(imageViewRightJoystick) * 2;
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
    }
}
