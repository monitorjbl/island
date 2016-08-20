package com.monitorjbl.island.domain;

import java.io.Serializable;

public class RPCResponse implements Serializable, RPC {
  private String id;
  private boolean error;
  private Object response;

  public RPCResponse() { }

  public RPCResponse(String id, boolean error, Object response) {
    this.id = id;
    this.error = error;
    this.response = response;
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public boolean isError() {
    return error;
  }

  public void setError(boolean error) {
    this.error = error;
  }

  public Object getResponse() {
    return response;
  }

  public void setResponse(Object response) {
    this.response = response;
  }
}
