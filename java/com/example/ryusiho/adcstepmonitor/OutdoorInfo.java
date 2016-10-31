package com.example.ryusiho.adcstepmonitor;



public class OutdoorInfo {

    public String placeName;  //장소명
    public double lon; //경도
    public double lat; //위도
    public int radius; //반경

    /* Constructor */
    public OutdoorInfo(String placeName, double lat, double lon, int radius){
        this.placeName = placeName;
        this.lon = lon;
        this.lat = lat;
        this.radius = radius;
    }
}
