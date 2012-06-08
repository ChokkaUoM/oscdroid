/** This file is part of OscDroid for Android.
 *
 * Copyright (C) 2012 K. van Wijk, Enschede, The Netherlands
 *
 * This program is free software; you can redistribute it and/or 
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA. * 
 * 
 */

package com.kvw.oscdroid.display;

import com.kvw.oscdroid.channels.AnalogChannel;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Handler;
import android.os.Message;
import android.util.AttributeSet;
import android.util.FloatMath;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

public class OscDroidSurfaceView extends SurfaceView implements SurfaceHolder.Callback{
	
	/** STATIC VALUES */
	private final static String TAG="OscDroidSurfaceView";
	
	private final static int CHANNEL1 = 0;
	private final static int CHANNEL2 = 1;
	private final static int LOGICPROBE = 2;
	
	private final static int SINGLETOUCH=0;
	private final static int MULTITOUCH=1;

	private final static int TRUE_OFFSET=30;
	private final static int FALSE_OFFSET=45;
	
	public final static int SET_VOLT_CH1 = 0xAA;
	public final static int SET_VOLT_CH2 = 0xBB;
	public final static int SET_TIME_DIV = 0xCC;
	
	public final static String VOLT_DIV = "VoltDiv";
	public final static String TIME_DIV = "TimeDiv";
	
	private Handler mHandler;
	
	private AnalogChannel channel1;
	private AnalogChannel channel2;
	
	private int surfaceWidth=0;
	private int surfaceHeight=0;
	private int touchMode=SINGLETOUCH;
	
	private int backgroundColor=Color.BLACK;
	
	public int SELECTED_CHANNEL=-1;
	
	
	private float mPreviousX;
	private float mPreviousY;
	private float mOffsetX;
	private float mOffsetY;
	
	private float tmpX=100;
	private float tmpY=100;
	
    private float oldDist;
    private float newDist;
	
	public int time=0;
	
	private Grid mGrid;
	
	private DrawThread drawThread;
	Paint tmpPaint = new Paint();
	
	/**
	 * Constructor for custom SurfaceView class
	 * 
	 * @param context Context for this surfaceView
	 * @param attrs AttributeSet to enable xml-embedding of this class
	 */
	public OscDroidSurfaceView(Context context, AttributeSet attrs) {
		super(context, attrs);
		getHolder().addCallback(this);
		drawThread = new DrawThread(getHolder(),this);
		setFocusable(true);
	}
	
	/**
	 * 
	 * @param handler Handler to carry messages to main activity
	 */
	public void setHandler(Handler handler){
		mHandler = handler;
	}
	
	/**
	 * 
	 * @param ch1Color DEPRECATED color for Channel1
	 * @param ch2Color DEPRECATED color for Channel 2
	 * @param logColor DEPRECATED color for logic probe
	 * @param backColor color for the background  of the scope view
	 */
	public void setColors(int ch1Color, int ch2Color, int logColor, int backColor)
	{
		
		backgroundColor=backColor;
		
		
		
		//TODO set channel colors

	}
	
	/**
	 * Add channel to the SurfaceView to enable drawing of the channel
	 * 
	 * @param chan AnalogChannel to add
	 * @param width Width of the canvas
	 * @param height Height of the canvas
	 */
	public void addChannel(AnalogChannel chan,float width, float height)
	{
		if(channel1==null)
			channel1=chan;
		else if(channel1!=null)
			channel2=chan;
		else return;
	}
	
	/**
	 * Calculate spacing between 2 downed pointers on touch screen
	 * 
	 * @param event MotionEvent containing 2 pointers
	 * @return float, Absolute spacing between 2 pointers
	 */
	private float spacing(MotionEvent event) {
 	   float x = event.getX(0) - event.getX(1);
 	   float y = event.getY(0) - event.getY(1);
 	   return FloatMath.sqrt(x * x + y * y);
 	}
	
	/**
	 * 
	 * @param event MotionEvent to check for vertical zooming
	 * @return Int, 0 == not valid, 1 == vertical, 2 == horizontal
	 */
	private int isVertical(MotionEvent event)
	{
		int isVert = 0;
		float touchOffMin = 90f;
		float touchOffMax = 20f;
		
		float x0=event.getX(0);
		float x1=event.getX(1);
		float y0=event.getY(0);
		float y1=event.getY(1);
		float spaceX;
		float spaceY;
		
		if (x0 > x1) spaceX=x0-x1;
		else spaceX=x1-x0;
		
		if (y0 > y1) spaceY=y0-y1;
		else spaceY=y1-y0;
		
		if(spaceY < touchOffMin && spaceX > touchOffMax) isVert=2; //horizontal pinch/zoom
		
		if(spaceX < touchOffMin && spaceY > touchOffMax) isVert=1; //vertical pinch/zoom

		
		return isVert;
	}

