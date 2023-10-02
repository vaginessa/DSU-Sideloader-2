package vegabobo.dsusideloader.installer.root

import android.gsi.GsiProgress
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.image.IDynamicSystemService
import vegabobo.dsusideloader.service.PrivilegedProvider

open class DynamicSystemImplementation : IDynamicSystemService {

    override fun asBinder(): IBinder? {
        return null
    }

    override fun getInstallationProgress(): GsiProgress {
        return PrivilegedProvider.getService().installationProgress
    }

    override fun abortInstallation(): Boolean {
        return PrivilegedProvider.getService().abortInstallation()
    }

    override fun isSystemInUse(): Boolean {
        return PrivilegedProvider.getService().isSystemInUse
    }

    override fun isSystemInstalled(): Boolean {
        return PrivilegedProvider.getService().isSystemInstalled
    }

    override fun isSystemEnabled(): Boolean {
        return PrivilegedProvider.getService().isSystemEnabled
    }

    override fun removeSystem(): Boolean {
        return PrivilegedProvider.getService().removeSystem()
    }

    override fun setSystemEnabled(enable: Boolean, oneShot: Boolean): Boolean {
        return PrivilegedProvider.getService().setSystemEnabled(enable, oneShot)
    }

    override fun finishInstallation(): Boolean {
        return PrivilegedProvider.getService().finishInstallation()
    }

    override fun startInstallation(dsuSlot: String): Boolean {
        return PrivilegedProvider.getService().startInstallation(dsuSlot)
    }

    override fun createPartition(name: String, size: Long, readOnly: Boolean): Int {
        return PrivilegedProvider.getService().createPartition(name, size, readOnly)
    }

    override fun closePartition(): Boolean {
        return PrivilegedProvider.getService().closePartition()
    }

    override fun setAshmem(fileDescriptor: ParcelFileDescriptor, size: Long): Boolean {
        return PrivilegedProvider.getService().setAshmem(fileDescriptor, size)
    }

    override fun submitFromAshmem(bytes: Long): Boolean {
        return PrivilegedProvider.getService().submitFromAshmem(bytes)
    }

    override fun suggestScratchSize(): Long {
        return PrivilegedProvider.getService().suggestScratchSize()
    }

    fun forceStopDynamicSystemUpdate() {
        PrivilegedProvider.getService().forceStopPackage("com.android.dynsystem")
    }
}
