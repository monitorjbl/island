package com.monitorjbl.island;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class IslandTest {

  @Test
  public void test() throws Exception {
    try(Island island = Island.builder()
        .entryClass(TestEntry.class)
        .queueBasePath("target/queues")
        .createAndStartup()) {

      TestIFace iface = island.generateProxy(TestIFace.class);
      for(int i = 0; i < 10; i++) {
        assertEquals("something", iface.doSomething());
        assertEquals(2, iface.addOne(1));
      }
    }
  }

}
