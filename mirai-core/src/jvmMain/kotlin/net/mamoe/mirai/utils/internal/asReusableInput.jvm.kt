package net.mamoe.mirai.utils.internal

import io.ktor.utils.io.ByteWriteChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import net.mamoe.mirai.message.data.toLongUnsigned
import java.io.File
import java.io.InputStream

internal actual fun ByteArray.asReusableInput(): ReusableInput {
    return object : ReusableInput {
        override val md5: ByteArray = md5()
        override val size: Long get() = this@asReusableInput.size.toLongUnsigned()

        override fun chunkedFlow(sizePerPacket: Int): ChunkedFlowSession<ChunkedInput> {
            return object : ChunkedFlowSession<ChunkedInput> {
                override val flow: Flow<ChunkedInput> = inputStream().chunkedFlow(sizePerPacket)

                override fun close() {
                    // nothing to do
                }
            }
        }

        override suspend fun writeTo(out: ByteWriteChannel): Long {
            out.writeFully(this@asReusableInput, 0, this@asReusableInput.size)
            out.flush()
            return this@asReusableInput.size.toLongUnsigned()
        }
    }
}

internal fun File.asReusableInput(deleteOnClose: Boolean): ReusableInput {
    return object : ReusableInput {
        override val md5: ByteArray = inputStream().use { it.md5() }
        override val size: Long get() = length()

        override fun chunkedFlow(sizePerPacket: Int): ChunkedFlowSession<ChunkedInput> {
            val stream = inputStream()
            return object : ChunkedFlowSession<ChunkedInput> {
                override val flow: Flow<ChunkedInput> = stream.chunkedFlow(sizePerPacket)
                override fun close() {
                    stream.close()
                    if (deleteOnClose) this@asReusableInput.delete()
                }
            }
        }

        override suspend fun writeTo(out: ByteWriteChannel): Long {
            return inputStream().use { it.copyTo(out) }
        }
    }
}

internal fun File.asReusableInput(deleteOnClose: Boolean, md5: ByteArray): ReusableInput {
    return object : ReusableInput {
        override val md5: ByteArray get() = md5
        override val size: Long get() = length()

        override fun chunkedFlow(sizePerPacket: Int): ChunkedFlowSession<ChunkedInput> {
            val stream = inputStream()
            return object : ChunkedFlowSession<ChunkedInput> {
                override val flow: Flow<ChunkedInput> = stream.chunkedFlow(sizePerPacket)
                override fun close() {
                    stream.close()
                    if (deleteOnClose) this@asReusableInput.delete()
                }
            }
        }

        override suspend fun writeTo(out: ByteWriteChannel): Long {
            return inputStream().use { it.copyTo(out) }
        }
    }
}

private suspend fun InputStream.copyTo(out: ByteWriteChannel): Long = withContext(Dispatchers.IO) {
    var bytesCopied: Long = 0

    ByteArrayPool.useInstance { buffer ->
        var bytes = read(buffer)
        while (bytes >= 0) {
            out.writeFully(buffer, 0, bytes)
            bytesCopied += bytes
            bytes = read(buffer)
        }
    }

    out.flush()

    return@withContext bytesCopied
}