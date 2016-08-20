package com.monitorjbl.island.domain;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;

@JsonTypeInfo(use = Id.CLASS, include = JsonTypeInfo.As.PROPERTY)
public interface RPC {
  String getId();
}
