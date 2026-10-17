package dto;

import AIChat.ChatDataKind;
import java.util.*;

/** Safe operational projections, never an account or full database entity. */
public final class ChatDatabaseSnapshotDTO {
    private final String scope, capturedAt;
    private final Map<ChatDataKind,List<Map<String,Object>>> records;
    private final Map<ChatDataKind,Map<String,Long>> totals;

    public ChatDatabaseSnapshotDTO(String scope,String capturedAt,
            Map<ChatDataKind,List<Map<String,Object>>> records,Map<ChatDataKind,Map<String,Long>> totals) {
        this.scope=scope;this.capturedAt=capturedAt;
        Map<ChatDataKind,List<Map<String,Object>>> copy=new EnumMap<>(ChatDataKind.class);
        for(Map.Entry<ChatDataKind,List<Map<String,Object>>> entry:records.entrySet()) {
            List<Map<String,Object>> rows=new ArrayList<>();
            for(Map<String,Object> row:entry.getValue()) rows.add(Collections.unmodifiableMap(new LinkedHashMap<>(row)));
            copy.put(entry.getKey(),Collections.unmodifiableList(rows));
        }
        this.records=Collections.unmodifiableMap(copy);
        Map<ChatDataKind,Map<String,Long>> counts=new EnumMap<>(ChatDataKind.class);
        for(Map.Entry<ChatDataKind,Map<String,Long>> entry:totals.entrySet()) counts.put(entry.getKey(),Collections.unmodifiableMap(new LinkedHashMap<>(entry.getValue())));
        this.totals=Collections.unmodifiableMap(counts);
    }
    public String getScope(){return scope;}
    public String getCapturedAt(){return capturedAt;}
    public Map<ChatDataKind,List<Map<String,Object>>> getRecords(){return records;}
    public Map<ChatDataKind,Map<String,Long>> getTotals(){return totals;}
}
