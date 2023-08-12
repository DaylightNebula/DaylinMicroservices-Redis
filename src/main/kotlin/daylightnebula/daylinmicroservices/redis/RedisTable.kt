package daylightnebula.daylinmicroservices.redis

import daylightnebula.daylinmicroservices.serializables.Result
import org.json.JSONArray
import org.json.JSONObject
import java.util.*
import java.util.concurrent.CompletableFuture

abstract class RedisTableEntry(val uuid: UUID) {
    abstract fun toJson(): JSONObject
}

fun <T: RedisTableEntry> redisTable(name: String, fromJson: (uuid: UUID, json: JSONObject) -> T) = RedisTable<T>(name, fromJson)
class RedisTable<T: RedisTableEntry>(val name: String, val fromJson: (uuid: UUID, json: JSONObject) -> T) {
    // registry of all uuids in this table
    val registry = RedisJSONArray(name, JSONArray())

    // functions to get all uuids in table
    fun getAllIDsAsync(): CompletableFuture<List<UUID>> {
        val future = CompletableFuture<List<UUID>>()
        registry.getAsync().whenComplete { array, _ ->
            future.complete(array.map { UUID.fromString(it as String) })
        }
        return future
    }
    fun getAllIDs() = registry.get().map { UUID.fromString(it as String) }

    // function to get all table entries
    fun getAll(): List<T> = getAllIDs().mapNotNull {
        val uuid = it as? UUID ?: return@mapNotNull null
        val request = RedisConnection.requestJson(uuid.toString())
        if (request.isOk()) fromJson(uuid, request.unwrap()) else null
    }

    // function to asynchronously loop through all entries in table
    fun forEachAsync(callback: (entry: T) -> Unit) {
        getAllIDsAsync().whenComplete { uuids, _ ->
            uuids.forEach {
                RedisConnection.requestJsonAsync(it.toString()).whenComplete { result, _ ->
                    if (result.isOk()) callback(fromJson(it, result.unwrap()))
                    else RedisConnection.logger.error("Table entry request (uuid = $it) failed with error: ${result.error()}")
                }
            }
        }
    }

    // functions to query table entries, operates as above but with a filter
    fun queryAll(filter: (entry: T) -> Boolean) = getAll().filter { entry -> filter(entry) }
    fun queryForEachAsync(filter: (entry: T) -> Boolean, callback: (entry: T) -> Unit) {
        getAllIDsAsync().whenComplete { uuids, _ ->
            uuids.forEach {
                RedisConnection.requestJsonAsync(it.toString()).whenComplete { result, _ ->
                    if (result.isOk())  {
                        val entry = fromJson(it, result.unwrap())
                        if (filter(entry)) callback(entry)
                    } else RedisConnection.logger.error("Table entry request (uuid = $it) failed with error: ${result.error()}")
                }
            }
        }
    }

    // functions to get specific table entries by UUID
    fun getEntry(uuid: UUID): Result<T> {
        val json = RedisConnection.requestJson(uuid.toString())
        return if (json.isOk()) Result.Ok(fromJson(uuid, json.unwrap()))
        else Result.Error(json.error())
    }
    fun getEntryAsync(uuid: UUID): CompletableFuture<Result<T>> {
        val future = CompletableFuture<Result<T>>()
        RedisConnection.requestJsonAsync(uuid.toString()).whenComplete { json, throwable ->
            if (throwable != null) future.complete(Result.Error(throwable.message ?: "No error message"))
            else if (json.isOk()) future.complete(Result.Ok(fromJson(uuid, json.unwrap())))
            else future.complete(Result.Error(json.error()))
        }
        return future
    }

    // add or update an entry to the table by updating its value in redis and making sure it is in the registry
    fun insertOrUpdate(entry: T) = registry.getAsync().whenComplete { curRegistry, _ ->
        // make sure registry contains this uuid
        if (!curRegistry.contains(entry.uuid)) {
            curRegistry.put(entry.uuid)
            registry.setAsync(curRegistry)
        }

        // insert or update entry in redis
        RedisConnection.setJsonAsync(entry.uuid.toString(), entry.toJson())
    }

    // functions to remove an entry from the table
    fun remove(entry: T) = remove(entry.uuid)
    fun remove(uuid: UUID) = registry.getAsync().whenComplete { curRegistry, _ ->
        // remove registry if necessary
        if (curRegistry.contains(uuid)) {
            curRegistry.remove(curRegistry.indexOf(uuid))
            registry.setAsync(curRegistry)
        }

        // remove entry
        RedisConnection.removeAsync(uuid.toString())
    }
}