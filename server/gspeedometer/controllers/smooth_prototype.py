# Copyright 2012 Google Inc.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#!/usr/bin/python2.4
#

"""Service to perform inferred RRC timers."""

__author__ = 'sanae@umich.edu (Sanae Rosen)'

import math, sys, logging
from google.appengine.ext import webapp
from google.appengine.ext.webapp.util import run_wsgi_app
from gspeedometer import wsgi
from gspeedometer import model
from google.appengine.ext import db
from gspeedometer.helpers import util

import datetime

(AVG_INDEX, BEGIN_INDEX, END_INDEX) = range(3)
(SMALL, BIG) = range(2)

REMOVEDROPPED = True
class ModelBuilder(webapp.RequestHandler):
  logging.info('creating smooth_proto class instance')

  def StartHandler(self, **unused_args):
    """Handler for '/_ah/start'.
    This url is called once when the backend is started.
    """
    logging.info('Backend Start Handler() Called!!!')  

  def post(self, method):
    logging.info('POST MEthod of smooth proto post called!!!!!!!1')

  def get(self, method):
    logging.info('POST MEthod of smooth proto GET called!!!!!!!1')

  def modelBuilder(self, **unused_args):
    """Handler for /generateModelWorker"""
    logging.info('Backend ModelBuilder Called!!!')

    phone_id = self.request.get('phone_id')
    logging.info('phone id =%s '%(phone_id))

    # Hash for anonymity        
    if phone_id.isdigit():
      phone_id = util.HashDeviceId(str(phone_id))

    # Retrieve all network types for phone ID as we want a different model for each
    network_types = self.get_network_types(phone_id)

    for network in network_types:
      data = self.get_all_values(phone_id, network)
      #logging.info('get_all_values() returned result:::')
      #logging.info(data)

      # How many complete tests do we have?
      # If we have sufficient data we can filter data less aggressively.
      # With less than 10 tests, our results are less reliable.
      count_complete = 0
      for i in data[SMALL]:
        if len(data[SMALL]) > 30 and data[SMALL][30] != 7000: # 7000: lost packet or timeout (after 7 seconds)
          count_complete += 1
      use_large_algorithm = count_complete > 10 # over a certain length we filter data more aggressively

      # First, for every test, apply our smoothing function.
      # This gets rid of intermittent noise spikes.
      newdata_small = []
      newdata_large = []
      for i in data[SMALL]:
        smooth_data = self.smooth(i, use_large_algorithm)
        newdata_small.append(smooth_data)
      for i in data[BIG]:
        smooth_data = self.smooth(i, use_large_algorithm)
        newdata_large.append(smooth_data)
      logging.info('newdata_small %s'%newdata_small)      
      logging.info('newdata_large %s'%newdata_large)

      if (len(newdata_small) == 0):
        return # no valid data
      if (len(newdata_large) == 0):
        return # no valid data

      # Then, remove outliers, comparing BETWEEN tests.
      data[SMALL] = self.remove_outliers(newdata_small, use_large_algorithm)
      data[BIG] = self.remove_outliers(newdata_large, use_large_algorithm)

      if not use_large_algorithm:
        # Smooth again if we have a low amount of data.
        # We risk losing transition spikes, but for low data we can at least
        # get approximate model parameters this way.
        data[SMALL] = self.smooth(data[SMALL], use_large_algorithm)
        data[BIG] = self.smooth(data[BIG], use_large_algorithm)

      logging.info('data after removeoutliers and smooth')
      logging.info(data[SMALL])

      # Generate initial model
      model = []
      model.append(self.make_model(data[SMALL], use_large_algorithm))
      model.append(self.make_model(data[BIG], use_large_algorithm))
      logging.info('data in model')
      logging.info(model[SMALL])     
      logging.info(model[BIG])     

      # Fix model artifacts, especially those caused when there is
      # a small amount of data.
      newmodel = []
      newmodel.append(self.simplify_model(model[SMALL], data[SMALL]))
      newmodel.append(self.simplify_model(model[BIG], data[BIG]))
      model = newmodel

      # Make sure model for big packets and small packets consistent.
      # Deals with the "fach" problem basically, where for one packet size the RTTS
      # don't change significantly between states, so you have to look at the other
      # to determine where states begin/end.
      model = self.regularize_model(model[SMALL], data[SMALL], model[BIG], data[BIG])

      # Regularize segments
      labels = self.label_segments(model[SMALL], model[BIG])

      self.upload_model(model, phone_id, labels, network)

  # TODO (Haokun): Double check projection effect!!!
  def get_network_types(self, phone_id):
    logging.info('Enter get_network_types:')
    query = model.RRCInferenceRawData.all()
    logging.info('phone id =%s '%(phone_id))
    query.filter('phone_id =',phone_id)

    network_types = set()
    for row in query:
      network_types.add(row.network_type)
    logging.info('Leave get_network_types:')
    return list(network_types)
    
  def get_all_values(self,phone_id, network_type):
    # Retrieve RTT values.
    # Organize first by whether they're associated with large or small packets
    # Next, by inter-packet time interval
    # Then, just have a big list of all values we can process.
    logging.info('Enter get_all_values:')
    logging.info('phone id =%s '%(phone_id))
    logging.info('network type =%s '%(network_type))
    query = model.RRCInferenceRawData.all()
    query.filter('phone_id =',phone_id)
    query.order('test_id')
    query.order('time_delay')
    logging.info('After filtering in get_all_value %s'%query.count())

    data_small = []
    data_large = []
    for row in query:
      if row.network_type != network_type:
        continue
      if row.time_delay == 0: 
        data_small.append([])
        data_large.append([])
      if row.rtt_low <= 0 or row.rtt_low > 7000:
        data_small[-1].append(7000)
      else:
        data_small[-1].append(int(row.rtt_low))
      if row.rtt_high <= 0 or row.rtt_high > 7000:
        data_large[-1].append(7000)
      else:
        data_large[-1].append(int(row.rtt_high))
    # select all max, and all min
    # if -1 set to 7000
    return [data_small, data_large]            

  def upload_model(self,models,phone_id, labels, network):
    # Save our created model

    #phone_id = util.HashDeviceId(str(phone_id))
    logging.info('Enter upload_model:%%:::')
    small = True;
    query_GQL = db.GqlQuery("SELECT * FROM RRCStateModel WHERE phone_id = :1 AND network_type = :2", phone_id, network)

    query_rows = query_GQL.count()
    for i in range(query_rows):
      query_get = query_GQL.get()
      query_get.delete()                
        
    logging.info('NO of instances after ALL delete %s'% query_GQL.count()) 
    query_test = db.GqlQuery("SELECT * FROM RRCStateModel WHERE phone_id = :1", phone_id)
    logging.info('Making sure all instance of this phone id is deleted. Count= %s'% query_test.count())

    test_count = 0
    for m in models:
      for i in range(len(m)):
        segment = m[i]
        label = labels[i]
        logging.info('Label for %s: %s'% (test_count, label))
        logging.info('Start for %s: %s'% (test_count, segment[BEGIN_INDEX]))
        logging.info('End for %s: %s'% (test_count, segment[END_INDEX]))
        logging.info('Avg for %s: %s'% (test_count, float(segment[AVG_INDEX])))
        logging.info('Phone id for %s: %s'% (test_count, phone_id))
        test_count = test_count + 1
        #logging.info('Counting the loop for model entry:%s'%test_count)
        rrcmodel = model.RRCStateModel()
        rrcmodel.phone_id = phone_id
        #rrcmodel.test_id = test_id                         
        rrcmodel.avg = float(segment[AVG_INDEX])
        rrcmodel.segment_begin = segment[BEGIN_INDEX]
        rrcmodel.segment_end = segment[END_INDEX]
        rrcmodel.small = small
        rrcmodel.timestamp = datetime.datetime.utcnow()
        rrcmodel.label = label
        rrcmodel.network_type = network
        rrcmodel.put()
      small = False;

    query_test = db.GqlQuery("SELECT * FROM RRCStateModel WHERE phone_id = :1", phone_id)
    for item in query_test:
      logging.info(item.avg)
    logging.info('entries for this phone after model is uploaded. Count= %s'% query_test.count())
            
            
  ###############  Functions for preprocessing data  #################

  def smooth(self,data, is_long):
    # For a specific test, average out noise spikes
    # while leaving state transitions.

    #logging.info('Enter smooth()')
    #logging.info('Input to Smooth() is %s'%data)
    data2 = [0 for i in range(len(data))]
    data2[0] = data[0]
    data2[-1] = data[-1]
    for i in range(1, len(data)-1):
      # If timeout/dropped packet,  filter out this packet
      if data[i+1] == 7000 or data[i-1] == 7000 and REMOVEDROPPED:
        data2[i] = data[i]
        continue
      diff_sides = abs(data[i+1] - data[i-1])
      # If the data on either side is similar, i.e no state transition,
      # then take the average of the data on each side.
      if diff_sides < data[i-1]/4 or diff_sides < 100: # tweak this parameter
        data2[i] = (data[i+1] + data[i-1])/2
      else:
        data2[i] = data[i]
    return data2

  def standard_deviation(self,vals):
    sumsquared = 0
    mean = sum(vals)/len(vals)
    for i in vals:
      sumsquared += (i-mean)**2
    return math.sqrt(sumsquared/(len(vals)))

  def remove_outlier_helper(self,row, is_long):
    # Helper function for removing outliers
    # If we have enough data, filter out everything less than half a standard deviation.
    # If we have less data, filter out just one standard deviation.

    if len(row) == 0:
      return -1
    mean = sum(row)/len(row)
    stdev = self.standard_deviation(row)
    row2 = []
    for j in row:
      if (not is_long and abs(j-mean) < stdev ) or (is_long and abs(j-mean) < stdev/2):
        row2.append(j)
    if len(row2) == 0:
      row2 = row
    newmean = sum(row2)/len(row2)
    return newmean

  def remove_outliers(self,data, is_long):
    # Compare all test rns and remove outliers.
    logging.info('remove outliers')

    datalen = max((len(x)) for x in data)
    data2 = []
    for i in range(datalen):
      row = []
      for j in range(len(data)):
        if len(data[j]) <= i:
          continue
        if REMOVEDROPPED and data[j][i] == 7000:
          continue
        row.append(data[j][i])
      if len(row) == 0:
        data2.append(data2[-1])
      else:
        data2.append(self.remove_outlier_helper(row, is_long))
   
    return data2

  ###############  Functions for creating and refining model  #################

  def make_model(self,data, use_large_model):
    # Here, after preprocessing, we actually produce the model.
    # divide into segments of roughly equal performance.
    # How we divide up segments is heuristic-based.
    segments = []

    cur_segment = []
    cur_segment_begin = 0
    cur_segment_end = 0
    avg = -1
    for i in range(len(data)):
      d = data[i]
      if len(cur_segment) == 0:
        cur_segment.append(d)
        cur_segment_end = i
        continue
      avg = float(sum(cur_segment))/len(cur_segment)
      # tentatively add new datapoint
      diff = float(abs(avg - d))
      condition = False
    # More aggressive if we have more data.
      if (use_large_model):
        if (diff/avg > 0.25 and d < 1700 and d > 200) and len(cur_segment) > 2:
          condition = True
        if (diff/avg > 0.5 and d < 200) and len(cur_segment) > 2:
          condition = True
        # Here, we are basically looking for the transition spike
        if (diff/avg > 0.75) and d > 200 and i > 1:
          condition = True
        if i >= len(data) - 1:
          condition = False
      else:
        if (diff/avg > 0.5 and d < 1700 and d > 200) and len(cur_segment) > 3:
          condition = True
        if (diff/avg > 0.75 and d < 200) and len(cur_segment) > 3:
          condition = True
        if (diff/avg > 1.0) and d > 200 and i > 1 and len(cur_segment) > 2:
          condition = True
        if i >= len(data) - 1:
          condition = False

      if condition:
        logging.info("decide to start new segment:" +str(diff) + " " +  str(d) +" " +  str(avg) + " " +  str(i) + " " +  str(diff/avg))
        segments.append([avg, cur_segment_begin, cur_segment_end])
        cur_segment_begin = i
        cur_segment_end = i
        cur_segment = []
      else:
        logging.info("decided not to start new segment:" +str(diff) + " " +  str(d) +" " +  str(avg) + " " +  str(i) + " " +  str(diff/avg))
      cur_segment.append(d)
      cur_segment_end = i
    if len(cur_segment) > 0:
      segments.append([avg, cur_segment_begin, cur_segment_end])

    return segments

  def segment_error(self,data, average, segment):
    # Helper function for regularizing segments.
    # Determine how accurately the segment describes the underlying data.
    if average == None:
      if segment[AVG_INDEX] == None:
        return 1000000
      else:
        return 0
    if segment[AVG_INDEX] == None:
      return 0
    score = 0
    for i in range(segment[BEGIN_INDEX], segment[END_INDEX] + 1):
      score += abs(data[i] - average)
    return score

  def create_regularized_segment(self,segment_to_copy, data, model, i, min_begin):
    # Helper function for regularizing model.
    # Basically, recalculate the average RTT for the new segment.
    segment = segment_to_copy[:]
    segment[BEGIN_INDEX] = min_begin
    if segment[BEGIN_INDEX] > segment[END_INDEX]:
      segment[END_INDEX] = segment[BEGIN_INDEX]

    averagebuilder = 0
    for j in range(segment[BEGIN_INDEX], segment[END_INDEX] + 1):
      averagebuilder += data[j]
    segment[AVG_INDEX] = averagebuilder/(segment[END_INDEX]-segment[BEGIN_INDEX] + 1)
    if i < len(model) - 1:
      model[i+1][BEGIN_INDEX] = segment[END_INDEX] + 1
    return segment
            
  def simplify_model_helper(self, data, first, last):
    avg_builder = []
    for i in range(first, last+1):
      avg_builder.append(data[i])
    avg = float(sum(avg_builder))/len(avg_builder)
    return [avg, first, last]

  def simplify_model(self, model, data):
    # Sometimes, during transitions, we get segments of length 2.
    # Usually, this is overfitting, unless it represents a noise spike.
    # So we filter out these cases.

    # Insufficient data, don't try and impove.
    if len(model) < 3:
      return model

    new_model = model[:]
    for i in range(1, len(model)-1):
      segment = model[i]
      if segment[END_INDEX] - segment[BEGIN_INDEX] > 2: # We're only filtering out ones <2
        continue
      if (model[i-1][AVG_INDEX] < segment[AVG_INDEX] and segment[AVG_INDEX] < model[i+1][AVG_INDEX]) or \
         (model[i-1][AVG_INDEX] > segment[AVG_INDEX] and segment[AVG_INDEX] > model[i+1][AVG_INDEX]):
        # If we have monotonically increasing or decreasing segment RTTs then one is due to uneven
        # data around a transition period and we want to merge it with the closer one.
        # Figure out what the closer one is and merge the segments.
        diff_before = abs(model[i-1][AVG_INDEX] - segment[AVG_INDEX])
        diff_after = abs(model[i+1][AVG_INDEX] - segment[AVG_INDEX])
        if diff_before > diff_after:
          new_model[i] = self.simplify_model_helper(data, model[i][BEGIN_INDEX], model[i+1][END_INDEX])
          new_model[i+1] = None
        else:
          new_model[i-1] = self.simplify_model_helper(data, model[i-1][BEGIN_INDEX], model[i][END_INDEX])
          new_model[i] = None

    retval = []

    for i in new_model:
      if i != None:
        retval.append(i)
    return retval

  def regularize_model(self,model1, data1, model2, data2):
    # Find mismatches in segment size and choose size that matches best
    # Usually due to the fact that some states only change RTTs for either
    # big or small models, or due to differences of 1 between them.
    new_model1 = []
    new_model2 = []
    logging.info("Enter regularize_model")

    i1 = 0
    i2 = 0
    min_begin = 0

    # Iterate over segments.
    # It's a while loop since, during this step, we might be adding segments.
    while True:
      # for i in range(max(len(model1), len(model2))):
      if i1 >= len(model1) and i2 >= len(model2):
        break

      # If there is a mismatch in the number of segments, we want the maximum number of segments
      # This happens generally because of the FACH transition not being visible looking at small or large packets alone
      if i1 >= len(model1):
        model1.append([None])
      if i2 >= len(model2):
        model2.append([None])

      segment1 = model1[i1]
      segment2 = model2[i2]

      # If we have a mismatch in segment lengths, then fix it.
      if segment1[AVG_INDEX] == None or segment2[AVG_INDEX] == None or segment1[END_INDEX] != segment2[END_INDEX]:

        # if it's not an off by one difference, then the bigger segment is shrunk to the size of the smaller one
        # and on a later pass, a new segment will be added.
        if segment1[AVG_INDEX] != None and segment2[AVG_INDEX] != None and segment1[END_INDEX] + 2 < segment2[END_INDEX]:
          segment2 = self.create_regularized_segment(segment1, data2, model2, i1, min_begin)
          segment1 = self.create_regularized_segment(segment1, data1, model1, i1, min_begin)
          i1 += 1

        elif segment1[AVG_INDEX] != None and segment2[AVG_INDEX] != None and segment2[END_INDEX] + 2 < segment1[END_INDEX]:
          segment1 = self.create_regularized_segment(segment2, data1, model1, i2, min_begin)
          segment2 = self.create_regularized_segment(segment2, data2, model2, i2, min_begin)
          i2 += 1

        # If it is an off by one difference, then we pick the one with the least error and treat that as the real segment
        else:
          i1 += 1
          i2 += 1

          # find which one would differ most from the average
          s1_keep_s1 = self.segment_error(data1, segment1[AVG_INDEX], segment1)
          s2_keep_s2 = self.segment_error(data2, segment2[AVG_INDEX], segment2)
          s1_switch_s2 = self.segment_error(data1, segment1[AVG_INDEX], segment2)
          s2_switch_s1 = self.segment_error(data2, segment2[AVG_INDEX], segment1)
          # i.e. it will cost more to  switch s1 than s2:
          if s1_switch_s2 - s1_keep_s1 > s2_switch_s1 - s2_keep_s2:
            logging.info('segment1, is the better segment, s1_switch_s2, and, s1_keep_s1, vs , s2_switch_s1, and, s2_keep_s2')
            segment2 = self.create_regularized_segment(segment1, data2, model2, i1, min_begin)
            segment1 = self.create_regularized_segment(segment1, data1, model1, i1, min_begin)
          else:
            logging.info('segment2, is the better segment, second higher, s2_switch_s1, and, s2_keep_s2, vs , s1_switch_s2 and s1_keep_s1')
            segment1 = self.create_regularized_segment(segment2, data1, model1, i2, min_begin)
            segment2 = self.create_regularized_segment(segment2, data2, model2, i2, min_begin)
      else:
        segment1 = self.create_regularized_segment(segment1, data1, model1, i1, min_begin)
        segment2 = self.create_regularized_segment(segment2, data2, model2, i2, min_begin)

        i1 += 1
        i2 += 1

      new_model1.append(segment1)
      new_model2.append(segment2)
      min_begin = segment1[END_INDEX] + 1

    return [new_model1, new_model2]

  def label_segments(self, model_small, model_large):
    """Must be run after regularizing: finds a label for every segment
       Heuristic-based, not as thoroughly tested as model generation.
       I have been manually verifying thes where they are used."""
    labels = []

    # While we don't necessarily expect to see all of these, they should appear in strict order.
    (INIT, STATE_DCH, STATE_FACH, STATE_PCH) = range(4)
    state = INIT
    pch_state_indices = []
    has_fach = False

    for i in range(len(model_small)):
      small_rtt = model_small[i][AVG_INDEX]
      big_rtt = model_large[i][AVG_INDEX]

      segment_len = model_small[i][END_INDEX] - model_small[i][BEGIN_INDEX]
      # Check for DCH:  Should be the first, and also less than 150.  However, if there's a weird noise period, may have two DCH periods...
      # May need to tweak these heuristics.
      DCH_SMALL_RTT_MAX = 200
      DCH_BIG_RTT_MAX = 200
      DCH_DIFF = 1.75

      FACH_SMALL_RTT_MAX = 400
      FACH_BIG_RTT_MAX = 1700
      FACH_RATIO_CUTOFF = 1.75

      FACH_ANOMALOUS_BIG_MIN = 1500
      FACH_ANOMALOUS_SMALL_MIN = 1000

      PCH_SMALL_MIN = 300
      PCH_BIG_MIN = 400

      # Basically, go through each segment and figure out the state.
      # The previous state constrains what the next state could plausibly be.
      # Then, we use ranges of plausible RTT values to figure it out.

      # The first one musth be DCH or Anomalous:
      if (state == INIT):
        if (small_rtt <  DCH_SMALL_RTT_MAX) and (big_rtt < DCH_BIG_RTT_MAX) and (float(big_rtt)/float(small_rtt) < DCH_DIFF) or \
               (big_rtt < 150): # We have certain expectations for the range of RTT values in DCH.  Otherwise, we label as having unusually high RTTs.
          labels.append("DCH")
        else:
          labels.append("DCH (high RTT) ") # This label may not actually be that useful in general
        state = STATE_DCH

        # Then, our options are continue in DCH/Anomalous, or transition to FACH/anomalous-FACH or DCH
        elif (state == STATE_DCH):
          # DCH is characterized by small RTTs that are similar
          if (small_rtt < DCH_SMALL_RTT_MAX) and (big_rtt < DCH_BIG_RTT_MAX) and (float(big_rtt)/small_rtt < 2):
            labels.append("Anomalous-DCH") # We should only see one DCH-like state
          # FACH is characterized by moderate RTTs and significant differences between RTTs based on packet size
          elif (small_rtt < FACH_SMALL_RTT_MAX) and (big_rtt < FACH_BIG_RTT_MAX) and (float(big_rtt)/small_rtt > 1.75):
            labels.append("FACH")
            state = STATE_FACH
          # FACH transitions are characterized by very high RTTs and short segments
          elif (small_rtt > FACH_ANOMALOUS_SMALL_MIN or big_rtt > FACH_ANOMALOUS_BIG_MIN) and segment_len < 8:
            labels.append("Anomalous-FACH")
            state = STATE_FACH
          # PCH is characterized by high RTTs, though not as high as FACH transitions
          elif small_rtt > PCH_SMALL_MIN and big_rtt > PCH_BIG_MIN:
            labels.append("PCH")
            pch_state_indices.append(i)
            state = STATE_PCH
          else:
            labels.append("Anomalous")
    # From FACH, we can continue to be in FACH, can be an an anomalous FACH state, or can go to PCH.
        elif (state == STATE_FACH):
          if (small_rtt < FACH_SMALL_RTT_MAX) and (big_rtt < FACH_BIG_RTT_MAX) and (float(big_rtt)/small_rtt > 1.75):
            labels.append("FACH")
            state = STATE_FACH
          elif small_rtt > FACH_ANOMALOUS_SMALL_MIN or big_rtt > FACH_ANOMALOUS_BIG_MIN and segment_len < 8:
            labels.append("Anomalous-FACH")
            state = STATE_FACH
          elif small_rtt > PCH_SMALL_MIN and big_rtt > PCH_BIG_MIN:
            labels.append("PCH")
            state = STATE_PCH
            pch_state_indices.append(i)
            # Note: Anomalous FACH, i.e. transition behavior, should always be less than PCH.  
            # If not, it needs correcting.
            if (labels[-2] == "Anomalous-FACH"):
              last_small_rtt = model_small[i-1][AVG_INDEX]
              last_big_rtt = model_large[i-1][AVG_INDEX]
              if last_small_rtt < small_rtt and last_big_rtt < big_rtt*1.5 and float(last_big_rtt)/last_small_rtt > 1.75:
                labels[-2] = "FACH"
          else:
            labels.append("Anomalous")
          has_fach = True
        # From PCH, we have to stay in PCH.
        elif (state == STATE_PCH):
          # if there are two PCH states, one is anomalous.  Go through and mark the biggest ones.
          labels.append("PCH")
          min_index = i
          min_val = small_rtt
          for j in pch_state_indices:
            if small_rtt > model_small[j][AVG_INDEX]:
              min_index = j
              min_val = model_small[j][AVG_INDEX]

          pch_state_indices.append(i)
          #logging.info("min val for pch is %s" %min_val)
          for j in pch_state_indices:
            if j != min_index:
              labels[j] = "Anomalous-PCH"      
                  
          if len(pch_state_indices) > 0 and labels[pch_state_indices[0]] == "Anomalous-PCH" and not has_fach:
            labels[pch_state_indices[0]] = "Anomalous-FACH"
            pch_state_indices = pch_state_indices[1:]
            has_fach = True

            # Now, check if the second one is really FACH
            fach_candidate = pch_state_indices[0]
            small_rtt_candidate = model_small[fach_candidate][AVG_INDEX]
            big_rtt_candidate = model_large[fach_candidate][AVG_INDEX]
            if (small_rtt_candidate < FACH_SMALL_RTT_MAX) and (big_rtt_candidate < FACH_BIG_RTT_MAX) and (float(big_rtt_candidate)/small_rtt_candidate > 1.75):
              labels[fach_candidate] = "FACH"

        logging.info("Chose label %s based on small_rtt: %s, big_rtt: %s, ratio: %s" % (labels[-1], small_rtt, big_rtt, float(big_rtt)/small_rtt))
    return labels

  ##########This is currently not used and it yet to be modified to be compatible with GAE---START ##################
  # Sanae- not needed
  def get_all_devices_to_process():
    #Get a list of all IDs that either do not have a model, or have more recent data than the most recent model.
    #       TODO: probably a more efficient way of doing the lookup.
    connection = get_database()
    cursor = connection.cursor()
    cursor.execute("SELECT DISTINCT phone_id, max(test_id) FROM rrc_inference")
    ids_initial = cursor.fetchall()
    ids_to_check = []
    for i in ids_initial:
            ID = i[0]
            new_test_id = i[1]
            rows_returned = cursor.execute("SELECT MAX(test_id) FROM models WHERE phone_id = %s", [ID])
            if rows_returned == 0:
                    ids_to_check.append(i)
            else:
                    old_test_id = cursor.fetchone()[0]
                    if old_test_id != new_test_id:
                            ids_to_check.append(i)  
                    
    close_database(connection)
    return ids_to_check

