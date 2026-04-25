package com.lgcns.test;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * /monitor 로 수신된 agent 데이터를 메모리에 저장·조회하는 싱글턴 저장소.
 * agentId를 키로 레코드 리스트를 관리한다.
 */
public class DataStore {

    public static final DataStore INSTANCE = new DataStore();

    private DataStore() {}

    public static class AgentRecord {
        public final String agentId;
        public final String reqId;
        public final String time;
        public final String dataType;
        public final String dataValue;

        public AgentRecord(String agentId, String reqId, String time,
                           String dataType, String dataValue) {
            this.agentId   = agentId;
            this.reqId     = reqId;
            this.time      = time;
            this.dataType  = dataType;
            this.dataValue = dataValue;
        }
    }

    // agentId -> 해당 agent에서 수신된 레코드 목록
    private final Map<String, List<AgentRecord>> store = new ConcurrentHashMap<>();

    public void add(AgentRecord record) {
        store.computeIfAbsent(record.agentId, k -> Collections.synchronizedList(new ArrayList<>()))
             .add(record);
    }

    /** 지정한 agentId 목록에 속하는 레코드를 모두 반환 */
    public List<AgentRecord> getByAgentIds(List<String> agentIds) {
        List<AgentRecord> result = new ArrayList<>();
        for (String agentId : agentIds) {
            List<AgentRecord> records = store.get(agentId);
            if (records != null) {
                synchronized (records) {
                    result.addAll(records);
                }
            }
        }
        return result;
    }
}
