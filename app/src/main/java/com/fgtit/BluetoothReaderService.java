package com.fgtit;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import androidx.core.app.ActivityCompat;

import com.fgtit.data.Conversions;
import com.fgtit.fpcore.FPMatch;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

/**
 * This class does all the work for setting up and managing Bluetooth
 * connections with other devices. It has a thread that listens for
 * incoming connections, a thread for connecting with a device, and a
 * thread for performing data transmissions when connected.
 */
public class BluetoothReaderService {
    // Debugging
    private static final String TAG = "BluetoothChatService";
    private static final boolean D = true;
    //Message
    // Key names received from the BluetoothChatService Handler
    public static final String DEVICE_NAME = "Bluetooth Reader";
    public static final String TOAST = "Hint";

    // Intent request codes
    public static final int REQUEST_CONNECT_DEVICE = 1;
    public static final int REQUEST_ENABLE_BT = 2;
    // Message types sent from the BluetoothChatService Handler
    public static final int MESSAGE_STATE_CHANGE = 0x71;
    public static final int MESSAGE_READ = 0x72;
    public static final int MESSAGE_WRITE = 0x73;
    public static final int MESSAGE_DEVICE_NAME = 0x74;
    public static final int MESSAGE_TOAST = 0x75;
    public static final int MESSAGE_TIMEOUT = 0x76;

    public final static int IMAGESIZE_152_200 = 15200;
    public final static int IMAGESIZE_256_288 = 36864;
    public final static int IMAGESIZE_256_360 = 46080;

    //Command
    public final static byte CMD_PASSWORD = 0x01;    //Password
    public final static byte CMD_ENROLID = 0x02;        //Enroll in Device
    public final static byte CMD_VERIFY = 0x03;        //Verify in Device
    public final static byte CMD_IDENTIFY = 0x04;    //Identify in Device
    public final static byte CMD_DELETEID = 0x05;    //Delete in Device
    public final static byte CMD_CLEARID = 0x06;        //Clear in Device
    public final static byte CMD_ENROLHOST = 0x07;    //Enroll to Host
    public final static byte CMD_CAPTUREHOST = 0x08;    //Caputre to Host
    public final static byte CMD_MATCH = 0x09;        //Match
    public final static byte CMD_WRITEFPCARD = 0x0A;    //Write Card Data
    public final static byte CMD_READFPCARD = 0x0B;    //Read Card Data
    public final static byte CMD_CARDSN = 0x0E;        //Read Card Sn
    public final static byte CMD_GETSN = 0x10;
    public final static byte CMD_FPCARDMATCH = 0x13;

    public final static byte CMD_WRITEDATACARD = 0x14;    //Write Card Data
    public final static byte CMD_READDATACARD = 0x15;     //Read Card Data

    public final static byte CMD_PRINTCMD = 0x20;        //Printer Print
    public final static byte CMD_GETBAT = 0x21;
    public final static byte CMD_GETVERSION = 0x22;        //Version
    public final static byte CMD_SHUTDOWNDEVICE = 0x23;        //Version

    public final static byte CMD_GETIMAGE = 0x30;
    public final static byte CMD_GETCHAR = 0x31;
    public final static byte CMD_UPCARDSN = 0x43;

    public final static byte CMD_GETDEVTYPE = 0x50;
    public final static byte CMD_SETDEVDELAY = 0x51;

    public final static byte CMD_GETIMAGESIZE = 0x60;
    public final static byte CMD_GETIMAGEDATA = 0x61;

    private Timer mTimerTimeout = null;
    private TimerTask mTaskTimeout = null;
    private Handler mHandlerTimeout;

    private byte mDeviceCmd = 0x00;
    private boolean mIsWork = false;
    private byte mCmdData[] = new byte[10240];
    private int mCmdSize = 0;

    public byte mRefData[] = new byte[512];
    public int mRefSize = 0;
    public byte mMatData[] = new byte[512];
    public int mMatSize = 0;

    public byte mCardSn[] = new byte[4];
    public byte mCardData[] = new byte[4096];
    public int mCardSize = 0;

    public byte mBat[] = new byte[2];
    public byte mUpImage[] = new byte[73728];
    public int mUpImageSize = 0;
    public int mUpImageCount = 0;
    public int mUpImageTotal = 0;

    public byte mRefCoord[] = new byte[512];
    public byte mMatCoord[] = new byte[512];
    public byte mIsoData[] = new byte[378];

    public byte mVersion[] = new byte[8];

    public byte mSendTest[] = new byte[1024];
    public byte mRevTest[] = new byte[1024];

    // Name for the SDP record when creating server socket
    private static final String NAME = "BluetoothChat";
    // Unique UUID for this application
    private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    // Member fields
    private final BluetoothAdapter mAdapter;
    private final Handler mHandler;
    private AcceptThread mAcceptThread;
    private ConnectThread mConnectThread;
    private ConnectedThread mConnectedThread;
    private int mState;
    private InputStream mInStream;
    private OutputStream mOutStream;

