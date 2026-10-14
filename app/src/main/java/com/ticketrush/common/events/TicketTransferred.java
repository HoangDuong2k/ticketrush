package com.ticketrush.common.events;

import java.time.Instant;
import java.util.UUID;

public record TicketTransferred(UUID ticketId, UUID eventId, UUID fromUserId, UUID toUserId, int tokenVersion,
        Instant transferredAt) {}
