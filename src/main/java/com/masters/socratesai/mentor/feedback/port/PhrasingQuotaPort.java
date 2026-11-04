package com.masters.socratesai.mentor.feedback.port;

/** Admission is durable and conservative: failed or uncertain calls also consume their allowance. */
public interface PhrasingQuotaPort {
    boolean acquire(Long studentId);
}
