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

package com.kvw.oscdroid;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.os.Handler;
import android.util.Log;

public class ConnectionService {

	/** public statics, result codes */
	public static final int RESULT_OK 		= 0x10;
	public static final int RESULT_ERROR	= 0x11;
	public static final int RESULT_CANCEL 	= 0x12;
	public static final int CH1_DATA_START	= 0x41;
	public static final int CH2_DATA_START	= 0x42;
	
	/** private statics, commands and values */ 
	private static final int CH1CON_ADDR			= 0x00;
	private static final int CH2CON_ADDR			= 0x01;
	private static final int ANATRIGLVL_ADDR		= 0x02;
	private static final int ANATIMECON_ADDR		= 0x03;
	private static final int ANATRIGCON_ADDR		= 0x04;
	private static final int LOGTRIGLVL_ADDR		= 0x05;
	private static final int LOGTIMECON_ADDR		= 0x06;
	private static final int DEVICEREV_ADDR			= 0x07;
	
	
//	private static final int SET_CH1_ENABLED		= 0xAA;
//	private static final int SET_CH2_ENABLED 		= 0xBB;
//	private static final int SET_TRIG_SOURCE_CH1 	= 0x0A;
//	private static final int SET_TRIG_SOURCE_CH2 	= 0x0B;
//	private static final int SET_TRIG_LEVEL			= 0x1A;
//	private static final int SET_TRIG_OFF			= 0x1B;
//	private static final int SET_VDIFF_CH1			= 0x0C;
//	private static final int SET_VDIFF_CH2			= 0x1C;
//	private static final int SET_TIME_DIFF			= 0x20;
//	private static final int SET_RUN_MODE			= 0x30;	
//	
	private static final int CONNTYPE_WIFI=1;
	private static final int CONNTYPE_USB=2;
	
	private static final int STATUS_NC=3;
	private static final int STATUS_CONNECTING=4;
	private static final int STATUS_CONNECTED=5;
	private static final int STATUS_DISCONNECTED=6;
	
	private static final String TAG = "OscDroid.ConnectionService";
	private static final String ACTION_USB_PERMISSION = "com.kvw.oscdroid.connectionservice.usb";
	
	private final Handler mHandler;
	
	private boolean permissionRequested=false;
	private boolean dataToSend = false;
	private boolean newDataReady=false;
	private boolean newDataReadyRequested=false;
	
	private int connectionStatus = STATUS_NC;
	private int connectionType = CONNTYPE_USB;
	
	/**	FPGA Register values */
	private byte CH1CON;
	private byte CH2CON;
	private byte ANATRIGLVL;
	private byte ANATIMECON;
	private byte ANATRIGCON;
	private byte LOGTRIGLVL;
	private byte LOGTIMECON;
	private byte DEVICEREV;
	
	private UsbManager usbManager=null;
	private UsbDevice usbDevice=null;
	private PendingIntent mPermissionIntent;
	private UsbOscilloscopeConnection connectionThread;
	
	private final Context parentContext;
	
	/**
	 * Constructor for ConnectionService class
	 * 
	 * @param context
	 * @param handler
	 */
	public ConnectionService(Context context, Handler handler)
	{
		mHandler=handler;
		usbManager= (UsbManager) context.getSystemService(Context.USB_SERVICE);
		parentContext=context;		
		
		mPermissionIntent = PendingIntent.getBroadcast(context, 0, new Intent(ACTION_USB_PERMISSION),0);
	}
	
	/**
	 * @deprecated
	 * @param type
	 */
	public void setConnectionType(int type)
	{
		connectionStatus=STATUS_NC;
		connectionType=type;

	}
	
	public void registerReceiver()
	{
//		Log.d(TAG,"Registering usbReceiver");
		parentContext.registerReceiver(mUsbReceiver,new IntentFilter(UsbManager.ACTION_USB_DEVICE_ATTACHED));
		parentContext.registerReceiver(mUsbReceiver,new IntentFilter(UsbManager.ACTION_USB_DEVICE_DETACHED));
		parentContext.registerReceiver(mUsbReceiver,new IntentFilter(ACTION_USB_PERMISSION));
	}
	
