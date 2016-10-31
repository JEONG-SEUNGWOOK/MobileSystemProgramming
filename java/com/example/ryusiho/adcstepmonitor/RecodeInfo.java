package com.example.ryusiho.adcstepmonitor;

/**
 * Tracking정보를 담고있는 리스트
 */
public class RecodeInfo {
    private String date, startTime, endTime, location; //운동날짜, 활동 시작시간, 종료시간, 위치장소
    private int steps, movingTime; //걸음수, 활동 시간
    private boolean isMoving; //움직임 여부

    public RecodeInfo() {
    }

    /* Constructor */
    public RecodeInfo(String date, String startTime, String endTime, String location, int steps, int movingTime, boolean isMoving) {
        this.date = date;
        this.startTime = startTime;
        this.endTime = endTime;
        this.location = location;
        this.steps = steps;
        this.movingTime = movingTime;
        this.isMoving = isMoving;
    }

    /* 각 정보에 대한 접근자 */
    public String getStartTime() {
        return startTime;
    }

    public String getEndTime() {
        return endTime;
    }

    public String getLocation() {
        return location;
    }

    public int getSteps() {
        return steps;
    }

    public boolean getIsMoving() {
        return isMoving;
    }

    public int getMovingTime() {return movingTime;}

    public String getDate() {return date;}
}
