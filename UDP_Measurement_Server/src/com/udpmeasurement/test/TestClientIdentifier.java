// Copyright 2012 RobustNet Lab, University of Michigan. All Rights Reserved.
package com.udpmeasurement.test;

import static org.junit.Assert.*;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashSet;

import org.junit.Test;

import com.udpmeasurement.ClientIdentifier;

/**
 * @author Hongyi Yao
 * Unit test for clientIndentifier.java, validate equals and hashcode
 * to see whether ClientIdentifier works properly as a key of HashMap
 */
public class TestClientIdentifier {
  /**
   * Test identical objects
   * @throws UnknownHostException
   */
  @Test
  public void TestEqual() throws UnknownHostException {
    InetAddress addr = InetAddress.getByName("192.168.1.1");
    int port = 1234;
    ClientIdentifier id1 = new ClientIdentifier(addr, port);
    ClientIdentifier id2 = new ClientIdentifier(addr, port);
    assertEquals("id1 must be identical with id2", id1, id2);
  }
  

  /**
   * Test equals(null)
   * @throws UnknownHostException
   */
  @Test
  public void TestEqualSingleNull() throws UnknownHostException {
    InetAddress addr = InetAddress.getByName("192.168.1.1");
    int port = 1234;
    ClientIdentifier id1 = new ClientIdentifier(addr, port);
    ClientIdentifier id2 = null;
    assertFalse("id1 must not be identical with id2", id1.equals(id2));
  }
  
  /**
   * Test whether HashSet can contain a same identifier 
   * @throws UnknownHostException
   */
  @Test
  public void TestHashSet() throws UnknownHostException {
    HashSet<ClientIdentifier> set = new HashSet<ClientIdentifier>();

    InetAddress addr = InetAddress.getByName("192.168.1.1");
    int port = 1234;
    ClientIdentifier id1 = new ClientIdentifier(addr, port);
    ClientIdentifier id2 = new ClientIdentifier(addr, port);
    
    set.add(id1);
    assertTrue("id2 should be in the set", set.contains(id2));
  }

  /**
   * Test whether hash collision affects correctness
   * @throws UnknownHostException
   */
  @Test
  public void TestHashSetCollision() throws UnknownHostException {
    HashSet<ClientIdentifier> set = new HashSet<ClientIdentifier>();

    InetAddress addr = InetAddress.getByName("192.168.1.1");
    int port = 1234;
    ClientIdentifier id1 = new ClientIdentifier(addr, port);
    InetAddress addr2 = InetAddress.getByName("192.168.1.2");
    int port2 = 1233;
    ClientIdentifier id2 = new ClientIdentifier(addr2, port2);
    
    set.add(id1);
    assertFalse("Though id2.hashcode == id1.hashcode, id2 should not be in the set",
      set.contains(id2));
  }
}
