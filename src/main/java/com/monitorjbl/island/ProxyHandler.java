package com.monitorjbl.island;

import com.monitorjbl.island.domain.RPCMessage;
import com.monitorjbl.island.domain.RPCResponse;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.Arrays;

import static java.util.stream.Collectors.toList;

public class ProxyHandler implements InvocationHandler {

  private final IPCBridge bridge;

  public ProxyHandler(IPCBridge bridge) {
    this.bridge = bridge;
  }

  @Override
  public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
    RPCMessage msg = RPCMessage.invocation();
    msg.setClassName(method.getDeclaringClass().getCanonicalName());
    msg.setMethodName(method.getName());
    if(args != null) {
      msg.setMethodParameters(Arrays.stream(args)
          .map(Serializer::serializeObject)
          .collect(toList()));
    }
    return bridge.send(msg, RPCResponse::getResponse).get();
  }

}
