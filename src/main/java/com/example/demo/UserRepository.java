package com.example.demo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);

    // Students whose uploaded receipt is waiting for an admin: not yet approved and
    // not rejected. Selects columns rather than whole users so the receipt PDFs
    // aren't loaded - the admin dashboard polls this.
    // Row: id, username, fullName, feeAmount, receiptUploadedAt, receiptAutoCheckNote
    @Query("SELECT u.id, u.username, u.fullName, u.feeAmount, u.receiptUploadedAt, u.receiptAutoCheckNote FROM User u "
            + "WHERE u.role = 'STUDENT' AND u.feePaid = false AND u.receiptFile IS NOT NULL AND u.receiptRejectionReason IS NULL")
    List<Object[]> findPendingReceipts();
}
