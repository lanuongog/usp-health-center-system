<?php
session_start();
require_once 'config.php';

// Enforce access control for Nurse and Admin roles only
if (!isset($_SESSION['user_role']) || !in_array($_SESSION['user_role'], ['nurse', 'admin'])) {
    die("Access denied. Nurse privileges required.");
}

$search_query = isset($_GET['search']) ? trim($_GET['search']) : '';
$students = [];

if (!empty($search_query)) {
    $stmt = $conn->prepare("SELECT * FROM students WHERE student_id LIKE ? OR full_name LIKE ?");
    $term = "%" . $search_query . "%";
    $stmt->bind_param("ss", $term, $term);
    $stmt->execute();
    $result = $stmt->get_result();
    while ($row = $result->fetch_assoc()) {
        $students[] = $row;
    }
    $stmt->close();
}
?>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>Nurse Check-In & Vitals</title>
    <style>
        body { font-family: Arial, sans-serif; margin: 20px; }
        .card { border: 1px solid #ccc; padding: 15px; margin-bottom: 20px; border-radius: 5px; }
        .error { color: red; font-weight: bold; }
        .success { color: green; font-weight: bold; }
        label { display: block; margin-top: 10px; }
        input[type="text"], input[type="number"], textarea { width: 300px; padding: 6px; }
        table { border-collapse: collapse; width: 100%; margin-top: 10px; }
        th, td { border: 1px solid #ddd; padding: 8px; text-align: left; }
        th { background-color: #f2f2f2; }
    </style>
</head>
<body>

    <h2>Nurse Pre-Consultation Check-In</h2>

    <?php if (isset($_GET['status']) && $_GET['status'] == 'success'): ?>
        <p class="success">Check-in and vitals saved successfully. Patient sent to queue.</p>
    <?php endif; ?>

    <?php if (isset($_GET['error'])): ?>
        <p class="error"><?php echo htmlspecialchars($_GET['error']); ?></p>
    <?php endif; ?>

    <div class="card">
        <h3>1. Search Student (Booked or Walk-In)</h3>
        <form method="GET" action="nurse_checkin.php">
            <input type="text" name="search" placeholder="Enter Student ID or Name" value="<?php echo htmlspecialchars($search_query); ?>" required>
            <button type="submit">Search</button>
        </form>

        <?php if (!empty($students)): ?>
            <table>
                <tr>
                    <th>Student ID</th>
                    <th>Name</th>
                    <th>Action</th>
                </tr>
                <?php foreach ($students as $student): ?>
                    <tr>
                        <td><?php echo htmlspecialchars($student['student_id']); ?></td>
                        <td><?php echo htmlspecialchars($student['full_name']); ?></td>
                        <td>
                            <a href="nurse_checkin.php?search=<?php echo urlencode($search_query); ?>&select_id=<?php echo $student['id']; ?>">Select Student</a>
                        </td>
                    </tr>
                <?php endforeach; ?>
            </table>
        <?php elseif (!empty($search_query)): ?>
            <p>No student records found.</p>
        <?php endif; ?>
    </div>

    <?php if (isset($_GET['select_id'])): 
        $selected_id = intval($_GET['select_id']);
        $stmt = $conn->prepare("SELECT * FROM students WHERE id = ?");
        $stmt->bind_param("i", $selected_id);
        $stmt->execute();
        $selected_student = $stmt->get_result()->fetch_assoc();
        $stmt->close();
    ?>
        <?php if ($selected_student): ?>
            <div class="card">
                <h3>2. Enter Vitals & Presenting Complaint</h3>
                <p><strong>Selected Student:</strong> <?php echo htmlspecialchars($selected_student['full_name']); ?> (ID: <?php echo htmlspecialchars($selected_student['student_id']); ?>)</p>

                <form method="POST" action="process_checkin.php">
                    <input type="hidden" name="student_db_id" value="<?php echo $selected_student['id']; ?>">
                    
                    <label>Visit Type:</label>
                    <select name="visit_type" required>
                        <option value="walk_in">Walk-In Student</option>
                        <option value="booked">Booked Appointment</option>
                    </select>

                    <label>Systolic BP (mmHg):</label>
                    <input type="number" name="systolic" min="50" max="250" placeholder="e.g. 120" required>

                    <label>Diastolic BP (mmHg):</label>
                    <input type="number" name="diastolic" min="30" max="150" placeholder="e.g. 80" required>

                    <label>Body Temperature (°C):</label>
                    <input type="number" step="0.1" name="temperature" min="30" max="45" placeholder="e.g. 37.0" required>

                    <label>Heart Rate (BPM):</label>
                    <input type="number" name="heart_rate" min="30" max="220" placeholder="e.g. 72" required>

                    <label>Presenting Complaint:</label><br>
                    <textarea name="presenting_complaint" rows="4" placeholder="Describe symptoms or primary health reason" required></textarea>

                    <br><br>
                    <button type="submit" name="submit_checkin">Mark as Ready for Doctor</button>
                </form>
            </div>
        <?php endif; ?>
    <?php endif; ?>

</body>
</html>
