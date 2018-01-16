package com.indigobyte.deploy;

class CrcHolder {
    private long oldCrc;
    private long newCrc;

    public CrcHolder(long oldCrc, long newCrc) {
        this.oldCrc = oldCrc;
        this.newCrc = newCrc;
    }

    public long getOldCrc() {
        return oldCrc;
    }

    public long getNewCrc() {
        return newCrc;
    }
}
