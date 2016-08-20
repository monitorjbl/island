package com.monitorjbl.island;

import com.monitorjbl.island.domain.RPCMessage;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;

import static java.util.function.Function.identity;

public class Island implements AutoCloseable {
  private static final Logger log = LoggerFactory.getLogger(Island.class);

  private final String classpath;
  private final String javaHome;
  private final String queueBasePath;
  private final String entryClass;

  private IPCBridge bridge;
  private Process process;
  private boolean running;

  public Island(String classpath, String javaHome, String queueBasePath, String entryClass) {
    this.classpath = classpath;
    this.javaHome = javaHome;
    this.queueBasePath = queueBasePath;
    this.entryClass = entryClass;
  }

  public void startup() {
    try {
      File parentPath = new File(queueBasePath + "/parent");
      File childPath = new File(queueBasePath + "/child");

      FileUtils.deleteDirectory(parentPath);
      FileUtils.deleteDirectory(childPath);
      parentPath.mkdirs();
      childPath.mkdirs();
      bridge = new IPCBridge(parentPath, childPath, msg -> { });

      ProcessBuilder pb = new ProcessBuilder();
      pb.command(
          javaHome + "/bin/java", "-cp",
          classpath, IslandChild.class.getCanonicalName(),
          queueBasePath,
          entryClass);
      pb.inheritIO();
      pb.redirectErrorStream();
      process = pb.start();

      log.info("Child [PID:{}] starting up", getProcessPID());
      ping();

      running = true;
      new Thread(() -> {
        while(running) {
          ping();
          try {
            Thread.sleep(5000);
          } catch(InterruptedException e) {
            e.printStackTrace();
          }
        }
      }, "ping").start();

    } catch(IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public void shutdown() {
    log.info("Child [PID:{}] shutting down", getProcessPID());
    bridge.send(RPCMessage.shutdown(), identity());
    running = false;
    process.destroy();
    process.exitValue();

    try {
      File parentPath = new File(queueBasePath + "/parent");
      File childPath = new File(queueBasePath + "/child");
      FileUtils.deleteDirectory(parentPath);
      FileUtils.deleteDirectory(childPath);
    } catch(IOException e) {
      log.error("Could not clean up queue paths", e);
    }
  }

  public void ping() {
    log.trace("Ping");
    bridge.send(RPCMessage.ping(), identity());
  }

  @SuppressWarnings("unchecked")
  public <E> E generateProxy(Class<E> iface) {
    return (E) Proxy.newProxyInstance(
        iface.getClassLoader(),
        new Class<?>[]{iface},
        new ProxyHandler(bridge));
  }

  public long getProcessPID() {
    long pid = -1;
    try {
      if(process.getClass().getName().equals("java.lang.UNIXProcess")) {
        Field f = process.getClass().getDeclaredField("pid");
        f.setAccessible(true);
        pid = f.getLong(process);
        f.setAccessible(false);
      }
    } catch(IllegalAccessException | NoSuchFieldException e) {
      log.error(e.getMessage(), e);
    }
    return pid;
  }

  @Override
  public void close() {
    shutdown();
  }

  public static IslandBuilder builder() {
    return new IslandBuilder();
  }

  public static class IslandBuilder {
    private String classpath = System.getProperty("java.class.path");
    private String javaHome = System.getProperty("java.home");
    private String entryClass = DefaultIslandEntry.class.getCanonicalName();
    private String queueBasePath;

    public IslandBuilder classpath(String classpath) {
      this.classpath = classpath;
      return this;
    }

    public IslandBuilder javaHome(String javaHome) {
      this.javaHome = javaHome;
      return this;
    }

    public IslandBuilder entryClass(Class entryClass) {
      this.entryClass = entryClass.getCanonicalName();
      return this;
    }

    public IslandBuilder queueBasePath(String queueBasePath) {
      this.queueBasePath = queueBasePath;
      return this;
    }

    public Island create() {
      if(entryClass == null) { throw new IllegalStateException("entryClass cannot be null"); }
      if(queueBasePath == null) {
        queueBasePath = new File(System.getProperty("java.io.tmpdir")).getAbsolutePath();
      }

      return new Island(classpath, javaHome, queueBasePath, entryClass);
    }

    public Island createAndStartup() {
      Island island = create();
      island.startup();
      return island;
    }
  }
}
