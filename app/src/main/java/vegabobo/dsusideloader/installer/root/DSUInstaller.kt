import android.app.Application
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.os.SharedMemory
import android.util.Log
import java.io.BufferedInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.lsposed.hiddenapibypass.HiddenApiBypass
import vegabobo.dsusideloader.model.DSUInstallationSource
import vegabobo.dsusideloader.model.ImagePartition
import vegabobo.dsusideloader.model.Type
import vegabobo.dsusideloader.preparation.InstallationStep
import vegabobo.dsusideloader.service.PrivilegedProvider

class DSUInstaller(
    private val application: Application,
    private val userdataSize: Long,
    private val dsuInstallation: DSUInstallationSource,
    private var installationJob: Job = Job(),
    private val onInstallationError: (error: InstallationStep, errorInfo: String) -> Unit,
    private val onInstallationProgressUpdate: (progress: Float, partition: String) -> Unit,
    private val onCreatePartition: (partition: String) -> Unit,
    private val onInstallationStepUpdate: (step: InstallationStep) -> Unit,
    private val onInstallationSuccess: () -> Unit,
) : () -> Unit {

    private val tag = this.javaClass.simpleName

    // ... Existing code ...

    private fun installImage(
        partition: String,
        uncompressedSize: Long,
        inputStream: InputStream,
        readOnly: Boolean = true,
    ) {
        val sis = SparseInputStream(
            BufferedInputStream(inputStream),
        )
        val partitionSize = if (sis.unsparseSize != -1L) sis.unsparseSize else uncompressedSize
        onCreatePartition(partition)
        
        // Replace this line with logic to obtain the URI or path to the existing partition.
        val existingPartitionUri = getExistingPartitionUri(partition) // Replace with actual URI/path retrieval.

        createNewPartition(partition, partitionSize, readOnly)
        onInstallationStepUpdate(InstallationStep.INSTALLING_ROOTED)
        SharedMemory.create("dsu_buffer_$partition", Constants.SHARED_MEM_SIZE)
            .use { sharedMemory ->
                MappedMemoryBuffer(sharedMemory.mapReadWrite()).use { mappedBuffer ->
                    val fdDup = getFdDup(sharedMemory)
                    setAshmem(fdDup, sharedMemory.size.toLong())
                    publishProgress(0L, partitionSize, partition)
                    var installedSize: Long = 0
                    val readBuffer = ByteArray(sharedMemory.size)
                    val buffer = mappedBuffer.mBuffer
                    var numBytesRead: Int
                    while (0 < sis.read(readBuffer, 0, readBuffer.size)
                            .also { numBytesRead = it }
                    ) {
                        if (installationJob.isCancelled) {
                            return
                        }
                        buffer!!.position(0)
                        buffer.put(readBuffer, 0, numBytesRead)
                        submitFromAshmem(numBytesRead.toLong())
                        installedSize += numBytesRead.toLong()
                        publishProgress(installedSize, partitionSize, partition)
                    }
                    publishProgress(partitionSize, partitionSize, partition)
                }
            }

        if (!closePartition()) {
            Log.d(tag, "Failed to install $partition partition")
            onInstallationError(InstallationStep.ERROR_CREATE_PARTITION, partition)
            return
        }
        Log.d(
            tag,
            "Partition $partition installed, readOnly: $readOnly, partitionSize: $partitionSize",
        )
    }

    // ... Existing code ...

    private fun startInstallation() {
        PrivilegedProvider.getService().setDynProp()
        if (isInUse) {
            onInstallationError(InstallationStep.ERROR_ALREADY_RUNNING_DYN_OS, "")
            return
        }
        if (isInstalled) {
            onInstallationError(InstallationStep.ERROR_REQUIRES_DISCARD_DSU, "")
            return
        }
        forceStopDSU()
        startInstallation(Constants.DEFAULT_SLOT)

        // Replace this line with logic to obtain the URI or path to the existing "userdata" partition.
        val userdataPartitionUri = getExistingPartitionUri("userdata") // Replace with actual URI/path retrieval.
        
        installWritablePartition("userdata", userdataSize)
        when (dsuInstallation.type) {
            Type.SINGLE_SYSTEM_IMAGE -> {
                // Replace this line with logic to obtain the URI or path to the existing "system" partition.
                val systemPartitionUri = getExistingPartitionUri("system") // Replace with actual URI/path retrieval.
                installImage(
                    "system",
                    dsuInstallation.fileSize,
                    openInputStream(systemPartitionUri),
                )
            }

            Type.MULTIPLE_IMAGES -> {
                installImages(dsuInstallation.images)
            }

            Type.DSU_PACKAGE -> {
                installStreamingZipUpdate(openInputStream(dsuInstallation.uri))
            }

            Type.URL -> {
                val url = URL(dsuInstallation.uri.toString())
                installStreamingZipUpdate(url.openStream())
            }

            else -> {}
        }
        if (!installationJob.isCancelled) {
            finishInstallation()
            Log.d(tag, "Installation finished successfully.")
            onInstallationSuccess()
        }
    }

    // ... Existing code ...

    override fun invoke() {
        startInstallation()
    }
}
