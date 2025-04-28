package claj;

import arc.math.Mathf;
import arc.net.Connection;
import arc.net.DcReason;
import arc.net.FrameworkMessage;
import arc.net.NetListener;
import arc.net.Server;
import arc.struct.IntMap;
import arc.struct.Seq;
import arc.util.Log;
import arc.util.Ratekeeper;
import arc.util.Threads;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
/* loaded from: Copy-Link-and-Join.jar:claj/Distributor.class */
public class Distributor extends Server {
    public static final char[] symbols = "AaBbCcDdEeFfGgHhIiJjKkLlMmNnOoPpQqRrSsTtUuVvWwYyXxZz".toCharArray();
    public static final int TIMEOUT = 15000;
    public static final int KEEP_ALIVE = 3000;
    public static final int BUFFER_SIZE = 32768;
    public int spamLimit;
    public IntMap<Room> rooms;
    public IntMap<Redirector> redirectors;

    public Distributor() {
        super(BUFFER_SIZE, BUFFER_SIZE, new Serializer());
        this.spamLimit = 500;
        this.rooms = new IntMap<>();
        this.redirectors = new IntMap<>();
        addListener(new Listener());
    }

    public void run(int port, int maxRooms) throws IOException {
        Blacklist.refresh();
        Log.info("Distributor hosted on port @.", port);
        try {
            bind(port, port);
            setDiscoveryHandler((inetAddress, reponseHandler) -> {
                try {
                    ByteBuffer buffer = writeServerData(maxRooms);
                    int length = buffer.position();
                    buffer.position(0);
                    buffer.limit(length);
                    reponseHandler.respond(buffer);
                } catch (Exception e) {
                    Log.err("Discovery handler error", e);
                }
            });
            run();
        } catch (Exception e) {
            Log.err("Failed to start server", e);
            throw e;
        }
    }

    ByteBuffer writeServerData(int maxRooms) {
        ByteBuffer buffer = ByteBuffer.allocate(500);
        writeString(buffer, "Multiplayer worker", 100);
        writeString(buffer, "", 64);
        buffer.putInt(this.rooms.size);
        buffer.putInt(0);
        buffer.putInt(1);
        writeString(buffer, "cong");
        buffer.put((byte) 1);
        buffer.putInt(maxRooms);
        writeString(buffer, "[red]如果你看到这行字 说明你的使用方法是错误的", 100);
        writeString(buffer, "Multiplayer", 50);
        return buffer;
    }

