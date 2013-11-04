// Copyright 2012 RobustNet Lab, University of Michigan. All Rights Reserved.
package com.udpmeasurement;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;

/**
 * @author Hongyi Yao
 * The thread sends data to the client according to the downlink request packet
 * Therefore, the downlink burst will not block the processing of other uplink 
 * data packet
 */
public class RequestHandler implements Runnable {
  private DatagramSocket socket;
  private ClientIdentifier clientId;
  private ClientRecord  clientRecord;
  
  /**
   * Constructor
   * @param socket the datagram socket created by the receiver thread
   * @param clientId corresponding client identifier
   * @param clientRecord the downlink request
   */
  public RequestHandler(DatagramSocket socket, ClientIdentifier clientId,
                        ClientRecord  clientRecord) {
    this.socket = socket;
    this.clientId = clientId;
    this.clientRecord = clientRecord;
  }

  /**
   * Send a downlink packet according to ClientRecord
   * @throws MeasurementError send failed
   */
  private void sendPacket() throws MeasurementError {
    MeasurementPacket packet = new MeasurementPacket(clientId);
    MeasurementPacket dataPacket = packet;
    dataPacket.type = Config.PKT_DATA;
    dataPacket.burstCount = clientRecord.burstCount;
    dataPacket.packetNum = clientRecord.packetReceived;
    dataPacket.timestamp = System.currentTimeMillis(); 
    dataPacket.packetSize = clientRecord.packetSize;

    byte[] sendBuffer = packet.getByteArray();
    DatagramPacket sendPacket = new DatagramPacket(
      sendBuffer, sendBuffer.length, clientId.addr, clientId.port); 

    try {
      socket.send(sendPacket);
    } catch (IOException e) {
      throw new MeasurementError(
        "Fail to send UDP packet to " + clientId.toString());
    }

    Config.logmsg("Sent response to " + clientId.toString() + " type: PKT_DATA b:" +
        packet.burstCount + " p:" + packet.packetNum + " i:" +
        packet.intervalNum + " j:" + packet.timestamp + " s:" +
        packet.packetSize);
  }
  
  /* (non-Javadoc)
   * @see java.lang.Runnable#run()
   * send n=burstCount downlink packets with the interval of udpInterval
   */
  @Override
  public void run() {
    for ( int i = 0; i < clientRecord.burstCount; i++ ) {
      clientRecord.packetReceived = i;
      try {
        sendPacket();
      } catch (MeasurementError e) {
        Config.logmsg("Error processing message: " + e.getMessage());
        break;
      }

      try {
        Thread.sleep(clientRecord.udpInterval);
      } catch (InterruptedException e) {
        Config.logmsg("sleep is interrupted: " + e.getMessage());
      }
    }
  }

}
