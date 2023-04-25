package cn.edu.sustech.cs209.chatting.server;

import cn.edu.sustech.cs209.chatting.common.Group;
import cn.edu.sustech.cs209.chatting.common.Message;
import cn.edu.sustech.cs209.chatting.common.User;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

public class Main {
  //private static List<Group> groupList = new ArrayList<>();
  //private static List<User> userList = new ArrayList<>();
  //private static List<User> onlineUserList = new ArrayList<>();
  private static final ConcurrentHashMap<String, ServerThread> threadList = new ConcurrentHashMap<>();
  private static final ConcurrentHashMap<String, Group> groupList = new ConcurrentHashMap<>();
  private static final ConcurrentHashMap<String, User> userList = new ConcurrentHashMap<>();
  private static final List<String> onlineUserList = new ArrayList<>();
  private static final ReentrantLock onlineUserListLock = new ReentrantLock();
  private static Connection connection;

  public static void main(String[] args) {
    System.out.println("Server starting...");
    try {
      initSQL();
    } catch (Exception e) {
      System.out.println("Server failed in running");
      e.printStackTrace();
      return;
    }
    System.out.println("Server started");
    //创建服务器端管理线程
    Thread serverManager = new Thread(() -> {
      while (true) {
        Scanner scanner = new Scanner(System.in);
        String command = scanner.nextLine();
        System.out.println("Command: " + command);
        if (command.equals("exit")) {
          System.out.println("Server stopping...");
          try {
            connection.close();
            for (Map.Entry<String, ServerThread> entry : threadList.entrySet()) {
              entry.getValue().close();
            }
          } catch (SQLException e) {
            e.printStackTrace();
          }
          System.out.println("Server stopped");
          System.exit(0);
        }
        if (command.equals("socket")) {
          for (Map.Entry<String, ServerThread> entry : threadList.entrySet()) {
            System.out.println(entry.getKey() + " " + entry.getValue().getUsername() + " " + entry.getValue().getStatus());
            System.out.println("Socket: " + entry.getValue().getSocket().getInetAddress().getHostAddress() + ":" + entry.getValue().getSocket().getPort());
          }
        }
        if (command.equals("pull")) {
          for (Map.Entry<String, ServerThread> entry : threadList.entrySet()) {
            Message message = new Message("server", "server", "test");
            entry.getValue().sendMsg(message);
          }
        }
        if (command.equals("online")) {
          for (String username : onlineUserList) {
            System.out.println(username);
          }
        }
        if (command.equals("group")) {
          for (Map.Entry<String, Group> entry : groupList.entrySet()) {
            System.out.println(entry.getKey() + " " + entry.getValue().getGroupName());
          }
        }
      }
    });
    serverManager.start();
    //创建ServerSocket
    try (ServerSocket serverSocket = new ServerSocket(6869)) {
      //等待客户端连接
      while (true) {
        Socket socket = serverSocket.accept();
        System.out.println("Client connected:");
        System.out.println("Now socket number: " + (threadList.size() + 1));
        //输出客户端的IP地址
        System.out.println("Client: " + socket.getInetAddress().getHostAddress() + ":" + socket.getPort());
        //创建线程
        ServerThread thread = new ServerThread(socket);
        //启动线程
        thread.start();
      }
    } catch (IOException e) {
      System.out.println("Server failed in running");
      e.printStackTrace();
    }
  }

  private static void initSQL() {
    try {
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
          Group group = new Group(groupID, groupName, 0L);
          groupList.put(groupName, group);
        }
        //取组成员
        sql = "SELECT * FROM groupMembers";
        ResultSet resultSet2 = statement.executeQuery(sql);
        //处理
        while (resultSet2.next()) {
          String groupName = resultSet2.getString("groupName");
          String username = resultSet2.getString("username");
          groupList.get(groupName).initAddPeople(username);
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
          String groupName = resultSet0.getString("groupName");
          String data = resultSet0.getString("data");
          Message msg = new Message(msgID, timestamp, sentBy, sendTo, groupName, data);
          groupList.get(groupName).initAddMessage(msg);
        }

        resultSet.close();
        resultSet0.close();
        resultSet1.close();
        resultSet2.close();
      }
    } catch (SQLException | ClassNotFoundException e) {
      System.out.println("Start failed,Error in initSQL");
      throw new RuntimeException(e);
    }
  }


  public static User getUser(String username) {
    return userList.getOrDefault(username, null);
  }

  public static Boolean hasOnlineUser(String username) {
    return onlineUserList.contains(username);
  }

  public static List<Group> getGroupIncludingUser(String username) {
    List<Group> groups = new ArrayList<>();
    for (Map.Entry<String, Group> entry : groupList.entrySet()) {
      if (entry.getValue().hasMember(username)) {
        groups.add(entry.getValue());
      }
    }
    System.out.println("182" + groups);
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
    Message msg = new Message("server", username, "ONLINE");
    serverBroadcast(msg);
    threadList.put(username, thread);
  }

  public static void addOnlineUser(String username) {
    onlineUserListLock.lock();
    onlineUserList.add(username);
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
      String sql = "INSERT INTO messages (timestamp,sentBy,sendTo,groupName,data) VALUES ('" + msg.getTimestamp() + "','" + msg.getSentBy() + "','" + msg.getSendTo() + "','" + msg.getGroupName() + "','" + msg.getData() + "')";
      statement.executeUpdate(sql);
      sql = "SELECT * FROM messages WHERE data = '" + msg.getData() + "'";
      ResultSet resultSet = statement.executeQuery(sql);
      long msgID = resultSet.getLong("msgID");
      msg.setMsgID(msgID);
      resultSet.close();
      return msg;
    } catch (SQLException e) {
      e.printStackTrace();
    }
    return new Message();
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
      e.printStackTrace();
    }
    return null;
  }

  private synchronized static void createAndSendGroup(String groupName, String[] users) {
    try (Statement statement = connection.createStatement()) {
      String sql = "INSERT INTO groups (groupName) VALUES ('" + groupName + "')";
      statement.executeUpdate(sql);
      sql = "SELECT * FROM groups WHERE groupName = '" + groupName + "'";
      ResultSet resultSet = statement.executeQuery(sql);
      long groupID = resultSet.getLong("groupID");
      resultSet.close();
      Group group = new Group(groupID, groupName);
      for (String user : users) {
        group.initAddPeople(user);
      }
      groupList.put(groupName, group);
      for (String user : users) {
        System.out.println("user" + user);

        //Message msg = new Message("server", user, groupName, "INVITE");
        if (threadList.containsKey(user)) {
          //threadList.get(user).sendMsg(msg);
          System.out.println("send to" + user + ", group" + group);
          threadList.get(user).sendGroup(group);
        }
      }
      for (String user : users) {
        sql = "INSERT INTO groupMembers (groupName,username) VALUES ('" + groupName + "','" + user + "')";
        statement.executeUpdate(sql);
      }
    } catch (SQLException e) {
      e.printStackTrace();
    }
  }

  public synchronized static void createAndSendGroup(String[] users) {
    String groupName = "Group Chat " + (groupList.size() + 1);
    System.out.println("createAndSendGroup");
    createAndSendGroup(groupName, users);
  }

  public synchronized static void createAndSendPrivateGroup(String[] users) {
    String groupName = "Private Chat " + (groupList.size() + 1);
    System.out.println("createAndSendPrivate");
    createAndSendGroup(groupName, users);
  }
}
