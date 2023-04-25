package cn.edu.sustech.cs209.chatting.client;

import java.io.IOException;
import java.io.ObjectOutputStream;

import static cn.edu.sustech.cs209.chatting.client.Controller.sendMessageQueue;


/**
 * Only responsible for sending messages.
 */
public class ClientSendThread implements Runnable {
  private final ObjectOutputStream out;


  public ClientSendThread(ObjectOutputStream out) {
    this.out = out;
  }

  @Override
  public void run() {
    while (true) {
      //获取消息
      Object msg;
      try {
        msg = sendMessageQueue.take();
      } catch (InterruptedException e) {
        e.printStackTrace();
        break;
      }
      //发送消息
      try {
        out.writeObject(msg);
      } catch (IOException e) {
        System.out.println(e.getMessage());
      }
    }
  }

  public void start() {
    new Thread(this).start();
  }

  public void close() {
    //关闭输出流
    try {
      out.close();
    } catch (IOException e) {
      System.out.println(e.getMessage());
    }
    //中断线程
    Thread.currentThread().interrupt();
  }

}