	/**
	 * Change zoomfactor for vertical zooming on the samples
	 * 
	 * @param event MotionEvent of the zooming movement
	 */
	private void changeVoltDiv(MotionEvent event)
	{
		////TODO maybe cleanup some code

		float STEPTHRES = 80;
		float zoomFactor = newDist-oldDist;
		
		if(zoomFactor > STEPTHRES){
			//TODO
			switch(SELECTED_CHANNEL){
			case CHANNEL1:
				if(channel1.isEnabled()){
					int newDiv = channel1.getVoltDiv()<1 ? 0 : channel1.getVoltDiv()-1;
					oldDist=newDist;
					Message msg = new Message();
					msg.what=SET_VOLT_CH1;
					msg.arg1=newDiv;
					mHandler.sendMessage(msg);
					
				}
				break;
			case CHANNEL2:
				if(channel2.isEnabled()){
					int newDiv = channel2.getVoltDiv()<1 ? 0 : channel2.getVoltDiv()-1;
					oldDist=newDist;
					Message msg = new Message();
					msg.what=SET_VOLT_CH2;
					msg.arg1=newDiv;
					mHandler.sendMessage(msg);
				}
				break;
			case LOGICPROBE:
				
				break;
			default:
					
				break;
		}				
			
		} else if(-zoomFactor > STEPTHRES){
			//TODO
			
			switch(SELECTED_CHANNEL){
			case CHANNEL1:
				if(channel1.isEnabled()){
					int newDiv = channel1.getVoltDiv()>9 ? 10 : channel1.getVoltDiv()+1;
					oldDist=newDist;
					Message msg = new Message();
					msg.what=SET_VOLT_CH1;
					msg.arg1=newDiv;
					mHandler.sendMessage(msg);					
				}
				break;
			case CHANNEL2:
				if(channel2.isEnabled()){
					int newDiv = channel2.getVoltDiv()>9 ? 10 : channel2.getVoltDiv()+1;
					oldDist=newDist;
					Message msg = new Message();
					msg.what=SET_VOLT_CH2;
					msg.arg1=newDiv;
					mHandler.sendMessage(msg);					
				}
				break;
			case LOGICPROBE:
				
				break;
			default:
					
				break;
		}				
		}
		
		
	}
	
	/**
	 * Change zoomfactor for horizontal zooming on the samples
	 * 
	 * @param event MotionEvent of the zooming movement
	 */
	private void changeTimeDiv(MotionEvent event)
	{
		//TODO should zoom, not set time div!!!
//		time = "Horizontal " + String.valueOf(newDist/oldDist);
		float STEPTHRES=80;
		float zoomFactor=newDist-oldDist;
		
		int newDiv = channel1.getTimeDiv();
		
		if(zoomFactor > STEPTHRES){
			newDiv = newDiv<1 ? 0:newDiv-1;
			oldDist=newDist;
			Message msg = new Message();
			msg.what=SET_TIME_DIV;
			msg.arg1=newDiv;
			mHandler.sendMessage(msg);
		}
		if(-zoomFactor > STEPTHRES){
			newDiv = (newDiv > 17) ? 18:newDiv+1;
			oldDist=newDist;
			Message msg = new Message();
			msg.what=SET_TIME_DIV;
			msg.arg1=newDiv;
			mHandler.sendMessage(msg);
		}
		
	}
	
	/**
	 * Redraw the surfaceview
	 */
	@Override
	public void onDraw(Canvas canvas){
		canvas.drawColor(backgroundColor);
		
		if (mGrid != null)
			mGrid.drawGrid(canvas);
		
		if(channel1!=null)
			if(channel1.isEnabled())
				channel1.drawChannel(canvas);
		if(channel2!=null)
			if(channel2.isEnabled())
				channel2.drawChannel(canvas);
		
		tmpPaint.setColor(Color.RED);
//		tmpPaint.setStrokeWidth(1f);
//		canvas.drawCircle(tmpX, tmpY, 10f, tmpPaint);
		canvas.drawText(String.valueOf(time), 20, 100, tmpPaint);
		time++;
	}
	
