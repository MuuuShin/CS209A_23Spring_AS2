package cn.edu.sustech.cs209.chatting.common;

import java.io.Serializable;
import java.util.ArrayList;

/**
 * A class representing a chat window.
 * people number:2 for private chat, more for group chat.
 */
public class Group implements Serializable {
    private long groupID;
    private String groupName;
    private ArrayList<String> userList;
    private int userNum;
    private ArrayList<Message> msgList;

    private Long timestamp;

    public Group(long groupID, String groupName,Long timestamp) {
        this.groupID = groupID;
        this.groupName = groupName;
        this.userList = new ArrayList<>();
        this.userNum = 0;
        this.msgList = new ArrayList<>();
        this.timestamp = timestamp;
    }

    public Group(long groupID, String groupName) {
        this.groupID = groupID;
        this.groupName = groupName;
        this.userList = new ArrayList<>();
        this.userNum = 0;
        this.msgList = new ArrayList<>();
        this.timestamp = System.currentTimeMillis();
    }

    public int initAddMessage(Message msg) {
        if (msgList.contains(msg)) {
            return 0;
        }
        msgList.add(msg);
        if(!userList.contains(msg.getSentBy())){
            initAddPeople(msg.getSentBy());
        }
        if(!userList.contains(msg.getSendTo())){
            initAddPeople(msg.getSendTo());
        }
        return 1;
    }

    public int initAddPeople(String username) {
        if (userList.contains(username)) {
            return 0;
        }
        userList.add(username);
        userNum++;
        return 1;
    }

    public int addMessage(Message msg) {
        if (msgList.contains(msg)) {
            return 0;
        }
        msgList.add(msg);
        if(!userList.contains(msg.getSentBy())){
            initAddPeople(msg.getSentBy());
        }
        if(!userList.contains(msg.getSendTo())){
            initAddPeople(msg.getSendTo());
        }
        this.timestamp = System.currentTimeMillis();
        return 1;
    }

    public int addPeople(String username) {
        if (userList.contains(username)) {
            return 0;
        }
        userList.add(username);
        userNum++;
        this.timestamp = System.currentTimeMillis();
        return 1;
    }

    public long getGroupID() {
        return groupID;
    }

    public String getGroupName() {
        return groupName;
    }

    public ArrayList<String> getUserList() {
        return userList;
    }

    public Long getTimestamp() {
        return timestamp;
    }

    public int getUserNum() {
        return userNum;
    }

    public ArrayList<Message> getMsgList() {
        return msgList;
    }

    public synchronized void groupBroadcast(Message msg) {

    }

}
