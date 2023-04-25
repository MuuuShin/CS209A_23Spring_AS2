package cn.edu.sustech.cs209.chatting.common;

import java.util.ArrayList;
import java.util.List;

/**
 * Only used for authorization.
 */
public class User {
  private final long userID;
  private final String username;
  private final String password;

  private final List<Long> groupIDs;

  public User(long userID, String username, String password) {
    this.userID = userID;
    this.username = username;
    this.password = password;
    this.groupIDs = new ArrayList<>();
  }

  public void addGroupID(long groupID) {
    groupIDs.add(groupID);
  }

  public long getUserID() {
    return userID;
  }

  public String getUsername() {
    return username;
  }

  public String getPassword() {
    return password;
  }
}
