package com.android.simt;


import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.text.Editable;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Adapter;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

	public static final String TAG = "MainActivity";
	private final static byte[] hex = "0123456789abcdef".getBytes();
	private final static int MAX_READ_BUFFER_SIZE = 256;
	private final static int SDC_CMD_T0 = 0;
//	private final static String SDCARD_MOUNTED_DIR = System.getenv("SECONDARY_STORAGE");
	private final static String SDCARD_MOUNTED_DIR = "/storage/sdcard1";
	private final static String SDCARD_SYSTEM_FILE_NAME = "MPAY_SSD.SYS";
	private final static String CMD_FILE_PATH = Environment.
			getExternalStorageDirectory().toString() + "/apducmd.txt";
	private final static String PATCH_URL = 
			"https://android-review.googlesource.com/#/c/82570/";
	
	private TextView msgTv;
	private Spinner cmdSp;
	private Map<String, String> cmdMap;
	private List<String> cmdNames;
	private ArrayAdapter<String> cmdAdapter;
	private SharedPreferences preference;
	private boolean sdCardInited;
	private Simt13ApduJni apduJni;
	
	private Handler handler;
	
	private String myPath;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		Log.e(TAG, "onCreate");
		msgTv = (TextView)findViewById(R.id.msg_tv);
		cmdSp = (Spinner)findViewById(R.id.cmd_spinner);
		
		preference = getSharedPreferences("cmd", MODE_PRIVATE);
		sdCardInited = false;
		
		apduJni = new Simt13ApduJni();
		
		handler = new Handler();
		handler.post(new Runnable() {
			
			@Override
			public void run() {
				// TODO Auto-generated method stub
				initCmdSpinner();
			}
		});
		
		
	}
	
	@Override
	protected void onDestroy() {
		// TODO Auto-generated method stub
		apduJni.disconnect();
		apduJni.unbind();
		super.onDestroy();
	}
	
	public void onSDCheckBtClicked(View v) {
		
		if(!findSDCard())
			return;
		
		apduJni.disconnect();
		apduJni.unbind();
//		loadSDKeyDllToMyDir();
		
		if(!SDInit())
			return;
		if(!SDBind())
			return;
		if(!SDConnect()) {
			apduJni.unbind();
			return;
		}
		
		sdCardInited = true;
	}
	
	public void onClearBtClicked(View v) {
		
		msgTv.setText("");
		
	}
	
