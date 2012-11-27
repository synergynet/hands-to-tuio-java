// Utilises Andrew Davison's HandTracker (http://fivedots.coe.psu.ac.th/~ad/jg/nui165/index.html)

package hands2TUIO;

import java.awt.*;
import java.awt.event.*;
import java.lang.management.ManagementFactory;

import javax.swing.*;

public class HandsToTUIO extends JFrame{
	
	private static final long serialVersionUID = -1891195877753310514L;
	private TUIOPanel trackerPanel;
	
	public static int THRESHOLD_DISTANCE = 750;
	public static Boolean VERBOSE = false;
	
	public static String TUIO_HOST = "127.0.0.1";
	public static int TUIO_PORT = 3333;
	
	public HandsToTUIO(){
		super("Hands to TUIO");
		
		Container c = getContentPane();
		c.setLayout( new BorderLayout() ); 
		
		trackerPanel = new TUIOPanel(this); // the camera image appears here
		c.add( trackerPanel, BorderLayout.CENTER);
		
		addWindowListener( new WindowAdapter() {
			
			public void windowClosing(WindowEvent e){
				trackerPanel.closeDown();  }  // stop rendering
			}
		);
		
		pack();  
		
		setResizable(false);
		setLocationRelativeTo(null);
		setVisible(true);
	}
	
	public static void main( String args[] ){
		
		try{
			THRESHOLD_DISTANCE = Integer.parseInt(ManagementFactory.getRuntimeMXBean().getSystemProperties().get("threshold"));
		}catch(Exception e){
			System.out.println("No threshold argument giving, using default.");
		}
		
		System.out.println("Threshold: " + THRESHOLD_DISTANCE);
		
		try{
			VERBOSE = Boolean.parseBoolean(ManagementFactory.getRuntimeMXBean().getSystemProperties().get("verbose"));
		}catch(Exception e){
			System.out.println("No verbose argument giving, using default.");
		}
		
		System.out.println("Verbose mode: " + VERBOSE);
		
		try{
			TUIO_HOST = ManagementFactory.getRuntimeMXBean().getSystemProperties().get("host");
		}catch(Exception e){
			System.out.println("No host argument giving, using default.");
		}
		
		System.out.println("Host: " + TUIO_HOST);
		
		try{
			TUIO_PORT = Integer.parseInt(ManagementFactory.getRuntimeMXBean().getSystemProperties().get("port"));
		}catch(Exception e){
			System.out.println("No port argument giving, using default.");
		}
		
		System.out.println("Host: " + TUIO_PORT);
		
		new HandsToTUIO();  
	}

}