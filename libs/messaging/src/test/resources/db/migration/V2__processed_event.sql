CREATE TABLE processed_event (
    consumer     varchar(100) NOT NULL,
    event_id     uuid         NOT NULL,
    processed_at timestamptz  NOT NULL DEFAULT now(),
    PRIMARY KEY (consumer, event_id)
);
