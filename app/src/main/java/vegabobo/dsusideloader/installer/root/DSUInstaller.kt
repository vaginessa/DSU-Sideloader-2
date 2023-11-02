package com.example.gsiinstaller

import android.app.Application
import android.os.ParcelFileDescriptor
import android.os.SharedMemory
import android.util.Log
import java.io.InputStream
import java.nio.ByteBuffer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.lsposed.hiddenapibypass.HiddenApiBypass

class GSIInstaller(
    private val application: Application,
    private val gsiImage: InputStream,
    private val partitions: List<String>,
    private val installationJob: Job = Job(),
    private val onInstallationError: (errorInfo: String) -> Unit,
    private val onInstallationProgressUpdate: (progress: Float, partition: String) -> Unit,
    private val onInstallationSuccess: () -> Unit
) : () -> Unit {

    private val tag = this.javaClass.simpleName

    object Constants {
        const val SHARED_MEM_SIZE: Int = 524288
    }

    private class MappedMemoryBuffer(var mBuffer: ByteBuffer?) : AutoCloseable {
        override fun close() {
            if (mBuffer != null) {
                SharedMemory.unmap(mBuffer!!)
                mBuffer = null
            }
        }
    }

    private fun getFdDup(sharedMemory: SharedMemory): ParcelFileDescriptor {
        return HiddenApiBypass.invoke(
            sharedMemory.javaClass,
            sharedMemory,
            "getFdDup",
        ) as ParcelFileDescriptor
    }

    private fun publishProgress(bytesRead: Long, totalBytes: Long, partition: String) {
        val progress = if (totalBytes != 0L) bytesRead.toFloat() / totalBytes.toFloat() else 0F
        onInstallationProgressUpdate(progress, partition)
    }

    private fun installImage(partition: String) {
        createNewPartition(partition)

        SharedMemory.create("gsi_buffer_$partition", Constants.SHARED_MEM_SIZE)
            .use { sharedMemory ->
                MappedMemoryBuffer(sharedMemory.mapReadWrite()).use { mappedBuffer ->
                    val fdDup = getFdDup(sharedMemory)
                    val buffer = mappedBuffer.mBuffer
                    val readBuffer = ByteArray(sharedMemory.size)

                    var bytesRead: Long = 0
                    var numBytesRead: Int

                    while (gsiImage.read(readBuffer).also { numBytesRead = it } != -1) {
                        if (installationJob.isCancelled) {
                            return
                        }

                        buffer!!.position(0)
                        buffer.put(readBuffer, 0, numBytesRead)

                        bytesRead += numBytesRead
                        publishProgress(bytesRead, sharedMemory.size.toLong(), partition)
                    }

                    publishProgress(sharedMemory.size.toLong(), sharedMemory.size.toLong(), partition)
                }
            }

        if (!closePartition()) {
            val errorMessage = "Failed to install $partition partition"
            Log.e(tag, errorMessage)
            onInstallationError(errorMessage)
        } else {
            val partitionSize = gsiImage.available().toLong()
            Log.d(tag, "Partition $partition installed, partitionSize: $partitionSize")
        }
    }

    private fun installImages() {
        for (partition in partitions) {
            installImage(partition)
            if (installationJob.isCancelled) {
                return
            }
        }
    }

    private fun startInstallation() {
        installImages()

        if (!installationJob.isCancelled) {
            Log.d(tag, "Installation finished successfully.")
            onInstallationSuccess()
        }
    }

    override fun invoke() {
        startInstallation()
    }
}
