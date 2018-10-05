package com.peoplesoft.scraper.Util;

import java.text.SimpleDateFormat;
import java.util.Date;

public class Timer {
	public static String getDate() {
		Date currentDate = new Date();
		SimpleDateFormat formatter = new SimpleDateFormat("MM/dd/yyyy");  
		return formatter.format(currentDate);
	}
}