    // Constants that indicate the current connection state
    public static final int STATE_NONE = 0;       // we're doing nothing
    public static final int STATE_LISTEN = 1;     // now listening for incoming connections
    public static final int STATE_CONNECTING = 2; // now initiating an outgoing connection
    public static final int STATE_CONNECTED = 3;  // now connected to a remote device
    Context contexto;

    /**
     * Constructor. Prepares a new BluetoothChat session.
     * @param context  The UI Activity Context
     * @param handler  A Handler to send messages back to the UI Activity
     */
    public BluetoothReaderService(Context context, Handler handler) {
        mAdapter = BluetoothAdapter.getDefaultAdapter();
        mState = STATE_NONE;
        mHandler = handler;
        contexto = context;
    }

    /**
     * Set the current state of the chat connection
     * @param state  An integer defining the current connection state
     */
    private synchronized void setState(int state) {
        if (D) Log.d(TAG, "setState() " + mState + " -> " + state);
        mState = state;

        // Give the new state to the Handler so the UI Activity can update
        mHandler.obtainMessage(MESSAGE_STATE_CHANGE, state, -1).sendToTarget();
    }

    /**
     * Return the current connection state. */
    public synchronized int getState() {
        return mState;
    }

    /**
     * Start the chat service. Specifically start AcceptThread to begin a
     * session in listening (server) mode. Called by the Activity onResume() */
    public synchronized void start() {
        if (D) Log.d(TAG, "start");

        // Cancel any thread attempting to make a connection
        if (mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }

        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        // Start the thread to listen on a BluetoothServerSocket
        if (mAcceptThread == null) {
            mAcceptThread = new AcceptThread();
            mAcceptThread.start();
        }
        setState(STATE_LISTEN);
    }

    /**
     * Start the ConnectThread to initiate a connection to a remote device.
     * @param device  The BluetoothDevice to connect
     */
    public synchronized void connect(BluetoothDevice device) {
        if (D) Log.d(TAG, "connect to: " + device);

        // Cancel any thread attempting to make a connection
        if (mState == STATE_CONNECTING) {
            if (mConnectThread != null) {
                mConnectThread.cancel();
                mConnectThread = null;
            }
        }

        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        // Start the thread to connect with the given device
        mConnectThread = new ConnectThread(device);
        mConnectThread.start();
        setState(STATE_CONNECTING);
    }

    /**
     * Start the ConnectedThread to begin managing a Bluetooth connection
     * @param socket  The BluetoothSocket on which the connection was made
     * @param device  The BluetoothDevice that has been connected
     */
    public synchronized void connected(BluetoothSocket socket, BluetoothDevice device) {
        if (D) Log.d(TAG, "connected");

        // Cancel the thread that completed the connection
        if (mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }

        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        // Cancel the accept thread because we only want to connect to one device
        if (mAcceptThread != null) {
            mAcceptThread.cancel();
            mAcceptThread = null;
        }

        // Start the thread to manage the connection and perform transmissions
        mConnectedThread = new ConnectedThread(socket);
        mConnectedThread.start();

        // Send the name of the connected device back to the UI Activity
        Message msg = mHandler.obtainMessage(MESSAGE_DEVICE_NAME);
        Bundle bundle = new Bundle();
        if (ActivityCompat.checkSelfPermission(contexto, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.

        }
        bundle.putString(DEVICE_NAME, device.getName());
        msg.setData(bundle);
        mHandler.sendMessage(msg);

        setState(STATE_CONNECTED);
    }

    /**
     * Stop all threads
     */
    public synchronized void stop() {
        if (D) Log.d(TAG, "stop");
        if (mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }
        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }
        if (mAcceptThread != null) {
            mAcceptThread.cancel();
            mAcceptThread = null;
        }
        setState(STATE_NONE);
    }

    /**
     * Write to the ConnectedThread in an unsynchronized manner
     * @param out The bytes to write
     * @see ConnectedThread#write(byte[])
     */
    public void write(byte[] out) {
        // Create temporary object
        ConnectedThread r;
        // Synchronize a copy of the ConnectedThread
        synchronized (this) {
            if (mState != STATE_CONNECTED) return;
            r = mConnectedThread;
        }
        // Perform the write unsynchronized
        r.write(out);
    }

    /**
     * Indicate that the connection attempt failed and notify the UI Activity.
     */
    private void connectionFailed() {
        setState(STATE_LISTEN);

        // Send a failure message back to the Activity
        Message msg = mHandler.obtainMessage(MESSAGE_TOAST);
        Bundle bundle = new Bundle();
        bundle.putString(TOAST, "Unable to connect device");
        msg.setData(bundle);
        mHandler.sendMessage(msg);
    }

    /**
     * Indicate that the connection was lost and notify the UI Activity.
     */
    private void connectionLost() {
        setState(STATE_LISTEN);

        // Send a failure message back to the Activity
        Message msg = mHandler.obtainMessage(MESSAGE_TOAST);
        Bundle bundle = new Bundle();
        bundle.putString(TOAST, "Device connection was lost");
        msg.setData(bundle);
        mHandler.sendMessage(msg);
    }

