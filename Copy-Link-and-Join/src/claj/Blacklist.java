package claj;

import arc.struct.Seq;
import arc.util.Http;
import arc.util.Log;
import arc.util.serialization.Jval;
/* loaded from: Copy-Link-and-Join.jar:claj/Blacklist.class */
public class Blacklist {
    public static final String ACTIONS_URL = "https://api.github.com/meta";
    public static final Seq<String> ips = new Seq<>();
    public static final String IPV6_PREFIX = "2001:"; // 典型的IPv6前缀

    public static void refresh() {
        try {
            Http.get(ACTIONS_URL)
                .timeout(10000)
                .header("User-Agent", "ClajClient/1.0")
                .submit(Blacklist::handleResponse);
        } catch (Exception e) {
            Log.warn("Failed to initiate GitHub Actions IPs fetch", e);
        }
    }
    
    private static void handleResponse(Http.HttpResponse response) {
        try {
            String content = response.getResultAsString();
            if (content == null || content.isEmpty()) {
                Log.warn("Empty response from GitHub API");
                return;
            }
            
            Jval json = Jval.read(content);
            if (json == null) {
                Log.warn("Failed to parse JSON from GitHub API");
                return;
            }
            
            Jval actionsArray = json.get("actions");
            if (actionsArray == null || !actionsArray.isArray()) {
                Log.warn("No actions array in GitHub API response");
                return;
            }
            
            int addedCount = 0;
            for (Jval element : actionsArray.asArray()) {
                String ip = element.asString();
                if (ip != null && !ip.startsWith(IPV6_PREFIX)) {
                    ips.add(ip);
                    addedCount++;
                }
            }
            
            Log.info("Added @ GitHub Actions IPs to blacklist.", addedCount);
        } catch (Exception e) {
            Log.warn("Failed to parse GitHub Actions IPs", e);
        }
    }

    public static void add(String ip) {
        if (ip != null && !ip.isEmpty() && !ips.contains(ip)) {
            ips.add(ip);
            Log.debug("IP @ added to blacklist", ip);
        }
    }

    public static boolean contains(String ip) {
        return ip != null && !ip.isEmpty() && ips.contains(ip);
    }

    public static void remove(String ip) {
        if (ip != null && ips.remove(ip)) {
            Log.debug("IP @ removed from blacklist", ip);
        }
    }

    public static void clear() {
        int count = ips.size;
        ips.clear();
        Log.debug("Cleared @ IPs from blacklist", count);
    }
}
