package cn.edu.sustech.cs209.chatting.common;

import java.io.Serializable;

/**
 * A class representing a message.
 */
public class Message implements Serializable{

    private long msgID;

    private final Long timestamp;

    private final String sentBy;

    private final String sendTo;

    private final String groupName;

    private final String data;

    public Message(long msgID, Long timestamp, String sentBy, String sendTo, String groupName, String data) {
        this.msgID = msgID;
        this.timestamp = timestamp;
        this.sentBy = sentBy;
        this.sendTo = sendTo;
        this.groupName = groupName;
        this.data = data;
    }
    public Message(String sentBy, String sendTo, String groupName, String data) {
        this.msgID = -1;
        this.timestamp = System.currentTimeMillis();
        this.sentBy = sentBy;
        this.sendTo = sendTo;
        this.groupName = groupName;
        this.data = data;
    }

    public Message(String sentBy, String sendTo, String data) {
        this.msgID = -1;
        this.timestamp = System.currentTimeMillis();
        this.sentBy = sentBy;
        this.sendTo = sendTo;
        this.groupName = "Private Chat";
        this.data = data;
    }

    public Message(){
        this.msgID = -1;
        this.timestamp = System.currentTimeMillis();
        this.sentBy = "anonymous";
        this.sendTo = "anonymous";
        this.groupName = "Private Chat";
        this.data = "empty";
    }

    public Long getTimestamp() {
        return timestamp;
    }

    public String getSentBy() {
        return sentBy;
    }

    public String getSendTo() {
        return sendTo;
    }

    public String getGroupName() {
        return groupName;
    }

    public String getData() {
        return data;
    }

    public void setMsgID(long msgID) {
        this.msgID = msgID;
    }
}
