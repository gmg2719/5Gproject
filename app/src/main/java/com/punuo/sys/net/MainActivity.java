package com.punuo.sys.net;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.TrafficStats;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.telephony.CellIdentityNr;
import android.telephony.CellInfo;
import android.telephony.CellInfoLte;
import android.telephony.CellInfoNr;
import android.telephony.CellSignalStrengthLte;
import android.telephony.CellSignalStrengthNr;
import android.telephony.ServiceState;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import com.punuo.sys.net.delay.BadNetworkCase;
import com.punuo.sys.net.delay.DxNetworkUtil;
import com.punuo.sys.net.push.Constant;
import com.punuo.sys.net.push.PushRequest;
import com.punuo.sys.sdk.PnApplication;
import com.punuo.sys.sdk.activity.BaseActivity;
import com.punuo.sys.sdk.httplib.HttpManager;
import com.punuo.sys.sdk.httplib.RequestListener;
import com.punuo.sys.sdk.model.BaseModel;
import com.punuo.sys.sdk.util.ToastUtils;
import com.punuo.sys.net.push.ProcessTasks;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import permissions.dispatcher.NeedsPermission;
import permissions.dispatcher.OnPermissionDenied;
import permissions.dispatcher.RuntimePermissions;

@RuntimePermissions
public class MainActivity extends BaseActivity{
    private TelephonyManager telephonyManager;
    private List<CellInfo> cellInfoList;
    private TextView displayIdentity;
    private TextView displaySignal;
    private TextView netType;
    private static final int SIGN_GET = 0x0001;


    /**
     * 上传数据用
     */
    private static final String TAG = "kuiya";
    private static int  timeSum;
    public static HashMap<Integer,Object> infoMap = new HashMap<>(18);


    private static long lastTotalRxBytes = 0;
    private static long lastTotalTxBytes = 0;
    private static long lastTime = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ProcessTasks.commonLaunchTasks(PnApplication.getInstance());
        telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        displaySignal = findViewById(R.id.display_sign);
        displayIdentity = findViewById(R.id.display_identity);
        netType = findViewById(R.id.netType);

