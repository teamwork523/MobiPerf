package com.udpmeasurement;

public class UDPServer extends GlobalFunctionAndConstant {
  /**
   * @param args
   */
  public static void main(String[] args) {
    UDPReceiver deamon;
    int port = 0;
    
    if (args.length == 1) {
      port = Integer.parseInt(args[0]);
      if ( port < 1 || port > 65535 ) {
        logmsg("Invalid port " + port);
        return;
      }
    }
    else {
      port = DEFAULT_PORT;
    }
    System.out.println("UDP Burst server(Ver 2.0) runs on port " + port);
    try {
      deamon = new UDPReceiver(port);
      new Thread(deamon).start();
    } catch (MeasurementError e) {
      logmsg("Error when creating receiver thread: " + e.getMessage());
    }
  }

}
