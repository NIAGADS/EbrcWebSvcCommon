package org.eupathdb.websvccommon.wsfplugin.textsearch;

import org.gusdb.wsf.plugin.PluginModelException;
import org.gusdb.wsf.plugin.PluginUserException;

public interface ResultContainer {

  void addResult(SearchResult result) throws PluginModelException, PluginUserException;
  
  boolean hasResult(String sourceId);
}
