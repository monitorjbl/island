package com.monitorjbl.island;

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
