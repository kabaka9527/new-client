package claj;

import arc.net.FrameworkMessage;
import arc.net.NetSerializer;
import arc.util.io.ByteBufferInput;
import arc.util.io.ByteBufferOutput;
import arc.util.io.Reads;
import arc.util.io.Writes;
import java.nio.ByteBuffer;
/* loaded from: Copy-Link-and-Join.jar:claj/Serializer.class */
public class Serializer implements NetSerializer {
    public static final int BUFFER_SIZE = 32768;
    public ByteBuffer last = ByteBuffer.allocate(BUFFER_SIZE);

    @Override // arc.net.NetSerializer
    public void write(ByteBuffer buffer, Object object) {
        if (object instanceof ByteBuffer) {
            ByteBuffer raw = (ByteBuffer) object;
            if (raw.remaining() > buffer.remaining()) {
                throw new RuntimeException("Buffer overflow! Data size: " + raw.remaining() + ", Buffer remaining: " + buffer.remaining());
            }
            buffer.put(raw);
        } else if (object instanceof FrameworkMessage) {
            FrameworkMessage message = (FrameworkMessage) object;
            buffer.put((byte) -2);
            writeFramework(buffer, message);
        } else if (object instanceof String) {
            String link = (String) object;
            buffer.put((byte) -3);
            Writes.get(new ByteBufferOutput(buffer)).str(link);
        }
    }

    @Override // arc.net.NetSerializer
    public Object read(ByteBuffer buffer) {
        int lastPosition = buffer.position();
        byte id = buffer.get();
        
        if (id == -2) {
            return readFramework(buffer);
        }
        if (id == -3) {
            return Reads.get(new ByteBufferInput(buffer)).str();
        }
        
        // 重置缓冲区位置到读取ID之前
        buffer.position(lastPosition);
        
        this.last.clear();
        if (buffer.remaining() > this.last.capacity()) {
            throw new RuntimeException("Buffer overflow! Data size: " + buffer.remaining() + ", Buffer capacity: " + this.last.capacity());
        }
        
        // 将数据从buffer复制到last
        this.last.put(buffer);
        this.last.flip(); // 准备读取
        
        return this.last;
    }

    public void writeFramework(ByteBuffer buffer, FrameworkMessage message) {
        if (message instanceof FrameworkMessage.Ping) {
            FrameworkMessage.Ping ping = (FrameworkMessage.Ping) message;
            buffer.put((byte) 0)
                  .putInt(ping.id)
                  .put(ping.isReply ? (byte) 1 : (byte) 0);
        } else if (message instanceof FrameworkMessage.DiscoverHost) {
            buffer.put((byte) 1);
        } else if (message instanceof FrameworkMessage.KeepAlive) {
            buffer.put((byte) 2);
        } else if (message instanceof FrameworkMessage.RegisterUDP) {
            FrameworkMessage.RegisterUDP p = (FrameworkMessage.RegisterUDP) message;
            buffer.put((byte) 3).putInt(p.connectionID);
        } else if (message instanceof FrameworkMessage.RegisterTCP) {
            FrameworkMessage.RegisterTCP p = (FrameworkMessage.RegisterTCP) message;
            buffer.put((byte) 4).putInt(p.connectionID);
        }
    }

    public FrameworkMessage readFramework(final ByteBuffer buffer) {
        byte id = buffer.get();
        
        switch (id) {
            case 0: {
                FrameworkMessage.Ping ping = new FrameworkMessage.Ping();
                ping.id = buffer.getInt();
                ping.isReply = buffer.get() == 1;
                return ping;
            }
            case 1:
                return FrameworkMessage.discoverHost;
            case 2:
                return FrameworkMessage.keepAlive;
            case 3: {
                FrameworkMessage.RegisterUDP registerUDP = new FrameworkMessage.RegisterUDP();
                registerUDP.connectionID = buffer.getInt();
                return registerUDP;
            }
            case 4: {
                FrameworkMessage.RegisterTCP registerTCP = new FrameworkMessage.RegisterTCP();
                registerTCP.connectionID = buffer.getInt();
                return registerTCP;
            }
            default:
                throw new RuntimeException("Unknown framework message ID: " + id);
        }
    }
}
