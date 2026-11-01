package com.attendance.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Encapsulates attendance summary metrics for a planned academic session.
 *
 * Rules:
 * - Deterministic, pure Java computation over retrieved roster items.
 * - Missing attendance in COMPLETED sessions is NOT inferred as ABSENT.
 * - Attendance Percentage is calculated as: ((PRESENT + LATE) / Total Enrolled) * 100.
 * - Percentage is N/A (null) for zero enrolled students or unactivated SCHEDULED sessions.
 * - Inconsistent records are identified separately without inflating metrics.
 */
public class SessionRosterMetrics {

    private int totalEnrolled;
    private int presentCount;
    private int lateCount;
    private int absentCount;
    private int notRecordedCount;
    private Double attendancePercentage;
    private boolean percentageApplicable;
    private boolean containsInconsistencies;
    private boolean inProgress;
    private int inconsistencyCount;

    public SessionRosterMetrics() {}

    public int getTotalEnrolled() {
        return totalEnrolled;
    }

    public void setTotalEnrolled(int totalEnrolled) {
        this.totalEnrolled = totalEnrolled;
    }

    public int getPresentCount() {
        return presentCount;
    }

    public void setPresentCount(int presentCount) {
        this.presentCount = presentCount;
    }

    public int getLateCount() {
        return lateCount;
    }

    public void setLateCount(int lateCount) {
        this.lateCount = lateCount;
    }

    public int getAbsentCount() {
        return absentCount;
    }

    public void setAbsentCount(int absentCount) {
        this.absentCount = absentCount;
    }

    public int getNotRecordedCount() {
        return notRecordedCount;
    }

    public void setNotRecordedCount(int notRecordedCount) {
        this.notRecordedCount = notRecordedCount;
    }

    public Double getAttendancePercentage() {
        return attendancePercentage;
    }

    public void setAttendancePercentage(Double attendancePercentage) {
        this.attendancePercentage = attendancePercentage;
    }

    public boolean isPercentageApplicable() {
        return percentageApplicable;
    }

    public void setPercentageApplicable(boolean percentageApplicable) {
        this.percentageApplicable = percentageApplicable;
    }

    public boolean isContainsInconsistencies() {
        return containsInconsistencies;
    }

    public void setContainsInconsistencies(boolean containsInconsistencies) {
        this.containsInconsistencies = containsInconsistencies;
    }

    public boolean isInProgress() {
        return inProgress;
    }

    public void setInProgress(boolean inProgress) {
        this.inProgress = inProgress;
    }

    public int getInconsistencyCount() {
        return inconsistencyCount;
    }

    public void setInconsistencyCount(int inconsistencyCount) {
        this.inconsistencyCount = inconsistencyCount;
    }

    /**
     * Computes deterministic metrics over the provided roster items and session state.
     */
    public static SessionRosterMetrics compute(String plannedStatus, Integer qrSessionId, List<SessionRosterItem> items) {
        SessionRosterMetrics m = new SessionRosterMetrics();
        if (items == null || items.isEmpty()) {
            m.totalEnrolled = 0;
            m.percentageApplicable = false;
            m.attendancePercentage = null;
            return m;
        }

        m.totalEnrolled = items.size();

        for (SessionRosterItem item : items) {
            String pStatus = item.getPresentationStatus();
            if ("PRESENT".equals(pStatus)) {
                m.presentCount++;
            } else if ("LATE".equals(pStatus)) {
                m.lateCount++;
            } else if ("ABSENT".equals(pStatus)) {
                m.absentCount++;
            } else if ("NOT ACTIVATED".equals(pStatus)
                    || "NOT YET RECORDED".equals(pStatus)
                    || "NOT RECORDED".equals(pStatus)) {
                m.notRecordedCount++;
            } else if ("DATA INCONSISTENCY".equals(pStatus) || item.isInconsistent()) {
                m.inconsistencyCount++;
                m.containsInconsistencies = true;
            }
        }

        // Lifecycle state: inProgress reflects active session status independently of percentage applicability
        m.inProgress = "ATTENDANCE_OPEN".equals(plannedStatus);

        // Percentage applicability rules:
        // 1. Inconsistent records present -> N/A (percentageApplicable = false, attendancePercentage = null)
        // 2. SCHEDULED without an activated QR session -> N/A
        // 3. Otherwise applicable: (PRESENT + LATE) / totalEnrolled * 100 with HALF_UP rounding to 2 decimals
        if (m.inconsistencyCount > 0) {
            m.percentageApplicable = false;
            m.attendancePercentage = null;
        } else if ("SCHEDULED".equals(plannedStatus) && qrSessionId == null) {
            m.percentageApplicable = false;
            m.attendancePercentage = null;
        } else {
            m.percentageApplicable = true;
            double rawPercentage = ((double) (m.presentCount + m.lateCount) / m.totalEnrolled) * 100.0;
            m.attendancePercentage = BigDecimal.valueOf(rawPercentage)
                    .setScale(2, RoundingMode.HALF_UP)
                    .doubleValue();
        }

        return m;
    }
}

