package cn.edu.sustech.cs209.chatting.server;

import cn.edu.sustech.cs209.chatting.common.*;

import java.io.IOException;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.net.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

public class Main {
    private static Connection connection;
    //private static List<Group> groupList = new ArrayList<>();
    //private static List<User> userList = new ArrayList<>();
    //private static List<User> onlineUserList = new ArrayList<>();
    private static ConcurrentHashMap<String, ServerThread> threadList = new ConcurrentHashMap<>();
    private static ConcurrentHashMap<String, Group> groupList = new ConcurrentHashMap<>();
    private static ConcurrentHashMap<String, User> userList = new ConcurrentHashMap<>();
    private static List<String> onlineUserList = new ArrayList<>();

    private static ReentrantLock onlineUserListLock = new ReentrantLock();


    public static void main(String[] args) {
        initSQL();
        System.out.println("Server started");
        //1.创建ServerSocket
        try (ServerSocket serverSocket = new ServerSocket(6868)) {
            //2.等待客户端连接
            while (true) {
                Socket socket = serverSocket.accept();
                System.out.println("Client connected:");
                //输出客户端的IP地址
                System.out.println(socket.getInetAddress().getHostAddress() + "\n");
                //3.创建线程
                ServerThread thread = new ServerThread(socket);
                //4.启动线程
                thread.start();
            }
        } catch (IOException e) {
            System.out.println("Server failed in running");
            e.printStackTrace();
        }
    }

    private static void initSQL() {
        try {
            System.out.println("Starting server...");
            //注册驱动
            Class.forName("org.sqlite.JDBC");
            //获取连接
            connection = DriverManager.getConnection("jdbc:sqlite:D:\\SUSTC\\java_code\\U2B\\as2\\chatting-server\\src\\main\\resources\\content.sqlite");
            //获取执行者对象
            try (Statement statement = connection.createStatement()) {
                //取用户
                String sql = "SELECT * FROM users";
                ResultSet resultSet1 = statement.executeQuery(sql);
                //处理
                while (resultSet1.next()) {
                    long userID = resultSet1.getLong("userID");
                    String username = resultSet1.getString("username");
                    String password = resultSet1.getString("password");
                    User user = new User(userID, username, password);
                    userList.put(username, user);
                }
                //取组
                sql = "SELECT * FROM groups";
                ResultSet resultSet = statement.executeQuery(sql);
                //处理
                while (resultSet.next()) {
                    long groupID = resultSet.getLong("groupID");
                    String groupName = resultSet.getString("groupName");
                    Group group = new Group(groupID, groupName);
                    groupList.put(groupName, group);
                }
                //取消息
                sql = "SELECT * FROM messages";
                ResultSet resultSet0 = statement.executeQuery(sql);
                //处理
                while (resultSet0.next()) {
                    long msgID = resultSet0.getLong("msgID");
                    Long timestamp = resultSet0.getLong("timestamp");
                    String sentBy = resultSet0.getString("sentBy");
                    String sendTo = resultSet0.getString("sendTo");
                    String groupName = resultSet0.getString("groupID");
                    String data = resultSet0.getString("data");
                    Message msg = new Message(msgID, timestamp, sentBy, sendTo, groupName, data);
                    groupList.get(groupName).initAddMessage(msg);
                }

                resultSet.close();
                resultSet0.close();
                resultSet1.close();
            }
        } catch (SQLException | ClassNotFoundException e) {
            System.out.println("Start failed,Error in initSQL");
            throw new RuntimeException(e);
        }
    }


    public static User getUser(String username) {
        return userList.getOrDefault(username, null);
    }

    public static List<Group> getGroupIncludingUser(String username) {
        List<Group> groups = new ArrayList<>();
        for (Map.Entry<String, Group> entry : groupList.entrySet()) {
            if (entry.getKey().equals(username)) {
                groups.add(entry.getValue());
            }
        }
        return groups;
    }

    public static Group getGroup(String groupName) {
        return groupList.getOrDefault(groupName, null);
    }

