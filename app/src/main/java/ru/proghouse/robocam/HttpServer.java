package ru.proghouse.robocam;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.res.AssetManager;
import android.os.Build;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.webbitserver.BaseWebSocketHandler;
import org.webbitserver.WebServer;
import org.webbitserver.WebServers;
import org.webbitserver.WebSocketConnection;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.URL;
import java.net.URLDecoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Enumeration;
import java.util.GregorianCalendar;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import ru.proghouse.robocam.drivers.RoboCamDriver;

public class HttpServer extends IntentService {
    //private static HttpServlet dummyServlet = null;
    //private static VetoableChangeListener test = null;

    public static final int SERVER_IS_OFF = 0;
    public static final int SERVER_IS_WORKING = 1;
    public static final int SERVER_IS_INITIALIZING = 2;
    public static final int SERVER_IS_STOPPING = 3;
    private static int serverState = SERVER_IS_OFF;

    private volatile ServerSocket serverSocket;
    private volatile boolean terminated;
    private static Hashtable extensions = new Hashtable(); static {fillExtensions();}
    private static String separatorChar = new String(new char[]{File.separatorChar});
    private static HashSet<String> authorizedPaths = new HashSet<String>(); static {fillAuthorizedPaths();}
    private static HashSet<String> noCachePaths = new HashSet<String>(); static {fillNoCachePaths();}
    private static HashSet<String> scripts = new HashSet<String>(); static {fillScripts();}
    private static Hashtable<String, String> sessionKeys = new Hashtable<String, String>();
    private static Hashtable<String, Date> adminSessions = new Hashtable<String, Date>();
    private static Hashtable<String, Date> guestSessions = new Hashtable<String, Date>();
    private static int sessionLifetime = 30; //minutes
    private static int connectionLifetime = 4; //seconds
    public static Object sync = new Object();
    private static int httpPort = 8088;
    private static int webSocketPort = 8089;
    private volatile WebServer webServer = null;
    private volatile RoboCamWebSocketHandler webSocketHandler = null;
    //private static volatile File cacheDir = null;
    private static volatile File adsDir = null;
    private static HttpServer server = null;
    private static Hashtable<WebSocketConnection, String> currentConnections = new Hashtable<WebSocketConnection, String>();
    private static Hashtable<WebSocketConnection, String> currentConnectionTests = new Hashtable<WebSocketConnection, String>();
    private static Hashtable<WebSocketConnection, Date> currentConnectionTestTimes = new Hashtable<WebSocketConnection, Date>();
    private static Hashtable<String, Socket> movieSockets = new Hashtable<String, Socket>();
    private volatile static String adminName = "admin";
    private volatile static String adminPassword = "123";
    private volatile static String spectatorName = "guest";
    private volatile static String spectatorPassword = "123";
    private volatile static boolean allowSpectators = true;

    private static void fillExtensions()
    {
        //text
        extensions.put(".htm", "text/html; charset=UTF-8");
        extensions.put(".html", "text/html; charset=UTF-8");
        extensions.put(".text", "text/plain");
        extensions.put(".txt", "text/plain");
        extensions.put(".js", "text/javascript; charset=UTF-8");
        extensions.put(".xml", "text/xml; charset=UTF-8");
        extensions.put(".css", "text/css");
        extensions.put(".json", "application/json");

        //audio
        extensions.put(".ogg", "audio/ogg");
        extensions.put(".m4a", "audio/mp4");
        extensions.put(".wav", "audio/x-wav");

        //images
        extensions.put(".png", "image/png");
        extensions.put(".jpg", "image/jpeg");
        extensions.put(".jpeg", "image/jpeg");
        extensions.put(".gif", "image/gif");

        extensions.put(".appcache", "text/cache-manifest");

        /*extentions.put(".uu", "application/octet-stream");
        extentions.put(".exe", "application/octet-stream");
        extentions.put(".ps", "application/postscript");
        extentions.put(".zip", "application/zip");
        extentions.put(".sh", "application/x-shar");
        extentions.put(".tar", "application/x-tar");
        extentions.put(".snd", "audio/basic");
        extentions.put(".au", "audio/basic");
        extentions.put(".c", "text/plain");
        extentions.put(".cc", "text/plain");
        extentions.put(".c++", "text/plain");
        extentions.put(".h", "text/plain");
        extentions.put(".pi", "text/plain");
        extentions.put(".Java", "text/plain");*/
    }

    private static void fillAuthorizedPaths() {
        authorizedPaths.add(correctSeparators("/index.html"));
    }

    private static void fillNoCachePaths() {
        noCachePaths.add(correctSeparators("/index.html"));
    }

    private static void fillScripts()
    {
        scripts.add(correctSeparators("/login.html"));
        scripts.add(correctSeparators("/index.html"));
    }

    private static String correctSeparators(String path)
    {
        return path.replace("/", separatorChar).replace("\\", separatorChar);
    }

    public HttpServer() {
        super("RoboCamServer");
    }

    public static void start(Context context)
    {
        synchronized (HttpServer.sync) {
            serverState = SERVER_IS_INITIALIZING;
        }
        RoboCamBroker.doServerStateChange();
        context.startService(new Intent(context, HttpServer.class));
    }

