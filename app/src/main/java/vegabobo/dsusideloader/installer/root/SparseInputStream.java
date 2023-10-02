/*
 * This class represents a SparseInputStream that reads from an upstream source and detects the data format.
 * If the upstream is a valid sparse data, it will unsparse it on the fly. Otherwise, it will pass through as is.
 */
public class SparseInputStream extends InputStream {
    static final int FILE_HEADER_SIZE = 28;
    static final int CHUNK_HEADER_SIZE = 12;

    /**
     * This class represents a chunk in the sparse image.
     */
    private class SparseChunk {
        static final short RAW = (short) 0xCAC1;
        static final short FILL = (short) 0xCAC2;
        static final short DONTCARE = (short) 0xCAC3;
        public short chunkType;
        public int chunkSize;
        public int totalSize;
        public byte[] fill;
        public String toString() {
            return String.format(Locale.getDefault(),
                    "type: %x, chunk_size: %d, total_size: %d", chunkType, chunkSize, totalSize);
        }
    }

    private byte[] readFull(InputStream in, int size) throws IOException {
        byte[] buf = new byte[size];
        for (int done = 0, n = 0; done < size; done += n) {
            if ((n = in.read(buf, done, size - done)) < 0) {
                throw new IOException("Failed to readFull");
            }
        }
        return buf;
    }

    private ByteBuffer readBuffer(InputStream in, int size) throws IOException {
        return ByteBuffer.wrap(readFull(in, size)).order(ByteOrder.LITTLE_ENDIAN);
    }

    private SparseChunk readChunk(InputStream in) throws IOException {
        SparseChunk chunk = new SparseChunk();
        ByteBuffer buf = readBuffer(in, CHUNK_HEADER_SIZE);
        chunk.chunkType = buf.getShort();
        buf.getShort();
        chunk.chunkSize = buf.getInt();
        chunk.totalSize = buf.getInt();
        return chunk;
    }

    private BufferedInputStream inputStream;
    private boolean isSparse;
    private long blockSize;
    private long totalBlocks;
    private long totalChunks;
    private SparseChunk currentChunk;
    private long remainingBytes;
    private int currentChunks;

    public SparseInputStream(BufferedInputStream in) throws IOException {
        inputStream = in;
        in.mark(FILE_HEADER_SIZE * 2);
        ByteBuffer buf = readBuffer(inputStream, FILE_HEADER_SIZE);
        isSparse = (buf.getInt() == 0xed26ff3a);
        if (!isSparse) {
            inputStream.reset();
            return;
        }
        int major = buf.getShort();
        int minor = buf.getShort();

        if (major > 0x1 || minor > 0x0) {
            throw new IOException("Unsupported sparse version: " + major + "." + minor);
        }

        if (buf.getShort() != FILE_HEADER_SIZE) {
            throw new IOException("Illegal file header size");
        }
        if (buf.getShort() != CHUNK_HEADER_SIZE) {
            throw new IOException("Illegal chunk header size");
        }
        blockSize = buf.getInt();
        if ((blockSize & 0x3) != 0) {
            throw new IOException("Illegal block size, must be a multiple of 4");
        }
        totalBlocks = buf.getInt();
        totalChunks = buf.getInt();
        remainingBytes = 0;
        currentChunks = 0;
    }

    private boolean prepareChunk() throws IOException {
        if (currentChunk == null || remainingBytes <= 0) {
            if (++currentChunks > totalChunks) return true;
            currentChunk = readChunk(inputStream);
            if (currentChunk.chunkType == SparseChunk.FILL) {
                currentChunk.fill = readFull(inputStream, 4);
            }
            remainingBytes = currentChunk.chunkSize * blockSize;
        }
        return remainingBytes == 0;
    }

    @Override
    public int read(byte[] buf, int off, int len) throws IOException {
        if (!isSparse) {
            return inputStream.read(buf, off, len```java
        if (!isSparse) {
            return inputStream.read(buf, off, len);
        }
        if (prepareChunk()) return -1;
        int n = -1;
        switch (currentChunk.chunkType) {
            case SparseChunk.RAW:
                n = inputStream.read(buf, off, (int) Math.min(remainingBytes, len));
                remainingBytes -= n;
                return n;
            case SparseChunk.DONTCARE:
                n = (int) Math.min(remainingBytes, len);
                Arrays.fill(buf, off, off + n, (byte) 0);
                remainingBytes -= n;
                return n;
            case SparseChunk.FILL:
                // The FILL type is rarely used, so use a simple implementation.
                return super.read(buf, off, len);
            default:
                throw new IOException("Unsupported Chunk:" + currentChunk.toString());
        }
    }

    @Override
    public int read() throws IOException {
        if (!isSparse) {
            return inputStream.read();
        }
        if (prepareChunk()) return -1;
        int ret = -1;
        switch (currentChunk.chunkType) {
            case SparseChunk.RAW:
                ret = inputStream.read();
                break;
            case SparseChunk.DONTCARE:
                ret = 0;
                break;
            case SparseChunk.FILL:
                ret = Byte.toUnsignedInt(currentChunk.fill[(4 - ((int) remainingBytes & 0x3)) & 0x3]);
                break;
            default:
                throw new IOException("Unsupported Chunk:" + currentChunk.toString());
        }
        remainingBytes--;
        return ret;
    }

    /**
     * Get the unsparse size
     * @return -1 if unknown
     */
    public long getUnsparseSize() {
        if (!isSparse) {
            return -1;
        }
        return blockSize * totalBlocks;
    }
}
