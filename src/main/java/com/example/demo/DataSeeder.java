package com.example.demo;

import java.time.LocalDate;

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

            if (userRepository.findByUsername("student1").isEmpty()) {
                User student = new User("student1", passwordEncoder.encode("student123"), "STUDENT");
                userRepository.save(student);
                System.out.println("Seeded default student user: student1 / student123");
            }

            if (scheduleRepository.count() == 0) {
                String[] timeSlots = {
                    "09:00 AM - 09:30 AM",
                    "09:30 AM - 10:00 AM",
                    "10:00 AM - 10:30 AM",
                    "10:30 AM - 11:00 AM",
                    "02:00 PM - 02:30 PM",
                    "02:30 PM - 03:00 PM",
                    "03:00 PM - 03:30 PM"
                };

                // Seed for Today, Tomorrow, and Next 2 Days
                LocalDate today = LocalDate.now();
                String[] doctors = {"Dr. Patel", "Dr. Lee", "Dr. Sharma"};

                for (int dayOffset = 0; dayOffset <= 3; dayOffset++) {
                    LocalDate date = today.plusDays(dayOffset);
                    for (int i = 0; i < doctors.length; i++) {
                        String doctor = doctors[i];
                        // Add 2 slots per doctor per day
                        int slotIdx1 = (dayOffset + i) % timeSlots.length;
                        int slotIdx2 = (dayOffset + i + 2) % timeSlots.length;

                        DoctorSchedule s1 = new DoctorSchedule();
                        s1.setDoctorName(doctor);
                        s1.setScheduleDate(date);
                        s1.setTimeSlot(timeSlots[slotIdx1]);
                        s1.setBooked(false);
                        scheduleRepository.save(s1);

                        DoctorSchedule s2 = new DoctorSchedule();
                        s2.setDoctorName(doctor);
                        s2.setScheduleDate(date);
                        s2.setTimeSlot(timeSlots[slotIdx2]);
                        s2.setBooked(false);
                        scheduleRepository.save(s2);
                    }
                }

                System.out.println("Seeded doctor availability slots across multiple days.");
            }

            if (appointmentRepository.count() == 0) {
                Appointment a1 = new Appointment();
                a1.setStudentName("Alice Tan");
                a1.setDoctorName("Dr. Patel");
                a1.setAppointmentDate(LocalDate.of(2026, 8, 10));
                a1.setVisitReason("Fever");
                a1.setUrgencyLevel("Urgent");
                a1.setStatus("COMPLETED");
                appointmentRepository.save(a1);

                Appointment a2 = new Appointment();
                a2.setStudentName("Ben Kumar");
                a2.setDoctorName("Dr. Patel");
                a2.setAppointmentDate(LocalDate.of(2026, 8, 12));
                a2.setVisitReason("Routine Checkup");
                a2.setUrgencyLevel("Routine");
                a2.setStatus("COMPLETED");
                appointmentRepository.save(a2);

                Appointment a3 = new Appointment();
                a3.setStudentName("Chen Wei");
                a3.setDoctorName("Dr. Lee");
                a3.setAppointmentDate(LocalDate.of(2026, 8, 14));
                a3.setVisitReason("Follow-up");
                a3.setUrgencyLevel("Routine");
                a3.setStatus("NO_SHOW");
                appointmentRepository.save(a3);

                Appointment a4 = new Appointment();
                a4.setStudentName("Divya Singh");
                a4.setDoctorName("Dr. Lee");
                a4.setAppointmentDate(LocalDate.of(2026, 8, 15));
                a4.setVisitReason("Injury");
                a4.setUrgencyLevel("Emergency");
                a4.setStatus("COMPLETED");
                appointmentRepository.save(a4);

                Appointment a5 = new Appointment();
                a5.setStudentName("Elena Prasad");
                a5.setDoctorName("Dr. Patel");
                a5.setAppointmentDate(LocalDate.now());
                a5.setVisitReason("Headache");
                a5.setUrgencyLevel("Routine");
                a5.setStatus("BOOKED");
                appointmentRepository.save(a5);

                Appointment a6 = new Appointment();
                a6.setStudentName("Frank Lal");
                a6.setDoctorName("Dr. Lee");
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