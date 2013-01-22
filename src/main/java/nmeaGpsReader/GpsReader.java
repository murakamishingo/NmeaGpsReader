package nmeaGpsReader;

import gnu.io.CommPort;
import gnu.io.CommPortIdentifier;
import gnu.io.NoSuchPortException;
import gnu.io.PortInUseException;
import gnu.io.SerialPort;
import gnu.io.UnsupportedCommOperationException;

import java.io.InputStream;
import java.util.ArrayList;

import ocss.nmea.api.NMEAClient;
import ocss.nmea.api.NMEAEvent;
import ocss.nmea.api.NMEAListener;
import ocss.nmea.api.NMEAReader;


public class GpsReader extends NMEAClient{

	private final static String PREFIX = "GP";
	private final static String ARRAY[] = {"GGA"};
	
	private String comPort = "COM10"; 
	private long delay = 1000L; //Default delay is 1000 msec
	private int distance = 5; //Default distance threthold is 5 meters
	private GpsListener listener;
	
	private double lastLat = -1;
	private double lastLng = -1;
	
	private long lastUpdateTime = System.currentTimeMillis();
	
	public GpsReader(String comPort, int delaySec, int distance, GpsListener listener){
		super(PREFIX, ARRAY);
		if(comPort == null || delay < 1 || listener == null)
			throw new IllegalArgumentException();
		this.comPort = comPort;
		if(delay > 0)
			this.delay = delaySec*1000;
		if(distance > 0)
			this.distance = distance;
		this.listener = listener;
		initClient();
		setReader(new CustomSerialClient(getListeners())); // Serial Port reader
		startWorking();
	}
	
	
	@Override
	public void dataDetectedEvent(NMEAEvent e) {
		System.out.println("GPS reader dataDetectedEvent():" + e.getContent());
		
		String s = e.getContent();
		String[] params = s.split(",");
		
		int validity = Integer.parseInt(params[6]);
		
		if(validity != 0){ //valid GPS

			double currentLat = convertToDegree(params[2]);
			double currentLng = convertToDegree(params[4]);
			if(params[3].equals("S")){
				currentLat = currentLat * (-1);
			}
			if(params[5].equals("W")){
				currentLng = currentLng * (-1);
			}
			
			if(lastLng <= 0 //just changed from invalid to valid state
					|| distance(lastLat, lastLng, currentLat, currentLng) > distance
					|| lastUpdateTime < System.currentTimeMillis() - delay){
				notifyListener(currentLat, currentLng);
			}
			
		}else{ //invalid GPS fix
			if(lastLng != 0 //just changed from valid to invalid state 
					|| lastUpdateTime < System.currentTimeMillis()- delay){ 
				notifyListener(0, 0);
			}
		}
	}
	
	private void notifyListener(double currentLat, double currentLng){
		lastLng = currentLat; lastLat = currentLat;
		lastUpdateTime = System.currentTimeMillis();
		listener.onLocationUpdate(lastLat, lastLng);
	}
	
	/**
	 * Convert from Degree-Minute-Minute format to Degree format
	 * @param DegreeMinute.Minute format data
	 * @return
	 */
	public static double convertToDegree(String dmm){
		//NMEA format is dddmm.mmmm
		String s[] = dmm.split("\\.");
		int len = s[0].length();
		
		//seems the last .mmmm can be 4 or 5 digits
		int denominator = (s[1].length() == 4? 10000 : 100000);
		
		double ddd = Double.parseDouble(s[0].substring(0, len - 2));
		double mm = Double.parseDouble(s[0].substring(len - 2, len))/60;
		double mmmm = Double.parseDouble(s[1])/(60*denominator);
		
		System.out.println("ddd=" + ddd + " mm=" + mm + " mmmm=" + mmmm);
		
		return ddd + mm + mmmm;
	}
	
	public static int distance(double latA, double lngA, double latB, double lngB){
		
		final double earth_r = 6378.137;

		double diffLat = Math.PI / 180 * (latB - latA);
		double diffLng = Math.PI / 180 * (lngB - lngA);
	
		double distanceVertical = earth_r * diffLat;
		double distanceHorizontal = Math.cos(Math.PI/180*latA) * earth_r * diffLng;
		double distance = Math.sqrt(Math.pow(distanceHorizontal, 2) + Math.pow(distanceVertical, 2));
		return (int)(distance*1000); //km -> m
	}
	
	private class CustomSerialClient extends NMEAReader{
	  
		public CustomSerialClient(ArrayList<NMEAListener> al){
			super(al);
		}
		
	
		
		public void read(){
			super.enableReading();
			CommPortIdentifier com = null;
			try { 
				com = CommPortIdentifier.getPortIdentifier(comPort); 
			}catch (NoSuchPortException nspe){
				System.err.println("No Such Port");
				return;
			}
	    
			CommPort thePort = null;
			try { 
				thePort = com.open("PortOpener", 10); 
			}catch (PortInUseException piue){
				System.err.println("Port In Use");
				return;
			}
			int portType = com.getPortType();
			if (portType == com.PORT_PARALLEL)
				System.out.println("This is a parallel port");
			else if (portType == com.PORT_SERIAL)
				System.out.println("This is a serial port");
			else
				System.out.println("This is an unknown port:" + portType);
			if (portType == com.PORT_SERIAL){
				SerialPort sp = (SerialPort)thePort;
				try{
					// Settings for B&G Hydra
					sp.setSerialPortParams(4800,
							SerialPort.DATABITS_8,
							SerialPort.STOPBITS_1,
							SerialPort.PARITY_NONE);
				}catch (UnsupportedCommOperationException ucoe){
					System.err.println("Unsupported Comm Operation");
					return;
				}
			}
			// Reading on Serial Port
			try{
				byte[] buffer = new byte[4096];
				InputStream theInput = thePort.getInputStream();
				//System.out.println("Reading serial port...");
				do{
					try{
						//Thread.sleep(delay); //DELAY
						if(canRead()){
							int bytesRead = theInput.read(buffer);
							if (bytesRead == -1)
								break;
							// Count up to the first not null
							int nn = bytesRead;
							for (int i=0; i<Math.min(buffer.length, bytesRead); i++){
								if (buffer[i] == 0){
									nn = i;
									break;
								}
							}
							byte[] toPrint = new byte[nn];
							for (int i=0; i<nn; i++)
								toPrint[i] = buffer[i];
							// Broadcast event
							super.fireDataRead(new NMEAEvent(this, new String(toPrint))); // Broadcast the event
						}
					}catch(Exception e){
						continue;
					}
				}while(true);
	     
				//System.out.println("Stop Reading serial port.");
			}catch (Exception e){
				e.printStackTrace();
			}
		}
	}


}
