import logging
import ipaddr
from gspeedometer.measurement.measurement_wrapper import MeasurementWrapper

class RRC(MeasurementWrapper):
  """Encapsulates RRC  data and provides methods for analyzing it."""
  vals = dict()
 
  def __init__(self, params, values):
    """ Initializes the RRC object """
    self.vals = values

  def GetHTML(self):
    """Returns an HTML representation of this measurement."""
    output = ""
    for key, value in sorted(self.vals.items()):
      output += str(key) + ": " + str(value) + " <br>\n"
    return output

  # TODO do this properly
  def Validate(self):
    """ 
      Parses data and returns a dict with validation results.
        valid -> boolean: true if data is good
        error_types -> list: list of errors found
    """
    results = dict()
    results["valid"] = True
    results["error_types"] = []

    return results