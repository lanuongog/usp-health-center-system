package com.example.demo;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.time.LocalDate;

@Configuration
public class DataSeeder {

    @Bean
    public CommandLineRunner seedData(UserRepository userRepository,
                                       AppointmentRepository appointmentRepository,
                                       PasswordEncoder passwordEncoder) {
        return args -> {
            if (userRepository.findByUsername("admin").isEmpty()) {
                User admin = new User("admin", passwordEncoder.encode("admin123"), "ADMIN");
                userRepository.save(admin);
                System.out.println("Seeded default admin user: admin / admin123");
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

                System.out.println("Seeded 4 sample appointments.");
            }
        };
    }
}
