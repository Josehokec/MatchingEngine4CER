package corecer.execution.structures.CDS.time;

public abstract class CDSTimeNode {
    long mm;
    abstract public boolean isBottom();

    public long getMm() {
        return mm;
    }
}
