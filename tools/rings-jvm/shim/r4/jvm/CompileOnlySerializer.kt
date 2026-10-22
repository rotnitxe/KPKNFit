package r4.jvm

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/** A compile-only type must never silently change an exercised JSON schema. */
abstract class CompileOnlySerializer<T>(private val symbol: String) : KSerializer<T> {
    init { unavailable("$symbol serializer initialization") }
    override val descriptor: SerialDescriptor get() = unavailable("$symbol serializer descriptor")
    override fun serialize(encoder: Encoder, value: T): Unit = unavailable("$symbol serialize")
    override fun deserialize(decoder: Decoder): T = unavailable("$symbol deserialize")
}
