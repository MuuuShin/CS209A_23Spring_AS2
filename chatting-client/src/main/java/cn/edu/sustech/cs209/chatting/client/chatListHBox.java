package cn.edu.sustech.cs209.chatting.client;

import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;

import java.util.List;

public class chatListHBox extends HBox {
    private String groupName;
    private List<String> members;
    private Long timestamp;
    private Label markLabel;
    private Label infoLabel;

    public String getGroupName() {
        return groupName;
    }

    public List<String> getMembers() {
        return members;
    }

    public Long getTimestamp() {
        return timestamp;
    }

    public chatListHBox(String groupName, List<String> members, Long timestamp, String username) {
        this.groupName = groupName;
        this.members = members;
        this.timestamp = timestamp;

        this.setSpacing(10);
        this.setPrefWidth(200);
        this.setPrefHeight(50);
        this.setAlignment(Pos.CENTER_LEFT);
        this.markLabel = new Label();
        this.markLabel.setPrefWidth(10);
        this.markLabel.setPrefHeight(10);
        this.markLabel.setStyle("-fx-background-color: #FF0000");
        this.markLabel.setText(" ");
        this.infoLabel = new Label();
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
        }
        this.getChildren().addAll(markLabel, infoLabel);
    }

    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
        Platform.runLater(() -> {
            this.infoLabel.setText(groupName);
        });
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
