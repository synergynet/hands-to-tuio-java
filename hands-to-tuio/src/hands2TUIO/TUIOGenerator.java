/*
    Based on the TUIO Simulator - part of the reacTIVision project
    http://reactivision.sourceforge.net/

    Copyright (c) 2005-2009 Martin Kaltenbrunner <mkalten@iua.upf.edu>

    This program is free software; you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation; either version 2 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.
*/

package hands2TUIO;

import java.util.*;
import java.awt.*;
import com.illposed.osc.*;

public class TUIOGenerator{
	
	private OSCPortOut oscPort;

	private int currentFrame = 0;	
	
	private static final Boolean INVERT_X = false;
	private static final Boolean INVERT_Y = false;

	public Hashtable<Integer,Hand> currentCursorList = new Hashtable<Integer,Hand>();

	private Vector<Integer> stickyCursors = new Vector<Integer>();
	private Vector<Integer> jointCursors = new Vector<Integer>();
	
	public TUIOGenerator(String host, int port) {
		try { 
			oscPort = new OSCPortOut(java.net.InetAddress.getByName(host),port); 
		}catch (Exception e) {
			oscPort = null;
		}
		reset();

	}

	private void sendOSC(OSCPacket packet) {
		try { oscPort.send(packet); }
		catch (java.io.IOException e) {}
	}

	public void cursorDelete() {
		
		OSCBundle cursorBundle = new OSCBundle();
		OSCMessage aliveMessage = new OSCMessage("/tuio/2Dcur");
		aliveMessage.addArgument("alive");
		Enumeration<Integer> cursorList = currentCursorList.keys();
		while (cursorList.hasMoreElements()) {
			Integer s_id = cursorList.nextElement();
			aliveMessage.addArgument(s_id);
		}

		currentFrame++;
		OSCMessage frameMessage = new OSCMessage("/tuio/2Dcur");
		frameMessage.addArgument("fseq");
		frameMessage.addArgument(currentFrame);
		
		cursorBundle.addPacket(aliveMessage);
		cursorBundle.addPacket(frameMessage);

		sendOSC(cursorBundle);
	}

	public void cursorMessage(Hand cursor) {

		OSCBundle cursorBundle = new OSCBundle();
		OSCMessage aliveMessage = new OSCMessage("/tuio/2Dcur");
		aliveMessage.addArgument("alive");

		Enumeration<Integer> cursorList = currentCursorList.keys();
		while (cursorList.hasMoreElements()) {
			Integer s_id = cursorList.nextElement();
			aliveMessage.addArgument(s_id);
		}

		Point point = cursor.getPosition();
		float xpos = (point.x)/(float)TUIOPanel.width;
		if (INVERT_X) xpos = 1 - xpos;
		float ypos = (point.y)/(float)TUIOPanel.height;
		if (INVERT_Y) ypos = 1 - ypos;
		OSCMessage setMessage = new OSCMessage("/tuio/2Dcur");
		setMessage.addArgument("set");
		setMessage.addArgument(cursor.session_id);
		setMessage.addArgument(xpos);
		setMessage.addArgument(ypos);
		setMessage.addArgument(cursor.xspeed);
		setMessage.addArgument(cursor.yspeed);
		setMessage.addArgument(cursor.maccel);

		currentFrame++;
		OSCMessage frameMessage = new OSCMessage("/tuio/2Dcur");
		frameMessage.addArgument("fseq");
		frameMessage.addArgument(currentFrame);
		
		cursorBundle.addPacket(aliveMessage);
		cursorBundle.addPacket(setMessage);
		cursorBundle.addPacket(frameMessage);

		if (HandsToTUIO.VERBOSE)System.out.println("set cur "+cursor.session_id+" "+xpos+" "+ypos+" "+cursor.xspeed+" "+cursor.yspeed+" "+cursor.maccel);

		sendOSC(cursorBundle);
	}