	public void setDevice(UsbDevice tmpAcc)
	{
		usbDevice = tmpAcc;
	}
	
	public void setCh1Enabled(boolean enable)
	{
		if(enable){
			CH1CON |= 1 << 0;
			connectionThread.dataToWrite = new byte[] {'/','\\',CH1CON_ADDR,CH1CON};
			connectionThread.numBytesToRead=1; //TODO
			connectionThread.newWriteData=true;
			connectionThread.newReadData=true;
		}else if(!enable){
			CH1CON &= 1 << 0;
			connectionThread.dataToWrite=new byte[] {'/','\\',CH1CON_ADDR,CH1CON};
			connectionThread.numBytesToRead=1; //TODO
			connectionThread.newWriteData=true;
			connectionThread.newReadData=true;
		}
	}
	
	public void setCh2Enabled(boolean enable)
	{
		if(enable){
			CH2CON |= 1 << 0;
			connectionThread.dataToWrite = new byte[] {'/','\\',CH2CON_ADDR,CH2CON};
			connectionThread.numBytesToRead=1; //TODO
			connectionThread.newWriteData=true;
			connectionThread.newReadData=true;
		}else if(!enable){
			CH2CON &= 1 << 0;
			connectionThread.dataToWrite=new byte[] {'/','\\',CH2CON_ADDR,CH2CON};
			connectionThread.numBytesToRead=1; //TODO
			connectionThread.newWriteData=true;
			connectionThread.newReadData=true;
		}
	}

	public void setCh1Div(int div)
	{

		switch(div){
		//TODO switch div, set CH1CON bits accordingly
		}
		
		//TODO send CH1CON to CH1CON_ADDR
	}
	
	public void setCh2Div(int div)
	{
		
		switch(div){
		//TODO
		}
	}
	
	/**
	 * 
	 * @param lvl 8-bit integer, set trigger level without zero-biasing
	 */
	public void setTriggerLvl(int lvl)
	{
		ANATRIGLVL=(byte)lvl;
		//TODO send to FPGA
	}
	
	/**
	 * 
	 * @param div integer indicating selected time div
	 */
	public void setTimeDiv(int div)
	{
		int clkdiv=0;
		
		switch(div){
		//TODO calculate clock divider according to setting
		}
		
		ANATIMECON=(byte)clkdiv;
	}

	/**
	 * 
	 * @param pos 0=off, 1=right, 2=left, 3=center
	 */
	public void setTriggerPos(int pos)
	{
		switch(pos){
		case 1:
			ANATRIGCON |= 1 << 6;
			ANATRIGCON &= 0 << 7;
			break;
		case 2:
			ANATRIGCON &= 0 << 6;
			ANATRIGCON |= 1 << 7;
			break;
		case 3:
			ANATRIGCON |= 1 << 6;
			ANATRIGCON |= 1 << 7;
			break;
		case 0:
			ANATRIGCON &= 0 << 6;
			ANATRIGCON &= 0 << 7;
			break;		
		}
		connectionThread.dataToWrite = new byte[] {'/','\\',ANATRIGCON_ADDR,ANATRIGCON};
		connectionThread.numBytesToRead=1; //TODO
		connectionThread.newWriteData=true;
		connectionThread.newReadData=true;
		
	}
	
	public void setRunningMode(boolean continu)
	{
		if(!continu) //single shot
			ANATRIGCON &= 0 << 5;
		
		else if(continu) //continuous
			ANATRIGCON |= 0 << 5;
		
		
		connectionThread.dataToWrite=new byte[] {'/','\\',ANATRIGCON_ADDR,ANATRIGCON};
		connectionThread.numBytesToRead=1; //TODO
		connectionThread.newWriteData=true;
		connectionThread.newReadData=true;
	}

	
	public void setTriggerEdge(boolean rising)
	{
		if(rising) //rising edge trigger
			ANATRIGCON |= 1 << 4;
			
		else if (!rising) //falling edge trigger
			ANATRIGCON &= 0 << 4;
		
		connectionThread.dataToWrite=new byte[] {'/','\\',ANATRIGCON_ADDR,ANATRIGCON};
		connectionThread.numBytesToRead=1; //TODO
		connectionThread.newWriteData=true;
		connectionThread.newReadData=true;		
	}
	
