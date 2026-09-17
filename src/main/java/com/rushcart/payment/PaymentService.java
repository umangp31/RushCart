package com.rushcart.payment;

import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Mocked payment step (§1 non-goals) — no real PSP integration. The event flow around
 * payment (success path here; failure/timeout via {@code RollbackWorker}) is what's
 * under test, not payment processing itself.
 */
@Service
public class PaymentService {

    public boolean confirm(UUID orderId) {
        return true;
    }
}
