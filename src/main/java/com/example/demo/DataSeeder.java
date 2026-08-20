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
                DoctorSchedule s1 = new DoctorSchedule();
                s1.setDoctorName("Dr. Patel");
                s1.setScheduleDate(LocalDate.now().plusDays(1));
                s1.setTimeSlot("09:00 AM - 09:30 AM");
                scheduleRepository.save(s1);

                DoctorSchedule s2 = new DoctorSchedule();
                s2.setDoctorName("Dr. Patel");
                s2.setScheduleDate(LocalDate.now().plusDays(1));
                s2.setTimeSlot("09:30 AM - 10:00 AM");
                scheduleRepository.save(s2);

                DoctorSchedule s3 = new DoctorSchedule();
                s3.setDoctorName("Dr. Lee");
                s3.setScheduleDate(LocalDate.now().plusDays(1));
                s3.setTimeSlot("10:00 AM - 10:30 AM");
                scheduleRepository.save(s3);

                System.out.println("Seeded 3 doctor availability slots.");
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