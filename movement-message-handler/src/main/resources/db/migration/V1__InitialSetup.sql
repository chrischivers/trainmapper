CREATE TABLE IF NOT EXISTS schedule (
  id SERIAL PRIMARY KEY,
  schedule_train_id     VARCHAR(15)    NOT NULL,
  sequence SMALLINT NOT NULL,
  service_code    VARCHAR(15)   NOT NULL,
  tiploc_code VARCHAR(15) NOT NULL,
  location_type VARCHAR(5) NOT NULL,
  scheduled_arrival_time TIME NULL,
  scheduled_departure_time TIME NULL,
  days_run VARCHAR(8) NOT NULL,
  schedule_start DATE NOT NULL,
  schedule_end DATE NOT NULL,
  polyline_id INTEGER NULL,
  CONSTRAINT unique_cons_schedule UNIQUE(schedule_train_id, schedule_start, schedule_end, sequence)
);

CREATE TABLE IF NOT EXISTS polylines (
  id SERIAL PRIMARY KEY,
  from_tiploc_code VARCHAR(15) NOT  NULL,
  to_tiploc_code VARCHAR(15) NOT NULL,
  polyline VARCHAR NOT NULL,
  CONSTRAINT unique_cons_polylines UNIQUE(from_tiploc_code, to_tiploc_code)
)