package daylightnebula.daylinmicroservices.redis

import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.CompletableFuture

// A class to easily represent a value stored in redis, so it can easily be used
abstract class RedisValue<T: Any>(val key: String, val default: T) {
    abstract fun fromJson(value: JSONObject): T
    abstract fun toJson(value: T): JSONObject

    // function to get a value synchronously from redis
    fun get(): T {
        // get value
        val value = RedisConnection.requestJson(key)

        // process value and return result
        return if (value.isOk()) fromJson(value.unwrap())
            else {
                RedisConnection.logger.error("Failed to get $key from redis with error: ${value.error()}")
                default
            }
    }

    // function to get a value asynchronously from redis
    fun getAsync(): CompletableFuture<T> {
        val future = CompletableFuture<T>()
        RedisConnection.requestJsonAsync(key).whenComplete { value, _ ->
            if (value.isOk()) future.complete(fromJson(value.unwrap()))
            else {
                RedisConnection.logger.error("Failed to get $key from redis with error: ${value.error()}")
                future.complete(default)
            }
        }
        return future
    }

    // function to set a value in redis
    fun set(value: T) = RedisConnection.setJson(key, toJson(value))

    // function to set a value in redis asynchronously
    fun setAsync(value: T) = RedisConnection.setJsonAsync(key, toJson(value))
}

class RedisBoolean(key: String, default: Boolean): RedisValue<Boolean>(key, default) {
    override fun fromJson(value: JSONObject) = value.optBoolean("value") ?: default
    override fun toJson(value: Boolean) = JSONObject().put("value", value)
}

class RedisShort(key: String, default: Short): RedisValue<Short>(key, default) {
    override fun fromJson(value: JSONObject) = value.optInt("value").toShort() ?: default
    override fun toJson(value: Short) = JSONObject().put("value", value)
}

class RedisInt(key: String, default: Int): RedisValue<Int>(key, default) {
    override fun fromJson(value: JSONObject) = value.optInt("value") ?: default
    override fun toJson(value: Int) = JSONObject().put("value", value)
}

class RedisLong(key: String, default: Long): RedisValue<Long>(key, default) {
    override fun fromJson(value: JSONObject) = value.optLong("value") ?: default
    override fun toJson(value: Long) = JSONObject().put("value", value)
}

class RedisFloat(key: String, default: Float): RedisValue<Float>(key, default) {
    override fun fromJson(value: JSONObject) = value.optFloat("value") ?: default
    override fun toJson(value: Float) = JSONObject().put("value", value)
}

class RedisDouble(key: String, default: Double): RedisValue<Double>(key, default) {
    override fun fromJson(value: JSONObject) = value.optDouble("value") ?: default
    override fun toJson(value: Double) = JSONObject().put("value", value)
}

class RedisString(key: String, default: String): RedisValue<String>(key, default) {
    override fun fromJson(value: JSONObject) = value.optString("value") ?: default
    override fun toJson(value: String) = JSONObject().put("value", value)
}

class RedisJSONObject(key: String, default: JSONObject): RedisValue<JSONObject>(key, default) {
    override fun fromJson(value: JSONObject) = value
    override fun toJson(value: JSONObject) = value
}

class RedisJSONArray(key: String, default: JSONArray): RedisValue<JSONArray>(key, default) {
    override fun fromJson(value: JSONObject) = value.optJSONArray("value") ?: default
    override fun toJson(value: JSONArray) = JSONObject().put("value", value)
}
