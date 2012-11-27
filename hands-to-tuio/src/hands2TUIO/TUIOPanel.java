// Utilises Andrew Davison's HandTracker (http://fivedots.coe.psu.ac.th/~ad/jg/nui165/index.html)

package hands2TUIO;

import javax.swing.*;
import java.awt.*;
import java.awt.image.*;
import java.text.DecimalFormat;

import java.nio.ByteBuffer;

import org.OpenNI.*;

import com.primesense.NITE.*;

enum SessionState{
    IN_SESSION, NOT_IN_SESSION, QUICK_REFOCUS
}

public class TUIOPanel extends JPanel implements Runnable{

	private static final long serialVersionUID = 692632562674377006L;
	
	private static final int CIRCLE_SIZE = 15;
	
	private long lastFrameTime = -1;
	
	public static int width = 640;
	public static int height = 480;
	
	final TUIOGenerator tuioGenerator = new TUIOGenerator(HandsToTUIO.TUIO_HOST, HandsToTUIO.TUIO_PORT);
	
	private static final Color POINT_COLORS[] = {Color.RED, Color.BLUE, Color.CYAN, Color.GREEN, Color.MAGENTA, Color.PINK, Color.YELLOW };
	
	// image vars
	private BufferedImage image = null;
	private int imWidth, imHeight;
	
	private volatile boolean isRunning;
	
	// used for the average ms processing information
	private int imageCount = 0;
	private long totalTime = 0;
	private DecimalFormat df;
	private Font msgFont;
	
	// OpenNI and NITE vars
	private Context context;
	private ImageGenerator imageGen;
	private DepthGenerator depthGen;
	
	private SessionManager sessionMan;
	private SessionState sessionState;
	
	public TUIOPanel(JFrame top){		

		setBackground(Color.DARK_GRAY);
	
	    df = new DecimalFormat("0.#");  // 1 dp
	    msgFont = new Font("SansSerif", Font.BOLD, 18);
	
	    configKinect();
	
	    new Thread(this).start();   // start updating the panel's image
	}

	private void configKinect(){    // set up OpenNI and NITE generators and listeners
		try{
			context = new Context();
	
			// add the NITE Licence
			License licence = new License("PrimeSense", "0KOIk2JeIBYClPWVnMoRKn5cdY4=");
			context.addLicense(licence);
	
			// set up image and depth generators
			imageGen = ImageGenerator.create(context); // for displaying the scene
			depthGen = DepthGenerator.create(context); // for converting real-world coords to screen coords
			
			MapOutputMode mapMode = new MapOutputMode(640, 480, 30);   // xRes, yRes, FPS
			imageGen.setMapOutputMode(mapMode);
			depthGen.setMapOutputMode(mapMode);
			
			imageGen.setPixelFormat(PixelFormat.RGB24);
			
			ImageMetaData imageMD = imageGen.getMetaData();
			imWidth = imageMD.getFullXRes();
			imHeight = imageMD.getFullYRes();
			System.out.println("Image dimensions (" + imWidth + ", " + imHeight + ")");
			
			width = imWidth;
			height = imHeight;
			
			// set Mirror mode for all
			context.setGlobalMirror(true);
			
			// set up hands and gesture generators
			HandsGenerator hands = HandsGenerator.create(context);
			hands.SetSmoothing(0.1f);
			
			GestureGenerator gesture = GestureGenerator.create(context);     
			
			context.startGeneratingAll();
			if (gesture.isGenerating())System.out.println("Started context generating...");
			
			// set up session manager and points listener
			sessionMan = new SessionManager(context, "Click,Wave", "RaiseHand");
			
			// raise isn't recognised when click is included in focus
			setSessionEvents(sessionMan);
			sessionState = SessionState.NOT_IN_SESSION;
			
			PointControl pointCtrl = initPointControl();
			sessionMan.addListener(pointCtrl);
		}catch (GeneralException e) {
			e.printStackTrace();
			tuioGenerator.quit();
			System.exit(1);
		}
	}
	
	private void setSessionEvents(SessionManager sessionMan){  // create session callbacks
		try {
			// session start (S1)
			sessionMan.getSessionStartEvent().addObserver( new IObserver<PointEventArgs>() {
				public void update(IObservable<PointEventArgs> observable, PointEventArgs args){
					System.out.println("Session started...");
					sessionState = SessionState.IN_SESSION;
				}
			});
			
			// session end (S2)
			sessionMan.getSessionEndEvent().addObserver( new IObserver<NullEventArgs>() {
				public void update(IObservable<NullEventArgs> observable, NullEventArgs args){
					System.out.println("Session ended");
					isRunning = false;
					sessionState = SessionState.NOT_IN_SESSION;
				}
			});
		}catch (StatusException e) {
			e.printStackTrace();
		}
	}
	
