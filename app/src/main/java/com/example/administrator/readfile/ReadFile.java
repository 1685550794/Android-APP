package com.example.administrator.readfile;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.Environment;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.widget.EditText;
import android.widget.Toast;
import com.kekstudio.dachshundtablayout.DachshundTabLayout;
import com.kekstudio.dachshundtablayout.HelperUtils;
import com.kekstudio.dachshundtablayout.indicators.DachshundIndicator;
import com.kekstudio.dachshundtablayout.indicators.LineFadeIndicator;
import com.kekstudio.dachshundtablayout.indicators.LineMoveIndicator;
import com.kekstudio.dachshundtablayout.indicators.PointFadeIndicator;
import com.kekstudio.dachshundtablayout.indicators.PointMoveIndicator;
import com.leon.lfilepickerlibrary.LFilePicker;
import com.leon.lfilepickerlibrary.utils.Constant;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import lecho.lib.hellocharts.gesture.ContainerScrollType;
import lecho.lib.hellocharts.model.Axis;
import lecho.lib.hellocharts.model.AxisValue;
import lecho.lib.hellocharts.model.Line;
import lecho.lib.hellocharts.model.LineChartData;
import lecho.lib.hellocharts.model.PointValue;
import lecho.lib.hellocharts.gesture.ZoomType;
import lecho.lib.hellocharts.view.LineChartView;

