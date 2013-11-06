// Copyright 2012 RobustNet Lab, University of Michigan. All Rights Reserved.
package com.udpmeasurement;

import java.io.*;

/**
 * @author Hongyi Yao
 * A helper structure for packing and unpacking network message
 */
public class MeasurementPacket {
  public ClientIdentifier clientId;
  // the field in network message
  public int type;
  public int burstCount;
  public int packetNum;
  public int intervalNum;
  public long timestamp;
  public int packetSize;
  public int seq;
  public int udpInterval;

  /**
   * Create an empty structure
   * @param cliId corresponding client identifier
   */
  public MeasurementPacket(ClientIdentifier cliId) {
    this.clientId = cliId;
  }
  
  /**
   * Unpack received message and fill the structure
   * @param cliId corresponding client identifier
   * @param rawdata network message
   * @throws MeasurementError stream reader failed
   */
  public MeasurementPacket(ClientIdentifier cliId, byte[] rawdata)
      throws MeasurementError{
    this.clientId = cliId;

    ByteArrayInputStream byteIn = new ByteArrayInputStream(rawdata);
    DataInputStream dataIn = new DataInputStream(byteIn);
    
    try {
      type = dataIn.readInt();
      burstCount = dataIn.readInt();
      packetNum  = dataIn.readInt();
      intervalNum = dataIn.readInt();
      timestamp = dataIn.readLong();
      packetSize = dataIn.readInt();
      seq = dataIn.readInt();
      udpInterval = dataIn.readInt();
    } catch (IOException e) {
      throw new MeasurementError("Fetch payload failed! " + e.getMessage());
    }
    
    try {
      byteIn.close();
    } catch (IOException e) {
      throw new MeasurementError("Error closing inputstream!");
    }
  }
  
  /**
   * Pack the structure to the network message
   * @return the network message in byte[]
   * @throws MeasurementError stream writer failed
   */
  public byte[] getByteArray() throws MeasurementError {

    ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
    DataOutputStream dataOut = new DataOutputStream(byteOut);
    
    try {
      dataOut.writeInt(type);
      dataOut.writeInt(burstCount);
      dataOut.writeInt(packetNum);
      dataOut.writeInt(intervalNum);
      dataOut.writeLong(timestamp);
      dataOut.writeInt(packetSize);
      dataOut.writeInt(seq);
      dataOut.writeInt(udpInterval);
    } catch (IOException e) {
      throw new MeasurementError("Create rawpacket failed! " + e.getMessage());
    }
    
    byte[] rawPacket = byteOut.toByteArray();
    
    try {
      byteOut.close();
    } catch (IOException e) {
      throw new MeasurementError("Error closing outputstream!");
    }
    return rawPacket; 
  }

}
