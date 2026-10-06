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
            // Bootstrap default accounts only the very first time the app ever runs
            // against an empty database. Once any user exists, this never runs again -
            // otherwise an account intentionally deleted (e.g. via direct DB access,
            // since there is no in-app delete) would silently come back on every restart.
            if (userRepository.count() == 0) {
                User admin = new User("admin", passwordEncoder.encode("admin123"), "ADMIN");
                userRepository.save(admin);
                System.out.println("Seeded default admin user: admin / admin123");

                User nurse = new User("nurse", passwordEncoder.encode("nurse123"), "NURSE");
                userRepository.save(nurse);
                System.out.println("Seeded default nurse user: nurse / nurse123");

                User doctor = new User("doctor", passwordEncoder.encode("doctor123"), "DOCTOR");
                userRepository.save(doctor);
                System.out.println("Seeded default doctor user: doctor / doctor123");

                // Individual doctor logins, so consultation attribution and schedule
                // ownership checks work per-physician instead of everyone sharing "doctor".
                String[][] doctorAccounts = {
                    {"dr.patel", "patel123"},
                    {"dr.lee", "lee123"},
                    {"dr.sharma", "sharma123"}
                };
                for (String[] account : doctorAccounts) {
                    userRepository.save(new User(account[0], passwordEncoder.encode(account[1]), "DOCTOR"));
                    System.out.println("Seeded doctor user: " + account[0] + " / " + account[1]);
                }

                User student1 = new User("student1", passwordEncoder.encode("student123"), "STUDENT");
                student1.setFullName("Elena Prasad");
                student1.setMaritalStatus("Single");
                student1.setCountry("Fiji");
                student1.setFeeAmount(50.00);
                student1.setFeePaid(true);
                userRepository.save(student1);
                System.out.println("Seeded default student user: student1 / student123");
            }

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
                    String newName = appt.getDoctorName() == null ? null : nameMigration.get(appt.getDoctorName());
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

            // One-time backfill: schedule rows created before the "archived" column
            // existed have it stored as NULL. Every "AndArchivedFalse" query used for
            // availability/management translates to SQL `archived = false`, which does
            // NOT match NULL under SQL's three-valued logic - so without this backfill,
            // every pre-existing slot would silently vanish as if archived.
            {
                List<DoctorSchedule> needsBackfill = scheduleRepository.findAll().stream()
                        .filter(s -> s.getArchivedRaw() == null)
                        .collect(java.util.stream.Collectors.toList());
                if (!needsBackfill.isEmpty()) {
                    needsBackfill.forEach(s -> s.setArchived(false));
                    scheduleRepository.saveAll(needsBackfill);
                    System.out.println("Backfilled archived=false on " + needsBackfill.size() + " pre-existing schedule slot(s).");
                }
            }

            // One-time correction for schedule rows left over from the old model, where
            // booking one doctor's slot incorrectly marked every OTHER doctor's row for
            // that same date+time as booked too. A row is genuinely booked only if an
            // appointment that still holds it references it via scheduleSlotId. This must
            // match the runtime rule: a slot is only freed on cancellation, reschedule or
            // no-show, so checked-in and completed appointments still hold theirs (DEF-02 -
            // freeing them here let a restart open up a slot that was mid-consultation).
            {
                java.util.Set<String> slotHoldingStatuses = java.util.Set.of("BOOKED", "CHECKED_IN", "COMPLETED");
                java.util.Set<Long> actuallyBookedSlotIds = appointmentRepository.findAll().stream()
                        .filter(a -> slotHoldingStatuses.contains(a.getStatus()))
                        .map(Appointment::getScheduleSlotId)
                        .filter(java.util.Objects::nonNull)
                        .collect(java.util.stream.Collectors.toSet());

                List<DoctorSchedule> mismatched = scheduleRepository.findAll().stream()
                        .filter(s -> s.isBooked() != actuallyBookedSlotIds.contains(s.getId()))
                        .collect(java.util.stream.Collectors.toList());
                if (!mismatched.isEmpty()) {
                    mismatched.forEach(s -> s.setBooked(actuallyBookedSlotIds.contains(s.getId())));
                    scheduleRepository.saveAll(mismatched);
                    System.out.println("Corrected booked status on " + mismatched.size()
                            + " schedule slot(s) after moving to per-doctor booking.");
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
                                // Each doctor's slot is booked independently, so a freshly
                                // topped-up row always starts open regardless of other doctors.
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

        };
    }
}