    private void writeString(ByteBuffer buffer, String string, Integer maxlen) {
        byte[] bytes = string.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > maxlen) {
            bytes = Arrays.copyOfRange(bytes, 0, maxlen);
        }
        buffer.put((byte) bytes.length);
        buffer.put(bytes);
    }

    private void writeString(ByteBuffer buffer, String string) {
        writeString(buffer, string, 32);
    }

    public String generateLink() {
        StringBuilder builder = new StringBuilder("CLaJ");
        for (int i = 0; i < 42; i++) {
            builder.append(symbols[Mathf.random(symbols.length - 1)]);
        }
        return builder.toString();
    }

    public Room find(String link) {
        for (IntMap.Entry<Room> entry : this.rooms) {
            if (entry.value.link.equals(link)) {
                return entry.value;
            }
        }
        return null;
    }

    public Room find(Redirector redirector) {
        for (IntMap.Entry<Room> entry : this.rooms) {
            if (entry.value.redirectors.contains(redirector)) {
                return entry.value;
            }
        }
        return null;
    }

    /* loaded from: Copy-Link-and-Join.jar:claj/Distributor$Listener.class */
    public class Listener implements NetListener {
        public Listener() {
        }

        @Override // arc.net.NetListener
        public void connected(Connection connection) {
            try {
                if (Blacklist.contains(connection.getRemoteAddressTCP().getAddress().getHostAddress())) {
                    Log.debug("Connection @ rejected: blacklisted IP @", connection.getID(), Main.getIP(connection));
                    connection.close(DcReason.closed);
                    return;
                }
                connection.updateReturnTripTime();
                connection.setKeepAliveTCP(Distributor.KEEP_ALIVE);
                connection.setTimeout(Distributor.TIMEOUT);
                Threads.daemon("Connection-Keepalive-" + connection.getID(), () -> {
                    while (connection.isConnected()) {
                        try {
                            connection.sendTCP(FrameworkMessage.keepAlive);
                        } catch (Exception e) {
                            Log.debug("Failed to send keepalive to connection @: @", connection.getID(), e.getMessage());
                            break;
                        }
                        Threads.sleep(1500L);
                    }
                });
                Log.info("Connection @ received from @!", connection.getID(), Main.getIP(connection));
                connection.setArbitraryData(new Ratekeeper());
            } catch (Exception e) {
                Log.debug("Connection setup failed for @: @", connection.getID(), e.getMessage());
                try {
                    connection.close(DcReason.error);
                } catch (Exception ex) {
                    Log.debug("Failed to close connection after setup error: @", ex.getMessage());
                }
            }
        }

        @Override // arc.net.NetListener
        public void disconnected(Connection connection, DcReason reason) {
            if (reason == DcReason.error) {
                Log.debug("Connection @ disconnected with error", connection.getID());
            } else {
                Log.info("Connection @ lost: @.", connection.getID(), reason);
            }
            
            // 使用 synchronized 确保线程安全
            synchronized (Distributor.this) {
                try {
                    if (connection == null) {
                        Log.warn("Received disconnection event for null connection");
                        return;
                    }
                    
                    int connectionId = connection.getID();
                    
                    // 检查是否为房间主机
                    Room room = Distributor.this.rooms.get(connectionId);
                    if (room != null) {
                        Log.info("Closing room @ because host disconnected", room.link);
                        room.close();
                        Distributor.this.rooms.remove(connectionId);
                        return;
                    }
                    
                    // 检查是否为重定向器
                    Redirector redirector = Distributor.this.redirectors.get(connectionId);
                    if (redirector == null) {
                        return;
                    }
                    
                    // 这个连接是某个重定向器的一部分
                    Distributor.this.redirectors.remove(connectionId);
                    
                    // 处理重定向器的断开连接逻辑
                    Room redirectorRoom = Distributor.this.find(redirector);
                    if (redirectorRoom != null) {
                        // 从房间的重定向器列表中移除
                        redirectorRoom.redirectors.remove(redirector);
                        redirectorRoom.sendMessage("[lightgray]一个连接已断开");
                    }
                    
                    // 清理相关的连接映射
                    if (redirector.host != null && redirector.host != connection && 
                        redirector.host.isConnected()) {
                        try {
                            Distributor.this.redirectors.remove(redirector.host.getID());
                            redirector.host.close(DcReason.closed);
                        } catch (Exception e) {
                            Log.debug("Failed to remove host redirector mapping: @", e.getMessage());
                        }
                    }
                    
                    if (redirector.client != null && redirector.client != connection && 
                        redirector.client.isConnected()) {
                        try {
                            Distributor.this.redirectors.remove(redirector.client.getID());
                            redirector.client.close(DcReason.closed);
                        } catch (Exception e) {
                            Log.debug("Failed to remove client redirector mapping: @", e.getMessage());
                        }
                    }
                    
                    // 通知重定向器处理断开连接
                    try {
                        redirector.disconnected(connection, reason);
                    } catch (Exception e) {
                        Log.debug("Error in redirector disconnect handler: @", e.getMessage());
                    }
                } catch (Exception e) {
                    Log.err("Critical error during disconnect handling: @", e);
                }
            }
        }

        @Override // arc.net.NetListener
        public void received(Connection connection, Object object) {
            if (connection == null || object == null) {
                return;
            }
            
            try {
                if (object instanceof FrameworkMessage.KeepAlive) {
                    connection.updateReturnTripTime();
                    return;
                }
                
                // 检查连接是否已经断开
                if (!connection.isConnected()) {
                    return;
                }
                
                Ratekeeper rate = (Ratekeeper) connection.getArbitraryData();
                if (rate == null) {
                    // 防止空指针异常
                    connection.setArbitraryData(new Ratekeeper());
                    rate = (Ratekeeper) connection.getArbitraryData();
                }
                
                if (!rate.allow(3000L, Distributor.this.spamLimit)) {
                    rate.occurences = -Distributor.this.spamLimit;
                    synchronized (Distributor.this) {
                        Redirector redirector = Distributor.this.redirectors.get(connection.getID());
                        if (redirector != null && connection == redirector.host) {
                            Log.warn("Connection @ spammed with packets but not disconnected due to being a host.", connection.getID());
                            return;
                        }
                        Log.warn("Connection @ disconnected due to packet spam.", connection.getID());
                        if (redirector != null) {
                            Room room = Distributor.this.find(redirector);
                            if (room != null) {
                                room.sendMessage("[scarlet]⚠[] Connection closed due to packet spam.");
                                room.redirectors.remove(redirector);
                            }
                        }
                    }
                    connection.close(DcReason.closed);
                } else if (object instanceof FrameworkMessage) {
                    // 框架消息已处理，无需进一步操作
                } else if (object instanceof String) {
                    String link = (String) object;
                    if (link.equals("new")) {
                        synchronized (Distributor.this) {
                            String link2 = Distributor.this.generateLink();
                            connection.sendTCP(link2);
                            Distributor.this.rooms.put(connection.getID(), new Room(link2, connection));
                            Log.info("Connection @ created a room @.", connection.getID(), link2);
                        }
                    } else if (link.startsWith("host")) {
                        handleHostRequest(connection, link.substring(4));
                    } else if (link.startsWith("join")) {
                        handleJoinRequest(connection, link.substring(4));
                    } else {
                        Log.debug("Received unknown string command: @ from connection @", link, connection.getID());
                    }
                } else {
                    // 添加额外的检查，确保redirector仍然存在
                    synchronized (Distributor.this) {
                        Redirector redirector = Distributor.this.redirectors.get(connection.getID());
                        if (redirector != null) {
                            redirector.received(connection, object);
                        } else {
                            Log.debug("Received data for connection @ but no redirector found", connection.getID());
                        }
                    }
                }
            } catch (Exception e) {
                Log.err("Error processing received data from connection @: @", connection.getID(), e);
                try {
                    connection.close(DcReason.error);
                } catch (Exception ex) {
                    Log.debug("Error closing connection after exception", ex);
                }
            }
        }

        private void handleHostRequest(Connection connection, String roomLink) {
            synchronized (Distributor.this) {
                try {
                    Room room = Distributor.this.find(roomLink);
                    if (room == null) {
                        Log.debug("Host request for non-existent room: @", roomLink);
                        connection.close(DcReason.error);
                        return;
                    }
                    
                    if (!room.canAddRedirector()) {
                        Log.warn("Room @ has reached maximum redirectors limit.", room.link);
                        connection.close(DcReason.error);
                        return;
                    }
                    
                    String ip = Main.getIP(connection);
                    if (!Main.getIP(room.host).equals(ip) && room.hasConnection(ip)) {
                        Log.warn("Connection @ rejected: IP @ already joined room @", connection.getID(), ip, room.link);
                        connection.close(DcReason.error);
                        return;
                    }
                    
                    Redirector redirector = new Redirector(connection);
                    room.redirectors.add(redirector);
                    Distributor.this.redirectors.put(connection.getID(), redirector);
                    Log.info("Connection @ hosted a redirector in room @.", connection.getID(), room.link);
                } catch (Exception e) {
                    Log.err("Error processing host request: @", e);
                    connection.close(DcReason.error);
                }
            }
        }

        private void handleJoinRequest(Connection connection, String roomLink) {
            synchronized (Distributor.this) {
                try {
                    Room room = Distributor.this.find(roomLink);
                    if (room == null) {
                        Log.debug("Join request for non-existent room: @", roomLink);
                        connection.close(DcReason.error);
                        return;
                    }
                    
                    Redirector redirector = room.redirectors.find(r -> r.client == null);
                    if (redirector == null) {
                        Log.debug("No available redirector in room @", room.link);
                        connection.close(DcReason.error);
                        return;
                    }
                    
                    redirector.client = connection;
                    Distributor.this.redirectors.put(connection.getID(), redirector);
                    room.sendMessage("new");
                    Log.info("Connection @ joined to room @.", connection.getID(), room.link);
                } catch (Exception e) {
                    Log.err("Error processing join request: @", e);
                    connection.close(DcReason.error);
                }
            }
        }
    }
}
