package de.greenrobot.common.checksum;

import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import de.greenrobot.common.LongHashSet;
import de.greenrobot.common.checksum.otherhashes.Md5Checksum;
import de.greenrobot.common.checksum.otherhashes.Murmur2Checksum;
import de.greenrobot.common.checksum.otherhashes.Murmur2bChecksum;
import org.junit.Test;

import java.util.Random;
import java.util.zip.Adler32;
import java.util.zip.CRC32;
import java.util.zip.Checksum;

/** Test hash functions; prints out collisions and time. */
public class HashCollider {
    private final static boolean COUNT_BITS = true;

//    @Test
    public void hashColliderTotalRandom() {
        hashCollider("Adler32", new Adler32());
        hashCollider("FNV1a", new FNV32());
        hashCollider("FNVJ", new FNVJ32());
        hashCollider("Murmur2", new Murmur2Checksum());
        // Murmur2b is faster, hashes match Murmur2
        hashCollider("Murmur2b", new Murmur2bChecksum());
        hashCollider("Murmur3A-32", new Murmur32Checksum());
        hashCollider("FNVJ64", new FNVJ64());
        hashCollider("FNV1a-64", new FNV64());
        hashCollider("CRC32", new CRC32());
        hashCollider("Combined", new CombinedChecksum(new Adler32(), new CRC32()));
        hashCollider("MD5", new Md5Checksum());
    }

    //    @Test
    public void hashColliderSmallChanges() {
        hashColliderSmallChanges("Adler32", new Adler32());
        hashColliderSmallChanges("FNV1a", new FNV32());
        hashColliderSmallChanges("FNVJ", new FNVJ32());
        hashColliderSmallChanges("FNV1a-64", new FNV64());
        hashColliderSmallChanges("CRC32", new CRC32());
        hashColliderSmallChanges("Combined", new CombinedChecksum(new Adler32(), new CRC32()));
        hashColliderSmallChanges("Murmur3A-32", new Murmur32Checksum());
    }

    public void hashCollider(String name, Checksum checksum) {
        hashCollider(name, checksum, 10000000, 1024, 10, true);
    }

    public void hashColliderSmallChanges(String name, Checksum checksum) {
        hashCollider(name, checksum, 1000000, 1024, 10, false);
    }

    public void hashCollider(String name, Checksum checksum, int count, int byteLength, int logCount,
                             boolean totalRandom) {
        System.out.println(name + "\t-----------------------------------------------------------");

        // Provide seed (42) to have reproducible results
        Random random = new Random(42);
        byte[] bytes = new byte[byteLength];
        int[] bitOneCounts = new int[64];

        LongHashSet values = new LongHashSet(count);
        int collisions = 0;
        long totalTime = 0;
        int firstCollision = 0;
        int indexToChange = -1; // used if !totalRandom
        for (int i = 0; i < count; i++) {
            if (totalRandom) {
                random.nextBytes(bytes);
            } else {
                if (indexToChange != -1) {
                    byte existing = bytes[indexToChange];
                    byte newValue;
                    do {
                        newValue = (byte) random.nextInt();
                    } while (existing == newValue);
                    bytes[indexToChange] = newValue;
                }
                indexToChange++;
                if (indexToChange == byteLength) {
                    indexToChange = 0;
                }
            }
            checksum.reset();
            long start = System.nanoTime();
            checksum.update(bytes, 0, bytes.length);
            totalTime += System.nanoTime() - start;
            long hash = checksum.getValue();
            if (!values.add(hash)) {
                collisions++;
                if (firstCollision == 0) {
                    firstCollision = i + 1;
                }
            }
            if (COUNT_BITS) {
                for (int bitPos = 0; bitPos < 64; bitPos++) {
                    if (((hash >> bitPos) & 1) == 1) {
                        bitOneCounts[bitPos]++;

                    }
                }
            }

            if ((i + 1) % (count / logCount) == 0) {
                System.out.println(name + "\t" + (i + 1) + "\t\t" + "collisions: " + collisions + "\t\tms: " +
                        (totalTime / 1000000) + "\t\thash: " + hash);
            }
        }
        System.out.println(name + "\tfirst collision at: " + (firstCollision == 0 ? "none" : firstCollision));

        checkBitStats(name, bitOneCounts, count);
    }

    private void checkBitStats(String name, int[] bitOneCounts, int count) {
        if (COUNT_BITS) {
            boolean is64 = bitOneCounts[32] + bitOneCounts[33] > 0;
            int bits = is64 ? 64 : 32;
            int perfectBitCount = count / 2;
            long offSum = 0;
            long offSumQ = 0;
            double q = 0;
            for (int bitPos = 0; bitPos < bits; bitPos++) {
                int bitOneCount = bitOneCounts[bitPos];
                int delta = Math.abs(perfectBitCount - bitOneCount);
                //                System.out.println(name + "\tBit " + (bitPos < 10 ? "0" + bitPos : bitPos) + ": " + bitOneCount +
                //                        "\t\tdelta perfect: " + delta);
                offSum += delta;
                offSumQ += ((long) delta) * delta;
                q += ((double) delta) * delta / count / bits;
            }
            System.out.println(name + "\tQuality - off sum: " + offSum + "\t\toff² sum: " + offSumQ +
                    "\t\tnegQ: " + q);
        }
    }

    static class Murmur32Checksum implements Checksum {
        HashFunction x = Hashing.murmur3_32();
        Long hash;

        @Override
        public void update(int b) {
            throw new RuntimeException("Not implemented");
        }

        @Override
        public void update(byte[] b, int off, int len) {
            if (hash != null) {
                throw new RuntimeException("No hash building available");
            }
            hash = 0xffffffffL & x.hashBytes(b, off, len).asInt();
        }

        @Override
        public long getValue() {
            return hash;
        }

        @Override
        public void reset() {
            hash = null;
        }
    }

}