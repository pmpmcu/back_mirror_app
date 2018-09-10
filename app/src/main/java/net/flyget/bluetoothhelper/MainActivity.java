package net.flyget.bluetoothhelper;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity implements OnClickListener {
	private static final int REQUEST_ENABLE_BT = 0;
	private static final int REQUEST_CONNECT_DEVICE = 1;
	private static final String SPP_UUID = "00001101-0000-1000-8000-00805F9B34FB";
	private List<Integer> mBuffer;
	private ListView lv;
	List<Map<String, Object>> mData = new ArrayList<Map<String, Object>>();;
	private SimpleAdapter adapter;
	private static final String TAG = "MainActivity";
	private BluetoothAdapter mBluetoothAdapter;
	private ConnectThread mConnectThread;
	public ConnectedThread mConnectedThread;
	private Button mClearBtn, mScanBtn, mSendBtn,HEX_Send_Btn;
	private EditText mEditText;
	private String mTitle;
    private int[] bt_rx_data_buf=new int[1024];
    private int rx_cnt=0;
	private TextView lblTitle_bt_rx=null;

	private TextView lblTitle_OBD_voltage=null;
	private TextView lblTitle_OBD_speed=null;
	private TextView lblTitle_OBD_Engine_RPM=null;
	private TextView lblTitle_OBD_Throttle_position=null;

	private String readMessage="";
	private String bt_rx_string_buf="";
	private byte[] bt_rx_byte_buf=new byte[1024];
	private int task_cnt=0;
	private int bt_connect_flag=0;
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		getWindow().setFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON, WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); //不休眠。不黑屏。
		setContentView(R.layout.activity_main);
		mTitle = "蓝牙未连接";
		setTitle(mTitle);

		mClearBtn = (Button) findViewById(R.id.clearBtn);
		mScanBtn = (Button) findViewById(R.id.scanBtn);
		mSendBtn = (Button) findViewById(R.id.sendBtn);
		HEX_Send_Btn=(Button) findViewById(R.id.send_hex_Btn);

		mClearBtn.setOnClickListener(this);
		mScanBtn.setOnClickListener(this);
		mSendBtn.setOnClickListener(this);
		HEX_Send_Btn.setOnClickListener(this);

		lv = (ListView) findViewById(R.id.listview);
		mEditText = (EditText) findViewById(R.id.mEditText);
		lblTitle_bt_rx= (TextView) findViewById(R.id.TextView1);

		lblTitle_OBD_voltage=(TextView) findViewById(R.id.TextView_rpm_Battery_Voltage_val);
		lblTitle_OBD_speed=(TextView) findViewById(R.id.TextView_vss_val);
		lblTitle_OBD_Engine_RPM=(TextView) findViewById(R.id.TextView_rpm_val);
		lblTitle_OBD_Throttle_position=(TextView) findViewById(R.id.TextView_vss_Throttle_position_val);

		mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
		// If the adapter is null, then Bluetooth is not supported
		if (mBluetoothAdapter == null) {
			Toast.makeText(this, "Bluetooth is not available", Toast.LENGTH_LONG).show();
			finish();
			return;
		}

		mBuffer = new ArrayList<Integer>();
		adapter = new SimpleAdapter(MainActivity.this, mData, R.layout.list_item, new String[] { "list" },
				new int[] { R.id.tv_item });
		lv.setAdapter(adapter);
	}

	@Override
	public void onStart() {
		super.onStart();
		// If BT is not on, request that it be enabled.
		// setupChat() will then be called during onActivityResult
		if (!mBluetoothAdapter.isEnabled()) {
			Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
			startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
			// Otherwise, setup the chat session
		}

	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {

		switch (requestCode) {
		case REQUEST_ENABLE_BT:
			if (resultCode == Activity.RESULT_OK) {
				// Bluetooth is now enabled Launch the DeviceListActivity to see
				// devices and do scan
				Intent serverIntent = new Intent(this, DeviceListActivity.class);
				startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE);
			} else {
				// User did not enable Bluetooth or an error occured
				Log.d(TAG, "BT not enabled");
				Toast.makeText(this, "BT not enabled", Toast.LENGTH_SHORT).show();
				return;
			}
			break;
		case REQUEST_CONNECT_DEVICE:
			if (resultCode != Activity.RESULT_OK) {
				return;
			} else {
				String address = data.getExtras().getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
				// Get the BLuetoothDevice object
				BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
				// Attempt to connect to the device
				connect(device);
				//lblTitle_bt_rx.setText("bt_connect_ok");
				mHanlder.postDelayed(task, 1000);//第一次调用,延迟1秒执行task

			}
			break;
		}
	}

	public void connect(BluetoothDevice device) {
		Log.d(TAG, "connect to: " + device);
		// Start the thread to connect with the given device
		mConnectThread = new ConnectThread(device);
		mConnectThread.start();
	}

	/**
	 * This thread runs while attempting to make an outgoing connection with a
	 * device. It runs straight through; the connection either succeeds or
	 * fails.
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
				tmp = device.createRfcommSocketToServiceRecord(UUID.fromString(SPP_UUID));
			} catch (IOException e) {
				Log.e(TAG, "create() failed", e);
			}
			mmSocket = tmp;
			//lblTitle_bt_rx.setText("bt_connect_ok");
		}

		public void run() {
			Log.i(TAG, "BEGIN mConnectThread");
			setName("ConnectThread");

			// Always cancel discovery because it will slow down a connection
			mBluetoothAdapter.cancelDiscovery();

			// Make a connection to the BluetoothSocket
			try {
				// This is a blocking call and will only return on a
				// successful connection or an exception
				mmSocket.connect();
			} catch (IOException e) {

				Log.e(TAG, "unable to connect() socket", e);
				// Close the socket
				try {
					mmSocket.close();
				} catch (IOException e2) {
					Log.e(TAG, "unable to close() socket during connection failure", e2);
				}
				return;
			}

			mConnectThread = null;

			// Start the connected thread
			// Start the thread to manage the connection and perform
			// transmissions
			mConnectedThread = new ConnectedThread(mmSocket);
			mConnectedThread.start();

		}

		public void cancel() {
			try {
				mmSocket.close();
			} catch (IOException e) {
				Log.e(TAG, "close() of connect socket failed", e);
			}
		}
	}

	/**
	 * This thread runs during a connection with a remote device. It handles all
	 * incoming and outgoing transmissions.
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
		}

		public void run() {
			Log.i(TAG, "BEGIN mConnectedThread");
			final byte[] buffer = new byte[256];
			int bytes;
			bt_connect_flag=1;
			// Keep listening to the InputStream while connected
			while (true) {
				try {
					// Read from the InputStream
					bytes = mmInStream.read(buffer);
					synchronized (mBuffer) {
                        for (int i = 0; i < bytes; i++) {
                            mBuffer.add(buffer[i] & 0xFF);
                            bt_rx_data_buf[rx_cnt+i] = buffer[i] & 0xFF;
							bt_rx_byte_buf[rx_cnt+i]=buffer[i];
                            //System.out.println(buffer[i] & 0xFF);
							//rx_ok_flag=1;
							task_cnt=10;
                        }
                        //System.out.println(fileList().toString(mBuffer));
						 readMessage= new String(buffer, 0, bytes);
                    }
					bt_rx_string_buf=bt_rx_string_buf+readMessage;
					//bt_rx_string_buf=bt_rx_string_buf+byte2HexStr(buffer);
					//bt_rx_byte_buf=new byte[bt_rx_byte_buf.length+buffer.length];
                    rx_cnt=rx_cnt+bytes;
                    //if(rx_cnt>=8) {
                        //for (int i = 0; i < rx_cnt; i++) {
                            //System.out.println(bt_rx_data_buf[i]);
                       // }
                        //rx_cnt=0;
                        //System.out.println("------------------------------");
						/*
						new Thread() {
							public void run() {
								//这儿是耗时操作，完成之后更新UI；
								runOnUiThread(new Runnable(){

									@Override
									public void run() {
										//更新UI
										//lblTitle_bt_rx.setText(readMessage);
										lblTitle_bt_rx.setText(bt_rx_string_buf);
										//lblTitle_bt_rx.setText(byte2HexStr(buffer));
										bt_rx_string_buf="";

									}

								});
							}
						}.start();
						*/

					// mHandler.sendEmptyMessage(MSG_NEW_DATA);
				} catch (IOException e) {
					Log.e(TAG, "disconnected", e);
					break;
				}
			}
		}

		/**
		 * Write to the connected OutStream.
		 * 
		 * @param buffer
		 *            The bytes to write
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

	private boolean isBackCliecked = false;

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_BACK) {
			if (isBackCliecked) {
				this.finish();
			} else {
				isBackCliecked = true;
				Toast t = Toast.makeText(this, "Press \'Back\' again to exit.", Toast.LENGTH_LONG);
				t.setGravity(Gravity.CENTER, 0, 0);
				t.show();
			}
		}
		return true;
	}

	@Override
	public void onClick(View v) {
		isBackCliecked = false;
		if (v == mClearBtn) {
			mBuffer.clear();
			mData.clear();
			adapter.notifyDataSetChanged();
		} else if (v == mScanBtn) {
			if (mConnectThread != null) {
				mConnectThread.cancel();
				mConnectThread = null;
			}
			// if(mConnectedThread != null) {mConnectedThread.cancel();
			// mConnectedThread = null;}
			Intent serverIntent = new Intent(this, DeviceListActivity.class);
			startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE);
		} else if (v == mSendBtn) {
			String input = mEditText.getText().toString().trim();
			if (input != null && !"".equals(input)) {

				Map<String, Object> item = new HashMap<String, Object>();
				item.put("list", input);
				mData.add(item);

				adapter = new SimpleAdapter(MainActivity.this, mData, R.layout.list_item, new String[]{"list"},
						new int[]{R.id.tv_item});
				lv.setAdapter(adapter);
				try {
					mConnectedThread.write(input.getBytes());
				} catch (Exception e) {
				}
				//mEditText.setText("");
			}
		} else if (v == HEX_Send_Btn) {
			String input=String.valueOf(0xff);
			int i=254;
			byte[] txbuf=new byte[16];
			txbuf[0]=(byte)0xff;
			txbuf[1]=(byte)0x66;
			txbuf[2]=(byte)0xf0;
			txbuf[3]=(byte)0xfe;
			txbuf[4]=(byte)0xff; // ok
			txbuf[15]=(byte)i;
			//lblTitle_bt_rx.setText("HEX_Send_Btn");
			//write(intToBytes(0xff));
			try {
				mConnectedThread.write(txbuf);
				//mConnectedThread.write(input.getBytes());
			} catch (Exception e) {
			}
		}
	}

	@Override
	protected void onDestroy() {
		if (mConnectThread != null) {
			mConnectThread.cancel();
			mConnectThread = null;
		}
		if (mConnectedThread != null) {
			mConnectedThread.cancel();
			mConnectedThread = null;
		}
		super.onDestroy();
	}

	/*
	 * bytes转换成十六进制字符串
	 * @param byte[] b byte数组
	 * @return String 每个Byte值之间空格分隔
	 */
	public static String byte2HexStr(byte[] b,int length)
	{
		String stmp="";
		StringBuilder sb = new StringBuilder("");
		for (int n=0;n<length;n++)
		{
			stmp = Integer.toHexString(b[n] & 0xFF);
			sb.append((stmp.length()==1)? "0"+stmp : stmp);
			sb.append(" ");
		}
		return sb.toString().toUpperCase().trim();
	}

	/*
	 * 十六进制转换字符串
	 * @param String str Byte字符串(Byte之间无分隔符 如:[616C6B])
	 * @return String 对应的字符串
	 */
	public static String hexStr2Str(String hexStr)
	{
		String str = "0123456789ABCDEF";
		char[] hexs = hexStr.toCharArray();
		byte[] bytes = new byte[hexStr.length() / 2];
		int n;

		for (int i = 0; i < bytes.length; i++)
		{
			n = str.indexOf(hexs[2 * i]) * 16;
			n += str.indexOf(hexs[2 * i + 1]);
			bytes[i] = (byte) (n & 0xff);
		}
		return new String(bytes);
	}

	/**
	 * 将int类型的数据转换为byte数组
	 * @param n int数据
	 * @return 生成的byte数组
	 */
	public static byte[] intToBytes(int n){
		String s = String.valueOf(n);
		return s.getBytes();
	}

	/**
	 * 将int类型的数据转换为byte数组
	 * 原理：将int数据中的四个byte取出，分别存储
	 * @param n int数据
	 * @return 生成的byte数组
	 */
	public static byte[] intToBytes2(int n){
		byte[] b = new byte[4];
		for(int i = 0;i < 4;i++){
			b[i] = (byte)(n >> (24 - i * 8));
		}
		return b;
	}

	/**
	 * 将byte数组转换为int数据
	 * @param b 字节数组
	 * @return 生成的int数据
	 */
	public static int byteToInt2(byte[] b){
		return (((int)b[0]) << 24) + (((int)b[1]) << 16) + (((int)b[2]) << 8) + b[3];
	}

	public static int byteToInt(byte b) {
		//Java 总是把 byte 当做有符处理；我们可以通过将其和 0xFF 进行二进制与得到它的无符值
		return b & 0xFF;
	}
	byte data_check_sum(byte[] uart_buf_dat)
	{
		int key_i=0;
		int temp_check_data=0;
		int check_sum_data=0;


		for(key_i=0;key_i<13;key_i++) //13字节，加头2 尾1，16byte
		{
			temp_check_data=(check_sum_data+(uart_buf_dat[2+key_i]&0xff)); //第 3节是数据开始。
			check_sum_data=temp_check_data&0xff;
			if(temp_check_data>0xff) { //大于255 低位加 1
				check_sum_data = (check_sum_data + 1)&0xff;
			}
		}
		//System.out.println(check_sum_data+"--------------------------------");
		check_sum_data=(byte)( ~check_sum_data+1); //取反+1;
		//System.out.println(check_sum_data+"--------------------------------");
		if(check_sum_data==uart_buf_dat[15]) // 最后一个字节是检验和
		{
			return 1;
		}
		else
		{
			return 0;
		}
	}

	public void OBD_decode(byte[] buf){
		byte Engine_load=(byte) 0x04;
		byte Engine_RPM=(byte)0x0C;
		byte Velicele_speed=(byte)0x0D;
		byte Throttle_position=(byte)0x11;
		byte Battery_Voltage=(byte)0x42;
		float temp=0;
		if(((buf[0])==(byte)0xff)&(buf[1]==(byte)0x66)){
			if(data_check_sum(buf)==1)
			{
				if(buf[2+2]==Battery_Voltage){
					temp=(float)(((buf[3+2]&0xff)<<8)|(buf[4+2]&0xff));
					//System.out.println(temp+"--------------------------------");
					temp=temp/1000;
					lblTitle_OBD_voltage.setText(String.valueOf(temp));
				}
				if(buf[2+2]==Velicele_speed){
					temp=(float)((buf[3+2]&0xff));
				//	System.out.println(temp+"--------------------------------");
					lblTitle_OBD_speed.setText(String.valueOf(temp));
				}
				if(buf[2+2]==Engine_RPM){
					temp=(float)(((buf[3+2]&0xff)<<8)|(buf[4+2]&0xff));
					//System.out.println(temp+"--------------------------------");
					temp=temp/4;
					//	System.out.println(temp+"--------------------------------");
					lblTitle_OBD_Engine_RPM.setText(String.valueOf(temp));
				}
				if(buf[2+2]==Throttle_position){
					temp=(float)(buf[3+2]&0xff);
					//System.out.println(temp+"--------------------------------");
					temp=temp*100/255;
					//	System.out.println(temp+"--------------------------------");
					lblTitle_OBD_Throttle_position.setText(String.valueOf(temp));
				}

			}
		}
	}
	/**
	 * Handler可以用来更新UI
	 * */
	private Handler mHanlder = new Handler() {
		//@Override
		//public void handleMessage(Message msg) {

		//super.handleMessage(msg);
		//}
	};

	private Runnable task = new Runnable() {
		@Override
		public void run() {
			/**
			 * 此处执行任务
			 * */
			if(bt_connect_flag==1) {
				bt_connect_flag=0;
				//lblTitle_bt_rx.setText("bt_connect_ok");
				mTitle = "蓝牙连接成功";
				setTitle(mTitle);
			}
			if(task_cnt>0)
				task_cnt--;
			if ((task_cnt==0)&&(rx_cnt>0)) {          // 超时，并且有数据
				lblTitle_bt_rx.setText(byte2HexStr(bt_rx_byte_buf,rx_cnt));
				//lblTitle_bt_rx.setText(bt_rx_string_buf);
				OBD_decode(bt_rx_byte_buf);
				bt_rx_string_buf = "";
				rx_cnt=0;
			}

			mHanlder.postDelayed(this, 1 * 10);//延迟10m秒,再次执行task本身,实现了循环的效果

			//final String s2 = Integer.toString(dian_liao_timer_sec_cnt);  // 整形数转字符串。
			//sendMessage((s2+"\r\n"));

		}

	};
}
