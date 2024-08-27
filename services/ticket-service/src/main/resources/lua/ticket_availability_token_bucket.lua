-- keys[2]是我们当前用户购买的出发站点和达到的站点
local inputString = KEYS[2]
local actualKey = inputString
local colonIndex = string.find(actualKey, ":")
if colonIndex ~= nil then
    actualKey = string.sub(actualKey, colonIndex + 1)
end
-- actualkey是北京南-南京南
-- jsonArray是需要扣减的座位类型以及对应的座位的数量
local jsonArrayStr = ARGV[1]
local jsonArray = cjson.decode(jsonArrayStr)

local result = {}
local tokenIsNull = false
local tokenIsNullSeatTypeCounts = {}

for index, jsonObj in ipairs(jsonArray) do
    local seatType = tonumber(jsonObj.seatType)
    local count = tonumber(jsonObj.count)
    local actualInnerHashKey = actualKey .. "_" .. seatType
--     判断指定类型的座位的余量是否超过购买的人数
    local ticketSeatAvailabilityTokenValue = tonumber(redis.call('hget', KEYS[1], tostring(actualInnerHashKey)))
    if ticketSeatAvailabilityTokenValue < count then
        tokenIsNull = true
        table.insert(tokenIsNullSeatTypeCounts, seatType .. "_" .. count)
    end
end

result['tokenIsNull'] = tokenIsNull
if tokenIsNull then
    result['tokenIsNullSeatTypeCounts'] = tokenIsNullSeatTypeCounts
    return cjson.encode(result)
end

-- 通过上面的判断得知当前的抢票余票充足，可以开始抢票环节
local alongJsonArrayStr = ARGV[2]
-- 我们和当前站点相关的所有的出发点和终点的站点
local alongJsonArray = cjson.decode(alongJsonArrayStr)

-- 双层的for循环
-- 第一个jsonAraay中存放的是我们的当前的座位的类型以及对应的扣减的数量
for index, jsonObj in ipairs(jsonArray) do
    local seatType = tonumber(jsonObj.seatType)
    local count = tonumber(jsonObj.count)
--     qu遍历每一个与之相关的节点，并对当前的节点上所有的车票的数量进行扣减
    for indexTwo, alongJsonObj in ipairs(alongJsonArray) do
        local startStation = tostring(alongJsonObj.startStation)
        local endStation = tostring(alongJsonObj.endStation)
        local actualInnerHashKey = startStation .. "_" .. endStation .. "_" .. seatType
        redis.call('hincrby', KEYS[1], tostring(actualInnerHashKey), -count)
    end
end

return cjson.encode(result)
