package com.monitorjbl.island;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.UncheckedIOException;

public class Serializer {
  public static byte[] serializeObject(Object obj) {
    try {
      ByteArrayOutputStream bos = new ByteArrayOutputStream();
      ObjectOutputStream obj_out = new ObjectOutputStream(bos);
      obj_out.writeObject(obj);
      return bos.toByteArray();
    } catch(IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public static <E> E deserializeObject(InputStream inputStream) {
    try {
      ObjectInputStream obj_in = new ObjectInputStream(inputStream);
      return (E) obj_in.readObject();
    } catch(ClassNotFoundException | IOException e) {
      throw new RuntimeException(e);
    }
  }
}