	private PointControl initPointControl(){  // create 4 hand point listeners
		PointControl pointCtrl = null;
		try {
			pointCtrl = new PointControl();
			
			pointCtrl.getPointCreateEvent().addObserver( new IObserver<HandEventArgs>() {
				public void update(IObservable<HandEventArgs> observable, HandEventArgs args){
					sessionState = SessionState.IN_SESSION;
					HandPointContext handContext = args.getHand();
					int id = handContext.getID();
					
					if (handContext.getPosition().getZ() < HandsToTUIO.THRESHOLD_DISTANCE){
						touchCreate(id, handContext.getPosition());
					}
				}
			});
			
			pointCtrl.getPointUpdateEvent().addObserver( new IObserver<HandEventArgs>() {
				public void update(IObservable<HandEventArgs> observable, HandEventArgs args){
					sessionState = SessionState.IN_SESSION;
					HandPointContext handContext = args.getHand();
					int id = handContext.getID();
					
					if (!tuioGenerator.currentCursorList.containsKey(id)){
						if (handContext.getPosition().getZ() < HandsToTUIO.THRESHOLD_DISTANCE){
							touchCreate(id, handContext.getPosition());
						}
					}else{
						if (handContext.getPosition().getZ() < HandsToTUIO.THRESHOLD_DISTANCE){
							touchChange(id, handContext.getPosition());
						}else{
							touchDestroy(id);
						}
					}
				}
			});
			
			pointCtrl.getPointDestroyEvent().addObserver( new IObserver<IdEventArgs>() {
				public void update(IObservable<IdEventArgs> observable, IdEventArgs args){
					int id = args.getId();
					if (tuioGenerator.currentCursorList.containsKey(id)){
						touchDestroy(id);
					}
				}
			});
			
			// no active hand point, which triggers refocusing (P4)
			pointCtrl.getNoPointsEvent().addObserver( new IObserver<NullEventArgs>() {
				public void update(IObservable<NullEventArgs> observable, NullEventArgs args){
					if (sessionState != SessionState.NOT_IN_SESSION) {
						System.out.println("  Lost hand point, so refocusing");
						sessionState = SessionState.QUICK_REFOCUS;
					}
				}
			});
		}catch (GeneralException e) {
			e.printStackTrace();
		}
		
		return pointCtrl;
	}
	
	private void touchCreate(int id, Point3D position) {
		
		try{
			Point3D pt = depthGen.convertRealWorldToProjective(position);    // convert real-world coordinates to screen form
			if (pt == null)return;
			
			Hand touch = new Hand(id, (int)pt.getX(), (int)pt.getY(), (int)pt.getZ()); 			
			tuioGenerator.currentCursorList.put(id, touch);
			tuioGenerator.cursorMessage(touch);
			
		}catch (StatusException e){
			System.out.println("Problem converting point"); 
		}
		
	}
	
	private void touchChange(int id, Point3D position) {
		try{
			Point3D pt = depthGen.convertRealWorldToProjective(position);    // convert real-world coordinates to screen form
			if (pt == null)return;
			
			Hand touch = tuioGenerator.currentCursorList.get(id);
			touch.update((int)pt.getX(), (int)pt.getY(), (int)pt.getZ());
			tuioGenerator.cursorMessage(touch);				
			
		}catch (StatusException e){
			System.out.println("Problem converting point"); 
		}
		
	}
	
	private void touchDestroy(int id) {
		 tuioGenerator.currentCursorList.remove(id);
		 tuioGenerator.cursorDelete();		
	}
	
	public Dimension getPreferredSize(){
		return new Dimension(imWidth, imHeight);
	}
	
	public void closeDown(){
		isRunning = false; 
	}
	
