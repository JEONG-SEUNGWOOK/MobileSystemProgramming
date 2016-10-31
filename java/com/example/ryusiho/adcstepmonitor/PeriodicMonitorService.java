package com.example.ryusiho.adcstepmonitor;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.GpsSatellite;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;
import android.widget.Toast;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;


public class PeriodicMonitorService extends Service {

    // 실외정보 리스트
    ArrayList<OutdoorInfo> outdoorInfos = new ArrayList<>();
    // 실내정보 리스트
    ArrayList<IndoorInfo> indoorInfos = new ArrayList<>();

    private static final String LOGTAG = "ADC_Step_Monitor";
    //알람 매니저
    AlarmManager am;
    PendingIntent pendingIntent;

    //알람에 따른 wakelock
    private PowerManager.WakeLock wakeLock;
    //카운트 다운 타이머
    private CountDownTimer timer;

    //스텝모니터
    private StepMonitor accelMonitor;
    /* 주기 */
    private long period = 10000;
    private static final long activeTime = 1000;
    private static final long periodForMoving = 5000;
    private static final long periodIncrement = 5000;
    private static final long periodMax = 30000;

    /* 와이파이 매니저 */
    WifiManager wifiManager;  //와이파이 매니저 객체
    List<ScanResult> scanList;  //와이파이 스캔 결과
    private LocationManager mLocationManager = null;
    private final int MIN_TIME_UPDATES = 5000; // milliseconds 5초
    private final int MIN_DISTANCE_CHANGE_FOR_UPDATES = 5; // m
    private boolean isOutdoor;  //실내외 판단 변수
    //GPS 정보 경도, 위도값
    private double lon = 0.0; //경도
    private double lat = 0.0; //위도
    public double dis;  //거리


    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        //Log.d(LOGTAG, "onCreate");
        //트래킹할 장소 정보들 등록
        setIndoorInfos();  //실내 장소 정보 등록
        setOutdoorInfos();  //실외 장소 정보 등록

        //위치 관리자 객체 얻기
        mLocationManager = (LocationManager)getSystemService(Context.LOCATION_SERVICE);
        //와이파이 관리자 객체 얻기
        wifiManager = (WifiManager)getSystemService(WIFI_SERVICE);
        if(wifiManager.isWifiEnabled() == false)
            wifiManager.setWifiEnabled(true);

        //와이파이 리시버 등록
        IntentFilter filter = new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        registerReceiver(mReceiver, filter);

        // AlarmManager 객체 얻기
        am = (AlarmManager)getSystemService(ALARM_SERVICE);
        // Alarm 발생 시 전송되는 broadcast를 수신할 receiver 등록
        IntentFilter intentFilter = new IntentFilter("kr.ac.koreatech.msp.alarm");
        registerReceiver(AlarmReceiver, intentFilter);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // intent: startService() 호출 시 넘기는 intent 객체
        // flags: service start 요청에 대한 부가 정보. 0, START_FLAG_REDELIVERY, START_FLAG_RETRY
        // startId: start 요청을 나타내는 unique integer id

        Log.d(LOGTAG, "onStartCommand");
        Toast.makeText(this, "Activity Monitor 시작", Toast.LENGTH_SHORT).show();

        // Alarm이 발생할 시간이 되었을 때, 안드로이드 시스템에 전송을 요청할 broadcast를 지정
        Intent in = new Intent("kr.ac.koreatech.msp.alarm");
        pendingIntent = PendingIntent.getBroadcast(getApplicationContext(), 0, in, 0);

