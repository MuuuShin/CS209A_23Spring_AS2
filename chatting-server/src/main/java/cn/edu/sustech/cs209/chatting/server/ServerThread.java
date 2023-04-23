package cn.edu.sustech.cs209.chatting.server;

import cn.edu.sustech.cs209.chatting.common.*;

import java.io.*;
import java.net.Socket;
import java.util.concurrent.locks.ReentrantLock;

import static cn.edu.sustech.cs209.chatting.server.Main.*;

public class ServerThread implements Runnable {

    private Socket socket;
    private String status;
    private String username;

    private ObjectInputStream in;
    private ObjectOutputStream out;

    private ReentrantLock sendMsgLock = new ReentrantLock();

    public ServerThread(Socket socket) {
        this.socket = socket;
        this.status = "logout";
        this.username = "anonymous";

    }

    @Override
    public void run() {
        try {
            in = new ObjectInputStream(socket.getInputStream());
            out = new ObjectOutputStream(socket.getOutputStream());
//            in = new Scanner(inputStream);
//            out = new PrintWriter(outputStream, true);
        } catch (Exception e) {
            System.out.println(e.getMessage());
            return;
        }
        while (true) {
            //获取客户端信息，以及判断客户端状态
            Message message = null;
            try {
                message = (Message) in.readObject();
            } catch (IOException | ClassNotFoundException e) {
                System.out.println(e.getMessage());
            }
            if (message == null) {
                //客户端已经断开连接
                break;
            }
            login(message);
            if(status.equals("login")){
                break;
            }
        }
        if (status.equals("login")) {
            while (true) {
                //获取客户端信息，以及判断客户端状态
                Message message = null;
                try {
                    message = (Message) in.readObject();
                } catch (IOException | ClassNotFoundException e) {
                    System.out.println(e.getMessage());
                }
                if (message == null) {
                    //客户端已经断开连接
                    break;
                }

                if (message.getSendTo().equals("server")) {
                    //客户端向服务器发送了非聊天信息
                    if (message.getData().equals("CLOSE")) {
                        //客户端主动关闭连接
                        break;
                    }
                    if (message.getData().equals("GROUP")) {
                        //客户端获取群聊视窗
                        Group group=getGroup(message.getGroupName());
                        sendGroup(group);
                    }
                    if (message.getData().startsWith("CREATE ")) {
                        //客户端请求创建群聊/私聊
                        String userStr = username + " " + message.getData().substring(7);
                        String[] users = userStr.split(" ");
                        if (users.length == 2) {
                            //私聊
                            createPrivateGroup(users);
                        } else {
                            //群聊
                            createGroup(users);
                        }
                    }
                    continue;
                }
                //客户端向服务器发送了聊天信息
                message=processMessage(message);
                //String groupName = message.getGroupName();
                addMessageToGroup(message);
            }
        }

        System.out.println("a client disconnected");
        //尝试关闭此客户端的连接
        try {
            Message msgClose = new Message("server", username, "CLOSE");
            out.writeObject(msgClose);
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
        //关闭此线程
        removeThread(username);
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
        } else if (!user.getPassword().equals(password)) {
            //密码错误
            Message message = new Message("server", username, "430 Login failed");
            try {
                out.writeObject(message);
            } catch (IOException e) {
                System.out.println(e.getMessage());
            }
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
        } catch (IOException e) {
            System.out.println(e.getMessage());
        }
        //发送用户信息，组信息
        try {
            out.writeObject(getOnlineUserList());
            out.writeObject(getGroupIncludingUser(username));
        } catch (IOException e) {
            System.out.println(e.getMessage());
        }
        sendMsgLock.unlock();
    }

    //send message
    public void sendMsg(Message message) {
        sendMsgLock.lock();
        try {
            out.writeObject(message);
        } catch (IOException e) {
            System.out.println(e.getMessage());
        }
        sendMsgLock.unlock();
    }

    public void sendUser(User user) {
        sendMsgLock.lock();
        try {
            out.writeObject(user);
        } catch (IOException e) {
            System.out.println(e.getMessage());
        }
        sendMsgLock.unlock();
    }

    public void sendGroup(Group group) {
        sendMsgLock.lock();
        try {
            out.writeObject(group);
        } catch (IOException e) {
            System.out.println(e.getMessage());
        }
        sendMsgLock.unlock();
    }

    public void start() {
        new Thread(this).start();
    }
}