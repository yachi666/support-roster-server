CREATE TABLE IF NOT EXISTS support_team_contact (
    id BIGINT PRIMARY KEY,
    team_name VARCHAR(255) NOT NULL,
    team_email VARCHAR(255) NOT NULL,
    xmatter_group VARCHAR(255),
    gsd_group VARCHAR(255),
    eim_id VARCHAR(255),
    other_info TEXT,
    created_by_account_id BIGINT REFERENCES workspace_account (id),
    updated_by_account_id BIGINT REFERENCES workspace_account (id),
    deleted INTEGER NOT NULL DEFAULT 0,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_support_team_contact_team_email
    ON support_team_contact (LOWER(BTRIM(team_email)))
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_support_team_contact_team_name
    ON support_team_contact (LOWER(BTRIM(team_name)))
    WHERE deleted = 0;

CREATE TABLE IF NOT EXISTS support_team_contact_tag (
    id BIGINT PRIMARY KEY,
    contact_id BIGINT NOT NULL REFERENCES support_team_contact (id),
    tag VARCHAR(100) NOT NULL,
    sort_order INTEGER NOT NULL DEFAULT 0,
    deleted INTEGER NOT NULL DEFAULT 0,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_support_team_contact_tag_contact
    ON support_team_contact_tag (contact_id, sort_order)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_support_team_contact_tag_value
    ON support_team_contact_tag (LOWER(BTRIM(tag)))
    WHERE deleted = 0;

CREATE TABLE IF NOT EXISTS support_team_contact_staff (
    id BIGINT PRIMARY KEY,
    contact_id BIGINT NOT NULL REFERENCES support_team_contact (id),
    staff_code VARCHAR(128) NOT NULL,
    deleted INTEGER NOT NULL DEFAULT 0,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_support_team_contact_staff_contact
    ON support_team_contact_staff (contact_id, staff_code)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_support_team_contact_staff_code
    ON support_team_contact_staff (LOWER(BTRIM(staff_code)))
    WHERE deleted = 0;

CREATE TABLE IF NOT EXISTS support_team_contact_link (
    id BIGINT PRIMARY KEY,
    contact_id BIGINT NOT NULL REFERENCES support_team_contact (id),
    label VARCHAR(100) NOT NULL,
    url TEXT NOT NULL,
    sort_order INTEGER NOT NULL DEFAULT 0,
    deleted INTEGER NOT NULL DEFAULT 0,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_support_team_contact_link_contact
    ON support_team_contact_link (contact_id, sort_order)
    WHERE deleted = 0;
