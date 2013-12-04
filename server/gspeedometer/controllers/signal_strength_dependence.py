#!/usr/bin/python


import math
import sys
import logging

from google.appengine.ext import webapp
from google.appengine.ext.webapp.util import run_wsgi_app
from gspeedometer import wsgi
from gspeedometer import model
from google.appengine.ext import db
from gspeedometer.helpers import util


# Step 1: get model. 
# Step 2: get all values.  FIt into model.  Create

class Segment:
    all_segments = {}

    def __init__(self, start_val):
      self.start = start_val
      self.large_avg = None
      self.small_avg = None

    def add_small(self, small, start_val):
      logging.info("adding small")
      if start_val not in Segment.all_segments:
        Segment.all_segments[start_val] = Segment(start_val)
      Segment.all_segments[start_val].small_avg = small

    def add_large(self, large, start_val):
      logging.info("adding large")
      if start_val not in Segment.all_segments:
        Segment.all_segments[start_val] = Segment(start_val)
      Segment.all_segments[start_val].large_avg = large

    def normalize(self, n, is_small):
      if is_small:
        val = self.small_avg
      else:
        val = self.large_avg
      if n < 0 or n > 7000:
        n = 7000 
      if val == None:
        return n
      else:
        return n - val
        
 
class SignalStrengthDependence(webapp.RequestHandler): 

 
  def getAllDevices(self):
    ''' Get a list of all IDs that either do not have a model, or have more recent data than the most recent model.
      TODO: probably a more efficient way of doing the lookup.'''
    query = model.RRCStateModel.all()
    #query.filter("label=", "anomalous")
    ids_to_check = {}
    for row in query:
      if row.phone_id in ids_to_check:
        ids_to_check[row.phone_id].add(row.network_type)
      else:
        ids_to_check[row.phone_id] = set([row.network_type])

    l = []
    for k, v in ids_to_check.iteritems():
      for i in v:
        l.append([k, i])
  #  ids_to_check = ["3515650530c171a699c056f9abb0e2156c00b77730866ecc"]
    return l 

  def getModel(self, entry_filter):
    query = model.RRCStateModel.all()

#    WHY IS THIS NOT WORKING XXX
#    query.filter("phone_id=", str(entry_filter[0]))
#    query.filter("network_type=", str(entry_filter[1]))
    query.order("segment_begin") 
    logging.info("entry filter" + str(entry_filter[1]))
    last_small_avg = None
    last_large_avg = None
    builder = Segment(None)
    Segment.all_segments = {}
    for row in query:
      if row.phone_id != str(entry_filter[0]) or row.network_type != str(entry_filter[1]):
        continue
      if row.small:
        if last_small_avg != None and last_small_avg != row.avg:
          builder.add_small(row.avg, row.segment_begin)
        last_small_avg = row.avg
      else:
        if last_large_avg != None and last_large_avg != row.avg:
          builder.add_large(row.avg, row.segment_begin)
        last_large_avg = row.avg
    logging.info("Model for " + str(entry_filter[0]) +" is " + str(Segment.all_segments))
  
  def getValuesInSegment(self, entry_filter, segment):
    query = model.RRCInferenceRawData.all()
    #query.filter("phone_id=", entry_filter[0])
    #query.filter("network_type=", entry_filter[1])
    #query.filter("time_delay =", segment.start)

    low_values = []
    high_values = []
    signal_strengths = []

    for row in query:
      if row.phone_id != entry_filter[0] or row.network_type != entry_filter[1] or row.time_delay != segment.start or row.signal_low == None or row.rtt_low == None or row.rtt_high == None:
        continue
#      if row.rtt_low >= 0 and row.rtt_low < 7000:
      low_values.append(segment.normalize(row.rtt_low, True))
#      if row.rtt_high>= 0 and row.rtt_high < 7000:
      high_values.append(segment.normalize(row.rtt_high, False))
      signal_strengths.append(row.signal_low)
 
    correlation_big = self.correlation(signal_strengths, low_values)
    correlation_small = self.correlation(signal_strengths, high_values)
    avg_sig_strength = self.meanValue(signal_strengths)
    diff_sig_strength = max(signal_strengths) - min(signal_strengths)
    avg_high_values = self.meanValue(high_values)
    avg_low_values = self.meanValue(low_values)
    std_high_values = self.stdevValue(high_values)
    std_low_values = self.stdevValue(low_values)
    print entry_filter[0], entry_filter[1], segment.start, "</br>"
    print avg_sig_strength, diff_sig_strength, avg_high_values, avg_low_values, std_high_values, std_low_values, correlation_big, correlation_small, "</br>" 
    # for each distinct test, get the average signal strength
    # get the average and standard deviation value
    # TODO we need to also get the normalized value
  
  def calculateSignalStrengthDependence(self, **unused_args):
    devices = self.getAllDevices()
    for d in devices:
      logging.info("Processing device " + str(d))
      self.getModel(d)
      for segment in Segment.all_segments.values():
        self.getValuesInSegment(d, segment)
 
  def meanValue(self, l):
#    logging.info("About to calculate mean of: " + str(l))

    if len(l) == 0 or l == None:
      return 0
    else:
      return sum(l)/len(l)

  # calculate the standard deviation of the list
  def stdevValue(self, li, mean = None):
    """author: Haokun """
    if not li:
        return 0.0

    if not mean:
        mean = self.meanValue(li)

    diff_sum = 0.0
    for i in li:
        diff_sum += (i-mean)*(i-mean)
    return math.sqrt(diff_sum / len(li))

  def correlation(self, l1, l2):
    
    logging.info("Second covariance value is:" + str(l2))
    std1 = self.stdevValue(l1)
    std2 = self.stdevValue(l2)

    cov = self.covariance(l1, l2)
    if std1 != 0 and std2 != 0:
      return cov/(std1 * std2)
 
  def covariance(self, l1, l2):
    if len(l1) == 0 or len(l2) == 0:
      return 0
    mean1 = self.meanValue(l1)
    mean2 = self.meanValue(l2)
    cov = 0
    length = min([len(l1), len(l2)])
    for i in range(length):
      cov += (l1[i] - mean1)  * (l2[i] - mean2)
    return cov / length
