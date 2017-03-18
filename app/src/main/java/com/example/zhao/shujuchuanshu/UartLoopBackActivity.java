package com.example.zhao.shujuchuanshu;
//3.0新增GPS信息读取功能

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.hardware.usb.UsbManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import com.wch.wchusbdriver.CH34xAndroidDriver;
import com.wch.wchusbdriver.R;

import java.io.IOException;
//串口通信activity

public class UartLoopBackActivity extends Activity {

	public static final String TAG = "com.wch.wchusbdriver";//
	private static final String ACTION_USB_PERMISSION = "com.wch.wchusbdriver.USB_PERMISSION";
	private LocationManager locationManager;
	/* thread to read the data */
	public readThread handlerThread;
	protected final Object ThreadLock = new Object();

	/* declare UART interface variable */
	public CH34xAndroidDriver uartInterface;//????

	EditText readText;
	EditText writeText;
	EditText sendText;
	EditText setIP;
	Spinner baudSpinner;
	Spinner stopSpinner;
	Spinner dataSpinner;
	Spinner paritySpinner;
	Spinner flowSpinner;
	Button writeButton, configButton;

	byte[] writeBuffer;
	char[] readBuffer;
	int actualNumBytes;

	/*int numBytes;
	byte count;
	int status;
	byte writeIndex = 0;
	byte readIndex = 0;*/

	//存储经纬度
	String temp1;
	String URL_PATH;

