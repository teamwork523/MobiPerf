from django.utils import simplejson as json
from google.appengine.api import users
from google.appengine.ext import db
from google.appengine.ext import webapp

from gspeedometer import model
from gspeedometer.helpers import error
from gspeedometer.helpers import util
import logging
import math
from google.appengine.api import backends
from google.appengine.api import urlfetch
import urllib
import datetime
from google.appengine.api import taskqueue
from gspeedometer import config

class RRCStates(webapp.RequestHandler):
   """ Interact with RRC-related data stored in the database"""
  
    def uploadRRCInference(self, **unused_args):
        """Handler for uploadRRCInference request generated from client.
           Take the results of the RRC inference tasks and store them
           in the database. 
           Note that this is the result of *one* test, i.e. one sequence of
           three packets sent.  A set of tests with varying inter-packet intervals
           can be identified by all having the same test_id."""

        logging.info('Inside uploadRRCInference: input param: %s', self.request.body)        
        getRRCInferenceReqParam = json.loads(self.request.body)
        
        #read all the json param sent from the client.
        rawdata = model.RRCInferenceRawData()
        # ID that uniquely identifies each phone, hashed
        rawdata.phone_id = util.HashDeviceId(str(getRRCInferenceReqParam['phone_id']))
        # Test ID that is unique for each device and set of tests
        rawdata.test_id = getRRCInferenceReqParam['test_id']
        rawdata.network_type = getRRCInferenceReqParam['network_type']

        # Round trip times for the small packets
        rawdata.rtt_low = getRRCInferenceReqParam['rtt_low']
        # Round trip times for the large packets
        rawdata.rtt_high = getRRCInferenceReqParam['rtt_high']

        # number of packets lost for the small packets        
        rawdata.lost_low = getRRCInferenceReqParam['lost_low']
        # number of packets lost for the large packets
        rawdata.lost_high = getRRCInferenceReqParam['lost_high']

        # signal strength at the time the small packets were sent
        rawdata.signal_low = getRRCInferenceReqParam['signal_low']
        # signal strength at the time the large packets were sent
        rawdata.signal_high = getRRCInferenceReqParam['signal_high']

        # Error values currently not implemented
        rawdata.error_low = getRRCInferenceReqParam['error_low']
        rawdata.error_high = getRRCInferenceReqParam['error_high']
      
        # The corresponding inter-packet interval
        rawdata.time_delay = getRRCInferenceReqParam['time_delay']
        #get the current time in utc
        rawdata.timestamp = datetime.datetime.utcnow()

        #write to DB/model
        rawdata.put()
         
        return
        
        
    def generateModel(self, **unused_args):
        """Handler for generateModel request generated from client"""
        logging.info('Inside generateModel: input param: %s', self.request.body)        
        getReqParam = json.loads(self.request.body)         
        
        # Add the task to the default queue.
        taskqueue.add(url='/rrc/generateModelWorker?phone_id=%s' % (getReqParam['phone_id']), method='GET', queue_name='default')

        return
        
    def getRRCmodel(self, **unused_args):
        """Handler for getRRCmodel requests.

           Based on the data uploaded from the client, a model of RRC states and their
           timers is built.  This fetches that model. This is only used internally by
           the client to figure out when to run the upper-layer tests."""
        logging.info('Entering getRRCModel() handler:')
        if self.request.method.lower() != 'post':
            raise error.BadRequest('Not a POST request.')

        getmodelReqParam = json.loads(self.request.body)
        logging.info('Got getmodelReqParam: %s', self.request.body)

        try:
      
            #get the device id which is requesting for model.
            phone_id = util.HashDeviceId(str(getmodelReqParam['phone_id']))
            logging.info('getmodel req from device %s'%phone_id)

            #get computed model info based on testid and phone id
            rrc_modelData = self.GetRRCmodelData(phone_id)
            
            #encode obtained data into JSON format
            rrc_modelData_json = json.dumps(rrc_modelData)

            logging.info('Sending modeldata as response: %s', rrc_modelData_json)
            self.response.headers['Content-Type'] = 'application/json'
            self.response.out.write(rrc_modelData_json)

        except Exception, e:
            logging.error('Got exception during getmodel: %s', e)
            self.response.headers['Content-Type'] = 'application/json'
            self.response.out.write(json.dumps([]))
            
    
    def GetRRCmodelData(self,phone_id):
        """ Helper function for getRRCmodel.  

            Fetches the latest model created for a specific, given phone.
            Returns a list of times, each of which are in the middle of an inferred
            RRC state.  Used only by the client to determine the inter-packet intervals
            for the upper-layer tests (TCP. HTTP, DNS).
        """
        measurement_pts_list = []
        measurement_pts_dict = {}

        #read from database
        query = model.RRCStateModel.all()
        logging.info('count of all rows ')
        logging.info(query.count())
        query.filter('phone_id =',phone_id)
        # Since we don't care about average values corresponding to each timer, we
        # arbitrarily select the rows that correspond to the values for small packets.
        # Timers for large and small packets are the same.
        query.filter('small =',False)
        logging.info(query.count())       
        
        
        for row in query:
            # Take a time in the middle of each segment
            segmentvalue = math.floor((row.segment_end - row.segment_begin)/2 + row.segment_begin)
            measurement_pts_list.append(segmentvalue)
        
        measurement_pts_list = list(set(measurement_pts_list))
        measurement_pts_list.sort()
        logging.info('GetRRCmodelData called. Value of measurment points are %s',measurement_pts_list) 
        measurement_pts_dict['measurement_points'] = measurement_pts_list
        return measurement_pts_dict
