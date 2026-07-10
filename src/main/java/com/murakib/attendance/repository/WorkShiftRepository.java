package com.murakib.attendance.repository;

import com.murakib.attendance.db.DatabaseManager;
import com.murakib.attendance.model.WorkShift;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class WorkShiftRepository {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    private final DatabaseManager db;

    public WorkShiftRepository(DatabaseManager db) {
        this.db = db;
    }

    public void ensureDefaults(SettingsRepository settings) throws Exception {
        if (count() > 0) {
            return;
        }
        String workStart = settings.getOrDefault(SettingsRepository.WORK_START_TIME, "");
        String lateAfter = settings.getOrDefault(SettingsRepository.LATE_AFTER_TIME, "");
        if (!workStart.isBlank()) {
            WorkShift legacy = new WorkShift();
            legacy.setName("الشفت الافتراضي");
            legacy.setDaysOfWeek("1,2,3,4,5,6,7");
            legacy.setStartTime(LocalTime.parse(workStart, TIME_FMT));
            legacy.setLateGraceMinutes(calcGrace(workStart, lateAfter));
            legacy.setEarlyCheckinMinutes(60);
            legacy.setActive(true);
            legacy.setSortOrder(0);
            insert(legacy);
        }
    }

    private static int calcGrace(String start, String lateAfter) {
        if (lateAfter.isBlank()) {
            return 15;
        }
        try {
            long minutes = java.time.Duration.between(
                    LocalTime.parse(start, TIME_FMT),
                    LocalTime.parse(lateAfter, TIME_FMT)
            ).toMinutes();
            return (int) Math.max(0, minutes);
        } catch (Exception e) {
            return 15;
        }
    }

    public int count() throws Exception {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM work_shifts");
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        }
        return 0;
    }

    public List<WorkShift> findAll() throws Exception {
        List<WorkShift> shifts = new ArrayList<>();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM work_shifts ORDER BY sort_order, start_time");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                shifts.add(mapRow(rs));
            }
        }
        return shifts;
    }

    public List<WorkShift> findActiveForDay(DayOfWeek day) throws Exception {
        int dayNum = day.getValue();
        List<WorkShift> result = new ArrayList<>();
        for (WorkShift shift : findAll()) {
            if (!shift.isActive()) {
                continue;
            }
            if (appliesToDay(shift, dayNum)) {
                result.add(shift);
            }
        }
        return result;
    }

    public static boolean appliesToDay(WorkShift shift, int dayNum) {
        return Arrays.stream(shift.getDaysOfWeek().split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .mapToInt(Integer::parseInt)
                .anyMatch(d -> d == dayNum);
    }

    public Optional<WorkShift> findById(long id) throws Exception {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM work_shifts WHERE id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        }
        return Optional.empty();
    }

    public long insert(WorkShift shift) throws Exception {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("""
                 INSERT INTO work_shifts (name, days_of_week, start_time, late_grace_minutes, end_time,
                 early_checkin_minutes, is_active, sort_order)
                 VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                 """, Statement.RETURN_GENERATED_KEYS)) {
            bindShift(ps, shift);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    long id = keys.getLong(1);
                    shift.setId(id);
                    return id;
                }
            }
        }
        throw new IllegalStateException("Failed to insert shift");
    }

    public void update(WorkShift shift) throws Exception {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("""
                 UPDATE work_shifts SET name = ?, days_of_week = ?, start_time = ?, late_grace_minutes = ?,
                 end_time = ?, early_checkin_minutes = ?, is_active = ?, sort_order = ? WHERE id = ?
                 """)) {
            bindShift(ps, shift);
            ps.setLong(9, shift.getId());
            ps.executeUpdate();
        }
    }

    public void delete(long id) throws Exception {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM work_shifts WHERE id = ?")) {
            ps.setLong(1, id);
            ps.executeUpdate();
        }
    }

    private void bindShift(PreparedStatement ps, WorkShift shift) throws Exception {
        ps.setString(1, shift.getName());
        ps.setString(2, shift.getDaysOfWeek());
        ps.setString(3, shift.getStartTime().format(TIME_FMT));
        ps.setInt(4, shift.getLateGraceMinutes());
        if (shift.getEndTime() != null) {
            ps.setString(5, shift.getEndTime().format(TIME_FMT));
        } else {
            ps.setNull(5, java.sql.Types.VARCHAR);
        }
        ps.setInt(6, shift.getEarlyCheckinMinutes());
        ps.setInt(7, shift.isActive() ? 1 : 0);
        ps.setInt(8, shift.getSortOrder());
    }

    private WorkShift mapRow(ResultSet rs) throws Exception {
        WorkShift shift = new WorkShift();
        shift.setId(rs.getLong("id"));
        shift.setName(rs.getString("name"));
        shift.setDaysOfWeek(rs.getString("days_of_week"));
        shift.setStartTime(LocalTime.parse(rs.getString("start_time"), TIME_FMT));
        shift.setLateGraceMinutes(rs.getInt("late_grace_minutes"));
        String end = rs.getString("end_time");
        if (end != null && !end.isBlank()) {
            shift.setEndTime(LocalTime.parse(end, TIME_FMT));
        }
        shift.setEarlyCheckinMinutes(rs.getInt("early_checkin_minutes"));
        shift.setActive(rs.getInt("is_active") == 1);
        shift.setSortOrder(rs.getInt("sort_order"));
        return shift;
    }

    public static String formatDays(String daysOfWeek) {
        String[] names = {"", "إث", "ث", "ر", "خ", "ج", "س", "ح"};
        return Arrays.stream(daysOfWeek.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> {
                    int d = Integer.parseInt(s);
                    return d >= 1 && d <= 7 ? names[d] : s;
                })
                .collect(Collectors.joining(" "));
    }
}