	public void run(){  //update the Kinect info and redraw
		isRunning = true;
		
		while (isRunning) {
			try {
				context.waitAnyUpdateAll();
				sessionMan.update(context);
			}catch(StatusException e){
				System.out.println(e);
				tuioGenerator.quit();
				System.exit(1);
			}
			long startTime = System.currentTimeMillis();
			updateCameraImage();
			
            long dt = startTime - lastFrameTime;
            if (dt>1000) {
            	tuioGenerator.completeCursorMessage();
			}
			
			totalTime += (System.currentTimeMillis() - startTime);
			repaint();
		}
		
		// close down
		try {
			context.stopGeneratingAll();
		}catch (StatusException e) {}
		context.release();
		tuioGenerator.quit();
		System.exit(1);
	}
	
	private void updateCameraImage(){  // update Kinect camera's image
		try {
			ByteBuffer imageBB = imageGen.getImageMap().createByteBuffer();
			image = bufToImage(imageBB);
			imageCount++;
		}catch (GeneralException e) {
			System.out.println(e);
		}
	}
	
	private BufferedImage bufToImage(ByteBuffer pixelsRGB){
		//Transform the ByteBuffer of pixel data into a BufferedImage Converts RGB bytes to ARGB ints with no transparency.
		int[] pixelInts = new int[imWidth * imHeight];
		
		int rowStart = 0;
        // rowStart will index the first byte (red) in each row;
        // starts with first row, and moves down
		
		int bbIdx;               // index into ByteBuffer
		int i = 0;               // index into pixels int[]
		int rowLen = imWidth * 3;    // number of bytes in each row
		
		for (int row = 0; row < imHeight; row++) {
			bbIdx = rowStart;
			// System.out.println("bbIdx: " + bbIdx);
			for (int col = 0; col < imWidth; col++) {
				int pixR = pixelsRGB.get( bbIdx++ );
				int pixG = pixelsRGB.get( bbIdx++ );
				int pixB = pixelsRGB.get( bbIdx++ );
				pixelInts[i++] = 0xFF000000 | ((pixR & 0xFF) << 16) | ((pixG & 0xFF) << 8) | (pixB & 0xFF);
			}
			rowStart += rowLen;   // move to next row
		}
		
		// create a BufferedImage from the pixel data
		BufferedImage im = new BufferedImage( imWidth, imHeight, BufferedImage.TYPE_INT_ARGB);
		im.setRGB( 0, 0, imWidth, imHeight, pixelInts, 0, imWidth );
		
		return im;
	}
	
	public void paintComponent(Graphics g){  // Draw the camera image, blobs, user instructions and statistics.
		super.paintComponent(g);
		Graphics2D g2 = (Graphics2D) g;
		
		if (image != null)g2.drawImage(image, 0, 0, this);    // draw camera's image
		for (Integer id : tuioGenerator.currentCursorList.keySet()) {
			Hand touch = tuioGenerator.currentCursorList.get(id);
			
			int circleSize = getRelativeSizeFromZ(touch.getZLoc(), CIRCLE_SIZE);
			
		    g2.setColor(POINT_COLORS[(id+1) % POINT_COLORS.length]);			
		    g2.fillOval((int)touch.getPosition().getX()-circleSize/2, (int)touch.getPosition().getY()-circleSize/2, circleSize, circleSize);		    
		    
		}
		writeMessage(g2);
		
		writeStats(g2);
	}
	
	private int getRelativeSizeFromZ(float z, int modified){
		int possibleResult =  (int)(modified / (z/500));
		return possibleResult*2; 
	}  
	
	private void writeMessage(Graphics2D g2){  // draw user information based on the session state
		g2.setColor(Color.YELLOW);
		g2.setFont(msgFont);
		
		String msg = null;
		switch (sessionState) {
			case IN_SESSION:
				msg = "Tracking...";
				break;
			case NOT_IN_SESSION:
				msg = "Click/Wave to start tracking";
				break;
			case QUICK_REFOCUS:
				msg = "Click/Wave/Raise your hand to resume tracking";
				break;
		}
		
		if (msg != null)g2.drawString(msg, 5, 20);  // top left
	}
	
	private void writeStats(Graphics2D g2){  // write statistics in bottom-left corner, or "Loading" at start time
		g2.setColor(Color.YELLOW);
		g2.setFont(msgFont);
		int panelHeight = getHeight();
		if (imageCount > 0) {
			double avgGrabTime = (double) totalTime / imageCount;
			g2.drawString("Pic " + imageCount + "  " + df.format(avgGrabTime) + " ms", 5, panelHeight-10);  // bottom left
		}else { // no image yet
			g2.drawString("Loading...", 5, panelHeight-10);
		}
	}

}