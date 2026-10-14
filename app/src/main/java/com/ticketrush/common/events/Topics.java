package com.ticketrush.common.events;

/** Tên topic Kafka và loại event. Value của message là JSON, header {@code eventType} cho biết loại. */
public final class Topics {

    public static final String ORDER_EVENTS = "ticketrush.order-events";
    public static final String TICKET_EVENTS = "ticketrush.ticket-events";

    public static final String ORDER_PAID = "OrderPaid";
    public static final String TICKETS_ISSUED = "TicketsIssued";
    public static final String TICKET_TRANSFERRED = "TicketTransferred";

    /** Header của message Kafka (relay gắn từ cột outbox_events). */
    public static final String HEADER_EVENT_ID = "eventId";
    public static final String HEADER_EVENT_TYPE = "eventType";

    private Topics() {}
}
