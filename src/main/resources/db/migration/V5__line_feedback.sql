CREATE TABLE notification_delivery (
    id uuid PRIMARY KEY,
    retry_key uuid NOT NULL UNIQUE,
    recipient_id text NOT NULL,
    payload text NOT NULL CHECK (length(payload) > 0),
    status text NOT NULL CHECK (status IN ('prepared', 'sending', 'accepted', 'rejected', 'uncertain')),
    created_at timestamptz NOT NULL,
    attempted_at timestamptz,
    accepted_at timestamptz,
    request_id text,
    error_code text
);

CREATE TABLE notification_item (
    id uuid PRIMARY KEY,
    delivery_id uuid NOT NULL REFERENCES notification_delivery(id),
    media_type text NOT NULL,
    tmdb_id bigint NOT NULL,
    title text NOT NULL,
    position integer NOT NULL CHECK (position > 0),
    FOREIGN KEY (media_type, tmdb_id) REFERENCES tmdb_candidate(media_type, tmdb_id),
    UNIQUE (delivery_id, media_type, tmdb_id),
    UNIQUE (delivery_id, position)
);

CREATE TABLE line_webhook_event (
    event_id text PRIMARY KEY,
    received_at timestamptz NOT NULL,
    outcome text NOT NULL
);

CREATE TABLE feedback_event (
    event_id text PRIMARY KEY REFERENCES line_webhook_event(event_id),
    item_id uuid NOT NULL REFERENCES notification_item(id),
    action text NOT NULL CHECK (action IN ('interested', 'not_interested', 'watched')),
    occurred_at timestamptz NOT NULL
);

CREATE TABLE user_title_state (
    media_type text NOT NULL,
    tmdb_id bigint NOT NULL,
    action text NOT NULL CHECK (action IN ('interested', 'not_interested', 'watched')),
    event_id text NOT NULL REFERENCES feedback_event(event_id),
    occurred_at timestamptz NOT NULL,
    PRIMARY KEY (media_type, tmdb_id),
    FOREIGN KEY (media_type, tmdb_id) REFERENCES tmdb_candidate(media_type, tmdb_id)
);
