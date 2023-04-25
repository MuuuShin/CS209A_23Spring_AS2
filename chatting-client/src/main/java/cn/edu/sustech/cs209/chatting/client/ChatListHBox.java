package cn.edu.sustech.cs209.chatting.client;

import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;

import java.util.Date;
import java.util.List;


public class ChatListHBox extends HBox {
  private final String groupName;
  private final List<String> members;
  private final Label markLabel;
  private final Label infoLabel;
  private final Label timeLabel;
  private Long timestamp;

  public ChatListHBox(String groupName, List<String> members, Long timestamp, String username) {
    this.members = members;
    this.timestamp = timestamp;
    this.groupName = groupName;

    this.setSpacing(10);
    this.setPrefWidth(200);
    this.setPrefHeight(50);

    this.timeLabel = new Label();
    this.markLabel = new Label();
    this.infoLabel = new Label();
    HBox infoWrapper = new HBox();
    infoWrapper.setSpacing(10);
    infoWrapper.getChildren().addAll(markLabel, infoLabel);
    this.getChildren().addAll(infoWrapper, timeLabel);
    infoWrapper.setAlignment(Pos.CENTER_LEFT);

    this.timeLabel.setAlignment(Pos.CENTER_RIGHT);
    this.timeLabel.setPrefWidth(50);
    this.timeLabel.setPrefHeight(50);

    // 设置HBox的水平增长优先级
    HBox.setHgrow(infoWrapper, Priority.ALWAYS);

    // 设置时间标签的水平增长优先级以及对齐方式
    HBox.setHgrow(timeLabel, Priority.NEVER);
    this.timeLabel.setAlignment(Pos.CENTER_RIGHT);

    //时间戳转换24时制
    Date date = new Date(timestamp);
    this.timeLabel.setText(date.toString().substring(11, 19));

    this.markLabel.setPrefWidth(10);
    this.markLabel.setPrefHeight(10);
    this.markLabel.setStyle("-fx-background-color: #FF0000");
    this.markLabel.setText(" ");

    if (members.size() == 2) {
      if (members.get(0).equals(username)) {
        this.infoLabel.setText(members.get(1));
      } else {
        this.infoLabel.setText(members.get(0));
      }
    } else {
      String info = "";
      if (members.size() <= 3) {
        for (String member : members) {
          info += member + " ";
        }
        info += "(" + members.size() + ")";
      } else {
        //字典序
        members.sort(String::compareTo);
        info = members.get(0) + " " + members.get(1) + " " + members.get(2) + "... (" + members.size() + ")";
      }
      this.infoLabel.setText(info);
    }

  }

  public String getGroupName() {
    return groupName;
  }

  public List<String> getMembers() {
    return members;
  }

  public Long getTimestamp() {
    return timestamp;
  }

  public void setTimestamp(Long timestamp) {
    this.timestamp = timestamp;
    Date date = new Date(timestamp);
    Platform.runLater(() -> this.timeLabel.setText(date.toString().substring(11, 19)));
  }

  public void setMark(Boolean mark) {
    Platform.runLater(() -> {
      if (mark) {
        this.markLabel.setStyle("-fx-background-color: #FF0000");
      } else {
        this.markLabel.setStyle("-fx-background-color: #FFFFFF");
      }
    });
  }

  public String getInfoLabelText() {
    return infoLabel.getText();
  }
}