public class ReadFile extends AppCompatActivity {
    private String filename;
    private LineChartView lineChart;
    private List<PointValue> mPointValues = new ArrayList<PointValue>();
    private List<AxisValue> mAxisValues = new ArrayList<AxisValue>();
    private static final String DOG_BREEDS[] = {"折线图","表格"};
    private ViewPager viewPager;
    private DachshundTabLayout tabLayout;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.dachshund);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        toolbar.setTitle("压力记录仪数据分析");
        toolbar.setNavigationIcon(R.mipmap.backincostyleone);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(new View.OnClickListener(){    //设置按下左上角的图标会返回上一个活动
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        viewPager = (ViewPager) findViewById(R.id.view_pager);
        viewPager.setAdapter(new PagerAdapter(getSupportFragmentManager()));

        tabLayout = (DachshundTabLayout) findViewById(R.id.tab_layout);
        tabLayout.setupWithViewPager(viewPager);

        intentFilter = new IntentFilter() ;     //1. 动态注册广播接收器
        intentFilter.addAction("com.example.administrator.readfile.Open");
        intentFilter.addAction("com.example.administrator.readfile.Delete");
        myBroadcastReceiver = new MyBroadcastReceiver() ;
        registerReceiver(myBroadcastReceiver, intentFilter) ;
    }

    @Override
    protected void onDestroy() {    //在APP退出前，注销广播接收器
        super.onDestroy();
        unregisterReceiver(myBroadcastReceiver);
    }

    //定义广播接收器
    private IntentFilter intentFilter ;
    private MyBroadcastReceiver myBroadcastReceiver ;
    class MyBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if(action.equals("com.example.administrator.readfile.Open")){   //3. 接收打开文件的广播
                FileOpened = true;
                readFile();
                data  = new int[(int)bytes.length];
                for (int i = 0; i < bytes.length; i++) {
                    if (bytes[i] < 0)
                        data[i] = bytes[i] + 256;
                    else
                        data[i]  =bytes[i];
                }
                dataCount = data[28]*256*256*256 + data[27]*256*256 + data[26]*256 +data[25];
                Calculate();
                editText_page  = (EditText)findViewById(R.id.editText_page);
                Num = 1;
                DrawForm(Num);      //4. 绘制表格
                Thread thread = new Thread(){
                    @Override
                    public void run() {
                        try {
                            Thread.currentThread().sleep(1000);//1000毫秒
                        } catch (Exception e) {}
                        drewTheBitmap();//5. 绘制折线图
                        initLineChart();//初始化
                    }
                };
                thread.start();
            } else if(action.equals("com.example.administrator.readfile.Delete")){      //接收删除文件的广播
                deleteFile();  //删除选中的文件
            }
        }
    }

    //加载菜单布局 --- 自动调用
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    private int[] data;
    private int dataCount;
    private boolean OpenOrDelete = true;
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()){
            case R.id.OpenFile:
                //TODO
                OpenOrDelete = true;
                openFromActivity();
                break;
            case R.id.DeleteFile:
                //TODO
                OpenOrDelete = false;
                openFromActivity();
                //deleteFile();  //保存EditText的内容到文件中
                break;
        }
        return true;
    }

    //启动另一个Activity用于选择打开的文件
    public void openFromActivity() {
        new LFilePicker()
                .withActivity(this)
                .withRequestCode(Consant.REQUESTCODE_FROM_ACTIVITY)
                .withTitle("文件选择")
                .withNotFoundBooks("至少选择一个文件")
                .start();
    }

    //执行openFromActivity后打开另一个Activity，当这个Activity关闭后自动调用此函数，在此函数中解析选中的文件名
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            if (requestCode == Consant.REQUESTCODE_FROM_ACTIVITY) {
                String str2;
                int count = 0;
                char fir;
                List<String> list = data.getStringArrayListExtra(Constant.RESULT_INFO);
                for (String s : list) {
                    str2 = s;
                    /*去掉前面的/storage/emulated/0*/
                    while (true) {
                        fir = str2.charAt(0);//获取字符串的第一个字符
                        if (fir == '/') {
                            count++;
                            if (count == 4)
                                break;
                            str2 = str2.substring(1, str2.length());    //去掉第一个字符
                        }
                        else {
                            str2 = str2.substring(1, str2.length());
                        }
                    }
                    filename = str2;
                    Toast.makeText(getApplicationContext(), str2, Toast.LENGTH_SHORT).show();
                    Intent intent;
                    if(OpenOrDelete == true)    //如果是打开文件
                        intent = new Intent("com.example.administrator.readfile.Open");     //2. 发送打开文件的广播
                    else    //如果是删除文件
                        intent = new Intent("com.example.administrator.readfile.Delete");   //发送删除文件的广播
                    sendBroadcast(intent);
                }
            }
        }
    }

    //解析数据函数
    private int interval;
    private void Calculate() {
        EditText edit1  = (EditText)findViewById(R.id.editText1);
        EditText edit2  = (EditText)findViewById(R.id.editText2);
        EditText edit3  = (EditText)findViewById(R.id.editText3);
        EditText edit4  = (EditText)findViewById(R.id.editText4);
        EditText edit5  = (EditText)findViewById(R.id.editText5);
        EditText edit6  = (EditText)findViewById(R.id.editText6);
        EditText edit7  = (EditText)findViewById(R.id.editText7);
        EditText edit8  = (EditText)findViewById(R.id.editText8);
        EditText edit9  = (EditText)findViewById(R.id.editText9);
        EditText edit10 = (EditText)findViewById(R.id.editText10);
        EditText edit11 = (EditText)findViewById(R.id.editText11);
        EditText edit12 = (EditText)findViewById(R.id.editText12);
        EditText edit13 = (EditText)findViewById(R.id.editText13);
        EditText edit14 = (EditText)findViewById(R.id.editText14);
        EditText edit15 = (EditText)findViewById(R.id.editText15);
        EditText edit16 = (EditText)findViewById(R.id.editText16);
        EditText edit17 = (EditText)findViewById(R.id.editText17);
        EditText edit18 = (EditText)findViewById(R.id.editText18);
        EditText edit19 = (EditText)findViewById(R.id.editText19);
        EditText edit20 = (EditText)findViewById(R.id.editText20);
        EditText edit21 = (EditText)findViewById(R.id.editText21);
        EditText edit22 = (EditText)findViewById(R.id.editText22);
        EditText edit23 = (EditText)findViewById(R.id.editText23);
        EditText edit24 = (EditText)findViewById(R.id.editText24);
        int a1,a2,a3,a4,a5,a6,b1,b2,b3,b4,b5,b6;
        /*文件类型*/
        String fileType = ""+(char)data[0]+(char)data[1]+(char)data[2]+(char)data[3]+(char)data[4]+(char)data[5];
        edit1.setText(fileType);   //将字符数组显示在EditText中

        /*生成文件时间*/
        String S_year, S_month, S_day, S_hour, S_minite, S_second;
        S_year = "20"+Integer.toString(data[8]);
        if (data[9]/10 == 0)
            S_month = "0"+Integer.toString(data[9]);
        else
            S_month = Integer.toString(data[9]);
        if (data[10]/10 == 0)
            S_day = "0"+Integer.toString(data[10]);
        else
            S_day = Integer.toString(data[10]);
        if (data[11]/10 == 0)
            S_hour = "0"+Integer.toString(data[11]);
        else
            S_hour = Integer.toString(data[11]);
        if (data[12]/10 == 0)
            S_minite = "0"+Integer.toString(data[12]);
        else
            S_minite = Integer.toString(data[12]);
        if (data[13]/10 == 0)
            S_second = "0"+Integer.toString(data[13]);
        else
            S_second = Integer.toString(data[13]);
        String time = S_year + "-" + S_month + "-" + S_day + " " + S_hour + ":" + S_minite + ":" + S_second;
        //String time= "20"+Integer.toString(data[8])+"-"+Integer.toString(data[9])+"-"+Integer.toString(data[10])+" "+Integer.toString(data[11])+":"+Integer.toString(data[12])+":"+Integer.toString(data[13]);
        edit2.setText(time);   //将字符数组显示在EditText中

        /*表号*/
        int unmber = data[15]*256 + data[14];
        edit3.setText(Integer.toString(unmber));   //将字符数组显示在EditText中

        /*采样间隔*/
        interval = data[17]*256 + data[16];
        edit4.setText(Integer.toString(interval) + "秒");   //将字符数组显示在EditText中

        /*计算最大值最小值时刻*/
        float maxmum = ((float)(data[128+1]*256 + data[128]))/100;
        float minmum = ((float)(data[128+1]*256 + data[128]))/100;
        int max = 0, min = 0;
        float tmp;
        for (int i = 0; i < dataCount*2;){
            tmp = ((float)(data[128+i+1]*256 + data[128+i]))/100;
            if (maxmum < tmp){
                maxmum = tmp;
                max = i/2;
            }
            if (minmum > tmp){
                minmum = tmp;
                min = i/2;
            }
            i = i+2;
        }
        edit17.setText(timeCalculate(max));
        edit19.setText(timeCalculate(min));
        edit18.setText(String.valueOf(maxmum));
        edit20.setText(String.valueOf(minmum));

        /*压力值与采样值*/
        a1 = (data[35]*256 + data[34])/100;
        b1 = data[33]*256 + data[32];
        edit5.setText(Integer.toString(a1));   //将字符数组显示在EditText中
        edit11.setText(Integer.toString(b1));   //将字符数组显示在EditText中

        a2 = (data[39]*256 + data[38])/100;
        b2 = data[37]*256 + data[36];
        edit6.setText(Integer.toString(a2));   //将字符数组显示在EditText中
        edit12.setText(Integer.toString(b2));   //将字符数组显示在EditText中

        a3 = (data[43]*256 + data[42])/100;
        b3 = data[41]*256 + data[40];
        edit7.setText(Integer.toString(a3));   //将字符数组显示在EditText中
        edit13.setText(Integer.toString(b3));   //将字符数组显示在EditText中

        a4 = (data[47]*256 + data[46])/100;
        b4 = data[45]*256 + data[44];
        edit8.setText(Integer.toString(a4));   //将字符数组显示在EditText中
        edit14.setText(Integer.toString(b4));   //将字符数组显示在EditText中

        a5 = (data[51]*256 + data[50])/100;
        b5 = data[49]*256 + data[48];
        edit9.setText(Integer.toString(a5));   //将字符数组显示在EditText中
        edit15.setText(Integer.toString(b5));   //将字符数组显示在EditText中

        a6 = (data[55]*256 + data[54])/100;
        b6 = data[53]*256 + data[52];
        edit10.setText(Integer.toString(a6));   //将字符数组显示在EditText中
        edit16.setText(Integer.toString(b6));   //将字符数组显示在EditText中

        /*硬件版本号*/
        String Version = ""+(char)data[66]+(char)data[67]+(char)data[68]+(char)data[69]+(char)data[70]+(char)data[71];
        edit21.setText(Version);   //将字符数组显示在EditText中

        /*电池电压*/
        float V = ((float)(data[65]*256 + data[64]))/100;
        String Voltage=""+String.valueOf(V)+"V";
        edit22.setText(Voltage);   //将字符数组显示在EditText中

        /*压力上限值*/
        float P = ((float)(data[58]*256 + data[57]))/100;
        String Pressure=""+String.valueOf(P)+"Kpa";
        edit23.setText(Pressure);   //将字符数组显示在EditText中

        /*采集数据个数*/
        edit24.setText(Integer.toString(dataCount));   //将字符数组显示在EditText中
    }

    //绘制表格
    private void DrawForm(int num){
        EditText editText1_num  = (EditText)findViewById(R.id.editText1_num);
        editText1_num.setText("个数");
        EditText editText2_time  = (EditText)findViewById(R.id.editText2_time);
        editText2_time.setText("时间");
        EditText editText3_value  = (EditText)findViewById(R.id.editText3_value);
        editText3_value.setText("压力值:Kpa");

        EditText edit101  = (EditText)findViewById(R.id.editText101);
        EditText edit102  = (EditText)findViewById(R.id.editText102);
        EditText edit103  = (EditText)findViewById(R.id.editText103);
        EditText edit104  = (EditText)findViewById(R.id.editText104);
        EditText edit105  = (EditText)findViewById(R.id.editText105);
        EditText edit106  = (EditText)findViewById(R.id.editText106);
        EditText edit107  = (EditText)findViewById(R.id.editText107);
        EditText edit108  = (EditText)findViewById(R.id.editText108);
        EditText edit109  = (EditText)findViewById(R.id.editText109);
        EditText edit110  = (EditText)findViewById(R.id.editText110);
        EditText edit111  = (EditText)findViewById(R.id.editText111);
        EditText edit112  = (EditText)findViewById(R.id.editText112);
        EditText edit113  = (EditText)findViewById(R.id.editText113);
        EditText edit114  = (EditText)findViewById(R.id.editText114);
        EditText edit115  = (EditText)findViewById(R.id.editText115);

        EditText edit201  = (EditText)findViewById(R.id.editText201);
        EditText edit202  = (EditText)findViewById(R.id.editText202);
        EditText edit203  = (EditText)findViewById(R.id.editText203);
        EditText edit204  = (EditText)findViewById(R.id.editText204);
        EditText edit205  = (EditText)findViewById(R.id.editText205);
        EditText edit206  = (EditText)findViewById(R.id.editText206);
        EditText edit207  = (EditText)findViewById(R.id.editText207);
        EditText edit208  = (EditText)findViewById(R.id.editText208);
        EditText edit209  = (EditText)findViewById(R.id.editText209);
        EditText edit210  = (EditText)findViewById(R.id.editText210);
        EditText edit211  = (EditText)findViewById(R.id.editText211);
        EditText edit212  = (EditText)findViewById(R.id.editText212);
        EditText edit213  = (EditText)findViewById(R.id.editText213);
        EditText edit214  = (EditText)findViewById(R.id.editText214);
        EditText edit215  = (EditText)findViewById(R.id.editText215);

        EditText edit301  = (EditText)findViewById(R.id.editText301);
        EditText edit302  = (EditText)findViewById(R.id.editText302);
        EditText edit303  = (EditText)findViewById(R.id.editText303);
        EditText edit304  = (EditText)findViewById(R.id.editText304);
        EditText edit305  = (EditText)findViewById(R.id.editText305);
        EditText edit306  = (EditText)findViewById(R.id.editText306);
        EditText edit307  = (EditText)findViewById(R.id.editText307);
        EditText edit308  = (EditText)findViewById(R.id.editText308);
        EditText edit309  = (EditText)findViewById(R.id.editText309);
        EditText edit310  = (EditText)findViewById(R.id.editText310);
        EditText edit311  = (EditText)findViewById(R.id.editText311);
        EditText edit312  = (EditText)findViewById(R.id.editText312);
        EditText edit313  = (EditText)findViewById(R.id.editText313);
        EditText edit314  = (EditText)findViewById(R.id.editText314);
        EditText edit315  = (EditText)findViewById(R.id.editText315);

        edit101.setText(Integer.toString(1+15*(num-1)));
        edit102.setText(Integer.toString(2+15*(num-1)));
        edit103.setText(Integer.toString(3+15*(num-1)));
        edit104.setText(Integer.toString(4+15*(num-1)));
        edit105.setText(Integer.toString(5+15*(num-1)));
        edit106.setText(Integer.toString(6+15*(num-1)));
        edit107.setText(Integer.toString(7+15*(num-1)));
        edit108.setText(Integer.toString(8+15*(num-1)));
        edit109.setText(Integer.toString(9+15*(num-1)));
        edit110.setText(Integer.toString(10+15*(num-1)));
        edit111.setText(Integer.toString(11+15*(num-1)));
        edit112.setText(Integer.toString(12+15*(num-1)));
        edit113.setText(Integer.toString(13+15*(num-1)));
        edit114.setText(Integer.toString(14+15*(num-1)));
        edit115.setText(Integer.toString(15+15*(num-1)));

        edit201.setText(timeCalculate(0+15*(num-1)));
        edit202.setText(timeCalculate(1+15*(num-1)));
        edit203.setText(timeCalculate(2+15*(num-1)));
        edit204.setText(timeCalculate(3+15*(num-1)));
        edit205.setText(timeCalculate(4+15*(num-1)));
        edit206.setText(timeCalculate(5+15*(num-1)));
        edit207.setText(timeCalculate(6+15*(num-1)));
        edit208.setText(timeCalculate(7+15*(num-1)));
        edit209.setText(timeCalculate(8+15*(num-1)));
        edit210.setText(timeCalculate(9+15*(num-1)));
        edit211.setText(timeCalculate(10+15*(num-1)));
        edit212.setText(timeCalculate(11+15*(num-1)));
        edit213.setText(timeCalculate(12+15*(num-1)));
        edit214.setText(timeCalculate(13+15*(num-1)));
        edit215.setText(timeCalculate(14+15*(num-1)));

        float value;
        value= ((float)(data[128 + 1 + 15*(num-1)*2]*256 + data[128 + 0*2 + 15*(num-1)*2]))/100;
        edit301.setText(AddZeroOfValue(String.valueOf(value)));     //edit301.setText(String.valueOf(value));
        value= ((float)(data[128 + 1*2+1 + 15*(num-1)*2]*256 + data[128 + 1*2 + 15*(num-1)*2]))/100;
        edit302.setText(AddZeroOfValue(String.valueOf(value)));
        value= ((float)(data[128 + 2*2+1 + 15*(num-1)*2]*256 + data[128 + 2*2 + 15*(num-1)*2]))/100;
        edit303.setText(AddZeroOfValue(String.valueOf(value)));
        value= ((float)(data[128 + 3*2+1 + 15*(num-1)*2]*256 + data[128 + 3*2 + 15*(num-1)*2]))/100;
        edit304.setText(AddZeroOfValue(String.valueOf(value)));
        value= ((float)(data[128 + 4*2+1 + 15*(num-1)*2]*256 + data[128 + 4*2 + 15*(num-1)*2]))/100;
        edit305.setText(AddZeroOfValue(String.valueOf(value)));
        value= ((float)(data[128 + 5*2+1 + 15*(num-1)*2]*256 + data[128 + 5*2 + 15*(num-1)*2]))/100;
        edit306.setText(AddZeroOfValue(String.valueOf(value)));
        value= ((float)(data[128 + 6*2+1 + 15*(num-1)*2]*256 + data[128 + 6*2 + 15*(num-1)*2]))/100;
        edit307.setText(AddZeroOfValue(String.valueOf(value)));
        value= ((float)(data[128 + 7*2+1 + 15*(num-1)*2]*256 + data[128 + 7*2 + 15*(num-1)*2]))/100;
        edit308.setText(AddZeroOfValue(String.valueOf(value)));
        value= ((float)(data[128 + 8*2+1 + 15*(num-1)*2]*256 + data[128 + 8*2 + 15*(num-1)*2]))/100;
        edit309.setText(AddZeroOfValue(String.valueOf(value)));
        value= ((float)(data[128 + 9*2+1 + 15*(num-1)*2]*256 + data[128 + 9*2 + 15*(num-1)*2]))/100;
        edit310.setText(AddZeroOfValue(String.valueOf(value)));
        value= ((float)(data[128 + 10*2+1 + 15*(num-1)*2]*256 + data[128 + 10*2 + 15*(num-1)*2]))/100;
        edit311.setText(AddZeroOfValue(String.valueOf(value)));
        value= ((float)(data[128 + 11*2+1 + 15*(num-1)*2]*256 + data[128 + 11*2 + 15*(num-1)*2]))/100;
        edit312.setText(AddZeroOfValue(String.valueOf(value)));
        value= ((float)(data[128 + 12*2+1 + 15*(num-1)*2]*256 + data[128 + 12*2 + 15*(num-1)*2]))/100;
        edit313.setText(AddZeroOfValue(String.valueOf(value)));
        value= ((float)(data[128 + 13*2+1 + 15*(num-1)*2]*256 + data[128 + 13*2 + 15*(num-1)*2]))/100;
        edit314.setText(AddZeroOfValue(String.valueOf(value)));
        value= ((float)(data[128 + 14*2+1 + 15*(num-1)*2]*256 + data[128 + 14*2 + 15*(num-1)*2]))/100;
        edit315.setText(AddZeroOfValue(String.valueOf(value)));

        editText_page.setText(Integer.toString(num));
    }

    //数值补0，确保显示小数点后两位
    private String AddZeroOfValue(String str){
        if (str.indexOf(".") == -1)     //如果在字符串str中未找到"."返回-1，说明这是个整数
            return str + ".00";
        else {
            String str1 =str;
            int des = str1.indexOf(".") + 1;    //获取"."后第一个字符的位置
            String str2="";     //保存"."后对应的字符串
            for (int i=des; i<str.length();i++) {
                char ch = str.charAt(i);
                str2 = str2 + String.valueOf(ch);
            }
            if(str2.length() == 1)  //如果"."后只有一个字符，又由于压力值小数点后保留两位小数，所以要补0
                str = str + "0";
            return str;
        }
    }

    //上一页
    private  int Num;
    private EditText editText_page;
    private boolean FileOpened = false;
    public void PrePage(View view) {
        if (FileOpened == true){
            String pageNum = editText_page.getText().toString();
            int Num = Integer.parseInt(pageNum);
            Num--;
            if (Num < 1)
                Num = 1;
            pageNum = Integer.toString(Num);
            editText_page.setText(pageNum);
            DrawForm(Num);
        }
    }
    //下一页
    public void NextPage(View view) {
        if (FileOpened == true){
            String pageNum = editText_page.getText().toString();
            int Num = Integer.parseInt(pageNum);
            Num++;
            if (Num > dataCount/15)
                Num = dataCount/15;
            pageNum = Integer.toString(Num);
            editText_page.setText(pageNum);
            DrawForm(Num);
        }
    }
    //跳转
    public void SkipPage(View view) {
        if (FileOpened == true){
            String pageNum = editText_page.getText().toString();
            int Num = Integer.parseInt(pageNum);
            if (Num > dataCount/15)
                Num = dataCount/15;
            editText_page.setText(pageNum);
            DrawForm(Num);
        }
    }

    //由采样点的位置num计算采样点的时间
    private String timeCalculate(int num){//7114 13-11-6 14:56:38
        String time;
        int year = data[19]+2000, month = data[20], day = data[21], hour = data[22], minite = data[23], second = data[24];
        int tmpnum = num*interval + hour*3600 + minite*60 + second;     //增加的秒数
        int increaseDate = tmpnum/86400;    //增加的天数
        int ytemp=year,mtemp=month,dtemp=day;   //保存日期
        for (int i = 0; i < increaseDate; i++){
            dtemp++;
            if(dtemp>28)
            {
                if(((ytemp%100==0&&ytemp%400==0)||(ytemp%100!=0&&ytemp%4==0))&&mtemp==2)
                {
                    if(dtemp>29) {dtemp=1;mtemp++;}
                }
                else if(((ytemp%100==0&&ytemp%400==0)||(ytemp%100!=0&&ytemp%4==0))&&mtemp==2)
                {
                    if(dtemp>28){dtemp=1;mtemp++;}
                }
                else if(mtemp==1||mtemp==3||mtemp==5||mtemp==7||mtemp==8||mtemp==10||mtemp==12)
                {
                    if(dtemp>31) {dtemp=1;mtemp++;}
                }
                else if(mtemp==4||mtemp==6||mtemp==9||mtemp==11)
                {
                    if(dtemp>30) {dtemp=1;mtemp++;}
                }
            }
        }
        if(mtemp>12)//24121
        {
            mtemp=1;
            ytemp++;
        }
        year=ytemp;
        month=mtemp;
        day=dtemp;
        hour=tmpnum%86400/3600;
        minite=(tmpnum%86400%3600)/60;
        second=tmpnum%60;
        String S_year, S_month, S_day, S_hour, S_minite, S_second;
        S_year = Integer.toString(year);
        if (month/10 == 0)
            S_month = "0"+Integer.toString(month);
        else
            S_month = Integer.toString(month);
        if (day/10 == 0)
            S_day = "0"+Integer.toString(day);
        else
            S_day = Integer.toString(day);
        if (hour/10 == 0)
            S_hour = "0"+Integer.toString(hour);
        else
            S_hour = Integer.toString(hour);
        if (minite/10 == 0)
            S_minite = "0"+Integer.toString(minite);
        else
            S_minite = Integer.toString(minite);
        if (second/10 == 0)
            S_second = "0"+Integer.toString(second);
        else
            S_second = Integer.toString(second);
        time = ""+S_year+"-"+S_month+"-"+S_day+" "+S_hour+":"+S_minite+":"+S_second;
        //time = ""+Integer.toString(year)+"-"+Integer.toString(month)+"-"+Integer.toString(day)+" "+Integer.toString(hour)+":"+Integer.toString(minite)+":"+Integer.toString(second);
        return time;
    }

    //初始化LineChart
    private void initLineChart(){
        Line line = new Line(mPointValues).setColor(Color.GREEN).setCubic(false);  //折线的颜色
        List<Line> lines = new ArrayList<Line>();
        //line.setShape();//折线图上每个数据点的形状  这里是圆形 （有三种 ：ValueShape.SQUARE  ValueShape.CIRCLE  ValueShape.DIAMOND）
        line.setCubic(true);//曲线是否平滑
        line.setFilled(false);//是否填充曲线的面积
        line.setColor(Color.RED);
		line.setHasLabels(true);//曲线的数据坐标是否加上备注
        line.setHasLabelsOnlyForSelected(true);//点击数据坐标提示数据（设置了这个line.setHasLabels(true);就无效）
        line.setHasLines(true);//是否用直线显示。如果为false 则没有曲线只有点显示
        line.setHasPoints(false);//是否显示圆点 如果为false 则没有原点只有点显示
        line.setStrokeWidth(1);     //设置线宽
        //line.setHasLabels(true);
        //line.setCubic(true);
        lines.add(line);
        LineChartData data = new LineChartData();
        data.setLines(lines);

        //坐标轴
    /*    Axis axisX = new Axis(); //X轴
        axisX.setHasTiltedLabels(true);
        axisX.setTextColor(Color.WHITE);  //设置字体颜色
        axisX.setName("未来几天的天气");  //表格名称
        axisX.setTextSize(7);//设置字体大小
        axisX.setMaxLabelChars(7);  //最多几个X轴坐标
        axisX.setValues(mAxisValues);  //填充X轴的坐标名称
        data.setAxisXBottom(axisX); //x 轴在底部
//	    data.setAxisXTop(axisX);  //x 轴在顶部
  */
        Axis axisX = new Axis(); //X轴
        axisX.setHasTiltedLabels(true);
        axisX.setTextColor(Color.BLUE);  //设置字体颜色
        //axisX.setMaxLabelChars(20);  //设置轴标签可显示的最大字符个数
        axisX.setName("时间");  //表格名称
        axisX.setTextSize(7);//设置字体大小
        axisX.setMaxLabelChars(7);  //最多几个X轴坐标
        axisX.setValues(mAxisValues);  //填充X轴的坐标名称
        axisX.setHasLines(true);    //网格线
        axisX.setLineColor(Color.GREEN);    //网格线的颜色
        axisX.setHasSeparationLine(true);
        data.setAxisXBottom(axisX); //x 轴在底部
//	    data.setAxisXTop(axisX);  //x 轴在顶部

        Axis axisY = new Axis();  //Y轴
        axisY.setTextColor(Color.BLUE);  //设置字体颜色
        axisY.setMaxLabelChars(7); //默认是3，只能看最后三个数字
        axisY.setName("单位：Kpa");//y轴标注
        axisY.setTextSize(7);//设置字体大小
        axisY.setHasLines(true);    //网格线
        axisY.setLineColor(Color.GREEN);    //网格线的颜色
        axisY.setInside(true);      //轴坐标的值显示在图表内侧
        //axisY.setHasSeparationLine(true);
        data.setAxisYLeft(axisY);  //Y轴设置在左边
//	    data.setAxisYRight(axisY);  //y轴设置在右边

        lineChart = (LineChartView)findViewById(R.id.line_chart);
        //设置行为属性，支持缩放、滑动以及平移
        lineChart.setInteractive(true);
        lineChart.setZoomType(ZoomType.HORIZONTAL_AND_VERTICAL);
        lineChart.setContainerScrollEnabled(true, ContainerScrollType.HORIZONTAL);
        lineChart.setLineChartData(data);
        //lineChart.setValueTouchEnabled(true);   //设置是否允许点击图标上的值
        //lineChart.setValueSelectionEnabled(true);   //设置是否可以选中图表中的值，即当点击图表中的数据值后，会一直处于选中状态，直到用户点击其他空间。默认为false
        lineChart.setVisibility(View.VISIBLE);
    }

    //绘制折线图
    private void drewTheBitmap(){
        float tmp;
        int i;
        int Sum = dataCount;    //数据个数
        int Count;  //字节数
        String label;
        Count = Sum*2;
        mAxisValues.clear();    //清除上一次作的折线图的标签
        mPointValues.clear();   //清除上一次作的折线图的标签对应的数值
        for (i = 0; i < Sum; i++) {
            if (Sum < 5000) {
                label = timeCalculate(i);
                if (((i*interval)%3600) == 0)
                    mAxisValues.add(new AxisValue(i).setLabel(label));
            } else if (i % (Sum/5000) == 0) {   //每隔Sum/5000个点插入一个时间标签，目的是防止点过多卡死
                label = timeCalculate(i);
                if (((i*interval)%3600) == 0)
                    mAxisValues.add(new AxisValue(i).setLabel(label));
            }
        }

        for (i = 0; i < Count; ) {
            if (Sum < 5000) {
                tmp = ((float)(data[128+i+1]*256 + data[128+i]))/100;
                mPointValues.add(new PointValue(i/2, tmp));
                i = i + 2;
            } else if ((i/2) % (Sum/5000) == 0) {   //每隔Sum/5000个点插入一个时间标签对应的数值，目的是防止点过多卡死
                tmp = ((float) (data[128 + i + 1] * 256 + data[128 + i])) / 100;
                mPointValues.add(new PointValue(i / 2, tmp));
                i = i + 2;
            } else {
                i = i + 2;
            }
        }
    }

    //从SD卡读取文件
    private byte[] bytes;
    public  void readFile() {
        //读的时候要用字符流   万一里面有中文
        String state = Environment.getExternalStorageState();
        if (!state.equals(Environment.MEDIA_MOUNTED)) {
            Toast.makeText(this, "SD卡未就绪", Toast.LENGTH_SHORT).show();
            //return "";
        }
        File root = Environment.getExternalStorageDirectory();
        try {
            //fis = new FileInputStream(root + filename);     //filename为文件名
            //filename = "/1.dat";
            File file =new File(root + filename);
            long len = file.length();
            bytes = new byte[(int)len];

            BufferedInputStream bufferedInputStream=new BufferedInputStream(new FileInputStream(file));
            int r = bufferedInputStream.read( bytes );
            if (r != len)
                throw new IOException("读取文件不正确");
            bufferedInputStream.close();

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
        }
    }

    //删除SD卡文件
    public void deleteFile() {
        String state = Environment.getExternalStorageState();
        if (!state.equals(Environment.MEDIA_MOUNTED)) {
            Toast.makeText(this, "SD卡未就绪", Toast.LENGTH_SHORT).show();
            return;
        }
        //取得SD卡根目录
        File root = Environment.getExternalStorageDirectory();
        File myFile=new File(root+filename);
        //File myFile=new File(root,"sd.txt");
        if (myFile.exists()) {
            myFile.delete();
            Toast.makeText(this,"文件已删除",Toast.LENGTH_SHORT).show();
        }else {
            Toast.makeText(this,"文件不存在",Toast.LENGTH_SHORT).show();
        }
    }

    //左右滑动相关
    public class PagerAdapter extends FragmentStatePagerAdapter {
        public PagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(int i) {
            if (i == 0)
                return new PageFragment();
            else
                return new PageFragment2();
        }

        @Override
        public int getCount() {
            return DOG_BREEDS.length;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            return DOG_BREEDS[position];
        }
    }

    //折线图相关
    public static class PageFragment extends Fragment {
        public PageFragment() {}
        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState) {
            return inflater.inflate(R.layout.first_layout, container, false);   //启动Activity时加载first_layout
        }
    }

    //表格相关
    public static class PageFragment2 extends Fragment {
        public PageFragment2() {}
        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState) {
            return inflater.inflate(R.layout.form_layout, container, false);   //启动Activity时加载form_layout
        }
    }
}



