package com.murakib.attendance.service;

import com.murakib.attendance.model.WorkShift;
import com.murakib.attendance.repository.WorkShiftRepository;
import com.murakib.attendance.repository.EmployeeRepository;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class ShiftScheduleService {

    public record ShiftEvaluation(
            WorkShift shift,
            boolean late,
            long lateMinutes,
            String statusText
    ) {
    }

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    private final WorkShiftRepository shiftRepository;
    private final EmployeeRepository employeeRepository;

    public ShiftScheduleService(WorkShiftRepository shiftRepository, EmployeeRepository employeeRepository) {
        this.shiftRepository = shiftRepository;
        this.employeeRepository = employeeRepository;
    }

    public List<WorkShift> getShiftsForEmployee(long employeeId, LocalDate date) throws Exception {
        var assigned = employeeRepository.findAssignedShiftIds(employeeId);
        if (assigned.isEmpty()) return List.of();
        return getShiftsForDate(date).stream().filter(s -> assigned.contains(s.getId())).toList();
    }

    public Optional<WorkShift> findMatchingShift(long employeeId, LocalDate date, LocalTime checkInTime) throws Exception {
        return findMatchingShift(getShiftsForEmployee(employeeId, date), checkInTime);
    }

    public List<WorkShift> getShiftsForDate(LocalDate date) throws Exception {
        return shiftRepository.findActiveForDay(date.getDayOfWeek());
    }

    public Optional<WorkShift> findMatchingShift(LocalDate date, LocalTime checkInTime) throws Exception {
        return findMatchingShift(getShiftsForDate(date), checkInTime);
    }

    private Optional<WorkShift> findMatchingShift(List<WorkShift> shifts, LocalTime checkInTime) {
        if (shifts.isEmpty()) {
            return Optional.empty();
        }

        WorkShift best = null;
        long bestDistance = Long.MAX_VALUE;

        for (WorkShift shift : shifts) {
            LocalTime start = shift.getStartTime();
            LocalTime earliest = start.minusMinutes(shift.getEarlyCheckinMinutes());

            if (checkInTime.isBefore(earliest)) {
                continue;
            }
            if (shift.getEndTime() != null && checkInTime.isAfter(shift.getEndTime())) {
                continue;
            }

            long distance = Math.abs(Duration.between(start, checkInTime).toMinutes());
            if (distance < bestDistance) {
                bestDistance = distance;
                best = shift;
            }
        }
        return Optional.ofNullable(best);
    }

    public ShiftEvaluation evaluateCheckIn(LocalDateTime checkIn) throws Exception {
        Optional<WorkShift> shiftOpt = findMatchingShift(checkIn.toLocalDate(), checkIn.toLocalTime());
        return evaluate(checkIn, shiftOpt);
    }

    public ShiftEvaluation evaluateCheckIn(long employeeId, LocalDateTime checkIn) throws Exception {
        Optional<WorkShift> shiftOpt = findMatchingShift(employeeId, checkIn.toLocalDate(), checkIn.toLocalTime());
        return evaluate(checkIn, shiftOpt);
    }

    private ShiftEvaluation evaluate(LocalDateTime checkIn, Optional<WorkShift> shiftOpt) {
        if (shiftOpt.isEmpty()) {
            return new ShiftEvaluation(null, false, 0, "بدون شفت مطابق");
        }

        WorkShift shift = shiftOpt.get();
        LocalTime deadline = shift.getStartTime().plusMinutes(shift.getLateGraceMinutes());
        boolean late = checkIn.toLocalTime().isAfter(deadline);
        long lateMinutes = late
                ? Duration.between(deadline, checkIn.toLocalTime()).toMinutes()
                : 0;
        String status = late
                ? "متأخر (" + shift.getName() + " +" + lateMinutes + " د)"
                : "في الوقت (" + shift.getName() + ")";
        return new ShiftEvaluation(shift, late, lateMinutes, status);
    }

    public boolean isLate(LocalDateTime checkIn) throws Exception {
        return evaluateCheckIn(checkIn).late();
    }

    public String getAttendanceStatus(LocalDate date, LocalDateTime checkIn) throws Exception {
        if (checkIn == null) {
            List<WorkShift> shifts = getShiftsForDate(date);
            if (shifts.isEmpty()) {
                return "غائب";
            }
            return "غائب";
        }
        return evaluateCheckIn(checkIn).statusText();
    }

    public List<String> findLateEmployeesReport(LocalDate date, List<LocalDateTime> checkInsWithNames) throws Exception {
        List<String> lines = new ArrayList<>();
        for (var entry : checkInsWithNames) {
            ShiftEvaluation eval = evaluateCheckIn(entry);
            if (eval.late()) {
                lines.add("• متأخر - " + eval.shift().getName() + " - " + entry.format(DateTimeFormatter.ofPattern("HH:mm")));
            }
        }
        return lines;
    }

    public List<ShiftEvaluation> evaluateAllCheckInsForDate(LocalDate date, List<LocalDateTime> checkIns) throws Exception {
        List<ShiftEvaluation> results = new ArrayList<>();
        for (LocalDateTime checkIn : checkIns) {
            results.add(evaluateCheckIn(checkIn));
        }
        return results;
    }

    /** موظفون لم يسجّلوا دخولاً ضمن نافذة شفت معيّن */
    public boolean wasPresentInShift(LocalDate date, WorkShift shift, List<LocalDateTime> checkIns) {
        LocalTime earliest = shift.getStartTime().minusMinutes(shift.getEarlyCheckinMinutes());
        LocalTime latest = shift.getEndTime() != null
                ? shift.getEndTime()
                : shift.getStartTime().plusHours(4);

        for (LocalDateTime checkIn : checkIns) {
            if (!checkIn.toLocalDate().equals(date)) {
                continue;
            }
            LocalTime t = checkIn.toLocalTime();
            if (!t.isBefore(earliest) && !t.isAfter(latest)) {
                return true;
            }
        }
        return false;
    }

    public List<WorkShift> getShiftsSorted(LocalDate date) throws Exception {
        List<WorkShift> shifts = new ArrayList<>(getShiftsForDate(date));
        shifts.sort(Comparator.comparing(WorkShift::getStartTime));
        return shifts;
    }

    public static String formatShiftSummary(WorkShift shift) {
        String days = WorkShiftRepository.formatDays(shift.getDaysOfWeek());
        String end = shift.getEndTime() != null ? " → " + shift.getEndTime().format(TIME_FMT) : "";
        return shift.getName() + " | " + days + " | " + shift.getStartTime().format(TIME_FMT) + end
                + " | تأخير بعد " + shift.getLateGraceMinutes() + " د";
    }
}
