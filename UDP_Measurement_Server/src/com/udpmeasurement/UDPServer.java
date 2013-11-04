// Copyright 2012 RobustNet Lab, University of Michigan. All Rights Reserved.
package com.udpmeasurement;

/**
 * @author Hongyi Yao
 * Entry point of the UDP burst server
 */
public class UDPServer {
  /**
   * Main function
   * Check the port and create the receiver thread  
   * @param args port used by server 
   */
  public static void main(String[] args) {
    UDPReceiver deamon;
    int port = 0;
    
    if (args.length == 1) {
      port = Integer.parseInt(args[0]);
      if ( port < 1 || port > 65535 ) {
        Config.logmsg("Invalid port " + port);
        return;
      }
    }
    else {
      port = Config.DEFAULT_PORT;
    }
    System.out.println("UDP Burst server(Ver 2.0) runs on port " + port);
    try {
      deamon = new UDPReceiver(port);
      new Thread(deamon).start();
    } catch (MeasurementError e) {
      Config.logmsg("Error when creating receiver thread: " + e.getMessage());
    }
  }

}
