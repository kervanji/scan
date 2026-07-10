package com.murakib.attendance.model;

import java.time.LocalTime;

public class WorkShift {

    private long id;
    private String name;
    private String daysOfWeek;
    private LocalTime startTime;
    private int lateGraceMinutes;
    private LocalTime endTime;
    private int earlyCheckinMinutes;
    private boolean active;
    private int sortOrder;

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDaysOfWeek() {
        return daysOfWeek;
    }

    public void setDaysOfWeek(String daysOfWeek) {
        this.daysOfWeek = daysOfWeek;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalTime startTime) {
        this.startTime = startTime;
    }

    public int getLateGraceMinutes() {
        return lateGraceMinutes;
    }

    public void setLateGraceMinutes(int lateGraceMinutes) {
        this.lateGraceMinutes = lateGraceMinutes;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalTime endTime) {
        this.endTime = endTime;
    }

    public int getEarlyCheckinMinutes() {
        return earlyCheckinMinutes;
    }

    public void setEarlyCheckinMinutes(int earlyCheckinMinutes) {
        this.earlyCheckinMinutes = earlyCheckinMinutes;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }
}