    public static void stop(Context context)
    {
        context.stopService(new Intent(context, HttpServer.class));
    }

    public static void broadcastMessage(String msg){
        synchronized (HttpServer.sync) {
            if (server != null)
                server.webSocketHandler.broadcastMessage(msg);
        }
    }

    public static void updateJoysticks() {
        synchronized (HttpServer.sync) {
            if (server != null)
                server.webSocketHandler.updateJoysticks();
        }
    }

    public static void updateServerSettings(Context context, boolean forceDisconnect) {
        synchronized (HttpServer.sync) {
            SharedPreferences settings = context.getSharedPreferences(ExtraKey.APP_PREFERENCE, Context.MODE_PRIVATE);
            String oldAdminName = adminName;
            String oldAdminPassword = adminPassword;
            adminName = settings.getString(ExtraKey.DRIVER_NAME, DefaultValue.DRIVER_NAME);
            adminPassword = settings.getString(ExtraKey.DRIVER_PASSWORD, DefaultValue.DRIVER_PASSWORD);
            String oldSpectatorName = spectatorName;
            String oldSpectatorPassword = spectatorPassword;
            spectatorName = settings.getString(ExtraKey.SPECTATOR_NAME, DefaultValue.SPECTATOR_NAME);
            spectatorPassword = settings.getString(ExtraKey.SPECTATOR_PASSWORD, DefaultValue.SPECTATOR_PASSWORD);
            boolean oldAllowSpectators = allowSpectators;
            allowSpectators = settings.getBoolean(ExtraKey.ALLOW_SPECTATORS, DefaultValue.ALLOW_SPECTATORS);
            boolean useLocalControls = settings.getBoolean(ExtraKey.USE_LOCAL_CONTROLS, DefaultValue.USE_LOCAL_CONTROLS);

            List<WebSocketConnection> lostConnections = new ArrayList<WebSocketConnection>();
            List<Socket> guestSockets = new ArrayList<Socket>();
            if (forceDisconnect || useLocalControls || (oldAllowSpectators && !allowSpectators)
                    || oldSpectatorName != spectatorName
                    || oldSpectatorPassword != spectatorPassword) {
                //Searching for guest connections
                for (Enumeration<WebSocketConnection> enumerator = currentConnections.keys(); enumerator.hasMoreElements(); ) {
                    WebSocketConnection connection = enumerator.nextElement();
                    String sessionId = currentConnections.get(connection);
                    if (guestSessions.containsKey(sessionId))
                        lostConnections.add(connection);
                }
                //Searching for movie sockets
                for (Enumeration<String> enumerator = movieSockets.keys(); enumerator.hasMoreElements(); ) {
                    String sessionId = enumerator.nextElement();
                    if (guestSessions.containsKey(sessionId))
                        guestSockets.add(movieSockets.get(sessionId));
                }
                //Deleting all guest sessions
                guestSessions.clear();
            }
            if (forceDisconnect || useLocalControls || oldAdminName != adminName || oldAdminPassword != adminPassword) {
                //Searching for admin connections
                for (Enumeration<WebSocketConnection> enumerator = currentConnections.keys(); enumerator.hasMoreElements(); ) {
                    WebSocketConnection connection = enumerator.nextElement();
                    String sessionId = currentConnections.get(connection);
                    if (adminSessions.containsKey(sessionId))
                        lostConnections.add(connection);
                }
                //Searching for movie sockets
                for (Enumeration<String> enumerator = movieSockets.keys(); enumerator.hasMoreElements(); ) {
                    String sessionId = enumerator.nextElement();
                    if (adminSessions.containsKey(sessionId))
                        guestSockets.add(movieSockets.get(sessionId));
                }
                //Deleting all admin sessions
                adminSessions.clear();
                RoboCamDriver.getCurrentDriver().stop();
            }
            //Closing WebSocket connections
            if (lostConnections.size() > 0)
                for (WebSocketConnection connection : lostConnections) {
                    if (forceDisconnect)
                        sendLostConnection(connection, context.getString(R.string.error_server_has_been_turned_off));
                    else if (useLocalControls)
                        sendLostConnection(connection, context.getString(R.string.error_local_controls_have_been_activated));
                    else
                        sendLostConnection(connection, context.getString(R.string.error_security_settings_have_been_changed));
                    connection.close();
                }
            //Closing movie socket connections
            if (guestSockets.size() > 0)
                for (Socket socket : guestSockets)
                    try {
                        socket.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
        }
    }

    private static String formatDate(Date dt)
    {
        SimpleDateFormat format = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.ENGLISH);
        return format.format(dt);
    }

    public static int getHttpPort() {
        synchronized (HttpServer.sync) {
            return httpPort;
        }
    }