//	public void onIsPatchedBtClicked(View v) {
//		
//		if(apduJni.isKitKatPatched()) {
//			printMsg("No need to patch");
//			return;
//		}
//		printMsg("Install patch apk from the given url!");
//		Intent intent = new Intent();
//		intent.setAction("android.intent.action.VIEW");   
//		Uri content_url = Uri.parse(PATCH_URL);  
//		intent.setData(content_url); 
//		startActivity(intent);
//			
//	}
	
	public void onSendBtClicked(View v) {
		
		if(sdCardInited) {
			int position = cmdSp.getSelectedItemPosition();
			switch (position) {
				case 0:
					handler.post(new Runnable() {
						@Override
						public void run() {
							// TODO Auto-generated method stub
							art();
						}
					});
					break;
					
				default :
					final String name = cmdNames.get(position);
					final String cmd = cmdMap.get(name);
					handler.post(new Runnable() {
						@Override
						public void run() {
							// TODO Auto-generated method stub
							excuteCmd(cmd, name);
						}
					});
					break;
			}
		}
		else {
			msgTv.append("SDCard not inited,Check first\n");
		}
		
	}
	
	public void onLoadCMDBtClicked(View v) {
		
		File file = new File(CMD_FILE_PATH);
		if(!file.exists()) {
			printMsg("CMD File Not Foud");
			return;
		}
		
		try {
			FileInputStream fileInStr = new FileInputStream(new File(CMD_FILE_PATH));
			InputStreamReader inReader = new InputStreamReader(fileInStr);
			BufferedReader buffReeader = new BufferedReader(inReader);
			String line;
			
			SharedPreferences.Editor editor = preference.edit();
			while((line = buffReeader.readLine()) != null) {
				
				if(line.contains("#"))
					continue;
				
				if(!cmdNames.contains(line)) {
					cmdNames.add(line);
					cmdMap.put(line, line);
					editor.putString(line, line);
				}
					
			}
			editor.commit();
			cmdAdapter.notifyDataSetChanged();
			
			buffReeader.close();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public void onNewCMDBtClicked(View v) {
		showNewCmdDialog();
	}
	
	private void initCmdSpinner() {
		
		File cmdFile = new File(CMD_FILE_PATH);
		if(!cmdFile.exists())
			loadCMDFile();
		
		cmdMap = new HashMap<String, String>();
		cmdNames = new ArrayList<String>();
		
		try {
			
		cmdNames.add("ATR");
		
		Map<String, String> tempMap = (Map<String, String>) preference.getAll();
		cmdMap.putAll(tempMap);
		
		Set<String> set = cmdMap.keySet();
		List<String> tempList = new ArrayList<String>(set);
		cmdNames.addAll(tempList);
		
		cmdAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, cmdNames);
		
		cmdSp.setAdapter(cmdAdapter);
		} catch(Exception e) {
			e.printStackTrace();
		}
		
	}
	
	private void printBuffer(byte[] buf, int len) {
		
		byte[] msgbuf = new byte[len];
		for(int i = 0; i < len; i++)
			msgbuf[i] = buf[i];
		
		String msg = bytes2HexString(msgbuf);
		
		printMsg("Data:" + msg);
	}
	
	private void printMsg(String msg) {
		msgTv.append(msg + "\n");
	}
	
	public static byte[] hexStr2Bytes(String src){  
		/*对输入值进行规范化整理*/  
		src = src.trim().replace("#", "")
				.toUpperCase(Locale.US);  
		//处理值初始化  
		int m=0,n=0;  
		int iLen=src.length()/2; //计算长度  
		byte[] ret = new byte[iLen]; //分配存储空间  
		  
		for (int i = 0; i < iLen; i++){  
			m=i*2+1;  
			n=m+1;  
			ret[i] = (byte)(Integer.decode("0x"+ src.substring(i*2, m) + src.substring(m,n)) & 0xFF);  
		}  
		return ret;  
	}  
	
	private String bytes2HexString(byte[] b) {  
		
		byte[] buff = new byte[2 * b.length];  
		for (int i = 0; i < b.length; i++) {  
			buff[2 * i] = hex[(b[i] >> 4) & 0x0f];  
			buff[2 * i + 1] = hex[b[i] & 0x0f];  
		}  
		return new String(buff);  
	}  
	
	private void art() {
		
		int res = -1;
		int errorCode = -1;
		
		byte buffer[] = new byte[MAX_READ_BUFFER_SIZE];
		res = apduJni.atr(buffer, MAX_READ_BUFFER_SIZE);
		
		printMsg("ART");
		
		if(res == -1) {
			errorCode = apduJni.getError();
			printMsg("Error:" + Integer.toHexString(errorCode));
			return;
		}
		printBuffer(buffer, res);
	}
	
	private void excuteCmd(String cmd, String name) {
		
		printMsg(cmd);
		
		int res = -1;
		int errorCode = -1;
		
		byte[] cmdBuffer = hexStr2Bytes(cmd);
		byte[] readBuffer = new byte[MAX_READ_BUFFER_SIZE];
		
		byte[] headerBuffer = null;
		byte[] bodyBuffer = null;
		
		int totalLength = cmdBuffer.length;
		int headerLength = 0;
		int bodyLength = 0;
		
		if(totalLength <= 4) {
			
			headerLength = totalLength;
			bodyLength = 0;
			
			headerBuffer = new byte[headerLength];
			int i = 0;
			for(byte b : cmdBuffer)
				headerBuffer[i++] = b;
			
			bodyBuffer = new byte[5];
			
		}
		else {
			headerLength = 5;
			bodyLength = totalLength - 5;
			
			headerBuffer = new byte[headerLength];
			for(int i = 0; i < 5; i++)
				headerBuffer[i] = cmdBuffer[i];
			
			bodyBuffer = new byte[bodyLength + 1];
			for(int j = 5; j < totalLength; j ++)
				bodyBuffer[j - 5] = cmdBuffer[j];
			bodyBuffer[bodyLength] = 0;
		}
		
		long time = System.currentTimeMillis();
		res = apduJni.transmit(SDC_CMD_T0, headerLength, headerBuffer, 
				bodyLength, bodyBuffer, MAX_READ_BUFFER_SIZE, readBuffer);
		time = System.currentTimeMillis() - time;
		
//		res = apduJni.apduSend(cmdBuffer, cmdBuffer.length);
		
		if(res == -1) {
			errorCode = apduJni.getError();
			printMsg("Error:" + Integer.toHexString(errorCode));
		}
		else {
			printBuffer(readBuffer, res);
			String tmsg = String.format("Time:%dms\n", time);
			msgTv.append(tmsg);
		}
		
//		res = apduJni.apduReceive(readBuffer, MAX_READ_BUFFER_SIZE);
//		
//		if(res == -1) {
//			errorCode = apduJni.getError();
//			printMsg("Read error:" + Integer.toHexString(errorCode));
//			return;
//		}
	}
	
	@SuppressLint("NewApi")
	private boolean findSDCard() {
		
		File sdCard = getExternalSdcardDirectory();
				
		if(sdCard == null || !sdCard.canRead()) {
			printMsg("SDCard Not Find");
			return false;
		}
		
		if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
			
			File[] files = getExternalFilesDirs(null);
			Log.e(TAG, "file length" + files.length);
			for (File file : files) {
				Log.e(TAG, file.getAbsolutePath());
				String path = file.getAbsolutePath();
				if (path.contains(sdCard.toString())) {
					myPath = path;
				}
			}
		}
		else {
			myPath = sdCard.toString() + "/Android/data/" + getApplicationContext().getPackageName() +"/files";
		}
		Log.e(TAG, myPath);
		printMsg("SDCard Find");
		return true;
	}
	
	private boolean loadCMDFile() {
		
		File cmdFile = new File(CMD_FILE_PATH);
		try {
			FileOutputStream fo = new FileOutputStream(cmdFile);
			InputStream in = getAssets().open("apducmd.txt");
			
			byte[] buffer = new byte[1024];
			int i = 0;
			while((i = in.read(buffer)) > 0)
				fo.write(buffer);
			
			fo.flush();
			fo.close();
			in.close();
			return true;
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return false;
		
	}
	
	private boolean SDInit() {
		
		String initPath = myPath + "/";
		int res = -1;
		int errorCode = -1;
		
//		String initPath = "/mnt/extsd" + "/Android/data/" + getApplicationContext().getPackageName() +"/files/";
		Log.e(TAG, initPath);
		
		File file = new File(initPath);
		if(!file.exists())
			file.mkdirs();
		res = apduJni.init(initPath.getBytes());
		
		if(res == -1) {
			errorCode = apduJni.getError();
			printMsg("Init Error:" + Integer.toHexString(errorCode));
			return false;
		}
		
		printMsg("Init OK");
		return true;
	}
	
	private boolean SDBind() {
		
		int res = -1;
		int errorCode = -1;
		
		res = apduJni.bind();
		
		if(res == -1) {
			errorCode = apduJni.getError();
			printMsg("Bind Error:" + Integer.toHexString(errorCode));
			return false;
		}
		
		printMsg("Bind OK");
		return true;
	}
	
	private boolean SDConnect() {
		
		int res = -1;
		int errorCode = -1;
		
		res = apduJni.connect();
		
		if(res == -1) {
			errorCode = apduJni.getError();
			printMsg("Connect Error:" + Integer.toHexString(errorCode));
			return false;
		}
		
		printMsg("Connected");
		return true;
	}
	
public void showNewCmdDialog() {
		
		LayoutInflater layoutInflater = LayoutInflater.from(this);
		View dialogView = layoutInflater.inflate(R.layout.new_cmd_dialog, null);
		
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setView(dialogView);
		final AlertDialog newCmdDialog = builder.create();
		
		TextView cancelTv = (TextView) dialogView.findViewById(R.id.cancel_tv);
		TextView okTv = (TextView)dialogView.findViewById(R.id.ok_tv);
		
		final EditText name = (EditText)dialogView.findViewById(R.id.name_et);
		final EditText value = (EditText)dialogView.findViewById(R.id.value_et);
		
		cancelTv.setOnClickListener(new View.OnClickListener() {
			
			@Override
			public void onClick(View v) {
				// TODO Auto-generated method stub
				newCmdDialog.dismiss();
			}
		});
		
		okTv.setOnClickListener(new View.OnClickListener() {
			
			@Override
			public void onClick(View v) {
				// TODO Auto-generated method stub
				String cmdName = name.getText().toString();
				String cmd = value.getText().toString();
				
				if(cmdName.length() == 0)
					cmdName = cmd;
				
				if(cmd.length() == 0) {
					Toast.makeText(MainActivity.this, 
							"Please input your cmd", Toast.LENGTH_LONG).show();
					return;
				}
				
				SharedPreferences.Editor editor = preference.edit();
				editor.putString(cmdName, cmd);
				editor.commit();
				
				reloadCmds();
				newCmdDialog.dismiss();
				
			}
		});
		
		newCmdDialog.show();
	}

	private void reloadCmds() {
		
		cmdMap.clear();
		cmdNames.clear();
		
		cmdNames.add("ATR");
		
		Map<String, String> tempMap = (Map<String, String>) preference.getAll();
		cmdMap.putAll(tempMap);
		
		Set<String> set = cmdMap.keySet();
		List<String> tempList = new ArrayList<String>(set);
		cmdNames.addAll(tempList);
		
		cmdAdapter.notifyDataSetChanged();
	}
	
	@SuppressLint("DefaultLocale")
	private File getExternalSdcardDirectory() {

		File rootDir = null;
		File primaryStorage = Environment.getExternalStorageDirectory();
		Log.e(TAG, primaryStorage.getAbsolutePath());
		if(primaryStorage.toString().toLowerCase().contains("emulated"))
			rootDir = primaryStorage.getParentFile().getParentFile();
		else
			rootDir = primaryStorage.getParentFile();
		
		File[] files = rootDir.listFiles();
		for(File file : files) {
			String path = file.toString().toLowerCase();
			if(path.contains("sdcard") && !path.contains("sdcard0"))
				return file;
		}
		return primaryStorage;
	}
	
}
