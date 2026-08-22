CREATE TABLE IF NOT EXISTS app_user (
    id            BIGSERIAL PRIMARY KEY,
    username      VARCHAR(50)  NOT NULL UNIQUE,
    email         VARCHAR(100) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role          VARCHAR(20)  NOT NULL DEFAULT 'USER'
);

CREATE TABLE IF NOT EXISTS show (
    id          BIGSERIAL PRIMARY KEY,
    title       VARCHAR(200) NOT NULL,
    venue       VARCHAR(200) NOT NULL,
    start_time  TIMESTAMP    NOT NULL
);

CREATE TABLE IF NOT EXISTS seat (
    id          BIGSERIAL PRIMARY KEY,
    seat_number VARCHAR(20)  NOT NULL,
    status      VARCHAR(20)  NOT NULL DEFAULT 'AVAILABLE',
    show_id     BIGINT       NOT NULL REFERENCES show(id) ON DELETE CASCADE,
    version     BIGINT       NOT NULL DEFAULT 0,  
    held_by     VARCHAR(50),                       
    held_at     TIMESTAMP                          
);

CREATE INDEX IF NOT EXISTS idx_seat_show_status ON seat(show_id, status);

CREATE INDEX IF NOT EXISTS idx_seat_held ON seat(held_at) WHERE status = 'HELD';

CREATE TABLE IF NOT EXISTS booking (
    id               BIGSERIAL PRIMARY KEY,
    user_id          BIGINT       NOT NULL REFERENCES app_user(id),
    status           VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    created_at       TIMESTAMP    NOT NULL DEFAULT NOW(),
    idempotency_key  VARCHAR(100) UNIQUE   
);

CREATE TABLE IF NOT EXISTS booking_seat (
    id                BIGSERIAL PRIMARY KEY,
    booking_id        BIGINT NOT NULL REFERENCES booking(id) ON DELETE CASCADE,
    seat_id           BIGINT NOT NULL REFERENCES seat(id),
    price_at_booking  NUMERIC(10, 2) NOT NULL DEFAULT 0.00,
    CONSTRAINT uc_bookingseat_booking_seat UNIQUE (booking_id, seat_id)
);

CREATE TABLE IF NOT EXISTS idempotency_record (
    id               BIGSERIAL    PRIMARY KEY,
    idempotency_key  VARCHAR(100) NOT NULL UNIQUE,
    booking_id       BIGINT,
    response_status  INT          NOT NULL,
    response_body    TEXT,
    created_at       TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_idempotency_key ON idempotency_record(idempotency_key);
