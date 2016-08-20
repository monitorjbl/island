# Island

Helps to isolate particular parts of your codebase at runtime by starting them up in a separate JVM instance and performing local IPC to do remote method invocations on them. Currently requires interfaces to abstract the method invocations due to reliance on Proxies, which makes it a bit more verbose that I'd like. Could probably switch to using Javassist or Mockito though, the only requirement is a shim version of the class to be executed.

Example:

java```
  // Common interface to allow for IPC
  public interface TestFace {
    String doSomething();
    int addOne(int val);
  }

  // Implementation that will be invoked on the child JVM
  public class TestIFaceImpl implements TestIFace {
    @Override
    public String doSomething() {
      return "something";
    }
    @Override
    public int addOne(int val) {
      return val + 1;
    }
  }

  // Class that will be used as the entry point in the child JVM
  public class TestEntry implements IslandEntry {
    Map<Class, Object> impls = new HashMap<Class, Object>() {{
      put(TestIFace.class, new TestIFaceImpl());
    }};

    @Override
    public Object lookupImplementation(Class cls) {
      return impls.get(cls);
    }
  }

  // Test it out!
  public class Test{
    public static void main(String[] args){
      try(Island island = Island.builder()
          .entryClass(TestEntry.class)
          .createAndStartup()) {

        TestIFace iface = island.generateProxy(TestIFace.class);
        assertEquals("something", iface.doSomething());
        assertEquals(2, iface.addOne(1));
      }
    }
  }
```