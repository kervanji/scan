package com.murakib.attendance.repository;

import com.murakib.attendance.db.DatabaseManager;
import com.murakib.attendance.model.AttendanceEvent;
import com.murakib.attendance.model.EventType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class AttendanceRepository {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private final DatabaseManager db;

    public AttendanceRepository(DatabaseManager db) {
        this.db = db;
    }

    public long insert(AttendanceEvent event) throws Exception {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("""
                 INSERT INTO attendance_events (employee_id, event_type, event_time, photo_path, telegram_sent, device_name, notes, created_by)
                 VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                 """, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, event.getEmployeeId());
            ps.setString(2, event.getEventType().name());
            ps.setString(3, event.getEventTime().format(FORMATTER));
            ps.setString(4, event.getPhotoPath());
            ps.setInt(5, event.isTelegramSent() ? 1 : 0);
            ps.setString(6, event.getDeviceName());
            ps.setString(7, event.getNotes());
            ps.setString(8, event.getCreatedBy());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    long id = keys.getLong(1);
                    event.setId(id);
                    return id;
                }
            }
        }
        throw new IllegalStateException("Failed to insert attendance event");
    }

    public void updateTelegramSent(long eventId, boolean sent) throws Exception {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("UPDATE attendance_events SET telegram_sent = ? WHERE id = ?")) {
            ps.setInt(1, sent ? 1 : 0);
            ps.setLong(2, eventId);
            ps.executeUpdate();
        }
    }

    public void updateEvent(AttendanceEvent event) throws Exception {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("""
                 UPDATE attendance_events SET event_type = ?, event_time = ?, notes = ?, created_by = ? WHERE id = ?
                 """)) {
            ps.setString(1, event.getEventType().name());
            ps.setString(2, event.getEventTime().format(FORMATTER));
            ps.setString(3, event.getNotes());
            ps.setString(4, event.getCreatedBy());
            ps.setLong(5, event.getId());
            ps.executeUpdate();
        }
    }

    public Optional<AttendanceEvent> findLastOpenCheckIn(long employeeId, LocalDate date) throws Exception {
        String dayStart = date.atStartOfDay().format(FORMATTER);
        String dayEnd = date.plusDays(1).atStartOfDay().format(FORMATTER);

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("""
                 SELECT ae.* FROM attendance_events ae
                 WHERE ae.employee_id = ? AND ae.event_type = 'CHECK_IN'
                 AND ae.event_time >= ? AND ae.event_time < ?
                 AND NOT EXISTS (
                     SELECT 1 FROM attendance_events co
                     WHERE co.employee_id = ae.employee_id
                     AND co.event_type = 'CHECK_OUT'
                     AND co.event_time > ae.event_time
                     AND co.event_time >= ? AND co.event_time < ?
                 )
                 ORDER BY ae.event_time DESC LIMIT 1
                 """)) {
            ps.setLong(1, employeeId);
            ps.setString(2, dayStart);
            ps.setString(3, dayEnd);
            ps.setString(4, dayStart);
            ps.setString(5, dayEnd);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        }
        return Optional.empty();
    }

    public Optional<AttendanceEvent> findLastEventForEmployee(long employeeId) throws Exception {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("""
                 SELECT ae.*, e.full_name, e.employee_code, e.department
                 FROM attendance_events ae
                 JOIN employees e ON e.id = ae.employee_id
                 WHERE ae.employee_id = ? ORDER BY ae.event_time DESC LIMIT 1
                 """)) {
            ps.setLong(1, employeeId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRowWithEmployee(rs));
                }
            }
        }
        return Optional.empty();
    }

    public Optional<AttendanceEvent> findLatestEvent() throws Exception {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("""
                 SELECT ae.*, e.full_name, e.employee_code, e.department
                 FROM attendance_events ae
                 JOIN employees e ON e.id = ae.employee_id
                 ORDER BY ae.event_time DESC LIMIT 1
                 """)) {
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRowWithEmployee(rs));
                }
            }
        }
        return Optional.empty();
    }

    public List<AttendanceEvent> findEventsForDate(LocalDate date) throws Exception {
        String dayStart = date.atStartOfDay().format(FORMATTER);
        String dayEnd = date.plusDays(1).atStartOfDay().format(FORMATTER);
        List<AttendanceEvent> events = new ArrayList<>();

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("""
                 SELECT ae.*, e.full_name, e.employee_code, e.department
                 FROM attendance_events ae
                 JOIN employees e ON e.id = ae.employee_id
                 WHERE ae.event_time >= ? AND ae.event_time < ?
                 ORDER BY ae.event_time DESC
                 """)) {
            ps.setString(1, dayStart);
            ps.setString(2, dayEnd);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    events.add(mapRowWithEmployee(rs));
                }
            }
        }
        return events;
    }

    public List<AttendanceEvent> findPendingTelegramEvents() throws Exception {
        List<AttendanceEvent> events = new ArrayList<>();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("""
                 SELECT ae.*, e.full_name, e.employee_code, e.department
                 FROM attendance_events ae
                 JOIN employees e ON e.id = ae.employee_id
                 WHERE ae.telegram_sent = 0 ORDER BY ae.event_time
                 """)) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    events.add(mapRowWithEmployee(rs));
                }
            }
        }
        return events;
    }

    public List<AttendanceEvent> findByEmployeeAndDateRange(long employeeId, LocalDate from, LocalDate to) throws Exception {
        List<AttendanceEvent> events = new ArrayList<>();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("""
                 SELECT ae.*, e.full_name, e.employee_code, e.department
                 FROM attendance_events ae
                 JOIN employees e ON e.id = ae.employee_id
                 WHERE ae.employee_id = ? AND ae.event_time >= ? AND ae.event_time < ?
                 ORDER BY ae.event_time
                 """)) {
            ps.setLong(1, employeeId);
            ps.setString(2, from.atStartOfDay().format(FORMATTER));
            ps.setString(3, to.plusDays(1).atStartOfDay().format(FORMATTER));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    events.add(mapRowWithEmployee(rs));
                }
            }
        }
        return events;
    }

    public Optional<AttendanceEvent> findById(long id) throws Exception {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("""
                 SELECT ae.*, e.full_name, e.employee_code, e.department
                 FROM attendance_events ae
                 JOIN employees e ON e.id = ae.employee_id WHERE ae.id = ?
                 """)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRowWithEmployee(rs));
                }
            }
        }
        return Optional.empty();
    }

    public void logInvalidQrAttempt(String qrValue) throws Exception {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("INSERT INTO invalid_qr_attempts (qr_value, attempt_time) VALUES (?, ?)")) {
            ps.setString(1, qrValue);
            ps.setString(2, LocalDateTime.now().format(FORMATTER));
            ps.executeUpdate();
        }
    }

    private AttendanceEvent mapRow(ResultSet rs) throws Exception {
        AttendanceEvent event = new AttendanceEvent();
        event.setId(rs.getLong("id"));
        event.setEmployeeId(rs.getLong("employee_id"));
        event.setEventType(EventType.fromString(rs.getString("event_type")));
        event.setEventTime(LocalDateTime.parse(rs.getString("event_time"), FORMATTER));
        event.setPhotoPath(rs.getString("photo_path"));
        event.setTelegramSent(rs.getInt("telegram_sent") == 1);
        event.setDeviceName(rs.getString("device_name"));
        event.setNotes(rs.getString("notes"));
        event.setCreatedBy(rs.getString("created_by"));
        return event;
    }

    private AttendanceEvent mapRowWithEmployee(ResultSet rs) throws Exception {
        AttendanceEvent event = mapRow(rs);
        event.setEmployeeName(rs.getString("full_name"));
        event.setEmployeeCode(rs.getString("employee_code"));
        event.setDepartment(rs.getString("department"));
        return event;
    }
}
