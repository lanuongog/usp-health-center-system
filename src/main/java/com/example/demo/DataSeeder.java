package com.example.demo;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class DataSeeder {

    @Bean
    public CommandLineRunner seedData(UserRepository userRepository,
                                       AppointmentRepository appointmentRepository,
                                       DoctorScheduleRepository scheduleRepository,
                                       PasswordEncoder passwordEncoder) {
        return args -> {
            if (userRepository.findByUsername("admin").isEmpty()) {
                User admin = new User("admin", passwordEncoder.encode("admin123"), "ADMIN");
                userRepository.save(admin);
                System.out.println("Seeded default admin user: admin / admin123");
            }

            if (userRepository.findByUsername("nurse").isEmpty()) {
                User nurse = new User("nurse", passwordEncoder.encode("nurse123"), "NURSE");
                userRepository.save(nurse);
                System.out.println("Seeded default nurse user: nurse / nurse123");
            }

            if (userRepository.findByUsername("doctor").isEmpty()) {
                User doctor = new User("doctor", passwordEncoder.encode("doctor123"), "DOCTOR");
                userRepository.save(doctor);
                System.out.println("Seeded default doctor user: doctor / doctor123");
            }

            // Individual doctor logins, so consultation attribution (FR-04-57) and schedule
            // ownership checks work per-physician instead of everyone sharing "doctor".
            String[][] doctorAccounts = {
                {"dr.patel", "patel123"},
                {"dr.lee", "lee123"},
                {"dr.sharma", "sharma123"}
            };
            for (String[] account : doctorAccounts) {
                if (userRepository.findByUsername(account[0]).isEmpty()) {
                    userRepository.save(new User(account[0], passwordEncoder.encode(account[1]), "DOCTOR"));
                    System.out.println("Seeded doctor user: " + account[0] + " / " + account[1]);
                }
            }

            User student1 = userRepository.findByUsername("student1").orElse(null);
            if (student1 == null) {
                student1 = new User("student1", passwordEncoder.encode("student123"), "STUDENT");
                System.out.println("Seeded default student user: student1 / student123");
            }
            // Backfills registration/fee details even on an existing student1 row, so demo
            // data created before these fields existed doesn't show up blank in the admin view.
            if (student1.getFullName() == null) {
                student1.setFullName("Elena Prasad");
                student1.setMaritalStatus("Single");
                student1.setCountry("Fiji");
                student1.setFeeAmount(50.00);
                student1.setFeePaid(true);
            }
            userRepository.save(student1);

            // One-time migration: earlier seed data identified doctors by display name
            // (e.g. "Dr. Patel"). Individual doctor logins now use clean handles instead
            // (e.g. "dr.patel"), so existing schedule/appointment rows are renamed to match -
            // otherwise schedule ownership checks and consultation attribution would break
            // for the new per-doctor accounts.
            {
                Map<String, String> nameMigration = Map.of(
                        "Dr. Patel", "dr.patel",
                        "Dr. Lee", "dr.lee",
                        "Dr. Sharma", "dr.sharma"
                );

                int migratedSlots = 0;
                for (DoctorSchedule slot : scheduleRepository.findAll()) {
                    String newName = nameMigration.get(slot.getDoctorName());
                    if (newName != null) {
                        slot.setDoctorName(newName);
                        scheduleRepository.save(slot);
                        migratedSlots++;
                    }
                }

                int migratedAppointments = 0;
                for (Appointment appt : appointmentRepository.findAll()) {
                    String newName = nameMigration.get(appt.getDoctorName());
                    if (newName != null) {
                        appt.setDoctorName(newName);
                        appointmentRepository.save(appt);
                        migratedAppointments++;
                    }
                }

                if (migratedSlots > 0 || migratedAppointments > 0) {
                    System.out.println("Migrated " + migratedSlots + " schedule slot(s) and " + migratedAppointments
                            + " appointment(s) to the new doctor login handles.");
                }
            }

            // Tops up a rolling window of open slots (today -> +6 days) on every startup.
            // A one-time "count == 0" seed would only ever populate slots for the date the
            // app first ran, so restarting days later (or after testers book everything out)
            // left the booking page with nothing available for the current date.
            {
                String[] timeSlots = {
                    "09:00 AM - 09:30 AM",
                    "09:30 AM - 10:00 AM",
                    "10:00 AM - 10:30 AM",
                    "10:30 AM - 11:00 AM",
                    "02:00 PM - 02:30 PM",
                    "02:30 PM - 03:00 PM",
                    "03:00 PM - 03:30 PM"
                };

                LocalDate today = LocalDate.now();
                String[] doctors = {"dr.patel", "dr.lee", "dr.sharma"};
                int seeded = 0;

                for (int dayOffset = 0; dayOffset <= 6; dayOffset++) {
                    LocalDate date = today.plusDays(dayOffset);
                    for (String doctor : doctors) {
                        List<DoctorSchedule> existing = scheduleRepository.findByDoctorNameAndScheduleDate(doctor, date);
                        for (String timeSlot : timeSlots) {
                            boolean alreadyExists = existing.stream().anyMatch(s -> s.getTimeSlot().equals(timeSlot));
                            if (!alreadyExists) {
                                DoctorSchedule slot = new DoctorSchedule();
                                slot.setDoctorName(doctor);
                                slot.setScheduleDate(date);
                                slot.setTimeSlot(timeSlot);
                                slot.setBooked(false);
                                scheduleRepository.save(slot);
                                seeded++;
                            }
                        }
                    }
                }

                if (seeded > 0) {
                    System.out.println("Topped up " + seeded + " doctor availability slot(s) for the next 7 days.");
                }
            }

            if (appointmentRepository.count() == 0) {
                Appointment a1 = new Appointment();
                a1.setStudentName("Alice Tan");
                a1.setDoctorName("dr.patel");
                a1.setAppointmentDate(LocalDate.of(2026, 8, 10));
                a1.setVisitReason("Fever");
                a1.setUrgencyLevel("Urgent");
                a1.setStatus("COMPLETED");
                appointmentRepository.save(a1);

                Appointment a2 = new Appointment();
                a2.setStudentName("Ben Kumar");
                a2.setDoctorName("dr.patel");
                a2.setAppointmentDate(LocalDate.of(2026, 8, 12));
                a2.setVisitReason("Routine Checkup");
                a2.setUrgencyLevel("Routine");
                a2.setStatus("COMPLETED");
                appointmentRepository.save(a2);

                Appointment a3 = new Appointment();
                a3.setStudentName("Chen Wei");
                a3.setDoctorName("dr.lee");
                a3.setAppointmentDate(LocalDate.of(2026, 8, 14));
                a3.setVisitReason("Follow-up");
                a3.setUrgencyLevel("Routine");
                a3.setStatus("NO_SHOW");
                appointmentRepository.save(a3);

                Appointment a4 = new Appointment();
                a4.setStudentName("Divya Singh");
                a4.setDoctorName("dr.lee");
                a4.setAppointmentDate(LocalDate.of(2026, 8, 15));
                a4.setVisitReason("Injury");
                a4.setUrgencyLevel("Emergency");
                a4.setStatus("COMPLETED");
                appointmentRepository.save(a4);

                Appointment a5 = new Appointment();
                a5.setStudentName("Elena Prasad");
                a5.setDoctorName("dr.patel");
                a5.setAppointmentDate(LocalDate.now());
                a5.setVisitReason("Headache");
                a5.setUrgencyLevel("Routine");
                a5.setStatus("BOOKED");
                appointmentRepository.save(a5);

                Appointment a6 = new Appointment();
                a6.setStudentName("Frank Lal");
                a6.setDoctorName("dr.lee");
                a6.setAppointmentDate(LocalDate.now());
                a6.setVisitReason("Sprained Ankle");
                a6.setUrgencyLevel("Urgent");
                a6.setStatus("BOOKED");
                appointmentRepository.save(a6);

                System.out.println("Seeded 6 sample appointments (2 waiting in the check-in queue).");
            }
        };
    }
}