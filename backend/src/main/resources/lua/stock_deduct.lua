-- KEYS[1] = 库存 key (stock:session:{id})
-- ARGV[1] = 本次扣减数量
-- 返回：1=成功  0=库存不足  -1=key 不存在
local stock = redis.call('GET', KEYS[1])
if not stock then return -1 end
stock = tonumber(stock)
local need = tonumber(ARGV[1])
if stock < need then return 0 end
redis.call('DECRBY', KEYS[1], need)
return 1
