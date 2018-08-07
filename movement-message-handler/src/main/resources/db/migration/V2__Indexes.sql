CREATE INDEX schedule_train_id_idx ON schedule (schedule_train_id);
CREATE INDEX sequence_idx ON schedule (sequence);
CREATE INDEX polyline_tiploc_idx ON polylines (from_tiploc_code, to_tiploc_code);
