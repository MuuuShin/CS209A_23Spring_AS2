package cn.edu.sustech.cs209.chatting.server;

import cn.edu.sustech.cs209.chatting.common.Group;
import cn.edu.sustech.cs209.chatting.common.Message;
import cn.edu.sustech.cs209.chatting.common.SerFile;
import cn.edu.sustech.cs209.chatting.common.User;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

import static cn.edu.sustech.cs209.chatting.server.Main.*;

public class ServerThread implements Runnable {

  private static Map<String, SerFile> fileMap = new HashMap<>();
  private final Socket socket;
  private final ReentrantLock sendMsgLock = new ReentrantLock();
  private String status;
  private String username;
  private ObjectInputStream in;
  private ObjectOutputStream out;
  private Long lastHeartbeatTime;

  public ServerThread(Socket socket) {
    this.socket = socket;
    this.status = "logout";
    this.username = "anonymous";
  }

  public Socket getSocket() {
    return socket;
  }

  public String getStatus() {
    return status;
  }

  public String getUsername() {
    return username;
  }

  @Override
  public void run() {
    try {
      out = new ObjectOutputStream(socket.getOutputStream());
      in = new ObjectInputStream(socket.getInputStream());
//            in = new Scanner(inputStream);
//            out = new PrintWriter(outputStream, true);
    } catch (Exception e) {
      e.printStackTrace();
      return;
    }

    lastHeartbeatTime = System.currentTimeMillis();
    //心跳包
    new Thread(() -> {
      //客户端已经断开连接
      do {
        try {
          Thread.sleep(500);
        } catch (InterruptedException e) {
          e.printStackTrace();
        }
      } while (System.currentTimeMillis() - lastHeartbeatTime <= 4000);
      close();

    }).start();

    //登录循环
    while (true) {
      Object msg = null;
      try {
        msg = in.readObject();
      } catch (IOException | ClassNotFoundException e) {
        System.out.println(e.getMessage());
      }
      if (msg == null) {
        //客户端已经断开连接
        break;
      }
      if (msg instanceof String) {
        String heartbeat = (String) msg;
        if (heartbeat.equals("heartbeat")) {
          lastHeartbeatTime = System.currentTimeMillis();
          //System.out.println("88:heartbeat "+socket.getPort()+" "+System.currentTimeMillis());
          try {
            out.writeObject("heartbeat");
          } catch (IOException e) {
            System.out.println(e.getMessage());
          }
        }
        continue;
      }
      Message message = (Message) msg;
      login(message);
      if (status.equals("login")) {
        break;
      }
    }
    if (status.equals("login")) {

      //消息循环
      while (true) {
        Object msg = null;
        try {
          msg = in.readObject();

        } catch (IOException | ClassNotFoundException e) {
          //e.printStackTrace();
          System.out.println(e.getMessage());
        }
        if (msg == null) {
          //客户端已经断开连接
          break;
        }
        if (msg instanceof String) {
          String heartbeat = (String) msg;
          if (heartbeat.equals("heartbeat")) {
            //System.out.println("88:heartbeat "+socket.getPort()+" "+System.currentTimeMillis());
            lastHeartbeatTime = System.currentTimeMillis();
            sendMsgLock.lock();
            try {
              out.writeObject("heartbeat");
            } catch (IOException e) {
              System.out.println(e.getMessage());
            }
            sendMsgLock.unlock();
          }
          continue;
        }
        if (msg instanceof SerFile) {
          SerFile serFile = (SerFile) msg;
          fileMap.put(serFile.getFileName(), serFile);
          Message message = new Message("server", serFile.getFileName(), serFile.getGroupName(), "FILE");
          String[] users = getGroup(serFile.getGroupName()).getUserList().toArray(new String[0]);
          serverSelectedBroadcast(message, users);
          continue;
        }
        Message message = (Message) msg;
        System.out.println("108: " + message.getSentBy() + " " + message.getSendTo() + " " + message.getGroupName() + " " + message.getData());
        if (message.getSendTo().equals("server")) {
          //客户端向服务器发送了非聊天信息
          if (message.getData().equals("CLOSE")) {
            System.out.println("client initiated the shutdown");
            //客户端主动关闭连接
            break;
          }
          if (message.getData().equals("GROUP")) {
            //客户端获取群聊视窗
            Group group = getGroup(message.getGroupName());
            sendGroup(group);
          }
          if (message.getData().startsWith("CREATE ")) {
            //客户端请求创建群聊/私聊
            String userStr = username + " " + message.getData().substring(7);
            String[] users = userStr.split(" ");
            if (users.length == 2) {
              //私聊
              createAndSendPrivateGroup(users);
            } else {
              //群聊
              createAndSendGroup(users);
            }
          }
          if (message.getData().startsWith("FILE")) {
            String fileName = message.getData().substring(5);
            SerFile serFile = fileMap.get(fileName);
            if (serFile == null) {
              System.out.println("file not found");
              continue;
            }
            sendFile(serFile);
          }
          continue;
        }
        //客户端向服务器发送了聊天信息
        message = processMessage(message);
        //String groupName = message.getGroupName();
        addMessageToGroup(message);
      }
    }
    System.out.println("a client disconnected");
    //关闭连接
    close();
  }

