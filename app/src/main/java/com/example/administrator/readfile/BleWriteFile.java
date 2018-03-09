package com.example.administrator.readfile;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import static java.lang.Thread.sleep;

public class BleWriteFile extends AppCompatActivity {
    public static final String EXTRAS_DEVICE_NAME = "DEVICE_NAME";
    public static final String EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS";
    private String mDeviceName;         //设备名
    private String mDeviceAddress;      //设备地址
    private String  root = Environment.getExternalStorageDirectory().getAbsolutePath() + "/压力数据";   //根目录
    private BluetoothLeService mBluetoothLeService;     //实例化服务BluetoothLeService
    private TextView fileRecive;            //显示:当前接收文件
    private TextView fileNum;                //显示：文件数
    private ListView mListView;              //文件列表对应的ListView
    private TextView mRecvBytes;             //显示：接收数据大小
    private TextView mNotify_speed_text;    //显示：传递速度
    private CreateFiles createFiles;         //实例化CreateFiles
    private long lastSecondBytes=0;
    private long recvBytes = 0;
    private Timer timer;    //用于测速
    private TimerTask task;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.writefile);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        toolbar.setTitle("BLE抄表");
        toolbar.setNavigationIcon(R.mipmap.backincostyleone);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(new View.OnClickListener(){    //设置按下左上角的图标会返回上一个活动
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        //获取蓝牙的名字和地址
        final Intent intent = getIntent();
        mDeviceName = intent.getStringExtra(EXTRAS_DEVICE_NAME);            //取出上一个Activity传入的保存在键值中的设备名
        mDeviceAddress = intent.getStringExtra(EXTRAS_DEVICE_ADDRESS);     //取出上一个Activity传入的保存在键值中的设备地址
        //获得控件
        fileRecive = (TextView) findViewById(R.id.fileRecive);                      //显示:当前接收文件
        fileNum = (TextView) findViewById(R.id.filenum);                             //显示：文件数
        mListView = (ListView) this.findViewById(R.id.fileListView);               //文件列表对应的ListView
        mRecvBytes = (TextView) findViewById(R.id.byte_received_text);             //显示：接收数据大小
        mNotify_speed_text = (TextView) findViewById(R.id.notify_speed_text);     //显示：传递速度
        //实例化CreateFiles，此文件定义：创建文件夹及文件、向已创建的文件中写入数据的函数
        createFiles = new CreateFiles();
        //每隔1S执行一次task读取下载的速度
        final Handler handler = new Handler() {
            @Override
            public void handleMessage(Message msg) {    //消息处理机制
                switch (msg.what) {
                    case 1:
                        lastSecondBytes = recvBytes - lastSecondBytes;  //下载速度 = 已经接收的字节数 - 上次接收的字节数
                        mNotify_speed_text.setText(String.valueOf(lastSecondBytes)+ " B/s");    //控件显示：下载速度
                        lastSecondBytes = recvBytes;
                        break;
                }
            }
        };
        task = new TimerTask() {
            @Override
            public void run() {
                Message message = new Message();
                message.what = 1;
                message.obj = System.currentTimeMillis();
                handler.sendMessage(message);   //发送消息，会进入上面定义的消息处理机制
            }
        };
        timer = new Timer();
        timer.schedule(task, 1000, 1000);   //第2个参数：延时1秒后执行，第3个参数：每隔1秒执行1次task
        //3. 连接服务BluetoothLeService --- 用于数据的传输
        Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
    }

    private final ServiceConnection mServiceConnection = new ServiceConnection() {
        //连接BluetoothLeService服务并连接设备
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();  //1. 获得BluetoothLeService服务
            if (!mBluetoothLeService.initialize()) {    //初始化BluetoothLeService服务
                finish();
            }
            mBluetoothLeService.connect(mDeviceAddress);    //2. BluetoothLeService服务连接地址为mDeviceAddress的设备
        }
        //取消连接BluetoothLeService服务
        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothLeService = null;
        }
    };

    //Activity即将可见时执行
    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());    //4. 注册广播接收器
    }

    int sendDataLen=0;
    //定义广播接收器，用于和BluetoothLeService服务通信
    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            /*******************************************6. 连接设备***************************************************/
            if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {  //ACTION_GATT_CONNECTED: connected to a GATT server.
                Toast.makeText(BleWriteFile.this, "已连接", Toast.LENGTH_SHORT).show();
                try {
                    sleep(1000);    //等待一秒后下发20字节的同步时间包
                } catch (InterruptedException e) {
                }
                mBluetoothLeService.writeData(getTime());
            }
            /*断开连接*/
            else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {  //ACTION_GATT_DISCONNECTED: disconnected from a GATT server.
                mBluetoothLeService.disconnect();
                invalidateOptionsMenu();
                Toast.makeText(BleWriteFile.this, "已断开连接", Toast.LENGTH_SHORT).show();
            }
            /********************************************5. 发现设备************************************************/
            else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {   //ACTION_GATT_SERVICES_DISCOVERED: discovered GATT services.
                //特征值找到才代表连接成功
                invalidateOptionsMenu();
            }
            /**************************7. 将接收到的数据写入256字节的缓存get_byte[256]中，并将get_byte[256]写入文件中***************************/
            /*从蓝牙设备读取数据 --- BluetoothLeService服务每次接收蓝牙设备20字节的数据，去处前2字节和最后一字节后将剩余17字节数据保存到文件中*/
            else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) { //ACTION_DATA_AVAILABLE: received data from the device.  This can be a result of read
                RecvByte(intent.getByteArrayExtra(BluetoothLeService.EXTRA_DATA));  //将接收到的数据写入256字节的缓存get_byte[256]中，并将get_byte[256]写入文件中
                displayData(intent.getByteArrayExtra(BluetoothLeService.EXTRA_DATA));   //左下角控件显示：文件数据大小
            }
            else if (BluetoothLeService.ACTION_WRITE_SUCCESSFUL.equals(action)) {      //如果从蓝牙设备读取所有数据成功
            }
        }
    };
    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        intentFilter.addAction(BluetoothLeService.ACTION_WRITE_SUCCESSFUL);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_NO_DISCOVERED);
        return intentFilter;
    }

    static long recv_cnt = 0;
    //左下角控件显示：文件数据大小
    private void displayData(byte[] buf) {
        recvBytes += buf.length;
        recv_cnt += buf.length;
        mRecvBytes.setText(recvBytes + " ");    //控件显示：文件数据大小
    }

    //Activity销毁时执行
    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(mServiceConnection);  //断开和BluetoothLeService服务的连接
        mBluetoothLeService = null;
    }

    /****************************************************获取时间与文件时间打包*********************************************/
    private byte[] getTime() {
        //按格式获取手机时间参数
        SimpleDateFormat sDateFormat = new SimpleDateFormat("yy,MM,dd,HH,mm,ss");
        String date = sDateFormat.format(new java.util.Date());
        //存储目录
        String folder_root = root + "/" + mDeviceName;
        int num_temp;
        byte[] num= new byte[20];
        String time[] = date.split(",");
        num[0] = 'F';
        num[1] = 'D';
        //获取文件时间
        File path = new File(folder_root);
        String file_time_send[] = new String[]{"0","0","0","0","0","0"};
        String file_time = getFileName(path);
        //判断目录不存在或者没有文件
        if(!file_time.equals("")) {
            if(file_time.length() >= 27) {
                file_time = file_time.substring(9, 27);
                for (int i = 0; i < 6; i++) {
                    file_time_send[i] = file_time.substring(i * 3, (i + 1) * 3 - 1);
                }
            }
        }
        //文件时间写入下发包中
        for(int i=0;i<6;i++) {
            num_temp = Integer.parseInt(file_time_send[i]);
            if(num_temp < 128) {
                num[i+2] = (byte) num_temp;
            }
            else{
                break;
            }
        }
        for(int i=0;i<6;i++) {
            num[i+8] = 0;
        }
        //对时时间
        for(int i=0;i<6;i++) {
            num_temp = Integer.parseInt(time[i]);
            if(num_temp < 128) {
                num[i+14] = (byte) num_temp;
            }
            else{
                break;
            }
        }
        //接收全部文件
        return num;
    }

    //获得最近保存的文件名
    private String getFileName(File root) {
        String lastFileName = "";
        // 先判断目录是否为空，否则会报空指针
        if (!root.exists()) {
            try {
                //按照指定的路径创建文件夹
                root.mkdirs();
            }
            catch (Exception e) {}
        }
        File[] files = root.listFiles();
        if (files != null) {
            //轮询目录中的文件
            for (File file : files) {
                if(file.getName().length() >= 27) {
                    String fileName = file.getName();
                    //比较文件名的时间大小
                    if (lastFileName.compareTo(fileName) < 0) {
                        lastFileName = fileName;
                    }
                }
            }
        }
        //返回最后写入文件的时间
        return lastFileName;
    }

    /******************************将接收到的数据写入256字节的get_byte[256]数组，并将get_byte[256]数组的数据保存到文件中****************************/
    private int recvCount = 0;
    private int recvNum = 0;
    private byte[] send_byte = new byte[2];
    private byte[] get_byte = new byte[256];
    public void RecvByte(byte[] buf) {
        if(buf.length > 0) {    //如果有数据
            /*recvCount用于记录蓝牙已经接收到的字节数，recvNum为总共需要接收到的字节数*/
            if(recvCount == 0) {    //如果数据头正确，即接收到蓝牙的第一帧数据，也即接收到蓝牙的所有数据时此if一直成立
                /*查找起始位*/
                int startBit;
                for (startBit = 0; startBit < buf.length ; startBit++) {
                    if (buf[startBit] == 0x5A) {    //startBit为起始符的位置
                        break;
                    }
                }
                if (buf[startBit] == 0x5A) {    //第1位起始符0x5A
                    recvNum = (buf[1+startBit]) & 0xff; //读取第2位的总数据数recvNum
                    /*将数据写入get_byte数组*/
                    if ((startBit + recvNum) <= 17) {   //如果接收的数据小于17个（蓝牙接收的包一次为20个字节，减去前2字节和结束字符0xAA剩余3字节）
                        if ((buf[startBit + recvNum + 2] & 0xff) == 0xAA) {     //结束符0xAA
                            /*去掉结束符写入剩余数据到缓存*/
                            for (int i = 0; i < buf.length - 3 - startBit; i++) {
                                get_byte[recvCount] = buf[i + 2 + startBit];    /*******************8. 将接收到的数据保存到get_byte数组，前来2字节为起始符 + 总数据数**************/
                                recvCount++;
                            }
                            writeRecvFile(get_byte, recvCount);  /*********************9. 将接收到的recvCount字节的get_byte[256]数组的数据保存到文件中***********************/
                            //向下位机发送数据接收成功消息
                            send_byte[0] = 0x5A;
                            mBluetoothLeService.writeData(send_byte);   //将数据写入BluetoothLeService服务
                            recvCount = 0;  //6. recvCount清0,因此下一次if(recvCount == 0)还会成立
                        }
                    } else {    //一般不成立
                        for (int i = 0; i < buf.length - 2 - startBit; i++) {
                            get_byte[recvCount] = buf[i + 2 + startBit];    //将接收到的数据保存到get_byte数组
                            recvCount++;
                        }
                    }
                }
            }
            else if(recvCount != 0){    //如果数据头不正确，即未接收到蓝牙的第一帧数据，也即接收到蓝牙的数据有丢失时此if一直成立
                if((recvNum - recvCount) < 20) {    //仅仅在最后一个包成立 --- 剩余数据小于20位,BLE蓝牙接收的包一次为20个字节
                    if((buf[recvNum - recvCount]&0xff) == 0xAA){    //判断结束位是否为结束符0xAA
                        for(int i=0;i<buf.length-1;i++) {   //去掉结束符0xAA写入剩余数据到get_byte数组
                            get_byte[recvCount] = buf[i];
                            recvCount++;
                        }
                        writeRecvFile(get_byte,recvCount);      //将接收到的recvCount字节的get_byte[256]数组的数据保存到文件中
                        //向下位机发送数据接收成功消息
                        send_byte[0] = 0x5A;
                        mBluetoothLeService.writeData(send_byte);   //将数据0x5A写入BluetoothLeService服务，表示接收成功
                    } else{   //接收的数据长度不符
                        /*向下位机发送数据接收失败消息*/
                        send_byte[0] = 0x7A;
                        mBluetoothLeService.writeData(send_byte);   //将数据写入BluetoothLeService服务
                    }
                    recvCount = 0;  //清空缓存
                } else if(recvCount > recvNum) {  //防止出现负数的情况，一般不会出现
                    send_byte[0] = 0x7A;
                    mBluetoothLeService.writeData(send_byte);
                    recvCount = 0;
                } else{ //文件未结尾，正常写入到缓存
                    for(int i=0;i<buf.length;i++) {
                        get_byte[recvCount] = buf[i];
                        recvCount++;
                    }
                }
            }
        }
    }

    //将接收到的buf_len字节的buf数组的数据保存到文件中，根据读取“BLE1.0”来判断文件头
    private String filenamenum = "";
    private int file_Num = 0;
    private List<Map<String, Object>> mInfoList = new ArrayList<>();
    public void writeRecvFile(byte[] buf, int buf_len) {
        String s;
        String  file_root = root + "/" + mDeviceName;   //根目录为: /压力数据/ID_0001
        s = asciiToString(buf);
        //如果接收到的17字节数据是第一帧，则会进入if创建文件，仅在第一次执行writeRecvFile时进入if创建文件
        if(s.startsWith("BLE1.0")){     //找到文件头（接收格式为前8字节为BLE1.0表示为压力表）
            int[] dat_time = new int[6];
            filenamenum = "";
            Calendar fromCalendar = Calendar.getInstance();     //计算文件生成的时间，用来生成文件名
            //由文件创建的起始记录时间创建文件名filenamenum
            for(int i=0;i<6;i++) {
                dat_time[i] = buf[i+19] & 0xff;     //dat_time数组用于保存日期：年月日时分秒
                switch(i)
                {
                    case 0: //如果是年
                        if(mDeviceName.length() >= 6) {
                            filenamenum = "ID" + mDeviceName.substring(2, 6) + "_"; //文件名：ID + 设备名（例6001，前2字节为BH用于判断压力表） + "_"
                        }
                        filenamenum = filenamenum + Integer.toString(dat_time[i]+2000) + "年";   //+2013 +年
                        //将时间存入日历
                        fromCalendar.set(Calendar.YEAR,(dat_time[i]+2000));     //将年存入Calendar.YEAR
                        break;
                    case 1:     //如果是月
                        if(dat_time[i] < 10) {
                            filenamenum = filenamenum + "0" + Integer.toString(dat_time[i]);
                        }
                        else filenamenum = filenamenum + Integer.toString(dat_time[i]);
                        filenamenum = filenamenum + "月";
                        fromCalendar.set(Calendar.MONTH,dat_time[i]);
                        break;
                    case 2:     //如果是日
                        if(dat_time[i] < 10) {
                            filenamenum = filenamenum + "0" + Integer.toString(dat_time[i]);
                        }
                        else filenamenum = filenamenum + Integer.toString(dat_time[i]);
                        filenamenum = filenamenum + "日";
                        fromCalendar.set(Calendar.DAY_OF_MONTH,dat_time[i]);
                        break;
                    case 3:     //如果是时
                        if(dat_time[i] < 10) {
                            filenamenum = filenamenum + "0" + Integer.toString(dat_time[i]);
                        }
                        else filenamenum = filenamenum + Integer.toString(dat_time[i]);
                        filenamenum = filenamenum + "时";
                        fromCalendar.set(Calendar.HOUR_OF_DAY,dat_time[i]);
                        break;
                    case 4:     //如果是分
                        if(dat_time[i] < 10) {
                            filenamenum = filenamenum + "0" + Integer.toString(dat_time[i]);
                        }
                        else filenamenum = filenamenum + Integer.toString(dat_time[i]);
                        filenamenum = filenamenum + "分";
                        fromCalendar.set(Calendar.MINUTE,dat_time[i]);
                        break;
                    case 5:     //如果是秒
                        if(dat_time[i] < 10) {
                            filenamenum = filenamenum + "0" + Integer.toString(dat_time[i]);
                        }
                        else filenamenum = filenamenum + Integer.toString(dat_time[i]);
                        filenamenum = filenamenum + "秒";
                        fromCalendar.set(Calendar.SECOND,dat_time[i]);
                        break;
                    default:break;
                }
            }
            //创建新文件filenamenum.dat
            try {
                createFiles.CreateText(filenamenum + ".dat", file_root);    //a. 创建文件夹及文件 --- 定义于CreateFile.java
            } catch (IOException e) {}
            String display_data = "";
            //获取接收的数据长度dataLen
            int dataLen;
            dataLen =  (buf[25]&0xff) | (buf[26]&0xff) << 8;
            //控件显示：文件数
            file_Num++;
            display_data = Integer.toString(file_Num) + ",";
            fileNum.setText(Integer.toString(file_Num));
            //控件显示：当前接收文件
            fileRecive.setText(filenamenum + ".dat");
            display_data = display_data + ( "\n" + filenamenum + ".dat") + ",";
            //获取日历累加的毫秒总数
            long startTime = fromCalendar.getTimeInMillis();
            //获取记录间隔时间
            int time_Interval = (buf[16]&0xff) | (buf[17]&0xff) << 8;
            //计算文件结束时间，总数据长度要减一
            long endTime = startTime + (time_Interval*1000*(dataLen-1));
            //将毫秒总数转换为具体日历时间
            fromCalendar.setTimeInMillis(endTime);
            //将结束时间转换为指定格式的时间输出到屏幕
            String endTimestring = "";
            for(int i=0;i<6;i++) {
                switch(i)
                {
                    case 0:
                        endTimestring = endTimestring + "结束时间：" + Integer.toString(fromCalendar.get(Calendar.YEAR)) + "年";
                        break;
                    case 1:
                        if (fromCalendar.get(Calendar.MONTH) < 10) {
                            endTimestring = endTimestring + "0" + Integer.toString(fromCalendar.get(Calendar.MONTH)) + "月";
                        } else endTimestring = endTimestring + Integer.toString(fromCalendar.get(Calendar.MONTH)) + "月";
                        break;
                    case 2:
                        if (fromCalendar.get(Calendar.DAY_OF_MONTH) < 10) {
                            endTimestring = endTimestring + "0" + Integer.toString(fromCalendar.get(Calendar.DAY_OF_MONTH)) + "日";
                        } else endTimestring = endTimestring + Integer.toString(fromCalendar.get(Calendar.DAY_OF_MONTH)) + "日";
                        break;
                    case 3:
                        if (fromCalendar.get(Calendar.HOUR_OF_DAY) < 10) {
                            endTimestring = endTimestring + "0" + Integer.toString(fromCalendar.get(Calendar.HOUR_OF_DAY)) + "时";
                        } else endTimestring = endTimestring + Integer.toString(fromCalendar.get(Calendar.HOUR_OF_DAY)) + "时";
                        break;
                    case 4:
                        if (fromCalendar.get(Calendar.MINUTE) < 10) {
                            endTimestring = endTimestring + "0" + Integer.toString(fromCalendar.get(Calendar.MINUTE)) + "分";
                        } else endTimestring = endTimestring + Integer.toString(fromCalendar.get(Calendar.MINUTE)) + "分";
                        break;
                    case 5:
                        if (fromCalendar.get(Calendar.SECOND) < 10) {
                            endTimestring = endTimestring + "0" + Integer.toString(fromCalendar.get(Calendar.SECOND)) + "秒";
                        } else endTimestring = endTimestring + Integer.toString(fromCalendar.get(Calendar.SECOND)) + "秒";
                        break;
                    default:break;
                }
            }
            display_data = display_data + endTimestring + ",";
            //打印记录点数
            display_data = display_data + ("记录点数："+Integer.toString(dataLen));
            if (!display_data.equals("")) {
                Map<String, Object> map;
                String data_string[] = display_data.split(",");
                map = new HashMap<String, Object>();
                map.put("num_file", data_string[0]);
                map.put("file_name", data_string[1]);
                map.put("end_time", data_string[2]);
                map.put("rec_point", data_string[3]);
                mInfoList.add(map);
                SimpleAdapter adapter = new SimpleAdapter(this, mInfoList, R.layout.listitem_file,
                        new String[]{"num_file","file_name","end_time","rec_point"},
                        new int[]{R.id.num_file,R.id.file_name,R.id.end_time,R.id.rec_point});
                mListView.setAdapter(adapter);
                mListView.setSelection(mListView.getMaxScrollAmount());
            }
        }
        //每次执行writeRecvFile时进入if将数据写入文件（上一个if只在第一次执行writeRecvFile时进入，目的是创建文件）
        if(!filenamenum.equals("")) {
            createFiles.writeFiles(buf, buf_len, filenamenum + ".dat", file_root);      //b. 向已创建的文件中写入数据 --- 定义于CreateFile.java
        }
    }

    //ascii转化为String
    public String asciiToString(byte[] bytes) {
        char[] buf = new char[bytes.length];
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < buf.length; i++) {
            buf[i] = (char) bytes[i];
            sb.append(buf[i]);
        }
        return sb.toString();
    }
}
