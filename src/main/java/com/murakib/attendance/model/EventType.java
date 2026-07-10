package com.murakib.attendance.model;

public enum EventType {
    CHECK_IN,
    CHECK_OUT;

    public static EventType fromString(String value) {
        return EventType.valueOf(value);
    }
}
