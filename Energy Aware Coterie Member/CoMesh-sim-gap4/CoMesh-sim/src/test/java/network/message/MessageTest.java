package network.message;

import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;

import org.junit.jupiter.api.Test;

import network.message.payload.MessagePayload;

public class MessageTest {
    @Test
    public void objectSerializationTest() {
        Message msg = new Message("0", 1, "8", 2, (MessageType) null, (MessagePayload) null);
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
                ObjectOutputStream oos = new ObjectOutputStream(bos)) {
            oos.writeObject(msg);
            oos.flush();
            System.out.println(bos.toByteArray().length);
        } catch (Exception ex) {
            System.out.println("Exception during byte size calculation");
            ex.printStackTrace(System.out);
        }
    }
}
