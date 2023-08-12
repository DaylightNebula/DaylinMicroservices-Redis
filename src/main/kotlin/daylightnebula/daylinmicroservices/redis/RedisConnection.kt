package daylightnebula.daylinmicroservices.redis

import daylightnebula.daylinmicroservices.serializables.Result
import io.lettuce.core.RedisClient
import io.lettuce.core.RedisURI
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.async.RedisAsyncCommands
import io.lettuce.core.api.sync.RedisCommands
import mu.KotlinLogging
import org.json.JSONObject
import java.awt.event.TextEvent
import java.lang.Thread.sleep
import java.time.Duration
import java.util.UUID
import java.util.concurrent.CompletableFuture
import kotlin.concurrent.thread

object RedisConnection {
    val logger = KotlinLogging.logger("Redis Connection")

    // redis values
    lateinit var redis: RedisClient
    lateinit var connection: StatefulRedisConnection<String, String>
    lateinit var syncCommands: RedisCommands<String, String>
    lateinit var asyncCommands: RedisAsyncCommands<String, String>

    // function to initialize a redis connection
    fun init(
        redisAddress: String = System.getenv("redisAddress") ?: "localhost",
        redisPort: Int = System.getenv("redisPort")?.toInt() ?: 6379,
        redisUsername: String = System.getenv("redisUsername") ?: "",
        redisPassword: String = System.getenv("redisPassword") ?: "",
        redisDatabase: Int = System.getenv("redisDatabase")?.toInt() ?: -1,
        redisTimeout: Long = System.getenv("redisTimeout")?.toLong() ?: 5000
    ) {
        // build initial redis URI
        val builder = RedisURI.Builder
            .redis(redisAddress, redisPort)
            .withTimeout(Duration.ofMillis(redisTimeout))

        // if a database is given, add database info
        if (redisDatabase != -1)
            builder.withDatabase(redisDatabase)

        // if a username and password is given, add authentication to redis uri
        if (redisUsername.isNotEmpty() && redisPassword.isNotEmpty())
            builder.withAuthentication(redisUsername, redisPassword)

        // build redis
        redis = RedisClient.create(builder.build())
        connection = redis.connect()
        syncCommands = connection.sync()
        asyncCommands = connection.async()

        // add shutdown handler
        Runtime.getRuntime().addShutdownHook(thread(start = false) {
            connection.close()
            redis.shutdown()
        })
    }

    // functions to check if a client is initialized
    fun isInitialized() = this::redis.isInitialized

    // function that makes below functions that return results easier
    // internal is run only if redis is initialized
    fun resultIfInitialized(internal: () -> Result<JSONObject>): Result<JSONObject> =
        if (!isInitialized()) Result.Error("Redis connection not initialized!")
        else internal()

    // function that makes below functions that return future results easier
    // internal is run only if redis is initialized
    fun asyncResultIfInitialized(
        internal: (future: CompletableFuture<Result<JSONObject>>) -> Unit
    ): CompletableFuture<Result<JSONObject>> {
        val future = CompletableFuture<Result<JSONObject>>()
        if (!isInitialized()) future.complete(Result.Error("Redis connection not initialized"))
        else {
            internal(future)
        }
        return future
    }

    // functions to process a given nullable string into a result json object
    private fun processRequest(key: String, value: String?): Result<JSONObject> {
        // if value is empty, return empty error
        if (value.isNullOrEmpty()) return Result.Error("Redis get with key $key returned nothing")

        // return result based on if json conversion exists
        return try {
            val json = JSONObject(value)
            Result.Ok(json)
        } catch (ex: Exception) {
            Result.Error("Could not convert string to json: $value")
        }
    }

    // sync and async functions to request a value from redis with a given key
    fun requestJson(key: String) = resultIfInitialized { processRequest(key, syncCommands.get(key)) }
    fun requestJsonAsync(key: String) =
        asyncResultIfInitialized { future ->
            asyncCommands.get(key).whenComplete { value, throwable ->
                if (throwable != null) future.complete(Result.Error(throwable.message ?: "Error with no message"))
                else future.complete(processRequest(key, value))
            }
        }

    // sync and async functions to insert key and value into redis database
    fun setJson(key: String, value: JSONObject) = resultIfInitialized { processRequest(key, syncCommands.set(key, value.toString())) }
    fun setJsonAsync(key: String, value: JSONObject) =
        asyncResultIfInitialized { future ->
            asyncCommands.set(key, value.toString()).whenComplete { value, throwable ->
                if (throwable != null) future.complete(Result.Error(throwable.message ?: "Error with no message"))
                else future.complete(processRequest(key, value))
            }
        }

    // functions to remove a key from redis
    fun remove(key: String) = resultIfInitialized {
        val result = syncCommands.del(key)
        Result.Ok(JSONObject().put("delete_result", result))
    }
    fun removeAsync(key: String) = asyncResultIfInitialized { future ->
        asyncCommands.del().whenComplete { result, throwable ->
            if (throwable != null) future.complete(Result.Error(throwable.message ?: "Error with no message"))
            else future.complete(Result.Ok(JSONObject().put("delete_result", result)))
        }
    }
}