package network;

import network.message.Message;

public interface OnMsgListener {
    void onMsg(Message payload);
}