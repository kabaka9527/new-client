package claj;

import arc.Core;
import arc.Settings;
import arc.net.ArcNet;
import arc.net.Connection;
import arc.util.Log;
import arc.util.Threads;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
/* loaded from: Copy-Link-and-Join.jar:claj/Main.class */
public class Main {
    public static final String[] tags = {"&lc&fb[D]&fr", "&lb&fb[I]&fr", "&ly&fb[W]&fr", "&lr&fb[E]", ""};
    public static final DateTimeFormatter dateTime = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");

    public static void main(String[] args) {
        Core.settings = new Settings();
        ArcNet.errorHandler = Log::err;
        Log.logger = (level, text) -> {
            String result = Log.format("&lk&fb[" + dateTime.format(LocalDateTime.now()) + "]&fr " + tags[level.ordinal()] + " " + text + "&fr");
            System.out.println(result);
        };
        
        Distributor distributor = new Distributor();
        new Control(distributor);
        
        try {
            if (args.length == 0) {
                throw new RuntimeException("Need a port and max rooms as argument!");
            }
            
            final String port = args[0];
            Threads.daemon("Application Register", () -> {
                Threads.sleep(5000L);
                while (true) {
                    post("https://api.mindustry.top/servers/claj?port=" + port);
                    Threads.sleep(540000L);
                }
            });
            
            distributor.run(Integer.parseInt(port), Integer.parseInt(args[1]));
        } catch (Throwable error) {
            Log.err("Could not to load redirect system", error);
        }
    }

    public static String getIP(Connection connection) {
        if (connection == null) {
            return "unknown";
        }
        try {
            if (connection.getRemoteAddressTCP() != null) {
                return connection.getRemoteAddressTCP().getAddress().getHostAddress();
            }
            return "unknown";
        } catch (Exception e) {
            Log.debug("Error getting IP: @", e.getMessage());
            return "unknown";
        }
    }

    public static void post(String urlString) {
        HttpURLConnection connection = null;
        BufferedReader reader = null;
        StringBuilder response = new StringBuilder();
        
        try {
            URL url = new URL(urlString);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setDoInput(true);
            connection.setConnectTimeout(10000); // 添加连接超时
            connection.setReadTimeout(10000);    // 添加读取超时
            
            int responseCode = connection.getResponseCode();
            if (responseCode >= 200 && responseCode < 300) {
                reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), "utf-8"));
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                
                Log.info("Server registration successful: @", response.toString());
            } else {
                Log.warn("Server registration failed with code: @", responseCode);
                // 添加额外的重试逻辑
                Threads.daemon("Registration-Retry", () -> {
                    Threads.sleep(30000L); // 30秒后重试
                    post(urlString);
                });
            }
        } catch (IOException e) {
            Log.err("Error during server registration: @", e.getMessage());
            // 添加额外的重试逻辑
            Threads.daemon("Registration-Retry", () -> {
                Threads.sleep(30000L); // 30秒后重试
                post(urlString);
            });
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    Log.debug("Error closing reader: @", e.getMessage());
                }
            }
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
}
