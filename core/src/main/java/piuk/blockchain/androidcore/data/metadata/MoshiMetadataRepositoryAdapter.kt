package piuk.blockchain.androidcore.data.metadata

import com.blockchain.metadata.MetadataRepository
import com.blockchain.serialization.JsonSerializable
import com.squareup.moshi.Moshi
import io.reactivex.Maybe

internal class MoshiMetadataRepositoryAdapter(
    private val metadataManager: MetadataManager,
    private val moshi: Moshi
) : MetadataRepository {

    override fun <T : JsonSerializable> loadMetadata(metadataType: Int, clazz: Class<T>): Maybe<T> =
        metadataManager.fetchMetadata(metadataType).firstElement()
            .filter { it.isPresent }
            .map { it.get() }
            .map {
                adapter(clazz).fromJson(it) ?: throw IllegalStateException("Error parsing JSON")
            }

    override fun <T : JsonSerializable> saveMetadata(data: T, clazz: Class<T>, metadataType: Int) =
        metadataManager.saveToMetadata(adapter(clazz).toJson(data), metadataType)

    private fun <T : JsonSerializable> adapter(clazz: Class<T>) = moshi.adapter(clazz)
}
