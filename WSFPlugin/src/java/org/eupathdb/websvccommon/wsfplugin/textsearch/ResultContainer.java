package org.eupathdb.websvccommon.wsfplugin.textsearch;

import org.gusdb.wsf.plugin.WsfPluginException;

public interface ResultContainer {

  void addResult(SearchResult result) throws WsfPluginException;
  
  boolean hasResult(String sourceId);
}
