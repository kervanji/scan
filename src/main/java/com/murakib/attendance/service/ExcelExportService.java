package com.murakib.attendance.service;

import com.murakib.attendance.config.AppPaths;
import com.murakib.attendance.model.AttendanceEvent;
import com.murakib.attendance.model.Employee;
import com.murakib.attendance.model.EventType;
import com.murakib.attendance.repository.AttendanceRepository;
import com.murakib.attendance.repository.EmployeeRepository;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.FileOutputStream;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ExcelExportService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    private final AttendanceRepository attendanceRepository;
    private final EmployeeRepository employeeRepository;
    private final ShiftScheduleService shiftScheduleService;

    public ExcelExportService(
            AttendanceRepository attendanceRepository,
            EmployeeRepository employeeRepository,
            ShiftScheduleService shiftScheduleService
    ) {
        this.attendanceRepository = attendanceRepository;
        this.employeeRepository = employeeRepository;
        this.shiftScheduleService = shiftScheduleService;
    }

    public Path exportDailyReport(LocalDate date) throws Exception {
        List<AttendanceEvent> events = attendanceRepository.findEventsForDate(date);
        List<Employee> employees = employeeRepository.findActive();

        Map<Long, DailyRecord> records = new HashMap<>();
        for (Employee emp : employees) {
            records.put(emp.getId(), new DailyRecord(emp));
        }

        for (AttendanceEvent event : events) {
            DailyRecord record = records.computeIfAbsent(event.getEmployeeId(), id -> {
                try {
                    return employeeRepository.findById(id).map(DailyRecord::new).orElse(null);
                } catch (Exception e) {
                    return null;
                }
            });
            if (record == null) {
                continue;
            }
            if (event.getEventType() == EventType.CHECK_IN) {
                record.checkIn = event.getEventTime();
                record.notes = event.getNotes();
                record.shiftStatus = shiftScheduleService.evaluateCheckIn(event.getEventTime()).statusText();
            } else if (event.getEventType() == EventType.CHECK_OUT) {
                record.checkOut = event.getEventTime();
            }
        }

        Path output = AppPaths.config().resolve("report_" + date.format(DATE_FMT) + ".xlsx");
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("تقرير الحضور");
            Row header = sheet.createRow(0);
            String[] columns = {"رقم الموظف", "اسم الموظف", "القسم", "التاريخ", "وقت الدخول", "وقت الخروج", "مدة العمل", "الشفت / الحالة", "ملاحظات"};
            for (int i = 0; i < columns.length; i++) {
                header.createCell(i).setCellValue(columns[i]);
            }

            int rowNum = 1;
            for (DailyRecord record : records.values()) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(record.employee.getEmployeeCode());
                row.createCell(1).setCellValue(record.employee.getFullName());
                row.createCell(2).setCellValue(record.employee.getDepartment() != null ? record.employee.getDepartment() : "");
                row.createCell(3).setCellValue(date.format(DATE_FMT));
                row.createCell(4).setCellValue(record.checkIn != null ? record.checkIn.format(TIME_FMT) : "");
                row.createCell(5).setCellValue(record.checkOut != null ? record.checkOut.format(TIME_FMT) : "");

                if (record.checkIn != null && record.checkOut != null) {
                    Duration d = Duration.between(record.checkIn, record.checkOut);
                    row.createCell(6).setCellValue(AttendanceService.formatDuration(d));
                } else {
                    row.createCell(6).setCellValue("");
                }

                String status = record.shiftStatus != null ? record.shiftStatus
                        : shiftScheduleService.getAttendanceStatus(date, record.checkIn);
                row.createCell(7).setCellValue(status);
                row.createCell(8).setCellValue(record.notes != null ? record.notes : "");
            }

            for (int i = 0; i < columns.length; i++) {
                sheet.autoSizeColumn(i);
            }

            try (FileOutputStream fos = new FileOutputStream(output.toFile())) {
                workbook.write(fos);
            }
        }
        return output;
    }

    private static class DailyRecord {
        Employee employee;
        java.time.LocalDateTime checkIn;
        java.time.LocalDateTime checkOut;
        String notes;
        String shiftStatus;

        DailyRecord(Employee employee) {
            this.employee = employee;
        }
    }
}
