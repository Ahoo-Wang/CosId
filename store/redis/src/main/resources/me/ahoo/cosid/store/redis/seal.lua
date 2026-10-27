-- RangeStore.seal (06-stores.md §2.2). KEYS: cosid:{key}:sealed. The counter is kept and never deleted (D23); the
-- marker alone stops reserve, also for a key that does not exist yet (S6).
redis.call('SET', KEYS[1], '1')
return {'sealed'}
