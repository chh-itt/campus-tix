-- KEYS[1] = 库存 key (stock:session:{id})
-- ARGV[1] = 回补数量
-- 返回：1=成功  -1=key 不存在
local stock = redis.call('GET', KEYS[1])
if not stock then return -1 end
redis.call('INCRBY', KEYS[1], tonumber(ARGV[1]))
return 1
