package com.example.ryusiho.adcstepmonitor;

import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;


public class StepMonitor implements SensorEventListener{

    private static final String LOGTAG = "ADC_Step_Monitor";

    private Context context;
    private SensorManager mSensorManager;
    private Sensor mLinear;
    private boolean isMoving; //움직임 구분
    // onStart() 호출 이후 onStop() 호출될 때까지 센서 데이터 업데이트 횟수를 저장하는 변수
    private int sensingCount;
    // 센서 데이터 업데이트 중 움직임으로 판단된 횟수를 저장하는 변수
    private int movementCount;

    // 움직임 여부를 판단하기 위한 3축 가속도 데이터의 RMS 값의 기준 문턱값
    private static final double RMS_THRESHOLD = 1.0;


    private double[] rmsArray;
    private int rmsCount;
    private double steps;
    private static final int NUMBER_OF_SAMPLES = 5;

    // 3축 가속도 데이터의 RMS 값의 1.5초간 평균값을 이용하여 걸음이 있었는지 판단하기 위한 기준 문턱값
    private static final double AVG_RMS_THRESHOLD = 1.5 ;

    // 평균 RMS가 4이상일 때는 STEPS가 아니라 판단
    private static final int IS_STEPS_RMS = 4 ;
    // 1초 간 걸음수를 2.5라 추정
    // 1초간 rms 평균값이 기준 문턱값을 넘었을 때, steps를 2.5 씩 증가
    private static final double NUMBER_OF_STEPS_PER_SEC = 2.5;

    //StepMonitor 생성자, Context는 AlarmReceiver에서 넘겨 받음
    public StepMonitor(Context context) {
        this.context = context;

        mSensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        mLinear = mSensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
    }

    public void onStart() {
        // SensorEventListener 등록
        if (mLinear != null) {
            Log.d(LOGTAG, "Register Accel Listener!");
            mSensorManager.registerListener(this, mLinear, SensorManager.SENSOR_DELAY_GAME);
        }
        // 변수 초기화
        isMoving = false;
        sensingCount = 0;
        movementCount = 0;

        rmsCount = 0;
        rmsArray = new double[NUMBER_OF_SAMPLES];
    }

    public void onStop() {
        // SensorEventListener 등록 해제
        if (mSensorManager != null) {
            Log.d(LOGTAG, "Unregister Accel Listener!");
            mSensorManager.unregisterListener(this);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    // 센서 데이터가 업데이트 되면 호출
    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_LINEAR_ACCELERATION) {

            // 센서 업데이트 횟수 증가
            sensingCount++;

            //***** sensor data collection *****//
            // event.values 배열의 사본을 만들어서 values 배열에 저장
            float[] values = event.values.clone();

            // movement detection
            detectMovement(values);

            // movement calculate
            computeStep(values);
        }
    }

    private void computeStep(float[] values){
        double avgRms = 0;
        // 현재 업데이트 된 accelerometer x, y, z 축 값의 Root Mean Square 값 계산
        double rms = Math.sqrt(values[0] * values[0] + values[1] * values[1] + values[2] * values[2]);
        //Log.d(LOGTAG, "rms: " + rms);

        if(rmsCount < NUMBER_OF_SAMPLES) {
            rmsArray[rmsCount] = rms;
            rmsCount++;
        } else if(rmsCount == NUMBER_OF_SAMPLES) {
            // 3. 1초간 rms 값이 모였으면 평균 rms 값을 계산
            double sum = 0;
            // 3-1. rms 값들의 합을 구함
            for(int i = 0; i < NUMBER_OF_SAMPLES; i++) {
                sum += rmsArray[i];
            }
            // 3-2. 평균 rms 계산
            avgRms = sum / NUMBER_OF_SAMPLES;
            Log.d(LOGTAG, "1sec avg rms: " + avgRms);

            // 4. rmsCount, rmsArray 초기화: 다시 1초간 rms sample을 모으기 위해
            rmsCount = 0;
            for(int i = 0; i < NUMBER_OF_SAMPLES; i++) {
                rmsArray[i] = 0;
            }

            // 5. 이번 업데이트로 계산된 rms를 배열 첫번째 원소로 저장하고 카운트 1증가
            rmsArray[0] = rms;
            rmsCount++;
        }

        /* avgRms가 1.5이하, 4이상일 때는 스텝이 아니라고 판단. */
        if(avgRms > AVG_RMS_THRESHOLD && avgRms < IS_STEPS_RMS) {
            // 1-1. step 수는 1초 걸음 시 step 수가 일정하다고 가정하고, 그 값을 더해 줌
            steps = NUMBER_OF_STEPS_PER_SEC;

            Intent intent = new Intent("kr.ac.koreatech.msp.stepcount");
            //intent.putExtra("rms", rms);
            int Isteps = (int) Math.round(steps);
            intent.putExtra("count", Isteps);
            Log.d(LOGTAG, "steps: " + Isteps);
            // broadcast 전송
            context.sendBroadcast(intent);
        }

    }

    private void detectMovement(float[] values) {
        // 현재 업데이트 된 accelerometer x, y, z 축 값의 Root Mean Square 값 계산
        double rms = Math.sqrt(values[0] * values[0] + values[1] * values[1] + values[2] * values[2]);
        Log.d(LOGTAG, "rms: " + rms);

        // 계산한 rms 값을 threshold 값과 비교하여 움직임이면 count 변수 증가
        if(rms > RMS_THRESHOLD) {
            movementCount++;
        }

    }

    // 일정 시간 동안 움직임 판단 횟수가 센서 업데이트 횟수의 50%를 넘으면 움직임으로 판단
    public boolean isMoving() {
        double ratio = (double)movementCount / (double)sensingCount;
        if(ratio >= 0.5) {
            isMoving = true;
        } else {
            isMoving = false;
        }
        return isMoving;
    }
}
