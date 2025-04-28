package claj;

import arc.Core;
import arc.net.Connection;
import arc.net.DcReason;
import arc.struct.Seq;
import arc.util.Log;
import java.util.Iterator;
/* loaded from: Copy-Link-and-Join.jar:claj/Room.class */
public class Room {
    public static final int MAX_REDIRECTORS = 2;
    public String link;
    public Connection host;
    public Seq<Redirector> redirectors = new Seq<>();
    private boolean closed = false;

    public Room(String link, Connection host) {
        this.link = link;
        this.host = host;
        sendMessage("new");
        
        String text = Core.settings.getString("text", "");
        if (!text.isEmpty()) {
            String[] messages = text.split("\n");
            for (String message : messages) {
                if (message != null && !message.isEmpty()) {
                    sendMessage(message);
                }
            }
        }
        
        Log.info("Room @ created!", link);
    }

    public synchronized void close() {
        if (closed) {
            Log.debug("Attempted to close already closed room @", this.link);
            return;
        }
        
        closed = true;
        
        try {
            // 创建redirectors的副本以避免并发修改问题
            Seq<Redirector> toClose = new Seq<>(this.redirectors);
            this.redirectors.clear();
            
            // 分别处理每个redirector
            for (Redirector r : toClose) {
                if (r == null) continue;
                
                // 关闭客户端连接
                if (r.client != null) {
                    try {
                        if (r.client.isConnected()) {
                            r.client.close(DcReason.closed);
                        }
                    } catch (Exception e) {
                        Log.debug("Error closing client connection in room @", this.link, e);
                    } finally {
                        r.client = null;
                    }
                }
                
                // 关闭主机连接
                if (r.host != null) {
                    try {
                        if (r.host.isConnected()) {
                            r.host.close(DcReason.closed);
                        }
                    } catch (Exception e) {
                        Log.debug("Error closing host connection in room @", this.link, e);
                    } finally {
                        r.host = null;
                    }
                }
            }
            
            // 关闭房间主机连接
            if (this.host != null) {
                try {
                    if (this.host.isConnected()) {
                        this.host.close(DcReason.closed);
                    }
                } catch (Exception e) {
                    Log.debug("Error closing room host connection for room @", this.link, e);
                } finally {
                    this.host = null;
                }
            }
            
            Log.info("Room @ closed.", this.link);
        } catch (Exception e) {
            Log.err("Error during room @ closure: @", this.link, e);
        }
    }

    public synchronized void sendMessage(String message) {
        if (message == null || closed) {
            return;
        }
        
        try {
            // 检查主机连接是否有效
            if (this.host != null && this.host.isConnected()) {
                this.host.sendTCP(message);
            }
        } catch (Exception e) {
            Log.err("Error sending message in room @: @", this.link, e.getMessage());
        }
    }

    public synchronized boolean canAddRedirector() {
        return !closed && this.redirectors.size < MAX_REDIRECTORS;
    }

    public synchronized boolean hasConnection(String ip) {
        if (ip == null || closed) {
            return false;
        }
        
        for (Redirector r : this.redirectors) {
            if (r != null && r.client != null && ip.equals(Main.getIP(r.client))) {
                return true;
            }
        }
        return false;
    }

    public synchronized boolean isClosed() {
        return closed;
    }
}
