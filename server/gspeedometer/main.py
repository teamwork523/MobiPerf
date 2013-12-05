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

"""Service to collect and visualize mobile network performance data."""

__author__ = 'mdw@google.com (Matt Welsh)'

import sys
# pylint: disable-msg=C6205
try:
  # This is expected to fail on the local server.
  from google.appengine.dist import use_library  # pylint: disable-msg=C6204
except ImportError:
  pass
else:
  # We could use newer version of Django, but 1.2 is the highest version that
  # Appengine provides (as of May 2011).
  for k in [k for k in sys.modules if k.startswith('django')]:
    del sys.modules[k]
  use_library('django', '1.2')

# from google.appengine.ext.webapp import Request
# from google.appengine.ext.webapp import RequestHandler
# from google.appengine.ext.webapp import Response
# from google.appengine.ext.webapp import template
from google.appengine.ext.webapp.util import run_wsgi_app

# pylint: disable-msg=W0611
from gspeedometer import wsgi
from gspeedometer.controllers import checkin
from gspeedometer.controllers import device
from gspeedometer.controllers import googlemap
from gspeedometer.controllers import home
from gspeedometer.controllers import measurement
from gspeedometer.controllers import schedule
from gspeedometer.controllers import timeseries
import routes
from gspeedometer import config

m = routes.Mapper()
m.connect('/',
          controller='home:Home',
          action='Dashboard')

m.connect('/about',
          controller='about:About',
          action='About')

m.connect('/help',
          controller='help:Help',
          action='Help')

m.connect('/checkin',
          controller='checkin:Checkin',
          action='Checkin')

m.connect('/anonymous/checkin',
          controller='checkin:Checkin',
          action='Checkin')

m.connect('/anonymous/',
          controller='home:Home',
          action='Dashboard')

m.connect('/anonymous/postmeasurement',
          controller='measurement:Measurement',
          action='PostMeasurement')

m.connect('/device/view',
          controller='device:Device',
          action='DeviceDetail')

m.connect('/device/delete',
          controller='device:Device',
          action='Delete')

m.connect('/postmeasurement',
          controller='measurement:Measurement',
          action='PostMeasurement')

m.connect('/measurements',
          controller='measurement:Measurement',
          action='ListMeasurements')

m.connect('/measurement/view',
          controller='measurement:Measurement',
          action='MeasurementDetail')

m.connect('/schedule/add',
          controller='schedule:Schedule',
          action='Add')

m.connect('/schedule/delete',
          controller='schedule:Schedule',
          action='Delete')

m.connect('/map',
          controller='googlemap:MapView',
          action='MapView')

m.connect('/timeseries',
          controller='timeseries:Timeseries',
          action='Timeseries')

m.connect('/timeseries/data',
          controller='timeseries:Timeseries',
          action='TimeseriesData')

m.connect('/validation/data',
          controller='validation:Validation',
          action='Validate')

m.connect('/validation/dashboard',
          controller='validation_dashboard:Dashboard',
          action='Dashboard')

m.connect('/validation/dashboard/detail',
          controller='validation_dashboard:Dashboard',
          action='ErrorDetail')

m.connect('/validation/timeseries',
          controller='validation_timeseries:Timeseries',
          action='Timeseries')

m.connect('/validation/timeseries/data',
          controller='validation_timeseries:Timeseries',
          action='TimeseriesData')

# Control to these handlers is controlled by the app.yaml acl lists.
m.connect('/admin/archive/gs',
          controller='archive:Archive',
          action='ArchiveToGoogleStorage')

m.connect('/admin/archive/file',
          controller='archive:Archive',
          action='ArchiveToFile')

m.connect('/admin/archive/cron',
          controller='archive:Archive',
          action='ArchiveToGoogleStorage')

# New RRCInference START
# below are the path and handlers for RRC state inference START:
# for getting computed RRC model from the server
#m.connect('/rrc/getRRCmodel',
#            controller='RRCstates:RRCStates',
#            action='getRRCmodel')
#
## to upload the raw rrc data to server
#m.connect('/rrc/uploadRRCInference',
#            controller='RRCstates:RRCStates',
#            action='uploadRRCInference')
#
## to handle taskqueue tasks
#m.connect('/rrc/generateModelWorker',
#            controller='smooth_prototype:ModelBuilder',
#            action='modelBuilder')
#            
#m.connect('/rrc/generateModel',
#            controller='RRCstates:RRCStates',
#            action='generateModel')
#
#m.connect('/rrc/generateModelAll',
#            controller='smooth_prototype:ModelBuilder',
#            action='buildAll')
#
#m.connect('/rrc/debug',
#            controller='smooth_prototype:ModelBuilder',
#            action='debug')
#
#m.connect('/rrc/debugModel',
#            controller='smooth_prototype:ModelBuilder',
#            action='debug_get_models')
#
#m.connect('/rrc/debugSignalStregnth',
#            controller='signal_strength_dependence:SignalStrengthDependence',
#            action='calculateSignalStrengthDependence')
#

# to upload size data
m.connect('/rrc/uploadRRCInferenceSizes',
            controller='RRCstates:RRCStates',
            action='uploadRRCSizes')

# anonymous version of the RRC model
#m.connect('/anonymous/rrc/getRRCmodel',
#            controller='RRCstates:RRCStates',
#            action='getRRCmodel')

m.connect('/anonymous/rrc/uploadRRCInferenceSizes',
            controller='RRCstates:RRCStates',
            action='uploadRRCInferenceSizes')

m.connect('/anonymous/rrc/uploadRRCInference',
            controller='RRCstates:RRCStates',
            action='uploadRRCInference')

#m.connect('/anonymous/rrc/generateModelWorker',
#            controller='smooth_prototype:ModelBuilder',
#            action='modelBuilder')
#
#m.connect('/anonymous/rrc/generateModelAll',
#            controller='smooth_prototype:ModelBuilder',
#            action='buildAll')
            
#m.connect('/anonymous/rrc/generateModel',
#            controller='RRCstates:RRCStates',
#            action='generateModel')

# A cron job to process RRC data
m.connect('/cron/rrc/generateModelWorker',
            controller='smooth_prototype:ModelBuilder',
            action='cronModelBuiler')

# New RRCInference END

# For backend instance, give it something that won't
# return a 500 error.
m.connect('/_ah/start',
          controller='about:About',
          action='About')

application = wsgi.WSGIApplication(m, debug=False)


def real_main():
  run_wsgi_app(application)


def profile_main():
  # This is the main function for profiling
  import cProfile, pstats, StringIO, logging
  prof = cProfile.Profile()
  prof = prof.runctx('real_main()', globals(), locals())
  print '<pre>'
  stats = pstats.Stats(prof)
  stats.sort_stats('cumulative')
  stats.print_stats(80)  # 80 = how many to print
  stats.print_callees()
  stats.print_callers()
  print '</pre>'


# main = profile_main
main = real_main


if __name__ == '__main__':
  main()
