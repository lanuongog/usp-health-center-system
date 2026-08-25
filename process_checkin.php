<?php
session_start();
require_once 'config.php';

// Enforce access control for Nurse and Admin roles only
if (!isset($_SESSION['user_role']) || !in_array($_SESSION['user_role'], ['nurse', 'admin'])) {
    die("Access denied. Nurse privileges required.");
}

if ($_SERVER['REQUEST_METHOD'] === 'POST' && isset($_POST['submit_checkin'])) {
    $student_db_id = intval($_POST['student_db_id']);
    $visit_type = trim($_POST['visit_type']);
    $systolic = intval($_POST['systolic']);
    $diastolic = intval($_POST['diastolic']);
    $temperature = floatval($_POST['temperature']);
    $heart_rate = intval($_POST['heart_rate']);
    $presenting_complaint = trim($_POST['presenting_complaint']);

    // Check duplicate pending check-ins for the same student visit
    $check_stmt = $conn->prepare("SELECT id FROM visits WHERE student_id = ? AND status = 'ready_for_doctor'");
    $check_stmt->bind_param("i", $student_db_id);
    $check_stmt->execute();
    $check_result = $check_stmt->get_result();

    if ($check_result->num_rows > 0) {
        $check_stmt->close();
        header("Location: nurse_checkin.php?error=" . urlencode("This student is already checked in and waiting for the doctor."));
        exit();
    }
    $check_stmt->close();

    // Range validation for vital signs
    if ($systolic < 50 || $systolic > 250 || $diastolic < 30 || $diastolic > 150) {
        header("Location: nurse_checkin.php?error=" . urlencode("Blood pressure values are out of realistic range."));
        exit();
    }

    if ($temperature < 30.0 || $temperature > 45.0) {
        header("Location: nurse_checkin.php?error=" . urlencode("Temperature reading is out of realistic range."));
        exit();
    }

    if ($heart_rate < 30 || $heart_rate > 220) {
        header("Location: nurse_checkin.php?error=" . urlencode("Heart rate is out of realistic range."));
        exit();
    }

    $bp_formatted = $systolic . "/" . $diastolic;
    $check_in_time = date('Y-m-d H:i:s');
    $status = 'ready_for_doctor';

    // Insert visit record and trigger queue handoff
    $stmt = $conn->prepare("INSERT INTO visits (student_id, visit_type, check_in_time, blood_pressure, temperature, heart_rate, presenting_complaint, status) VALUES (?, ?, ?, ?, ?, ?, ?, ?)");
    $stmt->bind_param("isssdiss", $student_db_id, $visit_type, $check_in_time, $bp_formatted, $temperature, $heart_rate, $presenting_complaint, $status);

    if ($stmt->execute()) {
        $stmt->close();
        header("Location: nurse_checkin.php?status=success");
        exit();
    } else {
        $stmt->close();
        header("Location: nurse_checkin.php?error=" . urlencode("Database insert failed. Please try again."));
        exit();
    }
} else {
    header("Location: nurse_checkin.php");
    exit();
}
