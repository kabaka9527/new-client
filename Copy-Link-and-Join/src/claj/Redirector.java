package claj;

import arc.net.Connection;
import arc.net.DcReason;
import arc.net.NetListener;
import arc.util.Log;
/* loaded from: Copy-Link-and-Join.jar:claj/Redirector.class */
public class Redirector implements NetListener {
    public Connection host;
    public Connection client;

    public Redirector(Connection host) {
        this.host = host;
    }

    @Override // arc.net.NetListener
    public void disconnected(Connection connection, DcReason reason) {
        if (connection != null) {
            try {
                Log.debug("Redirector disconnecting: @ -> @", connection.getID(), reason);
            } catch (Exception e) {
                Log.debug("Error during redirector disconnect", e);
                return;
            }
        }
        
        closeConnection(this.host, reason);
        this.host = null;
        
        closeConnection(this.client, reason);
        this.client = null;
    }
    
    private void closeConnection(Connection conn, DcReason reason) {
        if (conn != null) {
            try {
                if (conn.isConnected()) {
                    conn.close(reason);
                }
            } catch (Exception e) {
                Log.debug("Error closing connection", e);
            }
        }
    }

    @Override // arc.net.NetListener
    public void received(Connection connection, Object object) {
        try {
            Connection receiver = connection == this.host ? this.client : this.host;
            if (receiver != null && receiver.isConnected()) {
                receiver.sendTCP(object);
            }
        } catch (Exception e) {
            Log.err("Error in redirector message handling", e);
            disconnected(connection, DcReason.error);
        }
    }
}
