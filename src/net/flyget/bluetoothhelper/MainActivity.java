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
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.SimpleAdapter;
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
	private Button mClearBtn, mScanBtn, mSendBtn;
	private EditText mEditText;
	private String mTitle;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		mTitle = "Bluetooth Debug Helper";
		setTitle(mTitle);

		mClearBtn = (Button) findViewById(R.id.clearBtn);
		mScanBtn = (Button) findViewById(R.id.scanBtn);
		mSendBtn = (Button) findViewById(R.id.sendBtn);

		mClearBtn.setOnClickListener(this);
		mScanBtn.setOnClickListener(this);
		mSendBtn.setOnClickListener(this);
		lv = (ListView) findViewById(R.id.listview);
		mEditText = (EditText) findViewById(R.id.mEditText);

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
			byte[] buffer = new byte[256];
			int bytes;

			// Keep listening to the InputStream while connected
			while (true) {
				try {
					// Read from the InputStream
					bytes = mmInStream.read(buffer);
					synchronized (mBuffer) {
						for (int i = 0; i < bytes; i++) {
							mBuffer.add(buffer[i] & 0xFF);
						}
					}
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

				adapter = new SimpleAdapter(MainActivity.this, mData, R.layout.list_item, new String[] { "list" },
						new int[] { R.id.tv_item });
				lv.setAdapter(adapter);
				try {
					mConnectedThread.write(input.getBytes());
				} catch (Exception e) {
				}
				mEditText.setText("");
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
}
