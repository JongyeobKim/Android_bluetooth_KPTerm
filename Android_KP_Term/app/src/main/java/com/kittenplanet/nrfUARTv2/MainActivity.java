
/*
 * Copyright (c) 2015, Nordic Semiconductor
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the
 * documentation and/or other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to endorse or promote products derived from this
 * software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
 * USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.kittenplanet.nrfUARTv2;




import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;  // 모든 Bluetooth API는 android.bluetooth 패키지에서 구할 수 있다
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import java.io.UnsupportedEncodingException;
import java.text.DateFormat;
import java.util.Date;


public class MainActivity extends Activity implements RadioGroup.OnCheckedChangeListener {
    private static final int REQUEST_SELECT_DEVICE = 1;
    private static final int REQUEST_ENABLE_BT = 2; // StartActivityForResult()로 전달되는 상수 (requestCode)
    private static final int UART_PROFILE_READY = 10;
    public static final String TAG = "nRFUART";
    private static final int UART_PROFILE_CONNECTED = 20;
    private static final int UART_PROFILE_DISCONNECTED = 21;
    private static final int STATE_OFF = 10;

    TextView mRemoteRssiVal;
    RadioGroup mRg;
    private int mState = UART_PROFILE_DISCONNECTED;
    private UartService mService = null;    // UartService 변수
    private BluetoothDevice mDevice = null; // 원격 블루투스 기기를 나타낸다. 이를 사용하여 BluetoothSocket을 통해 원격 기기와의 연결을 요청하거나 이름, 주소, 클래스 및 연결 상태와 같은 기기 정보를 쿼리합니다.
    private BluetoothAdapter mBtAdapter = null; // 로컬 블루투스 어댑터(블루투스 송수신 장치). 이를 사용하여 다른 블루투스 기기를 검색하고 연결된(페어링된) 기기 목록을 쿼리하고 알려진 MAC 주소로 BluetoothDevice를 인스턴스화 하고 다른 기기로부터 통신을 수신 대기하는 BluetoothServerSocket을 만들 수 있다.
    private ListView messageListView;
    private ArrayAdapter<String> listAdapter;
    private Button btnConnectDisconnect,btnSend;
    private EditText edtMessage;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        mBtAdapter = BluetoothAdapter.getDefaultAdapter();  // BluetooothAdapter를 가져오려면 정적 getDefaultAdapter() 메서드를 호출한다. 그러면 기기의 자체 블루투스 어댑(블루투스 송수신 장치)를 나타내는 BluetoothAdapter가 반환된다.
        if (mBtAdapter == null) {   // getDefaultAdapter() 메서드가 null을 반환하는 경우 기기는 블루투스를 지원하지 않는다.
            Toast.makeText(this, "Bluetooth is not available", Toast.LENGTH_LONG).show();   // Toast 창 출력
            finish();   // 종료
            return;
        }
        messageListView = (ListView) findViewById(R.id.listMessage);
        listAdapter = new ArrayAdapter<String>(this, R.layout.message_detail);
        messageListView.setAdapter(listAdapter);
        messageListView.setDivider(null);
        btnConnectDisconnect=(Button) findViewById(R.id.btn_select);    // Connect button
        btnSend=(Button) findViewById(R.id.sendButton); // 명령 send button
        edtMessage = (EditText) findViewById(R.id.sendText);    // 명령 send message, s:start, p:pause, q:quit
        service_init();

     
       
        // Handle Disconnect & Connect button
        btnConnectDisconnect.setOnClickListener(new View.OnClickListener() {    // Connect button 이벤트
            @Override
            public void onClick(View v) {
                Log.d(TAG, "Click Connect Button !");
                if (!mBtAdapter.isEnabled()) {  // 블루투스 활성화, isEnabled를 호출하여 블루투스가 현재 활성화되어 있는지 확인한다. false를 반환하는 경우 블루투스가 비활성화된다. (false 반환시에 if 조건 실행)
                    // 단말의 BT 비활성화 시 실행 권한 요청
                    Log.i(TAG, "onClick - BT not enabled yet");
                    Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);   // 블루투스 활성화를 요청하려면 ACTION_REQUEST_ENABLE 작업 Intent를 사용하여 startActivityForResult()를 호출한다. 그러면 (애플리케이션을을 중지하지 않고) 시스템 설정을 통한 블루투스 활성화 요청이 발급된다.
                    startActivityForResult(enableIntent, REQUEST_ENABLE_BT);    // (애플리케이션을 중지하지 않고) 시스템 설정을 통한 블루투스 활성화 요청이 발급된다.
                                                                                // startActivityForResult() 메서드로 전달된 REQUEST_ENABLE_BT 상수는 시스템이 requestCode 매개변수로서, onActivityResult() 구현에서 개발자에게 다시 전달하는 지역적으로 정의된 정수(0보다 커야함)이다.
                }
                else {  // true 반환 시 else 조건 실행 (블루투스가 정상적으로 실행된 경우)
                	if (btnConnectDisconnect.getText().equals("Connect")){  // Connect 버튼 클릭
                		
                		//Connect button pressed, open DeviceListActivity class, with popup windows that scan for devices
                		
            			Intent newIntent = new Intent(MainActivity.this, DeviceListActivity.class); // DeviceListActivity 실행
            			startActivityForResult(newIntent, REQUEST_SELECT_DEVICE);   // startActivityForResult() 메서드 : 작업이 끝나면 결과를 원하는 활동 시작. RequestCode = REQUEST_SELECT_DEVICE (0이상인 경우 시작된 활동에서 결과가 반환 될 때까지 창이 표시되지 않는다.)
        			} else {    // Disconnect 버튼 클릭
        				//Disconnect button pressed
        				if (mDevice!=null)
        				{
        					mService.disconnect();  // 블루투스 연결 해제
        				}
        			}
                }
            }
        });
        // Handle Send button
        btnSend.setOnClickListener(new View.OnClickListener() { // 명령 send button 클릭 시
            @Override
            public void onClick(View v) {
            	EditText editText = (EditText) findViewById(R.id.sendText); // 전송 메시지
            	String message = editText.getText().toString();
            	byte[] value;
				try {
					//send data to service
					value = message.getBytes("UTF-8");
					mService.writeRXCharacteristic(value);
					//Update the log with time stamp
					String currentDateTimeString = DateFormat.getTimeInstance().format(new Date());
					listAdapter.add("["+currentDateTimeString+"] TX: "+ message);
               	 	messageListView.smoothScrollToPosition(listAdapter.getCount() - 1);
               	 	edtMessage.setText("");
				} catch (UnsupportedEncodingException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
                
            }
        });
     
        // Set initial UI state
        
    }
    
    //UART service connected/disconnected
    private ServiceConnection mServiceConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder rawBinder) {
        		mService = ((UartService.LocalBinder) rawBinder).getService();
        		Log.d(TAG, "onServiceConnected mService= " + mService);
        		if (!mService.initialize()) {
                    Log.e(TAG, "Unable to initialize Bluetooth");
                    finish();
                }

        }

        public void onServiceDisconnected(ComponentName classname) {
       ////     mService.disconnect(mDevice);
        		mService = null;
        }
    };

    private Handler mHandler = new Handler() {
        @Override
        
        //Handler events that received from UART service 
        public void handleMessage(Message msg) {
  
        }
    };

    // Create a BroadcastReceiver for ACTION_FOUND
    private final BroadcastReceiver UARTStatusChangeReceiver = new BroadcastReceiver() {    // 기기 검색을 시작하려면 startDiscovery()를 호출한다. 이는 비동기 프로세스이며 해당 메서드는 검색이 성공적으로 시작했는지 여부를 나타내는 부울을 즉시 반환한다.
                                                                                            // 검색 프로세스는 일반적으로 12초 정도의 조회 스캔과, 블루투스 이름을 가져오는 검색된 각 기기의 페이지 스캔을 포함한다.

        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            final Intent mIntent = intent;
           //*********************//
            if (action.equals(UartService.ACTION_GATT_CONNECTED)) { // Connect 상태인 경우
            	 runOnUiThread(new Runnable() {
                     public void run() {
                         // Cancel discovery because it will slow down the connection
                         //mBtAdapter.cancelDiscovery();  // 기기 검색 중단
                            String currentDateTimeString = DateFormat.getTimeInstance().format(new Date()); // 현재시간
                             Log.d(TAG, "UART_CONNECT_MSG");
                             btnConnectDisconnect.setText("Disconnect");    // Connect Or Disconnect 버튼 텍스트 변경
                             edtMessage.setEnabled(true);   // setEnable : 해당 노드가 사용가능한지 설정, send Text 사용 가능 상태로 변경
                             btnSend.setEnabled(true);  // send button 사용 가능 상태로 변경
                             ((TextView) findViewById(R.id.deviceName)).setText(mDevice.getName()+ " - ready"); // 페어링된 기기명 + "- ready" 텍스트 띄움
                             listAdapter.add("["+currentDateTimeString+"] Connected to: "+ mDevice.getName());
                        	 	messageListView.smoothScrollToPosition(listAdapter.getCount() - 1);
                             mState = UART_PROFILE_CONNECTED;
                     }
            	 });
            }
           
          //*********************//
            if (action.equals(UartService.ACTION_GATT_DISCONNECTED)) {
            	 runOnUiThread(new Runnable() {
                     public void run() {
                    	 	 String currentDateTimeString = DateFormat.getTimeInstance().format(new Date());    // 현재시
                             Log.d(TAG, "UART_DISCONNECT_MSG");
                             btnConnectDisconnect.setText("Connect");   // // Connect Or Disconnect 버튼 텍스트 변경
                             edtMessage.setEnabled(false);  // send Text 사용 불가능 상태로 변경
                             btnSend.setEnabled(false);     // send button 사용 불가능 상태로 변경
                             ((TextView) findViewById(R.id.deviceName)).setText("Not Connected");
                             listAdapter.add("["+currentDateTimeString+"] Disconnected to: "+ mDevice.getName());
                             mState = UART_PROFILE_DISCONNECTED;
                             mService.close();
                            //setUiState();
                         
                     }
                 });
            }
            
          
          //*********************//
            if (action.equals(UartService.ACTION_GATT_SERVICES_DISCOVERED)) {
             	 mService.enableTXNotification();
            }
          //*********************//
            if (action.equals(UartService.ACTION_DATA_AVAILABLE)) {
              
                final byte[] txValue = intent.getByteArrayExtra(UartService.EXTRA_DATA);

                runOnUiThread(new Runnable() {
                    public void run() {
                        try {
                            String currentDateTimeString = DateFormat.getTimeInstance().format(new Date());
                            if(txValue[0] == 's') {     // Sensor
                                final short accx = (short)((((short)txValue[1] & 0xff) << 8) | ((short)txValue[2] & 0xff));    // 가속도 X Axis
                                final short accy = (short)((((short)txValue[3] & 0xff) << 8) | ((short)txValue[4] & 0xff));    // 가속도 Y Axis
                                final short accz = (short)((((short)txValue[5] & 0xff) << 8) | ((short)txValue[6] & 0xff));    // 가속도 Z Axis
                                final byte key = txValue[7];        // button
                                final byte checksum = txValue[8];   // checksum

                                String dataString = String.format("%d %d %d %d", accx, accy, accz, key);
                                listAdapter.add("["+currentDateTimeString+"] SENSOR : " + dataString);
                            }
                            else if(txValue[0] == 'd') {    // Device Info
                                String dataString1 = String.format("%02X %02X %02X %02X ", txValue[0], txValue[1], txValue[2], txValue[3]);
                                String dataString2 = String.format("%02X %02X %02X %02X ", txValue[4], txValue[5], txValue[6], txValue[7]);
                                String dataString3 = String.format("%02X %02X %02X %02X", txValue[8], txValue[9], txValue[10], txValue[11]);
                                listAdapter.add("["+currentDateTimeString+"] INFO : " + dataString1 + dataString2 + dataString3);
                            }

                            messageListView.smoothScrollToPosition(listAdapter.getCount() - 1);

                        } catch (Exception e) {
                            Log.e(TAG, e.toString());
                        }
                    }
                });
             }

           //*********************//
            if (action.equals(UartService.DEVICE_DOES_NOT_SUPPORT_UART)){
            	showMessage("Device doesn't support UART. Disconnecting");
            	mService.disconnect();
            }
            
            
        }
    };

    private void service_init() {
        Intent bindIntent = new Intent(this, UartService.class);
        bindService(bindIntent, mServiceConnection, Context.BIND_AUTO_CREATE);
  
        LocalBroadcastManager.getInstance(this).registerReceiver(UARTStatusChangeReceiver, makeGattUpdateIntentFilter());
    }
    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(UartService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(UartService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(UartService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(UartService.ACTION_DATA_AVAILABLE);
        intentFilter.addAction(UartService.DEVICE_DOES_NOT_SUPPORT_UART);
        return intentFilter;
    }
    @Override
    public void onStart() {
        super.onStart();
    }

    @Override
    public void onDestroy() {
    	 super.onDestroy();
        Log.d(TAG, "onDestroy()");
        
        try {
        	LocalBroadcastManager.getInstance(this).unregisterReceiver(UARTStatusChangeReceiver);
        } catch (Exception ignore) {
            Log.e(TAG, ignore.toString());
        } 
        unbindService(mServiceConnection);
        mService.stopSelf();
        mService= null;
       
    }

    @Override
    protected void onStop() {
        Log.d(TAG, "onStop");
        super.onStop();
    }

    @Override
    protected void onPause() {
        Log.d(TAG, "onPause");
        super.onPause();
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        Log.d(TAG, "onRestart");
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG, "onResume");
        if (!mBtAdapter.isEnabled()) {
            Log.i(TAG, "onResume - BT not enabled yet");
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
        }
 
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {

        case REQUEST_SELECT_DEVICE:
        	//When the DeviceListActivity return, with the selected device address
            if (resultCode == Activity.RESULT_OK && data != null) {
                String deviceAddress = data.getStringExtra(BluetoothDevice.EXTRA_DEVICE);
                mDevice = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(deviceAddress);
               
                Log.d(TAG, "... onActivityResultdevice.address==" + mDevice + "mserviceValue" + mService);
                ((TextView) findViewById(R.id.deviceName)).setText(mDevice.getName()+ " - connecting");
                mService.connect(deviceAddress);
                            

            }
            break;
        case REQUEST_ENABLE_BT:
            // When the request to enable Bluetooth returns
            if (resultCode == Activity.RESULT_OK) {
                Toast.makeText(this, "Bluetooth has turned on ", Toast.LENGTH_SHORT).show();    // 블루투스가 활성화되었음을 Toast 창을 통해 띄워준다.

            } else {
                // User did not enable Bluetooth or an error occurred
                Log.d(TAG, "BT not enabled");
                Toast.makeText(this, "Problem in BT Turning ON ", Toast.LENGTH_SHORT).show();   // 블루투스 활성화 중 오류가 발생하였음을 Toast 창을 통해 띄워준다.
                finish(); // Call this when your activity is done and should be closed.
            }
            break;
        default:
            Log.e(TAG, "wrong request code");
            break;
        }
    }

    @Override
    public void onCheckedChanged(RadioGroup group, int checkedId) {
       
    }


    private void showMessage(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();

    }

    @Override
    public void onBackPressed() {
        if (mState == UART_PROFILE_CONNECTED) {
            Intent startMain = new Intent(Intent.ACTION_MAIN);
            startMain.addCategory(Intent.CATEGORY_HOME);
            startMain.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(startMain);
            showMessage("nRFUART's running in background.\n             Disconnect to exit");
        }
        else {
            new AlertDialog.Builder(this)
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .setTitle(R.string.popup_title)
                    .setMessage(R.string.popup_message)
                    .setPositiveButton(R.string.popup_yes, new DialogInterface.OnClickListener()
                    {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            finish();
                        }
                    })
                    .setNegativeButton(R.string.popup_no, null)
                    .show();
        }
    }
}
