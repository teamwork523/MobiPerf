// Copyright 2012 RobustNet Lab, University of Michigan. All Rights Reserved.
package com.udpmeasurement;

import java.util.ArrayList;

/**
 * @author Hongyi Yao
 * ClientRecord save the information and status of a UDP burst
 * , both uplink and downlink
 */
public class ClientRecord {
  public int seq;
  public int burstCount;
  public int packetReceived;
  public int packetSize;
  public long lastTimestamp;
  public int udpInterval;
  public ArrayList<Integer> receivedNumberList;
  public ArrayList<Long> offsetedDelayList;  

  public ClientRecord() {
    receivedNumberList = new ArrayList<Integer>();
    offsetedDelayList = new ArrayList<Long>();
  }

  /**
   * Leverage the combine process during merge-sort to calculate inversion
   * number
   * @param packetNumList the entire array to be processed
   * @param start start point of the first array
   * @param mid the next to the end point of the first array 
   *            and the start point of the second one
   * @param end the next to the end point of the second array
   * @return the inversion number between two arrays
   */
  private int combine(Integer[] packetNumList, int start, int mid, int end) {
    int inversionCounter = 0;
    int[] tmp = new int[end - start + 1];
    int pf = start;
    int ps = mid + 1;
    int pt = 0;// the number of sorted elements
    while (pf <= mid && ps <= end)
      if (packetNumList[pf] > packetNumList[ps]) {
        for (int t = pf; t <= mid; t++)
          inversionCounter++;
        tmp[pt++] = packetNumList[ps++];
      } else {
        tmp[pt++] = packetNumList[pf++];
      }
    while (pf <= mid)
      tmp[pt++] = packetNumList[pf++];
    while (ps <= end)
      tmp[pt++] = packetNumList[ps++];
    for (int i = start; i <= end; i++)
      packetNumList[i] = tmp[i - start];
    return inversionCounter;
  }

  /**
   * Recursively accumulate the inversion number  
   * @param packetNumList the entire array to be processed
   * @param start start point of the current array
   * @param end the next to end point of the current array
   * @return the inversion number of current array
   */
  private int merge(Integer[] packetNumList, int start, int end) {
    if (start < end) {
      int mid = (start + end) / 2;
      int invLeft = merge(packetNumList, start, mid);
      int invRight = merge(packetNumList, mid + 1, end);
      int invThis = combine(packetNumList, start, mid, end);
      return invLeft + invRight + invThis;
    }
    else {
      return 0;
    }
  }

  /**
   * Get inversion number as the metric of UDP out-of-order count
   * @return the inversion number of the current UDP burst
   */
  public int calculateInversionNumber() {
    Integer[] base = new Integer[receivedNumberList.size()];
    receivedNumberList.toArray(base);

    return merge(base, 0, base.length - 1);
  }

  /**
   * Calculate jitter as the standard deviation of one-way delays. 
   * Clock sync between client and server is not required since the clock
   * offset will be cancelled out during the calculation process
   * @return the jitter of UDP burst
   */
  public long calculateJitter() {
    int size = offsetedDelayList.size();
    if ( size > 1 ) {
      double offsetedDelay_mean = 0;
      for ( long offsetedDelay : offsetedDelayList ) {
        offsetedDelay_mean += (double)offsetedDelay / size;
      }

      double jitter = 0;
      for ( long offsetedDelay : offsetedDelayList ) {
        jitter += ((double)offsetedDelay - offsetedDelay_mean)
            * ((double)offsetedDelay - offsetedDelay_mean)  / (size - 1);
      }
      jitter = Math.sqrt(jitter);
      
      return (long)jitter;
    }
    else {
      return 0;
    }
  }
}
