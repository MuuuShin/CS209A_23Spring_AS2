package cn.edu.sustech.cs209.chatting.client;

import cn.edu.sustech.cs209.chatting.common.Group;
import cn.edu.sustech.cs209.chatting.common.Message;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Callback;
import javafx.util.Pair;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

public class Controller implements Initializable {

    public Label currentUsername;
    public static Label currentOnlineCnt;
    public static ListView<chatListHBox> chatList = new ListView<>();
    public TextArea inputArea;
    @FXML
    static
    ListView<Message> chatContentList = new ListView<>();

    static String username;
    public static Button sendButton;

    private Socket clientSocket;

    private ObjectInputStream in;
    private ObjectOutputStream out;

    public static BlockingQueue<Object> receiveMessageQueue;
    public static BlockingQueue<Object> sendMessageQueue;

    private static ClientReceiveThread clientReceiveThread;
    private static ClientSendThread clientSendThread;
    private static ConcurrentHashMap<String, Group> groupMap = new ConcurrentHashMap<>();
    private static List<Group> groupList = new ArrayList<>();
    private static List<String> onlineUserList = new ArrayList<>();

    private static int onlineUserNum = 0;
    private static ReentrantLock chatListLock = new ReentrantLock();
    private static ReentrantLock onlineUserListLock = new ReentrantLock();

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {

        //连接服务器
        System.out.println("Connecting to server...");
        try {
            clientSocket = new Socket("localhost", 6868);
            System.out.println("Connected to server");

            in = new ObjectInputStream(clientSocket.getInputStream());
            out = new ObjectOutputStream(clientSocket.getOutputStream());
            receiveMessageQueue = new ArrayBlockingQueue<>(10);
            clientReceiveThread = new ClientReceiveThread(in);
            clientReceiveThread.start();
        } catch (Exception e) {
            System.out.println("Connecting to server failed");
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("Warning");
            alert.setHeaderText("Connecting to server failed");
            alert.setContentText("Please check your network connection.");
            alert.showAndWait();
            System.out.println(e.getMessage());
            Platform.exit();
            return;
        }

        //login windows reference this website:
        //https://blog.csdn.net/qq_40990854/article/details/85161449

        // 创建对话框
        Dialog<Pair<String, String>> dialog = new Dialog<>();
        dialog.setTitle("Login/Register");
        dialog.setHeaderText("Please enter your username and password");

        // Set the button types.
        ButtonType loginButtonType = new ButtonType("登录/注册", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(loginButtonType, ButtonType.CANCEL);

        // 创建一个网格布局
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        TextField userName = new TextField();
        userName.setPromptText("Username");
        //展示方便可以换成文字的，现在就这样吧
        PasswordField password = new PasswordField();
        password.setPromptText("Password");

        grid.add(new Label("Username:"), 0, 0);
        grid.add(userName, 1, 0);
        grid.add(new Label("Password:"), 0, 1);
        grid.add(password, 1, 1);

        // 用户不输入名字密码就不给按登录按钮
        Node loginButton = dialog.getDialogPane().lookupButton(loginButtonType);
        loginButton.setDisable(true);
        password.textProperty().addListener((observable, oldValue, newValue) -> {
            loginButton.setDisable(userName.getText().trim().isEmpty() || newValue.trim().isEmpty() || userName.getText().equals("server"));
        });
        userName.textProperty().addListener((observable, oldValue, newValue) -> {
            loginButton.setDisable(password.getText().trim().isEmpty() || newValue.trim().isEmpty() || newValue.equals("server"));
        });

        dialog.getDialogPane().setContent(grid);

        // 用户初始化界面后无需鼠标点击直接可以输入用户名
        Platform.runLater(userName::requestFocus);

        // Convert the result to a username-password-pair when the login button is clicked.
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == loginButtonType) {
                return new Pair<>(userName.getText(), password.getText());
            }
            return null;
        });

        while (true) {
            Optional<Pair<String, String>> input = dialog.showAndWait();

            if (input.isPresent() && !input.get().getKey().isEmpty() && !input.get().getValue().isEmpty()) {
                //发送用户名和密码
                try {
                    Message message = new Message(input.get().getKey(), "server", input.get().getValue());
                    out.writeObject(message);
                    out.flush();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                //接收服务器返回的信息
                try {
                    Message message = (Message) receiveMessageQueue.take();
                    System.out.println(message.getData());
                    if (message.getData().startsWith("230 ")) {
                        username = input.get().getKey();
                        break;
                    } else if (message.getData().startsWith("430 ")) {
                        Alert alert = new Alert(Alert.AlertType.WARNING);
                        alert.setTitle("Warning");
                        alert.setHeaderText("null");
                        alert.setContentText(message.getData().substring(4));
                        alert.showAndWait();
                        continue;
                    } else {
                        System.out.println("unknown error");
                        Alert alert = new Alert(Alert.AlertType.WARNING);
                        alert.setTitle("Warning");
                        alert.setHeaderText("null");
                        alert.setContentText("unknown error, please contact with admin");
                        alert.showAndWait();
                        threadClose();
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

            } else {
                //直接通过关闭退出
                threadClose();
            }
        }
        //进行初始化
        try {
            Object object = receiveMessageQueue.take();
            if (object instanceof ArrayList<?>) {
                for (Object o : (ArrayList<?>) object) {
                    if (o instanceof String) {
                        onlineUserList.add((String) o);
                        onlineUserNum++;
                    }
                }
            }
            System.out.println("init 1/3");
            object = receiveMessageQueue.take();
            if (object instanceof ArrayList<?>) {
                for (Object o : (ArrayList<?>) object) {
                    if (o instanceof Group) {
                        groupMap.put(((Group) o).getGroupName(), (Group) o);
                        groupList.add((Group) o);
                    }
                }
            }
            System.out.println("init 2/3");
            for (Group group : groupList) {
                chatListHBox hBox = new chatListHBox(group.getGroupName(), group.getUserList(), group.getTimestamp(), username);
                chatList.getItems().add(hBox);
            }
            sortChatList();
            //添加聊天列表监听器
            chatList.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
                if (newValue != null) {
                    chatContentList.getItems().clear();
                    Group group = groupMap.get(newValue.getGroupName());
                    for (Message message : group.getMsgList()) {
                        chatContentList.getItems().add(message);
                    }
                    if(newValue.getMembers().size()>2){
                        Stage stage = new Stage();
                        stage.setTitle("群聊成员");
                        //列出群聊用户信息
                        VBox vBox = new VBox();
                        vBox.setSpacing(10);
                        vBox.setPadding(new Insets(10, 10, 10, 10));
                        for (String member : newValue.getMembers()) {
                            vBox.getChildren().add(new Label(member));
                        }
                        Scene scene = new Scene(vBox, 200, 200);
                        stage.setScene(scene);
                        stage.show();
                    }
                    Platform.runLater(() -> {
                        newValue.setMark(false);
                        chatContentList.refresh();
                        chatContentList.scrollTo(chatContentList.getItems().size() - 1);
                        onlineUserListLock.lock();
                        if(onlineUserList.contains(newValue.getInfoLabelText())) {
                            sendButton.setDisable(false);
                        }else{
                            sendButton.setDisable(true);
                        }
                        onlineUserListLock.unlock();
                    });
                }
            });
            //添加输入框监听器
            inputArea.textProperty().addListener((observable, oldValue, newValue) -> {
                sendButton.setDisable(newValue.trim().isEmpty());
            });
            System.out.println("init 3/3");
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
//        onlineUserList.add(username);
        onlineUserNum++;
//        System.out.println(onlineUserList);

        currentUsername.setText("Current User: " + username);
        currentOnlineCnt.setText("Online: " + String.valueOf(onlineUserNum));

        clientReceiveThread.setUsername(username);
        sendMessageQueue = new ArrayBlockingQueue<>(10);
        clientSendThread = new ClientSendThread(out);
        clientSendThread.start();
        System.out.println("init success");
        chatContentList.setCellFactory(new MessageCellFactory());
    }

    @FXML
    public void createPrivateChat() {
        AtomicReference<String> user = new AtomicReference<>();

        Stage stage = new Stage();
        ComboBox<String> userSel = new ComboBox<>();

        onlineUserListLock.lock();
        userSel.getItems().addAll(onlineUserList);
        userSel.getItems().addAll("test1", "test2");
        onlineUserListLock.unlock();

        Button okBtn = new Button("OK");
        okBtn.setOnAction(e -> {
            user.set(userSel.getSelectionModel().getSelectedItem());
            stage.close();
        });

        HBox box = new HBox(10);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(20, 20, 20, 20));
        box.getChildren().addAll(userSel, okBtn);
        stage.setScene(new Scene(box));
        stage.showAndWait();

        //  if the current user already chatted with the selected user, just open the chat with that user
        chatListLock.lock();
        for (chatListHBox hBox : chatList.getItems()) {
            //这里是特殊的，因为这里的infoLabel是用户名，groupName是群名，我们要对比的是用户名
            if (hBox.getInfoLabelText().equals(user.get())) {
                chatList.getSelectionModel().select(hBox);
                chatListLock.unlock();
                return;
            }
            Message msg = new Message(username, "server", "CREATE "+user.get());
            try {
                sendMessageQueue.put(msg);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        chatListLock.unlock();
        // otherwise, create a new chat item in the left panel, the title should be the selected user's name
    }

    /**
     * A new dialog should contain a multi-select list, showing all user's name.
     * You can select several users that will be joined in the group chat, including yourself.
     * <p>
     * The naming rule for group chats is similar to WeChat:
     * If there are > 3 users: display the first three usernames, sorted in lexicographic order, then use ellipsis with the number of users, for example:
     * UserA, UserB, UserC... (10)
     * If there are <= 3 users: do not display the ellipsis, for example:
     * UserA, UserB (2)
     */
    @FXML
    public void createGroupChat() {
        List<String> selectedUsers = new ArrayList<>();

        Stage stage = new Stage();
        onlineUserListLock.lock();
        int size=onlineUserList.size();
        CheckBox[] checkBoxes = new CheckBox[size+2];

        for(int i = 0; i < size; i++) {
            checkBoxes[i] = new CheckBox(onlineUserList.get(i));
        }
        onlineUserListLock.unlock();
        checkBoxes[size] = new CheckBox("test1");
        checkBoxes[size+1] = new CheckBox("test2");

        Button okBtn = new Button("OK");
        okBtn.setOnAction(e -> {
            for (CheckBox checkBox : checkBoxes) {
                if (checkBox.isSelected()) {
                    selectedUsers.add(checkBox.getText());
                }
            }
            stage.close();
        });

        VBox box = new VBox(10);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(20, 20, 20, 20));
        box.getChildren().addAll(checkBoxes);
        box.getChildren().add(okBtn);
        stage.setScene(new Scene(box));
        stage.showAndWait();

        //if the current user already chatted with the selected user, just open the chat with that user
        chatListLock.lock();
        for (chatListHBox hBox : chatList.getItems()) {
            //这里是特殊的，因为这里的infoLabel是用户名，groupName是群名，我们要对比的是用户名
            if (hBox.getMembers().equals(selectedUsers)) {
                chatList.getSelectionModel().select(hBox);
                chatListLock.unlock();
                return;
            }
            String userStr = "";
            for (String user : selectedUsers) {
                userStr += user + " ";
            }
            Message msg = new Message(username, "server", "CREATE "+userStr);
            try {
                sendMessageQueue.put(msg);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        chatListLock.unlock();
        // otherwise, create a new chat item in the left panel, the title should be the selected user's name
    }

    /**
     * Sends the message to the <b>currently selected</b> chat.
     * <p>
     * Blank messages are not allowed.
     * After sending the message, you should clear the text input field.
     */
    @FXML
    public void doSendMessage() {
        String msg = inputArea.getText();
        //做unicode表情包处理

        if (msg.equals("")) {
            return;
        }
        inputArea.clear();
        String groupName = chatList.getSelectionModel().getSelectedItem().getGroupName();
        Message message = new Message(username, "server", groupName + " " + msg);
        try {
            sendMessageQueue.put(message);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * You may change the cell factory if you changed the design of {@code Message} model.
     * Hint: you may also define a cell factory for the chats displayed in the left panel, or simply override the toString method.
     */
    private class MessageCellFactory implements Callback<ListView<Message>, ListCell<Message>> {
        @Override
        public ListCell<Message> call(ListView<Message> param) {
            return new ListCell<Message>() {

                @Override
                public void updateItem(Message msg, boolean empty) {
                    super.updateItem(msg, empty);
                    if (empty || Objects.isNull(msg)) {
                        setText(null);
                        setGraphic(null);
                        return;
                    }

                    HBox wrapper = new HBox();
                    Label nameLabel = new Label(msg.getSentBy());
                    Label msgLabel = new Label(msg.getData());

                    nameLabel.setPrefSize(50, 20);
                    nameLabel.setWrapText(true);
                    nameLabel.setStyle("-fx-border-color: black; -fx-border-width: 1px;");

                    if (username.equals(msg.getSentBy())) {
                        wrapper.setAlignment(Pos.TOP_RIGHT);
                        wrapper.getChildren().addAll(msgLabel, nameLabel);
                        msgLabel.setPadding(new Insets(0, 20, 0, 0));
                    } else {
                        wrapper.setAlignment(Pos.TOP_LEFT);
                        wrapper.getChildren().addAll(nameLabel, msgLabel);
                        msgLabel.setPadding(new Insets(0, 0, 0, 20));
                    }

                    setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
                    setGraphic(wrapper);
                }
            };
        }
    }


    public static void addOnlineUser(String username) {
        onlineUserListLock.lock();
        onlineUserList.add(username);
        onlineUserNum++;
        onlineUserListLock.unlock();
        Platform.runLater(() -> {
            currentOnlineCnt.setText("Online: " + String.valueOf(onlineUserNum));
            if(chatList.getSelectionModel().getSelectedItem().getInfoLabelText().equals(username)){
                sendButton.setDisable(false);
            }
        });
    }

    public static void removeOnlineUser(String username) {
        onlineUserListLock.lock();
        onlineUserList.remove(username);
        onlineUserNum--;
        onlineUserListLock.unlock();
        Platform.runLater(() -> {
            currentOnlineCnt.setText("Online: " + String.valueOf(onlineUserNum));
            if(chatList.getSelectionModel().getSelectedItem().getInfoLabelText().equals(username)){
                sendButton.setDisable(true);
            }
        });
    }
    public static void addGroup(Group group) {
        String name = group.getGroupName();
        groupMap.put(name, group);
        chatListLock.lock();
        groupList.add(group);
        chatListLock.unlock();
        Platform.runLater(() -> {
            chatList.getItems().add(new chatListHBox(name, group.getUserList(), group.getTimestamp(), username));
        });
        sortChatList();
        //chatList选中在新Group上
        chatListLock.lock();
        for(chatListHBox hBox:chatList.getItems()){
            if(hBox.getGroupName().equals(name)){
                chatList.getSelectionModel().select(hBox);
                break;
            }
        }
        chatListLock.unlock();
        Platform.runLater(() -> {
            chatContentList.refresh();
        });
    }

    public static void addMessage(String groupName, Message message) {
        groupMap.get(groupName).addMessage(message);
        for (chatListHBox hBox : chatList.getItems()) {
            if (hBox.getGroupName().equals(groupName)) {
                hBox.setTimestamp(message.getTimestamp());
                if (chatList.getSelectionModel().getSelectedItem() != null && chatList.getSelectionModel().getSelectedItem().getGroupName().equals(groupName)) {
                    chatContentList.getItems().add(message);
                    Platform.runLater(() -> {
                        chatContentList.refresh();
                    });
                } else if (chatList.getSelectionModel().getSelectedItem() != null && !chatList.getSelectionModel().getSelectedItem().getGroupName().equals(groupName)) {
                    hBox.setMark(true);
                }

                break;
            }
        }
        sortChatList();
    }

    public synchronized static void sortChatList() {
        chatListLock.lock();
        //timestamp排序，大的在前
        //根据时间排序chatlist
        chatList.getItems().sort((o1, o2) -> {
            return ((chatListHBox) o2).getTimestamp().compareTo(((chatListHBox) o1).getTimestamp());
        });
        chatListLock.unlock();
        Platform.runLater(() -> {
            chatList.refresh();
        });
    }

    public static void threadClose() {
        System.out.println("thread close normaly");
        if (clientSendThread != null)
            clientSendThread.close();
        if (clientReceiveThread != null)
            clientReceiveThread.close();
        Platform.exit();
        System.exit(0);
    }

    public static void threadUnexpectedClose() {
        if (clientSendThread != null)
            clientSendThread.close();
        if (clientReceiveThread != null)
            clientReceiveThread.close();
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Warning");
        alert.setHeaderText("null");
        alert.setContentText("server error, please contact with admin");
        alert.showAndWait();
        Platform.exit();
        System.exit(0);
    }

}
