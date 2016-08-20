package com.monitorjbl.island;

import java.util.HashMap;
import java.util.Map;

/**
 *
 */
public class TestEntry implements IslandEntry {

  Map<Class, Object> impls = new HashMap<Class, Object>() {{
    put(TestIFace.class, new TestIFaceImpl());
  }};

  @Override
  public Object lookupImplementation(Class cls) {
    return impls.get(cls);
  }
}