	/**
	 * 
	 * @param source 1=channel1, 2=channel2
	 */
	public void setTriggerSource(int source)
	{
		if(source==1) // channel1
			ANATRIGCON &= 0 << 3;
		
		else if(source==2) // channel2
			ANATRIGCON |= 1 << 3;
		
		connectionThread.dataToWrite=new byte[] {'/','\\',ANATRIGCON_ADDR,ANATRIGCON};
		connectionThread.numBytesToRead=1; //TODO
		connectionThread.newWriteData=true;
		connectionThread.newReadData=true;		
	}
	
	public void setTriggerEnabled(boolean enable)
	{
		if(enable) //trigger enabled
			ANATRIGCON |= 1 << 1;
			
		else if(!enable) //trigger disabled
			ANATRIGCON &= 0 << 1;
		
		connectionThread.dataToWrite=new byte[] {'/','\\',ANATRIGCON_ADDR,ANATRIGCON};
		connectionThread.numBytesToRead=1; //TODO
		connectionThread.newWriteData=true;
		connectionThread.newReadData=true;
			
	}
	
	/** check if there was a trigger event. If yes: data ready */
	public void dataReady()
	{
		connectionThread.dataToWrite=new byte[] {'/','?',ANATRIGCON_ADDR};
		connectionThread.numBytesToRead=5; //TODO
		connectionThread.newWriteData=true;
		connectionThread.newReadData=true;
		
		newDataReadyRequested=true;	
	}
	
	
	
	public void setupConnection()
	{
		setState(STATUS_CONNECTING);

		if(connectionThread !=null)
			connectionThread.mRun=false;
		
		if(usbDevice!=null){
			if(!permissionRequested || !usbManager.hasPermission(usbDevice)){
				permissionRequested=true;
				usbManager.requestPermission(usbDevice, mPermissionIntent);
				return;
			}
			else{
				connectionThread=new UsbOscilloscopeConnection();
				connectionThread.start();
			}
		} 
		else { //usbDevice == null
//			Log.d(TAG,"Getting usbDeviceList");
			HashMap <String,UsbDevice> deviceList = usbManager.getDeviceList();
			if(!deviceList.isEmpty()){
				Collection<UsbDevice> c = deviceList.values();
				Iterator<UsbDevice> itr = c.iterator();
				
				// Check all devices, find the right one
				while(itr.hasNext()){
					UsbDevice tmpDev = (UsbDevice) itr.next();
					if(tmpDev.getProductId()==4660 && tmpDev.getVendorId()==4660)
						usbDevice=tmpDev;
					if(usbDevice!=null)
						break;
				}			
				
				if(usbDevice==null) //No correct devices
					return;
				if(!permissionRequested || !usbManager.hasPermission(usbDevice)){
					permissionRequested=true;
//					Log.d(TAG,"requesting permission, setup");
					usbManager.requestPermission(usbDevice, mPermissionIntent);
				}
			}
		}
	}
	
	public void closeConnection()
	{

		if(connectionThread!=null){
			//Close connection						
			connectionThread.mRun=false;
			
//			Log.d(TAG,"Closing Connection");
			try {connectionThread.join();}
			catch(InterruptedException e){e.printStackTrace();}
			finally{
				connectionThread=null;
			}
		}
		permissionRequested=false;
		usbDevice=null;
		setState(STATUS_DISCONNECTED);	
	}
	
	public void cleanup()
	{
		closeConnection();
		parentContext.unregisterReceiver(mUsbReceiver);
	}
	
	private void handleData(byte[] data)
	{
		Log.d(TAG,"Received: " + data);		
	}
	
	private void setState(int state)
	{
		connectionStatus=state;		
	}
	 
	public int getState(){
		return connectionStatus;
	}
	
