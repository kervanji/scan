package com.murakib.attendance.repository;

import com.murakib.attendance.db.DatabaseManager;
import com.murakib.attendance.model.Employee;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class EmployeeRepository {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private final DatabaseManager db;

    public EmployeeRepository(DatabaseManager db) {
        this.db = db;
    }

    public List<Employee> findAll() throws Exception {
        List<Employee> employees = new ArrayList<>();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM employees ORDER BY full_name");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                employees.add(mapRow(rs));
            }
        }
        return employees;
    }

    public List<Employee> findActive() throws Exception {
        List<Employee> employees = new ArrayList<>();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM employees WHERE is_active = 1 ORDER BY full_name");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                employees.add(mapRow(rs));
            }
        }
        return employees;
    }

    public Optional<Employee> findById(long id) throws Exception {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM employees WHERE id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        }
        return Optional.empty();
    }

    public Optional<Employee> findByQrToken(String qrToken) throws Exception {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM employees WHERE qr_token = ? AND is_active = 1")) {
            ps.setString(1, qrToken);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        }
        return Optional.empty();
    }

    public long insert(Employee employee) throws Exception {
        String qrToken = "EMP-" + UUID.randomUUID();
        employee.setQrToken(qrToken);
        employee.setCreatedAt(LocalDateTime.now().format(FORMATTER));

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("""
                 INSERT INTO employees (employee_code, full_name, department, job_title, qr_token, profile_photo_path, is_active, created_at)
                 VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                 """, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, employee.getEmployeeCode());
            ps.setString(2, employee.getFullName());
            ps.setString(3, employee.getDepartment());
            ps.setString(4, employee.getJobTitle());
            ps.setString(5, employee.getQrToken());
            ps.setString(6, employee.getProfilePhotoPath());
            ps.setInt(7, employee.isActive() ? 1 : 0);
            ps.setString(8, employee.getCreatedAt());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    long id = keys.getLong(1);
                    employee.setId(id);
                    return id;
                }
            }
        }
        throw new IllegalStateException("Failed to insert employee");
    }

    public void update(Employee employee) throws Exception {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("""
                 UPDATE employees SET employee_code = ?, full_name = ?, department = ?, job_title = ?,
                 profile_photo_path = ?, is_active = ? WHERE id = ?
                 """)) {
            ps.setString(1, employee.getEmployeeCode());
            ps.setString(2, employee.getFullName());
            ps.setString(3, employee.getDepartment());
            ps.setString(4, employee.getJobTitle());
            ps.setString(5, employee.getProfilePhotoPath());
            ps.setInt(6, employee.isActive() ? 1 : 0);
            ps.setLong(7, employee.getId());
            ps.executeUpdate();
        }
    }

    public void deactivate(long id) throws Exception {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("UPDATE employees SET is_active = 0 WHERE id = ?")) {
            ps.setLong(1, id);
            ps.executeUpdate();
        }
    }

    public int countActive() throws Exception {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM employees WHERE is_active = 1");
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        }
        return 0;
    }

    private Employee mapRow(ResultSet rs) throws Exception {
        Employee e = new Employee();
        e.setId(rs.getLong("id"));
        e.setEmployeeCode(rs.getString("employee_code"));
        e.setFullName(rs.getString("full_name"));
        e.setDepartment(rs.getString("department"));
        e.setJobTitle(rs.getString("job_title"));
        e.setQrToken(rs.getString("qr_token"));
        e.setProfilePhotoPath(rs.getString("profile_photo_path"));
        e.setActive(rs.getInt("is_active") == 1);
        e.setCreatedAt(rs.getString("created_at"));
        return e;
    }
}
