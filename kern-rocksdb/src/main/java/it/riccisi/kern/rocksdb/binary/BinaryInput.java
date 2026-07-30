package it.riccisi.kern.rocksdb.binary;

public interface BinaryInput {

    int nextInt();

    long nextLong();

    byte[] nextBytes(int length);

    void exhausted();
}
