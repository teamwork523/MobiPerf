// Copyright 2012 RobustNet Lab, University of Michigan. All Rights Reserved.
package com.udpmeasurement;

import java.net.InetAddress;

/**
 * @author Hongyi Yao
 * ClientIdentifier Encapsulate the IP address and the port. It is used as 
 * the key of clientMap to locate corresponding ClientRecord
 */
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

  /* (non-Javadoc)
   * @see java.lang.Object#equals(java.lang.Object)
   * Override equals to ensure its proper behavior as the key
   * of a hash map
   */
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
  
  /* (non-Javadoc)
   * @see java.lang.Object#hashCode()
   * Override hashcode to ensure its proper behavior as the key
   * of a hash map
   */
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