    private String getPackageDate() {
        String date = "";
        try {
            ApplicationInfo ai = getPackageManager().getApplicationInfo(getPackageName(), 0);
            ZipFile zf = new ZipFile(ai.sourceDir);
            try {
                ZipEntry ze = zf.getEntry("classes.dex");
                long time = ze.getTime();
                date = formatDate(new Date(time));
            }
            finally {
                zf.close();
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return date;
    }

    public static int getServerState() {
        synchronized (HttpServer.sync){
            return serverState;
        }
    }

    private static void sendLostConnection(WebSocketConnection connection,
                                           String reason) {
        connection.send("<msg><name>connectionWasBroken</name>"
                + "<reason>" + reason + "</reason>"
                + "</msg>");
    }

    class WebSocketTester implements Runnable {

        @Override
        public void run() {
            List<WebSocketConnection> lostConnections = new ArrayList<WebSocketConnection>();
            while (true) {
                synchronized (HttpServer.sync) {
                    lostConnections.clear();
                    Calendar calendar = GregorianCalendar.getInstance();
                    calendar.add(Calendar.SECOND, -connectionLifetime);
                    for (Enumeration<WebSocketConnection> enumerator = currentConnections.keys(); enumerator.hasMoreElements(); ) {
                        WebSocketConnection connection = enumerator.nextElement();
                        String sessionId = currentConnections.get(connection);
                        if (adminSessions.containsKey(sessionId)) {
                            if (!currentConnectionTestTimes.containsKey(connection))
                                currentConnectionTestTimes.put(connection, new Date());
                            Date date = currentConnectionTestTimes.get(connection);
                            if (calendar.getTime().after(date))
                                lostConnections.add(connection);
                        }
                    }
                    if (lostConnections.size() > 0)
                        for (WebSocketConnection connection : lostConnections) {
                            sendLostConnection(connection, getString(R.string.error_poor_connection_quality));
                            connection.close();
                        }
                }
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                if (server == null)
                    break;
            }
        }
    }

    class RoboCamWebSocketHandler extends BaseWebSocketHandler {

        public void broadcastMessage(String msg){
            synchronized (HttpServer.sync) {
                for (Enumeration<WebSocketConnection> enumerator = currentConnections.keys(); enumerator.hasMoreElements();) {
                    WebSocketConnection connection = enumerator.nextElement();
                    connection.send(msg);
                }
            }
        }

        public void updateJoysticks() {
            synchronized (HttpServer.sync) {
                for (Enumeration<WebSocketConnection> enumerator = currentConnections.keys(); enumerator.hasMoreElements();) {
                    WebSocketConnection connection = enumerator.nextElement();
                    String sessionId = currentConnections.get(connection);
                    boolean isAdmin = adminSessions.containsKey(sessionId);
                    if (isAdmin)
                        connection.send("<msg><name>updateJoysticks</name>"
                                + "<jb>" + (RoboCamDriver.getCurrentDriver().isConnected() ? RoboCamDriver.getCurrentDriver().getJoystickBehaviors() : "00000000") + "</jb>"
                                + "<js>" + (RoboCamDriver.getCurrentDriver().isConnected() ? RoboCamDriver.getCurrentDriver().getJoystickShapes() : "----") + "</js>"
                                + "<kb>" + (RoboCamDriver.getCurrentDriver().isConnected() ? RoboCamDriver.getCurrentDriver().getUsedKeys() : "") + "</kb>"
                                + "<hj>" + (RoboCamDriver.getCurrentDriver().isConnected()
                                        && RoboCamDriver.getCurrentDriver().isHideJoysticks() ? "1" : "0") + "</hj>"
                                + "<di>" + (RoboCamDriver.getCurrentDriver().isConnected()
                                        && RoboCamDriver.getCurrentDriver().isShowDebugInfo() ? "1" : "0") + "</di>"
                                + "</msg>");
                }
            }
        }

        public void onOpen(WebSocketConnection connection) {
            synchronized (HttpServer.sync) {
                String sessionId = "";
                String sessionKey = connection.httpRequest().queryParam("sk");
                if (sessionKeys.containsKey(sessionKey)) {
                    sessionId = sessionKeys.get(sessionKey);
                    sessionKeys.remove(sessionKey);
                }
                currentConnections.put(connection, sessionId);
            }
        }

        public void onClose(WebSocketConnection connection) {
            synchronized (HttpServer.sync) {
                String sessionId = currentConnections.get(connection);
                currentConnections.remove(connection);
                currentConnectionTests.remove(connection);
                currentConnectionTestTimes.remove(connection);
                if (adminSessions.containsKey(sessionId))
                    RoboCamDriver.getCurrentDriver().stop();
            }
        }

        public void onMessage(WebSocketConnection connection, String msg) {
            synchronized (HttpServer.sync) {
                String sessionId = currentConnections.get(connection);
                if (adminSessions.containsKey(sessionId)) {
                    String[] msgParts = msg.split(":", 2);
                    if (msgParts.length == 2 && msgParts[0].equalsIgnoreCase("tst")) {
                        if ((!currentConnectionTests.containsKey(connection))
                                || (!currentConnectionTests.get(connection).equals(msgParts[1]))) {
                            currentConnectionTests.put(connection, msgParts[1]);
                            currentConnectionTestTimes.put(connection, new Date());
                        }
                    }
                    else if (msgParts.length == 2 && msgParts[0].equalsIgnoreCase("jv")) {
                        //Joystick values are changed.
                        String[] parameterStrings = msgParts[1].split(";");
                        Hashtable<String, Integer> parameters = new Hashtable<String, Integer>();
                        for (String parameterString : parameterStrings) {
                            if (parameterString != null && !parameterString.equalsIgnoreCase("")) {
                                String[] parameterParts = parameterString.split("=");
                                if (parameterParts.length == 2) {
                                    try {
                                        int value = Integer.parseInt(parameterParts[1]);
                                        parameters.put(parameterParts[0], value);
                                    } catch (NumberFormatException e) {
                                        //
                                    }
                                }
                            }
                        }
                        if (parameters.size() > 0) {
                            RoboCamBroker.setJoystickValues(parameters);
                            //Log.d("RoboCam", "Message from joystick");
                        }
                    }
                    else if (msgParts.length == 2 && msgParts[0].equalsIgnoreCase("kp")) {
                        try {
                            HashSet<Integer> pressedKeys = new HashSet<Integer>();
                            for (int i = 0; i < msgParts[1].length() / 3; i++) {
                                int keyCode = Integer.parseInt(msgParts[1].substring(i * 3, i * 3 + 3));
                                if (keyCode > 0 && keyCode <= 255)
                                    pressedKeys.add(keyCode);
                            }
                            RoboCamBroker.setPressedKeys(pressedKeys);
                        } catch (NumberFormatException e) {
                            //
                        }
                    }
                    /*else if (msgParts.length == 2 && msgParts[0].equalsIgnoreCase("kr")) {
                        try {
                            int keyCode = Integer.parseInt(msgParts[1]);
                            RoboCamBroker.keyReleased(keyCode);
                        } catch (NumberFormatException e) {
                            //
                        }
                    }*/
                }
            }
        }
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        try {
            adsDir = Utils.getAdsDir(this);
            //cacheDir = getCacheDir();
            //intent.getStringExtra()
            webSocketHandler = new RoboCamWebSocketHandler();
            webServer = WebServers.createWebServer(webSocketPort)
                    .add("/channel", webSocketHandler);
            webServer.start();
            String packageDate = getPackageDate();
            terminated = false;
            serverSocket = new ServerSocket(httpPort);
            synchronized (HttpServer.sync) {
                server = this;
                serverState = SERVER_IS_WORKING;
            }
            new Thread(new WebSocketTester()).start();
            RoboCamBroker.doServerStateChange();
            while((!serverSocket.isClosed()) && (!terminated))
            {
                try {
                    Socket socket = serverSocket.accept();
                    new Thread(new SocketProcessor(this, socket, getAssets(), packageDate,
                            getString(R.string.local_web_path))).start();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onDestroy() {
        try {
            try {
                try {
                    synchronized (HttpServer.sync) {
                        serverState = SERVER_IS_STOPPING;
                        server = null;
                    }
                    RoboCamBroker.doServerStateChange();
                    terminated = true;
                    if (serverSocket != null)
                        serverSocket.close();
                    if (webServer != null)
                        webServer.stop();
                } finally {
                    RoboCamDriver.getCurrentDriver().stop();
                }
            } finally {
                synchronized (HttpServer.sync) {
                    serverState = SERVER_IS_OFF;
                }
                RoboCamBroker.doServerStateChange();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        super.onDestroy();
    }

    private static class SocketProcessor implements Runnable
    {
        private HttpServer server;
        private Socket socket;
        private InputStream inputStream;
        private OutputStream outputStream;
        private OutputStreamWriter writer;
        private AssetManager assetManager;
        private String packageDate;
        private static String rootPath = "web";
        private String localWebPath;
        private Integer storedByte = null;
        private Hashtable<String, String> header = new Hashtable<String, String>();
        private Hashtable<String, String> cookie = new Hashtable<String, String>();

        private String systemMessageDisplay = "none";
        private String systemMessageTitle = "";
        private String systemMessageText = "";
        private String loginWrongUsername = "";

        private SocketProcessor(HttpServer server, Socket socket, AssetManager assetManager, String packageDate,
                                String localWebPath) throws IOException {
            this.server = server;
            this.localWebPath = localWebPath;
            this.packageDate = packageDate;
            this.socket = socket;
            this.assetManager = assetManager;
            inputStream = socket.getInputStream();
            outputStream = socket.getOutputStream();
            writer = new OutputStreamWriter(outputStream, "UTF-8");
        }

        private void writeResponse(int code, String desc, String[] headers, boolean closeSocket,
                                         boolean showDesc) throws IOException {
            try {
                writer.write("HTTP/1.1 " + code + " " + desc + "\r\n");
                if (headers != null)
                    for (int i = 0; i < headers.length; i++)
                        writer.write(headers[i] + "\r\n");
                if (showDesc)
                    writer.write("Content-type: text/html\r\n\r\n" + desc);
                writer.flush();
            }
            finally {
                if (closeSocket)
                    socket.close();
            }

        }

        private boolean fileContains(String[] files, String fileName)
        {
            for (int i = 0; i < files.length; i++)
                if (files[i].toLowerCase().compareTo(fileName) == 0)
                    return true;
            return false;
        }

        private String getContentType(String fileName)
        {
            int ind = fileName.lastIndexOf('.');
            if (ind > 0) {
                String ext = fileName.substring(ind).toLowerCase();
                if (extensions.containsKey(ext))
                    return (String) extensions.get(ext);
            }
            return "content/unknown";
        }

        private int readByte() throws IOException {
            if (storedByte != null)
            {
                int value = storedByte.intValue();
                storedByte = null;
                return value;
            }
            return inputStream.read();
        }

        private String readLine() throws IOException {
            ArrayList<Byte> buf = new ArrayList<Byte>();
            while(true){
                int b = readByte();
                if (b < 0)
                    break;
                if (b == '\r' || b == '\n') {
                    //There is we have to check next char.
                    int b2 = readByte();
                    if (b2 < 0)
                        break;
                    if ((b == '\r' && b2 == '\n') || (b == '\n' && b2 == '\r'))
                        break;
                    storedByte = new Integer(b2);
                    break;
                }
                buf.add(new Byte((byte)b));
            }
            byte[] byteData = new byte[buf.size()];
            for (int i = 0; i < buf.size(); i++)
                byteData[i] = buf.get(i).byteValue();
            return new String(byteData, "UTF-8");
        }

        private Hashtable<String, String> parseParameters(String postData) throws UnsupportedEncodingException {
            Hashtable<String, String> params = new Hashtable<String, String>();
            String[] paramData = postData.split("&");
            for (int i = 0; i < paramData.length; i++){
                String parts[] = paramData[i].split("=", 2);
                if (parts.length > 0)
                    params.put(parts[0], parts.length > 1 ? URLDecoder.decode(parts[1], "UTF-8") : "");
            }
            return params;
        }

        private void checkSessions(Hashtable<String, Date> sessions)
        {
            synchronized(sync) {
                Calendar calendar = GregorianCalendar.getInstance();
                calendar.add(Calendar.MINUTE, -sessionLifetime);
                List<String> oldSessions = new ArrayList<String>();
                Enumeration<String> enumeration = sessions.keys();
                while (enumeration.hasMoreElements()) {
                    String sessionId = enumeration.nextElement();
                    if (sessions.get(sessionId).before(calendar.getTime()))
                        oldSessions.add(sessionId);
                }
                for (int i = 0; i < oldSessions.size(); i++)
                    sessions.remove(oldSessions.get(i));
            }
        }

        private void parseCookie() {
            if (header.containsKey("Cookie:"))
            {
                String[] params = header.get("Cookie:").split(";");
                for (int i = 0; i < params.length; i++)
                {
                    String[] param = params[i].split("=", 2);
                    cookie.put(param[0], param.length > 1 ? param[1] : "");
                }
            }
        }

        private String getSessionId(String adminSessionId, String guestSessionId) {
            if (adminSessionId != null)
                return adminSessionId;
            else if (guestSessionId != null)
                return guestSessionId;
            return "";
        }

        private String processScript(String script, String adminSessionId, String guestSessionId)
        {
            if (systemMessageText != null && systemMessageText.compareTo("") != 0
                    && systemMessageTitle != null && systemMessageTitle.compareTo("") != 0)
                systemMessageDisplay = "block";
            script = script.replace("$$systemMessageDisplay$$", systemMessageDisplay);
            script = script.replace("$$systemMessageText$$", systemMessageText);
            script = script.replace("$$systemMessageTitle$$", systemMessageTitle);
            script = script.replace("$$loginWrongUsername$$", loginWrongUsername);
            script = script.replace("<html manifest=\"offline.appcache\">", "<html>"); //removing appcache
            return script;
        }

        private void showMovie(String sessionId) {
            try {
                CameraManager cameraManager = CameraManager.getCameraManager();
                String boundary = "This is the frame!";
                writeResponse(HttpURLConnection.HTTP_OK, "OK", new String[]{
                                "Server: RoboCam Server",
                                "Connection: close",
                                "Max-Age: 0",
                                "Expires: 0",
                                "Cache-Control: no-store, no-cache, must-revalidate, pre-check=0, post-check=0, max-age=0",
                                "Pragma: no-cache",
                                "Content-Type: multipart/x-mixed-replace; boundary=" + boundary
                        },
                        false, false);
                writer.write("\r\n");
                writer.write("--" + boundary + "\r\n");
                writer.flush();
                movieSockets.put(sessionId, socket);
                try {
                    cameraManager.addClient();
                    try {
                        int id = 0;
                        while (true) {
                            int newId = cameraManager.writeJpg(outputStream, boundary, id);
                            try {
                                if (server.terminated || socket.isClosed()) {
                                    if (newId != id && newId != 0)
                                        outputStream.write(("--" + boundary + "--\r\n").getBytes());
                                    break;
                                } else if (newId != id && newId != 0)
                                    outputStream.write(("--" + boundary + "\r\n").getBytes());
                                else if (!cameraManager.isPreviewing())
                                    Thread.sleep(100);
                            } finally {
                                id = newId;
                            }


                            /*boolean result = cameraManager.writeJpg(outputStream, boundary);
                            if (server.terminated || socket.isClosed()) {
                                if (result)
                                    outputStream.write(("--" + boundary + "--\r\n").getBytes());
                                break;
                            } else if (result)
                                outputStream.write(("--" + boundary + "\r\n").getBytes());
                            else if (!cameraManager.isPreviewing())
                                 Thread.sleep(100);*/
                        }
                    } finally {
                        cameraManager.removeClient();
                    }
                } finally {
                    movieSockets.remove(sessionId);
                }
                socket.close();
            } catch(Throwable e){
                e.printStackTrace();
            }
        }

        private void sendSettings(String adminSessionId, String guestSessionId, URI uri) throws IOException {
            boolean isAdmin = adminSessionId != null;
            CameraManager cameraManager = CameraManager.getCameraManager();
            String sessionKey = UUID.randomUUID().toString();
            int screenMin = 0;
            String bannerPath = "", bannerUrl = "", bannerType = "";
            int bannerWidth = 0, bannerHeight = 0;
            File versionDir = null;
            String bannerVersion = "";
            try {
                String[] params = uri.getQuery().split("&");
                for (String param : params) {
                    String[] parts = param.split("=", 2);
                    if (parts[0].equals("sm")) {
                        screenMin = (int) Integer.parseInt(parts[1]);
                        break;
                    }
                }
                if (screenMin > 0) {
                    //File adsDir = new File(cacheDir, DefaultValue.ADS_DIRECTORY);
                    File versionFile = new File(adsDir, "v.xml");
                    if (versionFile.exists()) {
                        Element[] condition = {null};
                        String[] country = {null};
                        String[] language = {null};
                        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
                        DocumentBuilder db = dbf.newDocumentBuilder();
                        Document version = db.parse(versionFile);
                        if (MainActivity.findCondition(version, MainActivity.getLocales(server), condition, country, language)) {
                            bannerVersion = version.getDocumentElement().getAttribute("number");
                            versionDir = new File(adsDir, bannerVersion);
                            for (int i = 0; i < condition[0].getChildNodes().getLength(); i++) {
                                if (condition[0].getChildNodes().item(i).getNodeName().equals("device")) {
                                    Element device = (Element) condition[0].getChildNodes().item(i);
                                    if (    (device.getAttribute("target") == null
                                                || device.getAttribute("target").isEmpty()
                                                || device.getAttribute("target").equals("client"))
                                        &&
                                            (device.getAttribute("screenMin") == null
                                                || device.getAttribute("screenMin").isEmpty()
                                                || screenMin >= Double.parseDouble(device.getAttribute("screenMin")))
                                        &&
                                            (device.getAttribute("sdkMin") == null
                                                || device.getAttribute("sdkMin").isEmpty()
                                                || Build.VERSION.SDK_INT >= Integer.parseInt(device.getAttribute("sdkMin")))) {
                                        bannerPath = device.getAttribute("path");
                                        bannerUrl = device.getAttribute("url");
                                        bannerWidth = Integer.parseInt(device.getAttribute("width"));
                                        bannerHeight = Integer.parseInt(device.getAttribute("height"));
                                        bannerType = device.getAttribute("type");
                                        break;
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            String settings;
            synchronized (HttpServer.sync) {
                sessionKeys.put(sessionKey, adminSessionId != null ? adminSessionId : guestSessionId);
                settings = "<settings>"
                        + "<cp>" + webSocketPort + "</cp>"
                        + "<prw>" + cameraManager.getActualPreviewWidth() + "</prw>"
                        + "<prh>" + cameraManager.getActualPreviewHeight() + "</prh>"
                        + "<jb>" + (isAdmin && RoboCamDriver.getCurrentDriver().isConnected() ? RoboCamDriver.getCurrentDriver().getJoystickBehaviors() : "00000000") + "</jb>"
                        + "<js>" + (isAdmin && RoboCamDriver.getCurrentDriver().isConnected() ? RoboCamDriver.getCurrentDriver().getJoystickShapes() : "----") + "</js>"
                        + "<kb>" + (isAdmin && RoboCamDriver.getCurrentDriver().isConnected() ? RoboCamDriver.getCurrentDriver().getUsedKeys() : "") + "</kb>"
                        + "<hj>" + (isAdmin && RoboCamDriver.getCurrentDriver().isConnected()
                            && RoboCamDriver.getCurrentDriver().isHideJoysticks() ? "1" : "0") + "</hj>"
                        + "<di>" + (isAdmin && RoboCamDriver.getCurrentDriver().isConnected()
                            && RoboCamDriver.getCurrentDriver().isShowDebugInfo() ? "1" : "0") + "</di>"
                        + "<lng>" + server.getString(R.string.local_web_path) + "</lng>"
                        + "<sk>" + sessionKey + "</sk>";
                if ((!bannerPath.equals("")) && bannerWidth > 0 && bannerHeight > 0
                        && (bannerType.equals("replace") || (bannerType.equals("offline")))) {
                    File index = new File(versionDir, bannerPath);
                    if (index.exists()) {
                        settings += "<bnp>/" + bannerVersion + "/" + bannerPath + "</bnp>"
                                + "<bnw>" + Integer.toString(bannerWidth) + "</bnw>"
                                + "<bnh>" + Integer.toString(bannerHeight) + "</bnh>"
                                + "<bnu>" + bannerUrl + "</bnu>";
                    }
                }
                settings +=
                        "</settings>";
            }
            byte[] bytes = settings.getBytes("UTF-8");
            writeResponse(HttpURLConnection.HTTP_OK, "OK", new String[]{
                    "Server: RoboCam Server",
                    "Content-type: text/xml; charset=UTF-8",
                    "Content-Length: " + bytes.length,
                    "Date: " + formatDate(new Date()),
                    "Cache-Control: no-store, no-cache, must-revalidate",
                    "Pragma: no-cache",
                    "Access-Control-Allow-Origin: *"
            }, false, false);
            writer.write("\r\n");
            writer.write(settings);
            writer.flush();
            socket.close();
        }

        @Override
        public void run()
        {
            try
            {
                String s = readLine();
                if (s == null || s.trim().length() == 0) {
                    writeResponse(HttpURLConnection.HTTP_BAD_METHOD, "empty method type", new String[]{"Allow: GET, HEAD, POST"},
                            true, true);
                    return;
                }
                String[] method = s.split(" ", 3);
                if (method.length < 2) {
                    writeResponse(HttpURLConnection.HTTP_BAD_METHOD, "unknown method type",
                            new String[]{"Allow: GET, HEAD, POST"}, true, true);
                    return;
                }
                if (method[0].compareTo("GET") != 0 && method[0].compareTo("HEAD") != 0
                        && method[0].compareTo("POST") != 0) {
                    writeResponse(HttpURLConnection.HTTP_BAD_METHOD, "unsupported method type: " + method[0],
                            new String[]{"Allow: GET, HEAD, POST"}, true, true);
                    return;
                }
                while(true) {
                    s = readLine();
                    String[] headerParts = s.split(" ", 2);
                    if(s == null || s.trim().length() == 0)
                        break;
                    header.put(headerParts[0],
                            headerParts.length < 2 || headerParts[1] == null ? "" : headerParts[1]);
                }
                String adminSessionId = null;
                String guestSessionId = null;
                parseCookie();
                if (cookie.containsKey("sess")) {
                    checkSessions(adminSessions);
                    checkSessions(guestSessions);
                    synchronized(sync) {
                        if (adminSessions.containsKey(cookie.get("sess"))) {
                            adminSessionId = cookie.get("sess");
                            adminSessions.put(adminSessionId, new Date());
                        } else if (guestSessions.containsKey(cookie.get("sess"))) {
                            guestSessionId = cookie.get("sess");
                            guestSessions.put(guestSessionId, new Date());
                        }
                    }
                }
                if (RoboCamBroker.DEBUG_HTTP) {
                    if (adminSessionId == null) {
                        adminSessionId = UUID.randomUUID().toString();
                        adminSessions.put(adminSessionId, new Date());
                    }
                }
                int postSize = 0;
                byte[] postBuf = null;
                if (method[0].compareTo("POST") == 0) {
                    postSize = header.containsKey("Content-Length:")
                            ? Integer.parseInt(header.get("Content-Length:")) : 0;
                    if (postSize > 0){
                        postBuf = new byte[postSize];
                        postSize = inputStream.read(postBuf);
                    }
                    if (postSize > 0 && method[1].compareTo("/login.html") == 0)
                    {
                        Hashtable<String, String> params = parseParameters(new String(postBuf, 0, postSize, "UTF-8"));
                        if (params.get("username").compareTo(adminName) == 0
                                && params.get("password").compareTo(adminPassword) == 0) {
                            adminSessionId = UUID.randomUUID().toString();
                            synchronized(sync) {
                                adminSessions.put(adminSessionId, new Date());
                            }
                            Calendar calendar = GregorianCalendar.getInstance();
                            calendar.setTime(new Date());
                            calendar.add(Calendar.YEAR, 1);
                            writeResponse(HttpURLConnection.HTTP_MOVED_TEMP, "moved temporarily",
                                    new String[]{"Location: /",
                                            "Set-Cookie: sess=" + adminSessionId + "; expires=" +
                                                    formatDate(calendar.getTime()) + "; path=/"}, true, false);
                            return;
                        }
                        else if (allowSpectators
                                && params.get("username").compareTo(spectatorName) == 0
                                && params.get("password").compareTo(spectatorPassword) == 0) {
                            guestSessionId = UUID.randomUUID().toString();
                            synchronized (sync) {
                                guestSessions.put(guestSessionId, new Date());
                            }
                            Calendar calendar = GregorianCalendar.getInstance();
                            calendar.setTime(new Date());
                            calendar.add(Calendar.YEAR, 1);
                            writeResponse(HttpURLConnection.HTTP_MOVED_TEMP, "moved temporarily",
                                    new String[]{"Location: /",
                                            "Set-Cookie: sess=" + guestSessionId + "; expires=" +
                                                    formatDate(calendar.getTime()) + "; path=/"}, true, false);
                            return;
                        }
                        else
                        {
                            if (params.get("username").compareTo("") == 0)
                                systemMessageText = server.getString(R.string.enter_your_username);
                            else
                                systemMessageText = server.getString(R.string.username_and_password_are_wrong);
                            systemMessageTitle = server.getString(R.string.error);
                            loginWrongUsername = params.get("username");
                        }
                    }
                }
                method[1] = correctSeparators(method[1]);
                //Path comes raw from the request line; any parent reference turns the file
                //serving below into a read of arbitrary files available to the app user id.
                if (method[1].contains("..")
                        || method[1].substring(0, 1).compareTo(separatorChar) != 0){
                    writeResponse(HttpURLConnection.HTTP_NOT_FOUND, "path not found", null, true, true);
                    return;
                }
                URI uri = new URI(method[1]);
                if (method[1].compareTo(separatorChar) == 0) {
                    String[] files = assetManager.list(rootPath + separatorChar + localWebPath);
                    if (fileContains(files, "index.html"))
                        method[1] = separatorChar + "index.html";
                    else if (fileContains(files, "index.htm"))
                        method[1] = separatorChar + "index.htm";
                    else if (localWebPath.compareTo("def") != 0){
                        files = assetManager.list(rootPath + separatorChar + "def");
                        if (fileContains(files, "index.html"))
                            method[1] = separatorChar + "index.html";
                        else
                            method[1] = separatorChar + "index.htm";
                    }
                }
                if (adminSessionId == null && guestSessionId == null) {
                    if (authorizedPaths.contains(method[1])) {
                        writeResponse(HttpURLConnection.HTTP_MOVED_TEMP, "moved temporarily",
                                new String[]{"Location: /login.html"}, true, false);
                        return;
                    }
                }
                boolean noCache = false;
                if (noCachePaths.contains(method[1]))
                    noCache = true;
                try {
                    if (method[1].compareTo("/cam") == 0) {
                        showMovie(adminSessionId != null ? adminSessionId : guestSessionId);
                        return;
                    } else if (uri.getPath().equals("/settings")) {
                        sendSettings(adminSessionId, guestSessionId, uri);
                        return;
                    }
                } catch(IOException e) {
                    writeResponse(HttpURLConnection.HTTP_INTERNAL_ERROR, e.getMessage(), null, true, true);
                    return;
                }
                boolean isScript = scripts.contains(method[1]);
                InputStream assetStream;
                try {
                    assetStream = assetManager.open(rootPath + separatorChar + localWebPath + method[1]);
                    method[1] = rootPath + separatorChar + localWebPath + method[1];
                }
                catch(FileNotFoundException e) {
                    try {
                        assetStream = assetManager.open(rootPath + separatorChar + "def" + method[1]);
                        method[1] = rootPath + separatorChar + "def" + method[1];
                    }
                    catch(FileNotFoundException e1) {
                        try {
                            //File cacheDir = server.getCacheDir();
                            //File adsDir = new File(cacheDir, DefaultValue.ADS_DIRECTORY);
                            File adsDir = Utils.getAdsDir(server);
                            File file = new File(adsDir, method[1]);
                            assetStream = new FileInputStream(file);
                            method[1] = file.getName();
                        }
                        catch(FileNotFoundException e2) {
                                writeResponse(HttpURLConnection.HTTP_NOT_FOUND, "file not found", null, true, true);
                                return;
                        }
                    }
                }
                catch(IOException e) {
                    writeResponse(HttpURLConnection.HTTP_INTERNAL_ERROR, e.getMessage(), null, true, true);
                    return;
                }
                try {
                    byte[] buf = null;
                    int n;
                    if (isScript)
                    {
                        buf = new byte[assetStream.available()];
                        n = assetStream.read(buf, 0, buf.length);
                        String script = new String(buf, 0, n, "UTF-8");
                        script = processScript(script, adminSessionId, guestSessionId);
                        buf = script.getBytes("UTF-8");
                    }
                    if (noCache)
                        writeResponse(HttpURLConnection.HTTP_OK, "OK", new String[]{
                                        "Server: RoboCam Server",
                                        "Content-length: " + (isScript ? buf.length : assetStream.available()),
                                        "Content-type: " + getContentType(method[1]),
                                        "Max-Age: 0",
                                        "Expires: 0",
                                        "Cache-Control: no-store, no-cache, must-revalidate, pre-check=0, post-check=0, max-age=0",
                                        "Pragma: no-cache"
                                },
                                false, false);
                    else
                        writeResponse(HttpURLConnection.HTTP_OK, "OK", new String[]{
                                        "Server: RoboCam Server",
                                        "Date: " + formatDate(new Date()),
                                        "Content-length: " + (isScript ? buf.length : assetStream.available()),
                                        "Last Modified: " + packageDate,
                                        "Content-type: " + getContentType(method[1])
                                },
                                false, false);
                    if (method[0].compareTo("GET") == 0 || method[0].compareTo("POST") == 0)
                        writer.write("\r\n");
                    writer.flush();
                    if (method[0].compareTo("GET") == 0 || method[0].compareTo("POST") == 0) {
                        if (isScript)
                            outputStream.write(buf, 0, buf.length);
                        else
                        {
                            buf = new byte[2048];
                            while ((n = assetStream.read(buf)) >= 0)
                                outputStream.write(buf, 0, n);
                        }
                        outputStream.flush();
                    }
                    socket.close();
                } finally {
                    assetStream.close();
                }
            }
            catch (Throwable e)
            {
                e.printStackTrace();
            }
        }
    }

}
