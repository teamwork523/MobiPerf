package com.mobiperf;

import java.util.Date;
import java.util.Map;

import android.content.Context;

public class ContextMeasurementDesc extends MeasurementDesc {

	protected ContextMeasurementDesc(String type, String key, Date startTime,
			Date endTime, double intervalSec, long count, long priority,
			Map<String, String> params) {
		super(type, key, startTime, endTime, intervalSec, count, priority,
				params);
		// TODO Auto-generated constructor stub
	}

	@Override
	public String getType() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	protected void initializeParams(Map<String, String> params) {
		// TODO Auto-generated method stub
		if (params == null) {
			return;
		}
		return;
	}

}
