package network.message;

import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

import network.message.payload.MessagePayload;

public class Message implements Cloneable, Serializable {
    private static final long serialVersionUID = 6939491387675430673L;
    public String src, dst;
    public int srcSeqNo, dstTS;
    public MessageType type; // deprecating it in favor of MessagePayload specific extensions
    public MessagePayload payload;

    public Message(String src, int srcSeqNo, String dst, int dstTS, MessageType type, MessagePayload payload) {
        this.src = src;
        this.srcSeqNo = srcSeqNo; // might not need source sequence number
        this.dst = dst;
        this.dstTS = dstTS;
        this.type = type;
        this.payload = payload;
    }

    public int getByteSize() {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
                ObjectOutputStream oos = new ObjectOutputStream(bos)) {
            oos.writeObject(this.clone());
            oos.flush();
            return bos.toByteArray().length;
        } catch (Exception ex) {
            System.out.println("Exception during byte size calculation");
            ex.printStackTrace(System.out);
            return -1;
        }
    }

    public String toString() {
        return type + " msg from " + src + " to " + dst + " (arriving at ts = " + dstTS + ")";
    }
}