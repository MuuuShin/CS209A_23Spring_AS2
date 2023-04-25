package cn.edu.sustech.cs209.chatting.client;

import cn.edu.sustech.cs209.chatting.common.*;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
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
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.util.Callback;
import javafx.util.Pair;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.Socket;
import java.net.URL;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

public class Controller implements Initializable {

    public Label currentUsername;
    public Label currentOnlineCnt;
    public ListView<chatListHBox> chatList = new ListView<>();
    public TextArea inputArea;
    @FXML
    public ListView<Message> chatContentList = new ListView<>();

    static String username;
    public Button sendButton;

    private Socket clientSocket;

    private ObjectInputStream in;
    private ObjectOutputStream out;

    public Stage userStage;
    public VBox vBox;
    public static BlockingQueue<Object> receiveMessageQueue;
    public static BlockingQueue<Object> sendMessageQueue;

    private static ClientReceiveThread clientReceiveThread;
    private static ClientSendThread clientSendThread;
    private static final ConcurrentHashMap<String, Group> groupMap = new ConcurrentHashMap<>();
    private static final List<Group> groupList = new ArrayList<>();
    private static final List<String> onlineUserList = new ArrayList<>();

    private static int onlineUserNum = 0;
    private static final ReentrantLock chatListLock = new ReentrantLock();
    private static final ReentrantLock onlineUserListLock = new ReentrantLock();

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {

        //连接服务器
        System.out.println("Connecting to server...");
        try {
            clientSocket = new Socket("localhost", 6869);
            System.out.println("Connected to server");

            in = new ObjectInputStream(clientSocket.getInputStream());
            out = new ObjectOutputStream(clientSocket.getOutputStream());
            receiveMessageQueue = new ArrayBlockingQueue<>(10);
            clientReceiveThread = new ClientReceiveThread(in, out, this);
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
        //登录成功，进行初始化
        try {
            //线程开

            sendMessageQueue = new ArrayBlockingQueue<>(10);
            clientSendThread = new ClientSendThread(out);
            clientSendThread.start();

            Object object = receiveMessageQueue.take();
            if (object instanceof List<?>) {
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
                        //System.out.println(((Group) o).getGroupName());
                        //System.out.println(((Group) o).getMsgList());
                        //System.out.println(((Group) o).getMsgList().get(0).getData());
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
            chatContentList.setCellFactory(new MessageCellFactory());
            //添加当前组成员列表
            userStage = new Stage();
            userStage.setOnCloseRequest(event -> {
                userStage.hide();
            });
            userStage.setTitle("成员");
            vBox = new VBox();
            vBox.setSpacing(10);
            vBox.setPadding(new Insets(10, 10, 10, 10));
            Scene scene = new Scene(vBox, 200, 200);
            userStage.setScene(scene);
            //添加聊天列表监听器
            chatList.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
                if (newValue != null) {
                    vBox.getChildren().clear();
                    //列出群聊用户信息
                    for (String member : newValue.getMembers()) {
                        Label label = new Label(member);
                        //根据在离线染色
                        if (onlineUserList.contains(member) || member.equals(username)) {
                            label.setTextFill(Color.GREEN);
                        } else {
                            label.setTextFill(Color.GRAY);
                        }
                        vBox.getChildren().add(label);
                    }


                    chatContentList.getItems().clear();
                    Group group = groupMap.get(newValue.getGroupName());
                    ObservableList<Message> observablelist = FXCollections.observableArrayList(group.getMsgList());
                    chatContentList.setItems(observablelist);
                    //System.out.println(observablelist);
                    Platform.runLater(() -> {
                        if (oldValue == null || !oldValue.getGroupName().equals(newValue.getGroupName())) {
                            userStage.show();
                        }
                        newValue.setMark(false);
                        chatContentList.refresh();
                        chatContentList.scrollTo(chatContentList.getItems().size() - 1);
                        onlineUserListLock.lock();
                        sendButton.setDisable(true);
                        onlineUserListLock.unlock();
                        inputArea.clear();
                    });
                }
            });
            //添加输入框监听器
            inputArea.textProperty().addListener((observable, oldValue, newValue) -> {
                sendButton.setDisable(newValue.trim().isEmpty());
                //获取当前组
                chatListHBox hBox = chatList.getSelectionModel().getSelectedItem();
                if (hBox.getMembers().size() == 2) {
                    sendButton.setDisable(!onlineUserList.contains(hBox.getInfoLabelText()));
                }

            });
            System.out.println("init 3/3");
        } catch (InterruptedException e) {
            System.out.println("init error");
            e.printStackTrace();
            System.exit(1);
        }

        onlineUserNum++;
        clientReceiveThread.setUsername(username);
        currentUsername.setText("Current User: " + username);
        currentOnlineCnt.setText("Online: " + onlineUserNum);

        System.out.println("init success");
        Thread clientManager = new Thread(() -> {
            while (true) {
                Scanner scanner = new Scanner(System.in);
                String command = scanner.nextLine();
                System.out.println("Command: " + command);

                if (command.equals("onlineUser")) {
                    onlineUserListLock.lock();
                    System.out.println(onlineUserList);
                    onlineUserListLock.unlock();
                } else if (command.equals("groupList")) {
                    System.out.println(groupList);
                } else if (command.equals("groupMap")) {
                    System.out.println(groupMap);
                } else if (command.equals("chatList")) {
                    System.out.println(chatList.getItems());
                } else if (command.equals("chatContentList")) {
                    System.out.println(chatContentList.getItems());
                } else if (command.equals("sendMessageQueue")) {
                    System.out.println(sendMessageQueue);
                } else if (command.equals("receiveMessageQueue")) {
                    System.out.println(receiveMessageQueue);
                } else if (command.equals("exit")) {
                    threadClose();
                } else {
                    System.out.println("unknown command");
                }
            }
        });
        clientManager.start();
    }

