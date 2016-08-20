package com.monitorjbl.island;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.monitorjbl.island.domain.RPC;
import com.monitorjbl.island.domain.RPCMessage;
import com.monitorjbl.island.domain.RPCResponse;
import net.openhft.chronicle.queue.ChronicleQueue;
import net.openhft.chronicle.queue.ChronicleQueueBuilder;
import net.openhft.chronicle.queue.ExcerptAppender;
import net.openhft.chronicle.queue.ExcerptTailer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Function;

public class IPCBridge implements AutoCloseable {
  private static final Logger log = LoggerFactory.getLogger(IPCBridge.class);

  private final ChronicleQueue txQueue;
  private final ChronicleQueue rxQueue;
  private final int defaultTimeout;

  private final int pollDelay = 1;
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final Map<String, IPCFuture> consumers = new ConcurrentHashMap<>();
  private final Queue<RPC> queue = new ConcurrentLinkedQueue<>();

  private Thread rxThread;
  private Thread txThread;
  private boolean running = true;

  IPCBridge(File txPath, File rxPath, Consumer<RPCMessage> receiveHandler) {
    this.txQueue = ChronicleQueueBuilder.single(txPath.getAbsolutePath()).blockSize(5 * 1024 * 1024).build();
    this.rxQueue = ChronicleQueueBuilder.single(rxPath.getAbsolutePath()).build();

    this.defaultTimeout = 5000;
    this.objectMapper.setSerializationInclusion(Include.NON_NULL);
    init(receiveHandler);
  }

  private void init(Consumer<RPCMessage> receiveHandler) {
    ExcerptAppender appender = txQueue.acquireAppender();
    ExcerptTailer tailer = rxQueue.createTailer();
    rxThread = new Thread(() -> {
      while(running) {
        try {
          tailer.readDocument(wire -> {
            RPC rpc = readRPC(wire.read(() -> "ipc-message").text());
            if(rpc == null) {
              //do nothing
            } else if(rpc instanceof RPCResponse && consumers.containsKey(rpc.getId())) {
              IPCFuture future = consumers.get(rpc.getId());
              RPCResponse reply = (RPCResponse) rpc;
              if(reply.isError()) {
                future.error = true;
                future.reply = reply.getResponse();
              } else {
                future.reply = future.responseFunction.apply(reply);
              }
              future.complete = true;
              log.trace("Handled response for {}", reply.getId());
            } else if(rpc instanceof RPCMessage) {
              receiveHandler.accept((RPCMessage) rpc);
            }
          });
        } catch(Exception e) {
          log.error("Failed to receive", e);
        }
        sleep(pollDelay);
      }
      rxQueue.close();
    }, "receiver");
    txThread = new Thread(() -> {
      while(running) {
        try {
          if(queue.size() > 0) {
            RPC rpc = queue.poll();
            if(rpc instanceof RPCMessage) {
              log.trace("SEND {} ({})", ((RPCMessage) rpc).getType(), rpc.getId());
              appender.writeDocument(w -> w.write("ipc-message").text(toJson(rpc)));
            } else if(rpc instanceof RPCResponse) {
              log.trace("REPLY {} ({})", rpc.getId(), ((RPCResponse) rpc).getResponse());
              appender.writeDocument(w -> w.write("ipc-message").text(toJson(rpc)));
            }
          }
        } catch(Exception e) {
          log.error("Failed to send", e);
        }
        sleep(pollDelay);
      }
      txQueue.close();
    }, "sender");

    rxThread.start();
    txThread.start();
  }

  <T> Future<T> send(RPCMessage message, Function<RPCResponse, T> responseFunction) {
    IPCFuture<T> future = new IPCFuture<T>(message.getId(), responseFunction);
    consumers.put(message.getId(), future);
    queue.add(message);
    return future;
  }

  void reply(RPCResponse response) {
    queue.add(response);
  }

  private String toJson(Object obj) {
    try {
      return objectMapper.writeValueAsString(obj);
    } catch(JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  private RPC readRPC(String text) {
    try {
      if(text == null || !text.startsWith("{")) {
        return null;
      } else {
        return objectMapper.readValue(text, RPC.class);
      }
    } catch(IOException e) {
      throw new RuntimeException(e);
    }
  }

  private static void sleep(long milli) {
    try {
      Thread.sleep(milli);
    } catch(InterruptedException e) {
      e.printStackTrace();
    }
  }

  @Override
  public void close() throws Exception {
    this.running = false;
    this.rxThread.join();
    this.txThread.join();
  }

  private class IPCFuture<T> implements Future<T> {
    private final String id;
    private final Function<RPCResponse, T> responseFunction;
    private boolean cancelled;
    private boolean error;
    private boolean complete;
    private T reply;

    public IPCFuture(String id, Function<RPCResponse, T> responseFunction) {
      this.id = id;
      this.responseFunction = responseFunction;
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
      consumers.remove(id);
      cancelled = true;
      return true;
    }

    @Override
    public boolean isCancelled() {
      return cancelled;
    }

    @Override
    public boolean isDone() {
      return false;
    }

    @Override
    public T get() throws InterruptedException, ExecutionException {
      try {
        for(int i = 0; i < defaultTimeout && !cancelled && !complete; i++) {
          sleep(1);
        }

        if(complete && !error) {
          return reply;
        } else if(complete && error) {
          throw new RuntimeException(reply.toString());
        } else if(cancelled) {
          throw new CancellationException();
        } else {
          throw new RuntimeException(new TimeoutException());
        }
      } finally {
        consumers.remove(id);
      }
    }

    @Override
    public T get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
      try {
        for(int i = 0; i < timeout && !cancelled && !complete; i++) {
          sleep(1);
        }

        if(complete && !error) {
          return reply;
        } else if(complete && error) {
          throw new RuntimeException(reply.toString());
        } else {
          throw new TimeoutException();
        }
      } finally {
        consumers.remove(id);
      }
    }
  }
}
