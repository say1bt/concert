package ds.tutorial.synchronization.processs;

public interface DistributedTxListner {
    void onGlobalCommit();
    void onGlobalAbort();
}
