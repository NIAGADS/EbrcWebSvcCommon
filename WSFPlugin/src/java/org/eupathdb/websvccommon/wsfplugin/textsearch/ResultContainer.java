package org.eupathdb.websvccommon.wsfplugin.textsearch;

import org.gusdb.wsf.plugin.WsfException;

public interface ResultContainer {

  void addResult(SearchResult result) throws WsfException;
  
  boolean hasResult(String sourceId);
}
