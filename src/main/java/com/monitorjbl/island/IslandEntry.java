package com.monitorjbl.island;

import com.monitorjbl.island.domain.RPCMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.util.stream.Collectors.toList;

public interface IslandEntry {
  Logger log = LoggerFactory.getLogger(IslandEntry.class);
  Map<MethodEntry, Method> methodCache = new HashMap<>();

  default Object accept(RPCMessage message) {
    try {
      Class cls = Class.forName(message.getClassName());
      Object handler = lookupImplementation(cls);
      Object[] args = message.getMethodParameters().stream()
          .map(b -> Serializer.deserializeObject(new ByteArrayInputStream(b)))
          .toArray();
      List<Class> params = Arrays.stream(args)
          .map(Object::getClass)
          .collect(toList());

      Method m = findMethod(new MethodEntry(cls, message.getMethodName(), params));
      log.trace("Invoking method {}.{}({})", message.getClassName(), message.getMethodName(), params);
      return m.invoke(handler, args);
    } catch(ClassNotFoundException | InvocationTargetException | IllegalAccessException e) {
      throw new RuntimeException(e);
    }
  }

  default void startup() {}

  default void shutdown() {}

  default <E> E lookupImplementation(Class<E> cls) { return null; }

  static Method findMethod(MethodEntry entry) {
    if(methodCache.containsKey(entry)) {
      return methodCache.get(entry);
    }

    for(Method m : entry.cls.getMethods()) {
      Parameter[] methodParams = m.getParameters();
      if(m.getName().equals(entry.methodName) && methodParams.length == entry.parameters.size()) {
        boolean found = true;
        for(int i = 0; i < entry.parameters.size(); i++) {
          found &= unbox(entry.parameters.get(i)).equals(unbox(methodParams[i].getType()));
        }
        if(found) {
          return m;
        }
      }
    }
    throw new RuntimeException("Could not find method " + entry.cls + "." + entry.methodName + "(" + entry.parameters + ")");
  }

  static Class unbox(Class a) {
    if(a.equals(int.class)) {
      return Integer.class;
    } else if(a.equals(long.class)) {
      return Long.class;
    } else if(a.equals(double.class)) {
      return Double.class;
    } else if(a.equals(float.class)) {
      return Float.class;
    } else if(a.equals(boolean.class)) {
      return Boolean.class;
    } else if(a.equals(short.class)) {
      return Short.class;
    } else if(a.equals(char.class)) {
      return Character.class;
    } else {
      return a;
    }
  }

  class MethodEntry {
    Class cls;
    String methodName;
    List<Class> parameters;

    public MethodEntry(Class cls, String methodName, List<Class> parameters) {
      this.cls = cls;
      this.methodName = methodName;
      this.parameters = parameters;
    }

    @Override
    public boolean equals(Object o) {
      if(this == o) return true;
      if(o == null || getClass() != o.getClass()) return false;

      MethodEntry that = (MethodEntry) o;

      if(cls != null ? !cls.equals(that.cls) : that.cls != null) return false;
      if(methodName != null ? !methodName.equals(that.methodName) : that.methodName != null) return false;
      return parameters != null ? parameters.equals(that.parameters) : that.parameters == null;

    }

    @Override
    public int hashCode() {
      int result = cls != null ? cls.hashCode() : 0;
      result = 31 * result + (methodName != null ? methodName.hashCode() : 0);
      result = 31 * result + (parameters != null ? parameters.hashCode() : 0);
      return result;
    }
  }
}
