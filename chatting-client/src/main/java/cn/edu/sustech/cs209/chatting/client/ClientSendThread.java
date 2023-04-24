package cn.edu.sustech.cs209.chatting.client;

import cn.edu.sustech.cs209.chatting.common.Message;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.Socket;

import static cn.edu.sustech.cs209.chatting.client.Controller.*;


/**
 * Only responsible for sending messages.
 */
public class ClientSendThread implements Runnable{
    private ObjectOutputStream out;

    public ClientSendThread(ObjectOutputStream out) {
        this.out = out;
    }
    @Override
    public void run() {
        while(true){
            //获取消息
            Object msg = null;
            try {
                msg=sendMessageQueue.take();
            } catch (InterruptedException e) {
                e.printStackTrace();
                break;
            }
            //发送消息
            try {
                out.writeObject(msg);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void start() {
        new Thread(this).start();
    }

    public void close(){
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