package com.example.administrator.readfile;

import android.Manifest;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.Build;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class BleDeviceScan extends AppCompatActivity {
    private BluetoothAdapter mBluetoothAdapter;         //蓝牙适配器
    ListView listView;
    MyListAdapter listAdapter;
    private ProgressBar progressBar;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.devicescan);    //加载布局文件
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        toolbar.setTitle("BLE抄表");
        toolbar.setNavigationIcon(R.mipmap.toolbar_image);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {
                new AlertDialog.Builder(BleDeviceScan.this)
                        .setIcon(R.mipmap.main)
                        .setTitle(R.string.version)
                        .setMessage(R.string.message)
                        .setPositiveButton("确定", null)
                        .show();
            }
        });
        progressBar = (ProgressBar) findViewById(R.id.progress_bar);
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();    //1. 获得蓝牙适配器BluetoothAdapter
        if (mBluetoothAdapter == null) {
            Toast.makeText(this, "蓝牙设备不支持", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        listView = (ListView)findViewById(R.id.device_list_view);   //获得ListView的ID
        listAdapter = new MyListAdapter();      //实例化列表适配器 --- 内部类MyListAdapter
        listView.setAdapter(listAdapter);
        listAdapter.notifyDataSetChanged();    //2. 如果列表适配器的内容改变时需要强制调用getView来刷新每个Item的内容,可以实现动态的刷新列表的功能
    }

    MyBtReceiver btReceiver;
    IntentFilter intentFilter;
    //当Activity可见前自动调用此函数
    @Override
    protected void onResume() {
        super.onResume();
        // 蓝牙未打开，询问打开
        if (!mBluetoothAdapter.isEnabled()) {
            Intent turnOnBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(turnOnBtIntent, Params.REQUEST_ENABLE_BT);
        }

        intentFilter = new IntentFilter();  //筛选广播接收器注册动态的filter
        btReceiver = new MyBtReceiver();    //3. 实例化广播接收器
        intentFilter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);  //添加IntentFilter的动作
        intentFilter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        intentFilter.addAction(BluetoothDevice.ACTION_FOUND);
        BleDeviceScan.this.registerReceiver(btReceiver, intentFilter);   //注册广播接收器

        // 蓝牙已开启
        if (mBluetoothAdapter.isEnabled()) {
            showBondDevice();       //4. 显示已经绑定的设备
        }

        //listView中选中某一项
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                //TODO
                BluetoothDevice device = deviceList.get(position);
                if (device == null) {
                    return;
                }
                final Intent intent = new Intent(BleDeviceScan.this, BleWriteFile.class);
                intent.putExtra(BleWriteFile.EXTRAS_DEVICE_NAME, device.getName());   //设备名传递给下一个Activity
                intent.putExtra(BleWriteFile.EXTRAS_DEVICE_ADDRESS, device.getAddress());     //设备地址传递给下一个Activity
                startActivity(intent);  /****************************************7.1 启动Activity2开始接收数据**************************************/
            }
        });
    }

    //此函数为自动调用，创建菜单项
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_devicescan, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {   //右上的三个小圆点
        switch (item.getItemId()) {
            case R.id.menu_scan:    //搜索设备
                if (mBluetoothAdapter.isDiscovering()) {     //如果正在搜索
                    mBluetoothAdapter.cancelDiscovery();     //取消搜索
                }
                if (Build.VERSION.SDK_INT >= 6.0) {     //如果Android版本大于6.0，需要加入运行时的权限
                    ActivityCompat.requestPermissions(BleDeviceScan.this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                            Params.MY_PERMISSION_REQUEST_CONSTANT);
                }
                mBluetoothAdapter.startDiscovery();  //5. 开始搜索蓝牙设备
                break;
            case R.id.menu_analysis:      //数据分析软件
                //TODO
                final Intent intent = new Intent(BleDeviceScan.this, ReadFile.class);
                startActivity(intent);          /****************************************7.2 启动Activity3开始分析数据**************************************/
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    List<BluetoothDevice> deviceList = new ArrayList<>();
    //内部类MyListAdapter --- 蓝牙设备列表的adapter
    private class MyListAdapter extends BaseAdapter {
        private LayoutInflater mInflator;
        public MyListAdapter() {
            mInflator = BleDeviceScan.this.getLayoutInflater();
            //BleDeviceScan.this.registerReceiver();
        }
        //清空ArrayList列表
        public void clear() {
            deviceList.clear();
        }
        //获得ArrayList列表的设备个数
        @Override
        public int getCount() {
            return deviceList.size();
        }
        //在ArrayList列表中获得位置为position的设备
        @Override
        public Object getItem(int position) {
            return deviceList.get(position);
        }
        @Override
        public long getItemId(int position) {
            return position;
        }
        //如果列表适配器的内容改变时需要强制调用getView来刷新每个Item的内容,可以实现动态的刷新列表的功能
        @Override
        public View getView(int position, View view, ViewGroup parent) {
            ViewHolder viewHolder;
            if (view == null) {
                view = mInflator.inflate(R.layout.listitem_device, parent, false);

                viewHolder = new ViewHolder();
                viewHolder.deviceImage = (ImageView) view.findViewById(R.id.device_image);
                viewHolder.deviceName = (TextView) view.findViewById(R.id.device_name);
                viewHolder.deviceMac = (TextView) view.findViewById(R.id.device_mac);
                viewHolder.deviceState = (TextView) view.findViewById(R.id.device_state);
                view.setTag(viewHolder);
            } else {
                viewHolder = (ViewHolder) view.getTag();
            }
            int code = deviceList.get(position).getBondState();
            String name = deviceList.get(position).getName();       //获得设备名
            String mac = deviceList.get(position).getAddress();     //获得MAC地址
            String state;
            if (name == null || name.length() == 0) {
                name = "未命名设备";
            }
            if (code == BluetoothDevice.BOND_BONDED) {
                state = "ready";
                viewHolder.deviceState.setTextColor(getResources().getColor(R.color.green));
            } else {
                state = "new";
                viewHolder.deviceState.setTextColor(getResources().getColor(R.color.red));
            }
            if (mac == null || mac.length() == 0) {
                mac = "未知 mac 地址";
            }
            if (name != null && name.length() > 2) {    //如果获得了设备名
                if(name.substring(0,2).equals("BH")) {    //如果设备名前两个字母为BH，则表示此设备为压力表
                    viewHolder.deviceImage.setImageResource(R.mipmap.main);  //显示图标
                    viewHolder.deviceName.setText(name);  //显示名称
                }
                else {      //否则此设备为非压力表设备
                    viewHolder.deviceImage.setImageResource(R.drawable.ble_device);
                    viewHolder.deviceName.setText(name);
                    view.setBackgroundColor(Color.LTGRAY);
                }
            } else {
                viewHolder.deviceImage.setImageResource(R.drawable.unknow_device);
                viewHolder.deviceName.setText("未知设备");
                view.setBackgroundColor(Color.LTGRAY);
            }
            //viewHolder.deviceName.setText(name);
            viewHolder.deviceMac.setText(mac);
            viewHolder.deviceState.setText(state);
            return view;
        }
    }

    //内部类ViewHolder --- 与adapter配合的viewholder，对应布局文件Listitem_device.xml
    static class ViewHolder {
        public ImageView deviceImage;
        public TextView deviceName;
        public TextView deviceMac;
        public TextView deviceState;
    }


    /************************************************************************************************************/

    //用户打开蓝牙后，显示已绑定的设备列表
    private void showBondDevice() {
        deviceList.clear();
        Set<BluetoothDevice> tmp = mBluetoothAdapter.getBondedDevices();
        for (BluetoothDevice d : tmp) {
            deviceList.add(d);  //将已经绑定的设备将入蓝牙列表
        }
        listAdapter.notifyDataSetChanged();     //如果列表适配器的内容改变时需要强制调用getView来刷新每个Item的内容,可以实现动态的刷新列表的功能
    }

    //广播接收器
    private class MyBtReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothAdapter.ACTION_DISCOVERY_STARTED.equals(action)) {     //点击搜索会执行mBluetoothAdapter.startDiscovery();  //开始搜索蓝牙设备
                progressBar.setVisibility(View.VISIBLE);
                Toast.makeText(getApplicationContext(), "开始搜索 ...", Toast.LENGTH_SHORT).show();
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                progressBar.setVisibility(View.GONE);
                Toast.makeText(getApplicationContext(), "搜索结束", Toast.LENGTH_SHORT).show();
            } else if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (isNewDevice(device)) {
                    deviceList.add(device);     //6. 在蓝牙列表中添加新设备
                    listAdapter.notifyDataSetChanged();
                }
            }
        }
    }

    //判断搜索的设备是新蓝牙设备，且不重复
    private boolean isNewDevice(BluetoothDevice device){
        boolean repeatFlag = false;
        for (BluetoothDevice d :
                deviceList) {
            if (d.getAddress().equals(device.getAddress())){
                repeatFlag=true;
            }
        }
        //不是已绑定状态，且列表中不重复
        return device.getBondState() != BluetoothDevice.BOND_BONDED && !repeatFlag;
    }
}