    public static List<String> getOnlineUserList() {
        onlineUserListLock.lock();
        List<String> list = new ArrayList<>(onlineUserList);
        onlineUserListLock.unlock();
        return list;

    }

//    public static void addUser(User user) {
//        userList.put(user.getUsername(), user);
//    }

//    public static void addGroup(Group group) {
//        groupList.put(group.getGroupName(), group);
//    }

    public static void addThread(String username, ServerThread thread) {
        threadList.put(username, thread);
        addOnlineUser(getUser(username));
        Message msg = new Message("server", username, "ONLINE");
        serverBroadcast(msg);
    }

    private static void addOnlineUser(User user) {
        onlineUserListLock.lock();
        onlineUserList.add(user.getUsername());
        onlineUserListLock.unlock();
    }

    public static void addMessageToGroup(Message msg) {
        groupList.get(msg.getGroupName()).addMessage(msg);
        String[] users = groupList.get(msg.getGroupName()).getUserList().toArray(new String[0]);
        serverSelectedBroadcast(msg, users);
    }

    public static void removeThread(String username) {
        if (!threadList.containsKey(username))
            return;
        threadList.remove(username);
        removeOnlineUser(getUser(username));
        Message msg = new Message("server", username, "OFFLINE");
        serverBroadcast(msg);
    }

    private static void removeOnlineUser(User user) {
        onlineUserListLock.lock();
        onlineUserList.remove(user.getUsername());
        onlineUserListLock.unlock();
    }

    public synchronized static void serverBroadcast(Message msg) {
        for (Map.Entry<String, ServerThread> entry : threadList.entrySet()) {
            entry.getValue().sendMsg(msg);
        }
    }

    public synchronized static void serverSelectedBroadcast(Message msg, String[] users) {
        for (String user : users) {
            if (threadList.containsKey(user)) {
                threadList.get(user).sendMsg(msg);
            }
        }
    }

    public synchronized static Message processMessage(Message msg) {
        try (Statement statement = connection.createStatement()) {
            String sql = "INSERT INTO messages (timestamp,sentBy,sendTo,groupID,data) VALUES ('" + msg.getTimestamp() + "','" + msg.getSentBy() + "','" + msg.getSendTo() + "','" + msg.getGroupName() + "','" + msg.getData() + "')";
            statement.executeUpdate(sql);
            sql = "SELECT * FROM messages WHERE timestamp = '" + msg.getTimestamp() + "'";
            ResultSet resultSet = statement.executeQuery(sql);
            long msgID = resultSet.getLong("msgID");
            msg.setMsgID(msgID);
            resultSet.close();
            return msg;
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
        return null;
    }

    public synchronized static User createUser(String username, String password) {
        try (Statement statement = connection.createStatement()) {
            String sql = "INSERT INTO users (username,password) VALUES ('" + username + "','" + password + "')";
            statement.executeUpdate(sql);
            sql = "SELECT * FROM users WHERE username = '" + username + "'";
            ResultSet resultSet = statement.executeQuery(sql);
            long userID = resultSet.getLong("userID");
            User user = new User(userID, username, password);
            userList.put(username, user);
            resultSet.close();
            return user;
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
        return null;
    }

    private synchronized static Group createGroup(String groupName, String[] users) {
        try (Statement statement = connection.createStatement()) {
            String sql = "INSERT INTO groups (groupName) VALUES ('" + groupName + "')";
            statement.executeUpdate(sql);
            sql = "SELECT * FROM groups WHERE groupName = '" + groupName + "'";
            ResultSet resultSet = statement.executeQuery(sql);
            long groupID = resultSet.getLong("groupID");
            Group group = new Group(groupID, groupName);
            groupList.put(groupName, group);
            resultSet.close();
            for (String user : users) {
                Message msg = new Message("server", user, groupName, "INVITE");
                if (threadList.containsKey(user)) {
                    threadList.get(user).sendMsg(msg);
                }
            }
            return group;
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
        return null;
    }

    public synchronized static Group createGroup(String[] users) {
        String groupName = "Group Chat " + (groupList.size() + 1);
        return createGroup(groupName, users);
    }

    public synchronized static Group createPrivateGroup(String[] users) {
        String groupName = "Private Chat " + (groupList.size() + 1);
        return createGroup(groupName, users);
    }
}
