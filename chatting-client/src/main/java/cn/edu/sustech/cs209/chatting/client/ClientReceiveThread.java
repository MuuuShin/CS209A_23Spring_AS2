package cn.edu.sustech.cs209.chatting.client;

import cn.edu.sustech.cs209.chatting.common.Group;
import cn.edu.sustech.cs209.chatting.common.Message;
import cn.edu.sustech.cs209.chatting.common.SerFile;

import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;

import static cn.edu.sustech.cs209.chatting.client.Controller.*;

/**
 * Only responsible for receiving messages.
 */
public class ClientReceiveThread implements Runnable {
  private final ObjectInputStream in;
  private final ObjectOutputStream out;
  Controller controller;
  private String username;
  private Long lastHeartbeatTime;

  public ClientReceiveThread(ObjectInputStream in, ObjectOutputStream out, Controller controller) {
    this.username = "anonymous";
    this.in = in;
    this.out = out;
    this.controller = controller;
  }

  @Override
  public void run() {
    lastHeartbeatTime = System.currentTimeMillis();
    //心跳包
    new Thread(() -> {
      while (true) {
        //发送心跳包
        do {
          try {
            out.writeObject("heartbeat");
          } catch (IOException e) {
            System.out.println(e.getMessage());
          }

          try {
            Thread.sleep(500);
          } catch (InterruptedException e) {
            e.printStackTrace();
          }
        } while (System.currentTimeMillis() - lastHeartbeatTime <= 4000);
        //心跳包超时
        System.out.println("heartbeat timeout");
        threadUnexpectedClose();

      }
    }).start();
    while (true) {
      //获取客户端信息，以及判断客户端状态
      Object message = null;
      try {
        message = in.readObject();
        if (message instanceof String) {
          //System.out.println("60:" + message+System.currentTimeMillis());
          if (message.equals("heartbeat")) {
            lastHeartbeatTime = System.currentTimeMillis();
          }
          continue;
        }

        if (this.username.equals("anonymous")) {
          //System.out.println("64:" + message);
          receiveMessageQueue.put(message);
          continue;
        }
      } catch (IOException e) {
        System.out.println(e.getMessage());
        threadUnexpectedClose();
        return;
      } catch (ClassNotFoundException e) {
        e.printStackTrace();
      } catch (InterruptedException e) {
        e.printStackTrace();
        break;
      }
      if (message == null) {
        //客户端已经断开连接
        break;
      }
      if (message instanceof Group) {
        System.out.println("56: Group" + ((Group) message).getGroupName());
        controller.addGroup((Group) message);
      }
      if (message instanceof SerFile) {
        System.out.println("93: SerFile" + ((SerFile) message).getFileName());
        System.out.println(((SerFile) message).getFileContent().length);
        //保存到本地 \\username\\filename
        SerFile serFile = (SerFile) message;
        byte[] fileByte = serFile.getFileContent();
        System.out.println(fileByte);
        File file = new File("file\\" + username + "\\" + serFile.getFileName());
        if (!file.getParentFile().exists()) {
          file.getParentFile().mkdirs();
        }
        try {
          file.createNewFile();
        } catch (IOException e) {
          e.printStackTrace();
        }
        try {
          System.out.println("Writing");
          java.io.FileOutputStream fos = new java.io.FileOutputStream(file);
          fos.write(fileByte);
          fos.close();
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
      if (message instanceof Message) {
        Message msg = (Message) message;
        System.out.println("56" + msg.getSentBy() + " " + msg.getSendTo() + " " + msg.getGroupName() + " " + msg.getData());
        if (msg.getSentBy().equals("server")) {
          if (msg.getData().equals("CLOSE")) {
            threadClose();
            return;
          }
          if (msg.getData().equals("ONLINE")) {
            controller.addOnlineUser(msg.getSendTo());
            continue;
          }
          if (msg.getData().equals("OFFLINE")) {
            controller.removeOnlineUser(msg.getSendTo());
            continue;
          }
          if (msg.getData().equals("FILE")) {
            if (!FileMap.containsKey(msg.getGroupName())) {
              FileMap.put(msg.getGroupName(), new ArrayList<>());
            }
            FileMap.get(msg.getGroupName()).add(msg.getSendTo());
            continue;
          }

        }
        controller.addMessage(msg.getGroupName(), msg);
      }
    }
    threadClose();
  }

  public void start() {
    new Thread(this).start();
  }

  public void close() {
    //关闭输入流
    try {
      in.close();
    } catch (IOException e) {
      System.out.println(e.getMessage());
    }
    //中断线程
    Thread.currentThread().interrupt();
  }

  public void setUsername(String username) {
    this.username = username;
  }
}