	int baudRate; /* baud rate *///波特率
	byte baudRate_byte; /* baud rate */ //send to hardware by AOA
	byte stopBit; /* 1:1stop bits, 2:2 stop bits */
	byte dataBit; /* 8:8bit, 7: 7bit 6: 6bit 5: 5bit*/
	byte parity; /* 0: none, 1: odd, 2: even, 3: mark, 4: space */
	byte flowControl; /* 0:none, 1: flow control(CTS,RTS) */
	//byte timeout; // time out
	public Context global_context;
	public boolean isConfiged = false;
	public boolean READ_ENABLE = false;
	public SharedPreferences sharePrefSettings;
	Drawable originalDrawable;
	public String act_string;


	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);


		locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
		if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
			// TODO: Consider calling
			//    ActivityCompat#requestPermissions
			// here to request the missing permissions, and then overriding
			//   public void onRequestPermissionsResult(int requestCode, String[] permissions,
			//                                          int[] grantResults)
			// to handle the case where the user grants the permission. See the documentation
			// for ActivityCompat#requestPermissions for more details.
			return;
		}
		Location location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
		if (location != null) {
			String strLati = Double.toString(location.getLatitude());
			String strLong = Double.toString(location.getLongitude());
			temp1 = strLati + strLong;
		}
		setListener();
		/* create editable text objects */


		readText = (EditText) findViewById(R.id.ReadValues);
		writeText = (EditText) findViewById(R.id.WriteValues);
		sendText = (EditText) findViewById(R.id.sendText);
		/*setIP = (EditText) findViewById(R.id.IPValues);*/

		global_context = this;

		configButton = (Button) findViewById(R.id.configButton);
		writeButton = (Button) findViewById(R.id.WriteButton);

		originalDrawable = configButton.getBackground();

		/* allocate buffer */
		writeBuffer = new byte[512];
		readBuffer = new char[512];

		/* setup the baud rate list */
		baudSpinner = (Spinner) findViewById(R.id.baudRateValue);
		ArrayAdapter<CharSequence> baudAdapter = ArrayAdapter.createFromResource(this, R.array.baud_rate,
				R.layout.my_spinner_textview);
		baudAdapter.setDropDownViewResource(R.layout.my_spinner_textview);
		baudSpinner.setAdapter(baudAdapter);
		baudSpinner.setGravity(0x10);
		baudSpinner.setSelection(9);
		baudRate = 115200;

		/* stop bits */
		stopSpinner = (Spinner) findViewById(R.id.stopBitValue);
		ArrayAdapter<CharSequence> stopAdapter = ArrayAdapter.createFromResource(this, R.array.stop_bits,
				R.layout.my_spinner_textview);
		stopAdapter.setDropDownViewResource(R.layout.my_spinner_textview);
		stopSpinner.setAdapter(stopAdapter);
		stopSpinner.setGravity(0x01);
		/* default is stop bit 1 */
		stopBit = 1;

		/* data bits */
		dataSpinner = (Spinner) findViewById(R.id.dataBitValue);
		ArrayAdapter<CharSequence> dataAdapter = ArrayAdapter.createFromResource(this, R.array.data_bits,
				R.layout.my_spinner_textview);
		dataAdapter.setDropDownViewResource(R.layout.my_spinner_textview);
		dataSpinner.setAdapter(dataAdapter);
		dataSpinner.setGravity(0x11);
		dataSpinner.setSelection(3);
		/* default data bit is 8 bit */
		dataBit = 8;

		/* parity */
		paritySpinner = (Spinner) findViewById(R.id.parityValue);
		ArrayAdapter<CharSequence> parityAdapter = ArrayAdapter.createFromResource(this, R.array.parity,
				R.layout.my_spinner_textview);
		parityAdapter.setDropDownViewResource(R.layout.my_spinner_textview);
		paritySpinner.setAdapter(parityAdapter);
		paritySpinner.setGravity(0x11);
		/* default is none */
		parity = 0;

		/* flow control */
		flowSpinner = (Spinner) findViewById(R.id.flowControlValue);
		ArrayAdapter<CharSequence> flowAdapter = ArrayAdapter.createFromResource(this, R.array.flow_control,
				R.layout.my_spinner_textview);
		flowAdapter.setDropDownViewResource(R.layout.my_spinner_textview);
		flowSpinner.setAdapter(flowAdapter);
		flowSpinner.setGravity(0x11);
		/* default flow control is is none */
		flowControl = 0;

		/* set the adapter listeners for baud */
		baudSpinner.setOnItemSelectedListener(new MyOnBaudSelectedListener());
		/* set the adapter listeners for stop bits */
		stopSpinner.setOnItemSelectedListener(new MyOnStopSelectedListener());
		/* set the adapter listeners for data bits */
		dataSpinner.setOnItemSelectedListener(new MyOnDataSelectedListener());
		/* set the adapter listeners for parity */
		paritySpinner.setOnItemSelectedListener(new MyOnParitySelectedListener());
		/* set the adapter listeners for flow control */
		flowSpinner.setOnItemSelectedListener(new MyOnFlowSelectedListener());

		configButton.setOnClickListener(new OpenDeviceListener());
		writeButton.setOnClickListener(new OnClickedWriteButton());

		writeButton.setEnabled(false);


		uartInterface = new CH34xAndroidDriver(
				(UsbManager) getSystemService(Context.USB_SERVICE), this,
				ACTION_USB_PERMISSION);

		act_string = getIntent().getAction();
		if (-1 != act_string.indexOf("android.intent.action.MAIN")) {
			Log.d(TAG, "android.intent.action.MAIN");
		} else if (-1 != act_string.indexOf("android.hardware.usb.action.USB_DEVICE_ATTACHED")) {
			Log.d(TAG, "android.hardware.usb.action.USB_DEVICE_ATTACHED");
		}

		if (!uartInterface.UsbFeatureSupported()) {
			Toast.makeText(this, "No Support USB host API", Toast.LENGTH_SHORT)
					.show();
			readText.setText("No Support USB host API");
			uartInterface = null;
		}

		getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);

		if (READ_ENABLE == false) {
			READ_ENABLE = true;
			handlerThread = new readThread(handler);
			handlerThread.start();

		}
	}

	//启动按钮的监听。。。。
	public class OpenDeviceListener implements View.OnClickListener {

		@Override
		public void onClick(View v) {
			// TODO Auto-generated method stub
			boolean flags;
			if (false == isConfiged) {
				isConfiged = true;
				writeButton.setEnabled(true);
				if (uartInterface.isConnected()) {
					flags = uartInterface.UartInit();
					if (!flags) {
						Log.d(TAG, "Init Uart Error");
						Toast.makeText(global_context, "Init Uart Error", Toast.LENGTH_SHORT).show();
					} else {
						if (uartInterface.SetConfig(baudRate, dataBit, stopBit, parity, flowControl)) {
							Log.d(TAG, "Configed");
						}
					}
				}

				if (isConfiged == true) {
					configButton.setEnabled(false);
				}
			}

		}

	}

	public class OnClickedWriteButton implements View.OnClickListener {

		@Override
		public void onClick(View v) {
			// TODO Auto-generated method stub
			int count_int;
			int NumBytes = 0;
			int mLen = 0;

			if (writeText.length() != 0) {
				NumBytes = writeText.length();
				for (count_int = 0; count_int < NumBytes; count_int++) {
					writeBuffer[count_int] = (byte) writeText.getText().charAt(count_int);
				}
			}
			try {
				mLen = uartInterface.WriteData(writeBuffer, NumBytes);
			} catch (IOException e1) {
				// TODO Auto-generated catch block
				Toast.makeText(global_context, "WriteData Error", Toast.LENGTH_SHORT).show();
				e1.printStackTrace();
			}

			if (NumBytes != mLen) {
				Toast.makeText(global_context, "WriteData Error", Toast.LENGTH_SHORT).show();
			}
			Log.d(TAG, "WriteData Length is " + mLen);
		}

	}

	public class MyOnBaudSelectedListener implements OnItemSelectedListener {

		@Override
		public void onItemSelected(AdapterView<?> parent, View view,
								   int position, long id) {
			// TODO Auto-generated method stub
			baudRate = Integer.parseInt(parent.getItemAtPosition(position).toString());
		}

		@Override
		public void onNothingSelected(AdapterView<?> parent) {
			// TODO Auto-generated method stub
		}
	}

	public class MyOnStopSelectedListener implements OnItemSelectedListener {

		@Override
		public void onItemSelected(AdapterView<?> parent, View view,
								   int position, long id) {
			// TODO Auto-generated method stub
			stopBit = (byte) Integer.parseInt(parent.getItemAtPosition(position).toString());
		}

		@Override
		public void onNothingSelected(AdapterView<?> parent) {
			// TODO Auto-generated method stub

		}

	}

	public class MyOnDataSelectedListener implements OnItemSelectedListener {

		@Override
		public void onItemSelected(AdapterView<?> parent, View view,
								   int position, long id) {
			// TODO Auto-generated method stub
			dataBit = (byte) Integer.parseInt(parent.getItemAtPosition(position).toString());
		}

		@Override
		public void onNothingSelected(AdapterView<?> parent) {
			// TODO Auto-generated method stub

		}

	}

	public class MyOnParitySelectedListener implements OnItemSelectedListener {

		@Override
		public void onItemSelected(AdapterView<?> parent, View view,
								   int position, long id) {
			// TODO Auto-generated method stub
			String parityString = new String(parent.getItemAtPosition(position).toString());
			if (parityString.compareTo("None") == 0) {
				parity = 0;
			}

			if (parityString.compareTo("Odd") == 0) {
				parity = 1;
			}

			if (parityString.compareTo("Even") == 0) {
				parity = 2;
			}

			if (parityString.compareTo("Mark") == 0) {
				parity = 3;
			}

			if (parityString.compareTo("Space") == 0) {
				parity = 4;
			}
		}

		@Override
		public void onNothingSelected(AdapterView<?> parent) {
			// TODO Auto-generated method stub

		}

	}

	public class MyOnFlowSelectedListener implements OnItemSelectedListener {

		@Override
		public void onItemSelected(AdapterView<?> parent, View view,
								   int position, long id) {
			// TODO Auto-generated method stub
			String flowString = new String(parent.getItemAtPosition(position).toString());
			if (flowString.compareTo("None") == 0) {
				flowControl = 0;
			}

			if (flowString.compareTo("CTS/RTS") == 0) {
				flowControl = 1;
			}
		}

		@Override
		public void onNothingSelected(AdapterView<?> parent) {
			// TODO Auto-generated method stub

		}

	}

	public void onHomePressed() {
		onBackPressed();
	}

	public void onBackPressed() {
		super.onBackPressed();
	}

	protected void onResume() {
		super.onResume();
		if (2 == uartInterface.ResumeUsbList()) {
			uartInterface.CloseDevice();
			Log.d(TAG, "Enter onResume Error");
		}
	}

	protected void onPause() {super.onPause();
	}

	protected void onStop() {
		if (READ_ENABLE == true) {
			READ_ENABLE = false;
		}
		super.onStop();
}

	protected void onDestroy() {
		if (uartInterface != null) {
			if (uartInterface.isConnected()) {
				uartInterface.CloseDevice();
			}
			uartInterface = null;
		}

		super.onDestroy();
	}


	//................................................................................

	private void setListener() {
		//位置变化监听
		if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
			// TODO: Consider calling
			//    ActivityCompat#requestPermissions
			// here to request the missing permissions, and then overriding
			//   public void onRequestPermissionsResult(int requestCode, String[] permissions,
			//                                          int[] grantResults)
			// to handle the case where the user grants the permission. See the documentation
			// for ActivityCompat#requestPermissions for more details.
			return;
		}
		locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2000, 10,
				locationListener);

	}
	private final LocationListener locationListener = new LocationListener() {
		@Override
		public void onStatusChanged(String provider, int status,
									Bundle extras) {
		}

		@Override
		public void onProviderEnabled(String provider) {
		}

		@Override
		public void onProviderDisabled(String provider) {
		}
		@Override
		public void onLocationChanged(Location location) {
			temp1=Double.toString(location.getLatitude())+"@"+Double.toString(location.getLongitude());
		}
	};
	//................................................................................

	//更新UI；
	final Handler handler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			if (actualNumBytes != 0x00) {
				String temp=String.copyValueOf(readBuffer,0,actualNumBytes);
				readText.setText(temp);
				actualNumBytes = 0;
				//http://58.198.165.33:8080/myhttp/yunshu.jsp?pa1=00002017010616143300011122233333&pa2=30.88834935@121.89409772
				//发送数据
				HttpAsyncTask asyncTask=new HttpAsyncTask(sendText);
				//URL_PATH="http://202.121.66.53:8080/myhttp/yunshu.jsp";
				URL_PATH="http://58.198.165.33:8080/myhttp/yunshu.jsp";
				//temp=00002017010616143300011122233333
				String pa1=temp;
				//temp1=30.88834935@121.89409772
				String pa2=temp1;
				//content中加入gps的内容在temp1中
				asyncTask.execute(URL_PATH,pa1,pa2);

			}
		}
	};

	/* 读数据线程*/
	private class readThread extends Thread {
		/*Handler mHandler;*/

		/* constructor */
		Handler mhandler;
		readThread(Handler h) {
			mhandler = h;
			this.setPriority(Thread.MIN_PRIORITY);
		}

		public void run() {
			while(READ_ENABLE) {
				Message msg = mhandler.obtainMessage();
				try {
					Thread.sleep(1500);//50
				} catch(InterruptedException e) {
				}
//				Log.d(TAG, "Thread");
				synchronized (ThreadLock) {
					if(uartInterface != null) {
						actualNumBytes = uartInterface.ReadData(readBuffer, 64);

						if(actualNumBytes > 0)
						{
							mhandler.sendMessage(msg);
						}
					}
				}
			}
		}
	}

}
