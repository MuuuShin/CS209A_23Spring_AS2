package cn.edu.sustech.cs209.chatting.common;

import java.io.Serializable;

public class SerFile implements Serializable {
  private static final long serialVersionUID = 1L;
  private String fileName;
  private byte[] fileContent;

  private String groupName;

  public SerFile(String fileName, String groupName, byte[] fileContent) {
    this.fileName = fileName;
    this.fileContent = fileContent;
    this.groupName = groupName;
  }

  public byte[] getFileContent() {
    return fileContent;
  }

  public String getFileName() {
    return fileName;
  }

  public String getGroupName() {
    return groupName;
  }
}
