package com.udpmeasurement;

import java.net.InetAddress;

public class ClientIdentifier {
  InetAddress addr;
  int port;

  public ClientIdentifier (InetAddress addr, int port) {
    this.addr = addr;
    this.port = port;
  }

  @Override
  public String toString() {
    return addr.toString() + "(" + port + ")";
  }

  @Override
  public boolean equals(Object another) {
    // null protection
    if ( another == null ) {
      return false;
    }
    if ( another instanceof ClientIdentifier ) {
      ClientIdentifier anotherId = (ClientIdentifier)another;
      if ( this.addr.equals(anotherId.addr) && this.port == anotherId.port ) {
        return true;
      }
      else {
        return false;
      }
    }
    else {
      // not a ClientIdentifier
      return false;
    }
  }
  
  @Override
  public int hashCode() {
    // pack the address
    byte[] rawByte = addr.getAddress();
    int rawAddr = 0;
    for ( int i = 0; i < rawByte.length; i++ ) {
      rawAddr <<= 8;
      rawAddr |= ((int)rawByte[i] & 0xff);  // convert to unsigned number
    }
    rawAddr += port;
    
    return rawAddr;
  }
}
