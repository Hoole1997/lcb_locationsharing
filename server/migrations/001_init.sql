CREATE TABLE IF NOT EXISTS devices (
    device_id UUID PRIMARY KEY,
    device_name TEXT NOT NULL CHECK (char_length(device_name) BETWEEN 1 AND 80),
    invite_code VARCHAR(12) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_name = 'pairings'
          AND column_name = 'owner_device_id'
    ) THEN
        CREATE TABLE pairings_new (
            id UUID PRIMARY KEY,
            device_a_id UUID NOT NULL REFERENCES devices(device_id) ON DELETE CASCADE,
            device_b_id UUID NOT NULL REFERENCES devices(device_id) ON DELETE CASCADE,
            initiator_device_id UUID NOT NULL REFERENCES devices(device_id) ON DELETE CASCADE,
            status TEXT NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'revoked')),
            created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
            updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
            CHECK (device_a_id < device_b_id),
            CHECK (initiator_device_id = device_a_id OR initiator_device_id = device_b_id)
        );

        INSERT INTO pairings_new
            (id, device_a_id, device_b_id, initiator_device_id, status, created_at, updated_at)
        SELECT DISTINCT ON (
                LEAST(owner_device_id, viewer_device_id),
                GREATEST(owner_device_id, viewer_device_id)
            )
            id,
            LEAST(owner_device_id, viewer_device_id),
            GREATEST(owner_device_id, viewer_device_id),
            viewer_device_id,
            status,
            created_at,
            updated_at
        FROM pairings
        WHERE owner_device_id <> viewer_device_id
        ORDER BY
            LEAST(owner_device_id, viewer_device_id),
            GREATEST(owner_device_id, viewer_device_id),
            CASE WHEN status = 'active' THEN 0 ELSE 1 END,
            updated_at DESC;

        DROP TABLE pairings;
        ALTER TABLE pairings_new RENAME TO pairings;
    END IF;
END $$;

CREATE TABLE IF NOT EXISTS pairings (
    id UUID PRIMARY KEY,
    device_a_id UUID NOT NULL REFERENCES devices(device_id) ON DELETE CASCADE,
    device_b_id UUID NOT NULL REFERENCES devices(device_id) ON DELETE CASCADE,
    initiator_device_id UUID NOT NULL REFERENCES devices(device_id) ON DELETE CASCADE,
    status TEXT NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'revoked')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (device_a_id < device_b_id),
    CHECK (initiator_device_id = device_a_id OR initiator_device_id = device_b_id)
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_pairings_device_pair
    ON pairings(device_a_id, device_b_id);

CREATE INDEX IF NOT EXISTS idx_pairings_device_a_status
    ON pairings(device_a_id, status);

CREATE INDEX IF NOT EXISTS idx_pairings_device_b_status
    ON pairings(device_b_id, status);

CREATE TABLE IF NOT EXISTS friend_profiles (
    owner_device_id UUID NOT NULL REFERENCES devices(device_id) ON DELETE CASCADE,
    friend_device_id UUID NOT NULL REFERENCES devices(device_id) ON DELETE CASCADE,
    display_name TEXT CHECK (display_name IS NULL OR char_length(display_name) <= 80),
    gender TEXT CHECK (gender IS NULL OR gender IN ('male', 'female')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (owner_device_id, friend_device_id),
    CHECK (owner_device_id <> friend_device_id)
);

CREATE INDEX IF NOT EXISTS idx_friend_profiles_friend
    ON friend_profiles(friend_device_id);

CREATE TABLE IF NOT EXISTS latest_locations (
    device_id UUID PRIMARY KEY REFERENCES devices(device_id) ON DELETE CASCADE,
    lat DOUBLE PRECISION NOT NULL CHECK (lat BETWEEN -90 AND 90),
    lng DOUBLE PRECISION NOT NULL CHECK (lng BETWEEN -180 AND 180),
    accuracy DOUBLE PRECISION CHECK (accuracy IS NULL OR accuracy >= 0),
    speed DOUBLE PRECISION CHECK (speed IS NULL OR speed >= 0),
    bearing DOUBLE PRECISION CHECK (bearing IS NULL OR (bearing >= 0 AND bearing < 360)),
    battery INTEGER CHECK (battery IS NULL OR (battery >= 0 AND battery <= 100)),
    address TEXT CHECK (address IS NULL OR char_length(address) <= 300),
    recorded_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE latest_locations
    ADD COLUMN IF NOT EXISTS address TEXT;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'latest_locations_address_length'
    ) THEN
        ALTER TABLE latest_locations
            ADD CONSTRAINT latest_locations_address_length
            CHECK (address IS NULL OR char_length(address) <= 300);
    END IF;
END $$;
