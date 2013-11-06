// Copyright 2012 RobustNet Lab, University of Michigan. All Rights Reserved.
package com.udpmeasurement;

import java.sql.Date;
import java.text.SimpleDateFormat;

/**
 * @author Hongyi Yao
 * Provide the definition of constant and logging function
 * which will probabaly be used in other classes
 */
public class Config {
  //Todo(Hongyi): Arbitrary port number, need further discuss
  public static final int DEFAULT_PORT = 31341;
  // Larger then normal Ethernet MTU, leave enough margin
  public static final int BUFSIZE = 1500;
  // Min packet size =  (int type) + (int burstCount) + (int packetNum) +
  //                    (int intervalNum) + (long timestamp) +
  //                    (int packetSize) + (int seq) + (int udpInterval)
  //                 =  36
  public static final int MIN_PACKETSIZE = 36;
  // Leave enough margin for min MTU in the link and IP options
  public static final int MAX_PACKETSIZE = 512;
  // Todo(Hongyi): Arbitrary value, need further discuss
  public static final int DEFAULT_UDP_PACKET_SIZE = 100;
  // Todo(Hongyi): Arbitrary timeout value, need further discuss
  public static final int DEFAULT_TIMEOUT = 3000;
  // Todo(Hongyi): Arbitrary value, need further discuss
  public static final int MAX_BURSTCOUNT = 100;

  public static final int PKT_ERROR = 1;
  public static final int PKT_RESPONSE = 2;
  public static final int PKT_DATA = 3;
  public static final int PKT_REQUEST = 4;

  /**
   * print a log message with the current time and extra information 
   * @param a extra information to be logged
   */
  public static void logmsg(String a) {
    long timenow = System.currentTimeMillis();
    Date date = new Date(timenow);
    SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd kk:mm:ss");
    System.out.println(df.format(date) + " " + a);
  }
}
