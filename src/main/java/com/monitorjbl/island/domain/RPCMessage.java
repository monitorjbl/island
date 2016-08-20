package com.monitorjbl.island.domain;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class RPCMessage implements Serializable, RPC {
  private final String id = UUID.randomUUID().toString();
  private String type;
  private String className;
  private String methodName;
  private List<byte[]> methodParameters = Collections.emptyList();

  public String getId() {
    return id;
  }

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  public String getClassName() {
    return className;
  }

  public void setClassName(String className) {
    this.className = className;
  }

  public String getMethodName() {
    return methodName;
  }

  public void setMethodName(String methodName) {
    this.methodName = methodName;
  }

  public List<byte[]> getMethodParameters() {
    return methodParameters;
  }

  public void setMethodParameters(List<byte[]> methodParameters) {
    this.methodParameters = methodParameters;
  }

  public static RPCMessage ping() {
    RPCMessage msg = new RPCMessage();
    msg.setType(RPCType.PING);
    return msg;
  }

  public static RPCMessage shutdown() {
    RPCMessage msg = new RPCMessage();
    msg.setType(RPCType.SHUTDOWN);
    return msg;
  }

  public static RPCMessage invocation() {
    RPCMessage msg = new RPCMessage();
    msg.setType(RPCType.METHOD_INVOCATION);
    return msg;
  }
}
