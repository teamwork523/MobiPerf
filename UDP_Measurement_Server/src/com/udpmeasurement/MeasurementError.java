package com.udpmeasurement;

/**
 * Error raised when a measurement fails.
 */
public class MeasurementError extends Exception {
  /**
   * 
   */
  private static final long serialVersionUID = 4195653891242808967L;
  
  public MeasurementError(String reason) {
    super(reason);
  }
  public MeasurementError(String reason, Throwable e) {
    super(reason, e);
  }
}
