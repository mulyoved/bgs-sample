package com.red_folder.phonegap.plugin.backgroundservice.sample;

/**
 * Created by Muly on 10/12/2015.
 */
public class ActivityMessageEvent {
    public ActivityMessageEvent(String status, int confidence, long time) {
        this.status = status;
        this.confidence = confidence;
        this.time = time;
    }

    public String status;
    public int confidence;
    public long time;
}
