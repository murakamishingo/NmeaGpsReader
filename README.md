What:
- Simple java lib for reading a GPS coordinate from NMEA compatible GPS receiver.
- ocss.nmea.api http://javanmea.sourceforge.net/ is incorperated.

Usage:
- Plug-in NMEA-compatible GPS reader device into your PC USB port
- Find which COM port is allocated to the GPS reader
- Implement a listener class GpsListener
- Example
	
	
	import nmeaGpsReader.*;
	
	public class MyClass implements GpsListener{
		public void onLocationUpdate(double lat, double lng){
			//handle lat and lng
			System.out.println("current lat=" + lat + " current lng=" + lng);
		}
		
		public void main(String args){
			int minDelay = 3; //minimun interval between 
			int minDistance = 5;
			MyClass me = new MyClass();
			GpsReader gpsReader = new GpsReader("COM10", minDelay, minDistance, me);
		}
	}