	/**
	 * Handle touch events on the surfaceView
	 * Handled events: 	ACTION_DOWN, ACTION_POINTER_DOWN,
	 * 					ACTION_POINTER_UP, ACTION_MOVE
	 * 
	 */
	@Override
	public boolean onTouchEvent(MotionEvent event){
		float x = event.getX();
		float y = event.getY();
		
		switch(event.getActionMasked()){
		case MotionEvent.ACTION_DOWN:
			mOffsetX=x;
			mOffsetY=y;
			break;
		case MotionEvent.ACTION_POINTER_DOWN: // second finger placed on screen
			oldDist=spacing(event);
			
			if(oldDist > 10f)
				touchMode=MULTITOUCH;
			break;
		case MotionEvent.ACTION_POINTER_UP: // second finger released from screen
			touchMode=SINGLETOUCH;
			if(SELECTED_CHANNEL==CHANNEL1)
				channel1.releaseZoom();
			if(SELECTED_CHANNEL==CHANNEL2)
				channel2.releaseZoom();
			break;
		case MotionEvent.ACTION_MOVE:
			if(touchMode==SINGLETOUCH){ // Check movement, vertical or horizontal
				tmpX=x;
				tmpY=y;
				
				float spacingX = (x-mOffsetX);
				spacingX = FloatMath.sqrt(spacingX*spacingX);
				float spacingY = (y-mOffsetY);
				spacingY = FloatMath.sqrt(spacingY*spacingY);
				
				switch(SELECTED_CHANNEL){
				case CHANNEL1:
					if(channel1.isEnabled())
					if(spacingX < FALSE_OFFSET && spacingY > TRUE_OFFSET)
						channel1.setOffset(0, y-mPreviousY);
					
					if(channel1.isEnabled())
					if(spacingY<FALSE_OFFSET && spacingX>TRUE_OFFSET)
						channel1.setOffset(x-mPreviousX, 0);
					break;
				case CHANNEL2:
					if(channel2.isEnabled())
					if(spacingX < FALSE_OFFSET && spacingY > TRUE_OFFSET)
						channel2.setOffset(0, y-mPreviousY);
					
					if(channel2.isEnabled())
					if(spacingY<FALSE_OFFSET && spacingX>TRUE_OFFSET)
						channel2.setOffset(x-mPreviousX, 0);
					break;
				case LOGICPROBE:
					break;
				}
				
				//TODO implement moving of cursors (hor+vert)
				//TODO implement changing trigger level
			} 
			else if (touchMode==MULTITOUCH){
				newDist=spacing(event);
				if (isVertical(event)==1) changeVoltDiv(event);
				else if (isVertical(event)==2) changeTimeDiv(event);
			}			
			break;
		}
		mPreviousX=x;
		mPreviousY=y;
		
		return true;
	}

	/**
	 * surfaceChanged, reset grid and dimensions
	 */
	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, int width,
			int height) {
		surfaceWidth=width;
		surfaceHeight=height;
		
		if(channel1!=null)
			channel1.setDimensions(width,height);
		if(channel2!=null)
			channel2.setDimensions(width,height);
		
		mGrid = new Grid(width,height);
		if(backgroundColor==Color.WHITE)
			mGrid.blackBack=false;
		else if (backgroundColor==Color.BLACK)
			mGrid.blackBack=true;
		
//		Log.v(TAG,"SurfaceChanged; black= "+mGrid.blackBack 
//				+ " ch1: "+channel1.isEnabled() + " w: "+String.valueOf(width)
//				+" h: " + String.valueOf(height));
	}

	/**
	 * surface was first created
	 */
	@Override
	public void surfaceCreated(SurfaceHolder holder) {
		if(drawThread.getState().equals(Thread.State.TERMINATED))
			drawThread = new DrawThread(getHolder(),this);
		
		drawThread.setRunning(true);
		drawThread.start();
	}

	/**
	 * surface was destroyed
	 */
	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {
		boolean retry = true;
	    drawThread.setRunning(false);
	    
	    while (retry) {
	        try {
	            drawThread.join();
	            retry = false;
	        } catch (InterruptedException e) {
	            // we will try it again and again...
	        }
	    }		
	}
}