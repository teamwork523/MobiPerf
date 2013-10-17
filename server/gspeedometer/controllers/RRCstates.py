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
  
    def uploadRRCInference(self, **unused_args):
        """Handler for uploadRRCInference request generated from client"""
        logging.info('Inside uploadRRCInference: input param: %s', self.request.body)        
        getRRCInferenceReqParam = json.loads(self.request.body)
        
        #read all the json param sent from the client.
        rawdata = model.RRCInferenceRawData()
        rawdata.phone_id = util.HashDeviceId(str(getRRCInferenceReqParam['phone_id']))
        rawdata.test_id = getRRCInferenceReqParam['test_id']
        rawdata.network_type = getRRCInferenceReqParam['network_type']
        rawdata.rtt_low = getRRCInferenceReqParam['rtt_low']
        rawdata.rtt_high = getRRCInferenceReqParam['rtt_high']
        rawdata.lost_low = getRRCInferenceReqParam['lost_low']
        rawdata.lost_high = getRRCInferenceReqParam['lost_high']
        rawdata.signal_low = getRRCInferenceReqParam['signal_low']
        rawdata.signal_high = getRRCInferenceReqParam['signal_high']
        rawdata.error_low = getRRCInferenceReqParam['error_low']
        rawdata.error_high = getRRCInferenceReqParam['error_high']
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
        """Handler for getRRCmodel requests."""
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
        measurement_pts_list = []
        measurement_pts_dict = {}

        #read from database
        query = model.RRCStateModel.all()
        logging.info('count of all rows ')
        logging.info(query.count())
        query.filter('phone_id =',phone_id)
        query.filter('small =',False)
        logging.info(query.count())       
        
        
        for row in query:
            logging.info('Inside query iteration!! %d',row.segment_begin)
            segmentvalue = math.floor((row.segment_end - row.segment_begin)/2 + row.segment_begin)
            measurement_pts_list.append(segmentvalue)
        
        measurement_pts_list = list(set(measurement_pts_list))
        measurement_pts_list.sort()
        logging.info('GetRRCmodelData called. Value of measurment points are %s',measurement_pts_list) 
        measurement_pts_dict['measurement_points'] = measurement_pts_list
        return measurement_pts_dict
