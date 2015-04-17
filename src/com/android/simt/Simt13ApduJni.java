package com.android.simt;

public class Simt13ApduJni 
{
	public native int init(byte[] filePath);
	public native int bind();
	public native int unbind(); 
	public native int connect();
	public native int disconnect();
	public native int atr(byte[] outBuffer, int outBufferLength);
	public native int apduSend(byte[] sendBuffer, int sendLength);
	public native int apduReceive(byte[] receiveBuffer, int receiveBufferLength);
	public native int transmit(int mode, int HeaderLength, byte[] Iso7816CmdHeader, int transmitLength, byte[] dataBuffer,  int receiveBufferLength, byte[] receiveBuffer);
	public native int getError();
//	public native boolean isKitKatPatched();
	public native int testMySD(byte[] filePath);
    static {
        System.loadLibrary("simt13api-jni");
    }
} 