	public boolean isConnected()
	{
		if(connectionStatus==STATUS_CONNECTED)
			return true;
		else return false;
	}
	
	
	/** BroadcastReceiver to handle Usb Accessory events */
	BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
		@Override
	    public void onReceive(Context context, Intent intent) {
	        String action = intent.getAction(); 

	        if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
	            UsbDevice device = (UsbDevice)intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
	            if (device != null || usbDevice != null) {
	            	closeConnection();
	            	Log.w(TAG,"UsbDevice detached");
	                // call your method that cleans up and closes communication with the accessory
	            }
	            
	        } else if(UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)){
	        	usbDevice = (UsbDevice)intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
	        	usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
	        	if(!permissionRequested || !usbManager.hasPermission(usbDevice)){
	        		permissionRequested=true;
//	        		Log.d(TAG,"request permission, broadcastReceiver");
	        		usbManager.requestPermission(usbDevice, mPermissionIntent);
	        	}
	        	Log.v(TAG,"UsbDevice attached");
	        	
	        	
	        } else if(ACTION_USB_PERMISSION.equals(action)){
	        	permissionRequested=true;
	        	usbDevice = (UsbDevice)intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
	        	if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)){
	        		if (usbDevice!=null)
	        			setupConnection();
	        	}else {
	        		Log.w(TAG,"Permission denied for: " + usbDevice);
	        		usbDevice=null;
	        	}
	        }
	    }
	};
	
	
	/** DataThread, read/send data to Usb Accessory */
	class UsbOscilloscopeConnection extends Thread{
		private boolean connectionOk=false;

		private UsbDeviceConnection usbConnection=null;
		private UsbInterface usbIntf=null;
		private UsbEndpoint usbEndIn=null;
		private UsbEndpoint usbEndOut=null;
		
		public boolean mRun = true;
		public boolean isRunning=false;
		public boolean newWriteData=false;
		public boolean newReadData=false;
		public byte[] dataToWrite=null;
		public int numBytesToRead=-1;
		
		public UsbOscilloscopeConnection(){
			//Setup connection
			usbIntf=usbDevice.getInterface(1);
			usbConnection=usbManager.openDevice(usbDevice);
			
			//Find correct endpoints
			if(usbIntf.getEndpoint(0).getDirection()==UsbConstants.USB_DIR_IN){
				usbEndIn=usbIntf.getEndpoint(0);
				usbEndOut=usbIntf.getEndpoint(1);
			} else if (usbIntf.getEndpoint(0).getDirection()==UsbConstants.USB_DIR_OUT){
				usbEndOut=usbIntf.getEndpoint(0);
				usbEndIn=usbIntf.getEndpoint(1);
			}
			
			permissionRequested=true;
			if(usbEndIn!=null && usbEndOut != null)
				connectionOk=true;			
		}
		
		/**
		 * @param data byte array to be written, 2 bytes expected on receiving end
		 */
		private int writeCmd(byte[] data)
		{
			int tmp=usbConnection.bulkTransfer(usbEndOut, data, data.length, 2);
			
			return tmp;
		}
		
		private void readNumBytes(int numBytes)
		{
			byte[] buffer = new byte[numBytes];
			
			int tmp = usbConnection.bulkTransfer(usbEndIn, buffer, numBytes, 2);
			if(tmp<0)
				Log.e(TAG,"Error receiving data");
			else{
				newReadData=false;
				numBytesToRead=-1;
				handleData(buffer);				
			}
		}
		
		
		public void run(){
			if(!connectionOk){
				Log.w(TAG,"No connection!");
				setState(STATUS_DISCONNECTED);
				return;				
			}			
			
			setState(STATUS_CONNECTED);
//			Log.d(TAG,"Connection OK, thread");
			
			isRunning=true;
			
			while(mRun){
				//TODO read here
				
				if(usbDevice==null)
					mRun=false;
				
				if(newWriteData && dataToWrite!=null && usbDevice!=null){
					
					int retries=3;
					for(int i=0;i<retries;retries--){
						if(writeCmd(dataToWrite)<0)
							Log.e(TAG,"Sending failed, retry");
						else break;						
					}
					dataToWrite=null;					
				}
				
				if(newReadData && numBytesToRead>0)
					readNumBytes(numBytesToRead);				
			}
			
			isRunning=false;
			try{ usbConnection.close();}
			catch(NullPointerException e){}
			
			usbConnection=null;
			usbEndIn=null;
			usbEndOut=null;
			usbIntf=null;
		}		
	}	
}