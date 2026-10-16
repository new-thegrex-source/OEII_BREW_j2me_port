/* Saves live in RMS, same names the C port writes next to the game files. */
final class SaveStore {
    static final String CONFIG = "Config";

    static byte[] read(String name) {
        javax.microedition.rms.RecordStore rs = null;
        try {
            rs = javax.microedition.rms.RecordStore.openRecordStore(name, false);
            if (rs.getNumRecords() < 1)
                return null;
            return rs.getRecord(1);
        } catch (Exception e) {
            return null;
        } finally {
            close(rs);
        }
    }

    static void write(String name, byte[] data) throws Exception {
        javax.microedition.rms.RecordStore rs = null;
        try {
            rs = javax.microedition.rms.RecordStore.openRecordStore(name, true);
            if (rs.getNumRecords() < 1)
                rs.addRecord(data, 0, data.length);
            else
                rs.setRecord(1, data, 0, data.length);
        } finally {
            close(rs);
        }
    }

    private static void close(javax.microedition.rms.RecordStore rs) {
        if (rs == null)
            return;
        try {
            rs.closeRecordStore();
        } catch (Exception ignored) {
        }
    }
}
