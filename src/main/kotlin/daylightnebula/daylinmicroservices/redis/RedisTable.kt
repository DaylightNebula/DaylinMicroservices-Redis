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
    private val table = RedisJSONObject(name, JSONObject())

    // functions to get all uuids in table
    fun getAllIDsAsync(): CompletableFuture<List<UUID>> {
        val future = CompletableFuture<List<UUID>>()
        table.getAsync().whenComplete { obj, _ ->
            future.complete(obj.keySet().map { UUID.fromString(it as String) })
        }
        return future
    }
    fun getAllIDs() = table.get().keySet().map { UUID.fromString(it as String) }

    // function to get all table entries
    fun getAll(): List<T> {
        val json = table.get()
        return json.keySet().map { key -> fromJson(UUID.fromString(key), json.getJSONObject(key)) }
    }

    // function to asynchronously loop through all entries in table
    fun getAllAsync(): CompletableFuture<List<T>> {
        val future = CompletableFuture<List<T>>()
        table.getAsync().whenComplete { json, _ ->
            future.complete(json.keySet().map { key -> fromJson(UUID.fromString(key), json.getJSONObject(key)) })
        }
        return future
    }

    // functions to query table entries, operates as above but with a filter
    fun queryAll(filter: (entry: T) -> Boolean) = getAll().filter(filter)
    fun queryAsync(filter: (entry: T) -> Boolean): CompletableFuture<List<T>> {
        val future = CompletableFuture<List<T>>()
        table.getAsync().whenComplete { json, _ ->
            future.complete(
                json.keySet()
                    .map { key -> fromJson(UUID.fromString(key), json.getJSONObject(key)) }
                    .filter(filter)
            )
        }
        return future
    }

    // functions to get specific table entries by UUID
    fun getEntry(uuid: UUID): Result<T> {
        val json = table.get().optJSONObject(uuid.toString())
            ?: return Result.Error<T>("No entry with uuid $uuid")
        return Result.Ok(fromJson(uuid, json))
    }
    fun getEntryAsync(uuid: UUID): CompletableFuture<Result<T>> {
        val future = CompletableFuture<Result<T>>()
        table.getAsync().whenComplete { json, _ ->
            if (json == null || !json.keySet().contains(uuid.toString()))
                future.complete(Result.Error("No entry with uuid $uuid"))
            else future.complete(Result.Ok(fromJson(uuid, json)))
        }
        return future
    }

    // add or update an entry to the table by updating its value in redis and making sure it is in the registry
    fun insertOrUpdate(entry: T) = table.set(table.get().put(entry.uuid.toString(), entry.toJson()))
    fun insertOrUpdateAsync(entry: T) = table.getAsync().whenComplete { json, _ ->
        json.put(entry.uuid.toString(), entry.toJson())
        table.setAsync(json)
    }

    // functions to remove an entry from the table
    fun removeAsync(entry: T) = removeAsync(entry.uuid)
    fun removeAsync(uuid: UUID) = table.getAsync().whenComplete { json, _ ->
        json.remove(uuid.toString())
        table.set(json)
    }
    fun remove(entry: T) = remove(entry.uuid)
    fun remove(uuid: UUID) {
        val json = table.get()
        json.remove(uuid.toString())
        table.set(json)
    }
}