    @FXML
    public void createPrivateChat() {
        AtomicReference<String> user = new AtomicReference<>();

        Stage stage = new Stage();
        ComboBox<String> userSel = new ComboBox<>();

        onlineUserListLock.lock();
        userSel.getItems().addAll(onlineUserList);
        onlineUserListLock.unlock();

        Button okBtn = new Button("OK");
        okBtn.setOnAction(e -> {
            user.set(userSel.getSelectionModel().getSelectedItem());
            stage.close();
        });
        //关闭窗口
        stage.setOnCloseRequest(e -> {
            user.set(null);
            stage.close();
        });

        HBox box = new HBox(10);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(20, 20, 20, 20));
        box.getChildren().addAll(userSel, okBtn);
        stage.setScene(new Scene(box));
        stage.showAndWait();

        if (user.get() == null) {
            return;
        }
        //  if the current user already chatted with the selected user, just open the chat with that user
        chatListLock.lock();
        for (chatListHBox hBox : chatList.getItems()) {
            //这里是特殊的，因为这里的infoLabel是用户名，groupName是群名，我们要对比的是用户名
            if (hBox.getInfoLabelText().equals(user.get())) {
                chatList.getSelectionModel().select(hBox);
                chatListLock.unlock();
                System.out.println("chat exist");
                return;
            }
        }
        chatListLock.unlock();
        System.out.println("chat create");
        Message msg = new Message(username, "server", "CREATE " + user.get());
        try {
            sendMessageQueue.put(msg);
            System.out.println("chat put");
        } catch (InterruptedException e) {
            e.printStackTrace();
        }


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
        int size = onlineUserList.size();
        CheckBox[] checkBoxes = new CheckBox[size];

        for (int i = 0; i < size; i++) {
            checkBoxes[i] = new CheckBox(onlineUserList.get(i));
        }
        onlineUserListLock.unlock();