	public void aliveMessage() {

		OSCBundle oscBundle = new OSCBundle();
		OSCMessage aliveMessage = new OSCMessage("/tuio/2Dobj");
		aliveMessage.addArgument("alive");
		
		currentFrame++;
		OSCMessage frameMessage = new OSCMessage("/tuio/2Dobj");
		frameMessage.addArgument("fseq");
		frameMessage.addArgument(currentFrame);

		oscBundle.addPacket(aliveMessage);
		oscBundle.addPacket(frameMessage);
		
		sendOSC(oscBundle);
	}

	public void completeCursorMessage() {
		
		Vector<OSCMessage> messageList = new Vector<OSCMessage>(currentCursorList.size());
		
		OSCMessage frameMessage = new OSCMessage("/tuio/2Dcur");
		frameMessage.addArgument("fseq");
		frameMessage.addArgument(-1);
		
		OSCMessage aliveMessage = new OSCMessage("/tuio/2Dcur");
		aliveMessage.addArgument("alive");
		
		Enumeration<Integer> cursorList = currentCursorList.keys();
		while (cursorList.hasMoreElements()) {
			Integer s_id = cursorList.nextElement();
			aliveMessage.addArgument(s_id);

			Hand cursor = currentCursorList.get(s_id);
			Point point = cursor.getPosition();
					
			float xpos = (point.x)/(float)TUIOPanel.width;
			if (INVERT_X) xpos = 1 - xpos;
			float ypos = (point.y)/(float)TUIOPanel.height;
			if (INVERT_Y) ypos = 1 - ypos;

			OSCMessage setMessage = new OSCMessage("/tuio/2Dcur");
			setMessage.addArgument("set");
			setMessage.addArgument(s_id);
			setMessage.addArgument(xpos);
			setMessage.addArgument(ypos);
			setMessage.addArgument(cursor.xspeed);
			setMessage.addArgument(cursor.yspeed);
			setMessage.addArgument(cursor.maccel);
			messageList.addElement(setMessage);
		}
		
		int i;
		for (i=0;i<(messageList.size()/10);i++) {
			OSCBundle oscBundle = new OSCBundle();			
			oscBundle.addPacket(aliveMessage);
			
			for (int j=0;j<10;j++)
				oscBundle.addPacket((OSCPacket)messageList.elementAt(i*10+j));
			
			oscBundle.addPacket(frameMessage);
			sendOSC(oscBundle);
		} 
		
		if ((messageList.size()%10!=0) || (messageList.size()==0)) {
			OSCBundle oscBundle = new OSCBundle();			
			oscBundle.addPacket(aliveMessage);
			
			for (int j=0;j<messageList.size()%10;j++)
				oscBundle.addPacket((OSCPacket)messageList.elementAt(i*10+j));	
			
			oscBundle.addPacket(frameMessage);
			sendOSC(oscBundle);
		}
	}
	
	public void quit() {
		reset();
	}

	public void reset() {
		stickyCursors.clear();
		jointCursors.clear();		
		
		OSCBundle objBundle = new OSCBundle();
		OSCMessage aliveMessage = new OSCMessage("/tuio/2Dobj");
		aliveMessage.addArgument("alive");

		OSCMessage frameMessage = new OSCMessage("/tuio/2Dobj");
		frameMessage.addArgument("fseq");
		frameMessage.addArgument(-1);

		objBundle.addPacket(aliveMessage);
		objBundle.addPacket(frameMessage);		
		sendOSC(objBundle);
		
		OSCBundle curBundle = new OSCBundle();
		aliveMessage = new OSCMessage("/tuio/2Dcur");
		aliveMessage.addArgument("alive");
		
		frameMessage = new OSCMessage("/tuio/2Dcur");
		frameMessage.addArgument("fseq");
		frameMessage.addArgument(-1);
		
		curBundle.addPacket(aliveMessage);
		curBundle.addPacket(frameMessage);		
		sendOSC(curBundle);
	}

}