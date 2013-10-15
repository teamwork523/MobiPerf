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
        taskqueue.add(url='/rrc/generateModelWorker', params={'phone_id': getReqParam['phone_id']})

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
            #get testid for which model is to be returned 
#            test_id = getmodelReqParam['test_id']


            #get computed model info based on testid and phone id
#            rrc_modelData = self.GetRRCmodelData(phone_id,test_id)
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
        #get the current user
        #user = users.get_current_user()  :need to use this based on requirement
        
        #read from database
        '''query =  db.GqlQuery("SELECT * FROM RRCStateModel" 
                            "WHERE phone_id = :1 AND small=FALSE",phone_id)'''
        query = model.RRCStateModel.all()
        logging.info('count of all rows ')
#        logging.info(test_id)
        logging.info(query.count())
        query.filter('phone_id =',phone_id)
        #query.filter('test_id >=',test_id) #TO check of this condition is required
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
        
    #-------------------Temp method writing to DB: START--------------------------------------------   
    
    def temp_uploadRRCInference(self, **unused_args):
        """Handler for uploadRRCInference request generated from client"""
        logging.info('Entering temp_uploadRRCInference')
        #self.temp_writerawdatatoDB()
        rawdata = model.RRCInferenceRawData()
        rawdata.phone_id = 123456054340594
        rawdata.test_id = 8
        rawdata.network_type = '15'
        rawdata.rtt_low = 98
        rawdata.rtt_high = 100
        rawdata.lost_low = 8
        rawdata.lost_high = 0
        rawdata.signal_low = 11
        rawdata.signal_high = 11
        rawdata.error_low = 0
        rawdata.error_high = 0
        rawdata.time_delay = 0
        rawdata.timestamp = datetime.datetime.utcnow()
        #write to DB/model
        #rawdata.put()
        
        
        if config.BACKEND_ONLY:
            logging.info('executing only backend')
            payload = urllib.urlencode({'phone_id': 353024054340594,'test_id':8})
            #url = '%s/backend/smooth_prototype' % (backends.get_url('smoothprototype'))
            logging.info('backend is %s',backends.get_url('smoothprototype'))
            url_renamed = 'http://rrc-gae.smoothprototype.stateofrest.appspot.com/backend/smooth_prototype'
            logging.info('url_renamed %s',url_renamed)
            result =  urlfetch.fetch(url_renamed, method='POST',payload=payload)
            logging.info('result returned from fetch is %s',result.status_code)
        elif config.TASKQUEUE_ONLY:
            # Add the task to the default queue.
            logging.info('executing only taskqueue')
            taskqueue.add(url='/rrc/generateModelWorker', params={'phone_id': 123456054340594})
        
        
    def temp_getRRCmodel(self, **unused_args):
        logging.info('Temp save RRC model called!!!!!!!!!!!!!!!!')
        phone_id  = 353024054340594
        phone_id1 = 520834473640594
        phone_id2 = 405945208344736
        rrcmodel = model.RRCStateModel()
        
        rrcmodel.username = users.get_current_user()
        rrcmodel.phone_id = phone_id
        rrcmodel.test_id = 17
        rrcmodel.segment_begin = 6
        rrcmodel.segment_end = 30
        rrcmodel.avg = 1414.0
        rrcmodel.small = False
        #rrcmodel.put()
        
        rrcmodel1 = model.RRCStateModel()
        rrcmodel1.phone_id = phone_id1
        rrcmodel1.test_id = 17
        rrcmodel1.segment_begin = 0
        rrcmodel1.segment_end = 5
        rrcmodel1.avg = 149.0
        rrcmodel1.small = False
        #rrcmodel1.put()
        
        rrcmodel2 = model.RRCStateModel()
        rrcmodel2.phone_id = phone_id2
        rrcmodel2.test_id = 17
        rrcmodel2.segment_begin = 6
        rrcmodel2.segment_end = 30
        rrcmodel2.avg = 695.0
        rrcmodel2.small = True
        #rrcmodel2.put()
        
   
        #now test if reading of DB is succesful
        measurepts = self.GetRRCmodelData(phone_id)
        logging.info('measurepts :- %s'%measurepts)
        
 #------------------------Temp method writing to DB: END---------------------------------------   
    
