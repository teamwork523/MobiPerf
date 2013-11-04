// Copyright 2012 RobustNet Lab, University of Michigan. All Rights Reserved.
package com.udpmeasurement;

/**
 * Error raised when a measurement fails.
 */
@SuppressWarnings("serial")
public class MeasurementError extends Exception {
  public MeasurementError(String reason) {
    super(reason);
  }
  public MeasurementError(String reason, Throwable e) {
    super(reason, e);
  }
}