    /**
     * This thread runs while listening for incoming connections. It behaves
     * like a server-side client. It runs until a connection is accepted
     * (or until cancelled).
     */
    private class AcceptThread extends Thread {
        // The local server socket
        private final BluetoothServerSocket mmServerSocket;

        public AcceptThread() {
            BluetoothServerSocket tmp = null;

            // Create a new listening server socket
            try {
                if (ActivityCompat.checkSelfPermission(contexto, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    // TODO: Consider calling
                    //    ActivityCompat#requestPermissions
                    // here to request the missing permissions, and then overriding
                    //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                    //                                          int[] grantResults)
                    // to handle the case where the user grants the permission. See the documentation
                    // for ActivityCompat#requestPermissions for more details.

                }
                tmp = mAdapter.listenUsingRfcommWithServiceRecord(NAME, MY_UUID);
            } catch (IOException e) {
                Log.e(TAG, "listen() failed", e);
            }
            mmServerSocket = tmp;
        }

        public void run() {
            if (D) Log.d(TAG, "BEGIN mAcceptThread" + contexto);
            setName("AcceptThread");
            BluetoothSocket socket = null;

            // Listen to the server socket if we're not connected
            while (mState != STATE_CONNECTED) {
                try {
                    // This is a blocking call and will only return on a
                    // successful connection or an exception
                    socket = mmServerSocket.accept();
                } catch (IOException e) {
                    Log.e(TAG, "accept() failed", e);
                    break;
                }

                // If a connection was accepted
                if (socket != null) {
                    synchronized (BluetoothReaderService.this) {
                        switch (mState) {
                            case STATE_LISTEN:
                            case STATE_CONNECTING:
                                // Situation normal. Start the connected thread.
                                connected(socket, socket.getRemoteDevice());
                                break;
                            case STATE_NONE:
                            case STATE_CONNECTED:
                                // Either not ready or already connected. Terminate new socket.
                                try {
                                    socket.close();
                                } catch (IOException e) {
                                    Log.e(TAG, "Could not close unwanted socket", e);
                                }
                                break;
                        }
                    }
                }
            }
            if (D) Log.i(TAG, "END mAcceptThread");
        }

        public void cancel() {
            if (D) Log.d(TAG, "cancel " + this);
            try {
                mmServerSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "close() of server failed", e);
            }
        }
    }


    /**
     * This thread runs while attempting to make an outgoing connection
     * with a device. It runs straight through; the connection either
     * succeeds or fails.
     */
    private class ConnectThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;

