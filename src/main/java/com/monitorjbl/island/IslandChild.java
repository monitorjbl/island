package com.monitorjbl.island;

import com.monitorjbl.island.domain.RPCMessage;
import com.monitorjbl.island.domain.RPCResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.monitorjbl.island.domain.RPCType.METHOD_INVOCATION;
import static com.monitorjbl.island.domain.RPCType.PING;
import static com.monitorjbl.island.domain.RPCType.SHUTDOWN;

public class IslandChild {
  private static final Logger log = LoggerFactory.getLogger(IslandChild.class);
  private final IPCBridge bridge;
  private final IslandEntry entry;
  private final ExecutorService threadPool = Executors.newFixedThreadPool(10);
  private final int timeout = 10000;

  private boolean running = true;
  private long lastPing = System.currentTimeMillis();

  private IslandChild(String queueBasePath, String entryClass) throws Exception {
    File parentPath = new File(queueBasePath + "/parent");
    File childPath = new File(queueBasePath + "/child");
    this.bridge = new IPCBridge(childPath, parentPath, msg -> dispatch(msg));
    this.entry = initClass(entryClass);
    new Thread(() -> {
      while(running) {
        if(System.currentTimeMillis() - lastPing > timeout) {
          log.error("No pings seen in " + timeout + "ms, assuming parent is dead and becoming batman");
          shutdown();
        }
        sleep(1000);
      }
    }, "ping").start();
  }

  private void startup() {
    entry.startup();
  }

  private void listen() {
    while(running) {
      sleep(5);
    }
  }

  private void dispatch(RPCMessage message) {
    if(message != null) {
      switch(message.getType().trim()) {
        case METHOD_INVOCATION:
          threadPool.submit(() -> bridge.reply(new RPCResponse(message.getId(), false, methodInvocation(message))));
          break;
        case PING:
          bridge.reply(new RPCResponse(message.getId(), false, pong()));
          break;
        case SHUTDOWN:
          bridge.reply(new RPCResponse(message.getId(), false, shutdown()));
          break;
        default:
          bridge.reply(new RPCResponse(message.getId(), true, "Could not handle '" + message.getType() + "'"));
      }
    }
  }

  private Object methodInvocation(RPCMessage message) {
    return entry.accept(message);
  }

  private Object pong() {
    lastPing = System.currentTimeMillis();
    log.trace("Pong");
    return "pong";
  }

  private Object shutdown() {
    log.info("Shutting down");
    entry.shutdown();
    running = false;
    return null;
  }

  @SuppressWarnings("unchecked")
  private static IslandEntry initClass(String entryClass) throws ClassNotFoundException, IllegalAccessException, InstantiationException {
    Class<IslandEntry> cls = (Class<IslandEntry>) Class.forName(entryClass);
    return cls.newInstance();
  }

  private static void sleep(long milli) {
    try {
      Thread.sleep(milli);
    } catch(InterruptedException e) {
      e.printStackTrace();
    }
  }

  public static void main(String[] args) throws Exception {
    IslandChild self = new IslandChild(args[0], args[1]);
    self.startup();

    self.listen();
    System.exit(0);
  }
}
