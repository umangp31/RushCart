-- KEYS[1] = stock:{sku}, ARGV[1] = requested qty
-- Returns remaining stock on success, -1 if insufficient stock.
local current = tonumber(redis.call('GET', KEYS[1]) or '0')
if current < tonumber(ARGV[1]) then
    return -1
end
redis.call('DECRBY', KEYS[1], ARGV[1])
return current - tonumber(ARGV[1])