        Button okBtn = new Button("OK");
        okBtn.setOnAction(e -> {
            for (CheckBox checkBox : checkBoxes) {
                if (checkBox.isSelected()) {
                    selectedUsers.add(checkBox.getText());
                }
            }
            stage.close();
        });
        //关闭窗口
        stage.setOnCloseRequest(e -> {
            stage.close();
        });
        VBox box = new VBox(10);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(20, 20, 20, 20));
        box.getChildren().addAll(checkBoxes);
        box.getChildren().add(okBtn);
        stage.setScene(new Scene(box));
        stage.showAndWait();

        if (selectedUsers.size() == 0) {
            return;
        }
        //if the current user already chatted with the selected user, just open the chat with that user
        chatListLock.lock();
        for (chatListHBox hBox : chatList.getItems()) {
            if (hBox.getMembers().equals(selectedUsers)) {
                chatList.getSelectionModel().select(hBox);
                chatListLock.unlock();
                return;
            }
        }
        chatListLock.unlock();
        String userStr = "";
        for (String user : selectedUsers) {
            userStr += user + " ";
        }
        Message msg = new Message(username, "server", "CREATE " + userStr);
        try {
            sendMessageQueue.put(msg);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }


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
        if (msg.equals("")) {
            return;
        }
        inputArea.clear();
        String groupName = chatList.getSelectionModel().getSelectedItem().getGroupName();
        Message message = new Message(username, "user", groupName, msg);
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
                    Platform.runLater(() -> {
                        setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
                        setGraphic(wrapper);
                    });

                }
            };
        }
    }


    public void addOnlineUser(String username) {
        onlineUserListLock.lock();
        onlineUserList.add(username);
        onlineUserNum++;
        onlineUserListLock.unlock();
        Platform.runLater(() -> {
            //列出群聊用户信息
            for (Node label : vBox.getChildren()) {
                //根据在离线染色
                if (label instanceof Label) {
                    if (((Label) label).getText().equals(username)) {
                        ((Label) label).setTextFill(Color.GREEN);
                    }
                }
            }
            currentOnlineCnt.setText("Online: " + onlineUserNum);
            if (null != chatList.getSelectionModel().getSelectedItem() && chatList.getSelectionModel().getSelectedItem().getInfoLabelText().equals(username)) {
                sendButton.setDisable(false);
            }
        });
    }

    public void removeOnlineUser(String username) {
        onlineUserListLock.lock();
        onlineUserList.remove(username);
        onlineUserNum--;
        onlineUserListLock.unlock();
        Platform.runLater(() -> {
            //列出群聊用户信息
            for (Node label : vBox.getChildren()) {
                //根据在离线染色
                if (label instanceof Label) {
                    if (((Label) label).getText().equals(username)) {
                        ((Label) label).setTextFill(Color.GRAY);

                    }
                }
            }
            currentOnlineCnt.setText("Online: " + onlineUserNum);
            if (null != chatList.getSelectionModel().getSelectedItem() && chatList.getSelectionModel().getSelectedItem().getInfoLabelText().equals(username)) {
                sendButton.setDisable(true);
            }
        });
    }

    public void addGroup(Group group) {
        String name = group.getGroupName();
        groupMap.put(name, group);
        groupList.add(group);
        chatListLock.lock();
        Platform.runLater(() -> {
        chatList.getItems().add(new chatListHBox(name, group.getUserList(), group.getTimestamp(), username));
        });
        chatListLock.unlock();
        sortChatList();
        //chatList选中在新Group上
        chatListLock.lock();
        System.out.println("add group");
        for (chatListHBox hBox : chatList.getItems()) {
            if (hBox.getGroupName().equals(name)) {
                Platform.runLater(() -> chatList.getSelectionModel().select(hBox));
                break;
            }
        }
        chatListLock.unlock();
        Platform.runLater(() -> chatContentList.refresh());
    }

    public void addMessage(String groupName, Message message) {
        groupMap.get(groupName).addMessage(message);
        chatListLock.lock();
        for (chatListHBox hBox : chatList.getItems()) {
            if (hBox.getGroupName().equals(groupName)) {
                hBox.setTimestamp(message.getTimestamp());
                if (chatList.getSelectionModel().getSelectedItem() != null && chatList.getSelectionModel().getSelectedItem().getGroupName().equals(groupName)) {
                    chatContentList.getItems().add(message);
                    Platform.runLater(() -> chatContentList.refresh());
                } else if (chatList.getSelectionModel().getSelectedItem() != null && !chatList.getSelectionModel().getSelectedItem().getGroupName().equals(groupName)) {
                    hBox.setMark(true);
                }
                break;
            }
        }
        chatListLock.unlock();
        sortChatList();
    }

    public synchronized void sortChatList() {
        Platform.runLater(() -> {
            chatListLock.lock();
            //timestamp排序，大的在前
            //根据时间排序chatList
            chatList.getItems().sort((o1, o2) -> {
                return o2.getTimestamp().compareTo(o1.getTimestamp());
            });
            chatListLock.unlock();
            chatList.refresh();
        });
    }

    public static void threadClose() {
        System.out.println("thread close normally");
        if (clientSendThread != null)
            clientSendThread.close();
        if (clientReceiveThread != null)
            clientReceiveThread.close();
        Platform.exit();
        System.exit(0);
    }

    public static void threadUnexpectedClose() {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("Warning");
            alert.setContentText("Unexpected Close, please contact with admin");
            alert.show();
        });
        try {
            Thread.sleep(4000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        if (clientSendThread != null)
            clientSendThread.close();
        if (clientReceiveThread != null)
            clientReceiveThread.close();
        Platform.exit();
        System.exit(0);
    }

}
