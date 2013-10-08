/**
 * 
 */
package org.eupathdb.websvccommon.wsfplugin;

import org.gusdb.wsf.plugin.WsfPluginException;

/**
 * @author jerric
 * 
 */
public class EuPathServiceException extends WsfPluginException {

  /**
   * 
   */
  private static final long serialVersionUID = 4394578587877062402L;

  /**
   * @param message
   */
  public EuPathServiceException(String message) {
    super(message);
  }

  /**
   * @param cause
   */
  public EuPathServiceException(Throwable cause) {
    super(cause);
  }

  /**
   * @param message
   * @param cause
   */
  public EuPathServiceException(String message, Throwable cause) {
    super(message, cause);
  }

  /**
   * @param message
   * @param cause
   * @param enableSuppression
   * @param writableStackTrace
   */
  public EuPathServiceException(String message, Throwable cause,
      boolean enableSuppression, boolean writableStackTrace) {
    super(message, cause, enableSuppression, writableStackTrace);
  }

}
