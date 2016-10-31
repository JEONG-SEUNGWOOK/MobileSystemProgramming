package com.example.ryusiho.adcstepmonitor;


public class IndoorInfo {

    public String placeName; //장소명
    public String BSSID; //AP의 Mac 주소
    public String SSID; //AP의 이름
    public int rssi;     //rssi 신호세기

    /* Constructor */
    public IndoorInfo(String placeName, String BSSID, String SSID, int rssi){
        this.placeName = placeName;
        this.BSSID = BSSID;
        this.SSID = SSID;
        this.rssi = rssi;
    }

}