        mBaseHandler.sendEmptyMessage(SIGN_GET);
        MainActivityPermissionsDispatcher.is5GConnectedWithCheck(this);
    }



    @NeedsPermission(Manifest.permission.READ_PHONE_STATE)
    void is5GConnected() {
        if (Build.VERSION.SDK_INT >= 29) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                return;
            }
            ServiceState serviceState = telephonyManager.getServiceState();
            try {
                Method hwOwnMethod = ServiceState.class.getMethod("getHwNetworkType");
                hwOwnMethod.setAccessible(true);
                int result = (int) hwOwnMethod.invoke(serviceState);
                Log.i("han.chen", "值为：" + result);
                if (result == 20) {
                    netType.setText("5g网络");
                    Log.i("han.chen", "5g网络");
                } else {
                    netType.setText("非5g网络");
                    Log.i("han.chen", "非5g网络");
                }
            } catch (Exception e) {
                Log.i("han.chen", e.toString());
            }
        }
    }

    @NeedsPermission({Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION})
    void getAllCellInfo() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        cellInfoList = telephonyManager.getAllCellInfo();
        for (int i = 0; i < cellInfoList.size(); i++) {
            CellInfo cellInfo = cellInfoList.get(i);
            if (cellInfo instanceof CellInfoNr) {
                CellInfoNr cellInfoNr = (CellInfoNr) cellInfo;
                CellIdentityNr cellIdentityNr = (CellIdentityNr) cellInfoNr.getCellIdentity();
                StringBuilder identity = new StringBuilder();
                identity.append("基站信息: \n")
                        .append("基站编号 CID: ")
                        .append(cellIdentityNr.getNci())
                        .append("\n")
                        .append("Physical Cell Id: ")
                        .append(cellIdentityNr.getPci())
                        .append("\n")
                        .append("Tracking Area Code: ")
                        .append(cellIdentityNr.getTac())
                        .append("\n")
                        .append("New Radio Absolute Radio Frequency Channel Number: ")
                        .append(cellIdentityNr.getNrarfcn())
                        .append("\n")
                        .append("移动国家代码 MCC: ")
                        .append(cellIdentityNr.getMccString())
                        .append("\n")
                        .append("移动网络号码 MNC: ")
                        .append(cellIdentityNr.getMncString())
                        .append("\n");
                displayIdentity.setText(identity.toString());

                //TODO 添加数据到集合(注意需要按照顺序添加)
                infoMap.put(0,4915882);
                infoMap.put(1,"NSA_H_杭州西湖留下BBU9");
                infoMap.put(2,cellIdentityNr.getPci());

                CellSignalStrengthNr cellSignalStrengthNr = (CellSignalStrengthNr) cellInfoNr.getCellSignalStrength();
                StringBuilder signal = new StringBuilder();
                signal.append("信号信息：\n")
                        .append("asuLevel: ")
                        .append(cellSignalStrengthNr.getAsuLevel())
                        .append("\n")
                        .append("dbm: ")
                        .append(cellSignalStrengthNr.getDbm())
                        .append("\n")
                        .append("level: ")
                        .append(cellSignalStrengthNr.getLevel())
                        .append("\n")
                        .append("csiRsrp: ")
                        .append(cellSignalStrengthNr.getCsiRsrp())
                        .append("\n")
                        .append("csiRsrq: ")
                        .append(cellSignalStrengthNr.getCsiRsrq())
                        .append("\n")
                        .append("csiSinr: ")
                        .append(cellSignalStrengthNr.getCsiSinr())
                        .append("\n")
                        .append("ssRsrp: ")
                        .append(cellSignalStrengthNr.getSsRsrp())
                        .append("\n")
                        .append("ssRsrq: ")
                        .append(cellSignalStrengthNr.getSsRsrq())
                        .append("\n")
                        .append("ssRinr: ")
                        .append(cellSignalStrengthNr.getSsSinr())
                        .append("\n");
                displaySignal.setText(signal.toString());


//                TODO 添加数据到集合(注意需要按照顺序添加)
                infoMap.put(3,cellSignalStrengthNr.getSsRsrp());
                infoMap.put(4,cellSignalStrengthNr.getSsRsrp());
                infoMap.put(5,cellSignalStrengthNr.getSsSinr());
                infoMap.put(6,cellSignalStrengthNr.getSsSinr());
                infoMap.put(11,-1);
                infoMap.put(12,-1);
                infoMap.put(13,-1);

            }
        }
    }

    @OnPermissionDenied({Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.READ_PHONE_STATE, Manifest.permission.ACCESS_FINE_LOCATION})
    void onPermissionError() {
        ToastUtils.showToast("权限获取失败");
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        MainActivityPermissionsDispatcher.onRequestPermissionsResult(this, requestCode, grantResults);
    }

    @Override
    public void handleMessage(Message msg) {
        super.handleMessage(msg);
        MainActivityPermissionsDispatcher.getAllCellInfoWithCheck(this);
        getOtherData();

        for (Map.Entry<Integer, Object> entry : infoMap.entrySet()) {
            Log.i(TAG, "key= " + entry.getKey() + " and value= " + entry.getValue());
        }

        if(infoMap.size()!=18){
            Log.i(TAG, "网络问题，获取数据不全，无法上传");
        }else{
            pushInfo();
        }

        mBaseHandler.sendEmptyMessageDelayed(SIGN_GET, 10* 1000);
    }

    /**
     * 上传数据到后台
     */
    private PushRequest mPushRequest;
    public void pushInfo(){
        if(mPushRequest!=null&&!mPushRequest.isFinish()){
            return;
        }
        mPushRequest = new PushRequest();
        mPushRequest.addUrlParam("gNBID",infoMap.get(0));
        mPushRequest.addUrlParam("NAME",infoMap.get(1));
        mPushRequest.addUrlParam("PCI",infoMap.get(2));
        mPushRequest.addUrlParam("RSRP_UL",infoMap.get(3));
        mPushRequest.addUrlParam("RSRP_DL",infoMap.get(4));
        mPushRequest.addUrlParam("SINR_UL",infoMap.get(5));
        mPushRequest.addUrlParam("SINR_DL",infoMap.get(6));
        mPushRequest.addUrlParam("DELAY_REQUEST",infoMap.get(7));
        mPushRequest.addUrlParam("DELAY_SUCCESS",infoMap.get(8));
        mPushRequest.addUrlParam("DELAY_FAIL",infoMap.get(9));
        mPushRequest.addUrlParam("DELAY",infoMap.get(10));
        mPushRequest.addUrlParam("NSA_REQUEST",infoMap.get(11));
        mPushRequest.addUrlParam("NSA_SUCCESS",infoMap.get(12));
        mPushRequest.addUrlParam("NSA_FAIL",infoMap.get(13));
        mPushRequest.addUrlParam("LONGITUDE",infoMap.get(14));
        mPushRequest.addUrlParam("LATITUDE",infoMap.get(15));
        mPushRequest.addUrlParam("THROUGHPUT_UL",infoMap.get(16));
        mPushRequest.addUrlParam("THROUGHPUT_DL",infoMap.get(17));
        mPushRequest.setRequestListener(new RequestListener<BaseModel>() {
            @Override
            public void onComplete() {

            }

            @Override
            public void onSuccess(BaseModel result) {
                if(result==null){
                    return;
                }
                if(result.success){
                    Log.i(TAG, "上传5G数据完成");
                    ToastUtils.showToast(result.message);
                    infoMap.clear();
                }
            }

            @Override
            public void onError(Exception e) {
                Log.i(TAG, e.getMessage());
            }
        });
        HttpManager.addRequest(mPushRequest);
    }


    /**
     * 获取到延时
     */
    private void getOtherData(){
        new Thread(new Runnable() {
            @Override
            public void run() {
                //注意不能在子线程直接toast
                Looper.prepare();
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                judgeTheConnect("www.baidu.com");
                getNetSpeed();
                getLocation();
                Looper.loop();
            }
        }).start();
    }
    /**
     * ping指定ip 获取时延
     * @param ip
     */
    private void judgeTheConnect(String ip){
        String result="";
        Process p;
        float delay;
        try{
            if(ip!=null){
                //ping 3次，超时时间为10s
                p = Runtime.getRuntime().exec("ping -c 3 -w 10 "+ip);
//                p = Runtime.getRuntime().exec("ifconfig");
                int status = p.waitFor();
                InputStream input = p.getInputStream();
                BufferedReader in = new BufferedReader(new InputStreamReader(input));
                StringBuffer buffer = new StringBuffer();
                String line;
                while((line=in.readLine())!=null){
                    buffer.append(line);
                    String time;
                    if((time=getTime(line))!=null){
                        Log.i(TAG, time);
                        timeSum+=Float.parseFloat(time.substring(0,4));
                    }
                }
                Log.i("kuiya", "反馈结果为:"+"\t"+buffer.toString());
                if(status==0){
                    //代表成功
                    infoMap.put(7,3);
                    infoMap.put(8,3);
                    infoMap.put(9,0);
                    delay =(float) timeSum/3;
                    infoMap.put(10,delay);
                    //将静态变量还原，供下次使用
                    timeSum=0;
                    Log.i(TAG, "成功");
                }else{
                    infoMap.put(7,3);
                    infoMap.put(8,0);
                    infoMap.put(9,3);
                    infoMap.put(10,-1);//没有成功
                    Log.i(TAG, "失败");
                }
            }else{
                ToastUtils.showToast("IP为空");
            }
        }catch (Exception e){
            Log.i(TAG, e.getMessage());
        }
    }



    private  String getTime(String line) {
        String[] lines = line.split("\n");
        String time = null;
        for (String l : lines) {
            if (!l.contains("time="))
                continue;
            int index = l.indexOf("time=");
            time = l.substring(index + "time=".length());
        }
        return time;
    }

    /**
     * 获取到ip、mask、gateWay；
     */
    private String getIpInfo() {
        String[] PERMISSIONS = new String[] {
                Manifest.permission.ACCESS_WIFI_STATE,
                Manifest.permission.ACCESS_NETWORK_STATE,
                Manifest.permission.INTERNET,
        };

        List<String> notGrantPerms = new ArrayList<>();

        for (String permission : PERMISSIONS) {
            int grant = ActivityCompat.checkSelfPermission(this, permission);
            if (grant != PackageManager.PERMISSION_GRANTED) {
                notGrantPerms.add(permission);
            }
        }
        String[] requestPerms = new String[notGrantPerms.size()];
        for (int i=0; i<notGrantPerms.size(); i++) {
            requestPerms[i] = notGrantPerms.get(i);
        }
        if (requestPerms.length > 0) {
            ActivityCompat.requestPermissions(this, requestPerms, 1);
        }
        BadNetworkCase.showNetworkInfo(this);
        DxNetworkUtil.getIfconfig();
        return  Constant.serverAddress;
    }

    /**
     * 获取下行
     */
    public static long getTotalRxBytes(){
        //转换成kB
        return TrafficStats.getTotalRxBytes()==TrafficStats.UNSUPPORTED ? 0:(TrafficStats.getTotalRxBytes()/1024);
    }
    /**
     * 获取上行
     */
    public static long getTotalTxBytes(){
        return TrafficStats.getTotalTxBytes()==TrafficStats.UNSUPPORTED ? 0:(TrafficStats.getTotalTxBytes()/1024);
    }

    public static String getNetSpeed(){
        long nowTotalRxBytes = getTotalRxBytes();
        long nowTotalTxBytes = getTotalTxBytes();
        long nowTime = System.currentTimeMillis();

        long speedRx = ((nowTotalRxBytes-lastTotalRxBytes)*1000/(nowTime-lastTime));
        long speedTx = ((nowTotalTxBytes-lastTotalTxBytes)*1000/(nowTime-lastTime));
        lastTime = nowTime;
        lastTotalRxBytes = nowTotalRxBytes;
        lastTotalTxBytes = nowTotalTxBytes;

        Log.i(TAG, "下行："+(speedRx+"KB/s")+"上行："+(speedTx+"KB/s"));
        infoMap.put(16,speedTx);
        infoMap.put(17,speedRx);
        return speedRx+"KB/s";
    }

    /**
     * 获取经纬度
     */
    public void getLocation(){
        LocationManager locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);

        //添加权限检查
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
        //设置每一秒获取一次location信息
        locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,      //GPS定位提供者
                1000,       //更新数据时间为1秒
                1,      //位置间隔为1米
                //位置监听器
                new LocationListener() {  //GPS定位信息发生改变时触发，用于更新位置信息

                    @Override
                    public void onLocationChanged(Location location) {
                        //GPS信息发生改变时，更新位置
                        locationUpdates(location);
                    }

                    @Override
                    //位置状态发生改变时触发
                    public void onStatusChanged(String provider, int status, Bundle extras) {
                    }

                    @Override
                    //定位提供者启动时触发
                    public void onProviderEnabled(String provider) {
                    }

                    @Override
                    //定位提供者关闭时触发
                    public void onProviderDisabled(String provider) {
                    }
                });
        //从GPS获取最新的定位信息
        Location location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        locationUpdates(location);    //将最新的定位信息传递给创建的locationUpdates()方法中
    }

    public void locationUpdates(Location location) {  //获取指定的查询信息
        //如果location不为空时
        if (location != null) {
            StringBuilder stringBuilder = new StringBuilder();        //使用StringBuilder保存数据
            //获取经度、纬度、等属性值
            DecimalFormat decimalFormat = new DecimalFormat("#.00");
            String longitude = decimalFormat.format(location.getLongitude());
            String latitude  = decimalFormat.format(location.getLatitude());
            stringBuilder.append("您的位置信息：\n");
            stringBuilder.append("经度：");
            stringBuilder.append(longitude);
            stringBuilder.append("\n纬度：");
            stringBuilder.append(latitude);
            Log.i(TAG, "locationUpdates: "+stringBuilder.toString());
            infoMap.put(14,longitude);
            infoMap.put(15,latitude);
        } else {
            //否则输出空信息
            Log.i(TAG, "没有获取到GPS信息");
        }
    }
}
