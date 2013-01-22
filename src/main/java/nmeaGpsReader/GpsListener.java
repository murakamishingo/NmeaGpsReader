package nmeaGpsReader;

public interface GpsListener {
	
	public void onLocationUpdate(double latitude, double longtitude);
}
