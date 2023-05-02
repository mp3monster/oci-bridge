package com.oracle.ocibridge;

interface ConnectionBaseInterface {

  void connect();

  boolean connected();

  MessageList getMessages();

  String getConnectionName();

  void sendMessages(MessageList messages);

  void printProperties();

  /**
   * @param target
   */
  void setTarget(ConnectionBaseInterface target);

  void shutdown();

}