package cn.edu.sustech.cs209.chatting.client;

import cn.edu.sustech.cs209.chatting.common.Group;
import cn.edu.sustech.cs209.chatting.common.Message;

import java.io.IOException;
import java.io.ObjectInputStream;
import static cn.edu.sustech.cs209.chatting.client.Controller.*;

/**
 * Only responsible for receiving messages.
 */
public class ClientReceiveThread implements Runnable {
    private String username;
    private final ObjectInputStream in;


    public ClientReceiveThread(ObjectInputStream in) {
        this.username = "anonymous";
        this.in = in;
    }

    @Override
    public void run() {
        while (true) {
            //获取客户端信息，以及判断客户端状态
            Object message = null;
            try {
                message = in.readObject();
                if (this.username.equals("anonymous")) {
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
                addGroup((Group) message);
            }
            if (message instanceof Message) {
                Message msg=(Message)message;
                if(msg.getSendTo().equals("server")){
                    if(msg.getData().equals("CLOSE")){
                        threadClose();
                        return;
                    }
                    if(msg.getData().equals("ONLINE")){
                        addOnlineUser(msg.getSentBy());
                    }
                    if(msg.getData().equals("OFFLINE")){
                        removeOnlineUser(msg.getSentBy());
                    }
                }
                addMessage(msg.getGroupName(), msg);
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
