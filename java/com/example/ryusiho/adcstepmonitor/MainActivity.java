package com.example.ryusiho.adcstepmonitor;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.ActionBar;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.nfc.Tag;
import android.os.Build;
import android.os.CountDownTimer;
import android.os.PowerManager;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import com.melnykov.fab.FloatingActionButton;
import com.txusballesteros.widgets.FitChart;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends AppCompatActivity {

    private static final String LOGTAG = "HS_Location_Tracking";
    /* 레이아웃 뷰 */
    private TextView stepText;
    private TextView movingText;
    private TextView locationText;
    private TextView movingTimeText;
    private TextView todaysDate;

    private RecyclerView recyclerView;
    private FloatingActionButton fab;
    private List<RecodeInfo> movieList = new ArrayList<>();
    private RecodeAdapter mAdapter;

    //스텝수, 움직인 시간
    private int stepcount = 0;
    private int movingTime = 0;
    //활동 기록 리스트
    private RecodeInfo recode;

    //MainActivity wakeLock
    public static PowerManager.WakeLock wakeLock;

    /* 최초 움직임, 현재 움직임 */
    private int preMovingStatus = 0;
    private int curMovingStatus = 0;

    /* 최초 스텝수 , 현재 스텝수 */
    private int preSteps = 0;
    private int curSteps = 0;

    /* 최초 시간 현재 시간 */
    private String preTime;
    private String curTime;
    //현재장소
   private String place="실내";

    //현재 움직임
    private boolean moving = false;

    /* 움직임 상수 값 */
    private final int MOVING = 1;
    private final int NOT_MOVING = 2;

    /* 타이머 리셋 여부 */
    private boolean isResetTimer = false;
    /* 현재 모니터링 여부 */
    private boolean isMonitoring = false;

    /* 타이머 */
    Timer movingTimer = new Timer();
    TimerTask movingTimerTask = null;

    //스텝 모니터
    private BroadcastReceiver MyStepReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            /* Step Detector 인텐트 */
            if(intent.getAction().equals("kr.ac.koreatech.msp.adcstepmonitor")) {

                moving = intent.getBooleanExtra("moving", false);

                /* 타이머가 0일 때 즉, 상태가 바뀌거나 최초 상태일 때 */
                if(isResetTimer == false ){

                    preTime = getCurrentTime();
                    /* 움직임 판단 */
                    if(moving)
                        preMovingStatus = MOVING;
                    else
                        preMovingStatus = NOT_MOVING;

                    Log.d(LOGTAG,"preMovingStatus : " +preMovingStatus);
                    startTimerTask();
                }


                /* 상태가 바뀌고 1분 후 상태 검사 */
                if(preMovingStatus != 0 ){
                    if(moving)
                        curMovingStatus = MOVING;
                    else
                        curMovingStatus = NOT_MOVING;

                    Log.d(LOGTAG,"curMovingStatus : " +curMovingStatus);
                }

                //리스트 Adapter 갱신
                mAdapter.notifyDataSetChanged();

                /* 텍스트 레이아웃 초기화 */
                stepText.setText("스텝수 : " + stepcount);
                if(place != null)
                    locationText.setText("위치 : " + place);

                if(moving) {
                    movingText.setText("Moving");

                } else {
                    movingText.setText("NOT Moving");
                }

            }
            /* Step Counter 인텐트 */
            else if (intent.getAction().equals("kr.ac.koreatech.msp.stepcount")){

                stepcount += intent.getIntExtra("count", 0);
                Log.d(LOGTAG, "steps:" +stepcount);
            }
            /* 현재 장소 인텐트 */
            else if (intent.getAction().equals("kr.ac.koreatech.msp.place")){
                place = intent.getStringExtra("place");
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        //TextView 설정
        setTextFunc();
        //recyclerView initialize
        setRecyclerView();

        /* MainActivity에 대한 wakeLock 초기화 */
        PowerManager pm = (PowerManager)getSystemService(Context.POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Wakelock");
    }


    @Override
    protected void onResume() {
        super.onResume();

        /* Step Detector 대한 Receiver */
        IntentFilter monitorFilter = new IntentFilter("kr.ac.koreatech.msp.adcstepmonitor");
        registerReceiver(MyStepReceiver, monitorFilter);

        /* Step Counter 대한 Receiver */
        IntentFilter stepcountFilter = new IntentFilter("kr.ac.koreatech.msp.stepcount");
        registerReceiver(MyStepReceiver, stepcountFilter);

        /* Place에 대한 Receiver */
        IntentFilter placeFilter = new IntentFilter("kr.ac.koreatech.msp.place");
        registerReceiver(MyStepReceiver, placeFilter);
    }




    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(MyStepReceiver); //스텝리시버 해제

    }

    /* 위치에 대한 타이머 시작 */
    public void startTimerTask(){
        Log.d(LOGTAG, "Start TimerTask!");
        movingTimerTask = new TimerTask() {
            @Override
            public void run() {
                //최초 위치와 1분 후 위치가 같을 때
                if (preMovingStatus == curMovingStatus){
                    Log.d(LOGTAG,"preStatus == curStatus");
                    ++movingTime;
                    isResetTimer = true;
                }
                //최초위치와 1분 후 위치가 다를때 기록
                else{

                    stopTimerTask();
                }
                //활동시간 기록
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        movingTimeText.setText(movingTime+" 분");
                    }
                });
            }
        };
        //타이머 주기
        movingTimer.schedule(movingTimerTask, 1000, 60000);
    }

    //타이머 중지
    public void stopTimerTask(){
        //타이머가 실행중이라면
        if(movingTimerTask != null){
            curTime = getCurrentTime(); //현재 시간 기록
            //이전 움직임이 움직임 있을 때
            if(preMovingStatus == MOVING) {
                curSteps = stepcount; //현재 스텝 수 기록
                recode = new RecodeInfo(getCurrentDate(), preTime, curTime, place, curSteps - preSteps, movingTime, true);
            }
            //이전 움직임이 움직이지 않음 일 때
            else if (preMovingStatus == NOT_MOVING)
                recode = new RecodeInfo(getCurrentDate(), preTime, curTime, place, 0, movingTime, false);
            movieList.add(recode);

            preSteps = stepcount;
            movingTimerTask.cancel(); //타이머 중지
            movingTimerTask = null;   //타이머 null초기화
            movingTime = 0;  //이동시간 0으로 초기
            preMovingStatus = 0;
            curMovingStatus = 0;
            isResetTimer = false;

            if(wakeLock != null && wakeLock.isHeld())
                wakeLock.release();
        }
    }




    // Start/Stop 버튼을 눌렀을 때 호출되는 콜백 메소드
    // Activity monitoring을 수행하는 service 시작/종료
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public void onClick(View v) {
        if(v.getId() == R.id.fab) {
            if(fab.isClickable()){
                //Stop
                if(isMonitoring){
                    Log.d(LOGTAG,"Stop Monitoring!");
                    isMonitoring = false;
                    fab.setImageDrawable(getDrawable(R.drawable.play));
                    movingText.setText("Not Monitoring!");

                    stopService(new Intent(this, PeriodicMonitorService.class));
                    //총 움직인 시간
                    //총 스텝수
                    //현재 위치
                    movingText.setText("Not Monitoring");
                    locationText.setText("None");
                    stepText.setText(String.valueOf(0) +"step");
                    movingTimeText.setText("0 분");

                    stopTimerTask();
                    mAdapter.notifyDataSetChanged();
                    stepcount = 0;
                }
                //Start
                else{
                    Log.d(LOGTAG,"Start Monitoring!");
                    isMonitoring = true;
                    fab.setImageDrawable(getDrawable(R.drawable.stop));
                    movingText.setText("Monitoring!");

                    Intent intent = new Intent(this, PeriodicMonitorService.class);
                    startService(intent);
                }
            }
        }

    }

    /* 텍스트 레이아웃 초기화 */
    public void setTextFunc(){
        movingText = (TextView)findViewById(R.id.mainIsMoving);
        locationText = (TextView)findViewById(R.id.mainLocView);
        stepText = (TextView)findViewById(R.id.StepCount);
        movingTimeText = (TextView)findViewById(R.id.movingTime);
        stepText = (TextView)findViewById(R.id.StepCount);
        todaysDate = (TextView)findViewById(R.id.date);
        todaysDate.setText(getCurrentDate());
    }

    /* 리스트 레이아웃 초기화 */
    public void setRecyclerView(){
        mAdapter = new RecodeAdapter(movieList); //RecyclerView Adapter
        recyclerView = (RecyclerView) findViewById(R.id.list); //RecyclerView
        fab = (FloatingActionButton) findViewById(R.id.fab); //Floating Action Button
        fab.attachToRecyclerView(recyclerView); //Flaoting Action Button과 RecyclerView 연결

        recyclerView.setHasFixedSize(true); //리스트 크기 고정
        RecyclerView.LayoutManager mLayoutManager = new LinearLayoutManager(getApplicationContext()); //Layout Manger 초기화
        recyclerView.setLayoutManager(mLayoutManager); //RecyclerView와 LayoutManager 설정
        recyclerView.setItemAnimator(new DefaultItemAnimator()); //RecyclerView 클릭 에니메이션
        recyclerView.setAdapter(mAdapter); //RecyclerView와 Adapter 연결

    }

    /* 현재 시간 반환 함수 */
    public String getCurrentDate() {
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy년 MM월 dd일", Locale.KOREA);
        Date currentTime = new Date();
        String cdate = formatter.format(currentTime);
        return cdate;
    }

    /* 오늘 날짜 반환 함수 */
    public String getCurrentTime() {
        SimpleDateFormat formatter = new SimpleDateFormat("HH:mm", Locale.KOREA);
        Date currentTime = new Date();
        String ctime = formatter.format(currentTime);
        return ctime;
    }


}
