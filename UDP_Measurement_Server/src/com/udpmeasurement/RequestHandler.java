package com.udpmeasurement;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;

public class RequestHandler extends GlobalFunctionAndConstant implements
    Runnable {
  private DatagramSocket socket;
  private ClientIdentifier clientId;
  private ClientRecord  clientRecord;
  
  public RequestHandler(DatagramSocket socket, ClientIdentifier clientId, ClientRecord  clientRecord) {
    this.socket = socket;
    this.clientId = clientId;
    this.clientRecord = clientRecord;
  }

  private void sendPacket() throws MeasurementError {
    MeasurementPacket packet = new MeasurementPacket(clientId);
    MeasurementPacket dataPacket = packet;
    dataPacket.type = PKT_DATA;
    dataPacket.burstCount = clientRecord.burstCount;
    dataPacket.packetNum = clientRecord.packetReceived;
    dataPacket.timestamp = System.currentTimeMillis(); 
    dataPacket.packetSize = clientRecord.packetSize;

    byte[] sendBuffer = packet.getByteArray();
    DatagramPacket sendPacket = new DatagramPacket(sendBuffer, sendBuffer.length, clientId.addr, clientId.port); 

    try {
      socket.send(sendPacket);
    } catch (IOException e) {
      throw new MeasurementError("Fail to send UDP packet to " + clientId.toString());
    }

    logmsg("Sent response to " + clientId.toString() + " type: PKT_DATA b:" + packet.burstCount
        + " p:" + packet.packetNum + " i:" + packet.intervalNum + " j:" + packet.timestamp
        + " s:" + packet.packetSize);
  }
  
  @Override
  public void run() {
    for ( int i = 0; i < clientRecord.burstCount; i++ ) {
      clientRecord.packetReceived = i;
      try {
        sendPacket();
      } catch (MeasurementError e) {
        logmsg("Error processing message: " + e.getMessage());
        break;
      }

      try {
        Thread.sleep(clientRecord.udpInterval);
      } catch (InterruptedException e) {
        logmsg("sleep is interrupted: " + e.getMessage());
      }
    }
  }

}
