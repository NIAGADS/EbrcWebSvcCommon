package org.eupathdb.websvccommon.wsfplugin.textsearch;

import org.gusdb.wsf.common.WsfException;

public interface ResultContainer {

  void addResult(SearchResult result) throws WsfException;
  
  boolean hasResult(String sourceId);
}
