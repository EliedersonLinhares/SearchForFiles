package com.esl.searchforfiles.model;


public class FileIdentity {

    private final String ntfsFileId;    // ex: "844424930132800-2051"  (volSerial-fileIndex)
    private final String fingerprint;   // ex: "relatorio.pdf:204800:1710000000000"
    private final String lastKnownPath; // path mais recente conhecido

    public FileIdentity(String ntfsFileId, String fingerprint, String lastKnownPath) {
        this.ntfsFileId    = ntfsFileId;
        this.fingerprint   = fingerprint;
        this.lastKnownPath = lastKnownPath;
    }

    public String getNtfsFileId()    { return ntfsFileId; }
    public String getFingerprint()   { return fingerprint; }
    public String getLastKnownPath() { return lastKnownPath; }

    @Override
    public String toString() {
        return String.format("FileIdentity{ntfs=%s, fp=%s, path=%s}",
                ntfsFileId, fingerprint, lastKnownPath);
    }
}