        public ConnectThread(BluetoothDevice device) {
            mmDevice = device;
            BluetoothSocket tmp = null;

            // Get a BluetoothSocket for a connection with the
            // given BluetoothDevice
            try {
                if (ActivityCompat.checkSelfPermission(contexto, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    // TODO: Consider calling
                    //    ActivityCompat#requestPermissions
                    // here to request the missing permissions, and then overriding
                    //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                    //                                          int[] grantResults)
                    // to handle the case where the user grants the permission. See the documentation
                    // for ActivityCompat#requestPermissions for more details.

                }
                tmp = device.createInsecureRfcommSocketToServiceRecord(MY_UUID);
            } catch (IOException e) {
                Log.e(TAG, "create() failed", e);
            }
            mmSocket = tmp;
        }

        public void run() {
            Log.i(TAG, "BEGIN mConnectThread");
            setName("ConnectThread");

            // Always cancel discovery because it will slow down a connection
            if (ActivityCompat.checkSelfPermission(contexto, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.

            }
            mAdapter.cancelDiscovery();

            // Make a connection to the BluetoothSocket
            try {
                // This is a blocking call and will only return on a
                // successful connection or an exception
                mmSocket.connect();
            } catch (IOException e) {
                connectionFailed();
                // Close the socket
                try {
                    mmSocket.close();
                } catch (IOException e2) {
                    Log.e(TAG, "unable to close() socket during connection failure", e2);
                }
                // Start the service over to restart listening mode
                BluetoothReaderService.this.start();
                return;
            }

            // Reset the ConnectThread because we're done
            synchronized (BluetoothReaderService.this) {
                mConnectThread = null;
            }

            // Start the connected thread
            connected(mmSocket, mmDevice);
        }

        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "close() of connect socket failed", e);
            }
        }
    }

    public String toHexString(byte[] data, int size) {
        StringBuffer buffer = new StringBuffer();
        for(int i=0;i<size;i++){
            String s = Integer.toHexString(data[i] & 0xFF);
            if (s.length() == 1) {
                buffer.append("0" + s);
            } else {
                buffer.append(s);
            }
        }
        return buffer.toString();
    }
    /**
     * This thread runs during a connection with a remote device.
     * It handles all incoming and outgoing transmissions.
     */
    private class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;

        public ConnectedThread(BluetoothSocket socket) {
            Log.d(TAG, "create ConnectedThread");
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            // Get the BluetoothSocket input and output streams
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                Log.e(TAG, "temp sockets not created", e);
            }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;

            mInStream = tmpIn;
            mOutStream = tmpOut;
        }

        public void run() {
            Log.i(TAG, "BEGIN mConnectedThread");
            byte[] buffer = new byte[256];
            int bytes;

            // Keep listening to the InputStream while connected
            while (true) {
                try {
                    bytes = mmInStream.read(buffer);
                    ReceiveCommand(buffer,bytes);
                } catch (IOException e) {
                    Log.e(TAG, "disconnected", e);
                    connectionLost();
                    break;
                }
            }
        }

        /**
         * Write to the connected OutStream.
         * @param buffer  The bytes to write
         */
        public void write(byte[] buffer) {
            try {
                mmOutStream.write(buffer);
            } catch (IOException e) {
                Log.e(TAG, "Exception during write", e);
            }
        }

        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "close() of connect socket failed", e);
            }
        }
    }

    public boolean writestream(byte[] buffer)
    {
        boolean ret=false;
        if(mState == STATE_CONNECTED)
        {
            try {
                mOutStream.write(buffer);
                ret=true;
            } catch (IOException e) {
                Log.e(TAG, "Exception during write", e);
            }
        }
        return ret;
    }

    public int readstream(byte[] buffer)
    {
        int bytes=0;

        if(mState == STATE_CONNECTED)
        {
            try {
                bytes=mInStream.read(buffer);
            } catch (IOException e) {
                Log.e(TAG, "Exception during read", e);
            }
        }

        return bytes;
    }
    /////////////////////////////////////////////////////////////////////////////////////////////////////////////
    public void TimeOutStart(int tm) {
        if(mTimerTimeout!=null){
            return;
        }
        mTimerTimeout = new Timer();
        mHandlerTimeout = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                TimeOutStop();
                if(mIsWork){
                    mIsWork=false;
                    mHandler.obtainMessage(MESSAGE_TIMEOUT).sendToTarget();
                }
                super.handleMessage(msg);
            }
        };
        mTaskTimeout = new TimerTask() {
            @Override
            public void run() {
                Message message = new Message();
                message.what = 1;
                mHandlerTimeout.sendMessage(message);
            }
        };
        mTimerTimeout.schedule(mTaskTimeout, tm, tm);
    }

    public void TimeOutStop() {
        if (mTimerTimeout!=null) {
            mTimerTimeout.cancel();
            mTimerTimeout = null;
            mTaskTimeout.cancel();
            mTaskTimeout=null;
        }
    }


    private byte[] changeByte(int data) {
        byte b4 = (byte) ((data) >> 24);
        byte b3 = (byte) (((data) << 8) >> 24);
        byte b2 = (byte) (((data) << 16) >> 24);
        byte b1 = (byte) (((data) << 24) >> 24);
        byte[] bytes = { b1, b2, b3, b4 };
        return bytes;
    }

    private byte[] toBmpByte(int width, int height, byte[] data) {
        byte[] buffer = null;
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);

            int bfType = 0x424d;
            int bfSize = 54 + 1024 + width * height;
            int bfReserved1 = 0;
            int bfReserved2 = 0;
            int bfOffBits = 54 + 1024;

            dos.writeShort(bfType);
            dos.write(changeByte(bfSize), 0, 4);
            dos.write(changeByte(bfReserved1), 0, 2);
            dos.write(changeByte(bfReserved2), 0, 2);
            dos.write(changeByte(bfOffBits), 0, 4);

            int biSize = 40;
            int biWidth = width;
            int biHeight = height;
            int biPlanes = 1;
            int biBitcount = 8;
            int biCompression = 0;
            int biSizeImage = width * height;
            int biXPelsPerMeter = 0;
            int biYPelsPerMeter = 0;
            int biClrUsed = 256;
            int biClrImportant = 0;

            dos.write(changeByte(biSize), 0, 4);
            dos.write(changeByte(biWidth), 0, 4);
            dos.write(changeByte(biHeight), 0, 4);
            dos.write(changeByte(biPlanes), 0, 2);
            dos.write(changeByte(biBitcount), 0, 2);
            dos.write(changeByte(biCompression), 0, 4);
            dos.write(changeByte(biSizeImage), 0, 4);
            dos.write(changeByte(biXPelsPerMeter), 0, 4);
            dos.write(changeByte(biYPelsPerMeter), 0, 4);
            dos.write(changeByte(biClrUsed), 0, 4);
            dos.write(changeByte(biClrImportant), 0, 4);

            byte[] palatte = new byte[1024];
            for (int i = 0; i < 256; i++) {
                palatte[i * 4] = (byte) i;
                palatte[i * 4 + 1] = (byte) i;
                palatte[i * 4 + 2] = (byte) i;
                palatte[i * 4 + 3] = 0;
            }
            dos.write(palatte);

            dos.write(data);
            dos.flush();
            buffer = baos.toByteArray();
            dos.close();
            baos.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return buffer;
    }

    public byte[] getFingerprintImage(byte[] data,int width,int height,int offset) {
        if (data == null) {
            return null;
        }
        byte[] imageData = new byte[width*height];
        for (int i = 0; i < (width*height/2); i++) {
            imageData[i * 2] = (byte) (data[i+offset] & 0xf0);
            imageData[i * 2 + 1] = (byte) (data[i+offset] << 4 & 0xf0);
        }
        byte[] bmpData = toBmpByte(width, height, imageData);
        return bmpData;
    }

    public void memcpy(byte[] dstbuf,int dstoffset,byte[] srcbuf,int srcoffset,int size) {
        for(int i=0;i<size;i++) {
            dstbuf[dstoffset+i]=srcbuf[srcoffset+i];
        }
    }

    private int calcCheckSum(byte[] buffer,int size) {
        int sum=0;
        for(int i=0;i<size;i++) {
            sum=sum+buffer[i];
        }
        return (sum & 0x00ff);
    }

    private void SendCommand(byte cmdid,byte[] data,int size) {
        if(mIsWork)
            return;
        int sendsize=9+size;
        byte[] sendbuf = new byte[sendsize];
        sendbuf[0]='F';
        sendbuf[1]='T';
        sendbuf[2]=0;
        sendbuf[3]=0;
        sendbuf[4]=cmdid;
        sendbuf[5]=(byte)(size);
        sendbuf[6]=(byte)(size>>8);
        if(size>0) {
            for(int i=0;i<size;i++) {
                sendbuf[7+i]=data[i];
            }
        }
        int sum=calcCheckSum(sendbuf,(7+size));
        sendbuf[7+size]=(byte)(sum);
        sendbuf[8+size]=(byte)(sum>>8);

        mIsWork=true;
        mDeviceCmd=cmdid;
        mCmdSize=0;
        if((mDeviceCmd==CMD_ENROLID)||
                (mDeviceCmd==CMD_VERIFY)||
                (mDeviceCmd==CMD_IDENTIFY)||
                (mDeviceCmd==CMD_ENROLHOST)||
                (mDeviceCmd==CMD_CAPTUREHOST)||
                (mDeviceCmd==CMD_GETIMAGE)
        ){
            TimeOutStart(12000);
        }else{
            TimeOutStart(2000);
        }


        write(sendbuf);

    }

    private void ReceiveCommand(byte[] databuf,int datasize){
        if(mDeviceCmd==CMD_GETIMAGE) {
            memcpy(mUpImage,mUpImageSize,databuf,0,datasize);
            mUpImageSize=mUpImageSize+datasize;
            //if(mUpImageSize>=36864){
            if(mUpImageSize>=mUpImageTotal){
                mIsWork=false;
                TimeOutStop();
                mHandler.obtainMessage(CMD_GETIMAGE,1,mUpImageTotal, mUpImage).sendToTarget();
            }
        }else{
            System.arraycopy(databuf, 0, mCmdData, mCmdSize, datasize);
            mCmdSize=mCmdSize+datasize;
            Log.e(TAG, "ReceiveCommand:"+toHexString(mCmdData,mCmdSize));

            byte tmpcmd[]=new byte[1024];
            int tmpsize=0;
            int   mCmdTotal=0;
    		/*
        	for(int i=0;i<mCmdSize-4;i++){
        		if((mCmdData[0+i]==0x46)&&(mCmdData[1+i]==0x54)
        				&&(mCmdData[2+i]==0x00)&&(mCmdData[3+i]==0x00)){
        			tmpsize=mCmdSize-i;
        			System.arraycopy(mCmdData, i, tmpcmd, 0, tmpsize);
        			mCmdTotal=(tmpcmd[5]& 0x00FF)+((tmpcmd[6]<<8)&0xFF00)+9;
        			break;
            	}
        	}
        	if(mCmdTotal>=(tmpsize-2)){
				ProecessCmd(tmpcmd,tmpsize);
				mCmdSize=0;
				mCmdTotal=0;
			}
        	*/

            ///*
            if((mCmdData[0]=='F')&&(mCmdData[1]=='T')){
                mCmdTotal=(mCmdData[5]& 0x00FF)+((mCmdData[6]<<8)&0xFF00)+9;
            }

            Log.e(TAG, "Size:"+ String.valueOf(mCmdSize)+"/"+ String.valueOf(mCmdTotal));

            if(mCmdTotal>=(mCmdSize-2)){
                ProecessCmd(mCmdData,mCmdSize);
                mCmdSize=0;
                mCmdTotal=0;
            }
            //*/
        }

    }

    public void ProecessCmd(byte[] cmddata,int cmdsize){
        mIsWork=false;
        TimeOutStop();
        if((cmddata[0]=='F')&&(cmddata[1]=='T')){
            switch(cmddata[4]) {
                case CMD_PASSWORD:
                    break;
                case CMD_ENROLID: {
                    if(cmddata[7]==1) {
                        int id=(cmddata[8]& 0x00FF)+(byte)((cmddata[9]<<8)&0xFF00);
                        mHandler.obtainMessage(CMD_ENROLID, 1, id).sendToTarget();
                    }else
                        mHandler.obtainMessage(CMD_ENROLID, 0, 0).sendToTarget();
                }
                break;
                case CMD_VERIFY: {
                    if(cmddata[7]==1)
                        mHandler.obtainMessage(CMD_VERIFY, 1,0).sendToTarget();
                    else
                        mHandler.obtainMessage(CMD_VERIFY, 0,0).sendToTarget();
                }
                break;
                case CMD_IDENTIFY: {
                    if(cmddata[7]==1) {
                        int id=(byte)(cmddata[8])+(byte)((cmddata[9]<<8)&0xFF00);
                        mHandler.obtainMessage(CMD_IDENTIFY, 1, id).sendToTarget();
                    } else
                        mHandler.obtainMessage(CMD_IDENTIFY, 0, 0).sendToTarget();
                }
                break;
                case CMD_DELETEID:
                {
                    if(cmddata[7]==1)
                        mHandler.obtainMessage(CMD_DELETEID, 1,0).sendToTarget();
                    else
                        mHandler.obtainMessage(CMD_DELETEID, 0,0).sendToTarget();
                }
                break;
                case CMD_CLEARID: {
                    if(cmddata[7]==1)
                        mHandler.obtainMessage(CMD_CLEARID, 1,0).sendToTarget();
                    else
                        mHandler.obtainMessage(CMD_CLEARID, 0,0).sendToTarget();
                }
                break;
                case CMD_ENROLHOST: {
                    int size=(byte)(cmddata[5])+((cmddata[6]<<8)&0xFF00)-1;
                    if(cmddata[7]==1) {
                        memcpy(mRefData,0,cmddata,8,size);
                        mRefSize=size;
                        mHandler.obtainMessage(CMD_ENROLHOST,1,size, mRefData).sendToTarget();
                        //ISO Format
                        //String bsiso=Conversions.getInstance().IsoChangeCoord(mRefData, 1);
                    }else
                        mHandler.obtainMessage(CMD_ENROLHOST, 0,0).sendToTarget();
                }
                break;
                case CMD_CAPTUREHOST: {
                    int size=(byte)(cmddata[5])+((cmddata[6]<<8)&0xFF00)-1;
                    if(cmddata[7]==1) {
                        memcpy(mMatData,0,cmddata,8,size);
                        mMatSize=size;
                        mHandler.obtainMessage(CMD_CAPTUREHOST,1,size, mMatData).sendToTarget();
                        //ISO Format
                        //String bsiso=Conversions.getInstance().IsoChangeCoord(mMatData, 1);
                    }
                    else
                        mHandler.obtainMessage(CMD_CAPTUREHOST, 0,0).sendToTarget();
                }
                break;
                case CMD_MATCH:	{
                    if(cmddata[7]==1){
                        int score=(byte)(cmddata[8])+((cmddata[9]<<8)&0xFF00);
                        mHandler.obtainMessage(CMD_MATCH, 1,score).sendToTarget();
                    }else
                        mHandler.obtainMessage(CMD_MATCH, 0,0).sendToTarget();
                }
                break;
                case CMD_WRITEFPCARD: {
                    if(cmddata[7]==1)
                        mHandler.obtainMessage(CMD_WRITEFPCARD, 1,0).sendToTarget();
                    else
                        mHandler.obtainMessage(CMD_WRITEFPCARD, 0,0).sendToTarget();
                }
                break;
                case CMD_READFPCARD: {
                    int size=(byte)(cmddata[5])+((cmddata[6]<<8)&0xFF00);
                    if(size>0)
                    {
                        memcpy(mCardData,0,cmddata,8,size);
                        mCardSize=size;
                        mHandler.obtainMessage(CMD_READFPCARD,1,size, mCardData).sendToTarget();
                    }
                    else
                        mHandler.obtainMessage(CMD_READFPCARD,0,0).sendToTarget();
                }
                break;
                case CMD_FPCARDMATCH:{
                    if(cmddata[7]==1){
                        int size=(byte)(cmddata[5])+((cmddata[6]<<8)&0xFF00)-1;
                        byte[] tmpbuf=new byte[size];
                        memcpy(tmpbuf,0,cmddata,8,size);
                        mHandler.obtainMessage(CMD_READFPCARD,1,size, tmpbuf).sendToTarget();
                    }
                    else
                        mHandler.obtainMessage(CMD_FPCARDMATCH,0,0).sendToTarget();
                }
                break;
                case CMD_UPCARDSN:
                case CMD_CARDSN: {
                    int size=(byte)(cmddata[5])+((cmddata[6]<<8)&0xF0)-1;
                    if(size>0) {
                        memcpy(mCardSn,0,cmddata,8,size);
                        mHandler.obtainMessage(CMD_CARDSN,1,size, mCardSn).sendToTarget();
                    }
                    else
                        mHandler.obtainMessage(CMD_CARDSN,0,0).sendToTarget();
                }
                break;
                case CMD_WRITEDATACARD: {
                    if(cmddata[7]==1)
                        mHandler.obtainMessage(CMD_WRITEDATACARD,1,0).sendToTarget();
                    else
                        mHandler.obtainMessage(CMD_WRITEDATACARD,0,0).sendToTarget();
                }
                break;
                case CMD_READDATACARD: {
                    int size=(byte)(cmddata[5])+((cmddata[6]<<8)&0xFF00);
                    if(size>0)
                    {
                        memcpy(mCardData,0,cmddata,8,size);
                        mCardSize=size;
                        mHandler.obtainMessage(CMD_READDATACARD,1,size, mCardData).sendToTarget();
                    }
                    else
                        mHandler.obtainMessage(CMD_READDATACARD,0,0).sendToTarget();
                }
                break;
                case CMD_GETSN:{
                    int size=(byte)(cmddata[5])+((cmddata[6]<<8)&0xFF00)-1;
                    if(cmddata[7]==1) {
                        byte[] snb=new byte[32];
                        memcpy(snb,0,cmddata,8,size);
                        //String sn = null;
                        //try {
                        //	sn = new String(snb,0,size,"UNICODE");
                        //} catch (UnsupportedEncodingException e) {
                        //	e.printStackTrace();
                        //}
                        mHandler.obtainMessage(CMD_GETSN,1,size, snb).sendToTarget();
                    }
                    else
                        mHandler.obtainMessage(CMD_GETSN,0,0).sendToTarget();
                }
                break;
                case CMD_PRINTCMD:{
                    if(cmddata[7]==1){
                        mHandler.obtainMessage(CMD_PRINTCMD,1,0).sendToTarget();
                    }else{
                        mHandler.obtainMessage(CMD_PRINTCMD,0,0).sendToTarget();
                    }
                }
                break;
                case CMD_GETBAT:{
                    int size=(byte)(cmddata[5])+((cmddata[6]<<8)&0xFF00)-1;
                    if(size>0)
                    {
                        memcpy(mBat,0,cmddata,8,size);
                        //AddStatusList("Battery Value:"+Integer.toString(mBat[0]/10)+"."+Integer.toString(mBat[0]%10)+"V");
                        mHandler.obtainMessage(CMD_GETBAT,1,size, mBat).sendToTarget();
                    }else
                        mHandler.obtainMessage(CMD_GETBAT,0,0).sendToTarget();
                }
                break;
                case CMD_GETVERSION: {
                    int size = (byte) (cmddata[5]) + ((cmddata[6] << 8) & 0xFF00) - 1;
                    if (cmddata[7] == 1) {
                        size=size+1;
                        memcpy(mVersion, 0, cmddata, 8, size);
                        mHandler.obtainMessage(CMD_GETVERSION,1,size, mVersion).sendToTarget();
                    } else
                        mHandler.obtainMessage(CMD_GETVERSION,0,0).sendToTarget();
                }
                break;
                case CMD_SHUTDOWNDEVICE: {
                    if (cmddata[7] == 1) {
                        mHandler.obtainMessage(CMD_SHUTDOWNDEVICE,1,0).sendToTarget();
                    } else
                        mHandler.obtainMessage(CMD_SHUTDOWNDEVICE,0,0).sendToTarget();
                }
                break;
                case CMD_GETCHAR:{
                    int size=(byte)(cmddata[5])+((cmddata[6]<<8)&0xFF00)-1;
                    if(cmddata[7]==1) {
                        memcpy(mMatData,0,cmddata,8,size);
                        mMatSize=size;
                        mHandler.obtainMessage(CMD_GETCHAR,1,size, mMatData).sendToTarget();
                    }
                    else
                        mHandler.obtainMessage(CMD_GETCHAR,0,0).sendToTarget();
                }
                break;
                case CMD_GETIMAGESIZE:{
                    if(cmddata[7]==1) {
                        int size=(byte)(cmddata[5])+((cmddata[6]<<8)&0xFF00)-1;
                        mUpImageTotal=(byte)(cmddata[8])+((cmddata[9]<<8)&0xFF00);
                        mHandler.obtainMessage(CMD_GETIMAGESIZE,1,mUpImageTotal).sendToTarget();
    					/*
    					int getsize=128;
    					byte[] data = new byte[4];
    					data[0]=(byte)(mUpImageSize);
    					data[1]=(byte)(mUpImageSize>>8);
    					data[2]=(byte)(getsize);
    					data[3]=(byte)(getsize>>8);
    					SendCommand(CMD_GETIMAGEDATA,data,4);
    					*/
                        //GetImageData(mUpImageSize,128);
                    }else{
                        mHandler.obtainMessage(CMD_GETIMAGESIZE,0,0).sendToTarget();
                    }
                }
                break;
                case CMD_GETIMAGEDATA:{
                    if(cmddata[7]==1) {
                        int size=(cmddata[5]& 0x00FF)+((cmddata[6]<<8)&0xFF00)-1;
                        memcpy(mUpImage,mUpImageSize,cmddata,8,size);
                        mUpImageSize=mUpImageSize+size;

                        if(mUpImageSize>=mUpImageTotal){

                            //switch(mUpImageTotal){
                            //case 36864:
                            //	break;
                            //}
	    						/*
	    						byte[] bmpdata=getFingerprintImage(mUpImage,256,288,0);
	    						Bitmap image = BitmapFactory.decodeByteArray(bmpdata, 0,bmpdata.length);
	    						fingerprintImage.setImageBitmap(image);
	    						mUpImageSize=0;
	    						mUpImageCount=mUpImageCount+1;
	    						AddStatusList("Display Image");
	    						*/
                            mHandler.obtainMessage(CMD_GETIMAGEDATA,1,mUpImageTotal, mUpImage).sendToTarget();
                        }else{
	    						/*
	    						int getsize=128;
		    					byte[] data = new byte[4];
		    					data[0]=(byte)(mUpImageSize);
		    					data[1]=(byte)(mUpImageSize>>8);
		    					data[2]=(byte)(getsize);
		    					data[3]=(byte)(getsize>>8);
		    					SendCommand(CMD_GETIMAGEDATA,data,4);
		    					*/
                            //GetImageDataEx(128);
                            mHandler.obtainMessage(CMD_GETIMAGEDATA,2,0).sendToTarget();
                        }
                    }else{
                        mHandler.obtainMessage(CMD_GETIMAGEDATA,0,0).sendToTarget();
                    }
                }
                break;
            }
        }
    }

    public void EnrolToHost(){
        SendCommand(CMD_ENROLHOST,null,0);
    }

    public void CaptureToHost(){
        SendCommand(CMD_CAPTUREHOST,null,0);
    }

    public void MatchInDevice(byte[] mRefData,byte[] mMatData){
        byte buf[]=new byte[1024];
        memcpy(buf,0,mRefData,0,512);
        memcpy(buf,512,mMatData,0,512);
        System.arraycopy(mRefData, 0, buf, 0, 512);
        System.arraycopy(mMatData, 0, buf, 512, 256);
        SendCommand(CMD_MATCH,buf,1024);
    }

    public int MatchTemplate(byte[] mRefData,byte[] mMatData){
        if(Conversions.getInstance().GetDataType(mRefData)==1){
            return FPMatch.getInstance().MatchTemplate(mRefData,mMatData);
        }else{
            byte[] fa=new byte[512];
            byte[] fb=new byte[512];
            Conversions.getInstance().IsoToStd(2,mRefData,fa);
            Conversions.getInstance().IsoToStd(2,mMatData,fb);
            return FPMatch.getInstance().MatchTemplate(fa,fb);
        }
    }

    public void WriteFingrpirntCard(byte[] mRefData){
        byte buf[]=new byte[2048];
        memcpy(buf,0,mRefData,0,512);
        String str="Test";
        byte tb[]=str.getBytes();
        memcpy(buf,1024,tb,0,tb.length);
        SendCommand(CMD_WRITEFPCARD,buf,1024+tb.length);
    }

    public void MatchFingerprintCard(){
        SendCommand(CMD_FPCARDMATCH,null,0);
    }

    public void ReadFingerprintCard(){
        SendCommand(CMD_READFPCARD,null,0);
    }

    public void WriteCardData(byte[] data,int size){
        SendCommand(CMD_WRITEDATACARD,data,size);
    }

    public void ReadCardData(){
        SendCommand(CMD_READDATACARD,null,0);
    }

    public void ReadCardSn(){
        SendCommand(CMD_CARDSN,null,0);
    }

    public void GetDeviceSn(){
        SendCommand(CMD_GETSN,null,0);
    }

    public void GetBatVal(){
        SendCommand(CMD_GETBAT,null,0);
    }

    public void GetDeviceVer(){
        SendCommand(CMD_GETVERSION,null,0);
    }

    public void CloseDevvice(){
        SendCommand(CMD_SHUTDOWNDEVICE,null,0);
    }

    public void GetImageTemplate(int imagesize){
        mUpImageSize=0;
        GetImageData(imagesize);
    }

    public void GetImageData(int imagesize){
        mUpImageSize=0;
        mUpImageTotal=imagesize;
        SendCommand(CMD_GETIMAGE,null,0);
    }

    public void GetTemplate(){
        SendCommand(CMD_GETCHAR,null,0);
    }

    public void GetImageSize(){
        mUpImageSize=0;
        SendCommand(CMD_GETIMAGESIZE,null,0);
    }

    public void GetImageDataEx(int size){
        byte[] data = new byte[4];
        data[0]=(byte)(mUpImageSize);
        data[1]=(byte)(mUpImageSize>>8);
        data[2]=(byte)(size);
        data[3]=(byte)(size>>8);
        SendCommand(CMD_GETIMAGEDATA,data,4);
    }

    public void EnrolInModule(int id){
        byte buf[] = new byte[2];
        buf[0] = (byte) (id);
        buf[1] = (byte) (id >> 8);
        SendCommand(CMD_ENROLID, buf, 2);
    }

    public void VerifyInModule(int id){
        byte buf[] = new byte[2];
        buf[0] = (byte) (id);
        buf[1] = (byte) (id >> 8);
        SendCommand(CMD_VERIFY, buf, 2);
    }

    public void SearchInModule(){
        SendCommand(CMD_IDENTIFY, null, 0);
    }

    public void DeleteInModule(int id){
        byte buf[] = new byte[2];
        buf[0] = (byte) (id);
        buf[1] = (byte) (id >> 8);
        SendCommand(CMD_DELETEID, buf, 2);
    }

    public void ClearModule(){
        SendCommand(CMD_CLEARID,null,0);
    }
}