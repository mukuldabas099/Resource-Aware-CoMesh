package network.message.payload.failure;

import network.message.payload.ReplyMessagePayload;

public class NodeFailureAckMessagePayload extends ReplyMessagePayload {
    private static final long serialVersionUID = 2467589647387006617L;

    public NodeFailureAckMessagePayload(int srcSeqNo) {
        super(srcSeqNo);
    }
}