        // Alarm이 발생할 시간 및 alarm 발생시 이용할 pending intent 설정
        // 설정한 시간 (5000-> 5초, 10000->10초) 후 alarm 발생
        am.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + period, pendingIntent);

        //실내외 판단 시작
        INnOutDetermination();

        return super.onStartCommand(intent, flags, startId);
    }

    public void onDestroy() {
        Log.d(LOGTAG, "onDestroy");
        Toast.makeText(this, "Activity Monitor 중지", Toast.LENGTH_SHORT).show();

        try {
            // Alarm 발생 시 전송되는 broadcast 수신 receiver를 해제
            //리시버들 해제
            unregisterReceiver(AlarmReceiver);
            unregisterReceiver(mReceiver);
            Log.d(LOGTAG, "Cancel the location update request");
            //위치 관리자가 등록되어 있다면
            if(mLocationManager != null) {
                try {
                    //위치 정보 업데이트 요청 해제 및 리스너 해제
                    mLocationManager.removeNmeaListener(m_nmea_listener);
                    mLocationManager.removeGpsStatusListener(gpsListener);
                    mLocationManager.removeUpdates(locationListener);
                } catch(SecurityException se) {
                    se.printStackTrace();
                    Log.e(LOGTAG, "PERMISSION_NOT_GRANTED");
                }
            }
        } catch(IllegalArgumentException ex) {
            ex.printStackTrace();
        }
        // AlarmManager에 등록한 alarm 취소
        am.cancel(pendingIntent);

        // release all the resources you use
        if(timer != null)
            timer.cancel();
        if(wakeLock != null && wakeLock.isHeld())
            wakeLock.release();

        //등록한 장소 정보들 삭제
        outdoorInfos.clear();
        indoorInfos.clear();
    }

    int gps_num = 0;
    GpsStatus.NmeaListener m_nmea_listener = new GpsStatus.NmeaListener() {
        public void onNmeaReceived(long timestamp, String nmea) {
            String compare = "00";
            int numSatlite;   //위성 수
            String str_temp[] = nmea.split(",");
            if (str_temp[0].equals("$GPGGA")) {
                //위성의 개수 계산
                numSatlite = str_temp[7].compareTo(compare);
                Log.e("NUmber of statlatl : ", "" + numSatlite);
                if(gps_num + numSatlite > 17) {
                    Log.w("Value1",""+ (gps_num + numSatlite));
                    isOutdoor = true; //실외
                }
                else if((gps_num + numSatlite < 3) && (gps_num + numSatlite >= -2)) {
                    Log.w("Value2",""+ (gps_num + numSatlite));
                    isOutdoor = false; //실내

                }
            }
        }
    };

    GpsStatus.Listener gpsListener = new GpsStatus.Listener(){
        @Override
        public void onGpsStatusChanged(int event) {
            GpsStatus status = mLocationManager.getGpsStatus(null);
            Iterable sats = status.getSatellites();
            Iterator satI = sats.iterator();
            int count = 0;
            while (satI.hasNext()) {
                GpsSatellite gpssatellite = (GpsSatellite) satI.next();
                if (gpssatellite.usedInFix()) {
                    count++;  //위성의 개수 계산
                }
            }
            if(count > 12) isOutdoor = true;  //실외
            //else isOutdoor = false;
            Log.e("GPS Status", "" + count);
            gps_num = count;
        }
    };

    //WiFi 스캔 이후 스캔 결과를 받으면
    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION))
                checkWiFi();  //스캔 결과와 등록된 정보와 비교
        }
    };

    // Alarm 시간이 되었을 때 안드로이드 시스템이 전송해주는 broadcast를 받을 receiver 정의
    // 그리고 다시 동일 시간 후 alarm이 발생하도록 설정한다.
    private BroadcastReceiver AlarmReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {

            if(intent.getAction().equals("kr.ac.koreatech.msp.alarm")) {
                Log.d(LOGTAG, "Alarm fired!!!!");
                //-----------------
                // Alarm receiver에서는 장시간에 걸친 연산을 수행하지 않도록 한다
                // Alarm을 발생할 때 안드로이드 시스템에서 wakelock을 잡기 때문에 CPU를 사용할 수 있지만
                // 그 시간은 제한적이기 때문에 애플리케이션에서 필요하면 wakelock을 잡아서 연산을 수행해야 함
                //-----------------

                PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
                if(!MainActivity.wakeLock.isHeld())
                    MainActivity.wakeLock.acquire();

                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "DutyCyclingStepMonitor_Wakelock");
                // ACQUIRE a wakelock here to collect and process accelerometer data
                wakeLock.acquire();
                if(!MainActivity.wakeLock.isHeld())
                    MainActivity.wakeLock.acquire();
                accelMonitor = new StepMonitor(context);
                accelMonitor.onStart();

                timer = new CountDownTimer(activeTime, 1500) {

                    @Override
                    public void onTick(long millisUntilFinished) {
                    }

                    @Override
                    public void onFinish() {
                        Log.d(LOGTAG, "1-second accel data collected!!");
                        // stop the accel data update
                        accelMonitor.onStop();

                        // 움직임 여부에 따라 다음 alarm 설정
                        boolean moving = accelMonitor.isMoving();



                        if(isOutdoor == false){  //실외의 경우
                            //WiFi 스캔을 해서 등록한 실내 장소의 정보와 비교
                            wifiManager.startScan(); //와이파이 스캔 시작
                        } else if (isOutdoor == true){  //실내의 경우
                            checkGPSInfo();   //위치 정보 비교 시작
                        }



                            setNextAlarm(moving); //다음 알람 설정

                            // When you finish your job, RELEASE the wakelock
                            wakeLock.release();
//                        if( MainActivity.wakeLock.isHeld())
//                            MainActivity.wakeLock.release();

                    }
                };
                timer.start();
            }
        }
    };



    private void INnOutDetermination(){
        try {
            //위치 정보 업데이트 요청 및 리스너들 등록
            mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, MIN_TIME_UPDATES, MIN_DISTANCE_CHANGE_FOR_UPDATES, locationListener);
            mLocationManager.addNmeaListener(m_nmea_listener);
            mLocationManager.addGpsStatusListener(gpsListener);
        }catch (SecurityException se) {
            se.printStackTrace();
            Log.e(LOGTAG, "PERMISSION_NOT_GRANTED");
        }
    }

    private void checkGPSInfo(){
        double getLon;
        double getLat;
        int getRadius;
        String np="";
        String placeName;
        boolean inPlace;
        for(int i = 0; i < outdoorInfos.size(); i++){
            getLon = outdoorInfos.get(i).lon;
            getLat = outdoorInfos.get(i).lat;
            placeName = outdoorInfos.get(i).placeName;
            getRadius = outdoorInfos.get(i).radius;
            inPlace = caculateDistance(getLat, getLon, getRadius);
            //등록된 장소에 대한 정보 즉 거리(반경)와 얻어온 결과와 비교해서
            //등록된 반경 거리 안에 있으면 등록된 장소명 표시 아니면 실외로 표시
            if(isOutdoor == true) {
                if (inPlace == false) {
                    np = "Outdoor";
                } else if (inPlace == true) {
                    np = placeName;
                }
            }else{
                np = null;
            }
        }
        //구분한 장소명 브로드캐스트로 전송
        Intent intent = new Intent("kr.ac.koreatech.msp.place");
        intent.putExtra("place", np);    //장소명
        sendBroadcast(intent);
    }

    //좌표 정보를 기반으로한 거리 계산
    private boolean caculateDistance(double getLat, double getLon, int radius){
        boolean isInPlace = false;
        double disLat = 69.1 * (lat - getLat);
        double disLon = 69.1 * (lon - getLon) * Math.cos(getLat / 57.3);
        double distance = Math.sqrt((disLat * disLat) + (disLon * disLon));
        distance = distance / 0.00062137; //miles to meters
        dis = distance;
        Log.d(LOGTAG, ""+ String.valueOf(distance));
        Log.e("Distance", ""+ distance);
        //거리값이 등록된 반경내에 있다면 등록한 장소에 있다고 판단
        if(distance <= radius) {
            isInPlace = true;
            return isInPlace;
        }
        else return isInPlace;
    }


    LocationListener locationListener = new LocationListener() {
        public void onLocationChanged(Location location) {
            Log.d(LOGTAG, " Time : " + getCurrentTime() + " Longitude : " + location.getLongitude()
                    + " Latitude : " + location.getLatitude() + " Altitude: " + location.getAltitude()
                    + " Accuracy : " + location.getAccuracy());
            //위치 정보가 바뀌면 위도와 경도값을 재설정하고 장소 구분
            lon = location.getLongitude();
            lat = location.getLatitude();
            checkGPSInfo();
            //이 함수가 실행된다는 의미는 사용자가 실외에 있다고 판단
            isOutdoor = true; //실외로 판정
        }

        public void onStatusChanged(String s, int status, Bundle bundle) {
            Log.d(LOGTAG, "GPS status changed.");
            //Toast.makeText(getApplicationContext(), "GPS status changed." + s, Toast.LENGTH_SHORT).show();

        }
        public void onProviderEnabled(String s) {
            Log.d(LOGTAG, "GPS onProviderEnabled: " + s);
        }
        public void onProviderDisabled(String s) {
            Log.d(LOGTAG, "GPS onProviderDisabled: " + s);
            //Toast.makeText(getApplicationContext(), "GPS is off, please turn on!", Toast.LENGTH_LONG).show();
        }
    };

    private void checkWiFi() {
        // 등록된 top1 AP가 스캔 결과에 있으며, 그 RSSI가 top1 rssi 값보다 10이하로 작을 때
        // 등록된 장소 근처에 있는 것으로 판단
        // RSSI 크기가 가장 큰 것의 BSSID를 얻음
        //여기서 등록된 장소에 있다면 location tracker 동작 및 기록
        scanList = wifiManager.getScanResults();

        String scanBSSID;
        String scanSSID;
        int scanRSSI = 0;
        String scanAPId = "";

        String nameOfPlace;
        String BSSIDinfo;
        String SSIDinfo;
        String APIdinfo;
        int RSSIinfo;
        String np = "";

        String resultname = "";
        boolean isThere = false;


        // 스캔 결과 리스트에서 등록된 실내 정보와 비교를 해서
        // AP명과 물리주소 그리고 신호의 세기를 비교해서 일치하는 것이 있다면(실내 or 실내 장소명)
        // 해당 장소에 대한 장소명을 브로드캐스트로 전송
        for(int i = 0; i < scanList.size(); i++) {
            ScanResult result = scanList.get(i);
            scanRSSI = result.level;
            scanBSSID = result.BSSID;
            scanSSID = result.SSID;
            scanAPId = scanSSID + scanBSSID;
            for(int j = 0; j < indoorInfos.size(); j++) {
                nameOfPlace = indoorInfos.get(j).placeName;
                BSSIDinfo = indoorInfos.get(j).BSSID;
                SSIDinfo = indoorInfos.get(j).SSID;
                RSSIinfo = indoorInfos.get(j).rssi;
                APIdinfo = SSIDinfo + BSSIDinfo;
                if(APIdinfo.equals(scanAPId) && scanRSSI >= RSSIinfo - 10){
                    isThere = true;
                    np = nameOfPlace;
                }
            }
        }
        if(isThere) resultname = np;
        else resultname = "Indoor";

        Log.d(LOGTAG, "" + scanRSSI);
        Intent intent = new Intent("kr.ac.koreatech.msp.place");
        intent.putExtra("place", resultname);    //장소명
        sendBroadcast(intent);

    }

    private void setNextAlarm(boolean moving) {

        // 움직임이면 5초 period로 등록
        // 움직임이 아니면 5초 증가, max 30초로 제한
        if(moving) {
            Log.d(LOGTAG, "MOVING!!");
            period = periodForMoving;
        } else {
            Log.d(LOGTAG, "NOT MOVING!!");
            period = period + periodIncrement;
            if(period >= periodMax) {
                period = periodMax;
            }
        }
        Log.d(LOGTAG, "Next alarm: " + period);

        // 다음 alarm 등록
        Intent in = new Intent("kr.ac.koreatech.msp.alarm");
        pendingIntent = PendingIntent.getBroadcast(getApplicationContext(), 0, in, 0);
        am.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + period - activeTime, pendingIntent);

        //*****
        // 화면에 정보 표시를 위해 activity의 broadcast receiver가 받을 수 있도록 broadcast 전송
        Intent intent = new Intent("kr.ac.koreatech.msp.adcstepmonitor");
        intent.putExtra("moving", moving);  //이동 여부

        //intent.putExtra("dis", dis);        //거리값
        // broadcast 전송
        sendBroadcast(intent);
    }

    public String getCurrentTime() {
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy.MM.dd HH:mm:ss", Locale.KOREA);
        Date currentTime = new Date();
        String dTime = formatter.format(currentTime);
        return dTime;
    }

    public void setOutdoorInfos(){
        //실외 장소 정보 등록
        outdoorInfos.add(new OutdoorInfo("Playground", 36.762581, 127.284527, 80));
        outdoorInfos.add(new OutdoorInfo("Bench", 36.764215, 127.282173, 50));
        //outdoorInfos.add(new OutdoorInfo("ChamBit", 36.760906, 127.280814, 40));
        //outdoorInfos.add(new OutdoorInfo("Student's Hall", 36.763139, 127.282486, 50));
        //outdoorInfos.add(new OutdoorInfo("Wellfare", 36.762984, 127.281239, 40));
    }

    public void setIndoorInfos(){
        //실내 장소 정보 등록
        //indoorInfos.add(new IndoorInfo("Muhamad", "00:26:66:e0:e3:de", "Mulab", -55));
        indoorInfos.add(new IndoorInfo("Dasan_1F", "20:3a:07:9e:a6:c1", "KUTAP_N", -70));
        indoorInfos.add(new IndoorInfo("A312", "90:9f:33:cd:28:62", "iptime", -55));
    }

}