  //login
  public void login(Message loginMessage) {
    //解析登录信息
    String username = loginMessage.getSentBy();
    //按道理说密码应该加密，不过管他呢
    String password = loginMessage.getData();
    //验证登录信息
    User user = getUser(username);
    if (user == null) {
      //用户不存在,创建
      user = createUser(username, password);
    }
    if (hasOnlineUser(username)) {
      //已存在用户
      Message message = new Message("server", username, "430 Login failed, user already online");
      try {
        out.writeObject(message);
        out.flush();
      } catch (IOException e) {
        System.out.println(e.getMessage());
      }
      return;
    } else if (!user.getPassword().equals(password)) {
      //密码错误
      Message message = new Message("server", username, "430 Login failed, wrong password");
      try {
        out.writeObject(message);
        out.flush();
      } catch (IOException e) {
        System.out.println(e.getMessage());
      }
      return;
    }
    //登录成功
    this.username = username;
    this.status = "login";
    addThread(username, this);
    //发送登录成功信息
    Message message = new Message("server", username, "230 Login successful");
    sendMsgLock.lock();
    try {
      out.writeObject(message);
      out.flush();
      System.out.println("send login info, username: " + username + ", password: " + password);
    } catch (IOException e) {
      System.out.println(e.getMessage());
    }
    //发送用户信息，组信息
    try {
      out.writeObject(getOnlineUserList());
      //SerArrayList<Group> groupList1 = new SerArrayList<>();
      //groupList1.addAll(getGroupIncludingUser(username));
      //List<Group> groupList1 = getGroupIncludingUser(username);
      out.writeObject(getGroupIncludingUser(username));
      out.flush();
      System.out.println("send user list and group list");
    } catch (IOException e) {
      System.out.println(e.getMessage());
    }
    sendMsgLock.unlock();
    addOnlineUser(username);


  }

  //send message
  public void sendMsg(Message message) {
    sendMsgLock.lock();
    try {
      out.writeObject(message);
      out.flush();
    } catch (IOException e) {
      System.out.println(e.getMessage());
    }
    sendMsgLock.unlock();
  }

//    public void sendUser(User user) {
//        sendMsgLock.lock();
//        try {
//            out.writeObject(user);
//            out.flush();
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//        sendMsgLock.unlock();
//    }

  public void sendGroup(Group group) {
    sendMsgLock.lock();
    try {
      System.out.println("send group: " + group.getGroupName());
      out.writeObject(group);
      out.flush();
    } catch (IOException e) {
      System.out.println(e.getMessage());
    }
    sendMsgLock.unlock();
  }

  public void sendFile(SerFile file) {
    sendMsgLock.lock();
    try {
      System.out.println("send file: " + file.getFileName());
      out.writeObject(file);
      out.flush();
    } catch (IOException e) {
      System.out.println(e.getMessage());
    }
    sendMsgLock.unlock();
  }

  public void start() {
    new Thread(this).start();
  }

  public void close() {
    //尝试关闭此客户端的连接
    try {
      Message msgClose = new Message("server", username, "CLOSE");
      out.writeObject(msgClose);
      out.flush();
    } catch (IOException e) {
      System.out.println(e.getMessage());
    }
    //关闭输入输出流
    try {
      in.close();
      out.close();
    } catch (IOException e) {
      System.out.println(e.getMessage());
    }
    //关闭socket
    try {
      socket.close();
    } catch (IOException e) {
      System.out.println(e.getMessage());
    }
    //移除线程
    removeThread(username);
    //中断线程
    Thread.currentThread().interrupt();
  }
}
