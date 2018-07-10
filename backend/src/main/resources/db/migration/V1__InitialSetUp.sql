CREATE TABLE IF NOT EXISTS schedule (
  id SERIAL PRIMARY KEY,
  schedule_train_id     VARCHAR(15)    NOT NULL,
  sequence SMALLINT NOT NULL,
  service_code    VARCHAR(15)   NOT NULL,
  tiploc_code VARCHAR(15) NOT NULL,
  stanox_code VARCHAR(15) NULL,
  location_type VARCHAR(5) NOT NULL,
  scheduled_arrival_time TIME NULL,
  scheduled_departure_time TIME NULL,
  days_run VARCHAR(8) NOT NULL,
  schedule_start DATE NOT NULL,
  schedule_end DATE NOT NULL,
  CONSTRAINT unique_cons UNIQUE(schedule_train_id, schedule_start, schedule_end, sequence)
);
